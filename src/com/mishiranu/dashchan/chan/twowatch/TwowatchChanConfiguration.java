package com.mishiranu.dashchan.chan.twowatch;

import chan.content.ChanConfiguration;

public class TwowatchChanConfiguration extends ChanConfiguration
{
	private static final String KEY_EMAILS_ENABLED = "emails_enabled";
	private static final String KEY_SAGE_ENABLED = "sage_enabled";
	
	public static final String CAPTCHA_TYPE_INCH_NUMERIC = "inch_numeric";
	
	public TwowatchChanConfiguration()
	{
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		setDefaultName("a", "Cirno");
		setDefaultName("bo", "Читатель");
		setDefaultName("sci", "Ученый");
		setDefaultName("rm", "Куклоёб");
		addCaptchaType(CAPTCHA_TYPE_INCH_NUMERIC);
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		board.allowReporting = true;
		return board;
	}
	
	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType)
	{
		if (captchaType.startsWith("inch_"))
		{
			Captcha captcha = new Captcha();
			if (CAPTCHA_TYPE_INCH_NUMERIC.equals(captchaType))
			{
				captcha.title = "Numeric";
				captcha.input = Captcha.Input.NUMERIC;
			}
			captcha.validity = Captcha.Validity.LONG_LIFETIME;
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
		posting.allowEmail = get(boardName, KEY_EMAILS_ENABLED, true);
		posting.allowSubject = true;
		posting.optionSage = get(boardName, KEY_SAGE_ENABLED, true);
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/webm");
		posting.attachmentMimeTypes.add("audio/*");
		if ("f".equals(boardName)) posting.attachmentMimeTypes.add("application/x-shockwave-flash");
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
	
	@Override
	public Reporting obtainReportingConfiguration(String boardName)
	{
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.multiplePosts = true;
		return reporting;
	}
	
	public void storeEmailsSageEnabled(String boardName, boolean emailsEnabled, boolean sageEnabled)
	{
		set(boardName, KEY_EMAILS_ENABLED, emailsEnabled);
		set(boardName, KEY_SAGE_ENABLED, sageEnabled);
	}
}