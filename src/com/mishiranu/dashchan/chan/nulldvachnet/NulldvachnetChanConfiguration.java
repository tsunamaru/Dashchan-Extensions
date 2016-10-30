package com.mishiranu.dashchan.chan.nulldvachnet;

import chan.content.ChanConfiguration;

public class NulldvachnetChanConfiguration extends ChanConfiguration
{
	public NulldvachnetChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		addCaptchaType("wakaba");
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
		if ("wakaba".equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "Wakaba";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.IN_THREAD;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowEmail = true;
		posting.allowSubject = true;
		if (!"d".equals(boardName))
		{
			posting.attachmentCount = 1;
			posting.attachmentMimeTypes.add("image/*");
			if ("fl".equals(boardName)) posting.attachmentMimeTypes.add("application/x-shockwave-flash");
			posting.attachmentSpoiler = true;
		}
		return posting;
	}

	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		deleting.optionFilesOnly = true;
		return deleting;
	}
}