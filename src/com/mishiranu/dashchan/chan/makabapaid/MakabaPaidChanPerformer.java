package com.mishiranu.dashchan.chan.makabapaid;

import java.net.HttpURLConnection;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class MakabaPaidChanPerformer extends ChanPerformer
{
	private static final String COOKIE_AUTH = "usercode_auth";

	private String mLastUserAuthorizationData;
	private String mLastUserAuthorizationCookie;

	private HttpResponse readResponse(HttpRequest request, HttpHolder holder, boolean mayRetry) throws HttpException,
			InvalidResponseException
	{
		MakabaPaidChanConfiguration configuration = ChanConfiguration.get(this);
		String[] userAuthorizationDataArray = configuration.getUserAuthorizationData();
		String userAuthorizationData = userAuthorizationDataArray != null ? userAuthorizationDataArray[0] : null;
		synchronized (this)
		{
			if (!StringUtils.equals(userAuthorizationData, mLastUserAuthorizationData))
			{
				if (!readUserAuthorization(holder, null, userAuthorizationData))
				{
					throw new HttpException(HttpURLConnection.HTTP_UNAUTHORIZED, "Unauthorized");
				}
			}
		}
		HttpResponse response = (mayRetry ? request.copy() : request).addCookie(COOKIE_AUTH, configuration
				.getCookie(COOKIE_AUTH)).addCookie("usercode_nocaptcha", mLastUserAuthorizationCookie).read();
		if (response == null) return response;
		String responseText = response.getString();
		if (responseText.contains("<title>\nU shall not pass\n</title>"))
		{
			synchronized (this)
			{
				mLastUserAuthorizationData = null;
				mLastUserAuthorizationCookie = null;
			}
			if (mayRetry) return readResponse(request, holder, false);
			throw new HttpException(HttpURLConnection.HTTP_UNAUTHORIZED, "Unauthorized");
		}
		return response;
	}

	private HttpResponse readResponse(HttpRequest request, HttpHolder holder) throws HttpException,
			InvalidResponseException
	{
		return readResponse(request, holder, true);
	}

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, (data.isCatalog() ? "catalog" : data.pageNumber == 0
				? "index" : Integer.toString(data.pageNumber)) + ".json");
		JSONObject jsonObject = readResponse(new HttpRequest(uri, data.holder, data)
				.setValidator(data.validator), data.holder).getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				MakabaPaidChanConfiguration configuration = ChanConfiguration.get(this);
				configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				Posts[] threads = null;
				if (threadsArray != null && threadsArray.length() > 0)
				{
					threads = new Posts[threadsArray.length()];
					for (int i = 0; i < threads.length; i++)
					{
						threads[i] = MakabaPaidModelMapper.createThread(threadsArray.getJSONObject(i),
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
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		MakabaPaidChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		JSONObject jsonObject = readResponse(new HttpRequest(uri, data.holder, data)
				.setValidator(data.validator), data.holder).getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
				int uniquePosters = jsonObject.optInt("unique_posters");
				JSONArray jsonArray = jsonObject.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
				return new ReadPostsResult(new Posts(MakabaPaidModelMapper.createPosts(jsonArray,
						locator, data.boardName, false, configuration.isSageEnabled(data.boardName)))
						.setUniquePosters(uniquePosters));
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		MakabaPaidChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.createApiUri("makaba.fcgi");
		MultipartEntity entity = new MultipartEntity("task", "search", "board", "hidden_" + data.boardName,
				"find", data.searchQuery, "json", "1");
		JSONObject jsonObject = readResponse(new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT), data.holder).getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				String errorMessage = jsonObject.optString("message");
				if (!StringUtils.isEmpty(errorMessage)) throw new HttpException(0, errorMessage);
				return new ReadSearchPostsResult(MakabaPaidModelMapper.createPosts(jsonObject.getJSONArray("posts"),
						locator, data.boardName, false, configuration.isSageEnabled(data.boardName)));
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
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("b", "");
		String responseText = readResponse(new HttpRequest(uri, data.holder, data), data.holder).getString();
		int index = responseText.indexOf("<span class=\"nowrap\">Доски");
		if (index == -1) throw new InvalidResponseException();
		responseText = responseText.substring(index, responseText.indexOf("</span>", index));
		Matcher matcher = Pattern.compile("href=\"/(.*?)/\"").matcher(responseText);
		ArrayList<Board> boards = new ArrayList<>();
		while (matcher.find())
		{
			String boardName = matcher.group(1);
			uri = locator.buildPath(boardName, "index.json");
			JSONObject jsonObject = readResponse(new HttpRequest(uri, data.holder, data), data.holder).getJsonObject();
			if (jsonObject == null) throw new InvalidResponseException();
			try
			{
				String title = CommonUtils.getJsonString(jsonObject, "BoardName");
				String description = CommonUtils.optJsonString(jsonObject, "BoardInfoOuter");
				boards.add(new Board(boardName, title, description));
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		return new ReadBoardsResult(new BoardCategory(null, boards));
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		JSONObject jsonObject = readResponse(new HttpRequest(uri, data.holder, data)
				.setValidator(data.validator), data.holder).getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				JSONArray jsonArray = jsonObject.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
				return new ReadPostsCountResult(jsonArray.length());
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		Uri uri = data.uri;
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		if (StringUtils.isEmpty(uri.getQuery()) && locator.isAttachmentUri(uri)
				&& locator.isImageExtension(uri.getPath()))
		{
			uri = uri.buildUpon().encodedQuery("image=" + System.currentTimeMillis()).build();
		}
		return new ReadContentResult(readResponse(new HttpRequest(uri, data.holder, data), data.holder));
	}

	@Override
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
			InvalidResponseException
	{
		return new CheckAuthorizationResult(readUserAuthorization(data.holder, data, data.authorizationData[0]));
	}

	private boolean readUserAuthorization(HttpHolder holder, HttpRequest.Preset preset, String userAuthorizationData)
			throws HttpException, InvalidResponseException
	{
		mLastUserAuthorizationData = null;
		mLastUserAuthorizationCookie = null;
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("makaba.fcgi");
		UrlEncodedEntity entity = new UrlEncodedEntity("task", "auth", "usercode", userAuthorizationData, "json", "1");
		JSONObject jsonObject = new HttpRequest(uri, holder, preset).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		try
		{
			String userAuthorizationCookie = StringUtils.nullIfEmpty(CommonUtils.getJsonString(jsonObject, "Hash"));
			if (userAuthorizationCookie != null)
			{
				mLastUserAuthorizationData = userAuthorizationData;
				mLastUserAuthorizationCookie = userAuthorizationCookie;
			}
			return true;
		}
		catch (JSONException e)
		{
			String message = CommonUtils.optJsonString(jsonObject, "message");
			if (!StringUtils.isEmpty(message) && !message.contains("не существует"))
			{
				throw new HttpException(0, message);
			}
			return false;
		}
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

		MakabaPaidChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("posting.fcgi", "json", "1");
		String responseText = readResponse(new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT), data.holder).getString();

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
			MakabaPaidChanConfiguration configuration = ChanConfiguration.get(this);
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
		if (errorType != 0) throw new ApiException(errorType, extra);
		if (!StringUtils.isEmpty(reason)) throw new ApiException(reason);
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		MakabaPaidChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("makaba.fcgi");
		StringBuilder postsBuilder = new StringBuilder();
		for (String postNumber : data.postNumbers) postsBuilder.append(postNumber).append(", ");
		MultipartEntity entity = new MultipartEntity("task", "report", "board", "hidden_" + data.boardName,
				"thread", data.threadNumber, "posts", postsBuilder.toString(), "comment", data.comment, "json", "1");
		String referer = locator.createThreadUri(data.boardName, data.threadNumber).toString();
		JSONObject jsonObject = readResponse(new HttpRequest(uri, data.holder, data)
				.addHeader("Referer", referer).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT), data.holder).getJsonObject();
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