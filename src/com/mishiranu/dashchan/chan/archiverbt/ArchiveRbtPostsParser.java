package com.mishiranu.dashchan.chan.archiverbt;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.Threads;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class ArchiveRbtPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final ArchiveRbtChanLocator mLocator;
	
	private String mResTo;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_SUBJECT = 1;
	private static final int EXPECT_NAME = 2;
	private static final int EXPECT_TRIP = 3;
	private static final int EXPECT_COMMENT = 4;
	private static final int EXPECT_OMITTED = 5;
	
	private int mExpect = EXPECT_NONE;
	private boolean mSpanStarted = false;
	
	private static final Pattern FILE_SIZE = Pattern.compile("File: ([\\d\\.]+) (\\w+), (\\d+)x(\\d+)(?:, (.*))?");
	private static final Pattern NUMBER = Pattern.compile("(\\d+)");
	
	public ArchiveRbtPostsParser(String source, Object linked)
	{
		mSource = source;
		mLocator = ChanLocator.get(linked);
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
	
	public Threads convertThreads() throws ParseException
	{
		mThreads = new ArrayList<>();
		GroupParser.parse(mSource, this);
		closeThread();
		return mThreads.size() > 0 ? new Threads(mThreads) : null;
	}
	
	public Posts convertPosts(Uri threadUri) throws ParseException
	{
		GroupParser.parse(mSource, this);
		return mPosts.size() > 0 ? new Posts(mPosts).setArchivedThreadUri(threadUri) : null;
	}
	
	public ArrayList<Post> convertSearch() throws ParseException
	{
		GroupParser.parse(mSource, this);
		return mPosts;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException
	{
		if ("div".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if (id != null)
			{
				String number = id.substring(1);
				boolean isNumber;
				try
				{
					Integer.parseInt(number);
					isNumber = true;
				}
				catch (Exception e)
				{
					isNumber = false;
				}
				if (isNumber)
				{
					Post post = new Post();
					post.setPostNumber(number);
					mResTo = number;
					mPost = post;
					if (mThreads != null)
					{
						closeThread();
						mThread = new Posts();
					}
				}
			}
		}
		else if ("td".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if (cssClass != null && cssClass.contains("reply"))
			{
				String id = parser.getAttr(attrs, "id");
				if (id != null)
				{
					String number = id.substring(1);
					Post post = new Post();
					post.setParentPostNumber(mResTo);
					post.setPostNumber(number);
					mPost = post;
				}
			}
		}
		else if ("span".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("filetitle".equals(cssClass))
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
			else if (cssClass != null && cssClass.startsWith("postername"))
			{
				mExpect = EXPECT_NAME;
				return true;
			}
			else if ("postertrip".equals(cssClass))
			{
				mExpect = EXPECT_TRIP;
				return true;
			}
			else if ("posttime".equals(cssClass))
			{
				String timestamp = parser.getAttr(attrs, "title");
				if (timestamp != null) mPost.setTimestamp(Long.parseLong(timestamp));
			}
			else if ("omittedposts".equals(cssClass))
			{
				mExpect = EXPECT_OMITTED;
				return true;
			}
			else mSpanStarted = true;
		}
		else if ("a".equals(tagName))
		{
			boolean resToHandled = false;
			if (mPost != null && mResTo == null)
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("js".equals(cssClass))
				{
					String onclick = parser.getAttr(attrs, "onclick");
					if (onclick != null && onclick.startsWith("replyhighlight"))
					{
						String href = parser.getAttr(attrs, "href");
						mPost.setParentPostNumber(mLocator.getThreadNumber(Uri.parse(href)));
						resToHandled = true;
					}
				}
			}
			if (!resToHandled && mAttachment != null)
			{
				String href = parser.getAttr(attrs, "href");
				if (href.startsWith("/boards/") && href.contains("/img/"))
				{
					mAttachment.setFileUri(mLocator, mLocator.buildPath(href));
				}
			}
		}
		else if ("img".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if (mAttachment != null && "file thumb".equals(cssClass))
			{
				mAttachment.setThumbnailUri(mLocator, mLocator.buildPath(parser.getAttr(attrs, "src")));
			}
		}
		else if ("p".equals(tagName))
		{
			if (mPost != null)
			{
				mExpect = EXPECT_COMMENT;
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end) throws ParseException
	{
		if (mSpanStarted)
		{
			mSpanStarted = false;
			if (mPost != null)
			{
				String text = StringUtils.clearHtml(source.substring(start, end)).trim();
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
					mAttachment = new FileAttachment();
					mPost.setAttachments(mAttachment);
					mAttachment.setSize((int) size);
					mAttachment.setWidth(width);
					mAttachment.setHeight(height);
					mAttachment.setOriginalName(originalName);
				}
			}
		}
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		switch (mExpect)
		{
			case EXPECT_SUBJECT:
			{
				mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_NAME:
			{
				text = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim());
				if (text != null && text.startsWith("##")) mPost.setCapcode(text.substring(2));
				else mPost.setName(text);
				break;
			}
			case EXPECT_TRIP:
			{
				mPost.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_COMMENT:
			{
				mPost.setComment(text);
				boolean canAdd;
				try
				{
					Integer.parseInt(mPost.getPostNumber());
					canAdd = true;
				}
				catch (Exception e)
				{
					// Avoid numbers like 123213123_10
					canAdd = false;
				}
				if (canAdd) mPosts.add(mPost);
				mPost = null;
				mAttachment = null;
				break;
			}
			case EXPECT_OMITTED:
			{
				Matcher matcher = NUMBER.matcher(text);
				if (matcher.find()) mThread.addPostsCount(Integer.parseInt(matcher.group(1)));
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}