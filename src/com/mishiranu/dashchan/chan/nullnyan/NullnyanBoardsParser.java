package com.mishiranu.dashchan.chan.nullnyan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class NullnyanBoardsParser
{
	private final String mSource;

	private final ArrayList<Board> mBoards = new ArrayList<>();

	private static final Pattern PATTERN_BOARD = Pattern.compile("/(\\w+)/");

	public NullnyanBoardsParser(String source)
	{
		mSource = source;
	}

	public BoardCategory convert() throws ParseException
	{
		PARSER.parse(mSource, this);
		return new BoardCategory(null, mBoards);
	}

	private static final TemplateParser<NullnyanBoardsParser> PARSER = new TemplateParser<NullnyanBoardsParser>()
			.contains("a", "title", "").open((instance, holder, tagName, attributes) ->
	{
		String href = attributes.get("href");
		Matcher matcher = PATTERN_BOARD.matcher(href);
		if (matcher.matches())
		{
			String title = StringUtils.clearHtml(attributes.get("title")).trim();
			holder.mBoards.add(new Board(matcher.group(1), title));
		}
		return false;

	}).name("nav").close((instance, holder, tagName) -> instance.finish()).prepare();
}