package com.mishiranu.dashchan.chan.nullnyan;

import chan.content.ChanConfiguration;

public class NullnyanChanConfiguration extends ChanConfiguration
{
	public NullnyanChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Anonymous");
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
		posting.allowName = true;
		posting.allowTripcode = true;
		posting.allowEmail = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("video/webm");
		return posting;
	}
	
	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		Deleting deleting = new Deleting();
		deleting.password = true;
		return deleting;
	}
}