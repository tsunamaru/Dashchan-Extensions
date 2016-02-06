package com.mishiranu.dashchan.chan.krautchan;

import java.util.List;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class KrautchanChanLocator extends ChanLocator
{
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+(?:/\\d+\\.html)|/catalog/\\w+/?");
	private static final Pattern THREAD_PATH = Pattern.compile("/\\w+/thread-(\\d+)\\.html");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/files/\\d+\\.\\w+");
	
	public KrautchanChanLocator()
	{
		addChanHost("krautchan.net");
		setHttpsMode(HttpsMode.CONFIGURABLE);
	}
	
	@Override
	public boolean isBoardUri(Uri uri)
	{
		return isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH);
	}
	
	@Override
	public boolean isThreadUri(Uri uri)
	{
		return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH);
	}
	
	@Override
	public boolean isAttachmentUri(Uri uri)
	{
		return isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH);
	}
	
	@Override
	public String getBoardName(Uri uri)
	{
		if (uri != null)
		{
			List<String> segments = uri.getPathSegments();
			if (segments.size() > 0)
			{
				String boardName = segments.get(0);
				if ("files".equals(boardName) || "thumbnails".equals(boardName) || "banned".equals(boardName))
				{
					return null;
				}
				if ("catalog".equals(boardName))
				{
					if (segments.size() > 1) return segments.get(1);
					return null;
				}
				return boardName;
			}
		}
		return null;
	}
	
	@Override
	public String getThreadNumber(Uri uri)
	{
		return uri != null ? getGroupValue(uri.getPath(), THREAD_PATH, 1) : null;
	}
	
	@Override
	public String getPostNumber(Uri uri)
	{
		return uri.getFragment();
	}
	
	@Override
	public Uri createBoardUri(String boardName, int pageNumber)
	{
		return pageNumber > 0 ? buildPath(boardName, pageNumber + ".html") : buildPath(boardName, "");
	}
	
	@Override
	public Uri createThreadUri(String boardName, String threadNumber)
	{
		return buildPath(boardName, "thread-" + threadNumber + ".html");
	}
	
	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber)
	{
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
}