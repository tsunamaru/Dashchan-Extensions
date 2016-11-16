package com.mishiranu.dashchan.chan.freeportseven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class FreeportSevenChanMarkup extends ChanMarkup {
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_UNDERLINE | TAG_STRIKE | TAG_SPOILER;

	public FreeportSevenChanMarkup() {
		addTag("em", "bold", TAG_BOLD);
		addTag("em", "italic", TAG_ITALIC);
		addTag("em", "underline", TAG_UNDERLINE);
		addTag("em", "strike", TAG_STRIKE);
		addTag("em", "spoiler", TAG_SPOILER);
		addTag("blockquote", TAG_QUOTE);
		addTag("pre", TAG_CODE);
		addBlock("blockquote", false, false);
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName) {
		CommentEditor commentEditor = new CommentEditor();
		commentEditor.addTag(TAG_BOLD, "**", "**", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_ITALIC, "*", "*", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_UNDERLINE, "__", "__", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_STRIKE, "_", "_", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_SPOILER, "%%", "%%", CommentEditor.FLAG_ONE_LINE);
		return commentEditor;
	}

	@Override
	public boolean isTagSupported(String boardName, int tag) {
		return (SUPPORTED_TAGS & tag) == tag;
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(\\d+)(?:#i?(\\d+))?$");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) {
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) {
			return new Pair<>(matcher.group(1), matcher.group(2));
		}
		return null;
	}
}