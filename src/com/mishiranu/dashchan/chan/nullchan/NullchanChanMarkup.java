package com.mishiranu.dashchan.chan.nullchan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class NullchanChanMarkup extends ChanMarkup {
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_STRIKE | TAG_SPOILER | TAG_CODE;

	public NullchanChanMarkup() {
		addTag("b", TAG_BOLD);
		addTag("i", TAG_ITALIC);
		addTag("del", TAG_STRIKE);
		addTag("mark", TAG_SPOILER);
		addTag("blockquote", TAG_QUOTE);
		addTag("code", TAG_CODE);
		addBlock("blockquote", false, false);
		addPreformatted("code", true);
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName) {
		CommentEditor commentEditor = new CommentEditor.WakabaMarkCommentEditor();
		commentEditor.addTag(TAG_STRIKE, "-", "-", CommentEditor.FLAG_ONE_LINE);
		return commentEditor;
	}

	@Override
	public boolean isTagSupported(String boardName, int tag) {
		return (SUPPORTED_TAGS & tag) == tag;
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(?:#(\\d+))?");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) {
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.matches()) {
			return new Pair<>(null, matcher.group(1));
		}
		return null;
	}
}
