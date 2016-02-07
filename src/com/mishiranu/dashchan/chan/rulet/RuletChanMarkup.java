package com.mishiranu.dashchan.chan.rulet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class RuletChanMarkup extends ChanMarkup
{
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_UNDERLINE | TAG_STRIKE |
			TAG_SPOILER | TAG_CODE;
	
	public RuletChanMarkup()
	{
		addTag("strong", TAG_BOLD);
		addTag("em", TAG_ITALIC);
		addTag("ins", TAG_UNDERLINE);
		addTag("del", TAG_STRIKE);
		addTag("span", "unkfunc", TAG_QUOTE);
		addTag("span", "spoiler", TAG_SPOILER);
		addTag("code", TAG_CODE);
		addTag("pre", TAG_CODE);
		addPreformatted("code");
	}
	
	@Override
	public CommentEditor obtainCommentEditor(String boardName)
	{
		CommentEditor commentEditor = new CommentEditor.WakabaMarkCommentEditor();
		commentEditor.addTag(TAG_UNDERLINE, "__", "__", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_STRIKE, "--", "--", CommentEditor.FLAG_ONE_LINE);
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