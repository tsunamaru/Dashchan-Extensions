package com.mishiranu.dashchan.chan.twowatch;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class TwowatchBoardsParser implements GroupParser.Callback
{
	private final String mSource;
	
	private final ArrayList<BoardCategory> mBoardCategories = new ArrayList<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();
	
	private String mBoardCategoryTitle;
	private String mBoardName;

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_BOARD_CATEGORY = 1;
	private static final int EXPECT_BOARD_TITLE = 2;
	
	private int mExpect = EXPECT_NONE;
	
	private static final Pattern BOARD_URI = Pattern.compile("/(\\w+)/$");
	
	public TwowatchBoardsParser(String source)
	{
		mSource = source;
	}
	
	public ArrayList<BoardCategory> convert() throws ParseException
	{
		GroupParser.parse(mSource, this);
		closeCategory();
		return mBoardCategories;
	}
	
	private void closeCategory()
	{
		if (mBoards.size() > 0)
		{
			mBoardCategories.add(new BoardCategory(mBoardCategoryTitle, mBoards));
			mBoards.clear();
		}
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("h2".equals(tagName))
		{
			closeCategory();
			mExpect = EXPECT_BOARD_CATEGORY;
			return true;
		}
		else if ("a".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("boardlink".equals(cssClass))
			{
				String href = parser.getAttr(attrs, "href");
				Matcher matcher = BOARD_URI.matcher(href);
				if (matcher.find())
				{
					mBoardName = matcher.group(1);
					mExpect = EXPECT_BOARD_TITLE;
					return true;
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
		switch (mExpect)
		{
			case EXPECT_BOARD_CATEGORY:
			{
				String title = StringUtils.clearHtml(text).trim().substring(3);
				mBoardCategoryTitle = title.substring(0, 1).toUpperCase(Locale.getDefault()) + title.substring(1);
				break;
			}
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