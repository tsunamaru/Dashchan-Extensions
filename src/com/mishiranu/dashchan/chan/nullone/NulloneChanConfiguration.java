package com.mishiranu.dashchan.chan.nullone;

import chan.content.ChanConfiguration;

public class NulloneChanConfiguration extends ChanConfiguration {
	public static final String CAPTCHA_TYPE_INCH_NUMERIC = "inch_numeric";
	public static final String CAPTCHA_TYPE_INCH_LATIN = "inch_latin";
	public static final String CAPTCHA_TYPE_INCH_CYRILLIC = "inch_cyrillic";

	public NulloneChanConfiguration() {
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		addCaptchaType(CAPTCHA_TYPE_INCH_NUMERIC);
		addCaptchaType(CAPTCHA_TYPE_INCH_LATIN);
		addCaptchaType(CAPTCHA_TYPE_INCH_CYRILLIC);
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if (captchaType.startsWith("inch_")) {
			Captcha captcha = new Captcha();
			if (CAPTCHA_TYPE_INCH_NUMERIC.equals(captchaType)) {
				captcha.title = "Numeric";
				captcha.input = Captcha.Input.NUMERIC;
			} else if (CAPTCHA_TYPE_INCH_LATIN.equals(captchaType)) {
				captcha.title = "Latin";
				captcha.input = Captcha.Input.LATIN;
			} else if (CAPTCHA_TYPE_INCH_CYRILLIC.equals(captchaType)) {
				captcha.title = "Cyrillic";
				captcha.input = Captcha.Input.ALL;
			}
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowName = true;
		posting.allowTripcode = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		return posting;
	}

	@Override
	public Deleting obtainDeletingConfiguration(String boardName) {
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		deleting.optionFilesOnly = true;
		return deleting;
	}
}
