package com.mishiranu.dashchan.chan.ozuchan;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.net.Uri;

import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class OzuchanPostsParser
{
	private final String mSource;
	private final OzuchanChanConfiguration mConfiguration;
	private final OzuchanChanLocator mLocator;
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
		DateFormatSymbols symbols = new DateFormatSymbols();
		symbols.setShortMonths(new String[] {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг",
				"Сен", "Окт", "Ноя", "Дек"});
		DATE_FORMATS[0] = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", symbols);
		DATE_FORMATS[0].setTimeZone(TimeZone.getTimeZone("GMT+3"));
		DATE_FORMATS[1] = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
		DATE_FORMATS[1].setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}
	
	private static final Pattern FILE_SIZE = Pattern.compile("\\(([\\d\\.]+)(\\w+), (\\d+)x(\\d+)(?:, (.+))?\\)");
	private static final Pattern NUMBER = Pattern.compile("\\d+");
	
	public OzuchanPostsParser(String source, Object linked, String boardName)
	{
		mSource = source.replaceAll("<input type=\"checkbox\".*?>Не поднимать тред&nbsp;<br>", ""); // Fix parser;
		mConfiguration = OzuchanChanConfiguration.get(linked);
		mLocator = OzuchanChanLocator.get(linked);
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
	
	private static final TemplateParser<OzuchanPostsParser> PARSER = new TemplateParser<OzuchanPostsParser>()
			.equals("input", "name", "delete").open((instance, holder, tagName, attributes) ->
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
		return false;
		
	}).starts("td", "id", "reply").open((instance, holder, tagName, attributes) ->
	{
		String number = attributes.get("id").substring(5);
		Post post = new Post();
		post.setParentPostNumber(holder.mParent);
		post.setPostNumber(number);
		holder.mPost = post;
		return false;
		
	}).equals("span", "class", "filesize").content((instance, holder, text) ->
	{
		if (holder.mPost == null) holder.mPost = new Post();
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
			String onclick = attributes.get("onclick");
			if (onclick != null)
			{
				int start1 = onclick.indexOf("'http");
				if (start1 >= 0)
				{
					int end1 = onclick.indexOf('\'', start1 + 1);
					if (end1 >= 0)
					{
						String fileUriString = onclick.substring(start1 + 1, end1);
						holder.mAttachment.setFileUri(holder.mLocator, Uri.parse(fileUriString));
						int start2 = onclick.indexOf("'http", end1);
						if (start2 >= 0)
						{
							int end2 = onclick.indexOf('\'', start2 + 1);
							if (end2 >= 0)
							{
								String thumbnailUriString = onclick.substring(start1 + 1, end1);
								holder.mAttachment.setThumbnailUri(holder.mLocator, Uri.parse(thumbnailUriString));
							}
						}
						holder.mPost.setAttachments(holder.mAttachment);
					}
				}
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
		
	}).equals("span", "style", "color: red;").content((instance, holder, text) ->
	{
		String capcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim());
		if (capcode != null)
		{
			if (capcode.startsWith("## ") && capcode.endsWith(" ##"))
			{
				capcode = capcode.substring(3, capcode.length() - 3);
			}
			holder.mPost.setTripcode(capcode);
		}
		
	}).text((instance, holder, source, start, end) ->
	{
		if (holder.mHeaderHandling)
		{
			String text = source.substring(start, end).trim();
			if (text.length() > 0)
			{
				if (!text.contains("(")) text = text.substring(text.indexOf(' ') + 1);
				else text = text.substring(0, text.indexOf('(') - 1) + text.substring(text.indexOf(')') + 1);
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
		
	}).equals("div", "class", "message").content((instance, holder, text) ->
	{
		holder.mPost.setComment(text);
		holder.mPosts.add(holder.mPost);
		holder.mPost = null;
		holder.mAttachment = null;
		
	}).equals("div", "class", "omittedposts").content((instance, holder, text) ->
	{
		if (holder.mThreads != null)
		{
			Matcher matcher = NUMBER.matcher(text);
			if (matcher.find()) holder.mThread.addPostsCount(Integer.parseInt(matcher.group()));
		}
		
	}).equals("div", "class", "logo").content((instance, holder, text) ->
	{
		text = StringUtils.clearHtml(text);
		int index = text.indexOf("- ");
		if (index >= 0) text = text.substring(index + 2);
		holder.mConfiguration.storeBoardTitle(holder.mBoardName, text);
		
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