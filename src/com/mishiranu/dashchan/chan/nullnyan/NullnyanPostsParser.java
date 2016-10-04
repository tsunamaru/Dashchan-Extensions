package com.mishiranu.dashchan.chan.nullnyan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;

import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class NullnyanPostsParser
{
	private final String mSource;
	private final NullnyanChanConfiguration mConfiguration;
	private final NullnyanChanLocator mLocator;
	private final String mBoardName;

	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();

	private boolean mHeaderHandling = false;

	private static final SimpleDateFormat[] DATE_FORMATS;

	static
	{
		DATE_FORMATS = new SimpleDateFormat[2];
		DATE_FORMATS[0] = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.US);
		DATE_FORMATS[0].setTimeZone(TimeZone.getTimeZone("GMT+3"));
		DATE_FORMATS[1] = new SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.US);
		DATE_FORMATS[1].setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	private static final Pattern FILE_SIZE = Pattern.compile("\\[([\\d\\.]+)(\\w+), (\\d+)x(\\d+)(?:, (.+))?\\]");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	public NullnyanPostsParser(String source, Object linked, String boardName)
	{
		mSource = source;
		mConfiguration = NullnyanChanConfiguration.get(linked);
		mLocator = NullnyanChanLocator.get(linked);
		mBoardName = boardName;
	}

	private void closeThread()
	{
		if (mThread != null)
		{
			mThread.setPosts(mPosts);
			mThread.addPostsCount(mPosts.size());
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

	private static final TemplateParser<NullnyanPostsParser> PARSER = new TemplateParser<NullnyanPostsParser>()
			.starts("div", "id", "p").open((instance, holder, tagName, attributes) ->
	{
		String number = attributes.get("id").substring(1);
		holder.mPost = new Post();
		holder.mPost.setPostNumber(number);
		if ("OP".equalsIgnoreCase(attributes.get("class")))
		{
			holder.mParent = number;
			if (holder.mThreads != null)
			{
				holder.closeThread();
				holder.mThread = new Posts();
			}
		}
		else holder.mPost.setParentPostNumber(holder.mParent);
		return false;

	}).equals("i", "class", "label-checkbox").open((instance, holder, tagName, attributes) ->
	{
		holder.mHeaderHandling = true;
		return true; // Skip content

	}).contains("span", "class", "filesize").open((instance, nullnyanPostsParser, s, attributes) ->
	{
		String id = attributes.get("id");
		return id == null || !id.startsWith("exembed");

	}).content((instance, holder, text) ->
	{
		holder.mAttachment = new FileAttachment();
		text = StringUtils.clearHtml(text);
		Matcher matcher = FILE_SIZE.matcher(text);
		if (matcher.find())
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

	}).name("a").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mAttachment != null)
		{
			String href = attributes.get("href");
			if (href != null && (href.startsWith("src/") || href.contains("/src/")))
			{
				if (href.startsWith("../")) href = href.substring(3);
				href = "/" + holder.mBoardName + "/" + href;
				holder.mAttachment.setFileUri(holder.mLocator, holder.mLocator.buildPath(href));
				holder.mPost.setAttachments(holder.mAttachment);
			}
		}
		else
		{
			String id = attributes.get("id");
			if (id != null && id.startsWith("tiembed"))
			{
				String href = attributes.get("href");
				if (href.contains("youtube"))
				{
					href = href.replace("youtube-nocookie.com", "youtube.com").replace("/embed/", "/watch?v=");
				}
				EmbeddedAttachment attachment = EmbeddedAttachment.obtain(href);
				if (attachment != null) holder.mPost.setAttachments(attachment);
			}
		}
		return false;

	}).name("img").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mAttachment != null)
		{
			String src = attributes.get("src");
			if (src != null && (src.startsWith("thumb/") || src.contains("/thumb/")))
			{
				if (src.startsWith("../")) src = src.substring(3);
				src = "/" + holder.mBoardName + "/" + src;
				holder.mAttachment.setThumbnailUri(holder.mLocator, holder.mLocator.buildPath(src));
			}
		}
		return false;

	}).equals("span", "class", "filetitle").content((instance, holder, text) ->
	{
		holder.mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));

	}).name("a").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mHeaderHandling)
		{
			String href = attributes.get("href");
			if (href != null && href.startsWith("mailto:"))
			{
				if ("mailto:sage".equalsIgnoreCase(href)) holder.mPost.setSage(true);
				else holder.mPost.setEmail(StringUtils.clearHtml(href));
			}
		}
		return false;

	}).equals("span", "class", "postername").content((instance, holder, text) ->
	{
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
				text = text.substring(text.indexOf(' ') + 1);
				for (SimpleDateFormat dateFormat : DATE_FORMATS)
				{
					try
					{
						holder.mPost.setTimestamp(dateFormat.parse(text).getTime());
						break;
					}
					catch (java.text.ParseException e)
					{

					}
				}
				holder.mHeaderHandling = false;
			}
		}

	}).contains("i", "class", "icon-stickied").open((instance, holder, tagName, attributes) ->
	{
		holder.mPost.setSticky(true);
		return false;

	}).contains("i", "class", "icon-closed").open((instance, holder, tagName, attributes) ->
	{
		holder.mPost.setClosed(true);
		return false;

	}).equals("div", "class", "message").content((instance, holder, text) ->
	{
		int index = text.lastIndexOf("<span class=\"red-text\">(USER WAS BANNED FOR THIS POST)</span>");
		if (index >= 0)
		{
			text = text.substring(0, index);
			holder.mPost.setPosterBanned(true);
		}
		// Remove "Text wall" buttons
		text = text.replaceAll("<span class=\"textwall\">.*?<span>", "");
		// Fix links
		text = text.replaceAll("<a class=\"postlink\" href=\"(?:../)?res/",
				"<a class=\"postlink\" href=\"/" + holder.mBoardName + "/res/");
		holder.mPost.setComment(text);
		holder.mPosts.add(holder.mPost);
		holder.mPost = null;
		holder.mAttachment = null;

	}).equals("span", "class", "omittedposts").content((instance, holder, text) ->
	{
		if (holder.mThreads != null)
		{
			Matcher matcher = NUMBER.matcher(text);
			if (matcher.find()) holder.mThread.addPostsCount(Integer.parseInt(matcher.group()));
		}

	}).equals("span", "class", "logo center").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text);
		int index = text.indexOf("- ");
		if (index >= 0) text = text.substring(index + 2);
		holder.mConfiguration.storeBoardTitle(holder.mBoardName, text);

	}).equals("nav", "class", "pagenavigator").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text.replace("><", "> <"));
		String pagesCount = null;
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) pagesCount = matcher.group();
		if (pagesCount != null)
		{
			holder.mConfiguration.storePagesCount(holder.mBoardName, Integer.parseInt(pagesCount) + 1);
		}

	}).prepare();
}