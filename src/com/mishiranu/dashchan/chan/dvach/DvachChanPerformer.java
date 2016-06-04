package com.mishiranu.dashchan.chan.dvach;

import java.net.HttpURLConnection;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.ThreadSummary;
import chan.content.model.Threads;
import chan.http.CookieBuilder;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class DvachChanPerformer extends ChanPerformer
{
	private static final String COOKIE_AUTH = "usercode_auth";
	private static final String COOKIE_NOCAPTCHA = "usercode_nocaptcha";
	
	private static final String[] PREFERRED_BOARDS_ORDER = {"Разное", "Тематика", "Творчество", "Политика",
		"Техника и софт", "Игры", "Японская культура", "Взрослым", "Пробное"};
	
	private CookieBuilder buildCookies(String captchaPassCookie)
	{
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		CookieBuilder builder = new CookieBuilder();
		builder.append(COOKIE_AUTH, configuration.getCookie(COOKIE_AUTH));
		builder.append(COOKIE_NOCAPTCHA, captchaPassCookie);
		return builder;
	}
	
	private CookieBuilder buildCookiesWithCaptchaPass()
	{
		return buildCookies(ChanConfiguration.get(this).getCookie(COOKIE_NOCAPTCHA));
	}
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, (data.isCatalog() ? "catalog" : data.pageNumber == 0
				? "index" : Integer.toString(data.pageNumber)) + ".json");
		JSONObject response = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.setValidator(data.validator).read().getJsonObject();
		if (response != null)
		{
			try
			{
				JSONObject jsonObject = (JSONObject) response;
				DvachChanConfiguration configuration = ChanConfiguration.get(this);
				configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				Posts[] threads = null;
				if (threadsArray != null && threadsArray.length() > 0)
				{
					threads = new Posts[threadsArray.length()];
					for (int i = 0; i < threads.length; i++)
					{
						threads[i] = DvachModelMapper.createThread(threadsArray.getJSONObject(i),
								locator, data.boardName, configuration.isSageEnabled(data.boardName));
					}
				}
				int boardSpeed = jsonObject.optInt("board_speed");
				return new ReadThreadsResult(new Threads(threads).setBoardSpeed(boardSpeed));
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		boolean usePartialApi = data.partialThreadLoading;
		boolean tryReadStatic = false;
		try
		{
			return new ReadPostsResult(onReadPosts(data, usePartialApi, false));
		}
		catch (HttpException e)
		{
			int responseCode = e.getResponseCode();
			if (responseCode >= 500 && responseCode < 600 && usePartialApi) tryReadStatic = true;
			else if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) throw e;
		}
		if (tryReadStatic)
		{
			try
			{
				return new ReadPostsResult(onReadPosts(data, false, false));
			}
			catch (HttpException e)
			{
				if (e.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) throw e;
			}
		}
		return new ReadPostsResult(onReadPosts(data, false, true)).setFullThread(true);
	}
	
	public Posts onReadPosts(ReadPostsData data, boolean usePartialApi, boolean archive) throws HttpException,
			ThreadRedirectException, InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri;
		HttpRequest.RedirectHandler handler = HttpRequest.RedirectHandler.BROWSER;
		Uri[] threadUri = null;
		if (usePartialApi)
		{
			uri = locator.createApiUri("mobile.fcgi", "task", "get_thread", "board", data.boardName,
					"thread", data.threadNumber, "num", data.lastPostNumber == null ? data.threadNumber
					: Integer.toString(Integer.parseInt(data.lastPostNumber) + 1));
		}
		else if (archive)
		{
			uri = locator.createApiUri("archive.fcgi", "board", data.boardName, "thread", data.threadNumber,
					"json", "1");
			final Uri[] finalThreadUri = {uri};
			threadUri = finalThreadUri;
			handler = new HttpRequest.RedirectHandler()
			{
				@Override
				public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri,
						HttpHolder holder) throws HttpException
				{
					finalThreadUri[0] = redirectedUri;
					return BROWSER.onRedirectReached(responseCode, requestedUri, redirectedUri, holder);
				}
			};
		}
		else
		{
			uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		}
		HttpResponse response = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.setValidator(data.validator).setRedirectHandler(handler).read();
		String archiveStartPath = null;
		if (archive)
		{
			archiveStartPath = threadUri[0].getPath();
			int index = archiveStartPath.indexOf("/res");
			if (index > 0) archiveStartPath = archiveStartPath.substring(0, index + 1);
		}
		JSONObject jsonObject = response.getJsonObject();
		JSONArray jsonArray = response.getJsonArray();
		if (usePartialApi)
		{
			if (jsonArray != null)
			{
				try
				{
					Post[] posts = DvachModelMapper.createPosts(jsonArray, locator, data.boardName, null,
							configuration.isSageEnabled(data.boardName));
					if (posts != null && posts.length == 1)
					{
						Post post = posts[0];
						String parentPostNumber = post.getParentPostNumber();
						if (parentPostNumber != null && !parentPostNumber.equals(data.threadNumber))
						{
							throw new ThreadRedirectException(parentPostNumber, post.getPostNumber());
						}
					}
					int uniquePosters = 0;
					if (posts != null) uniquePosters = jsonArray.getJSONObject(0).optInt("unique_posters");
					return posts != null ? new Posts(posts).setUniquePosters(uniquePosters) : null;
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
			}
			else if (jsonObject != null)
			{
				handleMobileApiError(jsonObject);
			}
		}
		else
		{
			if (jsonObject != null)
			{
				try
				{
					configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
					int uniquePosters = jsonObject.optInt("unique_posters");
					jsonArray = jsonObject.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
					return new Posts(DvachModelMapper.createPosts(jsonArray, locator, data.boardName, archiveStartPath,
							configuration.isSageEnabled(data.boardName))).setUniquePosters(uniquePosters);
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.createApiUri("mobile.fcgi", "task", "get_post", "board", data.boardName,
				"post", data.postNumber);
		HttpResponse response = new HttpRequest(uri, data.holder, data)
				.addCookie(buildCookiesWithCaptchaPass()).read();
		JSONObject jsonObject = response.getJsonObject();
		JSONArray jsonArray = response.getJsonArray();
		if (jsonArray != null)
		{
			try
			{
				return new ReadSinglePostResult(DvachModelMapper.createPost(jsonArray.getJSONObject(0),
						locator, data.boardName, null, configuration.isSageEnabled(data.boardName)));
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		else if (jsonObject != null)
		{
			handleMobileApiError(jsonObject);
		}
		throw new InvalidResponseException();
	}
	
	private void handleMobileApiError(JSONObject jsonObject) throws HttpException
	{
		int code = Math.abs(jsonObject.optInt("Code"));
		if (code != 0)
		{
			String error = CommonUtils.optJsonString(jsonObject, "Error");
			if (code == HttpURLConnection.HTTP_NOT_FOUND) error = "Not Found";
			if (code == 1)
			{
				// Board not found
				throw HttpException.createNotFoundException();
			}
			throw new HttpException(code, error);
		}
	}
	
	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.createApiUri("makaba.fcgi");
		MultipartEntity entity = new MultipartEntity("task", "search", "board", data.boardName,
				"find", data.searchQuery, "json", "1");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
				.read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				String errorMessage = jsonObject.optString("message");
				if (!StringUtils.isEmpty(errorMessage)) throw new HttpException(0, errorMessage);
				return new ReadSearchPostsResult(DvachModelMapper.createPosts(jsonObject.getJSONArray("posts"),
						locator, data.boardName, null, configuration.isSageEnabled(data.boardName)));
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		DvachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("boards.json");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				JSONArray jsonArray = jsonObject.getJSONArray("boards");
				HashMap<String, ArrayList<Board>> boardsMap = new HashMap<>();
				for (int i = 0; i < jsonArray.length(); i++)
				{
					jsonObject = jsonArray.getJSONObject(i);
					String category = CommonUtils.getJsonString(jsonObject, "category");
					String boardName = CommonUtils.getJsonString(jsonObject, "id");
					String title = CommonUtils.getJsonString(jsonObject, "name");
					String description = CommonUtils.optJsonString(jsonObject, "info");
					description = configuration.transformBoardDescription(description);
					ArrayList<Board> boards = boardsMap.get(category);
					if (boards == null)
					{
						boards = new ArrayList<>();
						boardsMap.put(category, boards);
					}
					boards.add(new Board(boardName, title, description));
				}
				ArrayList<BoardCategory> boardCategories = new ArrayList<>();
				for (String title : PREFERRED_BOARDS_ORDER)
				{
					for (HashMap.Entry<String, ArrayList<Board>> entry : boardsMap.entrySet())
					{
						if (title.equals(entry.getKey()))
						{
							ArrayList<Board> boards = entry.getValue();
							Collections.sort(boards);
							boardCategories.add(new BoardCategory(title, boards));
							break;
						}
					}
				}
				configuration.updateFromBoardsJson(jsonArray);
				return new ReadBoardsResult(boardCategories);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException, InvalidResponseException
	{
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		DvachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("userboards.json");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				ArrayList<Board> boards = new ArrayList<>();
				JSONArray jsonArray = jsonObject.getJSONArray("boards");
				for (int i = 0; i < jsonArray.length(); i++)
				{
					JSONObject boardObject = jsonArray.getJSONObject(i);
					String boardName = CommonUtils.getJsonString(boardObject, "id");
					String title = CommonUtils.getJsonString(boardObject, "name");
					String description = CommonUtils.optJsonString(boardObject, "info");
					description = configuration.transformBoardDescription(description);
					boards.add(new Board(boardName, title, description));
				}
				return new ReadUserBoardsResult(boards);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws HttpException,
			InvalidResponseException
	{
		if (data.type == ReadThreadSummariesData.TYPE_ARCHIVED_THREADS)
		{
			ArrayList<ThreadSummary> threadSummaries = new ArrayList<>();
			DvachChanLocator locator = ChanLocator.get(this);
			for (int i = -1; i < 10; i++)
			{
				Uri uri = locator.buildPath(data.boardName, "arch", (i >= 0 ? i : "index") + ".json");
				JSONObject jsonObject = null;
				try
				{
					jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
				}
				catch (HttpException e)
				{
					if ((e.isHttpException() || e.isSocketException()) && !threadSummaries.isEmpty()) break;
					throw e;
				}
				if (jsonObject == null) throw new InvalidResponseException();
				try
				{
					JSONArray jsonArray = jsonObject.getJSONArray("threads");
					for (int j = 0; j < jsonArray.length(); j++)
					{
						jsonObject = jsonArray.getJSONObject(j);
						String threadNumber = CommonUtils.getJsonString(jsonObject, "num");
						String subject = StringUtils.clearHtml(CommonUtils.getJsonString(jsonObject, "subject")).trim();
						if ("Нет темы".equals(subject)) subject = "#" + threadNumber;
						threadSummaries.add(new ThreadSummary(data.boardName, threadNumber, subject));
					}
				}
				catch (JSONException e)
				{
					
				}
			}
			return new ReadThreadSummariesResult(threadSummaries);
		}
		else if (data.type == ReadThreadSummariesData.TYPE_POPULAR_THREADS)
		{
			DvachChanLocator locator = ChanLocator.get(this);
			Uri uri = locator.buildPath("popular.json");
			JSONObject jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
			if (jsonObject != null)
			{
				try
				{
					JSONArray jsonArray = (jsonObject).getJSONArray("threads");
					ArrayList<ThreadSummary> threadSummaries = new ArrayList<>();
					for (int i = 0; i < jsonArray.length(); i++)
					{
						jsonObject = jsonArray.optJSONObject(i);
						// Sometimes objects can be null
						if (jsonObject != null)
						{
							String boardName = CommonUtils.getJsonString(jsonObject, "board");
							String threadNumber = CommonUtils.getJsonString(jsonObject, "num");
							String description = StringUtils.clearHtml(DvachModelMapper
									.fixApiEscapeCharacters(CommonUtils.getJsonString(jsonObject, "subject")));
							ThreadSummary threadSummary = new ThreadSummary(boardName, threadNumber, description);
							threadSummary.setPostsCount(jsonObject.getInt("posts") + 1);
							threadSummary.setViewsCount(jsonObject.getInt("views"));
							String thumbnail = CommonUtils.optJsonString(jsonObject, "thumbnail");
							if (thumbnail != null)
							{
								threadSummary.setThumbnailUri(locator, locator.buildPath(boardName, thumbnail));
							}
							threadSummaries.add(threadSummary);
						}
					}
					return new ReadThreadSummariesResult(threadSummaries);
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
			}
			throw new InvalidResponseException();
		}
		else return super.onReadThreadSummaries(data);
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("mobile.fcgi", "task", "get_thread_last_info", "board",
				data.boardName, "thread", data.threadNumber);
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.read().getJsonObject();
		if (jsonObject != null)
		{
			if (jsonObject.has("posts")) return new ReadPostsCountResult(jsonObject.optInt("posts") + 1);
			else throw HttpException.createNotFoundException();
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException
	{
		Uri uri = data.uri;
		DvachChanLocator locator = ChanLocator.get(this);
		if (StringUtils.isEmpty(uri.getQuery()) && locator.isAttachmentUri(uri)
				&& locator.isImageExtension(uri.getPath()))
		{
			uri = uri.buildUpon().encodedQuery("image=" + System.currentTimeMillis()).build();
		}
		return new ReadContentResult(new HttpRequest(uri, data.holder, data)
				.addCookie(buildCookiesWithCaptchaPass()).read());
	}
	
	@Override
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
			InvalidResponseException
	{
		return new CheckAuthorizationResult(readCaptchaPass(data.holder, data, data.authorizationData[0]) != null);
	}
	
	private String mLastCaptchaPassData;
	private String mLastCaptchaPassCookie;
	
	private String readCaptchaPass(HttpHolder holder, HttpRequest.Preset preset, String captchaPassData)
			throws HttpException, InvalidResponseException
	{
		mLastCaptchaPassData = null;
		mLastCaptchaPassCookie = null;
		DvachChanConfiguration configuration = ChanConfiguration.get(this);
		configuration.storeCookie(COOKIE_NOCAPTCHA, null, null);
		DvachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("makaba.fcgi");
		UrlEncodedEntity entity = new UrlEncodedEntity("task", "auth", "usercode", captchaPassData, "json", "1");
		JSONObject jsonObject = new HttpRequest(uri, holder, preset).addCookie(buildCookies(null))
				.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				String captchaPassCookie = StringUtils.nullIfEmpty(CommonUtils.getJsonString(jsonObject, "Hash"));
				mLastCaptchaPassData = captchaPassData;
				mLastCaptchaPassCookie = captchaPassCookie;
				if (captchaPassCookie != null)
				{
					configuration.storeCookie(COOKIE_NOCAPTCHA, captchaPassCookie, "Usercode No Captcha");
				}
				return captchaPassCookie;
			}
			catch (JSONException e)
			{
				String message = CommonUtils.optJsonString(jsonObject, "message");
				if (!StringUtils.isEmpty(message) && !message.contains("не существует"))
				{
					throw new HttpException(0, message);
				}
				return null;
			}
		}
		throw new InvalidResponseException();
	}
	
	private static final String CAPTCHA_PASS_COOKIE = "captchaPassCookie";
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		return onReadCaptcha(data.holder, data, data.captchaType, data.captchaPass != null ? data.captchaPass[0] : null,
				data.threadNumber == null, true);
	}
	
	private ReadCaptchaResult makeCaptchaPassResult(String captchaPassCookie)
	{
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CAPTCHA_PASS_COOKIE, captchaPassCookie);
		return new ReadCaptchaResult(CaptchaState.PASS, captchaData)
				.setValidity(ChanConfiguration.Captcha.Validity.LONG_LIFETIME);
	}
	
	private ReadCaptchaResult onReadCaptcha(HttpHolder holder, HttpRequest.Preset preset, String captchaType,
			String captchaPassData, boolean newThread, boolean mayUseLastCaptchaPassCookie) throws HttpException,
			InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		String captchaPassCookie = null;
		boolean mayRelogin = false;
		if (captchaPassData != null)
		{
			if (mayUseLastCaptchaPassCookie && captchaPassData.equals(mLastCaptchaPassData))
			{
				captchaPassCookie = mLastCaptchaPassCookie;
				mayRelogin = true;
			}
			else captchaPassCookie = readCaptchaPass(holder, preset, captchaPassData);
		}
		Uri uri = locator.createApiUri("captcha.fcgi", "type", "2chaptcha");
		if (!newThread) uri = uri.buildUpon().appendQueryParameter("action", "thread").build();
		String responseText;
		HttpException exception = null;
		try
		{
			responseText = new HttpRequest(uri, holder, preset).addCookie(buildCookies(captchaPassCookie))
					.read().getString();
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == 0) throw e;
			responseText = null;
			exception = e;
		}
		if (responseText != null)
		{
			if (responseText.equals("OK"))
			{
				return new ReadCaptchaResult(CaptchaState.SKIP, null)
						.setValidity(ChanConfiguration.Captcha.Validity.LONG_LIFETIME);
			}
			else if (responseText.equals("DISABLED"))
			{
				return new ReadCaptchaResult(CaptchaState.SKIP, null);
			}
			else if (responseText.startsWith("CHECK"))
			{
				String keyOrChallenge = StringUtils.nullIfEmpty(responseText.substring(responseText.indexOf('\n') + 1));
				if (keyOrChallenge == null) throw new InvalidResponseException();
				CaptchaData captchaData = new CaptchaData();
				captchaData.put(CaptchaData.CHALLENGE, keyOrChallenge);
				ReadCaptchaResult result = new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
				uri = locator.createApiUri("captcha.fcgi", "type", "2chaptcha", "action", "image",
						"id", keyOrChallenge);
				Bitmap image = new HttpRequest(uri, holder, preset).read().getBitmap();
				if (image == null) throw new InvalidResponseException();
				Bitmap editable = image.copy(Bitmap.Config.ARGB_8888, true);
				image.recycle();
				if (editable == null) throw new RuntimeException();
				int[] pixels = new int[9];
				int center = pixels.length / 2;
				for (int i = 1; i < editable.getWidth() - 1; i++)
				{
					for (int j = 1; j < editable.getHeight() - 1; j++)
					{
						editable.getPixels(pixels, 0, 3, i - 1, j - 1, 3, 3);
						if (pixels[center] != 0xffffffff)
						{
							int count = 0;
							for (int k = 0; k < pixels.length; k++)
							{
								if (pixels[k] != 0xffffffff) count++;
							}
							if (count < 5) editable.setPixel(i, j, 0x00000000);
						}
					}
				}
				image = Bitmap.createBitmap((int) (editable.getWidth() * 1.5f) - 2, editable.getHeight(),
						Bitmap.Config.ARGB_8888);
				Rect src = new Rect(1, 1, editable.getWidth() - 2, editable.getHeight() - 2);
				Rect dst = new Rect(0, 0, image.getWidth(), image.getHeight());
				Canvas canvas = new Canvas(image);
				canvas.drawColor(0xffffffff);
				canvas.drawBitmap(editable, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
				editable.recycle();
				result.setImage(image);
				return result;
			}
			else if (responseText.equals("VIP"))
			{
				CaptchaData captchaData = new CaptchaData();
				if (captchaPassCookie != null) captchaData.put(CAPTCHA_PASS_COOKIE, captchaPassCookie);
				return new ReadCaptchaResult(CaptchaState.PASS, captchaData)
						.setValidity(ChanConfiguration.Captcha.Validity.LONG_LIFETIME);
			}
			else if (responseText.equals("VIPFAIL"))
			{
				return onReadCaptcha(holder, preset, captchaType, mayRelogin ? captchaPassData : null,
						newThread, false);
			}
		}
		// If wakaba is swaying, but passcode is verified, let's try to use it
		if (captchaPassCookie != null) return makeCaptchaPassResult(captchaPassCookie);
		if (exception != null) throw exception;
		throw new InvalidResponseException();
	}
	
	private static final Pattern PATTERN_TAG = Pattern.compile("(.*) /([^/]*)/");
	private static final Pattern PATTERN_BAN = Pattern.compile("([^ ]*?): (.*?)(?:\\.|$)");
	
	static final SimpleDateFormat DATE_FORMAT_BAN;
	
	static
	{
		DateFormatSymbols symbols = new DateFormatSymbols();
		symbols.setShortMonths(new String[] {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг",
				"Сен", "Окт", "Ноя", "Дек"});
		DATE_FORMAT_BAN = new SimpleDateFormat("MMM dd HH:mm:ss yyyy", symbols);
		DATE_FORMAT_BAN.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		String subject = data.subject;
		String tag = null;
		if (data.threadNumber == null && data.subject != null)
		{
			Matcher matcher = PATTERN_TAG.matcher(subject);
			if (matcher.matches())
			{
				subject = matcher.group(1);
				tag = matcher.group(2);
			}
		}
		MultipartEntity entity = new MultipartEntity();
		entity.add("task", "post");
		entity.add("board", data.boardName);
		entity.add("thread", data.threadNumber != null ? data.threadNumber : "0");
		entity.add("subject", subject);
		entity.add("tags", tag);
		entity.add("comment", data.comment);
		entity.add("name", data.name);
		entity.add("email", data.optionSage ? "sage" : data.email);
		if (data.optionOriginalPoster) entity.add("op_mark", "1");
		if (data.attachments != null)
		{
			for (int i = 0; i < data.attachments.length; i++)
			{
				data.attachments[i].addToEntity(entity, "image" + (i + 1));
			}
		}
		entity.add("icon", data.userIcon);
		String captchaPassCookie = null;
		
		DvachChanLocator locator = ChanLocator.get(this);
		if (data.captchaData != null)
		{
			entity.add("captcha_type", "2chaptcha");
			entity.add("2chaptcha_id", data.captchaData.get(CaptchaData.CHALLENGE));
			entity.add("2chaptcha_value", StringUtils.nullIfEmpty(data.captchaData.get(CaptchaData.INPUT)));
			captchaPassCookie = data.captchaData.get(CAPTCHA_PASS_COOKIE);
		}
		
		Uri uri = locator.createApiUri("posting.fcgi", "json", "1");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.addCookie(buildCookies(captchaPassCookie)).setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
				.read().getString();
		
		JSONObject jsonObject;
		try
		{
			jsonObject = new JSONObject(responseText);
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
		String auth = data.holder.getCookieValue(COOKIE_AUTH);
		if (!StringUtils.isEmpty(auth))
		{
			DvachChanConfiguration configuration = ChanConfiguration.get(this);
			configuration.storeCookie(COOKIE_AUTH, auth, "Usercode Auth");
		}
		String postNumber = CommonUtils.optJsonString(jsonObject, "Num");
		if (!StringUtils.isEmpty(postNumber)) return new SendPostResult(data.threadNumber, postNumber);
		String threadNumber = CommonUtils.optJsonString(jsonObject, "Target");
		if (!StringUtils.isEmpty(threadNumber)) return new SendPostResult(threadNumber, null);
		int error = Math.abs(jsonObject.optInt("Error", Integer.MAX_VALUE));
		String reason = CommonUtils.optJsonString(jsonObject, "Reason");
		int errorType = 0;
		Object extra = null;
		switch (error)
		{
			case 2: errorType = ApiException.SEND_ERROR_NO_BOARD; break;
			case 3: errorType = ApiException.SEND_ERROR_NO_THREAD; break;
			case 4: errorType = ApiException.SEND_ERROR_NO_ACCESS; break;
			case 7: errorType = ApiException.SEND_ERROR_CLOSED; break;
			case 8: errorType = ApiException.SEND_ERROR_TOO_FAST; break;
			case 9: errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG; break;
			case 10: errorType = ApiException.SEND_ERROR_FILE_EXISTS; break;
			case 11: errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED; break;
			case 12: errorType = ApiException.SEND_ERROR_FILE_TOO_BIG; break;
			case 13: errorType = ApiException.SEND_ERROR_FILES_TOO_MANY; break;
			case 16:
			case 18: errorType = ApiException.SEND_ERROR_SPAM_LIST; break;
			case 19: errorType = ApiException.SEND_ERROR_EMPTY_FILE; break;
			case 20: errorType = ApiException.SEND_ERROR_EMPTY_COMMENT; break;
			case 6:
			case 14:
			case 15: errorType = ApiException.SEND_ERROR_BANNED; break;
			case 5:
			case 21:
			case 22:
			{
				errorType = ApiException.SEND_ERROR_CAPTCHA;
				break;
			}
		}
		if (error == 6)
		{
			ApiException.BanExtra banExtra = new ApiException.BanExtra();
			Matcher matcher = PATTERN_BAN.matcher(reason);
			while (matcher.find())
			{
				String name = matcher.group(1);
				String value = matcher.group(2);
				if ("Бан".equals(name)) banExtra.setId(value);
				else if ("Причина".equals(name))
				{
					String end = " //!" + data.boardName;
					if (value.endsWith(end)) value = value.substring(0, value.length() - end.length());
					banExtra.setMessage(value);
				}
				else if ("Истекает".equals(name))
				{
					int index = value.indexOf(' ');
					if (index >= 0) value = value.substring(index + 1);
					try
					{
						long date = DATE_FORMAT_BAN.parse(value).getTime();
						banExtra.setExpireDate(date);
					}
					catch (java.text.ParseException e)
					{
						
					}
				}
			}
			extra = banExtra;
		}
		if (errorType == ApiException.SEND_ERROR_CAPTCHA)
		{
			mLastCaptchaPassData = null;
			mLastCaptchaPassCookie = null;
		}
		if (errorType != 0) throw new ApiException(errorType, extra);
		if (!StringUtils.isEmpty(reason)) throw new ApiException(reason);
		throw new InvalidResponseException();
	}
	
	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		DvachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("makaba.fcgi");
		StringBuilder postsBuilder = new StringBuilder();
		for (String postNumber : data.postNumbers) postsBuilder.append(postNumber).append(", ");
		MultipartEntity entity = new MultipartEntity("task", "report", "board", data.boardName,
				"thread", data.threadNumber, "posts", postsBuilder.toString(), "comment", data.comment, "json", "1");
		String referer = locator.createThreadUri(data.boardName, data.threadNumber).toString();
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).addCookie(buildCookiesWithCaptchaPass())
				.addHeader("Referer", referer).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		try
		{
			String message = CommonUtils.getJsonString(jsonObject, "message");
			if (StringUtils.isEmpty(message)) return null;
			int errorType = 0;
			if (message.contains("Вы уже отправляли жалобу"))
			{
				errorType = ApiException.REPORT_ERROR_TOO_OFTEN;
			}
			else if (message.contains("Вы ничего не написали в жалобе"))
			{
				errorType = ApiException.REPORT_ERROR_EMPTY_COMMENT;
			}
			if (errorType != 0) throw new ApiException(errorType);
			throw new ApiException(message);
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
}