package com.mishiranu.dashchan.chan.bonzibuddy;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class BonziBuddyBoardsParser {
	private final String source;

	private final ArrayList<Board> boards = new ArrayList<>();

	private boolean boardListParsing = false;

	private static final Pattern PATTERN_BOARD_URI = Pattern.compile("/(.*?)/index.html");

	public BonziBuddyBoardsParser(String source) {
		this.source = source;
	}

	public BoardCategory convert() throws ParseException {
		PARSER.parse(source, this);
		return new BoardCategory(null, boards);
	}

	private static final TemplateParser<BonziBuddyBoardsParser> PARSER = new TemplateParser<BonziBuddyBoardsParser>()
			.equals("div", "class", "boardlist").open((instance, holder, tagName, attributes) -> {
		holder.boardListParsing = true;
		return false;
	}).name("div").close((instance, holder, tagName) -> {
		if (holder.boardListParsing) {
			instance.finish();
		}
	}).ends("a", "href", "/index.html").open((instance, holder, tagName, attributes) -> {
		Matcher matcher = PATTERN_BOARD_URI.matcher(attributes.get("href"));
		if (matcher.matches()) {
			holder.boards.add(new Board(matcher.group(1), StringUtils.clearHtml(attributes.get("title"))));
		}
		return false;
	}).prepare();
}