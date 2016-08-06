package com.mishiranu.dashchan.chan.dvach;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.model.Attachment;
import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class DvachModelMapper
{
	private static final Pattern BADGE_PATTERN = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.+?/?>");
	private static final Pattern CODE_PATTERN = Pattern.compile("\\[code(?:\\s+lang=.+?)?\\](?:<br ?/?>)*(.+?)" +
			"(?:<br ?/?>)*\\[/code\\]", Pattern.CASE_INSENSITIVE);
	
	private static final Uri URI_ICON_OS = Uri.parse("chan:///res/raw/raw_os");
	private static final Uri URI_ICON_ANDROID = Uri.parse("chan:///res/raw/raw_os_android");
	private static final Uri URI_ICON_APPLE = Uri.parse("chan:///res/raw/raw_os_apple");
	private static final Uri URI_ICON_LINUX = Uri.parse("chan:///res/raw/raw_os_linux");
	private static final Uri URI_ICON_WINDOWS = Uri.parse("chan:///res/raw/raw_os_windows");
	
	private static final Uri URI_ICON_BROWSER = Uri.parse("chan:///res/raw/raw_browser");
	private static final Uri URI_ICON_CHROME = Uri.parse("chan:///res/raw/raw_browser_chrome");
	private static final Uri URI_ICON_EDGE = Uri.parse("chan:///res/raw/raw_browser_edge");
	private static final Uri URI_ICON_FIREFOX = Uri.parse("chan:///res/raw/raw_browser_firefox");
	private static final Uri URI_ICON_OPERA = Uri.parse("chan:///res/raw/raw_browser_opera");
	private static final Uri URI_ICON_SAFARI = Uri.parse("chan:///res/raw/raw_browser_safari");
	
	public static FileAttachment createFileAttachment(JSONObject jsonObject, DvachChanLocator locator, String boardName,
			String archiveStartPath) throws JSONException
	{
		String file = CommonUtils.getJsonString(jsonObject, "path");
		String thumbnail = CommonUtils.optJsonString(jsonObject, "thumbnail");
		Uri fileUri = file != null ? archiveStartPath != null ? locator.buildPath(archiveStartPath, file)
				: locator.buildPath(boardName, file) : null;
		Uri thumbnailUri = thumbnail != null ? archiveStartPath != null ? locator.buildPath(archiveStartPath, thumbnail)
				: locator.buildPath(boardName, thumbnail) : null;
		int size = jsonObject.optInt("size") * 1024;
		int width = jsonObject.optInt("width");
		int height = jsonObject.optInt("height");
		return new FileAttachment().setFileUri(locator, fileUri).setThumbnailUri(locator, thumbnailUri)
				.setSize(size).setWidth(width).setHeight(height);
	}
	
	public static Post createPost(JSONObject jsonObject, DvachChanLocator locator, String boardName,
			String archiveStartPath, boolean sageEnabled) throws JSONException
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
		post.setTimestamp(jsonObject.getLong("timestamp") * 1000L);
		String subject = CommonUtils.optJsonString(jsonObject, "subject");
		if (!StringUtils.isEmpty(subject))
		{
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		String comment = CommonUtils.getJsonString(jsonObject, "comment");
		if (!StringUtils.isEmpty(comment))
		{
			comment = comment.replace(" (OP)</a>", "</a>");
			comment = comment.replace("&#47;", "/");
		}
		comment = fixApiEscapeCharacters(comment);
		if ("pr".equals(boardName) && comment != null)
		{
			comment = CODE_PATTERN.matcher(comment).replaceAll("<fakecode>$1</fakecode>");
		}
		post.setComment(comment);
		ArrayList<Attachment> attachments = null;
		try
		{
			JSONArray filesArray = jsonObject.getJSONArray("files");
			if (filesArray.length() > 0)
			{
				for (int i = 0, length = filesArray.length(); i < length; i++)
				{
					if (attachments == null) attachments = new ArrayList<>();
					attachments.add(createFileAttachment(filesArray.getJSONObject(i), locator, boardName,
							archiveStartPath));
				}
			}
		}
		catch (JSONException e)
		{
			attachments = null;
		}
		post.setAttachments(attachments);
		
		String name = CommonUtils.optJsonString(jsonObject, "name");
		String tripcode = CommonUtils.optJsonString(jsonObject, "trip");
		String email = CommonUtils.optJsonString(jsonObject, "email");
		boolean sage = sageEnabled && !StringUtils.isEmpty(email) && email.equals("mailto:sage");
		String userAgentData = null;
		if (sage)
		{
			email = null;
			post.setSage(true);
		}
		String identifier = null;
		if (!StringUtils.isEmpty(name))
		{
			int index = "s".equals(boardName) ? name.indexOf("&nbsp;<span style=\"color:rgb(164,164,164);\">") : -1;
			if (index >= 0)
			{
				userAgentData = name.substring(index + 44);
				name = name.substring(0, index);
			}
			name = StringUtils.clearHtml(name).trim();
			index = name.indexOf(" ID: ");
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
		post.setName(name);
		post.setIdentifier(identifier);
		post.setTripcode(tripcode);
		post.setCapcode(capcode);
		post.setEmail(email);
		
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
		if (userAgentData != null)
		{
			int index1 = userAgentData.indexOf('(');
			int index2 = userAgentData.indexOf(')');
			if (index2 > index1 && index1 >= 0)
			{
				userAgentData = userAgentData.substring(index1 + 1, index2);
				int index = userAgentData.indexOf(':');
				if (index >= 0)
				{
					String os = StringUtils.clearHtml(userAgentData.substring(0, index));
					String browser = StringUtils.clearHtml(userAgentData.substring(index + 2));
					if (!"Неизвестно".equals(os))
					{
						Uri osIconUri = URI_ICON_OS;
						if (os.contains("Windows")) osIconUri = URI_ICON_WINDOWS;
						else if (os.contains("Linux")) osIconUri = URI_ICON_LINUX;
						else if (os.contains("Apple")) osIconUri = URI_ICON_APPLE;
						else if (os.contains("Android")) osIconUri = URI_ICON_ANDROID;
						if (icons == null) icons = new ArrayList<>();
						icons.add(new Icon(locator, osIconUri, os));
					}
					if (!"Неизвестно".equals(browser))
					{
						Uri browserIconUri = URI_ICON_BROWSER;
						if (browser.contains("Chrom")) browserIconUri = URI_ICON_CHROME;
						else if (browser.contains("Microsoft Edge")) browserIconUri = URI_ICON_EDGE;
						else if (browser.contains("Internet Explorer")) browserIconUri = URI_ICON_EDGE;
						else if (browser.contains("Firefox")) browserIconUri = URI_ICON_FIREFOX;
						else if (browser.contains("Iceweasel")) browserIconUri = URI_ICON_FIREFOX;
						else if (browser.contains("Opera")) browserIconUri = URI_ICON_OPERA;
						else if (browser.contains("Safari")) browserIconUri = URI_ICON_SAFARI;
						if (icons == null) icons = new ArrayList<>();
						icons.add(new Icon(locator, browserIconUri, browser));
					}
				}
			}
		}
		post.setIcons(icons);
		return post;
	}
	
	public static Post[] createPosts(JSONArray jsonArray, DvachChanLocator locator, String boardName,
			String archiveStartPath, boolean sageEnabled) throws JSONException
	{
		if (jsonArray.length() > 0)
		{
			Post[] posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++)
			{
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName, archiveStartPath, sageEnabled);
			}
			if (archiveStartPath != null) posts[0].setArchived(true);
			return posts;
		}
		return null;
	}
	
	public static Posts createThread(JSONObject jsonObject, DvachChanLocator locator, String boardName,
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
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName, null, sageEnabled);
			}
		}
		else posts = new Post[] {createPost(jsonObject, locator, boardName, null, sageEnabled)};
		for (int i = 0; i < posts.length; i++)
		{
			if (posts[i].getAttachmentsCount() > 0) postsWithFilesCount++;
		}
		postsCount += posts.length;
		return new Posts(posts).addPostsCount(postsCount).addPostsWithFilesCount(postsWithFilesCount);
	}
	
	public static Post createWakabaArchivePost(JSONObject jsonObject, DvachChanLocator locator, String boardName)
			throws JSONException
	{
		Post post = new Post();
		post.setArchived(true);
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
		post.setComment(CommonUtils.getJsonString(jsonObject, "comment"));
		String name = CommonUtils.optJsonString(jsonObject, "name");
		if (!StringUtils.isEmpty(name))
		{
			post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim()));
		}
		String subject = CommonUtils.optJsonString(jsonObject, "suject");
		if (!StringUtils.isEmpty(subject))
		{
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		post.setTimestamp(jsonObject.getLong("timestamp") * 1000L);
		ArrayList<Attachment> mAttachments = null;
		String image = CommonUtils.optJsonString(jsonObject, "image");
		if (!StringUtils.isEmpty(image))
		{
			String thumbnail = CommonUtils.optJsonString(jsonObject, "thumbnail");
			FileAttachment attachment = new FileAttachment();
			attachment.setFileUri(locator, locator.buildPath(boardName, "arch", "wakaba", image));
			if (!StringUtils.isEmpty(thumbnail))
			{
				attachment.setThumbnailUri(locator, locator.buildPath(boardName, "arch", "wakaba", thumbnail));
			}
			attachment.setWidth(jsonObject.optInt("width"));
			attachment.setHeight(jsonObject.optInt("height"));
			attachment.setSize(jsonObject.optInt("size") * 1024);
			if (mAttachments == null) mAttachments = new ArrayList<>();
			mAttachments.add(attachment);
		}
		String video = CommonUtils.optJsonString(jsonObject, "video");
		if (!StringUtils.isEmpty(video))
		{
			EmbeddedAttachment attachment = EmbeddedAttachment.obtain(video);
			if (attachment != null)
			{
				if (mAttachments == null) mAttachments = new ArrayList<>();
				mAttachments.add(attachment);
			}
		}
		if (mAttachments != null) post.setAttachments(mAttachments);
		return post;
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