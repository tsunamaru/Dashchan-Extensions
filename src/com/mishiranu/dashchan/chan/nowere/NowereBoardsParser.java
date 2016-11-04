package com.mishiranu.dashchan.chan.nowere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class NowereBoardsParser
{
	private final String mSource;

	private final ArrayList<BoardCategory> mBoardCategories = new ArrayList<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();

	private String mBoardCategoryTitle;
	private String mBoardName;

	private static final Pattern BOARD_NAME_PATTERN = Pattern.compile("/(\\w+)/");

	public NowereBoardsParser(String source)
	{
		mSource = source;
	}

	public ArrayList<BoardCategory> convert() throws ParseException
	{
		PARSER.parse(mSource, this);
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

	private static final TemplateParser<NowereBoardsParser> PARSER = new TemplateParser<NowereBoardsParser>()
			.equals("div", "class", "reply").content((instance, holder, text) ->
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
				holder.mBoardName = matcher.group(1);
				return true;
			}
		}
		return false;

	}).content((instance, holder, text) -> holder.mBoards.add(new Board(holder.mBoardName, text))).prepare();
}