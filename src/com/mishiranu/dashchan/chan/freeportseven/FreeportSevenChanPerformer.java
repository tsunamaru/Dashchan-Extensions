package com.mishiranu.dashchan.chan.freeportseven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import chan.content.RedirectException;
import chan.http.CookieBuilder;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class FreeportSevenChanPerformer extends ChanPerformer {
	private static final String COOKIE_USER = "procyon_user";

	private void handleCookie(HttpHolder holder) {
		String userCookie = holder.getCookieValue(COOKIE_USER);
		if (userCookie != null) {
			FreeportSevenChanConfiguration configuration = FreeportSevenChanConfiguration.get(this);
			configuration.storeCookie(COOKIE_USER, "Procyon User", userCookie);
		}
	}

	private CookieBuilder buildCookies() {
		FreeportSevenChanConfiguration configuration = FreeportSevenChanConfiguration.get(this);
		String userCookie = configuration.getCookie(COOKIE_USER);
		if (userCookie != null) {
			return new CookieBuilder().append(COOKIE_USER, userCookie);
		}
		return null;
	}

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException,
			RedirectException {
		FreeportSevenChanLocator locator = FreeportSevenChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data).addCookie(buildCookies())
				.setValidator(data.validator).read().getString();
		handleCookie(data.holder);
		try {
			return new ReadThreadsResult(new FreeportSevenPostsParser(responseText, this, data.boardName)
					.convertThreads());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		FreeportSevenChanLocator locator = FreeportSevenChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data).addCookie(buildCookies())
				.setValidator(data.validator).read().getString();
		handleCookie(data.holder);
		try {
			return new ReadPostsResult(new FreeportSevenPostsParser(responseText, this, data.boardName).convertPosts());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		FreeportSevenChanLocator locator = FreeportSevenChanLocator.get(this);
		Uri uri = locator.buildPath(data.threadNumber + ".json");
		JSONObject jsonObject = new HttpRequest(uri, data).addCookie(buildCookies())
				.setValidator(data.validator).read().getJsonObject();
		handleCookie(data.holder);
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			return new ReadPostsCountResult(jsonObject.getJSONArray("posts").length());
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}
	private static final Pattern PATTERN_TOKEN = Pattern.compile("<input .*?authenticity_token.*?value=\"(.*?)\"");
	private static final Pattern PATTERN_CAPTCHA = Pattern.compile("<img .*?captcha_image.*?" +
			"src=\"(.*?)\".*?value=(.*?) ");

	private static final String CAPTCHA_TOKEN = "token";

	private static final ColorMatrixColorFilter CAPTCHA_FILTER = new ColorMatrixColorFilter(new float[]
			{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f});

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		FreeportSevenChanLocator locator = FreeportSevenChanLocator.get(this);
		Uri uri = locator.buildPath("new");
		String responseText = new HttpRequest(uri, data).addCookie(buildCookies()).read().getString();
		handleCookie(data.holder);
		Matcher matcher = PATTERN_TOKEN.matcher(responseText);
		if (!matcher.find()) {
			throw new InvalidResponseException();
		}
		String token = matcher.group(1);
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CAPTCHA_TOKEN, token);
		if (data.threadNumber != null) {
			return new ReadCaptchaResult(CaptchaState.SKIP, captchaData);
		}
		matcher = PATTERN_CAPTCHA.matcher(responseText);
		if (!matcher.find()) {
			throw new InvalidResponseException();
		}
		String path = matcher.group(1);
		String challenge = matcher.group(2);
		uri = locator.buildPath(path);
		Bitmap image = new HttpRequest(uri, data).addCookie(buildCookies()).read().getBitmap();
		if (image != null) {
			Bitmap newImage = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(newImage);
			canvas.drawColor(0xffffffff);
			Paint paint = new Paint();
			paint.setColorFilter(CAPTCHA_FILTER);
			canvas.drawBitmap(image, 0f, 0f, paint);
			image.recycle();
			captchaData.put(CaptchaData.CHALLENGE, challenge);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(newImage);
		} else {
			throw new InvalidResponseException();
		}
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("discussion[title]", StringUtils.emptyIfNull(data.subject));
		entity.add("post[text]", StringUtils.emptyIfNull(data.comment));
		if (data.threadNumber == null) {
			entity.add("discussion[tags][]", "b");
		} else {
			if (data.optionSage) {
				entity.add("post[sage]", "1");
			}
		}
		if (data.attachments != null) {
			data.attachments[0].addToEntity(entity, "attachment[file]");
		}
		if (data.captchaData != null) {
			entity.add("authenticity_token", data.captchaData.get(CAPTCHA_TOKEN));
			entity.add("captcha[challenge]", data.captchaData.get(CaptchaData.CHALLENGE));
			entity.add("captcha[response]", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT)));
		}

		FreeportSevenChanLocator locator = FreeportSevenChanLocator.get(this);
		Uri uri = locator.buildPath(data.threadNumber);
		JSONObject jsonObject = new HttpRequest(uri, data).setPostMethod(entity).addCookie(buildCookies())
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).addHeader("Accept", "application/json")
				.addHeader("X-Requested-With", "XMLHttpRequest").setSuccessOnly(false).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		String threadNumber = jsonObject.optString("redirect", null);
		if (threadNumber != null) {
			if (threadNumber.startsWith("/")) {
				threadNumber = threadNumber.substring(1);
			}
			return new SendPostResult(threadNumber, null);
		}
		String postNumber = jsonObject.optString("post_id", null);
		if (postNumber != null) {
			return new SendPostResult(data.threadNumber, postNumber);
		}
		JSONArray jsonArray = jsonObject.optJSONArray("errors");
		if (jsonArray != null && jsonArray.length() > 0) {
			String firstMessage = null;
			boolean captcha = false;
			boolean emptySubject = false;
			boolean emptyComment = false;
			boolean emptyFile = false;
			for (int i = 0; i < jsonArray.length(); i++) {
				String text = jsonArray.optString(i);
				if (text != null) {
					if (text.contains("Капча введена неверно")) {
						captcha = true;
					} else if (text.contains("Заголовок не может быть пустым")) {
						emptySubject = true;
					} else if (text.contains("Текст не может быть пустым")) {
						emptyComment = true;
					} else if (text.contains("Необходимо приложить файл")) {
						emptyFile = true;
					}
					if (firstMessage == null) {
						firstMessage = text;
					}
				}
			}
			if (captcha) {
				throw new ApiException(ApiException.SEND_ERROR_CAPTCHA);
			} else if (emptySubject) {
				throw new ApiException(ApiException.SEND_ERROR_EMPTY_SUBJECT);
			} else if (emptyComment) {
				throw new ApiException(ApiException.SEND_ERROR_EMPTY_COMMENT);
			} else if (emptyFile) {
				throw new ApiException(ApiException.SEND_ERROR_EMPTY_FILE);
			}
			CommonUtils.writeLog("FreeportSeven send message", jsonArray.toString());
			if (firstMessage != null) {
				throw new ApiException(firstMessage);
			}
			throw new InvalidResponseException();
		}
		CommonUtils.writeLog("FreeportSeven send message", jsonObject.toString());
		throw new InvalidResponseException();
	}
}