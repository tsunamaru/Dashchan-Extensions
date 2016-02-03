package com.mishiranu.dashchan.chan.cirno;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;


import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class CirnoCatalogParser implements GroupParser.Callback
{
	private final String mSource;
	private final CirnoChanLocator mLocator;
	
	private Post mPost;
	private final ArrayList<Posts> mThreads = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_SUBJECT = 1;
	private static final int EXPECT_COMMENT = 2;
	
	private int mExpect = EXPECT_NONE;
	
	private static final Pattern LINK_TITLE = Pattern.compile("#(\\d+) \\((.*)\\)");
	
	public CirnoCatalogParser(String source, Object linked)
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
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException
	{
		if ("a".equals(tagName))
		{
			String title = parser.getAttr(attrs, "title");
			if (title != null && title.startsWith("#"))
			{
				Matcher matcher = LINK_TITLE.matcher(title);
				if (matcher.matches())
				{
					String number = matcher.group(1);
					String date = matcher.group(2);
					Post post = new Post();
					post.setPostNumber(number);
					try
					{
						post.setTimestamp(CirnoPostsParser.DATE_FORMAT.parse(date).getTime());
					}
					catch (java.text.ParseException e)
					{
						
					}
					mPost = post;
					mThreads.add(new Posts(post));
				}
			}
		}
		else if (mPost != null)
		{
			if ("img".equals(tagName))
			{
				String src = parser.getAttr(attrs, "src");
				if (src != null)
				{
					FileAttachment attachment = new FileAttachment();
					Uri thumbnailUri = src.contains("/thumb/") ? mLocator.buildPath(src) : null;
					attachment.setThumbnailUri(mLocator, thumbnailUri);
					attachment.setSpoiler(src.contains("extras/icons/spoiler.png"));
					if (thumbnailUri != null)
					{
						Uri fileUri = mLocator.buildPath(src.replace("/thumb/", "/src/").replace("s.", "."));
						attachment.setFileUri(mLocator, fileUri);
					}
					mPost.setAttachments(attachment);
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
				else if ("cattext".equals(cssClass))
				{
					mExpect = EXPECT_COMMENT;
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		if ("div".equals(tagName)) mPost = null;
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
			case EXPECT_SUBJECT:
			{
				mPost.setSubject(StringUtils.emptyIfNull(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_COMMENT:
			{
				text = StringUtils.nullIfEmpty(text);
				if (text != null) text = text.trim() + '\u2026';
				mPost.setComment(text);
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}