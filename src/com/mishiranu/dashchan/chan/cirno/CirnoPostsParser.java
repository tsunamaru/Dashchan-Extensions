package com.mishiranu.dashchan.chan.cirno;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;

import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class CirnoPostsParser
{
	private final String mSource;
	private final CirnoChanConfiguration mConfiguration;
	private final CirnoChanLocator mLocator;
	private final String mBoardName;
	
	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private boolean mHeaderHandling = false;
	
	private boolean mHasPostBlock = false;
	private boolean mHasPostBlockName = false;
	private boolean mHasPostBlockEmail = false;
	private boolean mHasPostBlockFile = false;
	private boolean mHasSpoilerCheckBox = false;
	
	static final SimpleDateFormat DATE_FORMAT;
	
	static
	{
		DateFormatSymbols symbols = new DateFormatSymbols();
		symbols.setShortWeekdays(new String[] {"", "Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"});
		symbols.setMonths(new String[] {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа",
				"сентября", "октября", "ноября", "декабря"});
		DATE_FORMAT = new SimpleDateFormat("EE dd MMMM yy HH:mm:ss", symbols);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}
	
	private static final Pattern FILE_SIZE = Pattern.compile("([\\d\\.]+) (\\w+), (\\d+)x(\\d+)(?:, (.+))?");
	private static final Pattern NAME_EMAIL = Pattern.compile("<a href=\"(.*?)\">(.*)</a>");
	private static final Pattern ADMIN_NAME = Pattern.compile("<span class=\"adminname\">(.*)</span>");
	private static final Pattern NUMBER = Pattern.compile("(\\d+)");
	private static final Pattern BUMP_LIMIT = Pattern.compile("Максимальное количество бампов треда: (\\d+).");
	
	public CirnoPostsParser(String source, Object linked, String boardName)
	{
		mSource = source;
		mConfiguration = CirnoChanConfiguration.get(linked);
		mLocator = CirnoChanLocator.get(linked);
		mBoardName = boardName;
	}
	
	private void closeThread()
	{
		if (mThread != null)
		{
			mThread.setPosts(mPosts);
			mThread.addPostsCount(mPosts.size());
			int postsWithFilesCount = 0;
			for (Post post : mPosts) postsWithFilesCount += post.getAttachmentsCount();
			mThread.addPostsWithFilesCount(postsWithFilesCount);
			mThreads.add(mThread);
			mPosts.clear();
		}
	}
	
	public ArrayList<Posts> convertThreads() throws ParseException
	{
		mThreads = new ArrayList<>();
		PARSER.parse(mSource, this);
		closeThread();
		if (mThreads.size() > 0)
		{
			updateConfiguration();
			return mThreads;
		}
		return null;
	}
	
	public ArrayList<Post> convertPosts() throws ParseException
	{
		PARSER.parse(mSource, this);
		if (mPosts.size() > 0)
		{
			updateConfiguration();
			return mPosts;
		}
		return null;
	}
	
	private void updateConfiguration()
	{
		if (mHasPostBlock)
		{
			mConfiguration.storeNamesEmailsImagesSpoilersEnabled(mBoardName, mHasPostBlockName, mHasPostBlockEmail,
					mHasPostBlockFile, mHasSpoilerCheckBox);
		}
	}
	
	private static final TemplateParser<CirnoPostsParser> PARSER = new TemplateParser<CirnoPostsParser>()
			.equals("input", "name", "delete").open((instance, holder, tagName, attributes) ->
	{
		if ("checkbox".equals(attributes.get("type")))
		{
			holder.mHeaderHandling = true;
			if (holder.mPost == null || holder.mPost.getPostNumber() == null)
			{
				String number = attributes.get("value");
				if (holder.mPost == null) holder.mPost = new Post();
				holder.mPost.setPostNumber(number);
				holder.mParent = number;
				if (holder.mThreads != null)
				{
					holder.closeThread();
					holder.mThread = new Posts();
				}
			}
		}
		return false;
		
	}).starts("td", "id", "reply").open((instance, holder, tagName, attributes) ->
	{
		String number = attributes.get("id").substring(5);
		Post post = new Post();
		post.setParentPostNumber(holder.mParent);
		post.setPostNumber(number);
		holder.mPost = post;
		return false;
		
	}).equals("span", "class", "filesize").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mPost == null) holder.mPost = new Post();
		holder.mAttachment = new FileAttachment();
		return false;
		
	}).name("a").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mAttachment != null)
		{
			holder.mAttachment.setFileUri(holder.mLocator, holder.mLocator.buildPath(attributes.get("href")));
		}
		return false;
		
	}).equals("img", "class", "thumb").open((instance, holder, tagName, attributes) ->
	{
		String src = attributes.get("src");
		if (src != null)
		{
			if (src.contains("/thumb/"))
			{
				holder.mAttachment.setThumbnailUri(holder.mLocator, holder.mLocator.buildPath(src));
			}
			holder.mAttachment.setSpoiler(src.contains("extras/icons/spoiler.png"));
		}
		holder.mPost.setAttachments(holder.mAttachment);
		holder.mAttachment = null;
		return false;
		
	}).equals("div", "class", "nothumb").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mAttachment.getSize() > 0 || holder.mAttachment.getWidth() > 0 || holder.mAttachment.getHeight() > 0)
		{
			holder.mPost.setAttachments(holder.mAttachment);
		}
		holder.mAttachment = null;
		return false;
		
	}).name("em").open((instance, holder, tagName, attributes) -> holder.mAttachment != null)
			.content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text);
		Matcher matcher = FILE_SIZE.matcher(text);
		if (matcher.matches())
		{
			float size = Float.parseFloat(matcher.group(1));
			String dim = matcher.group(2);
			if ("KB".equals(dim)) size *= 1024f;
			else if ("MB".equals(dim)) size *= 1024f * 1024f;
			int width = Integer.parseInt(matcher.group(3));
			int height = Integer.parseInt(matcher.group(4));
			String originalName = matcher.group(5);
			holder.mAttachment.setSize((int) size);
			holder.mAttachment.setWidth(width);
			holder.mAttachment.setHeight(height);
			holder.mAttachment.setOriginalName(StringUtils.isEmptyOrWhitespace(originalName) ? null : originalName);
		}
		
	}).equals("span", "class", "filetitle").equals("span", "class", "replytitle").content((instance, holder, text) ->
	{
		holder.mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
		
	}).equals("span", "class", "postername").equals("span", "class", "commentpostername")
			.content((instance, holder, text) ->
	{
		Matcher matcher = NAME_EMAIL.matcher(text);
		if (matcher.matches())
		{
			holder.mPost.setEmail(StringUtils.clearHtml(matcher.group(1)));
			text = matcher.group(2);
		}
		matcher = ADMIN_NAME.matcher(text);
		if (matcher.matches())
		{
			holder.mPost.setCapcode("Admin");
			text = matcher.group(1);
		}
		holder.mPost.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
		
	}).equals("span", "class", "postertrip").content((instance, holder, text) ->
	{
		holder.mPost.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
		
	}).text((instance, holder, source, start, end) ->
	{
		if (holder.mHeaderHandling)
		{
			String text = source.substring(start, end).trim();
			if (text.length() > 0)
			{
				try
				{
					holder.mPost.setTimestamp(DATE_FORMAT.parse(text).getTime());
				}
				catch (java.text.ParseException e)
				{
					
				}
				holder.mHeaderHandling = false;
			}
		}
		
	}).name("blockquote").content((instance, holder, text) ->
	{
		text = text.trim();
		int index = text.lastIndexOf("<div class=\"abbrev\">");
		if (index >= 0) text = text.substring(0, index).trim();
		holder.mPost.setComment(text);
		holder.mPosts.add(holder.mPost);
		holder.mPost = null;
		
	}).equals("span", "class", "omittedposts").content((instance, holder, text) ->
	{
		if (holder.mThreads != null)
		{
			Matcher matcher = NUMBER.matcher(text);
			if (matcher.find())
			{
				holder.mThread.addPostsCount(Integer.parseInt(matcher.group(1)));
				if (matcher.find()) holder.mThread.addPostsWithFilesCount(Integer.parseInt(matcher.group(1)));
			}
		}
		
	}).equals("div", "class", "rules").content((instance, holder, text) ->
	{
		Matcher matcher = BUMP_LIMIT.matcher(text);
		if (matcher.find())
		{
			int bumpLimit = Integer.parseInt(matcher.group(1));
			holder.mConfiguration.storeBumpLimit(holder.mBoardName, bumpLimit);
		}
		
	}).equals("div", "class", "logo").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text).trim();
		int index = text.indexOf("— ");
		if (index >= 0) text = text.substring(index + 2);
		if (!StringUtils.isEmpty(text)) holder.mConfiguration.storeBoardTitle(holder.mBoardName, text);
		
	}).equals("td", "class", "postblock").content((instance, holder, text) ->
	{
		holder.mHasPostBlock = true;
		if ("Имя".equals(text)) holder.mHasPostBlockName = true;
		else if ("E-mail".equals(text)) holder.mHasPostBlockEmail = true;
		else if ("Файл".equals(text)) holder.mHasPostBlockFile = true;
		
	}).equals("input", "name", "spoiler").open((instance, holder, tagName, attributes) ->
	{
		holder.mHasSpoilerCheckBox = true;
		return false;
		
	}).equals("table", "border", "1").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text);
		int index1 = text.lastIndexOf('[');
		int index2 = text.lastIndexOf(']');
		if (index1 >= 0 && index2 > index1)
		{
			text = text.substring(index1 + 1, index2);
			try
			{
				int pagesCount = Integer.parseInt(text) + 1;
				holder.mConfiguration.storePagesCount(holder.mBoardName, pagesCount);
			}
			catch (NumberFormatException e)
			{
				
			}
		}
		
	}).prepare();
}