package com.mishiranu.dashchan.chan.desustorage;

import java.util.ArrayList;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class DesustorageBoardsParser implements GroupParser.Callback {
	private final String source;
	private final ArrayList<Board> boards = new ArrayList<>();

	private String boardCategoryTitle;

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_CATEGORY = 1;
	private static final int EXPECT_BOARD = 2;

	private int expect = EXPECT_NONE;

	public DesustorageBoardsParser(String source) {
		this.source = source;
	}

	public BoardCategory convert() throws ParseException {
		GroupParser.parse(source, this);
		return new BoardCategory("Archives", boards);
	}

	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) {
		if ("h2".equals(tagName)) {
			expect = EXPECT_CATEGORY;
			return true;
		} else if (boardCategoryTitle != null) {
			if ("a".equals(tagName)) {
				expect = EXPECT_BOARD;
				return true;
			}
		}
		return false;
	}

	@Override
	public void onEndElement(GroupParser parser, String tagName) {}

	@Override
	public void onText(GroupParser parser, String source, int start, int end) {}

	@Override
	public void onGroupComplete(GroupParser parser, String text) {
		switch (expect) {
			case EXPECT_CATEGORY: {
				if ("Archives".equals(text)) {
					boardCategoryTitle = StringUtils.clearHtml(text);
				} else {
					boardCategoryTitle = null;
				}
				break;
			}
			case EXPECT_BOARD: {
				text = StringUtils.clearHtml(text).substring(1);
				int index = text.indexOf('/');
				if (index >= 0) {
					String boardName = text.substring(0, index);
					String title = text.substring(index + 2);
					boards.add(new Board(boardName, title));
				}
				break;
			}
		}
		expect = EXPECT_NONE;
	}
}