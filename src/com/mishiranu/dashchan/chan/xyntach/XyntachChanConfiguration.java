package com.mishiranu.dashchan.chan.xyntach;

import chan.content.ChanConfiguration;

public class XyntachChanConfiguration extends ChanConfiguration {
	public XyntachChanConfiguration() {
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		addCaptchaType("xyntach");
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		board.allowReporting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if ("xyntach".equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "Xyntach";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.IN_BOARD;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowSubject = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("audio/mp3");
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

	@Override
	public Reporting obtainReportingConfiguration(String boardName) {
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.multiplePosts = true;
		return reporting;
	}
}