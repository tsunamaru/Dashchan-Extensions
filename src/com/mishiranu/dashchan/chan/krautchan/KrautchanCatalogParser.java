package com.mishiranu.dashchan.chan.krautchan;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class KrautchanCatalogParser implements GroupParser.Callback
{
	private final String mSource;
	private final ChanLocator mLocator;
	
	private Post mPost;
	private final ArrayList<Posts> mThreads = new ArrayList<>();
	private final ArrayList<FileAttachment> mAttachments = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_SUBJECT = 1;
	private static final int EXPECT_COMMENT = 2;
	private static final int EXPECT_OMITTED = 3;
	
	private int mExpect = EXPECT_NONE;
	
	public KrautchanCatalogParser(String source, Object linked)
	{
		mSource = source;
		mLocator = ChanLocator.get(linked);
	}
	
	public ArrayList<Posts> convert() throws ParseException
	{
		GroupParser.parse(mSource, this);
		return mThreads;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("article".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("thread_OP".equals(cssClass))
			{
				String number = parser.getAttr(attrs, "id");
				Post post = new Post();
				post.setPostNumber(number);
				mPost = post;
				Posts thread = new Posts(post);
				mThreads.add(thread);
			}
		}
		else if ("img".equals(tagName))
		{
			String src = parser.getAttr(attrs, "src");
			if (src != null && src.startsWith("/thumbnails/"))
			{
				String fileName = src.substring(12);
				String originalName = parser.getAttr(attrs, "alt");
				FileAttachment attachment = new FileAttachment();
				attachment.setFileUri(mLocator, mLocator.buildPath("files", fileName));
				attachment.setThumbnailUri(mLocator, mLocator.buildPath("thumbnails", fileName));
				attachment.setOriginalName(StringUtils.nullIfEmpty(StringUtils.clearHtml(originalName)));
				mAttachments.add(attachment);
				if (mPost.getTimestamp() == 0L)
				{
					Matcher matcher = KrautchanPostsParser.NUMBER.matcher(src);
					if (matcher.find())
					{
						String timestamp = matcher.group(1);
						if (timestamp.length() >= 12) mPost.setTimestamp(Long.parseLong(timestamp));
					}
				}
			}
			else if (mExpect == EXPECT_SUBJECT && src != null && src.startsWith("/images/balls/"))
			{
				String country = src.substring(src.lastIndexOf('/') + 1, src.lastIndexOf('.'));
				Uri uri = mLocator.buildPath("images", "balls", country.toLowerCase(Locale.US) + ".png");
				mPost.setIcons(new Icon(mLocator, uri, country.toUpperCase(Locale.US)));
			}
		}
		else if ("h1".equals(tagName))
		{
			if (mPost != null)
			{
				mExpect = EXPECT_SUBJECT;
			}
		}
		else if ("section".equals(tagName))
		{
			mExpect = EXPECT_COMMENT;
			return true;
		}
		else if ("span".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("omitted_text".equals(cssClass))
			{
				mExpect = EXPECT_OMITTED;
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		if ("h1".equals(tagName))
		{
			if (mExpect == EXPECT_SUBJECT) mExpect = EXPECT_NONE;
		}
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end) throws ParseException
	{
		if (mExpect == EXPECT_SUBJECT)
		{
			String text = source.substring(start, end).trim();
			if (text.length() > 0)
			{
				if (!("#" + mPost.getPostNumber()).equals(text)) mPost.setSubject(text);
				mExpect = EXPECT_NONE;
			}
		}
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		switch (mExpect)
		{
			case EXPECT_COMMENT:
			{
				text = text.trim();
				text = StringUtils.linkify(text);
				mPost.setComment(text);
				if (mAttachments.size() > 0)
				{
					mPost.setAttachments(mAttachments);;
					mAttachments.clear();
				}
				mPost = null;
				break;
			}
			case EXPECT_OMITTED:
			{
				Matcher matcher = KrautchanPostsParser.NUMBER.matcher(text);
				if (matcher.find())
				{
					Posts thread = mThreads.get(mThreads.size() - 1);
					thread.addPostsCount(Integer.parseInt(matcher.group(1)) + 1);
				}
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}