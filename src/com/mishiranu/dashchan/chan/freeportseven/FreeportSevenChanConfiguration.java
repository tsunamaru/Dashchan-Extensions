package com.mishiranu.dashchan.chan.freeportseven;

import chan.content.ChanConfiguration;

public class FreeportSevenChanConfiguration extends ChanConfiguration {
	public FreeportSevenChanConfiguration() {
		request(OPTION_SINGLE_BOARD_MODE);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		addCaptchaType("freeportseven");
		setSingleBoardName("b");
		setBoardTitle("b", "Freeport 7");
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowPosting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if ("freeportseven".equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "freeport7.org";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowSubject = boardName == null || newThread;
		posting.optionSage = boardName == null || !newThread;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		return posting;
	}
}