package com.mishiranu.dashchan.chan.britfags;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;
import android.util.Pair;

import chan.content.ApiException;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class BritfagsChanPerformer extends ChanPerformer
{
	private static final Pattern PATTERN_CATALOG = Pattern.compile("(?s)<td valign=\"middle\">.*?" +
			"<a href=\"(.*?)\">.*?<small>(\\d+)</small>");

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		BritfagsChanLocator locator = BritfagsChanLocator.get(this);
		if (data.isCatalog())
		{
			Uri uri = locator.buildPath(data.boardName, "catalog.html");
			String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
			ArrayList<Pair<String, Integer>> threadInfos = new ArrayList<>();
			Matcher matcher = PATTERN_CATALOG.matcher(responseText);
			while (matcher.find())
			{
				String threadNumber = locator.getThreadNumber(Uri.parse(matcher.group(1)));
				int replies = Integer.parseInt(matcher.group(2));
				threadInfos.add(new Pair<>(threadNumber, replies));
			}
			if (threadInfos.isEmpty()) return null;
			uri = locator.buildQuery("brian/wire/expand.php", "board", data.boardName, "threadid", "0");
			responseText = new HttpRequest(uri, data).read().getString();
			ArrayList<Post> posts;
			try
			{
				posts = new BritfagsPostsParser(responseText, this, data.boardName).convertPosts();
			}
			catch (ParseException e)
			{
				throw new InvalidResponseException(e);
			}
			HashMap<String, Post> postsMap = new HashMap<>();
			for (Post post : posts)
			{
				post.setParentPostNumber(null);
				postsMap.put(post.getPostNumber(), post);
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
			String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
			try
			{
				return new ReadThreadsResult(new BritfagsPostsParser(responseText, this, data.boardName)
						.convertThreads());
			}
			catch (ParseException e)
			{
				throw new InvalidResponseException(e);
			}
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		BritfagsChanLocator locator = BritfagsChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadPostsResult(new BritfagsPostsParser(responseText, this, data.boardName).convertPosts());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		BritfagsChanLocator locator = BritfagsChanLocator.get(this);
		Uri uri = locator.buildPath("brian", "menu.html");
		String responseText = new HttpRequest(uri, data).read().getString();
		try
		{
			return new ReadBoardsResult(new BritfagsBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		BritfagsChanLocator locator = BritfagsChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + "+50.html");
		String responseText = new HttpRequest(uri, data).setValidator(data.validator)
				.setSuccessOnly(false).read().getString();
		boolean notFound = data.holder.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND;
		int count = 0;
		if (notFound)
		{
			uri = locator.createThreadUri(data.boardName, data.threadNumber);
			responseText = new HttpRequest(uri, data).setValidator(data.validator).read().getString();
		}
		else data.holder.checkResponseCode();
		int index = 0;
		while (index != -1)
		{
			count++;
			index = responseText.indexOf("<td class=\"reply\"", index + 1);
		}
		index = responseText.indexOf("<span class=\"omittedposts\">");
		if (index >= 0)
		{
			Matcher matcher = BritfagsPostsParser.PATTERN_NUMBER.matcher(responseText);
			matcher.find(index + 27);
			count += Integer.parseInt(matcher.group());
		}
		return new ReadPostsCountResult(count);
	}

	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("(?s)<h2.*?>(.*?)</h2>");

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("board", data.boardName);
		entity.add("replythread", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", data.name);
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("postpassword", data.password);
		if (data.optionSage) entity.add("sagecheck", "on");
		if (data.attachments != null) data.attachments[0].addToEntity(entity, "imagefile");

		BritfagsChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("brian", "post", "");
		String responseText;
		try
		{
			new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.NONE).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) return null;
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
			if (errorMessage.contains("is required for a reply"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			else if (errorMessage.contains("file is required for a new thread"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			}
			else if (errorMessage.contains("Invalid thread ID"))
			{
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			}
			else if (errorMessage.contains("your message is too long"))
			{
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			}
			else if (errorMessage.contains("Flood Detected"))
			{
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			}
			else if (errorMessage.contains("Duplicate file entry detected"))
			{
				errorType = ApiException.SEND_ERROR_FILE_EXISTS;
			}
			else if (errorMessage.contains("thread is locked"))
			{
				errorType = ApiException.SEND_ERROR_CLOSED;
			}
			if (errorType != 0) throw new ApiException(errorType, extra);
			CommonUtils.writeLog("Britfags send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		BritfagsChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "deletepost", "1",
				"postpassword", data.password);
		for (String postNumber : data.postNumbers) entity.add("delete", postNumber);
		if (data.optionFilesOnly) entity.add("fileonly", "on");
		Uri uri = locator.buildPath("brian", "post", "board.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.NONE).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) return null;
		if (responseText != null)
		{
			responseText = StringUtils.clearHtml(responseText);
			if (responseText.contains("Post successfully deleted") || responseText.contains("File successfully deleted")
					|| responseText.contains("doesn't have a file"))
			{
				// Response has message for any post
				// Ignore them, if at least 1 of them was deleted
				return null;
			}
			else if (responseText.contains("Incorrect password"))
			{
				throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
			}
			CommonUtils.writeLog("Britfags delete message", responseText);
			throw new ApiException(responseText);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		BritfagsChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "reportpost", "1",
				"reportreason", data.comment);
		for (String postNumber : data.postNumbers) entity.add("delete", postNumber);
		Uri uri = locator.buildPath("brian", "post", "board.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.NONE).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) return null;
		if (responseText != null)
		{
			responseText = StringUtils.clearHtml(responseText);
			if (responseText.contains("Post successfully reported") ||
					responseText.contains("That post is already in the report list") ||
					responseText.contains("That post has been cleared as not requiring any deletion"))
			{
				// Response has message for any post
				// Ignore them, if at least 1 of them was reported
				return null;
			}
			CommonUtils.writeLog("Britfags report message", responseText);
			throw new ApiException(responseText);
		}
		throw new InvalidResponseException();
	}
}