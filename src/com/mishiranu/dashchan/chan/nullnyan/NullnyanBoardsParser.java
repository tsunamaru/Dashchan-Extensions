package com.mishiranu.dashchan.chan.nullnyan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class NullnyanBoardsParser {
	private final String source;

	private final ArrayList<Board> boards = new ArrayList<>();

	private String boardName;

	private static final Pattern PATTERN_BOARD = Pattern.compile("/(\\w+)/");

	public NullnyanBoardsParser(String source) {
		this.source = source;
	}

	public BoardCategory convert() throws ParseException {
		PARSER.parse(source, this);
		return new BoardCategory(null, boards);
	}

	private static final TemplateParser<NullnyanBoardsParser> PARSER = new TemplateParser<NullnyanBoardsParser>()
			.name("a").open((instance, holder, tagName, attributes) -> {
		String href = attributes.get("href");
		Matcher matcher = PATTERN_BOARD.matcher(href);
		if (matcher.matches()) {
			holder.boardName = matcher.group(1);
			return true;
		}
		return false;
	}).content((instance, holder, text) -> {
		holder.boards.add(new Board(holder.boardName, StringUtils.clearHtml(text).trim()));
	}).name("nav").close((instance, holder, tagName) -> instance.finish()).prepare();
}
