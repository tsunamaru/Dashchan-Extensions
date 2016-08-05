package com.mishiranu.dashchan.chan.meguca;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class MegucaModelMapper
{
	public static FileAttachment createFileAttachment(JSONObject jsonObject, MegucaChanLocator locator)
			throws JSONException
	{
		String file = CommonUtils.getJsonString(jsonObject, "src");
		String thumbnail = CommonUtils.optJsonString(jsonObject, "thumb");
		Uri fileUri = locator.buildPath("static", "src", file);
		Uri thumbnailUri = thumbnail != null ? locator.buildPath("static", "thumb", thumbnail) : null;
		String originalName = CommonUtils.optJsonString(jsonObject, "imgnm");
		int size = jsonObject.optInt("size");
		if (thumbnailUri == null && locator.isImageExtension(file) && size <= 50 * 1024) thumbnailUri = fileUri;
		FileAttachment attachment = new FileAttachment().setFileUri(locator, fileUri)
				.setThumbnailUri(locator, thumbnailUri).setOriginalName(originalName).setSize(size);
		JSONArray jsonArray = jsonObject.optJSONArray("dims");
		if (jsonArray != null && jsonArray.length() >= 2)
		{
			attachment.setWidth(jsonArray.getInt(0)).setHeight(jsonArray.getInt(1));
		}
		return attachment;
	}
	
	public static Post createPost(JSONObject jsonObject, MegucaChanLocator locator, String boardName)
			throws JSONException
	{
		if (jsonObject.optBoolean("editing")) return null;
		Post post = new Post();
		int num = jsonObject.getInt("num");
		post.setPostNumber(Integer.toString(num));
		int op = jsonObject.optInt("op", 0);
		if (op > 0) post.setParentPostNumber(Integer.toString(op));
		post.setTimestamp(jsonObject.optLong("time"));
		int banned = jsonObject.optInt("banned", 0);
		if (banned != 0) post.setPosterBanned(true);
		post.setSubject(CommonUtils.optJsonString(jsonObject, "subject"));
		post.setName(CommonUtils.optJsonString(jsonObject, "name"));
		post.setIdentifier(CommonUtils.optJsonString(jsonObject, "mnemonic")); // May be replaced later
		post.setTripcode(CommonUtils.optJsonString(jsonObject, "tripcode"));
		post.setCapcode(CommonUtils.optJsonString(jsonObject, "auth"));
		String email = CommonUtils.optJsonString(jsonObject, "email");
		if ("sage".equals(email)) post.setSage(true);
		else post.setEmail(email);
		
		JSONObject linksObject = jsonObject.optJSONObject("links");
		String comment = CommonUtils.getJsonString(jsonObject, "body");
		StringBuilder commentBuilder = null;
		if (linksObject != null)
		{
			for (Iterator<String> keys = linksObject.keys(); keys.hasNext(); )
			{
				String postNumber = keys.next();
				if (Integer.parseInt(postNumber) >= num) continue;
				String threadNumber = CommonUtils.getJsonString(linksObject, postNumber);
				if (commentBuilder == null) commentBuilder = new StringBuilder(comment);
				int index = -1;
				while ((index = commentBuilder.indexOf(">>" + postNumber, index + 1)) >= 0)
				{
					commentBuilder.replace(index, index + 2 + postNumber.length(), "<a href=\"/" + boardName
							+ "/" + threadNumber + "#" + postNumber + "\">&gt;&gt;" + postNumber + "</a>");
				}
			}
		}
		if (commentBuilder != null) comment = commentBuilder.toString();
		comment = comment.replaceAll(">>>(/watch\\?v=.{11})", "https://youtube.com$1");
		comment = comment.replaceAll("(?<=^|\n)>(.*?)(?=\n|$)", "<em>&gt;$1</em>");
		comment = comment.replace("\n", "<br />");
		comment = comment.replaceAll("\\[spoiler\\](.*?)(?:\\[/spoiler\\]|$)", "<del>$1</del>");
		comment = StringUtils.linkify(comment);
		post.setComment(comment);
		
		jsonObject = jsonObject.optJSONObject("image");
		if (jsonObject != null) post.setAttachments(createFileAttachment(jsonObject, locator));
		return post;
	}
}