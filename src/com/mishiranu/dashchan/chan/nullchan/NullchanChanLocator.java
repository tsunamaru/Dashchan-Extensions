package com.mishiranu.dashchan.chan.nullchan;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class NullchanChanLocator extends ChanLocator {
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+/?");
	private static final Pattern THREAD_PATH = Pattern.compile("/\\w+/(\\d+)/?");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/\\d+/\\d+/\\d+/[^/]+");

	public NullchanChanLocator() {
		addChanHost("0chan.hk");
		addChanHost("nullchan7msxi257.onion");
		addConvertableChanHost("www.0chan.hk");
		for (int i = 0; i < 100; i++) {
			addSpecialChanHost(String.format(Locale.US, "s%02d.0chan.hk", i));
		}
		setHttpsMode(HttpsMode.CONFIGURABLE);
	}

	public String getHostTransition(String chanHost, String requiredHost) {
		if (requiredHost.startsWith("s01.") || requiredHost.startsWith("s02.")) {
			return requiredHost.substring(0, 4) + chanHost;
		}
		return null;
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
		List<String> segments = uri.getPathSegments();
		if (segments.size() > 0) {
			return segments.get(0);
		}
		return null;
	}

	@Override
	public String getThreadNumber(Uri uri) {
		return getGroupValue(uri.getPath(), THREAD_PATH, 1);
	}

	@Override
	public String getPostNumber(Uri uri) {
		return uri.getFragment();
	}

	@Override
	public Uri createBoardUri(String boardName, int pageNumber) {
		return buildPath(boardName);
	}

	@Override
	public Uri createThreadUri(String boardName, String threadNumber) {
		return buildPath(boardName, threadNumber);
	}

	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber) {
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
}
