package com.mishiranu.dashchan.chan.nullone;

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
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

public class NulloneChanPerformer extends ChanPerformer {
	private static final Pattern PATTERN_CATALOG = Pattern.compile("(?s)<a class=\"catalog-entry\" href=\"(.*?)\"" +
			".*?<small>(\\d+)</small>");

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		NulloneChanLocator locator = ChanLocator.get(this);
		if (data.isCatalog()) {
			Uri uri = locator.buildPath(data.boardName, "catalog.html");
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
			uri = locator.buildQuery("expand.php", "board", data.boardName);
			responseText = new HttpRequest(uri, data.holder, data).read().getString();
			ArrayList<Post> posts;
			try {
				posts = new NullonePostsParser(responseText, this, data.boardName).convertPosts();
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
			Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			try {
				return new ReadThreadsResult(new NullonePostsParser(responseText, this, data.boardName)
						.convertThreads());
			} catch (ParseException e) {
				throw new InvalidResponseException(e);
			}
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		NulloneChanLocator locator = ChanLocator.get(this);
		String lastPostNumber = data.partialThreadLoading ? data.lastPostNumber : null;
		if (lastPostNumber != null) {
			Uri uri = locator.buildQuery("expand.php", "board", data.boardName, "threadid", data.threadNumber,
					"after", lastPostNumber);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			ArrayList<Post> posts = null;
			if (!StringUtils.isEmpty(responseText)) {
				try {
					posts = new NullonePostsParser(responseText, this, data.boardName, data.threadNumber)
							.convertPosts();
				} catch (ParseException e) {
					throw new InvalidResponseException(e);
				}
			}
			if (posts == null || posts.isEmpty()) {
				uri = locator.buildQuery("read.php", "b", data.boardName, "t", "0",
						"p", data.threadNumber, "single", "");
				responseText = new HttpRequest(uri, data.holder, data).read().getString();
				if (!responseText.contains("<div id=\"thread")) {
					throw HttpException.createNotFoundException();
				}
			}
			return new ReadPostsResult(posts);
		} else {
			Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getString();
			try {
				return new ReadPostsResult(new NullonePostsParser(responseText, this, data.boardName).convertPosts());
			} catch (ParseException e) {
				throw new InvalidResponseException(e);
			}
		}
	}

	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException {
		NulloneChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildQuery("read.php", "b", data.boardName, "t", "0", "p", data.postNumber, "single", "");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try {
			Post post = new NullonePostsParser(responseText, this, data.boardName).convertSinglePost();
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
		NulloneChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("meta", "");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try {
			return new ReadBoardsResult(new NulloneBoardsParser(responseText).convert());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		NulloneChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		int count = 0;
		int index = 0;
		while (index != -1) {
			count++;
			index = responseText.indexOf("<td class=\"reply\"", index + 1);
		}
		return new ReadPostsCountResult(count);
	}

	private static final ColorMatrixColorFilter CAPTCHA_FILTER = new ColorMatrixColorFilter(new float[]
			{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f});

	private final HashMap<String, Pair<HttpValidator, Boolean>> readCaptchaValidators = new HashMap<>();

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		NulloneChanLocator locator = ChanLocator.get(this);
		boolean needCaptcha;
		synchronized (readCaptchaValidators) {
			Pair<HttpValidator, Boolean> pair = readCaptchaValidators.get(data.boardName);
			try {
				Uri uri = locator.createBoardUri(data.boardName, 0);
				String responseText = new HttpRequest(uri, data.holder, data).setValidator(pair != null
						? pair.first : null).setSuccessOnly(false).read().getString();
				needCaptcha = responseText.contains("<span class=\"captcha_status");
				pair = new Pair<>(data.holder.getValidator(), needCaptcha);
				readCaptchaValidators.put(data.boardName, pair);
			} catch (HttpException e) {
				if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
					needCaptcha = pair.second;
				} else {
					throw e;
				}
			}
		}
		if (!needCaptcha) {
			return new ReadCaptchaResult(CaptchaState.SKIP, null);
		}
		if (data.mayShowLoadButton) {
			return new ReadCaptchaResult(CaptchaState.NEED_LOAD, null);
		}
		Uri uri = locator.buildPath("myata.php");
		Bitmap image = new HttpRequest(uri, data.holder, data).read().getBitmap();
		if (image != null) {
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

	private static final HttpRequest.RedirectHandler POST_REDIRECT_HANDLER = (responseCode, rqu, rdu, holder) ->
			responseCode == HttpURLConnection.HTTP_MOVED_PERM ? HttpRequest.RedirectHandler.Action.RETRANSMIT
			: HttpRequest.RedirectHandler.Action.CANCEL;

	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("(?s)<h2.*?>(.*?)</h2>");
	private static final Pattern PATTERN_BLACK_LIST_WORD = Pattern.compile("Blacklisted link \\( (.*) \\) detected.");
	private static final Pattern PATTERN_BAN_DATA = Pattern.compile("<strong>(.*?)</strong>");

	private static final SimpleDateFormat DATE_FORMAT_BAN = new SimpleDateFormat("MMMM d, yyyy, KK:mm a", Locale.US);

	static {
		DATE_FORMAT_BAN.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("board", data.boardName);
		entity.add("replythread", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", data.name);
		if (data.optionSage) {
			entity.add("em", "sage");
		}
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

		NulloneChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("board.php");
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
			if (errorMessage.contains("Капча введена неверно") || errorMessage.contains("Капча протухла")) {
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			} else if (errorMessage.contains("Для ответа нужна картинка, видео или сообщение")) {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			} else if (errorMessage.contains("A file is required for a new thread") ||
					errorMessage.contains("Please enter an embed ID")) {
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			} else if (errorMessage.contains("Неверный ID треда")) {
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			} else if (errorMessage.contains("Sorry, your message is too long")) {
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			} else if (errorMessage.contains("Flood Detected")) {
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			} else if (errorMessage.contains("Убедитесь, что Ваш файл меньше")) {
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			} else if (errorMessage.contains("Обнаружен дублирующий файл")) {
				errorType = ApiException.SEND_ERROR_FILE_EXISTS;
			} else if (errorMessage.contains("Этот тред закрыт")) {
				errorType = ApiException.SEND_ERROR_CLOSED;
			} else if (errorMessage.contains("Эта доска закрыта")) {
				errorType = ApiException.SEND_ERROR_NO_ACCESS;
			} else if (errorMessage.contains("Вы забанены")) {
				errorType = ApiException.SEND_ERROR_BANNED;
				matcher = PATTERN_BAN_DATA.matcher(responseText);
				ApiException.BanExtra banExtra = new ApiException.BanExtra();
				boolean startDateFound = false;
				while (matcher.find()) {
					String group = matcher.group(1);
					boolean parseSuccess = false;
					long date = 0;
					try {
						date = DATE_FORMAT_BAN.parse(group).getTime();
						parseSuccess = true;
					} catch (java.text.ParseException e) {
						// Ignore exception
					}
					if (parseSuccess || startDateFound) {
						if (startDateFound) {
							if (parseSuccess) {
								banExtra.setExpireDate(date);
							} else if (group.contains("не истечет")) {
								banExtra.setExpireDate(Long.MAX_VALUE);
							}
							extra = banExtra;
							break;
						} else {
							banExtra.setStartDate(date);
							startDateFound = true;
						}
					} else {
						banExtra.setMessage(group);
					}
				}
			} else if (errorMessage.contains("Blacklisted link")) {
				errorType = ApiException.SEND_ERROR_SPAM_LIST;
				matcher = PATTERN_BLACK_LIST_WORD.matcher(errorMessage);
				if (matcher.matches()) {
					extra = new ApiException.WordsExtra().addWord(matcher.group(1));
				}
			}
			if (errorType != 0) {
				throw new ApiException(errorType, extra);
			}
			CommonUtils.writeLog("Nullone send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		NulloneChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "deletepost", "1",
				"postpassword", data.password);
		for (String postNumber : data.postNumbers) {
			entity.add("post[]", postNumber);
		}
		if (data.optionFilesOnly) {
			entity.add("fileonly", "on");
		}
		Uri uri = locator.buildPath("board.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity).read().getString();
		if (responseText != null) {
			if (responseText.contains("Пост удален") || responseText.contains("Изображение успешно удалено") ||
					responseText.contains("Ваш пост не имеет изображения")) {
				// Response has message for any post
				// Ignore them, if at least 1 of them was deleted
				return null;
			} else if (responseText.contains("Неверный пароль")) {
				throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
			}
			CommonUtils.writeLog("Nullone delete message", responseText);
		}
		throw new InvalidResponseException();
	}
}