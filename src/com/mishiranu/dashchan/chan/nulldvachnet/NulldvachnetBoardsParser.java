package com.mishiranu.dashchan.chan.nulldvachnet;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class NulldvachnetBoardsParser
{
	private final String mSource;

	private final ArrayList<Board> mBoards = new ArrayList<>();

	private String mBoardName;

	private static final Pattern PATTERN_BOARD = Pattern.compile("/(\\w+)/");

	public NulldvachnetBoardsParser(String source)
	{
		mSource = source;
	}

	public BoardCategory convert() throws ParseException
	{
		PARSER.parse(mSource, this);
		return new BoardCategory(null, mBoards);
	}

	private static final TemplateParser<NulldvachnetBoardsParser> PARSER =
			new TemplateParser<NulldvachnetBoardsParser>().name("a").open((instance, holder, tagName, attributes) ->
	{
		String href = attributes.get("href");
		if (href != null)
		{
			Matcher matcher = PATTERN_BOARD.matcher(href);
			if (matcher.matches())
			{
				holder.mBoardName = matcher.group(1);
				return true;
			}
		}
		return false;

	}).content((i, h, t) -> h.mBoards.add(new Board(h.mBoardName, StringUtils.clearHtml(t)))).prepare();
}