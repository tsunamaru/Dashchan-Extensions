package com.mishiranu.dashchan.chan.infinite;

import java.util.ArrayList;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class InfiniteCatalogParser implements GroupParser.Callback
{
	private final String mSource;
	private final InfiniteChanLocator mLocator;

	private Post mPost;
	private final ArrayList<Posts> mThreads = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_COMMENT = 1;
	
	private int mExpect = EXPECT_NONE;
	
	public InfiniteCatalogParser(String source, Object linked)
	{
		mSource = source;
		mLocator = ChanLocator.get(linked);
	}
	
	public ArrayList<Posts> convertThreads() throws ParseException
	{
		GroupParser.parse(mSource, this);
		return mThreads;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException
	{
		if ("div".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("mix".equals(cssClass))
			{
				String id = parser.getAttr(attrs, "data-id");
				boolean sticky = "true".equals(parser.getAttr(attrs, "data-sticky"));
				boolean closed = "true".equals(parser.getAttr(attrs, "data-locked"));
				boolean cyclical = "true".equals(parser.getAttr(attrs, "data-cycle"));
				String time = parser.getAttr(attrs, "data-time");
				String replies = parser.getAttr(attrs, "data-reply");
				if (id != null)
				{
					mPost = new Post().setPostNumber(id).setSticky(sticky).setClosed(closed).setCyclical(cyclical);
					if (time != null) mPost.setTimestamp(Long.parseLong(time) * 1000L);
					Posts posts = new Posts(mPost).addPostsCount(1);
					if (replies != null) posts.addPostsCount(Integer.parseInt(replies));
					mThreads.add(posts);
				}
			}
			else if (mPost != null && "replies".equals(cssClass))
			{
				mExpect = EXPECT_COMMENT;
				return true;
			}
		}
		else if ("img".equals(tagName))
		{
			if (mPost != null)
			{
				String id = parser.getAttr(attrs, "id");
				if (("img-" + mPost.getPostNumber()).equals(id))
				{
					String name = parser.getAttr(attrs, "data-name");
					if (name != null) name = StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim());
					String subject = parser.getAttr(attrs, "data-subject");
					if (subject != null) subject = StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim());
					mPost.setName(name);
					mPost.setSubject(subject);
					String thumbnail = parser.getAttr(attrs, "src");
					String image = parser.getAttr(attrs, "data-fullimage");
					if (!StringUtils.isEmpty(image))
					{
						FileAttachment attachment = new FileAttachment();
						attachment.setFileUri(mLocator, mLocator.buildPath(image));
						if (!StringUtils.isEmpty(thumbnail))
						{
							attachment.setThumbnailUri(mLocator, mLocator.buildPath(thumbnail));
						}
						mPost.setAttachments(attachment);
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
			case EXPECT_COMMENT:
			{
				int index = text.indexOf("<span class=\"subject\">");
				if (index >= 0)
				{
					index = text.indexOf("</span></p>");
					if (index >= 0) index += 11;
				}
				if (index == -1)
				{
					index = text.indexOf("</strong>");
					if (index >= 0) index += 9;
				}
				if (index >= 0) text = text.substring(index);
				text = text.trim();
				text = InfiniteModelMapper.fixCommentLineBreaks(text);
				mPost.setComment(text);
				mPost = null;
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}