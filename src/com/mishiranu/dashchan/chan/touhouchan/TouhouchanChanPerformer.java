package com.mishiranu.dashchan.chan.touhouchan;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class TouhouchanChanPerformer extends ChanPerformer {
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		TouhouchanChanConfiguration configuration = TouhouchanChanConfiguration.get(this);
		Uri uri = locator.buildQuery(data.boardName + "/api/threads", "page", Integer.toString(data.pageNumber + 1));
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		handleStatus(jsonObject);
		Posts[] threads;
		try {
			threads = TouhouchanModelMapper.createThreads(jsonObject, locator, data.boardName);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if (threads != null) {
			configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
		}
		jsonObject = jsonObject.optJSONObject("boardinfo");
		int boardSpeed = jsonObject != null ? jsonObject.optInt("board_speed") : 0;
		return new ReadThreadsResult(threads).setBoardSpeed(boardSpeed);
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		TouhouchanChanConfiguration configuration = TouhouchanChanConfiguration.get(this);
		String lastPostNumber = data.partialThreadLoading ? data.lastPostNumber : null;
		Uri uri;
		if (lastPostNumber != null) {
			uri = locator.buildQuery(data.boardName + "/api/newposts", "id", data.threadNumber,
					"after", lastPostNumber);
		} else {
			uri = locator.buildQuery(data.boardName + "/api/thread", "id", data.threadNumber);
		}
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			handleStatus(jsonObject);
		} catch (HttpException e) {
			if (lastPostNumber != null && e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
				// Will throw exception if original post doesn't exist
				onReadSinglePost(data, data.boardName, data.threadNumber);
				return new ReadPostsResult((Posts) null); // No new posts
			}
			throw e;
		}
		Post[] posts;
		try {
			posts = TouhouchanModelMapper.createPosts(jsonObject, locator, data.boardName);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if (posts != null) {
			configuration.updateFromThreadsPostsJson(data.boardName, jsonObject);
		}
		return new ReadPostsResult(posts);
	}

	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException {
		return new ReadSinglePostResult(onReadSinglePost(data, data.boardName, data.postNumber));
	}

	private Post onReadSinglePost(HttpRequest.Preset preset, String boardName, String postNumber)
			throws HttpException, InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildQuery(boardName + "/api/post", "id", postNumber);
		JSONObject jsonObject = new HttpRequest(uri, preset).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		handleStatus(jsonObject);
		try {
			jsonObject = jsonObject.getJSONArray("data").getJSONObject(0);
			return TouhouchanModelMapper.createPost(jsonObject, locator, boardName);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildQuery(data.boardName + "/api/search", "subject", "1", "comment", "1",
				"find", data.searchQuery);
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		JSONObject statusObject = jsonObject.optJSONObject("status");
		if (statusObject != null) {
			String errorMessage = CommonUtils.optJsonString(statusObject, "error_msg");
			if ("Request is too short".equals(errorMessage)) {
				throw new HttpException(0, errorMessage);
			}
		}
		handleStatus(jsonObject);
		try {
			ArrayList<Post> posts = new ArrayList<>();
			JSONArray jsonArray = jsonObject.getJSONArray("data");
			for (int i = 0; i < jsonArray.length(); i++) {
				posts.add(TouhouchanModelMapper.createPost(jsonArray.getJSONObject(i), locator, data.boardName));
			}
			return new ReadSearchPostsResult(posts);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildPath("to", "api", "getboards");
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		handleStatus(jsonObject);
		try {
			LinkedHashMap<String, ArrayList<Board>> boardsMap = new LinkedHashMap<>();
			JSONObject infoObject = jsonObject.optJSONObject("info");
			if (infoObject != null) {
				JSONArray jsonArray = infoObject.optJSONArray("categories");
				if (jsonArray != null) {
					for (int i = 0; i < jsonArray.length(); i++) {
						String title = jsonArray.getString(i);
						boardsMap.put(title, new ArrayList<>());
					}
				}
			}
			JSONArray jsonArray = jsonObject.getJSONArray("boards");
			for (int i = 0; i < jsonArray.length(); i++) {
				jsonObject = jsonArray.getJSONObject(i);
				boolean hidden = "1".equals(CommonUtils.optJsonString(jsonObject, "is_hidden"));
				if (!hidden) {
					String boardName = CommonUtils.getJsonString(jsonObject, "board_entry");
					String title = CommonUtils.getJsonString(jsonObject, "board_name");
					String category = CommonUtils.getJsonString(jsonObject, "category");
					ArrayList<Board> boards = boardsMap.get(category);
					if (boards == null) {
						boards = new ArrayList<>();
						boardsMap.put(category, boards);
					}
					boards.add(new Board(boardName, title));
				}
			}
			ArrayList<BoardCategory> boardCategories = new ArrayList<>();
			for (LinkedHashMap.Entry<String, ArrayList<Board>> entry : boardsMap.entrySet()) {
				ArrayList<Board> boards = entry.getValue();
				if (!boards.isEmpty()) {
					boardCategories.add(new BoardCategory(entry.getKey(), boards));
				}
			}
			return new ReadBoardsResult(boardCategories);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildQuery(data.boardName + "/api/postcount", "id", data.threadNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject != null) {
			handleStatus(jsonObject);
			try {
				return new ReadPostsCountResult(jsonObject.getJSONObject("data").getInt("postcount"));
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	private void handleStatus(JSONObject jsonObject) throws HttpException, InvalidResponseException {
		JSONObject statusObject;
		try {
			statusObject = jsonObject.getJSONObject("status");
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		int errorCode = statusObject.optInt("error_code");
		if (errorCode != HttpURLConnection.HTTP_OK) {
			if (errorCode == HttpURLConnection.HTTP_NOT_FOUND) {
				throw HttpException.createNotFoundException();
			}
			throw new HttpException(errorCode, CommonUtils.optJsonString(statusObject, "error_msg"));
		}
	}

	private static final ColorMatrixColorFilter CAPTCHA_FILTER = new ColorMatrixColorFilter(new float[]
			{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f});

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "api", "checkconfig");
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		boolean needCaptcha;
		try {
			needCaptcha = jsonObject.getJSONObject("config").optInt("captcha") != 0;
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if (needCaptcha) {
			uri = locator.buildQuery("captcha.pl", "board", data.boardName, "key",
					data.threadNumber == null ? "mainpage" : "res" + data.threadNumber);
			Bitmap image = new HttpRequest(uri, data).read().getBitmap();
			if (image != null) {
				Bitmap newImage = Bitmap.createBitmap(image.getWidth(), 32, Bitmap.Config.ARGB_8888);
				Canvas canvas = new Canvas(newImage);
				canvas.drawColor(0xffffffff);
				Paint paint = new Paint();
				paint.setColorFilter(CAPTCHA_FILTER);
				canvas.drawBitmap(image, 0f, (newImage.getHeight() - image.getHeight()) / 2, paint);
				image.recycle();
				return new ReadCaptchaResult(CaptchaState.CAPTCHA, new CaptchaData()).setImage(newImage);
			}
			throw new InvalidResponseException();
		} else {
			return new ReadCaptchaResult(CaptchaState.SKIP, null)
					.setValidity(TouhouchanChanConfiguration.Captcha.Validity.IN_BOARD);
		}
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("task", "post");
		entity.add("board", data.boardName);
		entity.add("parent", data.threadNumber);
		entity.add("akane", data.name);
		entity.add("nabiki", data.optionSage ? "sage" : data.email);
		entity.add("kasumi", data.subject);
		entity.add("shampoo", data.comment);
		entity.add("password", data.password);
		entity.add("ajax", "1");
		entity.add("gb2", "thread");
		if (data.attachments != null) {
			data.attachments[0].addToEntity(entity, "file");
		} else {
			entity.add("nofile", "on");
		}
		if (data.captchaData != null) {
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
		}

		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildPath("wakaba.pl");
		JSONObject jsonObject = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		JSONObject dataObject = jsonObject.optJSONObject("data");
		String parent = dataObject != null ? CommonUtils.optJsonString(dataObject, "parent") : null;
		String num = dataObject != null ? CommonUtils.optJsonString(dataObject, "num") : null;
		if (parent != null && num != null) {
			if ("0".equals(parent)) {
				parent = num;
				num = null;
			}
			return new SendPostResult(parent, num);
		}

		int banned = jsonObject.optInt("banned", 0);
		if (banned != 0) {
			JSONArray jsonArray = jsonObject.optJSONArray("bans");
			if (jsonArray != null) {
				try {
					long expires = 0;
					JSONObject targetBanObject = null;
					for (int i = 0; i < jsonArray.length(); i++) {
						JSONObject banObject = jsonArray.getJSONObject(i);
						long itExpires = banObject.getLong("expires");
						if (itExpires == 0) {
							itExpires = Long.MAX_VALUE;
						} else {
							itExpires = itExpires * 1000L;
						}
						if (itExpires > expires) {
							expires = itExpires;
							targetBanObject = banObject;
						}
					}
					if (targetBanObject != null) {
						String ip = StringUtils.nullIfEmpty(CommonUtils.optJsonString(targetBanObject, "ip"));
						String reason = CommonUtils.getJsonString(targetBanObject, "reason");
						if (!StringUtils.isEmpty(ip)) {
							reason = reason + " (" + ip + ")";
						}
						throw new ApiException(ApiException.SEND_ERROR_BANNED, new ApiException.BanExtra()
								.setMessage(reason).setExpireDate(expires));
					}
				} catch (JSONException e) {
					throw new InvalidResponseException(e);
				}
			}
		}

		String error = CommonUtils.optJsonString(jsonObject, "error_msg");
		if (error == null) {
			throw new InvalidResponseException();
		}
		int errorType = 0;
		int flags = 0;
		if (error.contains("Нет этого кода подтверждения") || error.contains("Код подтверждения неверен")) {
			errorType = ApiException.SEND_ERROR_CAPTCHA;
		} else if (error.contains("Текст не введён")) {
			errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			flags |= ApiException.FLAG_KEEP_CAPTCHA;
		} else if (error.contains("Файл не выбран") || error.contains("Отправка не разрешена")) {
			errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			flags |= ApiException.FLAG_KEEP_CAPTCHA;
		} else if (error.contains("слишком большое")) {
			errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			flags |= ApiException.FLAG_KEEP_CAPTCHA;
		} else if (error.contains("Слишком много символов")) {
			errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			flags |= ApiException.FLAG_KEEP_CAPTCHA;
		} else if (error.contains("Треда не существует")) {
			errorType = ApiException.SEND_ERROR_NO_THREAD;
		} else if (error.contains("Не принимаю строку") || error.contains("Спамеры идут лесом")) {
			errorType = ApiException.SEND_ERROR_SPAM_LIST;
			flags |= ApiException.FLAG_KEEP_CAPTCHA;
		} else if (error.contains("Файл уже залит") || error.contains("Файл с тем же именем уже есть")) {
			errorType = ApiException.SEND_ERROR_FILE_EXISTS;
			flags |= ApiException.FLAG_KEEP_CAPTCHA;
		} else if (error.contains("Бан") || error.contains("Прокси")) {
			errorType = ApiException.SEND_ERROR_BANNED;
		} else if (error.contains("Обнаружен флуд") || error.contains("Попробуйте создать тред через")) {
			errorType = ApiException.SEND_ERROR_TOO_FAST;
		}
		if (errorType != 0) {
			throw new ApiException(errorType, flags);
		}
		CommonUtils.writeLog("Touhouchan send message", error);
		throw new ApiException(error);
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		TouhouchanChanLocator locator = TouhouchanChanLocator.get(this);
		Uri uri = locator.buildPath("wakaba.pl");
		UrlEncodedEntity entity = new UrlEncodedEntity("task", "delete", "board", data.boardName,
				"parent", data.threadNumber, "password", data.password, "ajax", "1");
		for (String postNumber : data.postNumbers) {
			entity.add("delete", postNumber);
		}
		JSONObject jsonObject = new HttpRequest(uri, data).setPostMethod(entity).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		String path = CommonUtils.optJsonString(jsonObject, "redir");
		if (!StringUtils.isEmpty(path)) {
			return null;
		}
		JSONArray jsonArray;
		String firstMessage;
		try {
			jsonArray = jsonObject.getJSONArray("error_msg");
			firstMessage = CommonUtils.getJsonString(jsonArray.getJSONObject(0), "error");
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if (jsonArray.length() < data.postNumbers.size()) {
			return null; // At least 1 post was deleted
		}
		int errorType = 0;
		String jsonAsString = jsonArray.toString();
		if (jsonAsString.contains("Неверный пароль") || jsonAsString.contains("Неверный IP")) {
			errorType = ApiException.DELETE_ERROR_PASSWORD;
		} else if (jsonAsString.contains("Тред закрыт")) {
			errorType = ApiException.DELETE_ERROR_NO_ACCESS;
		}
		if (errorType != 0) {
			throw new ApiException(errorType);
		}
		CommonUtils.writeLog("Touhouchan delete message", jsonArray);
		throw new ApiException(firstMessage);
	}
}