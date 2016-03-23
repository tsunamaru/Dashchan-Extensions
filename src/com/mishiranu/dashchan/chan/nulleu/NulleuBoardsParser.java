package com.mishiranu.dashchan.chan.nulleu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;

public class NulleuBoardsParser implements GroupParser.Callback
{
	private final String mSource;
	
	private final ArrayList<BoardCategory> mBoardCategories = new ArrayList<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();

	private String mBoardCategoryTitle;
	
	public NulleuBoardsParser(String source)
	{
		mSource = source;
	}
	
	public ArrayList<BoardCategory> convert() throws ParseException
	{
		try
		{
			GroupParser.parse(mSource, this);
		}
		catch (FinishedException e)
		{
			
		}
		closeCategory();
		for (BoardCategory boardCategory : mBoardCategories) Arrays.sort(boardCategory.getBoards());
		return mBoardCategories;
	}
	
	private void closeCategory()
	{
		if (mBoardCategoryTitle != null)
		{
			if (mBoards.size() > 0) mBoardCategories.add(new BoardCategory(mBoardCategoryTitle, mBoards));
			mBoardCategoryTitle = null;
			mBoards.clear();
		}
	}
	
	private static class FinishedException extends ParseException
	{
		private static final long serialVersionUID = 1L;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws FinishedException
	{
		if ("div".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("menu-sect".equals(cssClass))
			{
				closeCategory();
				String id = parser.getAttr(attrs, "id");
				if (id != null && id.startsWith("ms-"))
				{
					String title = id.substring(3);
					if ("20".equals(title)) return false;
					if ("_options".equals(title)) throw new FinishedException();
					mBoardCategoryTitle = title.substring(0, 1).toUpperCase(Locale.US) + title.substring(1);
				}
			}
		}
		else if (mBoardCategoryTitle != null)
		{
			if ("a".equals(tagName))
			{
				String cssClass = parser.getAttr(attrs, "class");
				if ("menu-item".equals(cssClass))
				{
					String title = parser.getAttr(attrs, "title");
					String href = parser.getAttr(attrs, "href");
					String boardName = href.substring(1, href.length() - 1);
					mBoards.add(new Board(boardName, title));
				}
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end)
	{
		
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		
	}
}