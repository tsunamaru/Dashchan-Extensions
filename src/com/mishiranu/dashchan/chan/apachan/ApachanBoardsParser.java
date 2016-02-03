package com.mishiranu.dashchan.chan.apachan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class ApachanBoardsParser implements GroupParser.Callback
{
	private final String mSource;
	
	private final ArrayList<BoardBuilder> mBoards = new ArrayList<>();
	private final ArrayList<BoardBuilder> mWaitingBoards = new ArrayList<>();
	
	private static final class BoardBuilder
	{
		public String boardName;
		public String title;
		public String description;
	}

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_TITLE = 1;
	private static final int EXPECT_DESCRIPTION = 2;
	
	private int mExpect = EXPECT_NONE;
	
	private static final Pattern BOARD_URI = Pattern.compile("/(\\w+)\\.html$");
	
	public ApachanBoardsParser(String source)
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
		ArrayList<Board> boards = new ArrayList<>();
		for (BoardBuilder board : mBoards)
		{
			if (board.boardName != null && board.title != null)
			{
				boards.add(new Board(board.boardName, board.title, board.description));
			}
		}
		return boards.size() > 0 ? new BoardCategory("Доски", boards) : null;
	}
	
	private static class FinishedException extends ParseException
	{
		private static final long serialVersionUID = 1L;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws FinishedException
	{
		if ("td".equals(tagName))
		{
			if ("49".equals(parser.getAttr(attrs, "height")))
			{
				mExpect = EXPECT_TITLE;
			}
			else if (mWaitingBoards.size() > 0)
			{
				mExpect = EXPECT_DESCRIPTION;
				return true;
			}
		}
		else if ("h3".equals(tagName) && mBoards.size() > 0)
		{
			mExpect = EXPECT_TITLE;
		}
		else if ("a".equals(tagName))
		{
			if (mExpect == EXPECT_TITLE)
			{
				String href = parser.getAttr(attrs, "href");
				Matcher matcher = BOARD_URI.matcher(href);
				if (matcher.find())
				{
					String boardName = matcher.group(1);
					BoardBuilder board = new BoardBuilder();
					board.boardName = boardName;
					mBoards.add(board);
					mWaitingBoards.add(board);
					return true;
				}
				else mExpect = EXPECT_NONE;
			}
		}
		else if ("hr".equals(tagName) && mBoards.size() > 0)
		{
			throw new FinishedException();
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		if ("tr".equals(tagName))
		{
			mWaitingBoards.clear();
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
			case EXPECT_TITLE:
			{
				String title = StringUtils.clearHtml(text).trim();
				BoardBuilder board = mWaitingBoards.get(mWaitingBoards.size() - 1);
				board.title = title;
				break;
			}
			case EXPECT_DESCRIPTION:
			{
				String description = StringUtils.clearHtml(text).trim();
				int index = description.indexOf('\n');
				if (index >= 0) description = description.substring(0, index);
				for (BoardBuilder board : mWaitingBoards) board.description = description;
				mWaitingBoards.clear();
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}