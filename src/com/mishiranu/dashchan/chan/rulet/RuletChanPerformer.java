package com.mishiranu.dashchan.chan.rulet;

import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.util.SparseIntArray;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class RuletChanPerformer extends ChanPerformer
{
	private static final String COOKIE_SID = "sid";
	
	public HttpRequest modifyHttpRequst(HttpRequest httpRequest, String boardName)
	{
		if ("shi".equals(boardName)) httpRequest.addHeader("Authorization", "Basic YW5vbnltb3VzOnBvZHphYm9ybmlr");
		return httpRequest;
	}
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		RuletChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
				.setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new RuletPostsParser(responseText, this, data.boardName).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		RuletChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
				.setValidator(data.validator).setSuccessOnly(false).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_GONE) throw HttpException.createNotFoundException();
		data.holder.checkResponseCode();
		try
		{
			return new ReadPostsResult(new RuletPostsParser(responseText, this, data.boardName).convertPosts());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		RuletChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("menu.html");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new RuletBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		RuletChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
				.setValidator(data.validator).setSuccessOnly(false).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_GONE) throw HttpException.createNotFoundException();
		data.holder.checkResponseCode();
		int count = 0;
		int index = 0;
		while (index != -1)
		{
			count++;
			index = responseText.indexOf("<td class=\"reply\"", index + 1);
		}
		return new ReadPostsCountResult(count);
	}
	
	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		String boardName = ChanLocator.get(this).getBoardName(data.uri);
		return new ReadContentResult(modifyHttpRequst(new HttpRequest(data.uri, data.holder, data), boardName).read());
	}
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		RuletChanLocator locator = ChanLocator.get(this);
		RuletChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.buildPath(data.boardName, "api", "requires-captcha");
		String sidCookie = configuration.getCookie(COOKIE_SID);
		JSONObject jsonObject = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
				.addCookie(COOKIE_SID, sidCookie).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		String newSidCookie = data.holder.getCookieValue(COOKIE_SID);
		if (newSidCookie != null)
		{
			sidCookie = newSidCookie;
			configuration.storeCookie(COOKIE_SID, sidCookie, "SID");
		}
		boolean needCaptcha;
		try
		{
			needCaptcha = jsonObject.getInt("requires-captcha") != 0;
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.CHALLENGE, sidCookie);
		if (needCaptcha)
		{
			boolean strike = jsonObject.optInt("strike") != 0;
			uri = locator.buildPath(data.boardName, "captcha");
			String captchaLang = RuletChanConfiguration.CAPTCHA_TYPE_CYRILLIC.equals(data.captchaType)
					? "russian" : "english";
			Bitmap image = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
					.addCookie(COOKIE_SID, sidCookie).addCookie("tinaib-captcha", captchaLang).read().getBitmap();
			if (image == null) throw new InvalidResponseException();
			SparseIntArray colorsCount = new SparseIntArray();
			int[] pixels = new int[image.getWidth()];
			for (int j = 0; j < image.getHeight(); j++)
			{
				image.getPixels(pixels, 0, image.getWidth(), 0, j, image.getWidth(), 1);
				for (int i = 0; i < pixels.length; i++)
				{
					int color = pixels[i];
					int count = colorsCount.get(color) + 1;
					colorsCount.put(color, count);
				}
			}
			int mostOftenColor = 0;
			int mostOftenColorCount = 0;
			for (int i = 0, size = colorsCount.size(); i < size; i++)
			{
				int color = colorsCount.keyAt(i);
				int count = colorsCount.valueAt(i);
				if (count > mostOftenColorCount)
				{
					mostOftenColor = color;
					mostOftenColorCount = count;
				}
			}
			if (Color.alpha(mostOftenColor) == 0xff)
			{
				int mostOftenColorRed = Color.red(mostOftenColor);
				int mostOftenColorGreen = Color.green(mostOftenColor);
				int mostOftenColorBlue = Color.blue(mostOftenColor);
				Bitmap newImage = image.copy(Bitmap.Config.ARGB_8888, true);
				image.recycle();
				image = newImage;
				for (int j = 0; j < image.getHeight(); j++)
				{
					image.getPixels(pixels, 0, image.getWidth(), 0, j, image.getWidth(), 1);
					for (int i = 0; i < pixels.length; i++)
					{
						int color = pixels[i];
						int r = Color.red(color);
						int g = Color.green(color);
						int b = Color.blue(color);
						if ((r - 10 < mostOftenColorRed && r + 10 > mostOftenColorRed) &&
								(g - 10 < mostOftenColorGreen && g + 10 > mostOftenColorGreen) &&
								(b - 10 < mostOftenColorBlue && b + 10 > mostOftenColorBlue))
						{
							color = 0xffffffff;
						}
						else
						{
							r = 0xff * r / mostOftenColorRed / 2;
							g = 0xff * g / mostOftenColorGreen / 2;
							b = 0xff * b / mostOftenColorBlue / 2;
							int gray = (int) (Color.red(color) * 0.3f + Color.green(color) * 0.59f +
									Color.blue(color) * 0.11f);
							color = Color.argb(0xff, gray, gray, gray);
						}
						pixels[i] = color;
					}
					image.setPixels(pixels, 0, image.getWidth(), 0, j, image.getWidth(), 1);
				}
				if (strike)
				{
					float s = 8f;
					float width = image.getWidth();
					float height = image.getHeight();
					Path path = new Path();
					path.moveTo(0f, 0f);
					path.rLineTo(0f, s);
					path.rLineTo(s, -s);
					path.close();
					path.moveTo(width, 0f);
					path.rLineTo(0f, s);
					path.rLineTo(-s, -s);
					path.close();
					path.moveTo(0f, height);
					path.rLineTo(0f, -s);
					path.rLineTo(s, s);
					path.close();
					path.moveTo(width, height);
					path.rLineTo(0f, -s);
					path.rLineTo(-s, s);
					path.close();
					Canvas canvas = new Canvas(image);
					Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
					paint.setColor(0xff000000);
					paint.setStyle(Paint.Style.FILL);
					canvas.drawPath(path, paint);
				}
			}
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
		}
		return new ReadCaptchaResult(CaptchaState.SKIP, captchaData);
	}
	
	private static final Pattern PATTERN_POST_MESSAGE = Pattern.compile("(?s)<h2>(.*?)</h2>");
	private static final Pattern PATTERN_POST_URL = Pattern.compile("<meta http-equiv=\"refresh\" " +
			"content=\"(\\d+); *url=.*?(\\d+).html(?:#(\\d+))?\" />");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("parent", data.threadNumber == null ? "0" : data.threadNumber);
		entity.add("name", data.name);
		entity.add("email", data.optionSage ? "sage" : data.email);
		entity.add("subject", data.subject);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("password", data.password);
		entity.add("noko", "on");
		if (data.attachments != null)
		{
			data.attachments[0].addToEntity(entity, "file");
			if (data.attachments[0].optionSpoiler) entity.add("image_spoiler", "on");
		}
		String sidCookie = null;
		if (data.captchaData != null)
		{
			sidCookie = data.captchaData.get(CaptchaData.CHALLENGE);
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
		}
		
		boolean result;
		try
		{
			result = RuletCssTestPasser.getInstance(this).performTest(data.boardName, sidCookie, data.holder);
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
			{
				throw new ApiException(ApiException.SEND_ERROR_NO_BOARD);
			}
			throw e;
		}
		if (!result) throw new InvalidResponseException();
		RuletChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "post");
		String responseText = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
				.setPostMethod(entity).addCookie(COOKIE_SID, sidCookie)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
		
		Matcher matcher = PATTERN_POST_MESSAGE.matcher(responseText);
		if (matcher.find())
		{
			String message = matcher.group(1);
			if (message != null)
			{
				int errorType = 0;
				Object extra = null;
				if (message.contains("Пост добавлен"))
				{
					matcher = PATTERN_POST_URL.matcher(responseText);
					if (matcher.find())
					{
						int time = Integer.parseInt(matcher.group(1));
						if (time >= 0)
						{
							try
							{
								Thread.sleep(time * 1000);
							}
							catch (InterruptedException e)
							{
								Thread.currentThread().interrupt();
								return null;
							}
						}
						String threadNumber = matcher.group(2);
						String postNumber = data.threadNumber != null ? matcher.group(3) : null;
						return new SendPostResult(threadNumber, postNumber);
					}
					return null;
				}
				else if (message.contains("Неверно введена капча"))
				{
					errorType = ApiException.SEND_ERROR_CAPTCHA;
				}
				else if (message.contains("Для создания треда требуется картинка"))
				{
					errorType = ApiException.SEND_ERROR_EMPTY_FILE;
				}
				else if (message.contains("Введите сообщение или загрузите картинку"))
				{
					errorType = ApiException.SEND_ERROR_EMPTY_FILE;
				}
				else if (message.contains("некорректно указан тред"))
				{
					errorType = ApiException.SEND_ERROR_NO_THREAD;
				}
				else if (message.contains("Пост отклонен спамфильтром"))
				{
					errorType = ApiException.SEND_ERROR_SPAM_LIST;
				}
				else if (message.contains("Вам закрыт доступ к доске"))
				{
					errorType = ApiException.SEND_ERROR_BANNED;
					String text = StringUtils.clearHtml(message);
					int index = text.indexOf("Причина:");
					if (index >= 0)
					{
						ApiException.BanExtra banExtra = new ApiException.BanExtra();
						text = text.substring(index + 8).trim();
						index = text.indexOf("Бан истечет");
						String reason;
						if (index >= 0)
						{
							String dateString = text.substring(index + 11).trim();
							reason = text.substring(0, index).trim();
							try
							{
								long expireDate = RuletPostsParser.DATE_FORMAT.parse(dateString).getTime();
								banExtra.setExpireDate(expireDate);
							}
							catch (java.text.ParseException e)
							{
								
							}
						}
						else reason = text;
						banExtra.setMessage(reason);
						extra = banExtra;
					}
				}
				if (errorType != 0) throw new ApiException(errorType, extra);
			}
			CommonUtils.writeLog("Rulet send message", message);
			throw new ApiException(message);
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		RuletChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("password", data.password);
		for (String postNumber : data.postNumbers) entity.add("posts[]", postNumber);
		Uri uri = locator.buildPath(data.boardName, "delete");
		String responseText = modifyHttpRequst(new HttpRequest(uri, data.holder, data), data.boardName)
				.setPostMethod(entity).read().getString();
		Matcher matcher = PATTERN_POST_MESSAGE.matcher(responseText);
		if (matcher.find())
		{
			String message = matcher.group(1);
			if (message != null)
			{
				if (responseText.contains("Пост успешно удален"))
				{
					return null;
				}
				if (responseText.contains("Неверный пароль"))
				{
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
				CommonUtils.writeLog("Rulet delete message", message);
				throw new ApiException(message);
			}
		}
		throw new InvalidResponseException();
	}
}