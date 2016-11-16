package com.mishiranu.dashchan.chan.freeportseven;

import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class FreeportSevenChanLocator extends ChanLocator {
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+(?:/(?:\\d+/?)?)?");
	private static final Pattern THREAD_PATH = Pattern.compile("/(\\d+)");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/uploads/\\d+/fp7-\\d+\\.\\w+");

	public FreeportSevenChanLocator() {
		addChanHost("freeport7.org");
		addConvertableChanHost("www.freeport7.org");
	}

	@Override
	public boolean isBoardUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH);
	}

	@Override
	public boolean isThreadUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH);
	}

	@Override
	public boolean isAttachmentUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH);
	}

	@Override
	public String getBoardName(Uri uri) {
		return "b";
	}

	@Override
	public String getThreadNumber(Uri uri) {
		return uri != null ? getGroupValue(uri.getPath(), THREAD_PATH, 1) : null;
	}

	@Override
	public String getPostNumber(Uri uri) {
		String fragment = uri.getFragment();
		if (fragment != null && fragment.startsWith("i")) {
			return fragment.substring(1);
		}
		return fragment;
	}

	@Override
	public Uri createBoardUri(String boardName, int pageNumber) {
		return pageNumber > 0 ? buildPath("~", Integer.toString(pageNumber + 1), "") : buildPath("~", "");
	}

	@Override
	public Uri createThreadUri(String boardName, String threadNumber) {
		return buildPath(threadNumber);
	}

	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber) {
		return createThreadUri(boardName, threadNumber).buildUpon().fragment("i" + postNumber).build();
	}
}