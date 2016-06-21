package com.mishiranu.dashchan.chan.ronery;

import org.json.JSONException;
import org.json.JSONObject;

import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class RoneryModelMapper
{
	public static Post createPost(JSONObject jsonObject, RoneryChanLocator locator) throws JSONException
	{
		Post post = new Post();
		String postNumber = CommonUtils.getJsonString(jsonObject, "num");
		String threadNumber = CommonUtils.getJsonString(jsonObject, "thread_num");
		if (!postNumber.equals(threadNumber)) post.setParentPostNumber(threadNumber);
		post.setPostNumber(postNumber);
		post.setTimestamp(jsonObject.getLong("timestamp") * 1000L);
		String subject = CommonUtils.getJsonString(jsonObject, "title_processed");
		if (!StringUtils.isEmpty(subject))
		{
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		post.setComment(CommonUtils.getJsonString(jsonObject, "comment_processed"));
		String country = CommonUtils.optJsonString(jsonObject, "poster_country");
		String countryName = CommonUtils.optJsonString(jsonObject, "poster_country_name");
		if (country != null && countryName != null)
		{
			countryName = StringUtils.clearHtml(countryName);
			post.setIcons(RoneryPostsParser.obtainIcon(locator, country, countryName));
		}
		jsonObject = jsonObject.optJSONObject("media");
		if (jsonObject != null)
		{
			String filePath = CommonUtils.getJsonString(jsonObject, "media_link");
			String thumbnailPath = CommonUtils.optJsonString(jsonObject, "thumb_link");
			FileAttachment attachment = new FileAttachment();
			attachment.setFileUri(locator, locator.buildPath(filePath));
			if (thumbnailPath != null) attachment.setThumbnailUri(locator, locator.buildPath(thumbnailPath));
			if ("1".equals(CommonUtils.optJsonString(jsonObject, "spoiler"))) attachment.setSpoiler(true);
			attachment.setWidth(jsonObject.optInt("media_w"));
			attachment.setHeight(jsonObject.optInt("media_h"));
			attachment.setSize(jsonObject.optInt("media_size"));
		}
		return post;
	}
}