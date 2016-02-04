package com.mishiranu.dashchan.chan.dobrochan;

import org.json.JSONObject;

import android.content.res.Resources;
import android.util.Pair;

import chan.content.ChanConfiguration;

public class DobrochanChanConfiguration extends ChanConfiguration
{
	private static final String KEY_ALWAYS_LOAD_CAPTCHA = "always_load_captcha";
	
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_TRIPCODES_ENABLED = "tripcodes_enabled";
	
	private static final String KEY_ATTACHMENTS_COUNT = "attachments_count";
	
	private static final int DEFAULT_ATTACHMENTS_COUNT = 5;
	
	public DobrochanChanConfiguration()
	{
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Анонимус");
		setDefaultName("s", "Доброкодер");
		setDefaultName("sw", "Лоуренс");
		setDefaultName("wn", "Анонимный эксперт");
		setDefaultName("slow", "Добропок");
		setDefaultName("mad", "Экспериментатор");
		addCaptchaType("dobrochan");
		addCustomPreference(KEY_ALWAYS_LOAD_CAPTCHA, false);
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
		if ("dobrochan".equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "Dobrochan";
			captcha.input = Captcha.Input.ALL;
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
		posting.allowTripcode = get(boardName, KEY_TRIPCODES_ENABLED, true);
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = get(boardName, KEY_ATTACHMENTS_COUNT, DEFAULT_ATTACHMENTS_COUNT);
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("plain/text");
		posting.attachmentMimeTypes.add("application/pdf");
		posting.attachmentMimeTypes.add("application/zip");
		posting.attachmentMimeTypes.add("application/rar");
		posting.attachmentMimeTypes.add("application/x-shockwave-flash");
		posting.attachmentRatings.add(new Pair<>("SFW", "SFW"));
		posting.attachmentRatings.add(new Pair<>("R-15", "R-15"));
		posting.attachmentRatings.add(new Pair<>("R-18", "R-18"));
		posting.attachmentRatings.add(new Pair<>("R-18G", "R-18G"));
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
	
	@Override
	public CustomPreference obtainCustomPreferenceConfiguration(String key)
	{
		if (KEY_ALWAYS_LOAD_CAPTCHA.equals(key))
		{
			Resources resources = getResources();
			CustomPreference customPreference = new CustomPreference();
			customPreference.title = resources.getString(R.string.pref_always_load_captcha);
			customPreference.summary = resources.getString(R.string.pref_always_load_captcha_summary);
			return customPreference;
		}
		return null;
	}
	
	public boolean isAlwaysLoadCaptcha()
	{
		return get(null, KEY_ALWAYS_LOAD_CAPTCHA, false);
	}
	
	public void updateFromThreadsJson(String boardName, JSONObject jsonObject)
	{
		int pagesCount = jsonObject.optInt("pages");
		if (pagesCount > 0) storePagesCount(boardName, pagesCount);
	}
	
	public void updateFromPostsJson(String boardName, JSONObject jsonObject)
	{
		boolean filesEnabled = jsonObject.optBoolean("allow_files", true);
		boolean namesEnabled = jsonObject.optBoolean("allow_names", true);
		boolean tripcodesEnabled = !jsonObject.optBoolean("restrict_trip", false);
		int attachmentsCount = filesEnabled ? jsonObject.optInt("files_max_qty", DEFAULT_ATTACHMENTS_COUNT) : 0;
		set(boardName, KEY_NAMES_ENABLED, namesEnabled);
		set(boardName, KEY_TRIPCODES_ENABLED, tripcodesEnabled);
		set(boardName, KEY_ATTACHMENTS_COUNT, attachmentsCount);
	}
}