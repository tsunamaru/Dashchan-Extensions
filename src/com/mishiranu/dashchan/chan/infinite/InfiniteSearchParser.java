package com.mishiranu.dashchan.chan.infinite;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.Post;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class InfiniteSearchParser implements GroupParser.Callback
{
	private final String mSource;
	private final InfiniteChanLocator mLocator;

	private Post mPost;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_SUBJECT = 1;
	private static final int EXPECT_NAME = 2;
	private static final int EXPECT_TRIPCODE = 3;
	private static final int EXPECT_CAPCODE = 4;
	private static final int EXPECT_COMMENT = 5;
	
	private int mExpect = EXPECT_NONE;
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
	
	public InfiniteSearchParser(String source, Object linked)
	{
		mSource = source;
		mLocator = ChanLocator.get(linked);
	}
	
	public ArrayList<Post> convertPosts() throws ParseException
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
			if (id != null && (id.startsWith("reply_") || id.startsWith("op_")))
			{
				Post post = new Post();
				post.setPostNumber(id.substring(id.indexOf('_') + 1, id.length()));
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
			}
		}
		else if ("span".equals(tagName))
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
			else if ("tripcode".equals(cssClass))
			{
				mExpect = EXPECT_TRIPCODE;
				return true;
			}
			else if ("capcode".equals(cssClass))
			{
				mExpect = EXPECT_CAPCODE;
				return true;
			}
		}
		else if ("a".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("email".equals(cssClass))
			{
				String email = parser.getAttr(attrs, "href");
				if (email != null)
				{
					email = StringUtils.clearHtml(email);
					if (email.startsWith("mailto:")) email = email.substring(7);
					if (email.equalsIgnoreCase("sage")) mPost.setSage(true); else mPost.setEmail(email);
				}
			}
			else if ("post_no".equals(cssClass))
			{
				Uri uri = Uri.parse(parser.getAttr(attrs, "href"));
				String resto = mLocator.getThreadNumber(uri);
				if (!mPost.getPostNumber().equals(resto)) mPost.setParentPostNumber(resto);
			}
		}
		else if ("time".equals(tagName))
		{
			if (mPost != null)
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
						
					}
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
				mPost.setComment(text);
				mPosts.add(mPost);
				mPost = null;
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}