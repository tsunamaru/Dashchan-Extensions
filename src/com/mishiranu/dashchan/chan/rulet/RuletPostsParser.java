package com.mishiranu.dashchan.chan.rulet;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
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

@SuppressLint("SimpleDateFormat")
public class RuletPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final RuletChanConfiguration mConfiguration;
	private final RuletChanLocator mLocator;
	private final String mBoardName;
	
	private String mParent;
	private Posts mThread;
	private Post mPost;
	private FileAttachment mAttachment;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_FILE_DATA = 1;
	private static final int EXPECT_SUBJECT = 2;
	private static final int EXPECT_NAME = 3;
	private static final int EXPECT_TRIPCODE = 4;
	private static final int EXPECT_COMMENT = 5;
	private static final int EXPECT_OMITTED = 6;
	private static final int EXPECT_BOARD_TITLE = 7;
	private static final int EXPECT_PAGES_COUNT = 8;
	private static final int EXPECT_POST_BLOCK = 9;
	
	private int mExpect = EXPECT_NONE;
	private boolean mHeaderHandling = false; // True when parser is inside post's <label>. Used to parse date.
	
	private boolean mHasPostBlock = false;
	private boolean mHasPostBlockName = false;
	private boolean mHasPostBlockEmail = false;
	private boolean mHasPostBlockFile = false;
	private boolean mHasSpoilerCheckBox = false;
	
	static final SimpleDateFormat DATE_FORMAT;
	
	static
	{
		DateFormatSymbols symbols = new DateFormatSymbols();
		symbols.setShortWeekdays(new String[] {"", "Вск", "Пнд", "Втр", "Срд", "Чтв", "Птн", "Сбт"});
		symbols.setShortMonths(new String[] {"Янв", "Фев", "Мар", "Апр", "Мая", "Июн", "Июл", "Авг",
				"Сен", "Окт", "Ноя", "Дек"});
		DATE_FORMAT = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", symbols);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	private static final Pattern FILE_SIZE = Pattern.compile("([\\d\\.]+) (Б|КБ|МБ)");
	private static final Pattern FILE_RESOLUTION = Pattern.compile("(\\d+)x(\\d+)");
	private static final Pattern NUMBER = Pattern.compile("(\\d+)");
	
	public RuletPostsParser(String source, Object linked, String boardName)
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
	
	public ArrayList<Post> convertPosts() throws ParseException
	{
		GroupParser.parse(mSource, this);
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
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		/*
		 * For original posts:
		 * 1) Post has image -> create PostModel when 'span.filesize' reached
		 * 2) Post has no image -> create PostModel when 'input[type=checkbox][name="posts[]"]' reached
		 * Create ThreadModel when 'input[type=checkbox][name="posts[]"]' reached
		 * 
		 * For replies:
		 * Create PostModel when td[id^=reply] reached
		 */
		
		if ("input".equals(tagName))
		{
			if ("checkbox".equals(parser.getAttr(attrs, "type")) && "posts[]".equals(parser.getAttr(attrs, "name")))
			{
				mHeaderHandling = true;
				if (mPost == null || mPost.getPostNumber() == null)
				{
					String number = parser.getAttr(attrs, "value");
					if (mPost == null) mPost = new Post();
					mPost.setPostNumber(number);
					mParent = number;
					if (mThreads != null)
					{
						closeThread();
						mThread = new Posts();
					}
				}
			}
			else
			{
				if ("image_spoiler".equals(parser.getAttr(attrs, "name")))
				{
					mHasSpoilerCheckBox = true;
				}
			}
		}
		else if ("td".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if (id != null && id.startsWith("reply"))
			{
				String number = id.substring(5);
				Post post = new Post();
				post.setParentPostNumber(mParent);
				post.setPostNumber(number);
				mPost = post;
			}
			else
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("postblock".equals(cssClass))
				{
					mHasPostBlock = true;
					mExpect = EXPECT_POST_BLOCK;
					return true;
				}
			}
		}
		else if ("span".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("filesize".equals(cssClass))
			{
				if (mPost == null)
				{
					// New thread with image
					// Thread will be created later when parser reach deleting checkbox
					mPost = new Post();
				}
				mAttachment = new FileAttachment();
				mExpect = EXPECT_FILE_DATA;
			}
			else if ("filetitle".equals(cssClass))
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
			else if ("postername".equals(cssClass))
			{
				mExpect = EXPECT_NAME;
				return true;
			}
			else if (cssClass != null && cssClass.contains("postertrip"))
			{
				mExpect = EXPECT_TRIPCODE;
				return true;
			}
			else if ("omittedposts".equals(cssClass))
			{
				if (mThreads != null)
				{
					mExpect = EXPECT_OMITTED;
					return true;
				}
			}
		}
		else if ("img".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("thumb".equals(cssClass))
			{
				String src = parser.getAttr(attrs, "src");
				if (src != null && src.contains("/thumb/"))
				{
					mAttachment.setThumbnailUri(mLocator, Uri.parse(src));
				}
				mPost.setAttachments(mAttachment);
			}
		}
		else if ("div".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("countryball".equals(cssClass))
			{
				String style = parser.getAttr(attrs, "style");
				String title = parser.getAttr(attrs, "title");
				if (style != null)
				{
					int index1 = style.indexOf("url('");
					int index2 = index1 >= 0 ? style.indexOf("')", index1) : -1;
					if (index2 > index1)
					{
						String path = style.substring(index1 + 5, index2);
						title = StringUtils.clearHtml(title);
						mPost.setIcons(new Icon(mLocator, mLocator.buildPath(path), title));
					}
				}
			}
			else if ("nothumb".equals(cssClass))
			{
				if (mAttachment.getSize() > 0 || mAttachment.getWidth() > 0 || mAttachment.getHeight() > 0)
				{
					mPost.setAttachments(mAttachment);
				}
				mExpect = EXPECT_NONE;
			}
			else if ("logo".equals(cssClass))
			{
				mExpect = EXPECT_BOARD_TITLE;
				return true;
			}
		}
		else if ("a".equals(tagName))
		{
			if (mExpect == EXPECT_FILE_DATA)
			{
				String uriString = parser.getAttr(attrs, "href");
				if (uriString != null)
				{
					Uri uri = Uri.parse(uriString);
					mAttachment.setFileUri(mLocator, uri);
					if ("streaming.2-chru.net".equals(uri.getAuthority())) mExpect = EXPECT_NONE;
				}
			}
			else if (mHeaderHandling)
			{
				String href = parser.getAttr(attrs, "href");
				if (href != null && href.startsWith("mailto:"))
				{
					String email = StringUtils.clearHtml(href);
					if (email.equalsIgnoreCase("mailto:sage")) mPost.setSage(true);
					else mPost.setEmail(email);
				}
			}
		}
		else if ("blockquote".equals(tagName))
		{
			mExpect = EXPECT_COMMENT;
			return true;
		}
		else if ("ul".equals(tagName))
		{
			String id = parser.getAttr(attrs, "id");
			if ("pager".equals(id))
			{
				mExpect = EXPECT_PAGES_COUNT;
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
	public void onText(GroupParser parser, String source, int start, int end)
	{
		if (mExpect == EXPECT_FILE_DATA)
		{
			String text = source.substring(start, end).trim();
			if (text.startsWith("("))
			{
				text = StringUtils.clearHtml(text).trim();
				String[] splitted = text.substring(1, text.length() - 1).split(", *");
				for (String data : splitted)
				{
					Matcher matcher = FILE_SIZE.matcher(data);
					if (matcher.matches())
					{
						float size = Float.parseFloat(matcher.group(1));
						String dim = matcher.group(2);
						if ("КБ".equals(dim)) size *= 1024f;
						else if ("МБ".equals(dim)) size *= 1024f * 1024f;
						mAttachment.setSize((int) size);
					}
					matcher = FILE_RESOLUTION.matcher(data);
					if (matcher.matches())
					{
						mAttachment.setWidth(Integer.parseInt(matcher.group(1)));
						mAttachment.setHeight(Integer.parseInt(matcher.group(2)));
					}
				}
				mExpect = EXPECT_NONE;
			}
		}
		if (mHeaderHandling)
		{
			String text = source.substring(start, end).trim();
			if (text.length() > 0)
			{
				try
				{
					mPost.setTimestamp(DATE_FORMAT.parse(text).getTime());
				}
				catch (java.text.ParseException e)
				{
					
				}
				mHeaderHandling = false;
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
				mPost.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_TRIPCODE:
			{
				mPost.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_COMMENT:
			{
				text = text.trim();
				if (text.endsWith("<p><span class=\"red italic\">sky</span></p>"))
				{
					text = text.substring(0, text.length() - 42).trim();
					mPost.setCapcode("Sky");
				}
				mPost.setComment(text);
				mPosts.add(mPost);
				mPost = null;
				break;
			}
			case EXPECT_OMITTED:
			{
				Matcher matcher = NUMBER.matcher(text);
				if (matcher.find()) mThread.addPostsCount(Integer.parseInt(matcher.group(1)));
				break;
			}
			case EXPECT_BOARD_TITLE:
			{
				text = StringUtils.clearHtml(text).trim();
				int index = text.indexOf("— ");
				if (index >= 0) text = text.substring(index + 2);
				if (!StringUtils.isEmpty(text)) mConfiguration.storeBoardTitle(mBoardName, text);
				break;
			}
			case EXPECT_PAGES_COUNT:
			{
				text = StringUtils.clearHtml(text);
				Matcher matcher = NUMBER.matcher(text);
				while (matcher.find()) text = matcher.group(1);
				try
				{
					int pagesCount = Integer.parseInt(text) + 1;
					mConfiguration.storePagesCount(mBoardName, pagesCount);
				}
				catch (NumberFormatException e)
				{
					
				}
				break;
			}
			case EXPECT_POST_BLOCK:
			{
				if ("Имя".equals(text)) mHasPostBlockName = true;
				else if ("E-mail".equals(text)) mHasPostBlockEmail = true;
				else if ("Файл".equals(text)) mHasPostBlockFile = true;
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}