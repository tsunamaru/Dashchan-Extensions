package com.mishiranu.dashchan.chan.candydollchan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class CandydollchanBoardsParser {
	private final String source;

	private final ArrayList<Board> boards = new ArrayList<>();
	private String boardName;

	private boolean listHandling = false;

	private static final Pattern BOARD_URI = Pattern.compile("/(\\w+)/?");

	public CandydollchanBoardsParser(String source) {
		this.source = source;
	}

	public BoardCategory convert() throws ParseException {
		PARSER.parse(source, this);
		return new BoardCategory(null, boards);
	}

	private static final TemplateParser<CandydollchanBoardsParser> PARSER =
			new TemplateParser<CandydollchanBoardsParser>()
			.name("strong").open((instance, holder, tagName, attributes) -> !(holder.listHandling = true))
			.name("a").open((instance, holder, tagName, attributes) -> {
		if (holder.listHandling) {
			String href = attributes.get("href");
			Matcher matcher = BOARD_URI.matcher(href);
			if (matcher.matches()) {
				holder.boardName = matcher.group(1);
				return true;
			}
		}
		return false;
	}).content((instance, holder, text) -> {
		text = StringUtils.clearHtml(text).trim();
		holder.boards.add(new Board(holder.boardName, text));
	}).name("strong").close((instance, holder, tagName) -> {
		if (holder.listHandling) {
			instance.finish();
		}
	}).prepare();
}