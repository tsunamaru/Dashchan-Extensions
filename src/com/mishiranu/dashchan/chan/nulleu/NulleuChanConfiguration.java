package com.mishiranu.dashchan.chan.nulleu;

import chan.content.ChanConfiguration;

public class NulleuChanConfiguration extends ChanConfiguration {
	private static final String KEY_NAMES_ENABLED = "names_enabled";

	public static final String CAPTCHA_TYPE_INCH_CYRILLIC = "inch_cyrillic";

	public NulleuChanConfiguration() {
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		request(OPTION_READ_USER_BOARDS);
		setDefaultName("Аноним");
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
			if (CAPTCHA_TYPE_INCH_CYRILLIC.equals(captchaType)) {
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
		posting.allowName = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowTripcode = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/webm");
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

	public void storeNamesEnabled(String boardName, boolean namesEnabled) {
		set(boardName, KEY_NAMES_ENABLED, namesEnabled);
	}
}