package com.mishiranu.dashchan.chan.nullchan;

import chan.content.ChanConfiguration;

public class NullchanChanConfiguration extends ChanConfiguration {
	public NullchanChanConfiguration() {
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		request(OPTION_READ_USER_BOARDS);
		setDefaultName("Аноним");
		addCaptchaType("nullchan");
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowPosting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if ("nullchan".equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "Nullchan";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.attachmentCount = 8;
		posting.attachmentMimeTypes.add("image/*");
		return posting;
	}
}
