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
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class CirnoBoardsParser
{
	private final String mSource;
	
	private final LinkedHashMap<String, BoardCategory> mBoardCategories = new LinkedHashMap<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();
	
	private String mBoardCategoryTitle;
	private String mBoardName;

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
		PARSER.parse(mSource, this);
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
	
	private static String transform(String string)
	{
		if (string.length() > 0)
		{
			string = string.toLowerCase(Locale.getDefault());
			string = string.substring(0, 1).toUpperCase(Locale.getDefault()) + string.substring(1);
		}
		return string;
	}
	
	private static final TemplateParser<CirnoBoardsParser> PARSER = new TemplateParser<CirnoBoardsParser>()
			.equals("td", "class", "header").content((instance, holder, text) ->
	{
		holder.closeCategory();
		holder.mBoardCategoryTitle = StringUtils.clearHtml(text);
		
	}).name("a").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mBoardCategoryTitle != null)
		{
			String href = attributes.get("href");
			Matcher matcher = BOARD_NAME_PATTERN.matcher(href);
			if (matcher.matches())
			{
				String boardName = matcher.group(1);
				String title = VALID_BOARD_TITLES.get(boardName);
				holder.mBoardName = boardName;
				if (title != null) holder.mBoards.add(new Board(boardName, title)); else return true;
			}
		}
		return false;
		
	}).content((instance, holder, text) ->
	{
		text = transform(StringUtils.clearHtml(text));
		holder.mBoards.add(new Board(holder.mBoardName, text));
		
	}).prepare();
}