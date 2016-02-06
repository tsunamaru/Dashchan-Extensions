package com.mishiranu.dashchan.chan.local;

import java.io.File;
import java.util.ArrayList;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class LocalPostsParser implements GroupParser.Callback
{
	private final String mSource;
	private final ChanLocator mLocator;
	private final String mThreadNumber;
	private final String mLocalPath;
	
	private boolean mOnlyOriginalPost;
	private String mParent;
	private Uri mThreadUri;
	private int mPostsCount;
	private int mFilesCount;
	
	private Post mPost;
	private FileAttachment mAttachment;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	private final ArrayList<FileAttachment> mAttachments = new ArrayList<>();
	private final ArrayList<Icon> mIcons = new ArrayList<>();
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_SUBJECT = 1;
	private static final int EXPECT_COMMENT = 2;
	
	private int mExpect = EXPECT_NONE;
	
	private static class OriginalPostParsedException extends ParseException
	{
		private static final long serialVersionUID = 1L;
	}
	
	public LocalPostsParser(String source, Object linked, String threadNumber, File localDownloadDirectory)
	{
		mSource = source;
		mLocator = ChanLocator.get(linked);
		mThreadNumber = threadNumber;
		mLocalPath = localDownloadDirectory.getPath();
	}
	
	public Posts convertPosts() throws ParseException
	{
		GroupParser.parse(mSource, this);
		return mPosts.size() > 0 ? new Posts(mPosts).setArchivedThreadUri(mThreadUri) : null;
	}
	
	public Posts convertThread() throws ParseException
	{
		mOnlyOriginalPost = true;
		try
		{
			GroupParser.parse(mSource, this);
		}
		catch (OriginalPostParsedException e)
		{
			
		}
		return new Posts(mPost).addPostsCount(mPostsCount).addFilesCount(mFilesCount);
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws OriginalPostParsedException
	{
		if (attrs != null && attrs.contains("data-"))
		{
			String number = parser.getAttr(attrs, "data-number");
			if (number != null)
			{
				if (mOnlyOriginalPost && mPost != null) throw new OriginalPostParsedException();
				mPost = new Post();
				mPost.setPostNumber(number);
				mPost.setThreadNumber(mThreadNumber);
				if (mParent == null) mParent = number; else mPost.setParentPostNumber(mParent);
			}
			String posterName = parser.getAttr(attrs, "data-name");
			if (posterName != null)
			{
				mPost.setName(StringUtils.clearHtml(posterName));
			}
			String identifier = parser.getAttr(attrs, "data-identifier");
			if (identifier != null)
			{
				mPost.setIdentifier(StringUtils.clearHtml(identifier));
			}
			String tripcode = parser.getAttr(attrs, "data-tripcode");
			if (tripcode != null)
			{
				mPost.setTripcode(StringUtils.clearHtml(tripcode));
			}
			String capcode = parser.getAttr(attrs, "data-capcode");
			if (capcode != null)
			{
				mPost.setCapcode(StringUtils.clearHtml(capcode));
			}
			String defaultName = parser.getAttr(attrs, "data-default-name");
			if (defaultName != null)
			{
				mPost.setDefaultName(true);
			}
			String email = parser.getAttr(attrs, "data-email");
			if (email != null)
			{
				mPost.setEmail(StringUtils.clearHtml(email));
			}
			String timestamp = parser.getAttr(attrs, "data-timestamp");
			if (timestamp != null)
			{
				mPost.setTimestamp(Long.parseLong(timestamp));
			}
			String sage = parser.getAttr(attrs, "data-sage");
			if (sage != null)
			{
				mPost.setSage(true);
			}
			String op = parser.getAttr(attrs, "data-op");
			if (op != null)
			{
				mPost.setOriginalPoster(true);
			}
			String file = parser.getAttr(attrs, "data-file");
			if (file != null)
			{
				mAttachment = new FileAttachment();
				mAttachments.add(mAttachment);
				mAttachment.setFileUri(mLocator, createFileUriLocal(file));
			}
			String thumbnail = parser.getAttr(attrs, "data-thumbnail");
			if (thumbnail != null)
			{
				mAttachment.setThumbnailUri(mLocator, createFileUriLocal(thumbnail));
			}
			String originalName = parser.getAttr(attrs, "data-original-name");
			if (originalName != null)
			{
				mAttachment.setOriginalName(StringUtils.clearHtml(originalName));
			}
			String size = parser.getAttr(attrs, "data-size");
			if (size != null)
			{
				mAttachment.setSize(Integer.parseInt(size));
			}
			String width = parser.getAttr(attrs, "data-width");
			if (width != null)
			{
				mAttachment.setWidth(Integer.parseInt(width));
			}
			String height = parser.getAttr(attrs, "data-height");
			if (height != null)
			{
				mAttachment.setHeight(Integer.parseInt(height));
			}
			String icon = parser.getAttr(attrs, "data-icon");
			if (icon != null)
			{
				String src = parser.getAttr(attrs, "src");
				String title = StringUtils.clearHtml(parser.getAttr(attrs, "title"));
				mIcons.add(new Icon(mLocator, Uri.parse(src), title));
			}
			String subject = parser.getAttr(attrs, "data-subject");
			if (subject != null)
			{
				mExpect = EXPECT_SUBJECT;
				return true;
			}
			String comment = parser.getAttr(attrs, "data-comment");
			if (comment != null)
			{
				mExpect = EXPECT_COMMENT;
				return true;
			}
			String threadUriString = parser.getAttr(attrs, "data-thread-uri");
			if (threadUriString != null)
			{
				mThreadUri = Uri.parse(threadUriString);
			}
			String postsCount = parser.getAttr(attrs, "data-posts");
			if (postsCount != null)
			{
				mPostsCount = Integer.parseInt(postsCount);
			}
			String filesCount = parser.getAttr(attrs, "data-files");
			if (filesCount != null)
			{
				mFilesCount = Integer.parseInt(filesCount);
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
			case EXPECT_COMMENT:
			{
				mPost.setComment(text);
				mPosts.add(mPost);
				if (mAttachments != null)
				{
					mPost.setAttachments(mAttachments);
					mAttachments.clear();
				}
				if (mIcons != null)
				{
					mPost.setIcons(mIcons);
					mIcons.clear();
				}
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
	
	private Uri createFileUriLocal(String uriString)
	{
		Uri uri = Uri.parse(uriString);
		if (uri.isRelative())
		{
			String path = uri.getPath();
			if (path != null && !path.startsWith("/"))
			{
				path = mLocalPath + "/" + path;
				uri = new Uri.Builder().scheme("http").authority("localhost").path(path).build();
			}
		}
		return uri;
	}
}