package com.mishiranu.dashchan.chan.ozuchan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class OzuchanChanPerformer extends ChanPerformer
{
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new OzuchanPostsParser(responseText, this, data.boardName).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		try
		{
			ArrayList<Post> posts = new OzuchanPostsParser(responseText, this, data.boardName).convertPosts();
			if (posts == null || posts.isEmpty()) throw new InvalidResponseException();
			return new ReadPostsResult(posts);
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		if (!responseText.contains("<form id=\"delform\"")) throw new InvalidResponseException();
		int count = 0;
		int index = 0;
		while (index != -1)
		{
			count++;
			index = responseText.indexOf("<td class=\"reply\"", index + 1);
		}
		return new ReadPostsCountResult(count);
	}
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "inc", "captcha.php");
		Bitmap image = new HttpRequest(uri, data).read().getBitmap();
		if (image == null) throw new InvalidResponseException();
		String sessionCookie = data.holder.getCookieValue("PHPSESSID");
		Bitmap newImage = image.copy(image.getConfig(), true);
		image.recycle();
		if (newImage == null) throw new RuntimeException();
		int[] pixels = new int[newImage.getWidth()];
		for (int y = 0; y < newImage.getHeight(); y++)
		{
			newImage.getPixels(pixels, 0, pixels.length, 0, y, pixels.length, 1);
			for (int x = 0; x < pixels.length; x++)
			{
				int r = Color.red(pixels[x]);
				int g = Color.green(pixels[x]);
				int b = Color.blue(pixels[x]);
				if (r == g && r == b) pixels[x] = 0xffffffff; else
				{
					int c = (r + g + b) / 3;
					pixels[x] = Color.argb(0xff, c, c, c);
				}
			}
			newImage.setPixels(pixels, 0, pixels.length, 0, y, pixels.length, 1);
		}
		image = CommonUtils.trimBitmap(newImage, 0xffffffff);
		if (image != newImage) newImage.recycle();
		if (image == null) throw new InvalidResponseException();
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.CHALLENGE, sessionCookie);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
	}
	
	private static final Pattern PATTERN_POST_REDIRECT = Pattern.compile("<meta http-equiv=\"refresh\" content=\"0;" +
			"url=(.*?)\">");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("parent", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", StringUtils.emptyIfNull(data.name));
		entity.add("email", StringUtils.emptyIfNull(data.email));
		entity.add("subject", StringUtils.emptyIfNull(data.subject));
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("dir", "thread");
		entity.add("password", data.password);
		if (data.optionSage) entity.add("sage", "sage");
		if (data.attachments != null)
		{
			SendPostData.Attachment attachment = data.attachments[0];
			attachment.addToEntity(entity, "file");
		}
		String sessionCookie = null;
		if (data.captchaData != null)
		{
			sessionCookie = data.captchaData.get(CaptchaData.CHALLENGE);
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
		}
		
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity).addCookie("PHPSESSID", sessionCookie)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
		Matcher matcher = PATTERN_POST_REDIRECT.matcher(responseText);
		if (matcher.find())
		{
			uri = Uri.parse("/" + data.boardName + "/" + matcher.group(1));
			String threadNumber = locator.getThreadNumber(uri);
			String postNumber = locator.getPostNumber(uri);
			return new SendPostResult(threadNumber, postNumber);
		}
		
		int index = responseText.indexOf("<br><br>");
		if (index == -1) throw new InvalidResponseException();
		responseText = StringUtils.clearHtml(responseText.substring(0, index));
		int errorType = 0;
		if (responseText.contains("CAPTCHA"))
		{
			errorType = ApiException.SEND_ERROR_CAPTCHA;
		}
		else if (responseText.contains("Введите сообщение и/или загрузите файл"))
		{
			errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
		}
		else if (responseText.contains("Загрузите file, чтобы начать тред"))
		{
			errorType = ApiException.SEND_ERROR_EMPTY_FILE;
		}
		else if (responseText.contains("Invalid parent thread ID supplied"))
		{
			errorType = ApiException.SEND_ERROR_NO_THREAD;
		}
		if (errorType != 0) throw new ApiException(errorType);
		CommonUtils.writeLog("Ozuchan send message", responseText);
		throw new ApiException(responseText);
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php").buildUpon().query("delete").build();
		UrlEncodedEntity entity = new UrlEncodedEntity("password", data.password);
		for (String postNumber : data.postNumbers) entity.add("delete", postNumber);
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
		int index = responseText.indexOf("<br><br>");
		if (index == -1) throw new InvalidResponseException();
		responseText = StringUtils.clearHtml(responseText.substring(0, index));
		if (responseText.contains("Пост/тред удалён")) return new SendDeletePostsResult();
		int errorType = 0;
		if (responseText.contains("Неправильный пароль"))
		{
			errorType = ApiException.DELETE_ERROR_PASSWORD;
		}
		if (errorType != 0) throw new ApiException(errorType);
		CommonUtils.writeLog("Ozuchan delete message", responseText);
		throw new ApiException(responseText);
	}
}