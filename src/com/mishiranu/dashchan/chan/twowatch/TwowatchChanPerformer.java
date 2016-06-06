package com.mishiranu.dashchan.chan.twowatch;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Pair;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class TwowatchChanPerformer extends ChanPerformer
{
	private static final Pattern PATTERN_CATALOG = Pattern.compile("(?s)<center><a href=\"/.*?/res/(\\d+).html\">.*?"
			+ "</a></center><br />.*?<center.*?><small><i>R: (\\d+)</i></small>");
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		if (data.isCatalog())
		{
			Uri uri = locator.buildPath(data.boardName, "catalog.html");
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			ArrayList<Pair<String, Integer>> threadInfos = new ArrayList<>();
			Matcher matcher = PATTERN_CATALOG.matcher(responseText);
			while (matcher.find())
			{
				String threadNumber = matcher.group(1);
				if (threadNumber != null)
				{
					int replies = Integer.parseInt(matcher.group(2));
					threadInfos.add(new Pair<String, Integer>(threadNumber, replies));
				}
			}
			if (threadInfos.isEmpty()) return null;
			uri = locator.buildQuery("expand.php", "board", data.boardName);
			responseText = new HttpRequest(uri, data.holder, data).read().getString();
			ArrayList<Post> posts;
			try
			{
				posts = new TwowatchPostsParser(responseText, this, data.boardName).convertPosts(false);
				if (posts == null || posts.isEmpty()) return null;
			}
			catch (ParseException e)
			{
				throw new InvalidResponseException(e);
			}
			HashMap<String, Post> postsMap = new HashMap<>();
			for (Post post : posts)
			{
				if (post.getParentPostNumber() == null) postsMap.put(post.getPostNumber(), post);
			}
			ArrayList<Posts> threads = new ArrayList<>();
			for (Pair<String, Integer> threadInfo : threadInfos)
			{
				Post post = postsMap.get(threadInfo.first);
				if (post != null)
				{
					Posts thread = new Posts(post);
					thread.addPostsCount(1 + threadInfo.second);
					threads.add(thread);
				}
			}
			return new ReadThreadsResult(threads);
		}
		else
		{
			Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			try
			{
				return new ReadThreadsResult(new TwowatchPostsParser(responseText, this, data.boardName)
						.convertThreads());
			}
			catch (ParseException e)
			{
				throw new InvalidResponseException(e);
			}
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.read().getString();
		try
		{
			return new ReadPostsResult(new TwowatchPostsParser(responseText, this, data.boardName)
					.convertPosts(true));
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildQuery("read.php", "b", data.boardName, "t", "0", "p", data.postNumber, "single", "");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			Post post = new TwowatchPostsParser(responseText, this, data.boardName).convertSinglePost();
			if (post == null) throw HttpException.createNotFoundException();
			return new ReadSinglePostResult(post);
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("menu.php");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new TwowatchBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		int count = 0;
		int index = 0;
		while (index != -1)
		{
			count++;
			index = responseText.indexOf("<td class=\"reply\"", index + 1);
		}
		return new ReadPostsCountResult(count);
	}
	
	private static final ColorMatrixColorFilter CAPTCHA_FILTER = new ColorMatrixColorFilter(new float[]
			{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f});
	
	private final HashMap<String, Pair<HttpValidator, Boolean>> mReadCaptchaValidators = new HashMap<>();
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		boolean needCaptcha = false;
		Pair<HttpValidator, Boolean> pair = mReadCaptchaValidators.get(data.boardName);
		try
		{
			Uri uri = locator.createBoardUri(data.boardName, 0);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(pair != null ? pair.first : null)
					.setSuccessOnly(false).read().getString();
			needCaptcha = responseText.contains("<img id=\"captchaimage\"");
			pair = new Pair<>(data.holder.getValidator(), needCaptcha);
			mReadCaptchaValidators.put(data.boardName, pair);
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) needCaptcha = pair.second; else throw e;
		}
		if (!needCaptcha) return new ReadCaptchaResult(CaptchaState.SKIP, null);
		Uri uri = locator.buildPath("captcha.php");
		Bitmap image = new HttpRequest(uri, data.holder, data).addCookie("captchalang", "ru").read().getBitmap();
		if (image != null)
		{
			Bitmap newImage = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(newImage);
			canvas.drawColor(0xffffffff);
			Paint paint = new Paint();
			paint.setColorFilter(CAPTCHA_FILTER);
			canvas.drawBitmap(image, 0f, 0f, paint);
			image.recycle();
			String sessionCookie = data.holder.getCookieValue("PHPSESSID");
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.CHALLENGE, sessionCookie);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(newImage);
		}
		throw new InvalidResponseException();
	}
	
	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("(?s)<h2.*?>(.*?)</h2>");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("board", data.boardName);
		entity.add("replythread", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", data.name);
		if (data.optionSage) entity.add("em", "sage");
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("postpassword", data.password);
		entity.add("redirecttothread", "1");
		entity.add("embed", ""); // Otherwise there will be a "Please enter an embed ID" error
		if (data.attachments != null) data.attachments[0].addToEntity(entity, "imagefile");
		String sessionCookie = null;
		if (data.captchaData != null)
		{
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
			sessionCookie = data.captchaData.get(CaptchaData.CHALLENGE);
		}
		
		TwowatchChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("board.php");
		String responseText;
		try
		{
			new HttpRequest(uri, data.holder, data).setPostMethod(entity).addCookie("PHPSESSID", sessionCookie)
					.setRedirectHandler(HttpRequest.RedirectHandler.NONE).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP)
			{
				uri = data.holder.getRedirectedUri();
				String threadNumber = locator.getThreadNumber(uri);
				return new SendPostResult(threadNumber, null);
			}
			responseText = data.holder.read().getString();
		}
		finally
		{
			data.holder.disconnect();
		}
		
		Matcher matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (matcher.find())
		{
			String errorMessage = matcher.group(1).trim();
			int errorType = 0;
			Object extra = null;
			if (errorMessage.contains("Введен неправильный код подтверждения"))
			{
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			}
			else if (errorMessage.contains("загрузите изображение или введите текст"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			else if (errorMessage.contains("A file is required for a new thread") ||
					errorMessage.contains("Please enter an embed ID"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			}
			else if (errorMessage.contains("Invalid thread ID"))
			{
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			}
			else if (errorMessage.contains("Please wait a moment before posting again"))
			{
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			}
			if (errorType != 0) throw new ApiException(errorType, extra);
			CommonUtils.writeLog("Twowatch send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "deletepost", "1",
				"postpassword", data.password);
		for (String postNumber : data.postNumbers) entity.add("post[]", postNumber);
		if (data.optionFilesOnly) entity.add("fileonly", "on");
		Uri uri = locator.buildPath("board.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity).read().getString();
		if (responseText != null)
		{
			if (responseText.contains("Сообщение удалено") || responseText.contains("Изображение из сообщения удалено")
					|| responseText.contains("В Вашем сообщении нет изображений"))
			{
				// Response has message for any post
				// Ignore them, if at least 1 of them was deleted
				return null;
			}
			else if (responseText.contains("Неверный пароль"))
			{
				throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
			}
			CommonUtils.writeLog("Twowatch delete message", responseText);
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		TwowatchChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "reportpost", "1",
				"reportreason", data.comment);
		for (String postNumber : data.postNumbers) entity.add("post[]", postNumber);
		Uri uri = locator.buildPath("board.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity).read().getString();
		if (responseText != null)
		{
			if (responseText.contains("Post successfully reported") ||
					responseText.contains("That post is already in the report list") ||
					responseText.contains("That post has been cleared as not requiring any deletion"))
			{
				// Response has message for any post
				// Ignore them, if at least 1 of them was reported
				return null;
			}
			CommonUtils.writeLog("Twowatch report message", responseText);
		}
		throw new InvalidResponseException();
	}
}