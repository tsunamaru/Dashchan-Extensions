package com.mishiranu.dashchan.chan.cablesix;

import android.util.Pair;

import chan.content.ChanConfiguration;

public class CableSixChanConfiguration extends ChanConfiguration
{
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_IMAGE_SPOILERS_ENABLED = "image_spoilers_enabled";
	private static final String KEY_IMAGE_NSFW_ENABLED = "image_nsfw_enabled";
	
	public CableSixChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Anonyme");
		addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_1);
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowPosting = true;
		board.allowDeleting = true;
		board.allowReporting = true;
		return board;
	}
	
	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowName = get(boardName, KEY_NAMES_ENABLED, false);
		posting.allowTripcode = true;
		posting.allowEmail = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentSpoiler = get(boardName, KEY_IMAGE_SPOILERS_ENABLED, false);
		if (get(boardName, KEY_IMAGE_NSFW_ENABLED, false))
		{
			posting.attachmentRatings.add(new Pair<>("", "SFW"));
			posting.attachmentRatings.add(new Pair<>("nsfw", "NSFW"));
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
	
	@Override
	public Reporting obtainReportingConfiguration(String boardName)
	{
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.multiplePosts = true;
		return reporting;
	}
	
	public void storeNamesSpoilersNsfwEnabled(String boardName, boolean namesEnabled, boolean imageSpoilersEnabled,
			boolean imageNsfwEnabled)
	{
		set(boardName, KEY_NAMES_ENABLED, namesEnabled);
		set(boardName, KEY_IMAGE_SPOILERS_ENABLED, imageSpoilersEnabled);
		set(boardName, KEY_IMAGE_NSFW_ENABLED, imageNsfwEnabled);
	}
}