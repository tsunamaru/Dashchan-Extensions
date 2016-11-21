package com.mishiranu.dashchan.chan.gurochan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class GurochanBoardsParser {
	private final String source;

	private final ArrayList<BoardCategory> boardCategories = new ArrayList<>();
	private final ArrayList<Board> boards = new ArrayList<>();

	private String boardCategoryTitle;

	private static final Pattern BOARD_PATTERN = Pattern.compile("/(\\w+)/ — (.*)");

	public GurochanBoardsParser(String source) {
		this.source = source;
	}

	public ArrayList<BoardCategory> convert() throws ParseException {
		PARSER.parse(source, this);
		closeCategory();
		return boardCategories;
	}

	private void closeCategory() {
		if (boardCategoryTitle != null) {
			if (boards.size() > 0) {
				boardCategories.add(new BoardCategory(boardCategoryTitle, boards));
			}
			boardCategoryTitle = null;
			boards.clear();
		}
	}

	private static final TemplateParser<GurochanBoardsParser> PARSER = new TemplateParser<GurochanBoardsParser>()
			.name("legend").content((instance, holder, text) -> {
		holder.closeCategory();
		holder.boardCategoryTitle = StringUtils.clearHtml(text);
	}).name("a").open((i, h, t, a) -> h.boardCategoryTitle != null).content((instance, holder, text) -> {
		Matcher matcher = BOARD_PATTERN.matcher(StringUtils.clearHtml(text));
		if (matcher.matches()) {
			holder.boards.add(new Board(matcher.group(1), matcher.group(2)));
		}
	}).prepare();
}