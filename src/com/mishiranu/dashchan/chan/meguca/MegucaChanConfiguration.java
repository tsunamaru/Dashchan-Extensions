package com.mishiranu.dashchan.chan.meguca;

import chan.content.ChanConfiguration;

public class MegucaChanConfiguration extends ChanConfiguration
{
	public MegucaChanConfiguration()
	{
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Anonymous");
		addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_1);
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowReporting = true;
		return board;
	}
	
	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowName = true;
		posting.allowTripcode = true;
		posting.allowEmail = true;
		posting.allowSubject = newThread;
		posting.optionSage = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/webm");
		posting.attachmentMimeTypes.add("audio/mp3");
		posting.attachmentMimeTypes.add("application/pdf");
		return posting;
	}
	
	@Override
	public Reporting obtainReportingConfiguration(String boardName)
	{
		return new Reporting();
	}
}