package com.mishiranu.dashchan.chan.apachan;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class ApachanChanMarkup extends ChanMarkup
{
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_UNDERLINE | TAG_STRIKE | TAG_SPOILER;
	
	public ApachanChanMarkup()
	{
		addTag("b", TAG_BOLD);
		addTag("i", TAG_ITALIC);
		addTag("u", TAG_UNDERLINE);
		addTag("s", TAG_STRIKE);
		addTag("span", "spoiler", TAG_SPOILER);
		addTag("div", "quote", TAG_QUOTE);
	}
	
	@Override
	public CommentEditor obtainCommentEditor(String boardName)
	{
		return new CommentEditor.BulletinBoardCodeCommentEditor();
	}
	
	@Override
	public boolean isTagSupported(String boardName, int tag)
	{
		return (SUPPORTED_TAGS & tag) == tag;
	}
	
	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString)
	{
		int index = uriString.indexOf('#');
		if (index >= 0)
		{
			String fragment = uriString.substring(index + 1);
			String[] data = ApachanChanLocator.parseBoardThreadPost(fragment);
			if (data != null) return new Pair<String, String>(data[1], data[2]);
		}
		return null;
	}
}