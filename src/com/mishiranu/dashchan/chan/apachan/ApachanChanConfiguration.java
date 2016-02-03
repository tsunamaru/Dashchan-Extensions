package com.mishiranu.dashchan.chan.apachan;

import chan.content.ChanConfiguration;

public class ApachanChanConfiguration extends ChanConfiguration
{
	public ApachanChanConfiguration()
	{
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowPosting = true;
		board.allowDeleting = true;
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
		return posting;
	}
	
	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		return new Deleting();
	}
}