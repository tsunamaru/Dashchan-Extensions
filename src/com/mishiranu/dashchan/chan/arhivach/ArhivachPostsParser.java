package com.mishiranu.dashchan.chan.arhivach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class ArhivachPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final ArhivachChanLocator mLocator;
	private final String mArhivachId;
	
	private Uri mThreadUri;
	private String mParent;
	private Post mPost;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	private final ArrayList<FileAttachment> mAttachments = new ArrayList<>();

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_THREAD_URI = 1;
	private static final int EXPECT_SUBJECT = 2;
	private static final int EXPECT_NAME = 3;
	private static final int EXPECT_TRIPCODE = 4;
	private static final int EXPECT_DATE = 5;
	private static final int EXPECT_THUMBNAIL = 6;
	private static final int EXPECT_LABEL = 7;
	private static final int EXPECT_COMMENT = 8;
	
	private int mExpect = EXPECT_NONE;
	
	public ArhivachPostsParser(String source, Object linked, String arhivachId)
	{
		mSource = source;
		mLocator = ChanLocator.get(linked);
		mArhivachId = arhivachId;
	}
	
	public Posts convert() throws ParseException
	{
		GroupParser.parse(mSource, this);
		return new Posts(mPosts).setArchivedThreadUri(mThreadUri);
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException
	{
		if ("div".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("post".equals(cssClass))
			{
				String number = parser.getAttr(attrs, "postid");
				if (StringUtils.isEmpty(number))
				{
					// Sometimes numbers can be empty if moder delete the post
					if (mPost != null)
					{
						int oldNumber = Integer.parseInt(mPost.getPostNumber());
						number = Integer.toString(oldNumber + 1);
					}
					else throw new ParseException();
				}
				mPost = new Post();
				mPost.setThreadNumber(mArhivachId);
				mPost.setPostNumber(number);
				if (mParent == null) mParent = number;
				else mPost.setParentPostNumber(mParent);
				mAttachments.clear();
			}
			else if ("post_comment_body".equals(cssClass))
			{
				mExpect = EXPECT_COMMENT;
				return true;
			}
			else if ("span3".equals(cssClass))
			{
				mExpect = EXPECT_THREAD_URI;
				return true;
			}
		}
		else if ("h1".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("post_subject".equals(cssClass))
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
		}
		else if ("span".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("poster_name".equals(cssClass))
			{
				mExpect = EXPECT_NAME;
				return true;
			}
			else if ("poster_trip".equals(cssClass))
			{
				mExpect = EXPECT_TRIPCODE;
				return true;
			}
			else if ("post_time".equals(cssClass))
			{
				mExpect = EXPECT_DATE;
				return true;
			}
			else if ("label label-success".equals(cssClass))
			{
				mExpect = EXPECT_LABEL;
				return true;
			}
			else if ("post_subject".equals(cssClass))
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
		}
		else if ("iframe".equals(tagName))
		{
			if (mExpect == EXPECT_THUMBNAIL)
			{
				if (parseIframeThumbnail(parser, attrs, mLocator, mAttachments))
				{
					mExpect = EXPECT_NONE;
				}
			}
		}
		else if ("img".equals(tagName))
		{
			if (mExpect == EXPECT_THUMBNAIL)
			{
				parseImageThumbnail(parser, attrs, mLocator, mAttachments);
				mExpect = EXPECT_NONE;
			}
			else
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("poster_sage".equals(cssClass)) mPost.setSage(true);
			}
		}
		else if ("a".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("expand_image".equals(cssClass))
			{
				if (parseExpandImage(parser, attrs, mLocator, mAttachments))
				{
					mExpect = EXPECT_THUMBNAIL;
				}
			}
			else if ("post_mail".equals(cssClass))
			{
				String email = StringUtils.nullIfEmpty(StringUtils.clearHtml(parser.getAttr(attrs, "href")));
				if (email != null)
				{
					if (email.equals("mailto:sage")) mPost.setSage(true);
					else mPost.setEmail(email);
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
	
	static boolean parseExpandImage(GroupParser parser, String attrs, ArhivachChanLocator locator,
			ArrayList<FileAttachment> attachments)
	{
		String onclick = parser.getAttr(attrs, "onclick");
		if (onclick != null)
		{
			boolean relative = false;
			int start = onclick.indexOf("http");
			if (start == -1)
			{
				start = onclick.indexOf("/storage");
				relative = true;
			}
			if (start >= 0)
			{
				int end = onclick.indexOf("'", start);
				if (end >= 0)
				{
					FileAttachment attachment = new FileAttachment();
					attachments.add(attachment);
					String uriString = onclick.substring(start, end);
					if (uriString != null)
					{
						if (relative) attachment.setFileUri(locator, locator.buildPath(uriString));
						else attachment.setFileUri(locator, Uri.parse(uriString));
					}
					return true;
				}
			}
		}
		return false;
	}
	
	static boolean parseIframeThumbnail(GroupParser parser, String attrs, ArhivachChanLocator locator,
			ArrayList<FileAttachment> attachments)
	{
		String script = parser.getAttr(attrs, "src");
		if (script != null)
		{
			boolean relative = false;
			int start = script.indexOf("http");
			if (start == -1)
			{
				start = script.indexOf("/storage");
				relative = true;
			}
			if (start >= 0)
			{
				int end = script.indexOf("\\'", start);
				if (end >= 0)
				{
					FileAttachment attachment = attachments.get(attachments.size() - 1);
					String uriString = script.substring(start, end);
					if (uriString != null)
					{
						if (relative) attachment.setThumbnailUri(locator, locator.buildPath(uriString));
						else attachment.setThumbnailUri(locator, Uri.parse(uriString));
					}
					return true;
				}
			}
		}
		return false;
	}
	
	static void parseImageThumbnail(GroupParser parser, String attrs, ArhivachChanLocator locator,
			ArrayList<FileAttachment> attachments)
	{
		FileAttachment attachment = attachments.get(attachments.size() - 1);
		String uriString = parser.getAttr(attrs, "src");
		if (uriString != null)
		{
			if (uriString.startsWith("http")) attachment.setThumbnailUri(locator, Uri.parse(uriString));
			else attachment.setThumbnailUri(locator, locator.buildPath(uriString));
		}
	}
	
	private static final Pattern NAME_SAGE_PATTERN = Pattern.compile("ID:( |\u00a0|&nbsp;?)Heaven");
	private static final Pattern BADGE_PATTERN = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.+?/?>");
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		switch (mExpect)
		{
			case EXPECT_THREAD_URI:
			{
				mThreadUri = Uri.parse(StringUtils.clearHtml(text).trim());
				break;
			}
			case EXPECT_SUBJECT:
			{
				mPost.setSubject(StringUtils.emptyIfNull(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_NAME:
			{
				int index = text.indexOf("<img");
				if (index >= 0)
				{
					String icon = text.substring(index);
					text = text.substring(0, index);
					Matcher matcher = BADGE_PATTERN.matcher(icon);
					ArrayList<Icon> icons = null;
					while (matcher.find())
					{
						if (icons == null) icons = new ArrayList<>();
						String path = matcher.group(1);
						String title = matcher.group(2);
						Uri uri = Uri.parse(path);
						if (StringUtils.isEmpty(title))
						{
							title = uri.getLastPathSegment();
							title = title.substring(0, title.lastIndexOf('.'));
						}
						title = StringUtils.clearHtml(title);
						icons.add(new Icon(mLocator, uri, title));
					}
					mPost.setIcons(icons);
				}
				String name = StringUtils.emptyIfNull(StringUtils.clearHtml(text).trim());
				if (NAME_SAGE_PATTERN.matcher(name).find()) mPost.setSage(true); else
				{
					if (!StringUtils.isEmpty(name))
					{
						index = name.indexOf(" ID: ");
						if (index >= 0)
						{
							String identifier = name.substring(index + 5).replaceAll(" +", " ");
							name = name.substring(0, index);
							mPost.setIdentifier(identifier);
						}
						else if (name.endsWith(" ID:")) name = name.substring(0, name.length() - 4);
					}
					mPost.setName(name);
				}
				break;
			}
			case EXPECT_TRIPCODE:
			{
				String tripcode = StringUtils.emptyIfNull(StringUtils.clearHtml(text).trim());
				if (tripcode != null)
				{
					if ("## Abu ##".equals(tripcode)) mPost.setCapcode("Abu");
					else if ("## Mod ##".equals(tripcode)) mPost.setCapcode("Mod");
					else if (tripcode.startsWith("!")) mPost.setTripcode(tripcode);
					else if (mPost.getIdentifier() == null) mPost.setIdentifier(tripcode);
				}
				break;
			}
			case EXPECT_DATE:
			{
				mPost.setTimestamp(parseTimestamp(text.trim()));
				break;
			}
			case EXPECT_LABEL:
			{
				if ("OP".equals(text)) mPost.setOriginalPoster(true);
				break;
			}
			case EXPECT_COMMENT:
			{
				if (text != null)
				{
					int index = text.indexOf("<span class=\"pomyanem\"");
					if (index >= 0)
					{
						boolean banned = text.indexOf("Помянем", index) >= 0;
						if (banned) mPost.setPosterBanned(true); else mPost.setPosterWarned(true);
						text = text.substring(0, index);
					}
					text = text.replace(" (OP)</a>", "</a>");
				}
				mPost.setComment(text);
				if (mAttachments.size() > 0) mPost.setAttachments(mAttachments);
				mPosts.add(mPost);
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
	
	private static final Pattern DATE_1 = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{2}) \\w+ " +
			"(\\d{2}):(\\d{2}):(\\d{2})");
	
	private static final Pattern DATE_2 = Pattern.compile("\\w+ (\\d{2}) (\\w+) (\\d{4}) " +
			"(\\d{2}):(\\d{2}):(\\d{2})");
	
	public static final TimeZone TIMEZONE_GMT = TimeZone.getTimeZone("Etc/GMT");
	
	public static final List<String> MONTHS_1 = Arrays.asList(new String[] {"января", "февраля", "марта",
			"апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"});
	
	public static final List<String> MONTHS_2 = Arrays.asList(new String[] {"Янв", "Фев", "Мар",
			"Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"});
	
	private long parseTimestamp(String date)
	{
		Matcher matcher = DATE_1.matcher(date);
		if (matcher.find())
		{
			int day = Integer.parseInt(matcher.group(1));
			int month = Integer.parseInt(matcher.group(2)) - 1;
			int year = Integer.parseInt(matcher.group(3)) + 2000;
			int hour = Integer.parseInt(matcher.group(4));
			int minute = Integer.parseInt(matcher.group(5));
			int second = Integer.parseInt(matcher.group(6));
			GregorianCalendar calendar = new GregorianCalendar(year, month, day, hour, minute, second);
			calendar.setTimeZone(TIMEZONE_GMT);
			calendar.add(GregorianCalendar.HOUR, -3);
			return calendar.getTimeInMillis();
		}
		else
		{
			matcher = DATE_2.matcher(date);
			if (matcher.find())
			{
				int day = Integer.parseInt(matcher.group(1));
				String monthString = matcher.group(2);
				int month = MONTHS_1.indexOf(monthString);
				if (month == -1) month = MONTHS_2.indexOf(monthString);
				if (month == -1) return 0L;
				int year = Integer.parseInt(matcher.group(3));
				int hour = Integer.parseInt(matcher.group(4));
				int minute = Integer.parseInt(matcher.group(5));
				int second = Integer.parseInt(matcher.group(6));
				GregorianCalendar calendar = new GregorianCalendar(year, month, day, hour, minute, second);
				calendar.setTimeZone(TIMEZONE_GMT);
				calendar.add(GregorianCalendar.HOUR, -3);
				return calendar.getTimeInMillis();
			}
		}
		return 0L;
	}
}