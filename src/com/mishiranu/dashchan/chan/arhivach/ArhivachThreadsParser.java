package com.mishiranu.dashchan.chan.arhivach;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;
import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class ArhivachThreadsParser implements GroupParser.Callback
{
	private final String mSource;
	private final ArhivachChanConfiguration mConfiguration;
	private final ArhivachChanLocator mLocator;
	private final boolean mHandlePagesCount;

	private Post mPost;
	private final ArrayList<Pair<Post, Integer>> mPostHolders = new ArrayList<>();
	private final ArrayList<FileAttachment> mAttachments = new ArrayList<>();

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_POSTS_COUNT = 1;
	private static final int EXPECT_THUMBNAIL = 2;
	private static final int EXPECT_COMMENT = 3;
	private static final int EXPECT_DATE = 4;
	private static final int EXPECT_PAGES_COUNT = 5;
	
	private int mExpect = EXPECT_NONE;
	
	private static final Pattern SUBJECT_PATTERN = Pattern.compile("^<b>(.*?)</b> &mdash; ");
	
	public ArhivachThreadsParser(String source, Object linked, boolean handlePagesCount)
	{
		mSource = source;
		mConfiguration = ChanConfiguration.get(linked);
		mLocator = ChanLocator.get(linked);
		mHandlePagesCount = handlePagesCount;
	}
	
	public ArrayList<Posts> convertThreads() throws ParseException
	{
		GroupParser.parse(mSource, this);
		if (mPostHolders.size() > 0)
		{
			ArrayList<Posts> threads = new ArrayList<>(mPostHolders.size());
			for (Pair<Post, Integer> holder : mPostHolders)
			{
				threads.add(new Posts(holder.first).addPostsCount(holder.second));
			}
			return threads;
		}
		return null;
	}
	
	public ArrayList<Post> convertPosts() throws ParseException
	{
		GroupParser.parse(mSource, this);
		if (mPostHolders.size() > 0)
		{
			ArrayList<Post> posts = new ArrayList<>(mPostHolders.size());
			for (Pair<Post, Integer> holder : mPostHolders) posts.add(holder.first);
			return posts;
		}
		return null;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("tr".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if (id != null && id.startsWith("thread_row_"))
			{
				Post post = new Post();
				mPost = post;
				String number = id.substring(11);
				post.setThreadNumber(number);
				post.setPostNumber(number);
				mAttachments.clear();
			}
		}
		else if (mPost != null)
		{
			if ("td".equals(tagName))
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("thread_date".equals(cssClass))
				{
					mExpect = EXPECT_DATE;
					return true;
				}
			}
			else if ("div".equals(tagName))
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("thread_text".equals(cssClass))
				{
					mExpect = EXPECT_COMMENT;
					return true;
				}
			}
			else if ("span".equals(tagName))
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("thread_posts_count".equals(cssClass))
				{
					mExpect = EXPECT_POSTS_COUNT;
					return true;
				}
			}
			else if ("a".equals(tagName))
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("expand_image".equals(cssClass))
				{
					String onclick = parser.getAttr(attrs, "onclick");
					if (onclick != null)
					{
						int start = onclick.indexOf("http");
						if (start >= 0)
						{
							int end = onclick.indexOf("'", start);
							if (end >= 0)
							{
								FileAttachment attachment = new FileAttachment();
								mAttachments.add(attachment);
								String uriString = onclick.substring(start, end);
								if (uriString != null) attachment.setFileUri(mLocator, Uri.parse(uriString));
								mExpect = EXPECT_THUMBNAIL;
							}
						}
					}
				}
			}
			else if ("iframe".equals(tagName))
			{
				if (mExpect == EXPECT_THUMBNAIL)
				{
					String script = parser.getAttr(attrs, "src");
					if (script != null)
					{
						int start = script.indexOf("http");
						if (start >= 0)
						{
							int end = script.indexOf("\\'", start);
							if (end >= 0)
							{
								FileAttachment attachment = mAttachments.get(mAttachments.size() - 1);
								String uriString = script.substring(start, end);
								if (uriString != null) attachment.setThumbnailUri(mLocator, Uri.parse(uriString));
								mExpect = EXPECT_NONE;
							}
						}
					}
				}
			}
			else if ("img".equals(tagName))
			{
				if (mExpect == EXPECT_THUMBNAIL)
				{
					FileAttachment attachment = mAttachments.get(mAttachments.size() - 1);
					String uriString = parser.getAttr(attrs, "src");
					if (uriString != null) attachment.setThumbnailUri(mLocator, Uri.parse(uriString));
					mExpect = EXPECT_NONE;
				}
			}
		}
		else if ("a".equals(tagName))
		{
			if (mHandlePagesCount)
			{
				String title = parser.getAttr(attrs, "title");
				if ("Последняя страница".equals(title))
				{
					mExpect = EXPECT_PAGES_COUNT;
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end)
	{
		
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		switch (mExpect)
		{
			case EXPECT_POSTS_COUNT:
			{
				int postsCount = Integer.parseInt(text.trim());
				if (postsCount >= 0) mPostHolders.add(new Pair<>(mPost, postsCount));
				else mPost = null; // Thread is not yet archived
				break;
			}
			case EXPECT_COMMENT:
			{
				if (text != null)
				{
					text = text.trim();
					// remove unclosed <a> tag (see arhivach root page html)
					text = text.substring(text.indexOf(">") + 1);
					Matcher matcher = SUBJECT_PATTERN.matcher(text);
					if (matcher.find())
					{
						mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(matcher.group(1)).trim()));
						text = text.substring(matcher.group(0).length());
					}
					if (text.length() > 500 && !text.endsWith(".")) text += '\u2026';
					mPost.setComment(StringUtils.emptyIfNull(StringUtils.clearHtml(text).trim()));
				}
				break;
			}
			case EXPECT_DATE:
			{
				mPost.setTimestamp(parseTimestamp(text));
				if (mAttachments.size() > 0) mPost.setAttachments(mAttachments);
				mPost = null;
				break;
			}
			case EXPECT_PAGES_COUNT:
			{
				try
				{
					mConfiguration.storePagesCount(null, Integer.parseInt(text));
				}
				catch (NumberFormatException e)
				{
					
				}
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
	
	private static final Pattern DATE = Pattern.compile("(?:(\\d+) +)?(\\w+) +(?:(\\d+):(\\d+)|(\\d{4}))");
	
	public static long parseTimestamp(String date)
	{
		Matcher matcher = DATE.matcher(date);
		if (matcher.matches())
		{
			int day;
			int month;
			int year;
			int hour;
			int minute;
			GregorianCalendar calendar = new GregorianCalendar(ArhivachPostsParser.TIMEZONE_GMT);
			String dayString = matcher.group(1);
			String monthString = matcher.group(2);
			if (StringUtils.isEmpty(dayString))
			{
				if ("вчера".equals(monthString)) calendar.add(GregorianCalendar.DAY_OF_MONTH, -1);
				day = calendar.get(GregorianCalendar.DAY_OF_MONTH);
				month = calendar.get(GregorianCalendar.MONTH);
			}
			else
			{
				day = Integer.parseInt(dayString);
				month = ArhivachPostsParser.MONTHS_1.indexOf(monthString);
			}
			String yearString = matcher.group(5);
			if (!StringUtils.isEmpty(yearString))
			{
				hour = 0;
				minute = 0;
				year = Integer.parseInt(yearString);
			}
			else
			{
				hour = Integer.parseInt(matcher.group(3));
				minute = Integer.parseInt(matcher.group(4));
				year = calendar.get(GregorianCalendar.YEAR);
			}
			calendar = new GregorianCalendar(year, month, day, hour, minute, 0);
			calendar.setTimeZone(ArhivachPostsParser.TIMEZONE_GMT);
			calendar.add(GregorianCalendar.HOUR, -3);
			return calendar.getTimeInMillis();
		}
		return 0L;
	}
}