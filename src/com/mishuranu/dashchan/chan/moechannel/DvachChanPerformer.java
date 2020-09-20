package com.mishuranu.dashchan.chan.moechannel;

import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;
import android.webkit.CookieManager;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.ThreadSummary;
import chan.http.CookieBuilder;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class DvachChanPerformer extends ChanPerformer {
	private static final String COOKIE_USERCODE_AUTH = "_ssid";
	private static final String COOKIE_PASSCODE_AUTH = "__cfduid";

	private CookieBuilder buildCookies(String captchaPassCookie) {
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		CookieBuilder builder = new CookieBuilder();
		builder.append(COOKIE_USERCODE_AUTH, configuration.getCookie(COOKIE_USERCODE_AUTH));
		builder.append(COOKIE_PASSCODE_AUTH, captchaPassCookie);
		return builder;
	}

	private CookieBuilder buildCookiesWithCaptchaPass() {
		return buildCookies(DvachChanConfiguration.get(this).getCookie(COOKIE_PASSCODE_AUTH));
	}

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		CookieManager.getInstance().setAcceptCookie(true);
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, (data.isCatalog() ? "catalog" : data.pageNumber == 0
				? "index" : Integer.toString(data.pageNumber)) + ".json");
		HttpRequest hr = new HttpRequest(uri, data).setValidator(data.validator);
		HttpResponse answer = hr.read();
		JSONObject jsonObject = answer.getJsonObject();
		if (jsonObject != null) {
			try {
				DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
				configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				Posts[] threads = null;
				if (threadsArray != null && threadsArray.length() > 0) {
					threads = new Posts[threadsArray.length()];
					for (int i = 0; i < threads.length; i++) {
						threads[i] = DvachModelMapper.createThread(threadsArray.getJSONObject(i),
								locator, data.boardName, configuration.isSageEnabled(data.boardName));
					}
				}
				int boardSpeed = jsonObject.optInt("board_speed");
				return new ReadThreadsResult(threads).setBoardSpeed(boardSpeed);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException,
			RedirectException {
		CookieManager.getInstance().setAcceptCookie(true);
		
		boolean usePartialApi = data.partialThreadLoading;
		boolean tryReadStatic = false;
		try {
			return new ReadPostsResult(onReadPosts(data, usePartialApi, false));
		} catch (HttpException e) {
			int responseCode = e.getResponseCode();
			if (responseCode >= 500 && responseCode < 600 && usePartialApi) {
				tryReadStatic = true;
			} else if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
				throw e;
			}
		}
		if (tryReadStatic) {
			try {
				return new ReadPostsResult(onReadPosts(data, false, false));
			} catch (HttpException e) {
				if (e.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
					throw e;
				}
			}
		}
		return new ReadPostsResult(onReadPosts(data, false, true)).setFullThread(true);
	}

	private Posts onReadPosts(ReadPostsData data, boolean usePartialApi, boolean archive) throws HttpException,
			InvalidResponseException, RedirectException {
		CookieManager.getInstance().setAcceptCookie(true);
		
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		Uri uri;
		HttpRequest.RedirectHandler handler = HttpRequest.RedirectHandler.BROWSER;
		uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		HttpResponse response = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
				.setValidator(data.validator).setRedirectHandler(handler).read();
		String archiveDate = null;
		JSONObject jsonObject = response.getJsonObject();
		JSONArray jsonArray = response.getJsonArray();
		if (jsonObject != null) {
			try {
				if (archiveDate != null && archiveDate.equals("wakaba")) {
					jsonArray = jsonObject.getJSONArray("thread");
					ArrayList<Post> posts = new ArrayList<>();
					for (int i = 0; i < jsonArray.length(); i++) {
						posts.add(DvachModelMapper.createWakabaArchivePost(jsonArray.getJSONArray(i)
								.getJSONObject(0), locator, data.boardName));
					}
					return new Posts(posts);
				} else {
					configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
					int uniquePosters = jsonObject.optInt("unique_posters");
					jsonArray = jsonObject.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
					return new Posts(DvachModelMapper.createPosts(jsonArray, locator, data.boardName,
							archiveDate, configuration.isSageEnabled(data.boardName)))
							.setUniquePosters(uniquePosters);
				}
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException {
		CookieManager.getInstance().setAcceptCookie(true);
		
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		Uri uri = locator.buildPath("mobile", "task", "get_post", "board", data.boardName,
				"post", data.postNumber);
		HttpResponse response = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass()).read();
		JSONObject jsonObject = response.getJsonObject();
		JSONArray jsonArray = response.getJsonArray();
		if (jsonArray != null) {
			try {
				return new ReadSinglePostResult(DvachModelMapper.createPost(jsonArray.getJSONObject(0),
						locator, data.boardName, null, configuration.isSageEnabled(data.boardName)));
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		} else if (jsonObject != null) {
			handleMobileApiError(jsonObject);
		}
		throw new InvalidResponseException();
	}

	private void handleMobileApiError(JSONObject jsonObject) throws HttpException {
		int code = Math.abs(jsonObject.optInt("Code"));
		if (code == 1 || code == HttpURLConnection.HTTP_NOT_FOUND) {
			// Board or thread not found
			throw HttpException.createNotFoundException();
		} else if (code != 0) {
			throw new HttpException(code, CommonUtils.optJsonString(jsonObject, "Error"));
		}
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException {
		CookieManager.getInstance().setAcceptCookie(true);
		
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		if (data.searchQuery.startsWith("#")) {
			Uri uri = locator.buildPath(data.boardName, "catalog.json");
			JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
					.read().getJsonObject();
			try {
				String tag = data.searchQuery.substring(1);
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				ArrayList<Post> posts = new ArrayList<>();
				if (threadsArray != null && threadsArray.length() > 0) {
					for (int i = 0; i < threadsArray.length(); i++) {
						jsonObject = threadsArray.getJSONObject(i);
						if (tag.equals(CommonUtils.optJsonString(jsonObject, "tags"))) {
							posts.add(DvachModelMapper.createPost(jsonObject, locator, data.boardName, null,
									configuration.isSageEnabled(data.boardName)));
						}
					}
				}
				return new ReadSearchPostsResult(posts);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		} else {
			Uri uri = locator.buildPath(data.boardName, "catalog.json");
			JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
					.read().getJsonObject();
			if (jsonObject != null) {
				try {
					JSONArray jsonArray = jsonObject.getJSONArray("threads");
					JSONArray result = new JSONArray();
					for(int i = 0; i < jsonArray.length(); i++){
						JSONObject jo = jsonArray.getJSONObject(i);
						if(jo.getString("comment").toLowerCase().contains(data.searchQuery.toLowerCase()) ||
						 jo.getString("subject").toLowerCase().contains(data.searchQuery.toLowerCase()))
							result.put(jo);
					}
					return new ReadSearchPostsResult(DvachModelMapper.createPosts(result,
							locator, data.boardName, null, configuration.isSageEnabled(data.boardName)));
				} catch (JSONException e) {
					throw new InvalidResponseException(e);
				}
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		CookieManager.getInstance().setAcceptCookie(true);
		
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.buildPath("boards.json");
		JSONArray jsonArray = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
				.read().getJsonArray();
		if (jsonArray != null) {
			try {
				ArrayList<String> categories = new ArrayList<>();
				HashMap<String, ArrayList<Board>> boardsMap = new HashMap<>();
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					String category = CommonUtils.getJsonString(jsonObject, "group_name");
					String boardName = CommonUtils.getJsonString(jsonObject, "board");
					String title = CommonUtils.getJsonString(jsonObject, "board_name");
					String description = CommonUtils.optJsonString(jsonObject, "board_subtitle");
					description = configuration.transformBoardDescription(description);
					ArrayList<Board> boards = boardsMap.get(category);
					if (boards == null) {
						boards = new ArrayList<>();
						boards.add(new Board(boardName, title, description));
						boardsMap.put(category, boards);
						categories.add(category);
					}else
						boards.add(new Board(boardName, title, description));
				}
				configuration.updateFromBoardsJson(jsonArray);
				ArrayList<BoardCategory> boardCategories = new ArrayList<>();
				for(String c : categories)
					for (HashMap.Entry<String, ArrayList<Board>> entry : boardsMap.entrySet())
						if(c.equals(entry.getKey())){
							ArrayList<Board> boards = entry.getValue();
							Collections.sort(boards);
							boardCategories.add(new BoardCategory(c, boards));
							break;
						}
				return new ReadBoardsResult(boardCategories);
			} catch (Exception e) {
				e.printStackTrace();
				throw new InvalidResponseException(e);
			}
		}
			throw new InvalidResponseException();
		}

	@Override
	public ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws HttpException,
			InvalidResponseException {
		CookieManager.getInstance().setAcceptCookie(true);
		
		if (data.type == ReadThreadSummariesData.TYPE_ARCHIVED_THREADS) {
			DvachChanLocator locator = DvachChanLocator.get(this);
			Uri uri = locator.buildPath(data.boardName, "arch", "index.json");
			JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
			if (jsonObject == null) {
				throw new InvalidResponseException();
			}
			int pagesCount;
			try {
				pagesCount = jsonObject.getJSONArray("pages").length();
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
			if (data.pageNumber > 0) {
				if (data.pageNumber > pagesCount) {
					return new ReadThreadSummariesResult();
				}
				uri = locator.buildPath(data.boardName, "arch", (pagesCount - data.pageNumber) + ".json");
				jsonObject = new HttpRequest(uri, data).read().getJsonObject();
				if (jsonObject == null) {
					throw new InvalidResponseException();
				}
			}
			try {
				ArrayList<ThreadSummary> threadSummaries = new ArrayList<>();
				JSONArray jsonArray = jsonObject.getJSONArray("threads");
				for (int j = jsonArray.length() - 1; j >= 0; j--) {
					jsonObject = jsonArray.getJSONObject(j);
					String threadNumber = CommonUtils.getJsonString(jsonObject, "num");
					String subject = StringUtils.clearHtml(CommonUtils.getJsonString(jsonObject, "subject")).trim();
					if ("Нет темы".equals(subject)) {
						subject = "#" + threadNumber;
					}
					threadSummaries.add(new ThreadSummary(data.boardName, threadNumber, subject));
				}
				return new ReadThreadSummariesResult(threadSummaries);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		} else {
			return super.onReadThreadSummaries(data);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		try {
			DvachChanLocator locator = DvachChanLocator.get(this);
			Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
			JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
					.read().getJsonObject();
			if (jsonObject != null) {
				if (jsonObject.has("counter_posts")) {
					return new ReadPostsCountResult(jsonObject.optInt("counter_posts"));
				} else {
					throw HttpException.createNotFoundException();
				}
			}

		}catch (Exception e){
			e.printStackTrace();
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException {
		return new ReadContentResult(new HttpRequest(data.uri, data).addCookie(buildCookiesWithCaptchaPass()).read());
	}

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		try {
			CookieManager.getInstance().setAcceptCookie(true);
			DvachChanLocator locator = DvachChanLocator.get(this);
			DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
			Uri.Builder uriBuilder = locator.buildPath("api", "captcha", "service_id").buildUpon();
			uriBuilder.appendQueryParameter("board", data.boardName);
			if (data.threadNumber != null) {
				uriBuilder.appendQueryParameter("thread", data.threadNumber);
			}
			Uri uri = uriBuilder.build();
			JSONObject jsonObject = null;
			HttpException exception = null;
			try {
				HttpRequest hr = new HttpRequest(uri, data);
				HttpResponse answer = hr.addCookie(buildCookiesWithCaptchaPass()).read();
				jsonObject = answer.getJsonObject();
			} catch (HttpException e) {
				if (!e.isHttpException()) {
					throw e;
				}
				exception = e;
			}
			CookieManager.getInstance().setAcceptCookie(true);
			String coc1 = data.holder.getCookieValue(COOKIE_USERCODE_AUTH);
			String coc2 = data.holder.getCookieValue(COOKIE_PASSCODE_AUTH);
			if (!StringUtils.isEmpty(coc1)) {
				configuration = DvachChanConfiguration.get(this);
				configuration.storeCookie(COOKIE_USERCODE_AUTH, coc1, "_ssid");
			}
			if (!StringUtils.isEmpty(coc2)) {
				configuration = DvachChanConfiguration.get(this);
				configuration.storeCookie(COOKIE_PASSCODE_AUTH, coc2, "__cfduid");
			}
			String apiResult = jsonObject != null ? CommonUtils.optJsonString(jsonObject, "result") : null;
			if ("2".equals(apiResult)) {
				configuration.setMaxFilesCountEnabled(false);
				return new  ReadCaptchaResult(CaptchaState.CAPTCHA, new CaptchaData());
			} else {
				configuration.setMaxFilesCountEnabled(false);
				String id = jsonObject != null ? CommonUtils.optJsonString(jsonObject, "id") : null;
				if (id != null) {
					uri = locator.buildPath("api", "captcha", "image", id);
					CaptchaData captchaData = new CaptchaData();
					ReadCaptchaResult result;
					result = new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
					captchaData.put(CaptchaData.CHALLENGE, id);
					HttpRequest hr = new HttpRequest(uri, data);
					HttpResponse answer = hr.addCookie(buildCookiesWithCaptchaPass()).read();
					Bitmap image = answer.getBitmap();
					if (image == null) {
						throw new InvalidResponseException();
					}
					int width = image.getWidth();
					int height = image.getHeight();
					int[] pixels = new int[width * height];
					image.getPixels(pixels, 0, width, 0, 0, width, height);
					image.recycle();

					SparseIntArray colorCounts = new SparseIntArray();
					for (int i = 0; i < width; i++) {
						int c1 = pixels[i] & 0x00ffffff;
						int c2 = pixels[width * (height - 1) + i] & 0x00ffffff;
						colorCounts.put(c1, colorCounts.get(c1) + 1);
						colorCounts.put(c2, colorCounts.get(c2) + 1);
					}
					for (int i = 1; i < height - 1; i++) {
						int c1 = pixels[i * width] & 0x00ffffff;
						int c2 = pixels[i * (width + 1) - 1] & 0x00ffffff;
						colorCounts.put(c1, colorCounts.get(c1) + 1);
						colorCounts.put(c2, colorCounts.get(c2) + 1);
					}
					int backgroundColor = 0;
					int backgroundColorCount = -1;
					for (int i = 0; i < colorCounts.size(); i++) {
						int color = colorCounts.keyAt(i);
						int count = colorCounts.get(color);
						if (count > backgroundColorCount) {
							backgroundColor = color;
							backgroundColorCount = count;
						}
					}

					for (int j = 0; j < height; j++) {
						for (int i = 0; i < width; i++) {
							int color = pixels[j * width + i] & 0x00ffffff;
							if (color == backgroundColor) {
								pixels[j * width + i] = 0xffffffff;
							} else {
								int value = (int) (Color.red(color) * 0.2126f +
										Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f);
								pixels[j * width + i] = Color.argb(0xff, value, value, value);
							}
						}
					}
					for (int i = 0; i < pixels.length; i++) {
						if (pixels[i] == 0x00000000) {
							pixels[i] = 0xffffffff;
						}
					}
					image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
					Bitmap trimmed = CommonUtils.trimBitmap(image, 0xffffffff);
					if (trimmed != null) {
						if (trimmed != image) {
							image.recycle();
						}
						image = trimmed;
					}
					result.setImage(image);
					return result;
				} else {
					if (exception != null) {
						// If wakaba is swaying, but passcode is verified, let's try to use it
						throw exception;
					}
					throw new InvalidResponseException();
				}
			}
		} catch (Exception e){
			e.printStackTrace();
			throw new InvalidResponseException();
		}
	}

	private static final Pattern PATTERN_TAG = Pattern.compile("(.*) /([^/]*)/");
	private static final Pattern PATTERN_BAN = Pattern.compile("([^ ]*?): (.*?)(?:\\.|$)");

	private static final SimpleDateFormat DATE_FORMAT_BAN;

	static {
		DateFormatSymbols symbols = new DateFormatSymbols();
		symbols.setShortMonths(new String[] {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг",
				"Сен", "Окт", "Ноя", "Дек"});
		DATE_FORMAT_BAN = new SimpleDateFormat("MMM dd HH:mm:ss yyyy", symbols);
		DATE_FORMAT_BAN.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		String subject = data.subject;
		if (data.threadNumber == null && data.subject != null) {
			Matcher matcher = PATTERN_TAG.matcher(subject);
			if (matcher.matches()) {
				subject = matcher.group(1);
			}
		}
		MultipartEntity entity = new MultipartEntity();
		entity.add("task", "post");
		entity.add("board", data.boardName);
		entity.add("thread", data.threadNumber != null ? data.threadNumber : "0");
		entity.add("subject", subject);
		entity.add("comment", data.comment);
		entity.add("name", data.name);
		entity.add("email", data.optionSage ? "sage" : data.email);
		entity.add("postpasswd", "");
		if (data.optionOriginalPoster) {
			entity.add("op_mark", "1");
		}
		if (data.attachments != null) {
			for (int i = 0; i < data.attachments.length; i++) {
				data.attachments[i].addToEntity(entity, "formimages[]");
			}
		}
		DvachChanLocator locator = DvachChanLocator.get(this);
		if (data.captchaData != null) {
			String challenge = data.captchaData.get(CaptchaData.CHALLENGE);
			String input = StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT));
			entity.add("captcha_id", challenge);
			entity.add("captcha_value", input);
		}


		Uri uri = locator.buildPath("api", "posting");
		HttpRequest hr = new HttpRequest(uri, data);
		String responseText = hr.setPostMethod(entity).addCookie(buildCookiesWithCaptchaPass()).read().getString();
		Log.d("response", responseText);
		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(responseText);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		String postNumber = CommonUtils.optJsonString(jsonObject, "Num");
		String tNumber = CommonUtils.optJsonString(jsonObject, "Target");
		String status = CommonUtils.optJsonString(jsonObject, "Status");
		if (!StringUtils.isEmpty(postNumber)) {
			return new SendPostResult(data.threadNumber, postNumber);
		} else if (!StringUtils.isEmpty(tNumber) &&
				!StringUtils.isEmpty(status))
			return new SendPostResult(tNumber, "1");

		int error = Math.abs(jsonObject.optInt("Error", Integer.MAX_VALUE));
		String reason = CommonUtils.optJsonString(jsonObject, "Reason");
		int errorType = 0;
		Object extra = null;
		switch (error) {
			case 2: {
				errorType = ApiException.SEND_ERROR_NO_BOARD;
				break;
			}
			case 3: {
				errorType = ApiException.SEND_ERROR_NO_THREAD;
				break;
			}
			case 4: {
				errorType = ApiException.SEND_ERROR_NO_ACCESS;
				break;
			}
			case 7: {
				errorType = ApiException.SEND_ERROR_CLOSED;
				break;
			}
			case 8: {
				errorType = ApiException.SEND_ERROR_TOO_FAST;
				break;
			}
			case 9: {
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
				break;
			}
			case 10: {
				errorType = ApiException.SEND_ERROR_FILE_EXISTS;
				break;
			}
			case 11: {
				errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED;
				break;
			}
			case 12: {
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
				break;
			}
			case 13: {
				errorType = ApiException.SEND_ERROR_FILES_TOO_MANY;
				break;
			}
			case 16:
			case 18: {
				errorType = ApiException.SEND_ERROR_SPAM_LIST;
				break;
			}
			case 19: {
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
				break;
			}
			case 20: {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
				break;
			}
			case 6:
			case 14:
			case 15: {
				errorType = ApiException.SEND_ERROR_BANNED;
				break;
			}
			case 5:
			case 21:
			case 22: {
				errorType = ApiException.SEND_ERROR_CAPTCHA;
				break;
			}
		}
		if (error == 6) {
			ApiException.BanExtra banExtra = new ApiException.BanExtra();
			Matcher matcher = PATTERN_BAN.matcher(reason);
			while (matcher.find()) {
				String name = matcher.group(1);
				String value = matcher.group(2);
				if ("Бан".equals(name)) {
					banExtra.setId(value);
				} else if ("Причина".equals(name)) {
					String end = " //!" + data.boardName;
					if (value.endsWith(end)) {
						value = value.substring(0, value.length() - end.length());
					}
					banExtra.setMessage(value);
				} else if ("Истекает".equals(name)) {
					int index = value.indexOf(' ');
					if (index >= 0) {
						value = value.substring(index + 1);
					}
					try {
						long date = DATE_FORMAT_BAN.parse(value).getTime();
						banExtra.setExpireDate(date);
					} catch (java.text.ParseException e) {
						// Ignore exception
					}
				}
			}
			extra = banExtra;
		}
		if (errorType != 0) {
			throw new ApiException(errorType, extra);
		}
		if (!StringUtils.isEmpty(reason)) {
			throw new ApiException(reason);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.buildPath("api", "captcha", "service_id");
		StringBuilder postsBuilder = new StringBuilder();
		for (String postNumber : data.postNumbers) {
			postsBuilder.append(postNumber).append(", ");
		}
		MultipartEntity entity = new MultipartEntity("task", "report", "board", data.boardName,
				"thread", data.threadNumber, "posts", postsBuilder.toString(), "comment", data.comment, "json", "1");
		String referer = locator.createThreadUri(data.boardName, data.threadNumber).toString();
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
				.addHeader("Referer", referer).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			String message = CommonUtils.getJsonString(jsonObject, "message");
			if (StringUtils.isEmpty(message)) {
				return null;
			}
			int errorType = 0;
			if (message.contains("Вы уже отправляли жалобу")) {
				errorType = ApiException.REPORT_ERROR_TOO_OFTEN;
			} else if (message.contains("Вы ничего не написали в жалобе")) {
				errorType = ApiException.REPORT_ERROR_EMPTY_COMMENT;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			}
			throw new ApiException(message);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}
}
