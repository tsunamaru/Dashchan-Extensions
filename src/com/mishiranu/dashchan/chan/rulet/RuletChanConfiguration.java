package com.mishiranu.dashchan.chan.rulet;

import chan.content.ChanConfiguration;

public class RuletChanConfiguration extends ChanConfiguration
{
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_EMAILS_ENABLED = "emails_enabled";
	private static final String KEY_IMAGES_ENABLED = "images_enabled";
	private static final String KEY_SPOILERS_ENABLED = "spoilers_enabled";

	public static final String CAPTCHA_TYPE_LATIN = "rulet_latin";
	public static final String CAPTCHA_TYPE_CYRILLIC = "rulet_cyrillic";
	
	public RuletChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		setDefaultName("int", "Boris");
		addCaptchaType(CAPTCHA_TYPE_LATIN);
		addCaptchaType(CAPTCHA_TYPE_CYRILLIC);
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
		if (captchaType.startsWith("rulet_"))
		{
			Captcha captcha = new Captcha();
			switch (captchaType)
			{
				case CAPTCHA_TYPE_LATIN:
				{
					captcha.title = "Latin";
					captcha.input = Captcha.Input.LATIN;
					break;
				}
				case CAPTCHA_TYPE_CYRILLIC:
				{
					captcha.title = "Cyrillic";
					captcha.input = Captcha.Input.ALL;
					break;
				}
			}
			captcha.validity = Captcha.Validity.IN_BOARD;
			return captcha;
		}
		return null;
	}
	
	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowName = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowTripcode = true;
		posting.allowEmail = get(boardName, KEY_EMAILS_ENABLED, true);
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = get(boardName, KEY_IMAGES_ENABLED, true) ? 1 : 0;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("video/webm");
		if ("f".equals(boardName)) posting.attachmentMimeTypes.add("application/x-shockwave-flash");
		posting.attachmentSpoiler = get(boardName, KEY_SPOILERS_ENABLED, false);
		posting.hasCountryFlags = "int".equals(boardName);
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
	
	public void storeNamesEmailsImagesSpoilersEnabled(String boardName, boolean namesEnabled, boolean emailsEnabled,
			boolean imagesEnabled, boolean spoilersEnabled)
	{
		set(boardName, KEY_NAMES_ENABLED, namesEnabled);
	}
}