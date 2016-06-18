package com.mishiranu.dashchan.chan.cablesix;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class CableSixChanMarkup extends ChanMarkup
{
	private static final int SUPPORTED_TAGS = TAG_STRIKE | TAG_SPOILER;
	
	public CableSixChanMarkup()
	{
		addTag("del", TAG_STRIKE);
		addTag("span", "quote", TAG_QUOTE);
		addTag("span", "spoiler", TAG_SPOILER);
	}
	
	@Override
	public CommentEditor obtainCommentEditor(String boardName)
	{
		CommentEditor commentEditor = new CommentEditor();
		commentEditor.addTag(TAG_STRIKE, "--", "--", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_SPOILER, "**", "**", CommentEditor.FLAG_ONE_LINE);
		return commentEditor;
	}
	
	@Override
	public boolean isTagSupported(String boardName, int tag)
	{
		return (SUPPORTED_TAGS & tag) == tag;
	}
	
	private static final Pattern THREAD_LINK = Pattern.compile("/res/(\\d+)(?:.html)?(?:#(\\d+))?$");
	
	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString)
	{
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) return new Pair<>(matcher.group(1), matcher.group(2));
		return null;
	}
}