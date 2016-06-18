package com.mishiranu.dashchan.chan.cablesix;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;
import android.util.Pair;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class CableSixChanPerformer extends ChanPerformer
{
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new CableSixPostsParser(responseText, this, data.boardName)
					.convertThreads(data.pageNumber));
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadPostsResult(new CableSixPostsParser(responseText, this, data.boardName).convertPosts());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath();
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new CableSixBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		int count = 0;
		int index = 0;
		while (index != -1)
		{
			index = responseText.indexOf("<div class=\"post ", index + 1);
			if (index != -1) count++;
		}
		return new ReadPostsCountResult(count);
	}
	
	private static final Pattern PATTERN_CAPTCHA_API_KEY = Pattern.compile("<script type=\"text/javascript\" "
			+ "src=\"//www.google.com/recaptcha/api/challenge\\?k=(.*?)\">");
	
	private final HashMap<String, Pair<HttpValidator, Boolean>> mReadCaptchaValidators = new HashMap<>();
	private String mCaptchaApiKey;
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		String captchaApiKey = null;
		Pair<HttpValidator, Boolean> pair = mReadCaptchaValidators.get(data.boardName);
		try
		{
			Uri uri = locator.createBoardUri(data.boardName, 0);
			String responseText = new HttpRequest(uri, data.holder, data).setValidator(pair != null ? pair.first : null)
					.read().getString();
			Matcher matcher = PATTERN_CAPTCHA_API_KEY.matcher(responseText);
			if (matcher.find())
			{
				captchaApiKey = matcher.group(1);
				mCaptchaApiKey = captchaApiKey;
			}
			pair = new Pair<>(data.holder.getValidator(), captchaApiKey != null);
			mReadCaptchaValidators.put(data.boardName, pair);
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED)
			{
				captchaApiKey = pair.second ? mCaptchaApiKey : null;
			}
			else throw e;
		}
		if (captchaApiKey == null) return new ReadCaptchaResult(CaptchaState.SKIP, null);
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.API_KEY, captchaApiKey);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
	}
	
	private void readAndApplyTinyboardAntispamFields(HttpHolder holder, HttpRequest.Preset preset,
			MultipartEntity entity, String boardName, String threadNumber) throws HttpException,
			InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		Uri uri = threadNumber != null ? locator.createThreadUri(boardName, threadNumber)
				: locator.createBoardUri(boardName, 0);
		String responseText = new HttpRequest(uri, holder, preset).read().getString();
		int start = responseText.indexOf("<form name=\"post\"");
		if (start == -1) throw new InvalidResponseException();
		responseText = responseText.substring(start, responseText.indexOf("</form>") + 7);
		ArrayList<Pair<String, String>> fields;
		try
		{
			fields = new TinyboardAntispamFieldsParser(responseText).convert();
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException();
		}
		for (Pair<String, String> field : fields) entity.add(field.first, field.second);
	}
	
	private static final HttpRequest.RedirectHandler POST_REDIRECT_HANDLER = new HttpRequest.RedirectHandler()
	{	
		@Override
		public Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
				throws HttpException
		{
			if (responseCode == HttpURLConnection.HTTP_SEE_OTHER) return Action.CANCEL;
			return STRICT.onRedirectReached(responseCode, requestedUri, redirectedUri, holder);
		}
	};
	
	private static final Pattern PATTERN_ERROR = Pattern.compile("<h2.*?>(.*?)</h2>");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("post", "Poster");
		entity.add("board", data.boardName);
		entity.add("thread", data.threadNumber);
		entity.add("name", data.name);
		entity.add("email", data.optionSage ? "sage" : data.email);
		entity.add("subject", data.subject);
		entity.add("body", StringUtils.emptyIfNull(data.comment));
		if (data.attachments != null)
		{
			SendPostData.Attachment attachment = data.attachments[0];
			attachment.addToEntity(entity, "file");
			if (attachment.optionSpoiler) entity.add("spoiler", "on");
			if ("nsfw".equals(attachment.rating)) entity.add("spoiler_nsfw", "on");
		}
		entity.add("password", data.password);
		if (data.captchaData != null)
		{
			entity.add("recaptcha_challenge_field", data.captchaData.get(CaptchaData.CHALLENGE));
			entity.add("recaptcha_response_field", data.captchaData.get(CaptchaData.INPUT));
		}
		readAndApplyTinyboardAntispamFields(data.holder, data, entity, data.boardName, data.threadNumber);

		CableSixChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("post.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(POST_REDIRECT_HANDLER).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER)
		{
			uri = data.holder.getRedirectedUri();
			return new SendPostResult(locator.getThreadNumber(uri), locator.getPostNumber(uri));
		}
		
		Matcher matcher = PATTERN_ERROR.matcher(responseText);
		if (matcher.find())
		{
			String errorMessage = matcher.group(1);
			if (errorMessage != null)
			{
				int errorType = 0;
				if (errorMessage.contains("lors de votre vérification"))
				{
					errorType = ApiException.SEND_ERROR_CAPTCHA;
				}
				else if (errorMessage.contains("Ce texte est trop court ou vide"))
				{
					errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
				}
				else if (errorMessage.contains("Vous devez mettre en ligne une image"))
				{
					errorType = ApiException.SEND_ERROR_EMPTY_FILE;
				}
				else if (errorMessage.contains("Flood détecté"))
				{
					errorType = ApiException.SEND_ERROR_TOO_FAST;
				}
				else if (errorMessage.contains("Your IP address"))
				{
					errorType = ApiException.SEND_ERROR_BANNED;
				}
				if (errorType != 0) throw new ApiException(errorType);
			}
			CommonUtils.writeLog("CableSix send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "delete", "on",
				"password", data.password);
		for (String postNumber : data.postNumbers) entity.add("delete_" + postNumber, "on");
		if (data.optionFilesOnly) entity.add("file", "on");
		Uri uri = locator.buildPath("post.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(POST_REDIRECT_HANDLER).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) return null;
		if (responseText != null)
		{
			Matcher matcher = PATTERN_ERROR.matcher(responseText);
			if (matcher.find())
			{
				String errorMessage = matcher.group(1);
				if (errorMessage.contains("Mot de passe incorrect"))
				{
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
				else if (errorMessage.contains("Vous devez encore attendre"))
				{
					throw new ApiException(ApiException.DELETE_ERROR_TOO_NEW);
				}
				CommonUtils.writeLog("CableSix delete message", errorMessage);
				throw new ApiException(errorMessage);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		CableSixChanLocator locator = ChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("board", data.boardName, "report", "on",
				"reason", data.comment);
		entity.add("password", ""); // "You look like a bot" fix
		for (String postNumber : data.postNumbers) entity.add("delete_" + postNumber, "on");
		Uri uri = locator.buildPath("post.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(POST_REDIRECT_HANDLER).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) return null;
		if (responseText != null)
		{
			Matcher matcher = PATTERN_ERROR.matcher(responseText);
			if (matcher.find())
			{
				String errorMessage = matcher.group(1);
				CommonUtils.writeLog("CableSix report message", errorMessage);
				throw new ApiException(errorMessage);
			}
		}
		throw new InvalidResponseException();
	}
}