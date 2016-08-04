package com.mishiranu.dashchan.chan.nullnyan;

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

public class NullnyanChanPerformer extends ChanPerformer
{
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		NullnyanChanLocator locator = NullnyanChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new NullnyanPostsParser(responseText, this, data.boardName).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		NullnyanChanLocator locator = NullnyanChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		try
		{
			ArrayList<Post> posts = new NullnyanPostsParser(responseText, this, data.boardName).convertPosts();
			if (posts == null || posts.isEmpty()) throw new InvalidResponseException();
			return new ReadPostsResult(posts);
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		NullnyanChanLocator locator = NullnyanChanLocator.get(this);
		Uri uri = locator.createBoardUri("b", 0);
		String responseText = new HttpRequest(uri, data).read().getString();
		try
		{
			return new ReadBoardsResult(new NullnyanBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		NullnyanChanLocator locator = NullnyanChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		if (!responseText.contains("<form class=\"userdelete\" id=\"delform\"")) throw new InvalidResponseException();
		int count = -1;
		int index = 0;
		while (index != -1)
		{
			count++;
			index = responseText.indexOf("<label class=\"post-info\"", index + 1);
		}
		return new ReadPostsCountResult(count);
	}
	
	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("<span class=\"center\">(.*?)</span>");
	private static final Pattern PATTERN_POST_REDIRECT = Pattern.compile("<meta http-equiv=\"refresh\" content=\"0;" +
			"url=(.*?)\">");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		String email = data.email;
		if (data.name != null)
		{
			int index = data.name.indexOf('#');
			if (index >= 0)
			{
				String tripcode = data.name.substring(index);
				if (email == null) email = tripcode; else email += tripcode;
			}
		}
		MultipartEntity entity = new MultipartEntity();
		entity.add("parent", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("email", email); // Format: email#tripcode
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("noko", "on");
		entity.add("password", data.password);
		if (data.optionSage) entity.add("sage", "on");
		if (data.attachments != null)
		{
			SendPostData.Attachment attachment = data.attachments[0];
			attachment.addToEntity(entity, "file");
		}
		
		NullnyanChanLocator locator = NullnyanChanLocator.get(this);
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
		
		matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (!matcher.find()) throw new InvalidResponseException();
		responseText = StringUtils.clearHtml(matcher.group(1));
		int errorType = 0;
		if (responseText.contains("Please enter a message and/or upload a file"))
		{
			errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
		}
		else if (responseText.contains("A file or embed URL is required"))
		{
			errorType = ApiException.SEND_ERROR_EMPTY_FILE;
		}
		else if (responseText.contains("Please wait a moment before posting again"))
		{
			errorType = ApiException.SEND_ERROR_TOO_FAST;
		}
		else if (responseText.contains("Invalid parent thread ID supplied"))
		{
			errorType = ApiException.SEND_ERROR_NO_THREAD;
		}
		if (errorType != 0) throw new ApiException(errorType);
		CommonUtils.writeLog("Nullnyan send message", responseText);
		throw new ApiException(responseText);
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		NullnyanChanLocator locator = NullnyanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php").buildUpon().query("delete").build();
		UrlEncodedEntity entity = new UrlEncodedEntity("password", data.password, "delete", data.postNumbers.get(0));
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
		Matcher matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (!matcher.find()) throw new InvalidResponseException();
		responseText = StringUtils.clearHtml(matcher.group(1));
		if (responseText.contains("Post deleted")) return new SendDeletePostsResult();
		int errorType = 0;
		if (responseText.contains("Invalid password"))
		{
			errorType = ApiException.DELETE_ERROR_PASSWORD;
		}
		if (errorType != 0) throw new ApiException(errorType);
		CommonUtils.writeLog("Nullnyan delete message", responseText);
		throw new ApiException(responseText);
	}
}