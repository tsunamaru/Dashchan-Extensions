package com.mishiranu.dashchan.chan.britfags;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class BritfagsChanMarkup extends ChanMarkup
{
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_UNDERLINE | TAG_STRIKE |
			TAG_SUBSCRIPT | TAG_SUPERSCRIPT | TAG_SPOILER | TAG_CODE | TAG_HEADING;

	public BritfagsChanMarkup()
	{
		addTag("b", TAG_BOLD);
		addTag("i", TAG_ITALIC);
		addTag("strike", TAG_STRIKE);
		addTag("span", "style", "border-bottom: 1px dotted", TAG_UNDERLINE);
		addTag("span", "spoiler", TAG_SPOILER);
		addTag("span", "unkfunc", TAG_QUOTE);
		addTag("h2", TAG_HEADING);
		addTag("h3", TAG_HEADING);
		addTag("blockquote", TAG_QUOTE);
		addTag("sub", TAG_SUBSCRIPT);
		addTag("sup", TAG_SUPERSCRIPT);
		addTag("div", "style", "white-space: pre !important;font-family: monospace !important;", TAG_CODE);
		addPreformatted("div", "style", "white-space: pre !important;font-family: monospace !important;", true);
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName)
	{
		CommentEditor commentEditor = new CommentEditor.BulletinBoardCodeCommentEditor();
		commentEditor.addTag(TAG_HEADING, "[h3]", "[/h3]");
		return commentEditor;
	}

	@Override
	public boolean isTagSupported(String boardName, int tag)
	{
		return (SUPPORTED_TAGS & tag) == tag;
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString)
	{
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) return new Pair<>(matcher.group(1), matcher.group(2));
		return null;
	}
}