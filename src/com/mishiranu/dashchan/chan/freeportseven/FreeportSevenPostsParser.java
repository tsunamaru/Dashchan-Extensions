package com.mishiranu.dashchan.chan.freeportseven;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class FreeportSevenPostsParser {
	private final String source;
	private final FreeportSevenChanConfiguration configuration;
	private final FreeportSevenChanLocator locator;
	private final String boardName;

	private String parent;
	private Posts thread;
	private Post post;
	private FileAttachment attachment;
	private ArrayList<Posts> threads;
	private final ArrayList<Post> posts = new ArrayList<>();

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	public FreeportSevenPostsParser(String source, Object linked, String boardName) {
		this.source = source;
		configuration = FreeportSevenChanConfiguration.get(linked);
		locator = FreeportSevenChanLocator.get(linked);
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

	public ArrayList<Posts> convertThreads() throws ParseException {
		threads = new ArrayList<>();
		PARSER.parse(source, this);
		closeThread();
		return threads;
	}

	public ArrayList<Post> convertPosts() throws ParseException {
		PARSER.parse(source, this);
		return posts;
	}

	private static final TemplateParser<FreeportSevenPostsParser> PARSER =
			new TemplateParser<FreeportSevenPostsParser>()
			.equals("div", "class", "opening post").equals("div", "class", "post reply")
			.open((instance, holder, tagName, attributes) -> {
		boolean originalPost = "opening post".equals(attributes.get("class"));
		String postNumber = attributes.get(" id").substring(1);
		holder.post = new Post();
		holder.post.setPostNumber(postNumber);
		try {
			holder.post.setTimestamp(DATE_FORMAT.parse(attributes.get("data-time")).getTime());
		} catch (java.text.ParseException e) {
			// Ignore exception
		}
		if (originalPost) {
			holder.parent = postNumber;
			if (holder.threads != null) {
				holder.closeThread();
				holder.thread = new Posts();
			}
		} else {
			holder.post.setParentPostNumber(holder.parent);
		}
		return false;
	}).starts("a", "class", "title").content((instance, holder, text) -> {
		holder.post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).contains("a", "class", "fancybox").open((instance, holder, tagName, attributes) -> {
		String uriString = attributes.get("href");
		if (attributes.get("class").contains("fancybox.iframe")) {
			EmbeddedAttachment attachment = EmbeddedAttachment.obtain(uriString);
			if (attachment != null) {
				holder.post.setAttachments(attachment);
			}
		} else {
			holder.attachment = new FileAttachment();
			holder.attachment.setFileUri(holder.locator, holder.locator.buildPath(uriString));
			holder.post.setAttachments(holder.attachment);
		}
		return false;
	}).contains("img", "src", "/uploads/").open((instance, holder, tagName, attributes) -> {
		if (holder.attachment != null) {
			holder.attachment.setThumbnailUri(holder.locator, holder.locator.buildPath(attributes.get("src")));
		}
		return false;
	}).equals("div", "class", "text").content((instance, holder, text) -> {
		holder.post.setComment(text);
		holder.posts.add(holder.post);
		holder.attachment = null;
		holder.post = null;
	}).equals("div", "class", "omitted").content((instance, holder, text) -> {
		if (holder.threads != null) {
			Matcher matcher = NUMBER.matcher(text);
			if (matcher.find()) {
				holder.thread.addPostsCount(Integer.parseInt(matcher.group()));
			}
		}
	}).equals("nav", "class", "pagination").content((instance, holder, text) -> {
		String pagesCount = "1";
		Matcher matcher = NUMBER.matcher(StringUtils.clearHtml(text));
		while (matcher.find()) {
			pagesCount = matcher.group();
		}
		try {
			holder.configuration.storePagesCount(holder.boardName, Integer.parseInt(pagesCount));
		} catch (NumberFormatException e) {
			// Ignore exception
		}
	}).prepare();
}