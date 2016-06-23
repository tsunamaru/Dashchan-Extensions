package com.mishiranu.dashchan.chan.krautchan;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class KrautchanPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final KrautchanChanConfiguration mConfiguration;
	private final KrautchanChanLocator mLocator;
	private final String mBoardName;
	
	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	private final ArrayList<FileAttachment> mAttachments = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_FILE_INFO = 1;
	private static final int EXPECT_SUBJECT = 2;
	private static final int EXPECT_NAME = 3;
	private static final int EXPECT_TRIPCODE = 4;
	private static final int EXPECT_DATE = 5;
	private static final int EXPECT_COMMENT = 6;
	private static final int EXPECT_OMITTED = 7;
	private static final int EXPECT_BOARD_TITLE = 8;
	private static final int EXPECT_PAGES_COUNT = 9;
	
	private int mExpect = EXPECT_NONE;
	
	private boolean mHasPostForm = false;
	private boolean mHasPostFormName = false;
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
	
	static
	{
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
	}
	
	private static final Pattern FILE_SIZE = Pattern.compile("(\\d+)x(\\d+),[\\s\\S]*?([\\d\\.]+) (\\w+)");
	static final Pattern NUMBER = Pattern.compile("(\\d+)");
	private static final Pattern PAGES = Pattern.compile(">(\\d+)<");
	
	public KrautchanPostsParser(String source, Object linked, String boardName)
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
			int filesCount = 0;
			for (Post post : mPosts) filesCount += post.getAttachmentsCount();
			mThread.addFilesCount(filesCount);
			mThreads.add(mThread);
			mPosts.clear();
		}
	}
	
	public ArrayList<Posts> convertThreads() throws ParseException
	{
		mThreads = new ArrayList<>();
		GroupParser.parse(mSource, this);
		closeThread();
		if (mThreads.size() > 0)
		{
			updateConfiguration();
			return mThreads;
		}
		return null;
	}
	
	public Posts convertPosts() throws ParseException
	{
		GroupParser.parse(mSource, this);
		if (mPosts.size() > 0)
		{
			updateConfiguration();
			return new Posts(mPosts);
		}
		return null;
	}
	
	private void updateConfiguration()
	{
		if (mHasPostForm) mConfiguration.storeNamesEnabled(mBoardName, mHasPostFormName);
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("div".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if (id != null && id.startsWith("thread_"))
			{
				String number = id.substring(7, id.length());
				Post post = new Post();
				post.setPostNumber(number);
				mParent = number;
				mPost = post;
				if (mThreads != null)
				{
					closeThread();
					mThread = new Posts();
				}
			}
			else
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("file_thread".equals(cssClass) || "file_reply".equals(cssClass))
				{
					mAttachment = new FileAttachment();
				}
			}
		}
		else if ("td".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if (id != null && id.startsWith("post-"))
			{
				String number = id.substring(5);
				Post post = new Post();
				post.setParentPostNumber(mParent);
				post.setPostNumber(number);
				mPost = post;
			}
		}
		else if ("span".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("postsubject".equals(cssClass))
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
			else if ("postername".equals(cssClass))
			{
				mExpect = EXPECT_NAME;
				return true;
			}
			else if ("tripcode".equals(cssClass))
			{
				mExpect = EXPECT_TRIPCODE;
				return true;
			}
			else if ("postdate".equals(cssClass))
			{
				mExpect = EXPECT_DATE;
				return true;
			}
			else if ("authority_admin".equals(cssClass))
			{
				mPost.setCapcode("Admin");
			}
			else if ("authority_mod".equals(cssClass))
			{
				mPost.setCapcode("Mod");
			}
			else if ("sage".equals(cssClass))
			{
				mPost.setSage(true);
			}
			else if ("fileinfo".equals(cssClass))
			{
				mExpect = EXPECT_FILE_INFO;
				return true;
			}
			else if ("omittedinfo".equals(cssClass))
			{
				if (mThreads != null)
				{
					mExpect = EXPECT_OMITTED;
					return true;
				}
			}
		}
		else if ("a".equals(tagName))
		{
			String onMouseOver = parser.getAttr(attrs, "onmouseover");
			if (onMouseOver != null && onMouseOver.startsWith("fileNameTooltip"))
			{
				String href = parser.getAttr(attrs, "href");
				href = href.substring(href.lastIndexOf('/') + 1);
				try
				{
					String originalName = StringUtils.clearHtml(URLDecoder.decode(href, "UTF-8"));
					if (!StringUtils.isEmpty(originalName)) mAttachment.setOriginalName(originalName);
				}
				catch (UnsupportedEncodingException e)
				{
					throw new RuntimeException(e);
				}
			}
			else
			{
				String href = parser.getAttr(attrs, "href");
				if (href != null && href.startsWith("/files/"))
				{
					mAttachment.setFileUri(mLocator, mLocator.buildPath(href));
					mAttachments.add(mAttachment);
				}
			}
		}
		else if ("img".equals(tagName))
		{
			String src = parser.getAttr(attrs, "src");
			if (src != null)
			{
				if (src.startsWith("/images/balls/"))
				{
					String onMouseOver = parser.getAttr(attrs, "onmouseover");
					String country = src.substring(src.lastIndexOf('/') + 1, src.lastIndexOf('.'));
					String countryName = onMouseOver != null ? StringUtils.clearHtml(onMouseOver.substring
							(onMouseOver.indexOf('\'') + 1, onMouseOver.lastIndexOf('\''))) : null;
					if (StringUtils.isEmpty(countryName)) countryName = country.toUpperCase(Locale.US);
					Uri uri = mLocator.buildPath("images", "balls", country.toLowerCase(Locale.US) + ".png");
					mPost.setIcons(new Icon(mLocator, uri, countryName));
				}
				else if (src.startsWith("/thumbnails/")) mAttachment.setThumbnailUri(mLocator, mLocator.buildPath(src));
				else if ("/images/sticky.gif".equals(src)) mPost.setSticky(true);
				else if ("/images/locked.gif".equals(src)) mPost.setClosed(true);
			}
		}
		else if ("p".equals(tagName))
		{
			if (mPost != null)
			{
				String id = parser.getAttr(attrs, "id");
				if (("post_text_" + mPost.getPostNumber()).equals(id))
				{
					mExpect = EXPECT_COMMENT;
					return true;
				}
			}
		}
		else if ("h1".equals(tagName))
		{
			mExpect = EXPECT_BOARD_TITLE;
			return true;
		}
		else if ("table".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if ("postform_table".equals(id))
			{
				mHasPostForm = true;
			}
			else
			{
				String border = parser.getAttr(attrs, "border");
				if (mThreads != null && "1".equals(border))
				{
					mExpect = EXPECT_PAGES_COUNT;
					return true;
				}
			}
		}
		else if ("tr".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if ("postform_row_name".equals(id))
			{
				mHasPostFormName = true;
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
			case EXPECT_FILE_INFO:
			{
				Matcher matcher = FILE_SIZE.matcher(text);
				if (matcher.find())
				{
					int width = Integer.parseInt(matcher.group(1));
					int height = Integer.parseInt(matcher.group(2));
					float size = Float.parseFloat(matcher.group(3));
					String dim = matcher.group(4);
					if ("kB".equals(dim)) size *= 1024;
					else if ("MB".equals(dim)) size *= 1024 * 1024;
					mAttachment.setSize((int) size);
					mAttachment.setWidth(width);
					mAttachment.setHeight(height);
				}
				break;
			}
			case EXPECT_SUBJECT:
			{
				mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_NAME:
			{
				mPost.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_TRIPCODE:
			{
				mPost.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_DATE:
			{
				try
				{
					mPost.setTimestamp(DATE_FORMAT.parse(text).getTime() + 360000L); // + 1 hour
				}
				catch (java.text.ParseException e)
				{
					throw new RuntimeException(e);
				}
				break;
			}
			case EXPECT_COMMENT:
			{
				text = text.trim();
				text = StringUtils.linkify(text);
				mPost.setComment(text);
				if (mAttachments.size() > 0)
				{
					mPost.setAttachments(mAttachments);
					mAttachments.clear();
				}
				mPosts.add(mPost);
				mPost = null;
				break;
			}
			case EXPECT_OMITTED:
			{
				Matcher matcher = NUMBER.matcher(text);
				if (matcher.find())
				{
					mThread.addPostsCount(Integer.parseInt(matcher.group(1)));
					if (matcher.find()) mThread.addFilesCount(Integer.parseInt(matcher.group(1)));
				}
				break;
			}
			case EXPECT_BOARD_TITLE:
			{
				text = StringUtils.clearHtml(text).trim();
				text = text.substring(5 + mBoardName.length()); // Skip "/boardname/ - "
				mConfiguration.storeBoardTitle(mBoardName, text);
				break;
			}
			case EXPECT_PAGES_COUNT:
			{
				String pagesCount = null;
				Matcher matcher = PAGES.matcher(text);
				while (matcher.find()) pagesCount = matcher.group(1);
				if (pagesCount != null)
				{
					try
					{
						mConfiguration.storePagesCount(mBoardName, Integer.parseInt(pagesCount));
					}
					catch (NumberFormatException e)
					{
						
					}
				}
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}