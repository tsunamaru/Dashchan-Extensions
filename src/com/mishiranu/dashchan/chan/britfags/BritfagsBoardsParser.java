package com.mishiranu.dashchan.chan.britfags;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class BritfagsBoardsParser
{
	private final String mSource;

	private final ArrayList<BoardCategory> mBoardCategories = new ArrayList<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();

	private String mBoardCategoryTitle;
	private String mBoardName;

	private static final Pattern BOARD_NAME_PATTERN = Pattern.compile("/(\\w+)/");

	public BritfagsBoardsParser(String source)
	{
		mSource = source;
	}

	public ArrayList<BoardCategory> convert() throws ParseException
	{
		PARSER.parse(mSource, this);
		closeCategory();
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

	private static final TemplateParser<BritfagsBoardsParser> PARSER = new TemplateParser<BritfagsBoardsParser>()
			.name("h2").content((instance, holder, text) ->
	{
		holder.closeCategory();
		holder.mBoardCategoryTitle = StringUtils.clearHtml(text);

	}).name("a").open((instance, holder, tagName, attributes) ->
	{
		String href = attributes.get("href");
		if (href != null)
		{
			Matcher matcher = BOARD_NAME_PATTERN.matcher(href);
			if (matcher.matches())
			{
				holder.mBoardName = matcher.group(1);
				return true;
			}
		}
		return false;

	}).content((i, h, t) -> h.mBoards.add(new Board(h.mBoardName, StringUtils.clearHtml(t)))).prepare();
}