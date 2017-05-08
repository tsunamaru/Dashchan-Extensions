package com.mishiranu.dashchan.chan.nullchan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.SimpleEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class NullchanChanPerformer extends ChanPerformer {
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		NullchanChanLocator locator = NullchanChanLocator.get(this);
		boolean firstPage = data.pageNumber == 0;
		Uri uri = locator.buildQuery("api/board", "dir", data.boardName);
		JSONObject jsonObject = new HttpRequest(uri, data).setValidator(firstPage ? data.validator : null)
				.read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		String cursor;
		try {
			cursor = CommonUtils.getJsonString(jsonObject.getJSONObject("pagination"), "cursor");
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if (!firstPage) {
			uri = locator.buildQuery("api/board", "dir", data.boardName, "page", Integer.toString(data.pageNumber + 1),
					"cursor", cursor);
			jsonObject = new HttpRequest(uri, data).setValidator(data.validator).read().getJsonObject();
			if (jsonObject == null) {
				throw new InvalidResponseException();
			}
		} else {
			try {
				String description = CommonUtils.getJsonString(jsonObject.getJSONObject("board"), "description");
				if (!StringUtils.isEmpty(description)) {
					NullchanChanConfiguration.<NullchanChanConfiguration>get(this)
							.storeBoardDescription(data.boardName, description);
				}
			} catch (JSONException e) {
				// Ignore exception
			}
		}
		try {
			ArrayList<Posts> threads = NullchanModelMapper.createThreads(jsonObject, locator);
			if (threads.isEmpty()) {
				throw HttpException.createNotFoundException();
			}
			return new ReadThreadsResult(threads);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException,
			RedirectException {
		NullchanChanLocator locator = NullchanChanLocator.get(this);
		String fromPostNumber = data.partialThreadLoading ? data.lastPostNumber : null;
		Uri uri = fromPostNumber == null ? locator.buildQuery("api/thread", "thread", data.threadNumber)
				: locator.buildQuery("api/thread", "thread", data.threadNumber, "after", fromPostNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).setValidator(data.validator).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			String boardName = CommonUtils.getJsonString(jsonObject.getJSONObject("thread")
					.getJSONObject("board"), "dir");
			if (!StringUtils.equals(data.boardName, boardName)) {
				throw RedirectException.toThread(boardName, data.threadNumber, null);
			}
			return new ReadPostsResult(NullchanModelMapper.createPosts(jsonObject, locator));
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException {
		NullchanChanLocator locator = NullchanChanLocator.get(this);
		Uri uri = locator.buildQuery("api/post", "post", data.postNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			jsonObject = jsonObject.getJSONObject("post");
			String boardName = CommonUtils.getJsonString(jsonObject, "boardDir");
			if (!StringUtils.equals(data.boardName, boardName)) {
				throw HttpException.createNotFoundException();
			}
			return new ReadSinglePostResult(NullchanModelMapper.createPost(jsonObject, locator, null, null));
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private interface FilterBoard {
		public boolean apply(String boardName);
	}

	private ArrayList<Board> readBoards(HttpRequest.Preset preset, FilterBoard filterBoard) throws HttpException,
			InvalidResponseException {
		NullchanChanLocator locator = NullchanChanLocator.get(this);
		Uri uri = locator.buildPath("api", "board", "list");
		JSONObject jsonObject = new HttpRequest(uri, preset).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			JSONArray jsonArray = jsonObject.getJSONArray("boards");
			ArrayList<Board> boards = new ArrayList<>();
			for (int i = 0; i < jsonArray.length(); i++) {
				jsonObject = jsonArray.getJSONObject(i);
				String boardName = CommonUtils.getJsonString(jsonObject, "dir");
				if (filterBoard.apply(boardName)) {
					String title = CommonUtils.getJsonString(jsonObject, "name");
					boards.add(new Board(boardName, title));
				}
			}
			Collections.sort(boards);
			return boards;
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private static final HashSet<String> GENERAL_BOARDS = new HashSet<>(Arrays.asList("0", "b"));

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		return new ReadBoardsResult(new BoardCategory(null, readBoards(data, GENERAL_BOARDS::contains)));
	}

	@Override
	public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException,
			InvalidResponseException {
		return new ReadUserBoardsResult(readBoards(data, boardName -> !GENERAL_BOARDS.contains(boardName)));
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		NullchanChanLocator locator = NullchanChanLocator.get(this);
		Uri uri = locator.buildQuery("api/thread", "thread", data.threadNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).setValidator(data.validator).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			String boardName = CommonUtils.getJsonString(jsonObject.getJSONObject("thread")
					.getJSONObject("board"), "dir");
			if (!StringUtils.equals(data.boardName, boardName)) {
				throw HttpException.createNotFoundException();
			}
			return new ReadPostsCountResult(jsonObject.getJSONArray("posts").length());
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private static final String REQUIREMENT_POST = "post";

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		if (REQUIREMENT_POST.equals(data.requirement)) {
			NullchanChanLocator locator = NullchanChanLocator.get(this);
			Uri uri = locator.buildPath("api", "captcha");
			JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
			if (jsonObject == null) {
				throw new InvalidResponseException();
			}
			String challenge = CommonUtils.optJsonString(jsonObject, "captcha");
			String base64Image = CommonUtils.optJsonString(jsonObject, "image");
			if (challenge == null || base64Image == null) {
				throw new InvalidResponseException();
			}
			base64Image = base64Image.substring(base64Image.indexOf(',') + 1);
			byte[] imageArray = Base64.decode(base64Image, Base64.DEFAULT);
			if (imageArray == null) {
				throw new InvalidResponseException();
			}
			Bitmap image = BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
			if (image == null) {
				throw new InvalidResponseException();
			}
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.CHALLENGE, challenge);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
		} else {
			return new ReadCaptchaResult(CaptchaState.SKIP, null);
		}
	}

	private static final Pattern PATTERN_REPLY = Pattern.compile("^>>(\\d+)(?:$|\n(.*))");

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		NullchanChanLocator locator = NullchanChanLocator.get(this);

		String replyPostNumber = null;
		String comment = StringUtils.emptyIfNull(data.comment);
		if (data.threadNumber != null) {
			Uri uri = locator.buildQuery("api/thread", "thread", data.threadNumber);
			JSONObject postObject = new HttpRequest(uri, data).read().getJsonObject();
			if (postObject == null) {
				throw new InvalidResponseException();
			}
			JSONArray postsArray = postObject.optJSONArray("posts");
			if (postsArray == null) {
				throw new InvalidResponseException();
			}
			ArrayList<String> postsNumbers = new ArrayList<>();
			for (int i = 0; i < postsArray.length(); i++) {
				postObject = postsArray.optJSONObject(i);
				if (postObject == null) {
					throw new InvalidResponseException();
				}
				String postNumber = CommonUtils.optJsonString(postObject, "id");
				if (postNumber == null) {
					throw new InvalidResponseException();
				}
				postsNumbers.add(postNumber);
			}
			if (postsNumbers.isEmpty()) {
				throw new InvalidResponseException();
			}
			Matcher matcher = PATTERN_REPLY.matcher(comment);
			if (matcher.matches()) {
				String newComment = matcher.group(2);
				if (!StringUtils.isEmpty(newComment)) {
					String postNumber = matcher.group(1);
					if (postsNumbers.contains(postNumber)) {
						replyPostNumber = postNumber;
						comment = newComment;
					}
				}
			}
			if (replyPostNumber == null) {
				replyPostNumber = postsNumbers.get(0);
			}
		}

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("message", comment);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

		if (data.attachments != null) {
			JSONArray imagesArray = new JSONArray();
			for (SendPostData.Attachment attachment : data.attachments) {
				Uri uri = locator.buildPath("api", "attachment", "upload");
				MultipartEntity entity = new MultipartEntity();
				attachment.addToEntity(entity, "file");
				JSONObject attachmentObject = new HttpRequest(uri, data).setPostMethod(entity)
						.setSuccessOnly(false).read().getJsonObject();
				if (attachmentObject == null) {
					data.holder.checkResponseCode();
					throw new InvalidResponseException();
				}
				if (attachmentObject.optBoolean("ok")) {
					attachmentObject = attachmentObject.optJSONObject("attachment");
					if (attachmentObject == null) {
						throw new InvalidResponseException();
					}
					String token = CommonUtils.optJsonString(attachmentObject, "token");
					if (token == null) {
						throw new InvalidResponseException();
					}
					imagesArray.put(token);
				} else {
					String message = CommonUtils.optJsonString(attachmentObject, "message");
					CommonUtils.writeLog("Nullchan send message", message);
					throw new ApiException(message);
				}
			}
			if (imagesArray.length() > 0) {
				try {
					jsonObject.put("images", imagesArray);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
		}

		JSONObject responseObject;
		boolean retry = false;
		String captchaChallenge = null;
		String message;

		while (true) {
			if (captchaChallenge != null) {
				try {
					jsonObject.put("captcha", captchaChallenge);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
			SimpleEntity entity = new SimpleEntity();
			entity.setContentType("application/json");
			entity.setData(jsonObject.toString());

			Uri uri;
			if (replyPostNumber == null) {
				uri = locator.buildQuery("api/thread/create", "board", data.boardName);
			} else {
				uri = locator.buildQuery("api/thread/reply", "parent", replyPostNumber);
			}
			responseObject = new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
					.setSuccessOnly(false).read().getJsonObject();
			if (responseObject == null) {
				data.holder.checkResponseCode();
				throw new InvalidResponseException();
			}

			message = CommonUtils.optJsonString(responseObject, "message");
			if ("captcha required".equals(message)) {
				while (true) {
					CaptchaData captchaData = requireUserCaptcha(REQUIREMENT_POST,
							data.boardName, data.threadNumber, retry);
					retry = true;
					if (captchaData == null) {
						throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
					}
					captchaChallenge = captchaData.get(CaptchaData.CHALLENGE);
					uri = locator.buildQuery("api/captcha", "captcha", captchaChallenge,
							"answer", captchaData.get(CaptchaData.INPUT));
					JSONObject captchaObject = new HttpRequest(uri, data.holder).read().getJsonObject();
					if (captchaObject == null) {
						throw new InvalidResponseException();
					}
					if (captchaObject.optBoolean("ok")) {
						break;
					}
				}
			} else {
				break;
			}
		}

		JSONObject postObject = responseObject.optJSONObject("post");
		if (postObject != null) {
			String threadNumber = CommonUtils.optJsonString(postObject, "threadId");
			String postNumber = CommonUtils.optJsonString(postObject, "id");
			if (threadNumber == null || postNumber == null) {
				throw new InvalidResponseException();
			}
			return new SendPostResult(threadNumber, postNumber);
		}

		if (message != null) {
			int errorType = 0;
			if (message.contains("no message and no files to post")) {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			}
			CommonUtils.writeLog("Nullchan send message", message);
			throw new ApiException(message);
		}

		throw new InvalidResponseException();
	}
}
