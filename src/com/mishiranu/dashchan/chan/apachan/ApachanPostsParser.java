package com.mishiranu.dashchan.chan.apachan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.Threads;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class ApachanPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final ApachanChanConfiguration mConfiguration;
	private final ApachanChanLocator mLocator;
	private final String mBoardName;
	
	private String mParent;
	private Posts mThread;
	private Post mPost;
	private ArrayList<Posts> mThreads;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_SUBJECT = 1;
	private static final int EXPECT_COMMENT = 2;
	private static final int EXPECT_OMITTED = 3;
	private static final int EXPECT_PAGES_COUNT = 4;
	
	private int mExpect = EXPECT_NONE;
	private boolean mTableHandling = false;
	private String mNextSubject;
	
	private boolean mCheckPagesCount = false;
	private HashSet<String> mExistingPostNumbers;
	
	static final SimpleDateFormat DATE_FORMAT;
	
	static
	{
		DATE_FORMAT = new SimpleDateFormat("dd MMM,yy HH:mm", Locale.US);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}
	
	static final Pattern NUMBER = Pattern.compile("(\\d+)");
	
	public ApachanPostsParser(String source, Object linked, String boardName)
	{
		mSource = source;
		mConfiguration = ChanConfiguration.get(linked);
		mLocator = ChanLocator.get(linked);
		mBoardName = boardName;
	}
	
	public ApachanPostsParser(String source, Object linked, String boardName, String parent)
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
			mThreads.add(mThread);
			mPosts.clear();
		}
	}
	
	public Threads convertThreads() throws ParseException
	{
		mCheckPagesCount = true;
		mThreads = new ArrayList<>();
		GroupParser.parse(mSource, this);
		closeThread();
		return mThreads.size() > 0 ? new Threads(mThreads) : null;
	}
	
	public ArrayList<Post> convertPosts(String parent, HashSet<String> existingPostNumbers) throws ParseException
	{
		mParent = parent;
		mExistingPostNumbers = existingPostNumbers;
		GroupParser.parse(mSource, this);
		return mPosts.size() > 0 ? mPosts : null;
	}
	
	public Post convertSinglePost() throws ParseException
	{
		GroupParser.parse(mSource, this);
		return mPosts.size() > 0 ? mPosts.get(0) : null;
	}
	
	private void createPost(String number)
	{
		mPost = new Post();
		mPost.setParentPostNumber(mParent);
		mPost.setPostNumber(number);
		mPost.setSubject(mNextSubject);
		if (mThreads != null)
		{
			mThread = new Posts(mPost);
			mThreads.add(mThread);
		}
		if (number != null && mParent != null)
		{
			if (mExistingPostNumbers == null) mExistingPostNumbers = new HashSet<>();
			mExistingPostNumbers.add(number);
		}
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("table".equals(tagName))
		{
			mNextSubject = null;
			mTableHandling = true;
			if (mParent != null)
			{
				String style = parser.getAttr(attrs, "style");
				if ("font-size:13px;background-color:#CCEECC;border: 1px solid green;".equals(style))
				{
					createPost(mParent);
				}
			}
		}
		else if (mTableHandling && "div".equals(tagName))
		{
			if (mPost == null)
			{
				String id = parser.getAttr(attrs, "id");
				if (id != null && id.startsWith("cd_"))
				{
					id = id.substring(3);
					createPost(id);
				}
			}
			if (mPost != null && mPost.getComment() == null)
			{
				mExpect = EXPECT_COMMENT;
				return true;
			}
		}
		else if (mTableHandling && "a".equals(tagName))
		{
			if (mPost == null)
			{
				String name = parser.getAttr(attrs, "name");
				if (name != null && name.startsWith("t"))
				{
					name = name.substring(1);
					try
					{
						Integer.parseInt(name);
					}
					catch (NumberFormatException e)
					{
						name = null;
					}
					if (name != null) createPost(name);
				}
			}
			else
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("anew".equals(cssClass))
				{
					mExpect = EXPECT_OMITTED;
					return true;
				}
				else if (mPost.getComment() != null)
				{
					String href = parser.getAttr(attrs, "href");
					if (href != null)
					{
						int index = href.indexOf("#t");
						if (index >= 0)
						{
							href = href.substring(index + 2);
							try
							{
								Integer.parseInt(href);
							}
							catch (NumberFormatException e)
							{
								href = null;
							}
							if (href != null)
							{
								mPost.setComment(ApachanChanLocator.encodeBoardThreadPostHtml(mBoardName,
										mParent, href) + "<br>" + mPost.getComment());
							}
						}
					}
				}
			}
		}
		else if (mTableHandling && "b".equals(tagName))
		{
			if (mPost == null || mPost.getSubject() == null && mPost.getComment() == null)
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
		}
		else if (mCheckPagesCount && "span".equals(tagName) && mThreads.size() > 0)
		{
			String style = parser.getAttr(attrs, "style");
			if ("font-size:15px".equals(style))
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
		if ("table".equals(tagName))
		{
			mTableHandling = false;
			mPost = null;
			mThread = null;
		}
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end)
	{
		if (mTableHandling && mPost != null)
		{
			String text = source.substring(start, end).trim();
			if (text.startsWith("]"))
			{
				text = text.substring(1).trim();
				try
				{
					mPost.setTimestamp(DATE_FORMAT.parse(text).getTime());
				}
				catch (java.text.ParseException e)
				{
					
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
				String subject = StringUtils.emptyIfNull(StringUtils.clearHtml(text).trim());
				if (mPost != null) mPost.setSubject(subject); else mNextSubject = subject;
				break;
			}
			case EXPECT_COMMENT:
			{
				text = text.trim();
				if (mPost.getSubject() == null && text.startsWith("<b>"))
				{
					int index = text.indexOf("</b><br>");
					if (index >= 0)
					{
						String subject = text.substring(3, index);
						if (subject.indexOf('>') == -1 && subject.indexOf('<') == -1)
						{
							text = text.substring(index + 8);
							mPost.setSubject(StringUtils.emptyIfNull(StringUtils.clearHtml(subject).trim()));
						}
					}
				}
				int thumbEnd = -1;
				if (text.startsWith("<a href='/pic")) thumbEnd = text.indexOf("</a>") + 4;
				else if (text.startsWith("<img src='/thumbs")) thumbEnd = text.indexOf('>') + 1;
				if (thumbEnd >= 0)
				{
					String imageData = text.substring(0, thumbEnd);
					text = text.substring(thumbEnd);
					int index1 = imageData.indexOf("/thumbs");
					int index2 = imageData.indexOf("'", index1);
					String thumbnail = imageData.substring(index1, index2);
					String image = thumbnail;
					if (imageData.startsWith("<a ")) image = image.replace("/thumbs/", "/images/");
					FileAttachment attachment = new FileAttachment();
					attachment.setFileUri(mLocator, mLocator.buildPath(image));
					attachment.setThumbnailUri(mLocator, mLocator.buildPath(thumbnail));
					mPost.setAttachments(attachment);
				}
				text = text.replaceAll("<a style=\"background:darkgray;color:darkgray\".*?>(.*?)</a>",
						"<span class=\"spoiler\">$1</span>"); // Fix spoilers
				text = fixCommentLinks(text);
				text = fixCommentQuotes(text);
				text = fixCommentPostLinks(text);
				text = fixCommentAttachments(text);
				mPost.setComment(text);
				mPosts.add(mPost);
				break;
			}
			case EXPECT_OMITTED:
			{
				text = StringUtils.clearHtml(text);
				if (text.startsWith("Ответов"))
				{
					Matcher matcher = NUMBER.matcher(text);
					if (matcher.find()) mThread.addPostsCount(Integer.parseInt(matcher.group(1)) + 1);
				}
				break;
			}
			case EXPECT_PAGES_COUNT:
			{
				int pagesCount = extractPagesCount(text);
				if (pagesCount >= 0) mConfiguration.storePagesCount(mBoardName, pagesCount + 1);
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
	
	private String fixCommentLinks(String comment)
	{
		int index = 0;
		StringBuilder builder = null;
		while (true)
		{
			index = builder != null ? builder.indexOf("<a href=\"/go.php?", index)
					: comment.indexOf("<a href=\"/go.php?");
			if (index >= 0)
			{
				if (builder == null) builder = new StringBuilder(comment);
				int indexEnd = builder.indexOf(">", index) + 1;
				if (indexEnd <= 0) break;
				int indexClose = builder.indexOf("</a>", indexEnd);
				if (indexClose < 0) break;
				int end = indexClose + 4;
				String url = builder.substring(indexEnd, indexClose).replace("\"", "&quot;");
				if (url.startsWith("http://") || url.startsWith("https://"))
				{
					String replacement = "<a href=\"" + url + "\">" + url + "</a>";
					builder.replace(index, end, replacement);
					index += replacement.length();
				}
				else index = end;
			}
			else break;
		}
		if (builder != null) comment = builder.toString();
		return comment;
	}
	
	private String fixCommentQuotes(String comment)
	{
		int index = 0;
		StringBuilder builder = null;
		while (true)
		{
			index = builder != null ? builder.indexOf("<div class=\"quote\">", index)
					: comment.indexOf("<div class=\"quote\">");
			if (index >= 0)
			{
				index += 19;
				if (builder == null) builder = new StringBuilder(comment);
				int end = builder.indexOf("</div>", index);
				if (end < 0) break;
				String replacement = builder.substring(index, end);
				replacement = replacement.replaceAll("^|(?<=<br>)", "&gt; ");
				builder.replace(index, end, replacement);
				index += replacement.length() + 6;
				if (builder.indexOf("<br>", index) == index) builder.delete(index, index + 4);
			}
			else break;
		}
		if (builder != null) comment = builder.toString();
		return comment;
	}
	
	private static final Pattern PATTERN_LINK = Pattern.compile("<a href=\"/(\\d+).html\">#(\\d+)</a>");
	
	private String fixCommentPostLinks(String comment)
	{
		StringBuffer buffer = null;
		Matcher matcher = PATTERN_LINK.matcher(comment);
		while (matcher.find())
		{
			if (buffer == null) buffer = new StringBuffer();
			String postNumber = matcher.group(1);
			String threadNumber = mExistingPostNumbers != null && mExistingPostNumbers.contains(postNumber)
					? mParent : null;
			matcher.appendReplacement(buffer, ApachanChanLocator.encodeBoardThreadPostHtml(mBoardName,
					threadNumber, postNumber));
		}
		if (buffer != null)
		{
			matcher.appendTail(buffer);
			comment = buffer.toString();
		}
		return comment;
	}
	
	private static final Pattern PATTERN_OBJECT_IFRAME = Pattern.compile("<(object|iframe).*?>.*?</\\1>");
	
	private String fixCommentAttachments(String comment)
	{
		StringBuffer buffer = null;
		Matcher matcher = PATTERN_OBJECT_IFRAME.matcher(comment);
		while (matcher.find())
		{
			if (buffer == null) buffer = new StringBuffer();
			String data = matcher.group();
			String replaceLink = null;
			int start = data.indexOf("http://");
			if (start == -1) start = data.indexOf("https://");
			if (start >= 0)
			{
				int end = StringUtils.nearestIndexOf(data, start, '"', '\'', '>', '<', ' ');
				if (end > start) replaceLink = data.substring(start, end);
			}
			if (replaceLink != null)
			{
				replaceLink = replaceLink.replace("\"", "&quot;").replace("<", "&lt;").replace("<", "&gt;");
				matcher.appendReplacement(buffer, "<a href=\"" + replaceLink + "\">" + replaceLink + "</a>");
			}
			else matcher.appendReplacement(buffer, "");
		}
		if (buffer != null)
		{
			matcher.appendTail(buffer);
			comment = buffer.toString();
		}
		return comment;
	}
	
	public static int extractPagesCount(String text)
	{
		text = StringUtils.clearHtml(text);
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) text = matcher.group(1);
		try
		{
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e)
		{
			
		}
		return -1;
	}
}