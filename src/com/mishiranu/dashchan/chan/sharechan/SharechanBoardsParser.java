package com.mishiranu.dashchan.chan.sharechan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class SharechanBoardsParser implements GroupParser.Callback
{
	private final String mSource;
	
	private final ArrayList<Board> mBoards = new ArrayList<>();
	private String mBoardName;
	
	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_BOARD_TITLE = 1;
	
	private int mExpect = EXPECT_NONE;
	private boolean mListHandling = false;
	
	private static final Pattern BOARD_URI = Pattern.compile("/(\\w+)/?");
	
	public SharechanBoardsParser(String source)
	{
		mSource = source;
	}
	
	public BoardCategory convert() throws ParseException
	{
		try
		{
			GroupParser.parse(mSource, this);
		}
		catch (FinishedException e)
		{
			
		}
		return new BoardCategory("Boards", mBoards);
	}
	
	private static class FinishedException extends ParseException
	{
		private static final long serialVersionUID = 1L;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws FinishedException
	{
		if ("strong".equals(tagName))
		{
			mListHandling = true;
		}
		else if (mListHandling && "a".equals(tagName))
		{
			String href = parser.getAttr(attrs, "href");
			Matcher matcher = BOARD_URI.matcher(href);
			if (matcher.matches())
			{
				mBoardName = matcher.group(1);
				mExpect = EXPECT_BOARD_TITLE;
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName) throws FinishedException
	{
		if (mListHandling && "strong".equals(tagName))
		{
			throw new FinishedException();
		}
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end)
	{
		
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		switch (mExpect)
		{
			case EXPECT_BOARD_TITLE:
			{
				text = StringUtils.clearHtml(text).trim();
				mBoards.add(new Board(mBoardName, text));
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}