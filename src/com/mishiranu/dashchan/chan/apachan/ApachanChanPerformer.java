package com.mishiranu.dashchan.chan.apachan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class ApachanChanPerformer extends ChanPerformer
{
	private static final Pattern CATEGORY_PATTERN = Pattern.compile("<input type=\"hidden\" name=\"category\" " +
			"value=\"(\\d+)\">");
	
	private final HashMap<String, String> mCategoryMap = new HashMap<>();
	
	private void addBoardCategory(String boardName, String responseText)
	{
		Matcher matcher = CATEGORY_PATTERN.matcher(responseText);
		if (matcher.find())
		{
			String category = matcher.group(1);
			synchronized (mCategoryMap)
			{
				mCategoryMap.put(boardName, category);
			}
		}
	}
	
	private String getBoardCategory(String boardName)
	{
		synchronized (mCategoryMap)
		{
			return mCategoryMap.get(boardName);
		}
	}
	
	private static final String COOKIE_VISITOR = "visitor";
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		if ("all".equals(data.boardName)) throw new InvalidResponseException();
		ApachanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		addBoardCategory(data.boardName, responseText);
		try
		{
			return new ReadThreadsResult(new ApachanPostsParser(responseText, this, data.boardName).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	private static final Pattern PATTERN_ROOT_BOARD_LINK = Pattern.compile("<a href='/([a-z]+).html' target='_self'>" +
			"В корень раздела</a>");
	
	private static final Pattern PATTERN_ROOT_THREAD_LINK = Pattern.compile("<a href='(\\d+).html' target='_self'>" +
			"В корень треда</a>");
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		ApachanChanLocator locator = ChanLocator.get(this);
		String lastPostNumber = data.partialThreadLoading ? data.lastPostNumber : null;
		Uri uri = lastPostNumber != null ? locator.buildPath(data.threadNumber + ".html")
				: locator.buildQuery("new.php", "id", data.threadNumber, "t", "0");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		addBoardCategory(data.boardName, responseText);
		if (responseText.startsWith("Такой страницы не существует")) throw HttpException.createNotFoundException();
		String realBoardName = null;
		String realThreadNumber = null;
		Matcher matcher = PATTERN_ROOT_BOARD_LINK.matcher(responseText);
		if (matcher.find()) realBoardName = matcher.group(1);
		if (realBoardName == null) realBoardName = data.boardName;
		matcher = PATTERN_ROOT_THREAD_LINK.matcher(responseText);
		if (matcher.find()) realThreadNumber = matcher.group(1);
		if (realThreadNumber == null) realThreadNumber = data.threadNumber;
		if (!realBoardName.equals(data.boardName) || !realThreadNumber.equals(data.threadNumber))
		{
			throw new ThreadRedirectException(realBoardName, realThreadNumber,
					realThreadNumber.equals(data.threadNumber) ? null : data.threadNumber);
		}
		HashSet<String> existingPostNumbers = new HashSet<>();
		if (data.cachedPosts != null)
		{
			Post[] posts = data.cachedPosts.getPosts();
			if (posts != null)
			{
				for (Post post : posts) existingPostNumbers.add(post.getPostNumber());
			}
		}
		if (lastPostNumber != null)
		{
			try
			{
				ArrayList<Post> posts = new ApachanPostsParser(responseText, this, data.boardName)
						.convertPosts(data.threadNumber, existingPostNumbers);
				if (posts != null)
				{
					for (Post post : posts)
					{
						if (lastPostNumber.equals(post.getPostNumber())) return new ReadPostsResult(posts);
					}
				}
			}
			catch (ParseException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		uri = locator.buildQuery("new.php", "id", data.threadNumber, "t", "0");
		responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			ArrayList<Post> posts = new ApachanPostsParser(responseText, this, data.boardName)
					.convertPosts(data.threadNumber, existingPostNumbers);
			return posts != null ? new ReadPostsResult(posts) : null;
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		ApachanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.postNumber + ".html");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		addBoardCategory(data.boardName, responseText);
		if (responseText.startsWith("Такой страницы не существует")) throw HttpException.createNotFoundException();
		String threadNumber = null;
		Matcher matcher = PATTERN_ROOT_THREAD_LINK.matcher(responseText);
		if (matcher.find()) threadNumber = matcher.group(1);
		if (threadNumber == null) threadNumber = data.postNumber;
		try
		{
			ArrayList<Post> posts = new ApachanPostsParser(responseText, this, data.boardName)
					.convertPosts(threadNumber, null);
			return posts != null ? new ReadSinglePostResult(posts.get(0)) : null;
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		ApachanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new ApachanBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	private static final Pattern PATTERN_PAGES = Pattern.compile("<center>Страницы:.*?</center>");
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		ApachanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.threadNumber + ".html");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		if (responseText.startsWith("Такой страницы не существует")) throw HttpException.createNotFoundException();
		Matcher matcher = PATTERN_PAGES.matcher(responseText);
		int count = 0;
		if (matcher.find())
		{
			int pagesCount = ApachanPostsParser.extractPagesCount(matcher.group());
			if (pagesCount > 1)
			{
				uri = locator.buildPath(data.threadNumber + "_" + pagesCount + ".html");
				responseText = new HttpRequest(uri, data.holder, data).read().getString();
				count += 50 * (pagesCount - 1);
			}
		}
		int index = 0;
		while ((index = responseText.indexOf("<table align='center' width='780'", index + 1)) != -1) count++;
		return new ReadPostsCountResult(count);
	}
	
	private static final Pattern PATTERN_VERIFY_CAPTCHA_DATA = Pattern.compile("<input type='hidden' name='(.*?)' " +
			"value='(.*?)'>");
	private static final Pattern PATTERN_VERIFY_ANSWER = Pattern.compile("name='word' value='(.*?)'");
	private static final Pattern PATTERN_VERIFY_IMAGE = Pattern.compile("src='(capture/.*?)'");
	
	private static void updateVisitorCookie(HttpHolder holder, ApachanChanConfiguration configuration)
	{
		String value = holder.getCookieValue(COOKIE_VISITOR);
		if (value != null) configuration.storeCookie(COOKIE_VISITOR, value, "Visitor");
	}

	private static final Pattern PATTERN_REPLY_POST = Pattern.compile(">>(\\d+)");
	private static final Pattern PATTERN_FIRST_REPLY = Pattern.compile("^#(\\d+)(?: |\n)?");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		String replyPostNumber = null;
		String comment = data.comment;
		if (comment != null)
		{
			// Replace >>replies with #replies
			// Make single #reply reply thread number
			boolean canReply = data.threadNumber != null;
			Matcher matcher = PATTERN_REPLY_POST.matcher(comment);
			int count = 0;
			StringBuffer buffer = null;
			while (matcher.find())
			{
				if (buffer == null) buffer = new StringBuffer();
				matcher.appendReplacement(buffer, "#$1");
				count++;
			}
			if (buffer != null)
			{
				matcher.appendTail(buffer);
				comment = buffer.toString();
			}
			if (count == 1 && canReply)
			{
				matcher = PATTERN_FIRST_REPLY.matcher(comment);
				if (matcher.find())
				{
					replyPostNumber = matcher.group(1);
					comment = matcher.replaceAll("");
				}
			}
			// Replace >quote with [quote]quote[/quote]
			String[] lines = comment.split("\n", -1);
			StringBuilder builder = new StringBuilder();
			boolean inQuote = false;
			for (String line : lines)
			{
				boolean quote = line.startsWith(">");
				if (!quote && inQuote)
				{
					inQuote = false;
					builder.append("[/quote]");
				}
				if (builder.length() > 0) builder.append('\n');
				if (quote)
				{
					int from = 1;
					if (line.length() >= 2 && line.charAt(1) == ' ') from = 2;
					line = line.substring(from);
					if (!inQuote)
					{
						inQuote = true;
						builder.append("[quote]");
					}
				}
				builder.append(line);
			}
			comment = builder.toString();
		}
		if (replyPostNumber == null) replyPostNumber = data.threadNumber;
		
		ApachanChanLocator locator = ChanLocator.get(this);
		ApachanChanConfiguration configuration = ChanConfiguration.get(this);
		String category = getBoardCategory(data.boardName);
		boolean updateCategory = replyPostNumber == null && category == null;
		boolean invalidateVisitor = configuration.getCookie(COOKIE_VISITOR) == null;
		if (updateCategory || invalidateVisitor)
		{
			Uri uri = locator.createBoardUri(data.boardName, 0);
			String responseText = new HttpRequest(uri, data.holder, data).read().getString();
			addBoardCategory(data.boardName, responseText);
			if (updateCategory)
			{
				category = getBoardCategory(data.boardName);
				if (category == null) throw new InvalidResponseException();
			}
			if (invalidateVisitor) updateVisitorCookie(data.holder, configuration);
		}
		
		MultipartEntity entity = new MultipartEntity();
		entity.setEncoding("windows-1251");
		entity.add("category", category);
		entity.add("parent_id", replyPostNumber == null ? "0" : replyPostNumber);
		if (data.optionSage) entity.add("no_up", "on");
		entity.add("title", data.subject);
		entity.add("data", StringUtils.emptyIfNull(comment));
		if (data.attachments != null) data.attachments[0].addToEntity(entity, "userfile");

		String responseText;
		while (true)
		{
			Uri uri = locator.buildPath("post.php");
			responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
					.addCookie(COOKIE_VISITOR, configuration.getCookie(COOKIE_VISITOR)).read().getString();
			updateVisitorCookie(data.holder, configuration);
			if (responseText.contains("пост принят, осталось только ввести капчу"))
			{
				HashMap<String, String> captchaData = new HashMap<>();
				Matcher matcher = PATTERN_VERIFY_CAPTCHA_DATA.matcher(responseText);
				while (matcher.find())
				{
					captchaData.put(StringUtils.unescapeHtml(matcher.group(1)),
							StringUtils.unescapeHtml(matcher.group(2)));
				}
				
				ArrayList<String> answers = new ArrayList<>();
				matcher = PATTERN_VERIFY_ANSWER.matcher(responseText);
				while (matcher.find()) answers.add(StringUtils.unescapeHtml(matcher.group(1)));
				if (answers.isEmpty()) throw new InvalidResponseException();
				matcher = PATTERN_VERIFY_IMAGE.matcher(responseText);
				if (!matcher.find()) throw new InvalidResponseException();
				uri = locator.buildPath(matcher.group(1));
				Bitmap bitmap = new HttpRequest(uri, data.holder, data).addCookie(COOKIE_VISITOR,
						configuration.getCookie(COOKIE_VISITOR)).read().getBitmap();
				if (bitmap == null) throw new InvalidResponseException();
				uri = locator.buildPath("ac.php");
				responseText = new HttpRequest(uri, data.holder, data).addCookie(COOKIE_VISITOR,
						configuration.getCookie(COOKIE_VISITOR)).read().getString();
				int index = responseText.indexOf("value='");
				if (index >= 0)
				{
					index += 7;
					String value = responseText.substring(index, responseText.indexOf('\'', index));
					captchaData.put("mval", value);
				}

				ArrayList<Bitmap> answerBitmaps = new ArrayList<>();
				Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
				Rect bounds = new Rect();
				Paint.FontMetrics metrics = new Paint.FontMetrics();
				for (String answer : answers)
				{
					String[] lines = answer.trim().split(" +", -1);
					Bitmap answerBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
					Canvas canvas = new Canvas(answerBitmap);
					canvas.drawColor(0xffcccccc);
					float fontSize = 48f;
					paint.setTextSize(fontSize);
					float width = 0f;
					for (String line : lines) width = Math.max(width, paint.measureText(line));
					float maxWidth = answerBitmap.getWidth() * 0.9f;
					if (width >= maxWidth)
					{
						fontSize *= maxWidth / width;
						paint.setTextSize(fontSize);
						paint.getTextBounds(answer, 0, answer.length(), bounds);
					}
					paint.getFontMetrics(metrics);
					float height = metrics.bottom - metrics.top;
					float totalHeight = lines.length * height;
					int i = 0;
					for (String line : lines)
					{
						width = paint.measureText(line);
						canvas.drawText(line, (answerBitmap.getWidth() - width) / 2f, (answerBitmap.getHeight() -
								totalHeight) / 2f + (++i * height) - metrics.bottom, paint);
					}
					answerBitmaps.add(answerBitmap);
				}
				Integer result = requireUserImageSingleChoice(-1, answerBitmaps.toArray
						(new Bitmap[answerBitmaps.size()]), null, bitmap);
				if (result == null) throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
				String word = result >= 0 ? answers.get(result) : "";
				
				UrlEncodedEntity verifyEntity = new UrlEncodedEntity();
				verifyEntity.setEncoding("windows-1251");
				for (HashMap.Entry<String, String> entry : captchaData.entrySet())
				{
					verifyEntity.add(entry.getKey(), entry.getValue());
				}
				verifyEntity.add("word", word);
				uri = locator.buildPath("verify.php");
				responseText = new HttpRequest(uri, data.holder, data).setPostMethod(verifyEntity)
						.addCookie(COOKIE_VISITOR, configuration.getCookie(COOKIE_VISITOR)).read().getString();
				if (responseText.contains("Прошло больше") || responseText.contains("Капча введена не правильно"))
				{
					continue;
				}
			}
			break;
		}
		if (responseText.contains("Ваш Комментарий Принят"))
		{
			int index = responseText.indexOf("#t");
			if (index >= 0)
			{
				String postNumber = responseText.substring(index + 2, responseText.indexOf('\'', index));
				return new SendPostResult(data.threadNumber == null ? postNumber : data.threadNumber,
						data.threadNumber == null ? null : postNumber);
			}
		}
		else if (responseText.contains("Вы не можете запостить новый тред без картинки"))
		{
			throw new ApiException(ApiException.SEND_ERROR_EMPTY_FILE);
		}
		else if (responseText.contains("Вы не можете запостить пустое сообщение без картинки") ||
				responseText.contains("без текста и заголовка"))
		{
			throw new ApiException(ApiException.SEND_ERROR_EMPTY_COMMENT);
		}
		else if (responseText.contains("Слишком много букв"))
		{
			throw new ApiException(ApiException.SEND_ERROR_FIELD_TOO_LONG);
		}
		else if (responseText.contains("Комментария, на который вы пытаетесь ответить, не существует"))
		{
			throw new ApiException(ApiException.SEND_ERROR_NO_THREAD);
		}
		else if (responseText.contains("Не надо этого делать.") || responseText.contains("А не соснуть ли тебе"))
		{
			configuration.storeCookie(COOKIE_VISITOR, null, null);
			throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
		}
		else if (responseText.contains("Этот IP временно забанен") || responseText.contains("Вы временно забанены"))
		{
			int index = responseText.indexOf("Причина: ");
			ApiException.BanExtra banExtra = null;
			if (index >= 0)
			{
				String reason = responseText.substring(9);
				banExtra = new ApiException.BanExtra().setMessage(reason);
			}
			throw new ApiException(ApiException.SEND_ERROR_BANNED, banExtra);
		}
		String errorMessage = StringUtils.clearHtml(responseText);
		CommonUtils.writeLog("Apachan send message", errorMessage);
		throw new ApiException(errorMessage);
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		ApachanChanLocator locator = ChanLocator.get(this);
		ApachanChanConfiguration configuration = ChanConfiguration.get(this);
		String postNumber = data.postNumbers.get(0);
		Uri uri = locator.buildQuery("delete.php", "id", postNumber);
		Uri postUri = locator.createThreadUri(data.boardName, postNumber);
		String responseText = new HttpRequest(uri, data.holder, data).addCookie(COOKIE_VISITOR,
				configuration.getCookie(COOKIE_VISITOR)).addHeader("Referer", postUri.toString())
				.setSuccessOnly(false).read().getString();
		if (responseText != null)
		{
			if (responseText.isEmpty())
			{
				throw new ApiException(ApiException.DELETE_ERROR_NO_ACCESS);
			}
			else if (responseText.contains("Удалено"))
			{
				responseText = new HttpRequest(postUri, data.holder, data).read().getString();
				if (responseText.startsWith("Такой страницы не существует")) return null;
				throw new ApiException(ApiException.DELETE_ERROR_NO_ACCESS);
			}
			else if (responseText.contains("Комментарий слишком старый"))
			{
				throw new ApiException(ApiException.DELETE_ERROR_TOO_OLD);
			}
			CommonUtils.writeLog("Apachan delete message", responseText);
			throw new ApiException(responseText);
		}
		throw new InvalidResponseException();
	}
}