package com.mishiranu.dashchan.chan.touhouchan;

import org.json.JSONArray;
import org.json.JSONObject;

import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class TouhouchanChanConfiguration extends ChanConfiguration {
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_THREAD_IMAGES_ENABLED = "thread_images_enabled";
	private static final String KEY_REPLY_IMAGES_ENABLED = "reply_images_enabled";
	private static final String KEY_MAX_COMMENT_LENGTH = "max_comment_length";
	private static final String KEY_FLAGS_ENABLED = "flags_enabled";

	public TouhouchanChanConfiguration() {
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		addCaptchaType("wakaba");
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowSearch = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if ("wakaba".equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "Wakaba";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.IN_THREAD;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowName = posting.allowTripcode = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 0);
		posting.attachmentCount = 1;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("video/*");
		posting.attachmentMimeTypes.add("application/ogg");
		posting.attachmentMimeTypes.add("application/pdf");
		posting.attachmentMimeTypes.add("application/zip");
		posting.attachmentMimeTypes.add("application/rar");
		posting.attachmentMimeTypes.add("application/x-bittorrent");
		posting.attachmentMimeTypes.add("application/x-shockwave-flash");
		posting.hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false);
		return posting;
	}

	@Override
	public Deleting obtainDeletingConfiguration(String boardName) {
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		return deleting;
	}

	public void updateFromThreadsPostsJson(String boardName, JSONObject jsonObject) {
		JSONArray pagesArray = jsonObject.optJSONArray("pages");
		if (pagesArray != null && pagesArray.length() > 0) {
			storePagesCount(boardName, pagesArray.length());
		}
		JSONObject infoObject = jsonObject.optJSONObject("boardinfo");
		if (infoObject != null) {
			String title = CommonUtils.optJsonString(infoObject, "board_name");
			String description = CommonUtils.optJsonString(infoObject, "board_desc");
			if (!StringUtils.isEmpty(title)) {
				storeBoardTitle(boardName, title);
			}
			if (!StringUtils.isEmpty(description)) {
				storeBoardDescription(boardName, description);
			}
			JSONObject configObject = infoObject.optJSONObject("config");
			if (configObject != null) {
				String defaultName = StringUtils.nullIfEmpty(CommonUtils.optJsonString(configObject, "default_name"));
				int bumpLimit = configObject.optInt("max_res");
				boolean namesEnabled = configObject.optInt("names_allowed", 1) != 0;
				boolean threadImagesEnabled = configObject.optInt("image_op", 1) != 0;
				boolean replyImagesEnabled = configObject.optInt("image_replies", 1) != 0;
				int maxCommentLength = configObject.optInt("max_comment_length", 0);
				boolean flagsEnabled = configObject.optInt("geoip_enabled") != 0;
				storeDefaultName(boardName, defaultName);
				if (bumpLimit > 0) {
					storeBumpLimit(boardName, bumpLimit);
				}
				set(boardName, KEY_NAMES_ENABLED, namesEnabled);
				set(boardName, KEY_THREAD_IMAGES_ENABLED, threadImagesEnabled);
				set(boardName, KEY_REPLY_IMAGES_ENABLED, replyImagesEnabled);
				set(boardName, KEY_MAX_COMMENT_LENGTH, maxCommentLength);
				set(boardName, KEY_FLAGS_ENABLED, flagsEnabled);
			}
		}
	}
}