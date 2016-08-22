package com.mishiranu.dashchan.chan.ozuchan;

import chan.content.ChanConfiguration;

public class OzuchanChanConfiguration extends ChanConfiguration
{
	public OzuchanChanConfiguration()
	{
		request(OPTION_SINGLE_BOARD_MODE);
		request(OPTION_READ_POSTS_COUNT);
		setSingleBoardName("ba");
		setDefaultName("Аноним");
		addCaptchaType("ozuchan");
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
	public Captcha obtainCustomCaptchaConfiguration(String captchaType)
	{
		if ("ozuchan".equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "ozuchan";
			captcha.input = Captcha.Input.NUMERIC;
			captcha.validity = Captcha.Validity.IN_BOARD;
			return captcha;
		}
		return null;
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
		posting.attachmentMimeTypes.add("video/webm");
		return posting;
	}
	
	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		return deleting;
	}
}