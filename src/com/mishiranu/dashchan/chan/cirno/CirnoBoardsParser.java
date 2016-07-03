package com.mishiranu.dashchan.chan.cirno;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class CirnoBoardsParser implements GroupParser.Callback
{
	private final String mSource;
	
	private final LinkedHashMap<String, BoardCategory> mBoardCategories = new LinkedHashMap<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();
	
	private String mBoardCategoryTitle;
	private String mBoardName;

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_CATEGORY = 1;
	private static final int EXPECT_BOARD = 2;
	
	private int mExpect = EXPECT_NONE;
	
	private static final Pattern BOARD_NAME_PATTERN = Pattern.compile("/(\\w+)/");
	
	private static final HashMap<String, String> VALID_BOARD_TITLES = new HashMap<>();
	
	static
	{
		VALID_BOARD_TITLES.put("tv", "Кино и ТВ");
		VALID_BOARD_TITLES.put("bro", "My Little Pony");
		VALID_BOARD_TITLES.put("m", "Картинки-макросы и копипаста");
		VALID_BOARD_TITLES.put("s", "Электроника и ПО");
		VALID_BOARD_TITLES.put("azu", "Azumanga Daioh");
		VALID_BOARD_TITLES.put("ls", "Lucky\u2606Star");
		VALID_BOARD_TITLES.put("rm", "Rozen Maiden");
		VALID_BOARD_TITLES.put("sos", "Suzumiya Haruhi no Y\u016butsu");
		VALID_BOARD_TITLES.put("hau", "Higurashi no Naku Koro ni");
	}
	
	public CirnoBoardsParser(String source)
	{
		mSource = source;
	}
	
	public ArrayList<BoardCategory> convert() throws ParseException
	{
		GroupParser.parse(mSource, this);
		closeCategory();
		for (BoardCategory boardCategory : mBoardCategories.values()) Arrays.sort(boardCategory.getBoards());
		BoardCategory boardCategory = mBoardCategories.remove("Обсуждения");
		if (boardCategory != null) mBoardCategories.put(boardCategory.getTitle(), boardCategory);
		return new ArrayList<>(mBoardCategories.values());
	}
	
	private void closeCategory()
	{
		if (mBoardCategoryTitle != null)
		{
			if (mBoards.size() > 0)
			{
				mBoardCategories.put(mBoardCategoryTitle, new BoardCategory(mBoardCategoryTitle, mBoards));
			}
			mBoardCategoryTitle = null;
			mBoards.clear();
		}
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("td".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("header".equals(cssClass))
			{
				closeCategory();
				mExpect = EXPECT_CATEGORY;
				return true;
			}
		}
		else if (mBoardCategoryTitle != null)
		{
			if ("a".equals(tagName))
			{
				String href = parser.getAttr(attrs, "href");
				Matcher matcher = BOARD_NAME_PATTERN.matcher(href);
				if (matcher.matches())
				{
					mBoardName = matcher.group(1);
					String title = VALID_BOARD_TITLES.get(mBoardName);
					if (title != null) mBoards.add(new Board(mBoardName, title)); else
					{
						mExpect = EXPECT_BOARD;
						return true;
					}
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
			case EXPECT_CATEGORY:
			{
				mBoardCategoryTitle = StringUtils.clearHtml(text);
				break;
			}
			case EXPECT_BOARD:
			{
				text = transform(StringUtils.clearHtml(text));
				mBoards.add(new Board(mBoardName, text));
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
	
	private static String transform(String string)
	{
		if (string.length() > 0)
		{
			string = string.toLowerCase(Locale.getDefault());
			string = string.substring(0, 1).toUpperCase(Locale.getDefault()) + string.substring(1);
		}
		return string;
	}
}