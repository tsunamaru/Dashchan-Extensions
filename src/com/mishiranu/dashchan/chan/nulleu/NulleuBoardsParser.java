package com.mishiranu.dashchan.chan.nulleu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;

public class NulleuBoardsParser implements GroupParser.Callback {
	private final String source;

	private final ArrayList<BoardCategory> boardCategories = new ArrayList<>();
	private final ArrayList<Board> boards = new ArrayList<>();

	private String boardCategoryTitle;

	public NulleuBoardsParser(String source) {
		this.source = source;
	}

	public ArrayList<BoardCategory> convert() throws ParseException {
		try {
			GroupParser.parse(source, this);
		} catch (FinishedException e) {
			// Ignore exception
		}
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

	private static class FinishedException extends ParseException {
		private static final long serialVersionUID = 1L;
	}

	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws FinishedException {
		if ("div".equals(tagName)) {
			String cssClass = parser.getAttr(attrs, "class");
			if ("menu-sect".equals(cssClass)) {
				closeCategory();
				String id = parser.getAttr(attrs, "id");
				if (id != null && id.startsWith("ms-")) {
					String title = id.substring(3);
					if ("20".equals(title)) {
						return false;
					}
					if ("_options".equals(title)) {
						throw new FinishedException();
					}
					boardCategoryTitle = title.substring(0, 1).toUpperCase(Locale.US) + title.substring(1);
				}
			}
		} else if (boardCategoryTitle != null) {
			if ("a".equals(tagName)) {
				String cssClass = parser.getAttr(attrs, "class");
				if ("menu-item".equals(cssClass)) {
					String title = parser.getAttr(attrs, "title");
					String href = parser.getAttr(attrs, "href");
					String boardName = href.substring(1, href.length() - 1);
					boards.add(new Board(boardName, title));
				}
			}
		}
		return false;
	}

	@Override
	public void onEndElement(GroupParser parser, String tagName) {}

	@Override
	public void onText(GroupParser parser, String source, int start, int end) {}

	@Override
	public void onGroupComplete(GroupParser parser, String text) {}
}