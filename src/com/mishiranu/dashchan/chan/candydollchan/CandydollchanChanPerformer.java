package com.mishiranu.dashchan.chan.candydollchan;

import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class CandydollchanChanPerformer extends ChanPerformer {
	private static final HttpRequest.RedirectHandler SCHEME_REDIRECT_HANDLER =
			(responseCode, requestedUri, redirectedUri, holder) -> {
		HttpRequest.RedirectHandler.Action action = HttpRequest.RedirectHandler.BROWSER
				.onRedirectReached(responseCode, requestedUri, redirectedUri, holder);
		if (!requestedUri.getScheme().equals(redirectedUri.getScheme())) {
			action.setRedirectedUri(redirectedUri.buildUpon().scheme(requestedUri.getScheme()).build());
		}
		return action;
	};

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		CandydollchanChanLocator locator = CandydollchanChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data).setRedirectHandler(SCHEME_REDIRECT_HANDLER)
				.setValidator(data.validator).read().getString();
		try {
			return new ReadThreadsResult(new CandydollchanPostsParser(responseText, this, data.boardName)
					.convertThreads());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		CandydollchanChanLocator locator = CandydollchanChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setRedirectHandler(SCHEME_REDIRECT_HANDLER)
				.setValidator(data.validator).read().getString();
		try {
			return new ReadPostsResult(new CandydollchanPostsParser(responseText, this, data.boardName).convertPosts());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		CandydollchanChanLocator locator = CandydollchanChanLocator.get(this);
		Uri uri = locator.buildPath("");
		String responseText = new HttpRequest(uri, data).setRedirectHandler(SCHEME_REDIRECT_HANDLER).read().getString();
		try {
			return new ReadBoardsResult(new CandydollchanBoardsParser(responseText).convert());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		CandydollchanChanLocator locator = CandydollchanChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).setRedirectHandler(SCHEME_REDIRECT_HANDLER)
				.setValidator(data.validator).read().getString();
		if (!responseText.contains("<form id=\"delform\"")) {
			throw new InvalidResponseException();
		}
		int count = 0;
		int index = 0;
		while (index != -1) {
			count++;
			index = responseText.indexOf("<td class=\"reply\"", index + 1);
		}
		return new ReadPostsCountResult(count);
	}

	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("(?s)<div.*?F1EFC4.*?>(.*?)</div>");

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("board", data.boardName);
		entity.add("replythread", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", data.name);
		entity.add("em", data.optionSage ? "sage" : data.email);
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("postpassword", data.password);
		entity.add("embed", ""); // Otherwise there will be a "Please enter an embed ID" error
		if (data.attachments != null) {
			data.attachments[0].addToEntity(entity, "imagefile");
		}

		CandydollchanChanLocator locator = CandydollchanChanLocator.get(this);
		Uri uri = locator.buildPath("board.php");
		String responseText;
		try {
			new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.NONE).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
				uri = data.holder.getRedirectedUri();
				String threadNumber = locator.getThreadNumber(uri);
				return threadNumber != null ? new SendPostResult(threadNumber, null) : null;
			}
			responseText = data.holder.read().getString();
		} finally {
			data.holder.disconnect();
		}

		Matcher matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (matcher.find()) {
			String errorMessage = matcher.group(1).trim();
			int errorType = 0;
			Object extra = null;
			if (errorMessage.contains("An image, or message, is required for a reply")) {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			} else if (errorMessage.contains("A file is required for a new thread") ||
					errorMessage.contains("Please enter an embed ID")) {
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			} else if (errorMessage.contains("Invalid thread ID")) {
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			} else if (responseText.contains("Sorry, your message is too long")) {
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			}
			if (errorType != 0) {
				throw new ApiException(errorType, extra);
			}
			CommonUtils.writeLog("Candydollchan send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		CandydollchanChanLocator locator = CandydollchanChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "reportpost", "1", "reportreason",
				data.comment);
		for (String postNumber : data.postNumbers) {
			entity.add("post[]", postNumber);
		}
		Uri uri = locator.buildPath("board.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(SCHEME_REDIRECT_HANDLER).read().getString();
		if (responseText != null) {
			if (responseText.contains("Post successfully reported") ||
					responseText.contains("That post is already in the report list")) {
				// Response has message for any post
				// Ignore them, if at least 1 of them was reported
				return null;
			}
			CommonUtils.writeLog("Candydollchan report message", responseText);
		}
		throw new InvalidResponseException();
	}
}