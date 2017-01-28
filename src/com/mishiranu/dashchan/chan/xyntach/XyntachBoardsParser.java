package com.mishiranu.dashchan.chan.xyntach;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class XyntachBoardsParser {
	private final String source;

	private final ArrayList<Board> boards = new ArrayList<>();
	private boolean subBoards = false;
	private String boardName;

	private static final Pattern PATTERN_BOARD = Pattern.compile("/(\\w+)/$");
	private static final Pattern PATTERN_SUBBOARD = Pattern.compile("/(\\w+)/(\\w+)/$");

	public XyntachBoardsParser(String source) {
		this.source = source;
	}

	public ArrayList<Board> convert(boolean subBoards) throws ParseException {
		this.subBoards = subBoards;
		PARSER.parse(source, this);
		return boards;
	}

	private static final TemplateParser<XyntachBoardsParser> PARSER = new TemplateParser<XyntachBoardsParser>()
			.equals("a", "class", "boardlink").open((instance, holder, tagName, attributes) -> {
		String href = attributes.get("href");
		if (holder.subBoards) {
			Matcher matcher = PATTERN_SUBBOARD.matcher(href);
			if (matcher.find()) {
				holder.boardName = matcher.group(1) + "-" + matcher.group(2);
				return true;
			}
		} else {
			Matcher matcher = PATTERN_BOARD.matcher(href);
			if (matcher.find()) {
				holder.boardName = matcher.group(1);
				return true;
			}
		}
		return false;
	}).content((i, h, t) -> h.boards.add(new Board(h.boardName, StringUtils.clearHtml(t)))).prepare();
}