package com.mishiranu.dashchan.chan.dvach;

import java.util.List;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class DvachChanLocator extends ChanLocator
{
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+(?:/(?:(?:index|catalog|\\d+)\\.html)?)?");
	private static final Pattern THREAD_PATH = Pattern.compile("/\\w+/res/\\d+\\.html");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/\\w+/src/\\d+/\\d+\\.\\w+");
	
	private static final Pattern THREAD_NUMBER = Pattern.compile("^/\\w+/(?:res|src)/(\\d+)(?:/|\\.html)");
	
	public DvachChanLocator()
	{
		addChanHost("2ch.hk");
		addChanHost("2ch.pm");
		addChanHost("2ch.cm");
		addChanHost("2ch.re");
		addChanHost("2ch.tf");
		addChanHost("2ch.wf");
		addChanHost("2ch.yt");
		addChanHost("2-ch.so");
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
		return getGroupValue(uri.getPath(), THREAD_NUMBER, 1);
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
		return buildPath(boardName, "res", threadNumber + ".html");
	}
	
	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber)
	{
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
	
	public Uri createApiUri(String name, String... params)
	{
		Uri.Builder builder = buildPath("makaba", name).buildUpon();
		for (int i = 0; i < params.length; i += 2) builder.appendQueryParameter(params[i], params[i + 1]);
		return builder.build();
	}
}