package com.mishiranu.dashchan.chan.makabapaid;

import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class MakabaPaidChanConfiguration extends ChanConfiguration
{
	private static final String KEY_ICONS = "icons";
	private static final String KEY_IMAGES_ENABLED = "images_enabled";
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_TRIPCODES_ENABLED = "tripcodes_enabled";
	private static final String KEY_SUBJECTS_ENABLED = "subjects_enabled";
	private static final String KEY_SAGE_ENABLED = "sage_enabled";
	private static final String KEY_FLAGS_ENABLED = "flags_enabled";
	private static final String KEY_MAX_COMMENT_LENGTH = "max_comment_length";
	
	private static final String KEY_CAPTCHA_BYPASS = "captcha_bypass";
	
	public MakabaPaidChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		request(OPTION_ALLOW_USER_AUTHORIZATION);
		setDefaultName("Аноним");
		setBumpLimit(500);
		addCustomPreference(KEY_CAPTCHA_BYPASS, true);
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowSearch = true;
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowReporting = true;
		return board;
	}
	
	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean replying)
	{
		Posting posting = new Posting();
		posting.allowName = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowTripcode = get(boardName, KEY_TRIPCODES_ENABLED, true);
		posting.allowEmail = true;
		posting.allowSubject = get(boardName, KEY_SUBJECTS_ENABLED, true);
		posting.optionSage = get(boardName, KEY_SAGE_ENABLED, true);
		posting.optionOriginalPoster = true;
		posting.maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 15000);
		posting.maxCommentLengthEncoding = "UTF-8";
		posting.attachmentCount = get(boardName, KEY_IMAGES_ENABLED, true) ? 4 : 0;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/webm");
		try
		{
			JSONArray jsonArray = new JSONArray(get(boardName, KEY_ICONS, "[]"));
			for (int i = 0; i < jsonArray.length(); i++)
			{
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String name = CommonUtils.getJsonString(jsonObject, "name");
				int num = jsonObject.getInt("num");
				posting.userIcons.add(new Pair<>(Integer.toString(num), name));
			}
		}
		catch (Exception e)
		{
			
		}
		posting.hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false);
		return posting;
	}
	
	@Override
	public Reporting obtainReportingConfiguration(String boardName)
	{
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.multiplePosts = true;
		return reporting;
	}
	
	public boolean isSageEnabled(String boardName)
	{
		return get(boardName, KEY_SAGE_ENABLED, true);
	}
	
	public void updateFromBoardsJson(JSONArray jsonArray)
	{
		try
		{
			for (int i = 0; i < jsonArray.length(); i++)
			{
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String boardName = CommonUtils.getJsonString(jsonObject, "id");
				String defaultName = CommonUtils.optJsonString(jsonObject, "default_name");
				int bumpLimit = jsonObject.optInt("bump_limit");
				if (!StringUtils.isEmpty(defaultName)) storeDefaultName(boardName, defaultName);
				if (bumpLimit > 0) storeBumpLimit(boardName, bumpLimit);
			}
		}
		catch (JSONException e)
		{
			
		}
	}
	
	public void updateFromThreadsPostsJson(String boardName, JSONObject jsonObject)
	{
		String title = CommonUtils.optJsonString(jsonObject, "BoardName");
		String description = CommonUtils.optJsonString(jsonObject, "BoardInfoOuter");
		description = transformBoardDescription(description);
		String defaultName = CommonUtils.optJsonString(jsonObject, "default_name");
		int bumpLimit = jsonObject.optInt("bump_limit");
		int maxCommentLength = jsonObject.optInt("max_comment");
		if (!StringUtils.isEmpty(title)) storeBoardTitle(boardName, title);
		if (!StringUtils.isEmpty(description)) storeBoardDescription(boardName, description);
		if (!StringUtils.isEmpty(defaultName)) storeDefaultName(boardName, defaultName);
		if (bumpLimit > 0) storeBumpLimit(boardName, bumpLimit);
		if (maxCommentLength > 0) set(boardName, KEY_MAX_COMMENT_LENGTH, maxCommentLength);
		editBoards(boardName, jsonObject, KEY_IMAGES_ENABLED, "enable_images");
		editBoards(boardName, jsonObject, KEY_NAMES_ENABLED, "enable_names");
		editBoards(boardName, jsonObject, KEY_TRIPCODES_ENABLED, "enable_trips");
		editBoards(boardName, jsonObject, KEY_SUBJECTS_ENABLED, "enable_subject");
		editBoards(boardName, jsonObject, KEY_SAGE_ENABLED, "enable_sage");
		editBoards(boardName, jsonObject, KEY_FLAGS_ENABLED, "enable_flags");
		JSONArray pagesArray = jsonObject.optJSONArray("pages");
		if (pagesArray != null) storePagesCount(boardName, pagesArray.length());
		JSONArray iconsArray = jsonObject.optJSONArray("icons");
		set(boardName, KEY_ICONS, iconsArray != null ? iconsArray.toString() : null);
	}
	
	private void editBoards(String boardName, JSONObject jsonObject, String key, String name)
	{
		if (jsonObject.has(name))
		{
			boolean value = jsonObject.optInt(name, 1) != 0;
			set(boardName, key, value);
		}
	}
	
	public String transformBoardDescription(String description)
	{
		description = StringUtils.nullIfEmpty(StringUtils.clearHtml(description).trim());
		if (description != null)
		{
			StringBuilder builder = new StringBuilder();
			builder.append(description.substring(0, 1).toUpperCase(Locale.getDefault()));
			builder.append(description.substring(1));
			if (!description.endsWith(".") && !description.endsWith("!")) builder.append(".");
			description = builder.toString();
		}
		return description;
	}
}