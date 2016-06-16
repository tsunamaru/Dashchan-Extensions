package com.mishiranu.dashchan.chan.desustorage;

import java.util.List;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class DesustorageChanLocator extends ChanLocator
{
	private static final String HOST_MAIN = "desustorage.org";
	private static final String HOST_DATA = "data.desustorage.org";
	
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+(?:/(?:page/\\d+/?)?)?");
	private static final Pattern THREAD_PATH = Pattern.compile("/\\w+/(?:thread|post)/(\\d+)/?");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/\\w+/image/\\d+/\\d+/\\d+\\.\\w+");
	
	public DesustorageChanLocator()
	{
		addChanHost(HOST_MAIN);
		addSpecialChanHost(HOST_DATA);
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
		List<String> segments = uri.getPathSegments();
		if (segments.size() > 0) return segments.get(0);
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
		return pageNumber > 0 ? buildPath(boardName, "page", Integer.toString(pageNumber + 1), "")
				: buildPath(boardName, "");
	}
	
	@Override
	public Uri createThreadUri(String boardName, String threadNumber)
	{
		return buildPath(boardName, "thread", threadNumber, "");
	}
	
	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber)
	{
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
	
	public Uri createAttachmentUri(String path)
	{
		return buildPathWithHost(HOST_DATA, path);
	}
}