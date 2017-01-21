package com.mishiranu.dashchan.chan.nullone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;

public class NulloneBoardsParser {
	private final String source;

	private final ArrayList<BoardCategory> boardCategories = new ArrayList<>();
	private final ArrayList<Board> boards = new ArrayList<>();

	private String boardCategoryTitle;

	public NulloneBoardsParser(String source) {
		this.source = source;
	}

	public ArrayList<BoardCategory> convert() throws ParseException {
		PARSER.parse(source, this);
		closeCategory();
		for (BoardCategory boardCategory : boardCategories) {
			Arrays.sort(boardCategory.getBoards());
		}
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

	private static final TemplateParser<NulloneBoardsParser> PARSER = new TemplateParser<NulloneBoardsParser>()
			.equals("div", "class", "menu-sect").open((instance, holder, tagName, attributes) -> {
		holder.closeCategory();
		String id = attributes.get("id");
		if (id != null && id.startsWith("ms-")) {
			String title = id.substring(3);
			if (!"20".equals(title)) {
				if ("_options".equals(title)) {
					instance.finish();
				} else {
					holder.boardCategoryTitle = title.substring(0, 1).toUpperCase(Locale.US) + title.substring(1);
				}
			}
		}
		return false;
	}).equals("a", "class", "menu-item").open((instance, holder, tagName, attributes) -> {
		if (holder.boardCategoryTitle != null) {
			String title = attributes.get("title");
			String href = attributes.get("href");
			String boardName = href.substring(1, href.length() - 1);
			holder.boards.add(new Board(boardName, title));
		}
		return false;
	}).prepare();
}