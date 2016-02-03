package com.mishiranu.dashchan.chan.apachan;

import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class ApachanChanLocator extends ChanLocator
{
	private static final Pattern BOARD_PATH = Pattern.compile("/([a-z]+)(?:_\\d+)?\\.html");
	private static final Pattern THREAD_PATH = Pattern.compile("/(\\d+)(?:_\\d+)?\\.html");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/images/\\d+/\\d+/\\w+\\.jpg");
	
	public ApachanChanLocator()
	{
		addChanHost("apachan.net");
		addSpecialChanHost("www.apachan.net");
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
		String boardName = getGroupValue(uri.getPath(), BOARD_PATH, 1);
		if (boardName != null) return boardName;
		String[] data = parseBoardThreadPost(uri);
		return data != null ? data[0] : null;
	}
	
	@Override
	public String getThreadNumber(Uri uri)
	{
		if (isThreadUri(uri))
		{
			String[] data = parseBoardThreadPost(uri);
			if (data != null) return data[1];
			return getGroupValue(uri.getPath(), THREAD_PATH, 1);
		}
		return null;
	}
	
	@Override
	public String getPostNumber(Uri uri)
	{
		String[] data = parseBoardThreadPost(uri);
		return data != null ? data[2] : null;
	}
	
	@Override
	public Uri createBoardUri(String boardName, int pageNumber)
	{
		return buildPath(boardName + (pageNumber > 0 ? "_" + pageNumber : "") + ".html");
	}
	
	@Override
	public Uri createThreadUri(String boardName, String threadNumber)
	{
		return createPostUri(boardName, threadNumber, threadNumber);
	}
	
	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber)
	{
		Uri uri = buildPath();
		String pathFragment = encodeBoardThreadPostPathFragment(boardName, threadNumber, postNumber);
		return Uri.parse(uri + pathFragment);
	}
	
	public static String[] parseBoardThreadPost(Uri uri)
	{
		return uri != null ? parseBoardThreadPost(uri.getFragment()) : null;
	}
	
	public static String[] parseBoardThreadPost(String fragment)
	{
		if (fragment != null)
		{
			String[] splitted = fragment.split("&");
			if (splitted.length > 0)
			{
				String boardName = null;
				String threadNumber = null;
				String postNumber = null;
				for (String data : splitted)
				{
					if (data.startsWith("board=")) boardName = data.substring(6);
					else if (data.startsWith("thread=")) threadNumber = data.substring(7);
					else if (data.startsWith("post=")) postNumber = data.substring(5);
				}
				return new String[] {boardName, threadNumber, postNumber};
			}
		}
		return null;
	}
	
	public static String encodeBoardThreadPostPathFragment(String boardName, String threadNumber, String postNumber)
	{
		return "/" + (postNumber != null ? postNumber : threadNumber) + ".html#board=" + boardName + "&thread=" +
				(threadNumber != null ? threadNumber : postNumber) + "&post=" + postNumber;
	}
	
	public static String encodeBoardThreadPostHtml(String boardName, String threadNumber, String postNumber)
	{
		return "<a href=\"" + encodeBoardThreadPostPathFragment(boardName, threadNumber, postNumber) + "\">" +
				"&gt;&gt;" + postNumber + "</a>";
	}
}