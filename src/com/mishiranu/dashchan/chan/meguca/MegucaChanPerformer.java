package com.mishiranu.dashchan.chan.meguca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class MegucaChanPerformer extends ChanPerformer
{
	private static final Board[] BOARDS =
	{
		new Board("a", "Cancer Dispenser"),
		new Board("an", "Group Watching and Reading"),
		new Board("cr", "Creative"),
		new Board("g", "Technology & Programming"),
		new Board("v", "Games")
	};
	
	private static final String REQUIREMENT_REPORT = "report";
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		MegucaChanLocator locator = MegucaChanLocator.get(this);
		Uri uri = locator.buildPath("api", "board", data.boardName);
		JSONArray threadsArray = new HttpRequest(uri, data).read().getJsonArray();
		if (threadsArray == null) throw new InvalidResponseException();
		int start = 0;
		int end = threadsArray.length();
		if (!data.isCatalog())
		{
			int threadsPerPage = 10;
			start = threadsPerPage * data.pageNumber;
			end = Math.min(start + threadsPerPage, end);
		}
		if (end - start <= 0)
		{
			if (data.pageNumber == 0 || data.isCatalog()) return null;
			else throw HttpException.createNotFoundException();
		}
		try
		{
			ArrayList<Posts> threads = new ArrayList<>();
			ArrayList<Post> allPosts = new ArrayList<>();
			OUTER: for (int i = start; i < end; i++)
			{
				uri = locator.buildPath("api", "thread", threadsArray.getString(i)).buildUpon()
						.appendQueryParameter("last", "5").build();
				JSONArray jsonArray = new HttpRequest(uri, data).read().getJsonArray();
				if (jsonArray == null || jsonArray.length() == 0) throw new InvalidResponseException();
				ArrayList<Post> posts = new ArrayList<>(6);
				for (int j = 0; j < jsonArray.length(); j++)
				{
					JSONObject jsonObject = jsonArray.getJSONObject(j);
					Post post = MegucaModelMapper.createPost(jsonObject, locator, data.boardName);
					if (post == null)
					{
						if (j == 0) continue OUTER;
						continue;
					}
					posts.add(post);
					allPosts.add(post);
				}
				int postsCount = jsonArray.getJSONObject(0).optInt("omit") + posts.size();
				threads.add(new Posts(posts).addPostsCount(postsCount));
			}
			handleCapcodes(data.holder, allPosts);
			return new ReadThreadsResult(threads);
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		MegucaChanLocator locator = MegucaChanLocator.get(this);
		Uri uri = locator.buildPath("api", "thread", data.threadNumber);
		JSONArray jsonArray = new HttpRequest(uri, data).read().getJsonArray();
		if (jsonArray == null || jsonArray.length() == 0) throw new InvalidResponseException();
		try
		{
			JSONObject jsonObject = jsonArray.getJSONObject(0);
			String boardName = CommonUtils.getJsonString(jsonObject, "board");
			if (!boardName.equals(data.boardName))
			{
				throw new ThreadRedirectException(boardName, data.threadNumber, null);
			}
			ArrayList<Post> posts = new ArrayList<>();
			for (int i = 0; i < jsonArray.length(); i++)
			{
				Post post = MegucaModelMapper.createPost(jsonArray.getJSONObject(i), locator, data.boardName);
				if (post == null)
				{
					if (i == 0) throw HttpException.createNotFoundException();
					continue;
				}
				posts.add(post);
			}
			handleCapcodes(data.holder, posts);
			return new ReadPostsResult(posts);
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		MegucaChanLocator locator = MegucaChanLocator.get(this);
		Uri uri = locator.buildPath("api", "post", data.postNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		try
		{
			Post post = MegucaModelMapper.createPost(jsonObject, locator, data.boardName);
			if (post == null) throw HttpException.createNotFoundException();
			handleCapcodes(data.holder, Collections.singleton(post));
			return new ReadSinglePostResult(post);
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	private void handleCapcodes(HttpHolder holder, Collection<Post> posts) throws HttpException
	{
		ArrayList<Post> postsWithCapcodes = null;
		for (Post post : posts)
		{
			if (post.getCapcode() != null)
			{
				if (postsWithCapcodes == null) postsWithCapcodes = new ArrayList<>();
				postsWithCapcodes.add(post);
			}
		}
		if (postsWithCapcodes != null && postsWithCapcodes.size() > 0)
		{
			MegucaChanLocator locator = MegucaChanLocator.get(this);
			Uri uri = locator.buildPath("api", "config");
			JSONObject jsonObject = new HttpRequest(uri, holder).read().getJsonObject();
			try
			{
				jsonObject = jsonObject.getJSONObject("hot").getJSONObject("staff_aliases");
			}
			catch (JSONException e)
			{
				return;
			}
			for (Post post : postsWithCapcodes)
			{
				String capcode = CommonUtils.optJsonString(jsonObject, post.getCapcode());
				if (capcode != null) post.setCapcode(capcode);
			}
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		return new ReadBoardsResult(new BoardCategory(null, BOARDS));
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		MegucaChanLocator locator = MegucaChanLocator.get(this);
		Uri uri = locator.buildPath("api", "thread", data.threadNumber).buildUpon()
				.appendQueryParameter("last", "1").build();
		JSONArray jsonArray = new HttpRequest(uri, data).read().getJsonArray();
		if (jsonArray == null || jsonArray.length() == 0) throw new InvalidResponseException();
		try
		{
			JSONObject jsonObject = jsonArray.getJSONObject(0);
			String boardName = CommonUtils.getJsonString(jsonObject, "board");
			if (!boardName.equals(data.boardName)) throw HttpException.createNotFoundException();
			return new ReadPostsCountResult(jsonObject.optInt("omit") + jsonArray.length());
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		if (REQUIREMENT_REPORT.equals(data.requirement))
		{
			MegucaChanLocator locator = MegucaChanLocator.get(this);
			Uri uri = locator.buildPath("api", "config");
			JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
			try
			{
				String apiKey = CommonUtils.getJsonString(jsonObject.getJSONObject("config"), "RECAPTCHA_PUBLIC_KEY");
				CaptchaData captchaData = new CaptchaData();
				captchaData.put(CaptchaData.API_KEY, apiKey);
				return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		return new ReadCaptchaResult(CaptchaState.SKIP, null);
	}
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		if (data.attachments == null && data.comment == null)
		{
			throw new ApiException(ApiException.SEND_ERROR_EMPTY_COMMENT);
		}
		MegucaChanLocator locator = MegucaChanLocator.get(this);
		Streaming streaming = new Streaming(locator);
		try
		{
			streaming.start();
			long id = Streaming.generateId();
			if (data.threadNumber != null)
			{
				JSONObject jsonObject = new JSONObject();
				try
				{
					jsonObject.put(data.threadNumber, Integer.MAX_VALUE);
				}
				catch (JSONException e)
				{
					throw new RuntimeException(e);
				}
				streaming.send(data.holder, 32, id, data.boardName, jsonObject, false, "");
			}
			else streaming.send(data.holder, 32, id, data.boardName, null, true, "");
			String image = null;
			if (data.attachments != null)
			{
				Uri uri = locator.buildQuery("upload/", "id", Long.toString(id));
				MultipartEntity entity = new MultipartEntity("spoiler", data.attachments[0].optionSpoiler ? "1" : "0",
						"op", data.threadNumber != null ? data.threadNumber : "0");
				data.attachments[0].addToEntity(entity, "image");
				// Cookie header to avoid server error
				new HttpRequest(uri, data).setPostMethod(entity).addCookie("lastn", "100").setSuccessOnly(false)
						.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read();
				try
				{
					while (true)
					{
						JSONArray jsonArray = streaming.readAndFindData(0, 31);
						JSONObject jsonObject = jsonArray.getJSONObject(2);
						String status = CommonUtils.getJsonString(jsonObject, "t");
						String message = CommonUtils.optJsonString(jsonObject, "arg");
						if ("error".equals(status))
						{
							message = StringUtils.clearHtml(message);
							if (message.contains("Duplicate"))
							{
								throw new ApiException(ApiException.SEND_ERROR_FILE_EXISTS);
							}
							throw new ApiException(message);
						}
						else if ("alloc".equals(status))
						{
							image = message;
							break;
						}
						else if (!"status".equals(status)) throw new InvalidResponseException();
					}
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
			}
			JSONObject jsonObject = new JSONObject();
			try
			{
				jsonObject.put("nonce", Streaming.generateId());
				if (image != null) jsonObject.put("image", image);
				if (data.name != null) jsonObject.put("name", data.name);
				if (data.email != null) jsonObject.put("email", data.email);
				if (data.subject != null) jsonObject.put("subject", data.subject);
				jsonObject.put("frag", StringUtils.emptyIfNull(data.comment));
				if (data.threadNumber != null) jsonObject.put("op", Integer.parseInt(data.threadNumber));
			}
			catch (JSONException e)
			{
				throw new RuntimeException(e);
			}
			streaming.send(data.holder, 2, jsonObject);
			streaming.send(data.holder, 4);
			try
			{
				JSONArray jsonArray = streaming.readAndFindData(-1, 4);
				int i0 = jsonArray.getInt(0);
				int i2 = jsonArray.getInt(2);
				return new SendPostResult(Integer.toString(i0), Integer.toString(i2));
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		catch (Streaming.StreamingException e)
		{
			String message = e.getMessage();
			if (message != null)
			{
				if (message.contains("Bad protocol")) throw new InvalidResponseException();
				int errorType = 0;
				if (message.contains("Image lost") || message.contains("Image missing"))
				{
					errorType = ApiException.SEND_ERROR_EMPTY_FILE;
				}
				if (errorType != 0) throw new ApiException(errorType);
				CommonUtils.writeLog("Meguca post message", message);
				throw new ApiException(message);
			}
			throw new InvalidResponseException();
		}
		finally
		{
			streaming.disconnect();
		}
	}
	
	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		int postNumber = Integer.parseInt(data.postNumbers.get(0));
		MegucaChanLocator locator = MegucaChanLocator.get(this);
		Streaming streaming = new Streaming(locator);
		try
		{
			streaming.start();
			streaming.send(data.holder, 32, Streaming.generateId(), data.boardName, null, true, "");
			boolean retry = false;
			while (true)
			{
				CaptchaData captchaData = requireUserCaptcha(REQUIREMENT_REPORT,
						data.boardName, data.threadNumber, retry);
				if (captchaData == null) throw new ApiException(ApiException.REPORT_ERROR_NO_ACCESS);
				try
				{
					streaming.send(data.holder, 13, postNumber, captchaData.get(CaptchaData.CHALLENGE),
							captchaData.get(CaptchaData.INPUT));
					while (true)
					{
						JSONArray jsonArray = streaming.readAndFindData(Integer.parseInt(data.threadNumber), 13);
						int i2 = jsonArray.getInt(2);
						if (i2 == postNumber)
						{
							if (jsonArray.length() == 3) return null;
							else if (jsonArray.length() != 4) throw new InvalidResponseException();
							JSONObject jsonObject = jsonArray.getJSONObject(3);
							String message = CommonUtils.getJsonString(jsonObject, "error");
							// Invalid captcha
							if (message.contains("Incorrect") || message.contains("Pretty please")) break;
							CommonUtils.writeLog("Meguca report message", message);
							throw new ApiException(message);
						}
					}
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
				retry = true;
			}
		}
		catch (Streaming.StreamingException e)
		{
			throw new InvalidResponseException();
		}
		finally
		{
			streaming.disconnect();
		}
	}
}