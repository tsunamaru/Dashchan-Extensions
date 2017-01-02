package com.mishiranu.dashchan.chan.candydollchan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class CandydollchanChanMarkup extends ChanMarkup {
	public CandydollchanChanMarkup() {
		addTag("span", "unkfunc", TAG_QUOTE);
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName) {
		return new CommentEditor.WakabaMarkCommentEditor();
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) {
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) {
			return new Pair<>(matcher.group(1), matcher.group(2));
		}
		return null;
	}
}