package com.mishiranu.dashchan.chan.xyntach;

import java.util.List;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class XyntachChanLocator extends ChanLocator {
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+(?:/\\w+)?(?:/(?:\\d+\\.html)?)?");
	private static final Pattern THREAD_PATH = Pattern.compile("/\\w+(?:/\\w+)?/res/(\\d+)(?:[+-]\\d+)?\\.html");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/\\w+(?:/\\w+)?/src/\\d+\\.\\w+");

	public XyntachChanLocator() {
		addChanHost("xynta.ch");
		addConvertableChanHost("www.xynta.ch");
		setHttpsMode(HttpsMode.CONFIGURABLE);
	}

	@Override
	public boolean isBoardUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)
				&& !uri.getPath().contains("/res/") && !uri.getPath().contains("/src/");
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
		List<String> segments = uri.getPathSegments();
		if (segments.size() > 0) {
			String boardName = segments.get(0);
			if (segments.size() > 1) {
				String nextSegment = segments.get(1);
				if (!"res".equals(nextSegment) && !"src".equals(nextSegment)) {
					boardName = boardName + "-" + nextSegment;
				}
			}
			return boardName;
		}
		return null;
	}

	@Override
	public String getThreadNumber(Uri uri) {
		return getGroupValue(uri.getPath(), THREAD_PATH, 1);
	}

	@Override
	public String getPostNumber(Uri uri) {
		String fragment = uri.getFragment();
		if (fragment != null && fragment.startsWith("i")) {
			return fragment.substring(1);
		}
		return fragment;
	}

	public String convertInternalBoardName(String boardName) {
		return boardName != null ? boardName.replace('-', '/') : null;
	}

	public String[] splitInternalBoardName(String boardName) {
		String[] splitted = boardName != null ? boardName.split("-") : null;
		String[] result = new String[2];
		if (splitted != null && splitted.length > 0) {
			if (splitted.length == 1) {
				result[1] = splitted[0];
			} else {
				result[0] = splitted[0];
				result[1] = splitted[1];
			}
		}
		return result;
	}

	@Override
	public Uri createBoardUri(String boardName, int pageNumber) {
		boardName = convertInternalBoardName(boardName);
		return pageNumber > 0 ? buildPath(boardName, pageNumber + ".html") : buildPath(boardName, "");
	}

	@Override
	public Uri createThreadUri(String boardName, String threadNumber) {
		boardName = convertInternalBoardName(boardName);
		return buildPath(boardName, "res", threadNumber + ".html");
	}

	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber) {
		boardName = convertInternalBoardName(boardName);
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
}