package com.mishuranu.dashchan.chan.moechannel;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.util.Log;

import chan.content.model.Attachment;
import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class DvachModelMapper {
	private static final Pattern PATTERN_BADGE = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.+?/?>");
	private static final Pattern PATTERN_CODE = Pattern.compile("\\[code(?:\\s+lang=.+?)?\\](?:<br ?/?>)*(.+?)" +
			"(?:<br ?/?>)*\\[/code\\]", Pattern.CASE_INSENSITIVE);
	private static final Pattern PATTERN_HASHLINK = Pattern.compile("<a [^<>]*class=\"hashlink\"[^<>]*>");
	private static final Pattern PATTERN_HASHLINK_TITLE = Pattern.compile("title=\"(.*?)\"");


	private static String fixAttachmentPath(String boardName, String path) {
		if (!StringUtils.isEmpty(path)) {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.startsWith("/src/") || path.startsWith("/thumb/")) {
				path = "/" + boardName + path;
			}
			return path;
		} else {
			return null;
		}
	}

	public static FileAttachment createFileAttachment(JSONObject jsonObject, DvachChanLocator locator,
			String boardName, String archiveDate) throws JSONException {
		String file = fixAttachmentPath(boardName, CommonUtils.getJsonString(jsonObject, "path"));
		String thumbnail = fixAttachmentPath(boardName, CommonUtils.optJsonString(jsonObject, "thumbnail"));
		String originalName = StringUtils.nullIfEmpty(CommonUtils.optJsonString(jsonObject, "fullname"));
		Uri fileUri = file != null ? locator.buildPath(archiveDate != null
				? file.replace("/src/", "/arch/" + archiveDate + "/src/") : file) : null;
		Uri thumbnailUri = thumbnail != null ? locator.buildPath(archiveDate != null
				? thumbnail.replace("/thumb/", "/arch/" + archiveDate + "/thumb/") : thumbnail) : null;
		int size = jsonObject.optInt("size") * 1024;
		int width = jsonObject.optInt("width");
		int height = jsonObject.optInt("height");
		return new FileAttachment().setFileUri(locator, fileUri).setThumbnailUri(locator, thumbnailUri)
				.setSize(size).setWidth(width).setHeight(height).setOriginalName(originalName);
	}

	public static Post createPost(JSONObject jsonObject, DvachChanLocator locator, String boardName,
			String archiveDate, boolean sageEnabled) throws JSONException {
		Post post = new Post();
		String num = CommonUtils.getJsonString(jsonObject, "num");
		String parent = CommonUtils.getJsonString(jsonObject, "parent");
		post.setPostNumber(num);
		if (!"0".equals(parent)) {
			post.setParentPostNumber(parent);
		}
		if (jsonObject.optInt("op") != 0) {
			post.setOriginalPoster(true);
		}
		if (jsonObject.optInt("sticky") != 0) {
			post.setSticky(true);
		}
		if (jsonObject.optInt("closed") != 0) {
			post.setClosed(true);
		}
		if (jsonObject.optInt("endless") != 0) {
			post.setCyclical(true);
		}
		int banned = jsonObject.optInt("banned");
		if (banned == 1) {
			post.setPosterBanned(true);
		} else if (banned == 2) {
			post.setPosterWarned(true);
		}
		post.setTimestamp(jsonObject.optLong("timestamp") * 1000L);
		String subject = CommonUtils.optJsonString(jsonObject, "subject");
		if (!StringUtils.isEmpty(subject)) {
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		String comment = CommonUtils.getJsonString(jsonObject, "comment");
		if (!StringUtils.isEmpty(comment)) {
			comment = comment.replace(" (OP)</a>", "</a>");
			comment = comment.replace("&#47;", "/");
		}
		comment = StringUtils.replaceAll(comment, PATTERN_HASHLINK, matcher -> {
			String title = null;
			Matcher matcher2 = PATTERN_HASHLINK_TITLE.matcher(matcher.group());
			if (matcher2.find()) {
				title = matcher2.group(1);
			}
			if (title != null) {
				Uri uri = locator.createCatalogSearchUri(boardName, title);
				String encodedUri = uri.toString().replace("&", "&amp;").replace("\"", "&quot;");
				return "<a href=\"" + encodedUri + "\">";
			} else {
				return matcher.group();
			}
		});
		if ("pr".equals(boardName) && comment != null) {
			comment = PATTERN_CODE.matcher(comment).replaceAll("<code>$1</code>");
		}
		post.setComment(comment);
		// TODO Remove this after server side fix of subjects
		if (post.getParentPostNumber() == null && post.getSubject() != null) {
			String clearComment = StringUtils.clearHtml(comment).replaceAll("\\s", "");
			if (clearComment.startsWith(post.getSubject().replaceAll("\\s", ""))) {
				post.setSubject(null);
			}
		}
		ArrayList<Attachment> attachments = null;
		try {
			JSONArray filesArray = jsonObject.getJSONArray("files");
			if (filesArray.length() > 0) {
				for (int i = 0, length = filesArray.length(); i < length; i++) {
					if (attachments == null) {
						attachments = new ArrayList<>();
					}
					attachments.add(createFileAttachment(filesArray.getJSONObject(i), locator, boardName, archiveDate));
				}
			}
		} catch (JSONException e) {
			attachments = null;
		}
		post.setAttachments(attachments);

		String name = CommonUtils.optJsonString(jsonObject, "name");
		String tripcode = CommonUtils.optJsonString(jsonObject, "trip");
		String email = CommonUtils.optJsonString(jsonObject, "email");
		boolean sage = email.equals("sage");
		String userAgentData = null;
		if (sage) {
			email = null;
			post.setSage(true);
		}
		String identifier = null;
		if (!StringUtils.isEmpty(name)) {
			int index = "s".equals(boardName) ? name.indexOf("&nbsp;<span style=\"color:rgb(164,164,164);\">") : -1;
			if (index >= 0) {
				userAgentData = name.substring(index + 44);
				name = name.substring(0, index);
			}
			name = StringUtils.clearHtml(name).trim();
			index = name.indexOf(" ID: ");
			if (index >= 0) {
				identifier = name.substring(index + 5).replaceAll(" +", " ");
				name = name.substring(0, index);
				if ("Heaven".equals(identifier)) {
					identifier = null;
					post.setSage(true);
				}
			}
		}
		String capcode = null;
		if (!StringUtils.isEmpty(tripcode)) {
			if ("!!%adm%!!".equals(tripcode)) {
				capcode = "Admin";
			} else if ("!!%mod%!!".equals(tripcode)) {
				capcode = "Mod";
			}
			if (capcode != null) {
				tripcode = null;
			} else {
				tripcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(tripcode).trim());
			}
		}
		post.setName(name);
		post.setIdentifier(identifier);
		post.setTripcode(tripcode);
		post.setCapcode(capcode);
		post.setEmail(email);
		return post;
	}

	public static Post[] createPosts(JSONArray jsonArray, DvachChanLocator locator, String boardName,
			String archiveDate, boolean sageEnabled) throws JSONException {
		if (jsonArray.length() > 0) {
			Post[] posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++) {
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName, archiveDate, sageEnabled);
			}
			if (archiveDate != null) {
				posts[0].setArchived(true);
			}
			return posts;
		}
		return null;
	}

	public static Posts createThread(JSONObject jsonObject, DvachChanLocator locator, String boardName,
			boolean sageEnabled) throws JSONException {
		int postsCount = jsonObject.optInt("posts_count");
		int postsWithFilesCount = Math.max(jsonObject.optInt("files_count"), jsonObject.optInt("images_count"));
		Post[] posts;
		if (jsonObject.has("posts")) {
			JSONArray jsonArray = jsonObject.getJSONArray("posts");
			try {
				jsonArray = jsonArray.getJSONArray(0);
			} catch (JSONException e) {
				// Ignore exception
			}
			posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++) {
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName, null, sageEnabled);
			}
		} else {
			posts = new Post[] {createPost(jsonObject, locator, boardName, null, sageEnabled)};
		}
		if (posts.length > 0 && posts[0].getAttachmentsCount() > 0) {
			postsWithFilesCount++;
		}
		postsCount += posts.length;
		return new Posts(posts).addPostsCount(postsCount).addPostsWithFilesCount(postsWithFilesCount);
	}

	public static Post createWakabaArchivePost(JSONObject jsonObject, DvachChanLocator locator, String boardName)
			throws JSONException {
		Post post = new Post();
		post.setArchived(true);
		String num = CommonUtils.getJsonString(jsonObject, "num");
		String parent = CommonUtils.getJsonString(jsonObject, "parent");
		post.setPostNumber(num);
		if (!"0".equals(parent)) {
			post.setParentPostNumber(parent);
		}
		if (jsonObject.optInt("op") != 0) {
			post.setOriginalPoster(true);
		}
		if (jsonObject.optInt("sticky") != 0) {
			post.setSticky(true);
		}
		if (jsonObject.optInt("closed") != 0) {
			post.setClosed(true);
		}
		int banned = jsonObject.optInt("banned");
		if (banned == 1) {
			post.setPosterBanned(true);
		} else if (banned == 2) {
			post.setPosterWarned(true);
		}
		post.setComment(CommonUtils.getJsonString(jsonObject, "comment"));
		String name = CommonUtils.optJsonString(jsonObject, "name");
		if (!StringUtils.isEmpty(name)) {
			post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim()));
		}
		String subject = CommonUtils.optJsonString(jsonObject, "suject");
		if (!StringUtils.isEmpty(subject)) {
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		post.setTimestamp(jsonObject.optLong("timestamp") * 1000L);
		ArrayList<Attachment> attachments = null;
		String image = CommonUtils.optJsonString(jsonObject, "image");
		if (!StringUtils.isEmpty(image)) {
			String thumbnail = CommonUtils.optJsonString(jsonObject, "thumbnail");
			FileAttachment attachment = new FileAttachment();
			attachment.setFileUri(locator, locator.buildPath(boardName, "arch", "wakaba", image));
			if (!StringUtils.isEmpty(thumbnail)) {
				attachment.setThumbnailUri(locator, locator.buildPath(boardName, "arch", "wakaba", thumbnail));
			}
			attachment.setWidth(jsonObject.optInt("width"));
			attachment.setHeight(jsonObject.optInt("height"));
			attachment.setSize(jsonObject.optInt("size") * 1024);
			if (attachments == null) {
				attachments = new ArrayList<>();
			}
			attachments.add(attachment);
		}
		String video = CommonUtils.optJsonString(jsonObject, "video");
		if (!StringUtils.isEmpty(video)) {
			EmbeddedAttachment attachment = EmbeddedAttachment.obtain(video);
			if (attachment != null) {
				if (attachments == null) {
					attachments = new ArrayList<>();
				}
				attachments.add(attachment);
			}
		}
		if (attachments != null) {
			post.setAttachments(attachments);
		}
		return post;
	}
}
