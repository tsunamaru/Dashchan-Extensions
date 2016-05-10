package com.mishiranu.dashchan.chan.makabapaid;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.Attachment;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class MakabaPaidModelMapper
{
	private static final Pattern BADGE_PATTERN = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.+?/?>");
	
	public static FileAttachment createFileAttachment(JSONObject jsonObject, ChanLocator locator, String boardName,
			boolean archive) throws JSONException
	{
		String file = CommonUtils.getJsonString(jsonObject, "path");
		String thumbnail = CommonUtils.optJsonString(jsonObject, "thumbnail");
		Uri fileUri = file != null ? archive ? locator.buildPath(boardName, "arch", file)
				: locator.buildPath(boardName, file) : null;
		Uri thumbnailUri = thumbnail != null ? archive ? locator.buildPath(boardName, "arch", thumbnail)
				: locator.buildPath(boardName, thumbnail) : null;
		int size = jsonObject.optInt("size") * 1024;
		int width = jsonObject.optInt("width");
		int height = jsonObject.optInt("height");
		return new FileAttachment().setFileUri(locator, fileUri).setThumbnailUri(locator, thumbnailUri)
				.setSize(size).setWidth(width).setHeight(height);
	}
	
	public static Post createPost(JSONObject jsonObject, ChanLocator locator, String boardName, boolean archive,
			boolean sageEnabled) throws JSONException
	{
		Post post = new Post();
		String num = CommonUtils.getJsonString(jsonObject, "num");
		String parent = CommonUtils.getJsonString(jsonObject, "parent");
		post.setPostNumber(num);
		if (!"0".equals(parent)) post.setParentPostNumber(parent);
		if (jsonObject.getInt("op") != 0) post.setOriginalPoster(true);
		if (jsonObject.getInt("sticky") != 0) post.setSticky(true);
		if (jsonObject.getInt("closed") != 0) post.setClosed(true);
		int banned = jsonObject.optInt("banned");
		if (banned == 1) post.setPosterBanned(true);
		else if (banned == 2) post.setPosterWarned(true);
		String comment = CommonUtils.getJsonString(jsonObject, "comment");
		String email = CommonUtils.optJsonString(jsonObject, "email");
		String name = CommonUtils.optJsonString(jsonObject, "name");
		String subject = CommonUtils.optJsonString(jsonObject, "subject");
		String tripcode = CommonUtils.optJsonString(jsonObject, "trip");
		long timestamp = jsonObject.getLong("timestamp") * 1000L;
		ArrayList<Attachment> attachments = null;
		try
		{
			JSONArray filesArray = jsonObject.getJSONArray("files");
			if (filesArray.length() > 0)
			{
				for (int i = 0, length = filesArray.length(); i < length; i++)
				{
					if (attachments == null) attachments = new ArrayList<>();
					attachments.add(createFileAttachment(filesArray.getJSONObject(i), locator, boardName, archive));
				}
			}
		}
		catch (JSONException e)
		{
			attachments = null;
		}
		if (!StringUtils.isEmpty(subject)) subject = StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim());
		if (StringUtils.isEmpty(comment))
		{
			comment = comment.replace("href=\"/hidden_", "href=\"/"); // Fix links
			comment = comment.replace(" (OP)</a>", "</a>");
			comment = comment.replace("&#47;", "/");
		}
		comment = fixApiEscapeCharacters(comment);
		
		boolean sage = sageEnabled && !StringUtils.isEmpty(email) && email.equals("mailto:sage");
		if (sage)
		{
			email = null;
			post.setSage(true);
		}
		String identifier = null;
		if (!StringUtils.isEmpty(name))
		{
			name = StringUtils.clearHtml(name).trim();
			int index = name.indexOf(" ID: ");
			if (index >= 0)
			{
				identifier = name.substring(index + 5).replaceAll(" +", " ");
				name = name.substring(0, index);
				if ("Heaven".equals(identifier))
				{
					identifier = null;
					post.setSage(true);
				}
			}
		}
		String capcode = null;
		if (!StringUtils.isEmpty(tripcode))
		{
			if ("!!%adm%!!".equals(tripcode)) capcode = "Abu";
			else if ("!!%mod%!!".equals(tripcode)) capcode = "Mod";
			if (capcode != null) tripcode = null;
			else tripcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(tripcode).trim());
		}
		
		String icon = CommonUtils.optJsonString(jsonObject, "icon");
		ArrayList<Icon> icons = null;
		if (!StringUtils.isEmpty(icon))
		{
			Matcher matcher = BADGE_PATTERN.matcher(icon);
			while (matcher.find())
			{
				String path = matcher.group(1);
				String title = matcher.group(2);
				Uri uri = locator.buildPath(path);
				if (StringUtils.isEmpty(title))
				{
					title = uri.getLastPathSegment();
					title = title.substring(0, title.lastIndexOf('.'));
				}
				if (icons == null) icons = new ArrayList<>();
				title = StringUtils.clearHtml(title);
				icons.add(new Icon(locator, uri, title));
			}
		}
		
		return post.setTimestamp(timestamp).setSubject(subject).setComment(comment).setName(name)
				.setIdentifier(identifier).setTripcode(tripcode).setCapcode(capcode).setEmail(email)
				.setAttachments(attachments).setIcons(icons);
	}
	
	public static Post[] createPosts(JSONArray jsonArray, ChanLocator locator, String boardName, boolean archive,
			boolean sageEnabled) throws JSONException
	{
		if (jsonArray.length() > 0)
		{
			Post[] posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++)
			{
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName, archive, sageEnabled);
			}
			if (archive) posts[0].setArchived(true);
			return posts;
		}
		return null;
	}
	
	public static Posts createThread(JSONObject jsonObject, ChanLocator locator, String boardName,
			boolean sageEnabled) throws JSONException
	{
		int postsCount = jsonObject.optInt("posts_count");
		int postsWithFilesCount = Math.max(jsonObject.optInt("files_count"), jsonObject.optInt("images_count"));
		Post[] posts;
		if (jsonObject.has("posts"))
		{
			JSONArray jsonArray = jsonObject.getJSONArray("posts");
			try
			{
				jsonArray = jsonArray.getJSONArray(0);
			}
			catch (JSONException e)
			{
				
			}
			posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++)
			{
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName, false, sageEnabled);
			}
		}
		else posts = new Post[] {createPost(jsonObject, locator, boardName, false, sageEnabled)};
		for (int i = 0; i < posts.length; i++)
		{
			if (posts[i].getAttachmentsCount() > 0) postsWithFilesCount++;
		}
		postsCount += posts.length;
		return new Posts(posts).addPostsCount(postsCount).addPostsWithFilesCount(postsWithFilesCount);
	}
	
	public static String fixApiEscapeCharacters(String text)
	{
		if (text != null)
		{
			text = text.replace("\\t", "\t");
			text = text.replace("\\n", "\n");
			text = text.replace("\\r", "\r");
			text = text.replace("\\b", "\b");
		}
		return text;
	}
}