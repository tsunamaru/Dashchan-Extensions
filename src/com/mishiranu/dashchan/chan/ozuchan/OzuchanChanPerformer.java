package com.mishiranu.dashchan.chan.ozuchan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	
	private static final Pattern PATTERN_POST_REDIRECT = Pattern.compile("<meta http-equiv=\"refresh\" content=\"0;" +
			"url=(.*?)\">");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("parent", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", data.name);
		entity.add("email", data.email);
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("dir", "thread");
		entity.add("password", data.password);
		if (data.optionSage) entity.add("sage", "sage");
		if (data.attachments != null)
		{
			SendPostData.Attachment attachment = data.attachments[0];
			attachment.addToEntity(entity, "file");
		}
		
		OzuchanChanLocator locator = OzuchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
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
		if (responseText.contains("Введите сообщение и/или загрузите файл"))
		{
			errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
		}
		else if (responseText.contains("Вставьте file, чтобы начать тред"))
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