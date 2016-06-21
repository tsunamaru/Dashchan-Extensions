package com.mishiranu.dashchan.chan.ronery;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class RoneryChanPerformer extends ChanPerformer
{
	private static final String RECAPTCHA_API_KEY = "6LcANBkTAAAAAILaKmHLweO-A1Lj1JXcXS4-zbg3";
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "page", Integer.toString(data.pageNumber + 1), "");
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new RoneryPostsParser(responseText, this).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	private static final Pattern PATTERN_REDIRECT = Pattern.compile("You are being redirected to .*?/thread/(\\d+)/#");
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.setSuccessOnly(false).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
		{
			uri = locator.buildPath(data.boardName, "post", data.threadNumber, "");
			responseText = new HttpRequest(uri, data.holder, data).read().getString();
			Matcher matcher = PATTERN_REDIRECT.matcher(responseText);
			if (matcher.find()) throw new ThreadRedirectException(matcher.group(1), data.threadNumber);
			throw HttpException.createNotFoundException();
		}
		else data.holder.checkResponseCode();
		try
		{
			return new ReadPostsResult(new RoneryPostsParser(responseText, this).convertPosts());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("_", "api", "chan", "post", "").buildUpon().appendQueryParameter("board",
				data.boardName) .appendQueryParameter("num", data.postNumber).build();
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		try
		{
			return new ReadSinglePostResult(RoneryModelMapper.createPost(jsonObject, locator));
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		ArrayList<Post> posts = new ArrayList<>();
		RoneryChanLocator locator = ChanLocator.get(this);
		for (int i = 0; i < 5; i++)
		{
			Uri uri = locator.buildPath(data.boardName, "search", "text").buildUpon()
					.appendPath(data.searchQuery).appendEncodedPath("page/" + (i + 1) + "/").build();
			String responseText = new HttpRequest(uri, data.holder, data).read().getString();
			try
			{
				ArrayList<Post> result = new RoneryPostsParser(responseText, this).convertSearch();
				if (result == null || result.isEmpty()) break;
				posts.addAll(result);
			}
			catch (ParseException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		return new ReadSearchPostsResult(posts);
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		String responseText = new HttpRequest(locator.buildPath(), data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new RoneryBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("_", "api", "chan", "thread", "").buildUpon().appendQueryParameter("num",
				data.threadNumber).appendQueryParameter("board", data.boardName).build();
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		String error = CommonUtils.optJsonString(jsonObject, "error");
		if (error != null && error.contains("not found")) throw HttpException.createNotFoundException();
		try
		{
			jsonObject = jsonObject.getJSONObject(data.threadNumber);
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
		int count = 1;
		jsonObject = jsonObject.optJSONObject("posts");
		if (jsonObject != null) count += jsonObject.length();
		return new ReadPostsCountResult(count);
	}
	
	private static final Pattern PATTERN_FLAG_POSITION = Pattern.compile("background-position: "
			+ "(-\\d+|0)(?:px)? (-\\d+|0)(?:px)?;");
	
	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		HttpResponse response = new HttpRequest(data.uri, data.holder, data).read();
		if ("flags.png".equals(data.uri.getLastPathSegment()))
		{
			String country = data.uri.getQueryParameter("country");
			if (country != null)
			{
				Bitmap bitmap = response.getBitmap();
				if (bitmap != null)
				{
					RoneryChanLocator locator = ChanLocator.get(this);
					Uri uri = locator.buildPath("foolfuuka", "foolz", "foolfuuka-theme-foolfuuka", "assets-1.1.1",
							"flags.css");
					String responseText = new HttpRequest(uri, data.holder, data).read().getString();
					int index = responseText.indexOf(".flag.flag-" + country);
					if (index >= 0)
					{
						responseText = responseText.substring(index, responseText.indexOf('}', index));
						Matcher matcher = PATTERN_FLAG_POSITION.matcher(responseText);
						if (matcher.find())
						{
							int x = Integer.parseInt(matcher.group(1));
							int y = Integer.parseInt(matcher.group(2));
							Bitmap result = Bitmap.createBitmap(16, 11, Bitmap.Config.ARGB_8888);
							new Canvas(result).drawBitmap(bitmap, x, y, null);
							bitmap.recycle();
							ByteArrayOutputStream output = new ByteArrayOutputStream();
							result.compress(Bitmap.CompressFormat.PNG, 100, output);
							return new ReadContentResult(new HttpResponse(output.toByteArray()));
						}
					}
				}
				throw new InvalidResponseException();
			}
		}
		return new ReadContentResult(response);
	}
	
	private boolean mNeedCaptcha = false;
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		if (mNeedCaptcha)
		{
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.API_KEY, RECAPTCHA_API_KEY);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
		}
		else return new ReadCaptchaResult(CaptchaState.SKIP, null);
	}
	
	private static final String COOKIE_TOKEN = "foolframe_gHD_csrf_token";
	
	private String readToken(HttpHolder holder, HttpRequest.Preset preset) throws HttpException,
			InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath();
		new HttpRequest(uri, holder, preset).read();
		String token = holder.getCookieValue(COOKIE_TOKEN);
		if (token != null) return token;
		throw new InvalidResponseException();
	}
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		String token = readToken(data.holder, data);
		MultipartEntity entity = new MultipartEntity();
		entity.add("reply_numero", data.threadNumber != null ? data.threadNumber : "0");
		if (data.optionSage) entity.add("reply_elitterae", "sage");
		entity.add("reply_talkingde", data.subject);
		entity.add("reply_chennodiscursus", StringUtils.emptyIfNull(data.comment));
		if (data.attachments != null)
		{
			data.attachments[0].addToEntity(entity, "file_image");
			if (data.attachments[0].optionSpoiler) entity.add("reply_gattai_spoilered", "true");
		}
		entity.add("reply_nymphassword", data.password);
		entity.add("reply_gattai", "true");
		entity.add("csrf_token", token);
		if (data.captchaData != null)
		{
			entity.add("recaptcha_challenge_field", data.captchaData.get(CaptchaData.CHALLENGE));
			entity.add("recaptcha_response_field", data.captchaData.get(CaptchaData.INPUT));
		}
		
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "submit");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.addCookie(COOKIE_TOKEN, token).addHeader("X-Requested-With", "XMLHttpRequest")
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).setSuccessOnly(false).read().getJsonObject();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_ENTITY_TOO_LARGE)
		{
			throw new ApiException(ApiException.SEND_ERROR_FILE_TOO_BIG);
		}
		data.holder.checkResponseCode();
		mNeedCaptcha = false;
		if (jsonObject == null) throw new InvalidResponseException();
		String success = CommonUtils.optJsonString(jsonObject, "success");
		if (success != null)
		{
			String threadNumber = CommonUtils.optJsonString(jsonObject, "thread_num");
			if (threadNumber != null)
			{
				String postNumber = null;
				try
				{
					postNumber = CommonUtils.getJsonString(jsonObject.getJSONObject(threadNumber)
							.getJSONArray("posts").getJSONObject(0), "num");
				}
				catch (JSONException e)
				{
					
				}
				return new SendPostResult(threadNumber, postNumber);
			}
		}
		String error = CommonUtils.optJsonString(jsonObject, "error");
		if (error != null)
		{
			int errorType = 0;
			if (error.contains("Comment: This value should not be blank"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			else if (error.contains("You're sending the same comment as the last time") ||
					error.contains("You must wait up to"))
			{
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			}
			else if (error.contains("Incorrect CAPTCHA solution"))
			{
				errorType = ApiException.SEND_ERROR_CAPTCHA;
				mNeedCaptcha = true;
			}
			if (errorType != 0) throw new ApiException(errorType);
			CommonUtils.writeLog("Ronery post message", error);
			throw new ApiException(error);
		}
		if (jsonObject.optBoolean("captcha"))
		{
			mNeedCaptcha = true;
			throw new ApiException(ApiException.SEND_ERROR_CAPTCHA);
		}
		throw new InvalidResponseException();
	}
	
	private String readPostDocId(HttpHolder holder, HttpRequest.Preset preset, String boardName, String postNumber)
			throws HttpException, InvalidResponseException
	{
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("_", "api", "chan", "post", "").buildUpon().appendQueryParameter("board", boardName)
				.appendQueryParameter("num", postNumber).build();
		JSONObject jsonObject = new HttpRequest(uri, holder, preset).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		return StringUtils.nullIfEmpty(CommonUtils.optJsonString(jsonObject, "doc_id"));
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		String token = readToken(data.holder, data);
		String docId = readPostDocId(data.holder, data, data.boardName, data.postNumbers.get(0));
		if (docId == null) throw new ApiException(ApiException.DELETE_ERROR_NO_ACCESS);
		UrlEncodedEntity entity = new UrlEncodedEntity("action", "delete", "board", data.boardName,
				"doc_id", docId, "password", data.password, "csrf_token", token);
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("_", "api", "chan", "user_actions");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.addCookie(COOKIE_TOKEN, token).addHeader("X-Requested-With", "XMLHttpRequest")
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		String success = CommonUtils.optJsonString(jsonObject, "success");
		if (success != null) return null;
		String error = CommonUtils.optJsonString(jsonObject, "error");
		if (error != null)
		{
			int errorType = 0;
			if (error.contains("You did not provide the correct deletion password"))
			{
				errorType = ApiException.DELETE_ERROR_PASSWORD;
			}
			if (errorType != 0) throw new ApiException(errorType);
			CommonUtils.writeLog("Ronery delete message", error);
			throw new ApiException(error);
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		String token = readToken(data.holder, data);
		String docId = readPostDocId(data.holder, data, data.boardName, data.postNumbers.get(0));
		if (docId == null) throw new ApiException(ApiException.REPORT_ERROR_NO_ACCESS);
		UrlEncodedEntity entity = new UrlEncodedEntity("action", "report", "board", data.boardName,
				"doc_id", docId, "reason", data.comment, "csrf_token", token);
		RoneryChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("_", "api", "chan", "user_actions");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.addCookie(COOKIE_TOKEN, token).addHeader("X-Requested-With", "XMLHttpRequest")
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		String success = CommonUtils.optJsonString(jsonObject, "success");
		if (success != null) return null;
		String error = CommonUtils.optJsonString(jsonObject, "error");
		if (error != null)
		{
			CommonUtils.writeLog("Ronery report message", error);
			throw new ApiException(error);
		}
		throw new InvalidResponseException();
	}
}