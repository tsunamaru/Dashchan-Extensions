package com.mishiranu.dashchan.chan.britfags;

import chan.content.ChanConfiguration;

public class BritfagsChanConfiguration extends ChanConfiguration
{
	public BritfagsChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Anonymous");
	}

	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		board.allowReporting = true;
		return board;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		if ("b".equals(boardName) || "lit".equals(boardName)) posting.attachmentMimeTypes.add("text/plain");
		if ("zoo".equals(boardName)) posting.attachmentMimeTypes.add("audio/mp3");
		return posting;
	}

	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.optionFilesOnly = true;
		return deleting;
	}

	@Override
	public Reporting obtainReportingConfiguration(String boardName)
	{
		Reporting reporting = new Reporting();
		reporting.comment = true;
		return reporting;
	}
}