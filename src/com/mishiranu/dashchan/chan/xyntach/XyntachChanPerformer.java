package com.mishiranu.dashchan.chan.xyntach;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.util.Pair;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class XyntachChanPerformer extends ChanPerformer {
	private static final Pattern PATTERN_CATALOG = Pattern.compile("(?s)<td valign=\"middle\">.*?" +
			"<a href=\"(.*?)\">.*?</a>.*?<small>(\\d+)</small>.*?</td>");

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		if (data.isCatalog()) {
			Uri uri = locator.buildPath(locator.convertInternalBoardName(data.boardName), "catalog.html");
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			ArrayList<Pair<String, Integer>> threadInfos = new ArrayList<>();
			Matcher matcher = PATTERN_CATALOG.matcher(responseText);
			while (matcher.find()) {
				String path = matcher.group(1);
				uri = locator.buildPath(path);
				String threadNumber = locator.getThreadNumber(uri);
				if (threadNumber != null) {
					int replies = Integer.parseInt(matcher.group(2));
					threadInfos.add(new Pair<>(threadNumber, replies));
				}
			}
			if (threadInfos.isEmpty()) {
				return null;
			}
			String[] splittedBoardName = locator.splitInternalBoardName(data.boardName);
			uri = locator.buildQuery(splittedBoardName[0] != null ? splittedBoardName[0] + "/expand.php" : "expand.php",
					"board", splittedBoardName[1]);
			responseText = new HttpRequest(uri, data.holder, data).read().getString();
			ArrayList<Post> posts;
			try {
				posts = new XyntachPostsParser(responseText, this, data.boardName).convertPosts();
				if (posts == null || posts.isEmpty()) {
					return null;
				}
			} catch (ParseException e) {
				throw new InvalidResponseException(e);
			}
			HashMap<String, Post> postsMap = new HashMap<>();
			for (Post post : posts) {
				if (post.getParentPostNumber() == null) {
					postsMap.put(post.getPostNumber(), post);
				}
			}
			ArrayList<Posts> threads = new ArrayList<>();
			for (Pair<String, Integer> threadInfo : threadInfos) {
				Post post = postsMap.get(threadInfo.first);
				if (post != null) {
					Posts thread = new Posts(post);
					thread.addPostsCount(1 + threadInfo.second);
					threads.add(thread);
				}
			}
			return new ReadThreadsResult(threads);
		} else {
			Uri uri = locator.createBoardUri(locator.convertInternalBoardName(data.boardName), data.pageNumber);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			try {
				return new ReadThreadsResult(new XyntachPostsParser(responseText, this, data.boardName)
						.convertThreads());
			} catch (ParseException e) {
				throw new InvalidResponseException(e);
			}
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		Uri uri = locator.createThreadUri(locator.convertInternalBoardName(data.boardName), data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try {
			return new ReadPostsResult(new XyntachPostsParser(responseText, this, data.boardName).convertPosts());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		String[] splittedBoardName = locator.splitInternalBoardName(data.boardName);
		Uri uri = locator.buildQuery(splittedBoardName[0] != null ? splittedBoardName[0] + "/read.php" : "read.php",
				"b", splittedBoardName[1], "t", "0", "p", data.postNumber, "single", "");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try {
			Post post = new XyntachPostsParser(responseText, this, data.boardName).convertSinglePost();
			if (post == null) {
				throw HttpException.createNotFoundException();
			}
			return new ReadSinglePostResult(post);
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		Uri uri = locator.buildPath("menu.php");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		ArrayList<BoardCategory> categories = new ArrayList<>();
		try {
			categories.add(new BoardCategory("Xynta.ch", new XyntachBoardsParser(responseText).convert(false)));
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
		uri = locator.buildPath("d", "menu.php");
		responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try {
			categories.add(new BoardCategory("Depressarium", new XyntachBoardsParser(responseText).convert(true)));
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
		return new ReadBoardsResult(categories);
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		Uri uri = locator.createThreadUri(locator.convertInternalBoardName(data.boardName), data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		if (!responseText.contains("<form name=\"postform\"")) {
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

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		String[] splittedBoardName = locator.splitInternalBoardName(data.boardName);
		Uri uri = locator.buildPath(splittedBoardName[0], "captcha.php");
		Bitmap image = new HttpRequest(uri, data.holder, data).read().getBitmap();
		if (image != null) {
			int[] line = new int[image.getWidth()];
			image.getPixels(line, 0, line.length, 0, 0, line.length, 1);
			boolean inverted = false;
			for (int x = 0; x < image.getWidth(); x++) {
				int color = (Color.red(line[x]) + Color.green(line[x]) + Color.blue(line[x])) / 3;
				if (color <= 0x03) {
					inverted = true;
				}
			}
			Bitmap newImage = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
			for (int y = 0; y < image.getHeight(); y++) {
				image.getPixels(line, 0, line.length, 0, y, line.length, 1);
				for (int x = 0; x < image.getWidth(); x++) {
					int color = (Color.red(line[x]) + Color.green(line[x]) + Color.blue(line[x])) / 3;
					if (inverted) {
						color = 0xff - color;
						if (color >= 0x98) {
							color = 0xff;
						} else {
							color = color / 2;
						}
						line[x] = Color.argb(0xff, color, color, color);
					} else {
						if (color >= 0x35) {
							color = 0xff;
						}
						line[x] = Color.argb(0xff, color, color, color);
					}
				}
				newImage.setPixels(line, 0, line.length, 0, y, line.length, 1);
			}
			image.recycle();
			String sessionCookie = data.holder.getCookieValue("PHPSESSID");
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.CHALLENGE, sessionCookie);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(newImage);
		}
		throw new InvalidResponseException();
	}

	private static final HttpRequest.RedirectHandler POST_REDIRECT_HANDLER = (responseCode, rqu, rdu, holder) ->
			responseCode == HttpURLConnection.HTTP_MOVED_PERM ? HttpRequest.RedirectHandler.Action.RETRANSMIT
			: HttpRequest.RedirectHandler.Action.CANCEL;

	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("(?s)<h2.*?>(.*?)</h2>");

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		String[] splittedBoardName = locator.splitInternalBoardName(data.boardName);

		MultipartEntity entity = new MultipartEntity();
		entity.add("board", splittedBoardName[1]);
		entity.add("replythread", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("postpassword", data.password);
		entity.add("redirecttothread", "1");
		entity.add("embed", ""); // Otherwise there will be a "Please enter an embed ID" error
		if (data.attachments != null) {
			data.attachments[0].addToEntity(entity, "imagefile");
		}
		String sessionCookie = null;
		if (data.captchaData != null) {
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
			sessionCookie = data.captchaData.get(CaptchaData.CHALLENGE);
		}

		Uri uri = locator.buildPath(splittedBoardName[0], "board.php");
		String responseText;
		try {
			new HttpRequest(uri, data.holder, data).setPostMethod(entity).addCookie("PHPSESSID", sessionCookie)
					.setRedirectHandler(POST_REDIRECT_HANDLER).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
				uri = data.holder.getRedirectedUri();
				String threadNumber = locator.getThreadNumber(uri);
				return new SendPostResult(threadNumber, null);
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
			if (errorMessage.contains("негодная капча")) {
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			} else if (errorMessage.contains("хотя бы введите текст")) {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			} else if (errorMessage.contains("Неверный номер треда")) {
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			}
			if (errorType != 0) {
				throw new ApiException(errorType, extra);
			}
			CommonUtils.writeLog("Xyntach send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		String[] splittedBoardName = locator.splitInternalBoardName(data.boardName);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", splittedBoardName[1], "deletepost", "1",
				"postpassword", data.password);
		for (String postNumber : data.postNumbers) {
			entity.add("post[]", postNumber);
		}
		if (data.optionFilesOnly) {
			entity.add("fileonly", "on");
		}
		Uri uri = locator.buildPath(splittedBoardName[0], "board.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity).read().getString();
		if (responseText != null) {
			if (responseText.contains("удалено") || responseText.contains("нет изображений")) {
				// Response has message for any post
				// Ignore them, if at least 1 of them was deleted
				return null;
			} else if (responseText.contains("пароль не подходит")) {
				throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
			}
			CommonUtils.writeLog("Xyntach delete message", responseText);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		XyntachChanLocator locator = XyntachChanLocator.get(this);
		String[] splittedBoardName = locator.splitInternalBoardName(data.boardName);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", splittedBoardName[1], "reportpost", "1");
		for (String postNumber : data.postNumbers) {
			entity.add("post[]", postNumber);
		}
		Uri uri = locator.buildPath(splittedBoardName[0], "board.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity).read().getString();
		if (responseText != null) {
			if (responseText.contains("Post successfully reported") ||
					responseText.contains("That post is already in the report list")) {
				// Response has message for any post
				// Ignore them, if at least 1 of them was reported
				return null;
			}
			CommonUtils.writeLog("Xyntach report message", responseText);
		}
		throw new InvalidResponseException();
	}
}