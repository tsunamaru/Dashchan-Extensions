package com.mishiranu.dashchan.chan.krautchan;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class KrautchanChanPerformer extends ChanPerformer
{
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = data.isCatalog() ? locator.buildPath("catalog", data.boardName, "")
				: locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			ArrayList<Posts> posts;
			if (data.isCatalog()) posts = new KrautchanCatalogParser(responseText, this).convert();
			else posts = new KrautchanPostsParser(responseText, this, data.boardName).convertThreads();
			return new ReadThreadsResult(posts);
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.setSuccessOnly(false).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
		{
			uri = locator.buildPath("resolve", data.boardName, data.threadNumber);
			new HttpRequest(uri, data.holder, data).setRedirectHandler(HttpRequest.RedirectHandler.NONE).read();
			if (data.holder.getResponseCode() == HttpsURLConnection.HTTP_MOVED_TEMP)
			{
				Uri location = data.holder.getRedirectedUri();
				String threadNumber = locator.getThreadNumber(location);
				String postNumber = locator.getPostNumber(location);
				if (threadNumber != null) throw new ThreadRedirectException(threadNumber, postNumber);
			}
			throw HttpException.createNotFoundException();
		}
		data.holder.checkResponseCode();
		try
		{
			return new ReadPostsResult(new KrautchanPostsParser(responseText, this, data.boardName).convertPosts());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("nav");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new KrautchanBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		int count = 0;
		int index = 0;
		while (index != -1)
		{
			count++;
			index = responseText.indexOf("<td class=postreply", index + 1);
		}
		return new ReadPostsCountResult(count);
	}
	
	private static final Pattern PATTERN_ERROR = Pattern.compile("<td class=\"message_text.*?\">([\\s\\S]*?)</td>");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("board", data.boardName);
		entity.add("parent", data.threadNumber);
		entity.add("internal_n", data.name);
		entity.add("internal_s", data.subject);
		entity.add("internal_t", data.comment);
		if (data.optionSage) entity.add("sage", "1");
		entity.add("forward", "thread");
		entity.add("password", data.password);
		if (data.attachments != null)
		{
			for (int i = 0; i < data.attachments.length; i++) data.attachments[i].addToEntity(entity, "file_" + i);
		}

		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("post");
		String responseText;
		try
		{
			new HttpRequest(uri, data.holder, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.NONE).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP)
			{
				uri = data.holder.getRedirectedUri();
				if (("/banned/" + data.boardName).equals(uri.getPath()))
				{
					throw new ApiException(ApiException.SEND_ERROR_BANNED);
				}
				String threadNumber = locator.getThreadNumber(uri);
				return new SendPostResult(threadNumber, null);
			}
			responseText = data.holder.read().getString();
		}
		finally
		{
			data.holder.disconnect();
		}
		
		Matcher matcher = PATTERN_ERROR.matcher(responseText);
		if (matcher.find())
		{
			String errorMessage = matcher.group(1).trim();
			int errorType = 0;
			if (errorMessage.equals("Post contains neither files nor a comment.")
					|| errorMessage.equals("Post enth&auml;lt weder Dateien noch einen Kommentar.")
					|| errorMessage.equals("New threads must include a comment.")
					|| errorMessage.equals("Neue Threads m&uuml;ssen einen Kommentar enthalten."))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			else if (errorMessage.equals("New threads must include at least one file.")
					|| errorMessage.equals("Neue Threads m&uuml;ssen mindestens eine Datei enthalten."))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			}
			else if (errorMessage.contains("Kommentar zu lang"))
			{
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			}
			else if (errorMessage.contains("is too large") || errorMessage.contains("ist zu gro&szlig;"))
			{
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			}
			if (errorType != 0) throw new ApiException(errorType);
			CommonUtils.writeLog("Krautchan send message", errorMessage);
			throw new ApiException(StringUtils.clearHtml(errorMessage));
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("delete");
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "password", data.password);
		for (String postNumber : data.postNumbers) entity.add("post_" + postNumber, "delete");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.NONE).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) return null;
		int wasNotDeleted = 0;
		String errorMessage = null;
		Matcher matcher = PATTERN_ERROR.matcher(responseText);
		while (matcher.find())
		{
			errorMessage = matcher.group(1).trim();
			if (errorMessage.contains("was not deleted") || errorMessage.contains("wurde nicht gel&ouml;scht"))
			{
				wasNotDeleted++;
			}
			else CommonUtils.writeLog("Krautchan delete message", errorMessage);
		}
		if (wasNotDeleted == data.postNumbers.size()) throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
		throw new InvalidResponseException();
	}
	
	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		KrautchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("ajax", "report", "submit");
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "post", data.postNumbers.get(0),
				"type", data.type, "comment", data.comment);
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		if (jsonObject.optInt("success") != 0) return null;
		JSONArray jsonArray = jsonObject.optJSONArray("messages");
		String errorMessage = null;
		if (jsonArray != null)
		{
			for (int i = 0; i < jsonArray.length(); i++)
			{
				JSONArray errorArray = jsonArray.optJSONArray(i);
				if (errorArray != null)
				{
					if ("error".equals(errorArray.optString(0)))
					{
						String message = errorArray.optString(1);
						if (message != null)
						{
							errorMessage = message;
							if (message.contains("existiert nicht"))
							{
								throw new ApiException(ApiException.REPORT_ERROR_NO_ACCESS);
							}
						}
					}
				}
			}
		}
		if (errorMessage != null)
		{
			CommonUtils.writeLog("Krautchan report message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}
}