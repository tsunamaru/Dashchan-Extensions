package com.mishiranu.dashchan.chan.tumbach;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.model.Attachment;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class TumbachModelMapper {
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
	private static final Uri URI_ICON_VIVALDI = Uri.parse("chan:///res/raw/raw_browser_vivaldi");

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public static FileAttachment createFileAttachment(JSONObject jsonObject, TumbachChanLocator locator,
			String boardName) throws JSONException {
		FileAttachment attachment = new FileAttachment();
		String name = CommonUtils.getJsonString(jsonObject, "name");
		attachment.setFileUri(locator, locator.buildPath(boardName, "src", name));
		JSONObject thumbObject = jsonObject.optJSONObject("thumb");
		if (thumbObject != null) {
			String thumbName = CommonUtils.getJsonString(thumbObject, "name");
			attachment.setThumbnailUri(locator, locator.buildPath(boardName, "thumb", thumbName));
		}
		JSONObject dimensionsObject = jsonObject.optJSONObject("dimensions");
		attachment.setSize(jsonObject.optInt("size"));
		if (dimensionsObject != null) {
			attachment.setWidth(dimensionsObject.optInt("width"));
			attachment.setHeight(dimensionsObject.optInt("height"));
		}
		return attachment;
	}

	public static Post createPost(JSONObject jsonObject, TumbachChanLocator locator, String boardName)
			throws JSONException {
		Post post = new Post();
		if (jsonObject.optBoolean("isOp")) {
			post.setOriginalPoster(true);
		}
		String number = CommonUtils.getJsonString(jsonObject, "number");
		String threadNumber = CommonUtils.getJsonString(jsonObject, "threadNumber");
		post.setPostNumber(number);
		if (!number.equals(threadNumber)) {
			post.setParentPostNumber(threadNumber);
		}
		String createdAt = CommonUtils.getJsonString(jsonObject, "createdAt");
		try {
			post.setTimestamp(DATE_FORMAT.parse(createdAt).getTime());
		} catch (ParseException e) {
			// Ignore exception
		}
		String name = CommonUtils.optJsonString(jsonObject, "name");
		if (!StringUtils.isEmpty(name)) {
			post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim()));
		}
		String tripcode = CommonUtils.optJsonString(jsonObject, "tripcode");
		if (!StringUtils.isEmpty(tripcode)) {
			post.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(tripcode).trim()));
		}
		String email = CommonUtils.optJsonString(jsonObject, "email");
		if (!StringUtils.isEmpty(email) && email.equalsIgnoreCase("sage")) {
			post.setSage(true);
		} else {
			post.setEmail(StringUtils.nullIfEmpty(StringUtils.clearHtml(email).trim()));
		}
		JSONObject userObject = jsonObject.optJSONObject("user");
		if (userObject != null) {
			String level = CommonUtils.optJsonString(userObject, "level");
			if (!StringUtils.isEmpty(level) && !"USER".equals(level)) {
				post.setCapcode(level.substring(0, 1).toUpperCase(Locale.US) + level.substring(1, level.length())
						.toLowerCase(Locale.US));
			}
		}
		String subject = CommonUtils.optJsonString(jsonObject, "subject");
		if (!StringUtils.isEmpty(subject)) {
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		String text = CommonUtils.optJsonString(jsonObject, "text");
		if (text != null) {
			text = text.replaceAll("(?s)<a href=\"javascript:void\\(0\\);\" class=\"expandCollapse\".*?>.*?</a>", "");
			text = text.replaceAll("<div class=\"codeBlock.*?>(.*?)</div>", "<pre>$1</pre>");
			post.setComment(text);
		}
		post.setCommentMarkup(CommonUtils.optJsonString(jsonObject, "rawText"));
		ArrayList<Icon> icons = null;
		String extraData = CommonUtils.optJsonString(jsonObject, "extraData");
		if (extraData != null) {
			int index1 = extraData.indexOf('(');
			int index2 = extraData.indexOf(") ");
			if (index1 >= 0 && index2 > index1) {
				String[] osArray = extraData.substring(index1 + 1, index2).split("; *");
				for (int i = 0; i < osArray.length; i++) {
					Uri osIconUri = null;
					if (osArray[i].startsWith("Windows")) {
						osIconUri = URI_ICON_WINDOWS;
					} else if (osArray[i].startsWith("Linux")) {
						osIconUri = URI_ICON_LINUX;
					} else if (osArray[i].startsWith("Ubuntu")) {
						osIconUri = URI_ICON_LINUX;
					} else if (osArray[i].startsWith("Mac")) {
						osIconUri = URI_ICON_APPLE;
					} else if (osArray[i].startsWith("iOS")) {
						osIconUri = URI_ICON_APPLE;
					} else if (osArray[i].startsWith("Android")) {
						osIconUri = URI_ICON_ANDROID;
					}
					String os;
					if (osIconUri == null && i == osArray.length - 1) {
						osIconUri = URI_ICON_OS;
						os = osArray[0];
					} else {
						os = osArray[i];
					}
					if (osIconUri != null) {
						if (icons == null) {
							icons = new ArrayList<>();
						}
						icons.add(new Icon(locator, osIconUri, os));
						break;
					}
				}
				String[] browserArray = extraData.substring(index2 + 2).replaceAll("\\(.*?\\)", "").split(" +");
				for (int i = 0; i < browserArray.length; i++) {
					Uri browserIconUri = null;
					if (browserArray[i].startsWith("Chrom")) {
						browserIconUri = URI_ICON_CHROME;
					} else if (browserArray[i].startsWith("Firefox")) {
						browserIconUri = URI_ICON_FIREFOX;
					} else if (browserArray[i].startsWith("Iceweasel")) {
						browserIconUri = URI_ICON_FIREFOX;
					} else if (browserArray[i].startsWith("Edge")) {
						browserIconUri = URI_ICON_EDGE;
					} else if (browserArray[i].startsWith("IE")) {
						browserIconUri = URI_ICON_EDGE;
					} else if (browserArray[i].startsWith("Opera")) {
						browserIconUri = URI_ICON_OPERA;
					} else if (browserArray[i].startsWith("Vivaldi")) {
						browserIconUri = URI_ICON_VIVALDI;
					} else if (browserArray[i].startsWith("Safari")) {
						browserIconUri = URI_ICON_SAFARI;
					}
					String browser;
					if (browserIconUri == null && i == browserArray.length - 1) {
						browserIconUri = URI_ICON_BROWSER;
						browser = browserArray[0];
					} else {
						browser = browserArray[i];
					}
					if (browserIconUri != null) {
						if (icons == null) {
							icons = new ArrayList<>();
						}
						icons.add(new Icon(locator, browserIconUri, browser));
						break;
					}
				}
			}
		}
		if (icons != null) {
			post.setIcons(icons);
		}
		JSONArray filesArray = jsonObject.optJSONArray("fileInfos");
		if (filesArray != null && filesArray.length() > 0) {
			Attachment[]  attachments = new Attachment[filesArray.length()];
			for (int i = 0; i < attachments.length; i++) {
				attachments[i] = createFileAttachment(filesArray.getJSONObject(i), locator, boardName);
			}
			post.setAttachments(attachments);
		}
		return post;
	}

	public static Post[] createPosts(JSONObject jsonObject, TumbachChanLocator locator, String boardName)
			throws JSONException {
		Post originalPost = createPost(jsonObject.getJSONObject("opPost"), locator, boardName);
		if (jsonObject.optBoolean("fixed")) {
			originalPost.setSticky(true);
		}
		if (jsonObject.optBoolean("closed")) {
			originalPost.setClosed(true);
		}
		JSONArray jsonArray = jsonObject.optJSONArray("posts");
		if (jsonArray == null) {
			jsonArray = jsonObject.optJSONArray("lastPosts");
		}
		if (jsonArray != null && jsonArray.length() > 0) {
			Post[] posts = new Post[1 + jsonArray.length()];
			posts[0] = originalPost;
			for (int i = 0; i < jsonArray.length(); i++) {
				posts[i + 1] = createPost(jsonArray.getJSONObject(i), locator, boardName);
			}
			return posts;
		} else {
			return new Post[] {originalPost};
		}
	}

	public static Posts createThread(JSONObject jsonObject, TumbachChanLocator locator, String boardName)
			throws JSONException {
		Post[] posts = createPosts(jsonObject, locator, boardName);
		Posts thread = new Posts(posts);
		thread.addPostsCount(jsonObject.getInt("postCount"));
		return thread;
	}

	public static Posts[] createThreads(JSONArray jsonArray, TumbachChanLocator locator, String boardName)
			throws JSONException {
		if (jsonArray == null || jsonArray.length() == 0) {
			return null;
		}
		Posts[] threads = new Posts[jsonArray.length()];
		for (int i = 0; i < jsonArray.length(); i++) {
			threads[i] = createThread(jsonArray.getJSONObject(i), locator, boardName);
		}
		return threads;
	}
}