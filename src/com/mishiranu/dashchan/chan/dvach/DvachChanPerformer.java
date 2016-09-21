package com.mishiranu.dashchan.chan.dvach;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
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
public class DvachChanPerformer extends ChanPerformer
{
	private static final String COOKIE_AUTH = "usercode_auth";
	private static final String COOKIE_NOCAPTCHA = "usercode_nocaptcha";

	private static final String[] PREFERRED_BOARDS_ORDER = {"Разное", "Тематика", "Творчество", "Политика",
		"Техника и софт", "Игры", "Японская культура", "Взрослым", "Пробное"};

	private CookieBuilder buildCookies(String captchaPassCookie)
	{
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		CookieBuilder builder = new CookieBuilder();
		builder.append(COOKIE_AUTH, configuration.getCookie(COOKIE_AUTH));
		builder.append(COOKIE_NOCAPTCHA, captchaPassCookie);
		return builder;
	}

	private CookieBuilder buildCookiesWithCaptchaPass()
	{
		return buildCookies(DvachChanConfiguration.get(this).getCookie(COOKIE_NOCAPTCHA));
	}

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, (data.isCatalog() ? "catalog" : data.pageNumber == 0
				? "index" : Integer.toString(data.pageNumber)) + ".json");
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
				.setValidator(data.validator).read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
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
				return new ReadThreadsResult(threads).setBoardSpeed(boardSpeed);
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

	private Posts onReadPosts(ReadPostsData data, boolean usePartialApi, boolean archive) throws HttpException,
			ThreadRedirectException, InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		Uri uri;
		HttpRequest.RedirectHandler handler = HttpRequest.RedirectHandler.BROWSER;
		Uri[] threadUri = null;
		if (usePartialApi)
		{
			uri = locator.createFcgiUri("mobile", "task", "get_thread", "board", data.boardName,
					"thread", data.threadNumber, "num", data.lastPostNumber == null ? data.threadNumber
					: Integer.toString(Integer.parseInt(data.lastPostNumber) + 1));
		}
		else if (archive)
		{
			uri = locator.buildPath(data.boardName, "arch", "res", data.threadNumber + ".json");
			Uri[] finalThreadUri = {uri};
			threadUri = finalThreadUri;
			handler = (responseCode, requestedUri, redirectedUri, holder) ->
			{
				finalThreadUri[0] = redirectedUri;
				return HttpRequest.RedirectHandler.BROWSER.onRedirectReached(responseCode,
						requestedUri, redirectedUri, holder);
			};
		}
		else uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		HttpResponse response = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
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
					if (archiveStartPath != null && archiveStartPath.endsWith("/wakaba/"))
					{
						jsonArray = jsonObject.getJSONArray("thread");
						ArrayList<Post> posts = new ArrayList<>();
						for (int i = 0; i < jsonArray.length(); i++)
						{
							posts.add(DvachModelMapper.createWakabaArchivePost(jsonArray.getJSONArray(i)
									.getJSONObject(0), locator, data.boardName));
						}
						return new Posts(posts);
					}
					else
					{
						configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
						int uniquePosters = jsonObject.optInt("unique_posters");
						jsonArray = jsonObject.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
						return new Posts(DvachModelMapper.createPosts(jsonArray, locator, data.boardName,
								archiveStartPath, configuration.isSageEnabled(data.boardName)))
								.setUniquePosters(uniquePosters);
					}
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
			}
		}
		if (archive)
		{
			String responseText = response.getString();
			if (responseText.contains("Доска не существует")) throw HttpException.createNotFoundException();
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		Uri uri = locator.createFcgiUri("mobile", "task", "get_post", "board", data.boardName,
				"post", data.postNumber);
		HttpResponse response = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass()).read();
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
		if (code == 1 || code == HttpURLConnection.HTTP_NOT_FOUND)
		{
			// Board or thread not found
			throw HttpException.createNotFoundException();
		}
		else if (code != 0) throw new HttpException(code, CommonUtils.optJsonString(jsonObject, "Error"));
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		Uri uri = locator.createFcgiUri("makaba");
		MultipartEntity entity = new MultipartEntity("task", "search", "board", data.boardName,
				"find", data.searchQuery, "json", "1");
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
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
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.buildPath("boards.json");
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
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
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.buildPath("userboards.json");
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
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
			DvachChanLocator locator = DvachChanLocator.get(this);
			Uri uri = locator.buildPath(data.boardName, "arch", "index.json");
			JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
			if (jsonObject == null) throw new InvalidResponseException();
			int pagesCount;
			try
			{
				pagesCount = jsonObject.getJSONArray("pages").length();
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
			if (data.pageNumber > 0)
			{
				if (data.pageNumber > pagesCount) return new ReadThreadSummariesResult();
				uri = locator.buildPath(data.boardName, "arch", (pagesCount - data.pageNumber) + ".json");
				jsonObject = new HttpRequest(uri, data).read().getJsonObject();
				if (jsonObject == null) throw new InvalidResponseException();
			}
			try
			{
				ArrayList<ThreadSummary> threadSummaries = new ArrayList<>();
				JSONArray jsonArray = jsonObject.getJSONArray("threads");
				for (int j = jsonArray.length() - 1; j >= 0; j--)
				{
					jsonObject = jsonArray.getJSONObject(j);
					String threadNumber = CommonUtils.getJsonString(jsonObject, "num");
					String subject = StringUtils.clearHtml(CommonUtils.getJsonString(jsonObject, "subject")).trim();
					if ("Нет темы".equals(subject)) subject = "#" + threadNumber;
					threadSummaries.add(new ThreadSummary(data.boardName, threadNumber, subject));
				}
				return new ReadThreadSummariesResult(threadSummaries);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		else return super.onReadThreadSummaries(data);
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.createFcgiUri("mobile", "task", "get_thread_last_info", "board",
				data.boardName, "thread", data.threadNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
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
		DvachChanLocator locator = DvachChanLocator.get(this);
		if (StringUtils.isEmpty(uri.getQuery()) && locator.isAttachmentUri(uri)
				&& locator.isImageExtension(uri.getPath()))
		{
			uri = uri.buildUpon().encodedQuery("image=" + System.currentTimeMillis()).build();
		}
		return new ReadContentResult(new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass()).read());
	}

	@Override
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
			InvalidResponseException
	{
		return new CheckAuthorizationResult(readCaptchaPass(data, data.authorizationData[0]) != null);
	}

	private String mLastCaptchaPassData;
	private String mLastCaptchaPassCookie;

	private String readCaptchaPass(HttpRequest.Preset preset, String captchaPassData) throws HttpException,
			InvalidResponseException
	{
		mLastCaptchaPassData = null;
		mLastCaptchaPassCookie = null;
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		configuration.storeCookie(COOKIE_NOCAPTCHA, null, null);
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.createFcgiUri("makaba");
		UrlEncodedEntity entity = new UrlEncodedEntity("task", "auth", "usercode", captchaPassData, "json", "1");
		JSONObject jsonObject = new HttpRequest(uri, preset).addCookie(buildCookies(null)).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		if (jsonObject.optInt("result") != 1) return null;
		String captchaPassCookie = CommonUtils.optJsonString(jsonObject, "hash");
		if (StringUtils.isEmpty(captchaPassCookie)) throw new InvalidResponseException();
		mLastCaptchaPassData = captchaPassData;
		mLastCaptchaPassCookie = captchaPassCookie;
		if (captchaPassCookie != null)
		{
			configuration.storeCookie(COOKIE_NOCAPTCHA, captchaPassCookie, "Usercode No Captcha");
		}
		return captchaPassCookie;
	}

	private static final String CAPTCHA_PASS_COOKIE = "captchaPassCookie";
	private static final String USE_APP_CAPTCHA = "appCaptchaValue";

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
		if (data.threadNumber != null && configuration.isCaptchaBypassEnabled())
		{
			DvachAppCaptcha appCaptcha = DvachAppCaptcha.getInstance();
			if (appCaptcha != null)
			{
				Uri uri = locator.buildPath("api", "captcha", "app", "check", appCaptcha.getPublicKey());
				JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookies(null)).read().getJsonObject();
				if (jsonObject != null && jsonObject.optInt("result") == 1)
				{
					CaptchaData captchaData = new CaptchaData();
					captchaData.put(USE_APP_CAPTCHA, "true");
					return new ReadCaptchaResult(CaptchaState.SKIP, captchaData)
							.setValidity(DvachChanConfiguration.Captcha.Validity.IN_BOARD_SEPARATELY);
				}
			}
		}
		Uri uri = locator.buildPath("api", "captcha", "settings", data.boardName);
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookies(null)).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		if (jsonObject.optInt("enabled", 1) == 0) return new ReadCaptchaResult(CaptchaState.SKIP, null);
		LinkedHashSet<String> availableCaptchaTypes = null;
		try
		{
			JSONArray jsonArray = jsonObject.getJSONArray("types");
			for (int i = 0; i < jsonArray.length(); i++)
			{
				String captchaType = CommonUtils.getJsonString(jsonArray.getJSONObject(i), "id");
				if (availableCaptchaTypes == null) availableCaptchaTypes = new LinkedHashSet<>();
				availableCaptchaTypes.add(captchaType);
			}
		}
		catch (JSONException e)
		{

		}
		String captchaType = data.captchaType;
		boolean overrideCaptchaType = false;
		if (availableCaptchaTypes != null && !availableCaptchaTypes.contains(data.captchaType))
		{
			if (availableCaptchaTypes.contains(DvachChanConfiguration.CAPTCHA_TYPE_2CHAPTCHA))
			{
				captchaType = DvachChanConfiguration.CAPTCHA_TYPE_2CHAPTCHA;
			}
			else captchaType = availableCaptchaTypes.iterator().next();
			overrideCaptchaType = true;
		}
		return onReadCaptcha(data, captchaType, overrideCaptchaType, data.captchaPass != null
				? data.captchaPass[0] : null, true);
	}

	private static final String ANIMECAPTCHA_VALUE = "animecaptcha_value";

	private static ReadCaptchaResult makeCaptchaPassResult(String captchaPassCookie)
	{
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CAPTCHA_PASS_COOKIE, captchaPassCookie);
		return new ReadCaptchaResult(CaptchaState.PASS, captchaData)
				.setValidity(DvachChanConfiguration.Captcha.Validity.LONG_LIFETIME);
	}

	private ReadCaptchaResult onReadCaptcha(ReadCaptchaData data, String captchaType, boolean overrideCaptchaType,
			String captchaPassData, boolean mayUseLastCaptchaPassCookie) throws HttpException, InvalidResponseException
	{
		DvachChanLocator locator = DvachChanLocator.get(this);
		String captchaPassCookie = null;
		boolean mayRelogin = false;
		if (captchaPassData != null)
		{
			if (mayUseLastCaptchaPassCookie && captchaPassData.equals(mLastCaptchaPassData))
			{
				captchaPassCookie = mLastCaptchaPassCookie;
				mayRelogin = true;
			}
			else captchaPassCookie = readCaptchaPass(data, captchaPassData);
		}
		Uri.Builder uriBuilder = locator.buildPath("api", "captcha", captchaType, "id").buildUpon();
		uriBuilder.appendQueryParameter("board", data.boardName);
		if (data.threadNumber != null) uriBuilder.appendQueryParameter("thread", data.threadNumber);
		Uri uri = uriBuilder.build();
		JSONObject jsonObject = null;
		HttpException exception = null;
		try
		{
			jsonObject = new HttpRequest(uri, data).addCookie(buildCookies(captchaPassCookie)).read().getJsonObject();
		}
		catch (HttpException e)
		{
			if (!e.isHttpException()) throw e;
			exception = e;
		}
		String apiResult = jsonObject != null ? CommonUtils.optJsonString(jsonObject, "result") : null;
		if ("3".equals(apiResult))
		{
			return new ReadCaptchaResult(CaptchaState.SKIP, null);
		}
		else if ("2".equals(apiResult))
		{
			return makeCaptchaPassResult(captchaPassCookie);
		}
		else
		{
			if (mayRelogin) return onReadCaptcha(data, captchaType, overrideCaptchaType, captchaPassData, false);
			if (DvachChanConfiguration.CAPTCHA_TYPE_ANIMECAPTCHA.equals(captchaType) && data.mayShowLoadButton)
			{
				ReadCaptchaResult result = new ReadCaptchaResult(CaptchaState.NEED_LOAD, null);
				if (overrideCaptchaType) result.setCaptchaType(captchaType);
				return result;
			}
			String id = jsonObject != null ? CommonUtils.optJsonString(jsonObject, "id") : null;
			if (id != null)
			{
				CaptchaData captchaData = new CaptchaData();
				ReadCaptchaResult result;
				if (DvachChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1.equals(captchaType)
						|| DvachChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType)
						|| DvachChanConfiguration.CAPTCHA_TYPE_MAILRU.equals(captchaType))
				{
					result = new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
					captchaData.put(CaptchaData.API_KEY, id);
				}
				else
				{
					OUTER: if (DvachChanConfiguration.CAPTCHA_TYPE_2CHAPTCHA.equals(captchaType))
					{
						result = new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
						captchaData.put(CaptchaData.CHALLENGE, id);
						uri = locator.buildPath("api", "captcha", captchaType, "image", id);
						Bitmap image = new HttpRequest(uri, data).read().getBitmap();
						if (image == null) throw new InvalidResponseException();
						int width = image.getWidth();
						int height = image.getHeight();
						int[] pixels = new int[width * height];
						image.getPixels(pixels, 0, width, 0, 0, width, height);
						image.recycle();
						for (int j = 0; j < height; j++)
						{
							for (int i = 0; i < width; i++)
							{
								boolean replace = false;
								if (i == 0 || j == 0 || i == width - 1 || j == height - 1) replace = true;
								else if (pixels[j * width + i] != 0xffffffff)
								{
									int count = 0;
									if (pixels[(j - 1) * width + i - 1] != 0xffffffff) count++;
									if (pixels[(j - 1) * width + i] != 0xffffffff) count++;
									if (pixels[(j - 1) * width + i + 1] != 0xffffffff) count++;
									if (pixels[j * width + i - 1] != 0xffffffff) count++;
									if (pixels[j * width + i + 1] != 0xffffffff) count++;
									if (pixels[(j + 1) * width + i - 1] != 0xffffffff) count++;
									if (pixels[(j + 1) * width + i] != 0xffffffff) count++;
									if (pixels[(j + 1) * width + i + 1] != 0xffffffff) count++;
									if (count < 5) replace = true;
								}
								if (replace) pixels[j * width + i] = 0x00000000;
							}
						}
						for (int i = 0; i < pixels.length; i++)
						{
							if (pixels[i] == 0x00000000) pixels[i] = 0xffffffff;
						}
						image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
						Bitmap trimmed = CommonUtils.trimBitmap(image, 0xffffffff);
						if (trimmed != null)
						{
							if (trimmed != image) image.recycle();
							image = trimmed;
						}
						result.setImage(image);
					}
					else if (DvachChanConfiguration.CAPTCHA_TYPE_ANIMECAPTCHA.equals(captchaType))
					{
						String[] ids;
						String[] items;
						try
						{
							JSONArray jsonArray = jsonObject.getJSONArray("values");
							ids = new String[jsonArray.length()];
							items = new String[jsonArray.length()];
							for (int i = 0; i < jsonArray.length(); i++)
							{
								jsonObject = jsonArray.getJSONObject(i);
								ids[i] = CommonUtils.getJsonString(jsonObject, "id");
								items[i] = CommonUtils.getJsonString(jsonObject, "name");
							}
						}
						catch (JSONException e)
						{
							throw new InvalidResponseException(e);
						}
						uri = locator.buildPath("api", "captcha", captchaType, "image", id);
						Bitmap image = new HttpRequest(uri, data).read().getBitmap();
						Integer index;
						while (true)
						{
							index = requireUserItemSingleChoice(-1, items, null, image);
							if (index == null)
							{
								result = new ReadCaptchaResult(CaptchaState.NEED_LOAD, null);
								break OUTER;
							}
							if (index != -1) break;
						}
						uri = locator.buildPath("api", "captcha", captchaType, "check", id).buildUpon()
								.appendQueryParameter("value", ids[index]).build();
						jsonObject = new HttpRequest(uri, data).addCookie(buildCookies(captchaPassCookie))
								.read().getJsonObject();
						try
						{
							apiResult = CommonUtils.getJsonString(jsonObject, "result");
						}
						catch (JSONException e)
						{
							throw new InvalidResponseException();
						}
						if ("1".equals(apiResult))
						{
							result = new ReadCaptchaResult(CaptchaState.SKIP, captchaData);
							captchaData.put(CaptchaData.CHALLENGE, id);
							captchaData.put(ANIMECAPTCHA_VALUE, ids[index]);
							break OUTER;
						}
						// Read new captcha
						return onReadCaptcha(data, captchaType, overrideCaptchaType, captchaPassData,
								mayUseLastCaptchaPassCookie);
					}
					else throw new RuntimeException();
				}
				if (overrideCaptchaType) result.setCaptchaType(captchaType);
				return result;
			}
			else
			{
				// If wakaba is swaying, but passcode is verified, let's try to use it
				if (captchaPassCookie != null) return makeCaptchaPassResult(captchaPassCookie);
				if (exception != null) throw exception;
				throw new InvalidResponseException();
			}
		}
	}

	private static final Pattern PATTERN_TAG = Pattern.compile("(.*) /([^/]*)/");
	private static final Pattern PATTERN_BAN = Pattern.compile("([^ ]*?): (.*?)(?:\\.|$)");

	private static final SimpleDateFormat DATE_FORMAT_BAN;

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

		DvachChanLocator locator = DvachChanLocator.get(this);
		if (data.captchaData != null)
		{
			boolean check = false;
			String challenge = data.captchaData.get(CaptchaData.CHALLENGE);
			String input = StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT));
			if (data.captchaData.get(USE_APP_CAPTCHA) != null)
			{
				DvachAppCaptcha appCaptcha = DvachAppCaptcha.getInstance();
				Uri uri = locator.buildPath("api", "captcha", "app", "id", appCaptcha.getPublicKey());
				JSONObject jsonObject = new HttpRequest(uri, data.holder).addCookie(buildCookies(null))
						.read().getJsonObject();
				if (jsonObject == null) throw new InvalidResponseException();
				String id = CommonUtils.optJsonString(jsonObject, "id");
				if (!StringUtils.isEmpty(id))
				{
					entity.add("captcha_type", "app");
					entity.add("app_response_id", id);
					try
					{
						entity.add("app_response", appCaptcha.getCaptchaValue(id.getBytes("US-ASCII")));
					}
					catch (UnsupportedEncodingException e)
					{
						throw new RuntimeException(e);
					}
				}
			}
			else if (DvachChanConfiguration.CAPTCHA_TYPE_2CHAPTCHA.equals(data.captchaType))
			{
				entity.add("captcha_type", "2chaptcha");
				entity.add("2chaptcha_id", challenge);
				entity.add("2chaptcha_value", input);
				check = true;
			}
			else if (DvachChanConfiguration.CAPTCHA_TYPE_ANIMECAPTCHA.equals(data.captchaType))
			{
				entity.add("captcha_type", "animecaptcha");
				entity.add("animecaptcha_id", challenge);
				entity.add("animecaptcha_value", data.captchaData.get(ANIMECAPTCHA_VALUE));
			}
			else if (DvachChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(data.captchaType))
			{
				entity.add("captcha_type", "recaptcha");
				entity.add("g-recaptcha-response", input);
			}
			else if (DvachChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1.equals(data.captchaType))
			{
				entity.add("captcha_type", "recaptchav1");
				entity.add("recaptcha_challenge_field", challenge);
				entity.add("recaptcha_response_field", input);
			}
			else if (DvachChanConfiguration.CAPTCHA_TYPE_MAILRU.equals(data.captchaType))
			{
				entity.add("captcha_type", "mailru");
				entity.add("captcha_id", challenge);
				entity.add("captcha_value", input);
			}
			captchaPassCookie = data.captchaData.get(CAPTCHA_PASS_COOKIE);
			if (check && captchaPassCookie == null)
			{
				Uri uri = locator.buildPath("api", "captcha", data.captchaType, "check", challenge)
						.buildUpon().appendQueryParameter("value", input).build();
				JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
				if (jsonObject != null)
				{
					String apiResult = CommonUtils.optJsonString(jsonObject, "result");
					if ("0".equals(apiResult)) throw new ApiException(ApiException.SEND_ERROR_CAPTCHA);
				}
			}
		}

		Uri uri = locator.createFcgiUri("posting", "json", "1");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
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
			DvachChanConfiguration configuration = DvachChanConfiguration.get(this);
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
		DvachChanLocator locator = DvachChanLocator.get(this);
		Uri uri = locator.createFcgiUri("makaba");
		StringBuilder postsBuilder = new StringBuilder();
		for (String postNumber : data.postNumbers) postsBuilder.append(postNumber).append(", ");
		MultipartEntity entity = new MultipartEntity("task", "report", "board", data.boardName,
				"thread", data.threadNumber, "posts", postsBuilder.toString(), "comment", data.comment, "json", "1");
		String referer = locator.createThreadUri(data.boardName, data.threadNumber).toString();
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
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