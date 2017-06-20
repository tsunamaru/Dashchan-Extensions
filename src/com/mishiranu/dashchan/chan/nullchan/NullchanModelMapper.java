package com.mishiranu.dashchan.chan.nullchan;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.model.Attachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class NullchanModelMapper {
	private static final Pattern PATTERN_REFERENCE = Pattern.compile("<a data-post=\"(\\d+)\">&gt;&gt;\\d+</a>");

	private static Uri obtainAttachmentUri(JSONObject jsonObject) throws JSONException {
		if (jsonObject != null) {
			Uri uri = Uri.parse(CommonUtils.getJsonString(jsonObject, "url"));
			String host = uri.getHost();
			if (host.startsWith("s01.") || host.startsWith("s02.")) {
				host = host.substring(0, 4) + "0chan.hk";
			}
			return uri.buildUpon().scheme("https").authority(host).build();
		}
		return null;
	}

	public static Attachment createAttachment(JSONObject jsonObject, NullchanChanLocator locator) {
		JSONObject thumbnailObject = null;
		try {
			thumbnailObject = jsonObject.getJSONObject("images").getJSONObject("thumb_200px");
		} catch (JSONException e1) {
			try {
				thumbnailObject = jsonObject.getJSONObject("images").getJSONObject("thumb_100px");
			} catch (JSONException e2) {
				// Ignore exception
			}
		}
		try {
			jsonObject = jsonObject.getJSONObject("images").getJSONObject("original");
			int width = jsonObject.getInt("width");
			int height = jsonObject.getInt("height");
			int size = (int) (jsonObject.getDouble("size_kb") * 1024f);
			return new FileAttachment().setWidth(width).setHeight(height).setSize(size)
					.setFileUri(locator, obtainAttachmentUri(jsonObject))
					.setThumbnailUri(locator, obtainAttachmentUri(thumbnailObject));
		} catch (JSONException e) {
			return null;
		}
	}

	public static Post createPost(JSONObject jsonObject, NullchanChanLocator locator, String boardName,
			String threadNumber) throws JSONException {
		if (boardName == null) {
			boardName = CommonUtils.getJsonString(jsonObject, "boardDir");
		}
		if (threadNumber == null) {
			threadNumber = CommonUtils.getJsonString(jsonObject, "threadId");
		}
		if (boardName == null || threadNumber == null) {
			throw new JSONException("boardName or threadNumber is empty");
		}
		String postNumber = CommonUtils.optJsonString(jsonObject, "id");
		String parentPostNumber = CommonUtils.optJsonString(jsonObject, "opPostId");
		Post post = new Post();
		post.setThreadNumber(threadNumber);
		post.setPostNumber(postNumber);
		post.setParentPostNumber(parentPostNumber);
		post.setPosterBanned(jsonObject.optBoolean("isUserBanned"));
		post.setTimestamp(jsonObject.getLong("date") * 1000L);
		String message = CommonUtils.optJsonString(jsonObject, "messageHtml");
		if (message != null) {
			message = StringUtils.replaceAll(message, PATTERN_REFERENCE,
					matcher -> "<a href=\"#" + matcher.group(1) + "\">&gt;&gt;" + matcher.group(1) + "</a>");
			String linkToPostNumber = CommonUtils.optJsonString(jsonObject, "parentId");
			if (!StringUtils.isEmpty(linkToPostNumber) && !StringUtils.equals(parentPostNumber, linkToPostNumber)) {
				message = "<a href=\"#" + linkToPostNumber + "\">&gt;&gt;" + linkToPostNumber + "</a><br />" + message;
			}
			post.setComment(message);
		}
		JSONArray attachmentsArray = jsonObject.optJSONArray("attachments");
		if (attachmentsArray != null && attachmentsArray.length() > 0) {
			ArrayList<Attachment> attachments = new ArrayList<>();
			for (int i = 0; i < attachmentsArray.length(); i++) {
				Attachment attachment = createAttachment(attachmentsArray.getJSONObject(i), locator);
				if (attachment != null) {
					attachments.add(attachment);
				}
			}
			post.setAttachments(attachments);
		}
		return post;
	}

	public static Posts createPosts(JSONObject jsonObject, NullchanChanLocator locator) throws JSONException {
		String boardName = CommonUtils.getJsonString(jsonObject.getJSONObject("thread")
				.getJSONObject("board"), "dir");
		String threadNumber = CommonUtils.getJsonString(jsonObject.getJSONObject("thread"), "id");
		JSONArray jsonArray = jsonObject.getJSONArray("posts");
		ArrayList<Post> posts = new ArrayList<>();
		for (int i = 0; i < jsonArray.length(); i++) {
			posts.add(createPost(jsonArray.getJSONObject(i), locator, boardName, threadNumber));
		}
		return new Posts(posts);
	}

	public static ArrayList<Posts> createThreads(JSONObject jsonObject, NullchanChanLocator locator)
			throws JSONException {
		String boardName = CommonUtils.getJsonString(jsonObject.getJSONObject("board"), "dir");
		JSONArray jsonArray = jsonObject.getJSONArray("threads");
		ArrayList<Posts> threads = new ArrayList<>();
		for (int i = 0; i < jsonArray.length(); i++) {
			jsonObject = jsonArray.getJSONObject(i);
			String threadNumber = CommonUtils.getJsonString(jsonObject.getJSONObject("thread"), "id");
			ArrayList<Post> posts = new ArrayList<>();
			Post originalPost = createPost(jsonObject.getJSONObject("opPost"), locator, boardName, threadNumber);
			posts.add(originalPost);
			JSONArray postsArray = jsonObject.optJSONArray("lastPosts");
			if (postsArray != null && postsArray.length() > 0) {
				for (int j = 0; j < postsArray.length(); j++) {
					posts.add(createPost(postsArray.getJSONObject(j), locator, boardName, threadNumber));
				}
			}
			originalPost.setClosed(jsonObject.optBoolean("isLocked"));
			originalPost.setSticky(jsonObject.optBoolean("isPinned"));
			int skippedPosts = jsonObject.optInt("skippedPosts");
			threads.add(new Posts(posts).addPostsCount(posts.size()).addPostsCount(skippedPosts));
		}
		return threads;
	}
}
