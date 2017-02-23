package com.mishiranu.dashchan.chan.nullnyan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.net.Uri;

import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

@SuppressLint("SimpleDateFormat")
public class NullnyanPostsParser {
	private final String source;
	private final NullnyanChanConfiguration configuration;
	private final NullnyanChanLocator locator;
	private final String boardName;

	private String parent;
	private Posts thread;
	private Post post;
	private FileAttachment attachment;
	private ArrayList<Posts> threads;
	private final ArrayList<Post> posts = new ArrayList<>();

	private boolean headerHandling = false;
	private boolean parentFromDataParent = false;
	private boolean parentFromRefLink = false;

	private static final SimpleDateFormat[] DATE_FORMATS;

	static {
		DATE_FORMATS = new SimpleDateFormat[2];
		DATE_FORMATS[0] = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.US);
		DATE_FORMATS[0].setTimeZone(TimeZone.getTimeZone("GMT+3"));
		DATE_FORMATS[1] = new SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.US);
		DATE_FORMATS[1].setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	private static final Pattern FILE_SIZE = Pattern.compile("\\[([\\d\\.]+)(\\w+), (\\d+)x(\\d+)(?:, (.+))?\\]");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	public NullnyanPostsParser(String source, Object linked, String boardName) {
		this.source = source;
		configuration = NullnyanChanConfiguration.get(linked);
		locator = NullnyanChanLocator.get(linked);
		this.boardName = boardName;
	}

	private void closeThread() {
		if (thread != null) {
			thread.setPosts(posts);
			thread.addPostsCount(posts.size());
			threads.add(thread);
			posts.clear();
		}
	}

	private void closePost() {
		if (post != null) {
			posts.add(post);
			attachment = null;
			post = null;
		}
	}

	public ArrayList<Posts> convertThreads() throws ParseException {
		threads = new ArrayList<>();
		PARSER.parse(source, this);
		closePost();
		closeThread();
		return threads;
	}

	public ArrayList<Post> convertPosts() throws ParseException {
		PARSER.parse(source, this);
		closePost();
		return posts;
	}

	public ArrayList<Post> convertSearchPosts() throws ParseException {
		parentFromRefLink = true;
		PARSER.parse(source, this);
		closePost();
		return posts;
	}

	public Post convertSinglePost() throws ParseException {
		parentFromDataParent = true;
		PARSER.parse(source, this);
		closePost();
		return !posts.isEmpty() ? posts.get(0) : null;
	}

	private static final TemplateParser<NullnyanPostsParser> PARSER = new TemplateParser<NullnyanPostsParser>()
			.starts("div", "id", "p").open((instance, holder, tagName, attributes) -> {
		holder.closePost();
		String number = attributes.get("id").substring(1);
		holder.post = new Post();
		holder.post.setPostNumber(number);
		if ("OP".equalsIgnoreCase(attributes.get("class"))) {
			holder.parent = number;
			if (holder.threads != null) {
				holder.closeThread();
				holder.thread = new Posts();
			}
		} else {
			holder.post.setParentPostNumber(holder.parent);
		}
		return false;
	}).contains("a", "data-parent", "").open((instance, holder, tagName, attributes) -> {
		if (holder.parentFromDataParent) {
			holder.post.setParentPostNumber(attributes.get("data-parent"));
		}
		return false;
	}).equals("span", "class", "reflink").open((instance, holder, tagName, attributes) -> holder.parentFromRefLink)
			.content((instance, holder, text) -> {
		int start = text.indexOf("<a href=\"");
		int end = text.indexOf('"', start + 9);
		if (end > start && start >= 0) {
			Uri uri = Uri.parse(text.substring(start + 9, end));
			String threadNumber = holder.locator.getThreadNumber(uri);
			if (threadNumber != null) {
				holder.post.setParentPostNumber(threadNumber);
			}
		}
	}).contains("div", "class", "managePost").open((instance, holder, tagName, attributes) -> {
		holder.headerHandling = true;
		return true; // Skip content
	}).contains("span", "class", "filesize").open((instance, nullnyanPostsParser, s, attributes) -> {
		String id = attributes.get("id");
		return id == null || !id.startsWith("exembed");
	}).content((instance, holder, text) -> {
		if (holder.attachment == null) {
			holder.attachment = new FileAttachment();
		}
		text = StringUtils.clearHtml(text);
		Matcher matcher = FILE_SIZE.matcher(text);
		if (matcher.find()) {
			float size = Float.parseFloat(matcher.group(1));
			String dim = matcher.group(2);
			if ("KB".equals(dim)) {
				size *= 1024f;
			} else if ("MB".equals(dim)) {
				size *= 1024f * 1024f;
			}
			int width = Integer.parseInt(matcher.group(3));
			int height = Integer.parseInt(matcher.group(4));
			String originalName = matcher.group(5);
			holder.attachment.setSize((int) size);
			holder.attachment.setWidth(width);
			holder.attachment.setHeight(height);
			holder.attachment.setOriginalName(StringUtils.isEmptyOrWhitespace(originalName) ? null : originalName);
		}
	}).name("a").open((instance, holder, tagName, attributes) -> {
		if (holder.attachment != null) {
			String href = attributes.get("href");
			if (href != null && (href.startsWith("src/") || href.contains("/src/"))) {
				if (href.startsWith("../")) {
					href = href.substring(3);
				}
				href = "/" + holder.boardName + "/" + href;
				holder.attachment.setFileUri(holder.locator, holder.locator.buildPath(href));
				holder.post.setAttachments(holder.attachment);
			}
		} else {
			String id = attributes.get("id");
			if (id != null && id.startsWith("tiembed")) {
				String href = attributes.get("href");
				if (href.contains("youtube")) {
					href = href.replace("youtube-nocookie.com", "youtube.com").replace("/embed/", "/watch?v=");
				}
				EmbeddedAttachment attachment = EmbeddedAttachment.obtain(href);
				if (attachment != null) {
					holder.post.setAttachments(attachment);
				}
			}
		}
		return false;
	}).starts("img", "src", "thumb/").contains("img", "src", "/thumb/").open((i, holder, tagName, attributes) -> {
		String src = attributes.get("src");
		if (src.startsWith("../")) {
			src = src.substring(3);
		}
		src = "/" + holder.boardName + "/" + src;
		if (holder.attachment == null) {
			holder.attachment = new FileAttachment();
		}
		holder.attachment.setThumbnailUri(holder.locator, holder.locator.buildPath(src));
		return false;
	}).equals("span", "class", "filetitle").content((instance, holder, text) -> {
		holder.post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).name("a").open((instance, holder, tagName, attributes) -> {
		if (holder.headerHandling) {
			String href = attributes.get("href");
			if (href != null && href.startsWith("mailto:")) {
				if ("mailto:sage".equalsIgnoreCase(href)) {
					holder.post.setSage(true);
				} else {
					holder.post.setEmail(StringUtils.clearHtml(href));
				}
			}
		}
		return false;
	}).equals("span", "class", "postername").content((instance, holder, text) -> {
		holder.post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).equals("span", "class", "postertrip").content((instance, holder, text) -> {
		holder.post.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).name("time").content((instance, holder, text) -> {
		text = text.trim();
		if (text.contains("/")) {
			// e.g. "Mon 16/05/16 21:13:18" -> "16/05/16 21:13:18"
			text = text.substring(text.indexOf(' ') + 1);
		} else {
			int index = text.indexOf(", ");
			if (index >= 0) {
				// e.g. "Saturday, 9 July 2016 05:05:10" -> "9 July 2016 05:05:10"
				text = text.substring(index + 2);
			}
		}
		for (SimpleDateFormat dateFormat : DATE_FORMATS) {
			try {
				holder.post.setTimestamp(dateFormat.parse(text).getTime());
				break;
			} catch (java.text.ParseException e) {
				// Ignore exception
			}
		}
		holder.headerHandling = false;
	}).contains("i", "class", "iconStickied").open((instance, holder, tagName, attributes) -> {
		holder.post.setSticky(true);
		return false;
	}).contains("i", "class", "iconLock").open((instance, holder, tagName, attributes) -> {
		holder.post.setClosed(true);
		return false;
	}).equals("div", "class", "message").content((instance, holder, text) -> {
		int index = text.lastIndexOf("<span class=\"red-text\">(USER WAS BANNED FOR THIS POST)</span>");
		if (index >= 0) {
			text = text.substring(0, index);
			holder.post.setPosterBanned(true);
		}
		// Remove "Text wall" buttons
		text = text.replaceAll("<span class=\"textwall\">.*?<span>", "");
		// Fix links
		text = text.replaceAll("<a class=\"postlink\" href=\"(?:../)?res/",
				"<a class=\"postlink\" href=\"/" + holder.boardName + "/res/");
		holder.post.setComment(text);
	}).equals("span", "class", "omittedposts").content((instance, holder, text) -> {
		if (holder.threads != null) {
			Matcher matcher = NUMBER.matcher(text);
			if (matcher.find()) {
				holder.thread.addPostsCount(Integer.parseInt(matcher.group()));
			}
		}
	}).equals("span", "class", "center").content((instance, holder, text) -> {
		text = StringUtils.clearHtml(text);
		int index = text.indexOf("- ");
		if (index >= 0) {
			text = text.substring(index + 2);
		}
		holder.configuration.storeBoardTitle(holder.boardName, text);
	}).equals("nav", "class", "pagenavigator").content((instance, holder, text) -> {
		text = StringUtils.clearHtml(text.replace("><", "> <"));
		String pagesCount = null;
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			pagesCount = matcher.group();
		}
		if (pagesCount != null) {
			holder.configuration.storePagesCount(holder.boardName, Integer.parseInt(pagesCount) + 1);
		}
	}).prepare();
}