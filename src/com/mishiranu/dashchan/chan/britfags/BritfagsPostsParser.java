package com.mishiranu.dashchan.chan.britfags;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class BritfagsPostsParser
{
	private final String mSource;
	private final BritfagsChanConfiguration mConfiguration;
	private final BritfagsChanLocator mLocator;
	private final String mBoardName;

	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	private boolean mExpand = false;

	private static final SimpleDateFormat DATE_FORMAT;

	static
	{
		DATE_FORMAT = new SimpleDateFormat("dd MMMM yyyy hh:mm a", Locale.US);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+1"));
	}

	static final Pattern PATTERN_NUMBER = Pattern.compile("\\d+");
	private static final Pattern PATTERN_FILE_INFO = Pattern.compile("'(\\d+)'");

	public BritfagsPostsParser(String source, Object linked, String boardName)
	{
		mSource = source;
		mConfiguration = BritfagsChanConfiguration.get(linked);
		mLocator = BritfagsChanLocator.get(linked);
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

	private static final TemplateParser<BritfagsPostsParser> PARSER = new TemplateParser<BritfagsPostsParser>()
			.starts("div", "id", "thread").open((instance, holder, tagName, attributes) ->
	{
		holder.mParent = null;
		if (holder.mThreads != null)
		{
			holder.closeThread();
			holder.mThread = new Posts();
		}
		return false;

	}).starts("td", "id", "reply").open((instance, holder, tagName, attributes) ->
	{
		String number = attributes.get("id").substring(5);
		if (holder.mPost == null) holder.mPost = new Post();
		holder.mPost.setPostNumber(number);
		if (holder.mParent == null) holder.mParent = number;
		else holder.mPost.setParentPostNumber(holder.mParent);
		return false;

	}).equals("a", "class", "fileinfo").open((instance, holder, tagName, attributes) ->
	{
		holder.mAttachment = new FileAttachment();
		holder.mAttachment.setFileUri(holder.mLocator, holder.mLocator.buildPath(attributes.get("href")));
		Matcher matcher = PATTERN_FILE_INFO.matcher(attributes.get("onclick"));
		if (matcher.find())
		{
			if (matcher.find())
			{
				holder.mAttachment.setWidth(Integer.parseInt(matcher.group(1)));
				if (matcher.find()) holder.mAttachment.setHeight(Integer.parseInt(matcher.group(1)));
			}
		}
		if (holder.mPost == null) holder.mPost = new Post();
		holder.mPost.setAttachments(holder.mAttachment);
		return false;

	}).equals("img", "class", "thumb").open((instance, holder, tagName, attributes) ->
	{
		holder.mAttachment.setThumbnailUri(holder.mLocator, holder.mLocator.buildPath(attributes.get("src")));
		return false;

	}).equals("span", "class", "filetitle").content((instance, holder, text) ->
	{
		holder.mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));

	}).equals("span", "class", "postername").content((instance, holder, text) ->
	{
		holder.mPost.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));

	}).equals("span", "class", "admin").equals("span", "class", "mod").content((instance, holder, text) ->
	{
		holder.mPost.setCapcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()
				.replaceAll("^(?:## )?(.*?)(?: ##)?$", "$1")));

	}).equals("span", "class", "posterdate").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text).trim();
		int index1 = text.indexOf(' ');
		if (index1 >= 3) text = text.substring(0, index1 - 2) + text.substring(index1); // Remove st, nd, rd, th
		index1 = text.indexOf('\n');
		if (index1 >= 0)
		{
			int index2 = text.indexOf(' ', index1);
			if (index2 >= 0)
			{
				text = text.substring(0, index1) + text.substring(index2);
				try
				{
					holder.mPost.setTimestamp(DATE_FORMAT.parse(text).getTime());
				}
				catch (java.text.ParseException e)
				{

				}
			}
		}

	}).equals("img", "src", "/img/lockedpost.png").open((i, h, t, s) -> !h.mPost.setClosed(true).isClosed())
			.equals("img", "src", "/img/stickypost.png").open((i, h, t, s) -> !h.mPost.setSticky(true).isSticky())
			.name("blockquote").content((instance, holder, text) ->
	{
		holder.mPost.setComment(text);
		holder.mPosts.add(holder.mPost);
		holder.mPost = null;
		holder.mAttachment = null;

	}).equals("span", "class", "omittedposts").content((instance, holder, text) ->
	{
		if (holder.mThreads != null)
		{
			Matcher matcher = PATTERN_NUMBER.matcher(text);
			if (matcher.find())
			{
				holder.mThread.addPostsCount(Integer.parseInt(matcher.group()));
				if (matcher.find()) holder.mThread.addPostsWithFilesCount(Integer.parseInt(matcher.group()));
			}
		}

	}).equals("div", "class", "boarddescription").content((instance, holder, text) ->
	{
		holder.mConfiguration.storeBoardTitle(holder.mBoardName, StringUtils.clearHtml(text).trim());

	}).equals("table", "border", "1").content((instance, holder, text) ->
	{
		String count = null;
		Matcher matcher = PATTERN_NUMBER.matcher(StringUtils.clearHtml(text));
		while (matcher.find()) count = matcher.group();
		if (count != null) holder.mConfiguration.storePagesCount(holder.mBoardName, Integer.parseInt(count) + 1);

	}).prepare();
}