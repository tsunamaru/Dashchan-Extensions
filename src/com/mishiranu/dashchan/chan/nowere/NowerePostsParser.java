package com.mishiranu.dashchan.chan.nowere;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class NowerePostsParser
{
	private final String mSource;
	private final NowereChanConfiguration mConfiguration;
	private final NowereChanLocator mLocator;
	private final String mBoardName;

	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();

	private boolean mHeaderHandling = false;

	private static final SimpleDateFormat DATE_FORMAT;

	static
	{
		DATE_FORMAT = new SimpleDateFormat("yy/MM/dd(EEE)hh:mm", Locale.US);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	private static final Pattern FILE_SIZE = Pattern.compile("([\\d\\.]+) (\\w+), (\\d+)x(\\d+)");
	private static final Pattern NAME_EMAIL = Pattern.compile("<a href=\"(.*?)\">(.*)</a>");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	public NowerePostsParser(String source, Object linked, String boardName)
	{
		mSource = source;
		mConfiguration = ChanConfiguration.get(linked);
		mLocator = ChanLocator.get(linked);
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
		return mThreads;
	}

	public ArrayList<Post> convertPosts() throws ParseException
	{
		PARSER.parse(mSource, this);
		return mPosts;
	}

	private static final TemplateParser<NowerePostsParser> PARSER = new TemplateParser<NowerePostsParser>()
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
		if (src != null && src.contains("/thumb/"))
		{
			holder.mAttachment.setThumbnailUri(holder.mLocator, holder.mLocator.buildPath(src));
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
		Matcher matcher = FILE_SIZE.matcher(text);
		if (matcher.matches())
		{
			float size = Float.parseFloat(matcher.group(1));
			String dim = matcher.group(2);
			if ("kB".equals(dim)) size *= 1024;
			else if ("MB".equals(dim)) size *= 1024 * 1024;
			int width = Integer.parseInt(matcher.group(3));
			int height = Integer.parseInt(matcher.group(4));
			holder.mAttachment.setSize((int) size);
			holder.mAttachment.setWidth(width);
			holder.mAttachment.setHeight(height);
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
			String email = StringUtils.clearHtml(matcher.group(1));
			if (email.toLowerCase(Locale.US).contains("sage")) holder.mPost.setSage(true);
			else holder.mPost.setEmail(email);
			text = matcher.group(2);
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
				holder.mThread.addPostsCount(Integer.parseInt(matcher.group()));
				if (matcher.find()) holder.mThread.addPostsWithFilesCount(Integer.parseInt(matcher.group()));
			}
		}

	}).equals("div", "class", "logo").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text).trim();
		if (!StringUtils.isEmpty(text)) holder.mConfiguration.storeBoardTitle(holder.mBoardName, text);

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