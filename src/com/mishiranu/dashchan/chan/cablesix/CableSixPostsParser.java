package com.mishiranu.dashchan.chan.cablesix;

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

public class CableSixPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final CableSixChanConfiguration mConfiguration;
	private final CableSixChanLocator mLocator;
	private final String mBoardName;
	
	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_FILE_LINK = 1;
	private static final int EXPECT_FILE_SIZE = 2;
	private static final int EXPECT_SUBJECT = 3;
	private static final int EXPECT_NAME = 4;
	private static final int EXPECT_TRIPCODE = 5;
	private static final int EXPECT_CAPCODE = 6;
	private static final int EXPECT_COMMENT = 7;
	private static final int EXPECT_OMITTED = 8;
	private static final int EXPECT_BOARD_TITLE = 9;
	private static final int EXPECT_PAGES_COUNT = 10;
	
	private int mExpect = EXPECT_NONE;
	
	private boolean mHasPostForm = false;
	private boolean mNamesEnabled = false;
	private boolean mImageSpoilersEnabled = false;
	private boolean mImageNsfwEnabled = false;
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
	
	static
	{
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Etc/GMT"));
	}
	
	private static final Pattern FILE_SIZE = Pattern.compile("([\\d\\.]+) (\\w+), (\\d+)x(\\d+)");
	static final Pattern NUMBER = Pattern.compile("(\\d+)");
	
	public CableSixPostsParser(String source, Object linked, String boardName)
	{
		mSource = source;
		mConfiguration = ChanConfiguration.get(linked);
		mLocator = ChanLocator.get(linked);
		mBoardName = boardName;
	}
	
	public CableSixPostsParser(String source, Object linked, String boardName, String parent)
	{
		this(source, linked, boardName);
		mParent = parent;
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
	
	public ArrayList<Posts> convertThreads(int pageNumber) throws ParseException
	{
		mThreads = new ArrayList<>();
		GroupParser.parse(mSource, this);
		closeThread();
		if (mThreads.size() > 0)
		{
			if (pageNumber == 0 && mHasPostForm)
			{
				mConfiguration.storeNamesSpoilersNsfwEnabled(mBoardName, mNamesEnabled, mImageSpoilersEnabled,
						mImageNsfwEnabled);
			}
			return mThreads;
		}
		return null;
	}
	
	public Posts convertPosts() throws ParseException
	{
		GroupParser.parse(mSource, this);
		if (mPosts.size() > 0) return new Posts(mPosts);
		return null;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException
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
				if (mThreads != null)
				{
					closeThread();
					mThread = new Posts();
				}
				mPost = post;
			}
			else if (id != null && id.startsWith("reply_"))
			{
				String number = id.substring(6, id.length());
				Post post = new Post();
				post.setParentPostNumber(mParent);
				post.setPostNumber(number);
				mPost = post;
			}
			else
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("body".equals(cssClass))
				{
					mExpect = EXPECT_COMMENT;
					return true;
				}
				else if (mThreads != null && "pages".equals(cssClass))
				{
					mExpect = EXPECT_PAGES_COUNT;
					return true;
				}
			}
		}
		else if ("p".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("fileinfo".equals(cssClass))
			{
				mAttachment = new FileAttachment();
				mExpect = EXPECT_FILE_LINK;
			}
		}
		else if ("span".equals(tagName))
		{
			if (mPost != null)
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("subject".equals(cssClass))
				{
					mExpect = EXPECT_SUBJECT;
					return true;
				}
				else if ("name".equals(cssClass))
				{
					mExpect = EXPECT_NAME;
					return true;
				}
				else if ("trip".equals(cssClass))
				{
					mExpect = EXPECT_TRIPCODE;
					return true;
				}
				else if ("capcode".equals(cssClass))
				{
					mExpect = EXPECT_CAPCODE;
					return true;
				}
				else if ("unimportant".equals(cssClass))
				{
					mExpect = EXPECT_FILE_SIZE;
				} 
				else if (mAttachment != null)
				{
					String title = parser.getAttr(attrs, "title");
					if (title != null && title.startsWith("Save as original filename"))
					{
						title = title.substring(title.indexOf('(') + 1, title.lastIndexOf(')'));
						mAttachment.setOriginalName(StringUtils.clearHtml(title));
					}
				}
			}
			else if (mThread != null)
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("omitted".equals(cssClass))
				{
					mExpect = EXPECT_OMITTED;
					return true;
				}
			}
		}
		else if ("time".equals(tagName))
		{
			String datetime = parser.getAttr(attrs, "datetime");
			if (datetime != null)
			{
				try
				{
					mPost.setTimestamp(DATE_FORMAT.parse(datetime).getTime());
				}
				catch (java.text.ParseException e)
				{
					throw new ParseException(e);
				}
			}
		}
		else if ("img".equals(tagName))
		{
			if (mPost != null)
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("post-image".equals(cssClass))
				{
					String src = parser.getAttr(attrs, "src");
					if ("/static/spoiler.png".equals(src)) mAttachment.setSpoiler(true);
					else if (!"/static/spoiler_nsfw.png".equals(src))
					{
						mAttachment.setThumbnailUri(mLocator, mLocator.buildPath(src));
					}
				}
				else if ("flag".equals(cssClass))
				{
					String src = parser.getAttr(attrs, "src");
					String title = parser.getAttr(attrs, "title");
					if (title != null) title = StringUtils.clearHtml(title).trim();
					else title = src.substring(src.lastIndexOf('/') + 1, src.lastIndexOf('.')).toUpperCase(Locale.US);
					mPost.setIcons(new Icon(mLocator, mLocator.buildPath(src), title));
				}
			}
		}
		else if ("a".equals(tagName))
		{
			if (mExpect == EXPECT_FILE_LINK)
			{
				Uri uri = Uri.parse(parser.getAttr(attrs, "href"));
				mAttachment.setFileUri(mLocator, uri);
				mExpect = EXPECT_NONE;
			}
			else
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("email".equals(cssClass))
				{
					mPost.setEmail(StringUtils.clearHtml(parser.getAttr(attrs, "href")));
				}
			}
		}
		else if ("i".equals(tagName))
		{
			if (mPost != null)
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("fa fa-thumb-tack".equals(cssClass)) mPost.setSticky(true);
			}
		}
		else if ("h1".equals(tagName))
		{
			mExpect = EXPECT_BOARD_TITLE;
			return true;
		}
		else if ("form".equals(tagName))
		{
			if (mThreads != null && mThread == null)
			{
				String name = parser.getAttr(attrs, "name");
				if ("post".equals(name)) mHasPostForm = true;
			}
		}
		else if ("input".equals(tagName))
		{
			if (mThreads != null && mThread == null)
			{
				String name = parser.getAttr(attrs, "name");
				if ("name".equals(name)) mNamesEnabled = true;
				else if ("spoiler".equals(name)) mImageSpoilersEnabled = true;
				else if ("spoiler_nsfw".equals(name)) mImageNsfwEnabled = true;
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
		if (mExpect == EXPECT_FILE_SIZE)
		{
			Matcher matcher = FILE_SIZE.matcher(source.substring(start, end));
			if (matcher.find())
			{
				float size = Float.parseFloat(matcher.group(1));
				String dim = matcher.group(2);
				if ("KB".equals(dim)) size *= 1024;
				else if ("MB".equals(dim)) size *= 1024 * 1024;
				int width = Integer.parseInt(matcher.group(3));
				int height = Integer.parseInt(matcher.group(4));
				mAttachment.setSize((int) size);
				mAttachment.setWidth(width);
				mAttachment.setHeight(height);
			}
			mExpect = EXPECT_NONE;
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
				mPost.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_TRIPCODE:
			{
				mPost.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_CAPCODE:
			{
				String capcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim());
				if (capcode != null && capcode.startsWith("## ")) mPost.setCapcode(capcode.substring(3));
				break;
			}
			case EXPECT_COMMENT:
			{
				mPost.setComment(text.trim());
				mPosts.add(mPost);
				if (mAttachment != null)
				{
					mPost.setAttachments(mAttachment);
					mAttachment = null;
				}
				mPost = null;
				break;
			}
			case EXPECT_OMITTED:
			{
				text = StringUtils.clearHtml(text);
				Matcher matcher = NUMBER.matcher(text);
				if (matcher.find())
				{
					mThread.addPostsCount(Integer.parseInt(matcher.group(1)));
					if (matcher.find()) mThread.addPostsWithFilesCount(Integer.parseInt(matcher.group(1)));
				}
				break;
			}
			case EXPECT_BOARD_TITLE:
			{
				text = StringUtils.clearHtml(text).trim();
				int index = text.indexOf("- ");
				if (index >= 0) text = text.substring(index + 2);
				mConfiguration.storeBoardTitle(mBoardName, text);
				break;
			}
			case EXPECT_PAGES_COUNT:
			{
				String number = null;
				text = StringUtils.clearHtml(text);
				Matcher matcher = NUMBER.matcher(text);
				while (matcher.find()) number = matcher.group(1);
				if (number != null) mConfiguration.storePagesCount(mBoardName, Integer.parseInt(number));
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}