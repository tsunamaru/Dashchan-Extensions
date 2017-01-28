package com.mishiranu.dashchan.chan.xyntach;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class XyntachPostsParser {
	private final String source;
	private final XyntachChanConfiguration configuration;
	private final XyntachChanLocator locator;
	private final String boardName;

	private String parent;
	private Posts thread;
	private Post post;
	private FileAttachment attachment;
	private ArrayList<Posts> threads;
	private final ArrayList<Post> posts = new ArrayList<>();

	private boolean headerHandling = false;
	private boolean parentFromRefLink = false;

	private static final SimpleDateFormat DATE_FORMAT;

	static {
		DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	private static final Pattern FILE_SIZE = Pattern.compile("\\(([\\d\\.]+)(\\w+)(?: *, *(\\d+)x(\\d+))?");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	public XyntachPostsParser(String source, Object linked, String boardName) {
		this.source = source;
		configuration = XyntachChanConfiguration.get(linked);
		locator = XyntachChanLocator.get(linked);
		this.boardName = boardName;
	}

	private void closeThread() {
		if (thread != null) {
			thread.setPosts(posts);
			thread.addPostsCount(posts.size());
			int postsWithFilesCount = 0;
			for (Post post : posts) {
				postsWithFilesCount += post.getAttachmentsCount();
			}
			thread.addPostsWithFilesCount(postsWithFilesCount);
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

	public Post convertSinglePost() throws ParseException {
		parentFromRefLink = true;
		PARSER.parse(source, this);
		return posts.size() > 0 ? posts.get(0) : null;
	}

	private String convertUriString(String uriString) {
		if (uriString != null) {
			int index = uriString.indexOf("://");
			if (index > 0) {
				uriString = uriString.substring(uriString.indexOf('/', index + 3));
			}
		}
		return uriString;
	}

	private static final TemplateParser<XyntachPostsParser> PARSER = new TemplateParser<XyntachPostsParser>()
			.starts("div", "id", "thread").open((instance, holder, tagName, attributes) -> {
		String id = attributes.get("id");
		String number = id.substring(6, id.length() -
				holder.boardName.substring(holder.boardName.indexOf('-') + 1).length());
		Post post = new Post();
		post.setPostNumber(number);
		holder.parent = number;
		holder.post = post;
		if (holder.threads != null) {
			holder.closeThread();
			holder.thread = new Posts();
		}
		return false;
	}).starts("td", "id", "reply").open((instance, holder, tagName, attributes) -> {
		String number = attributes.get("id").substring(5);
		Post post = new Post();
		post.setParentPostNumber(holder.parent);
		post.setPostNumber(number);
		holder.post = post;
		return false;
	}).equals("a", "name", "s").open((instance, holder, tagName, attributes) -> {
		if (holder.post == null) {
			holder.post = new Post();
		}
		return false;
	}).contains("a", "href", "/res/").open((instance, holder, tagName, attributes) -> {
		if (holder.post != null) {
			boolean hasPostNumber = holder.post.getPostNumber() != null;
			boolean hasParentPostNumber = holder.post.getParentPostNumber() != null && !holder.parentFromRefLink;
			if (!hasPostNumber || !hasParentPostNumber) {
				String href = attributes.get("href");
				Uri uri = Uri.parse(href);
				String threadNumber = holder.locator.getThreadNumber(uri);
				if (!hasPostNumber) {
					holder.post.setPostNumber(holder.locator.getPostNumber(uri));
				}
				if (!hasParentPostNumber && !threadNumber.equals(holder.post.getPostNumber())) {
					holder.post.setParentPostNumber(threadNumber);
				}
			}
		}
		return false;
	}).name("label").open((instance, holder, tagName, attributes) -> {
		holder.headerHandling = holder.post != null;
		return false;
	}).equals("span", "class", "filetitle").content((instance, holder, text) -> {
		holder.post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).equals("span", "class", "postername").content((instance, holder, text) -> {
		holder.post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).equals("span", "class", "postertrip").content((instance, holder, text) -> {
		holder.post.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).equals("span", "class", "hand").content((instance, holder, text) -> {
		holder.post.setIdentifier(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).text((instance, holder, source, start, end) -> {
		if (holder.headerHandling) {
			String text = source.substring(start, end).trim();
			if (text.length() > 0 && (text = StringUtils.clearHtml(text).trim()).length() > 0) {
				int index1 = text.indexOf('(');
				int index2 = text.indexOf(')');
				if (index2 > index1 && index1 > 0) {
					// Remove week in brackets
					text = text.substring(0, index1 - 1) + text.substring(index2 + 1);
				}
				try {
					holder.post.setTimestamp(DATE_FORMAT.parse(text).getTime());
				} catch (java.text.ParseException e) {
					// Ignore exception
				}
				holder.headerHandling = false;
			}
		}
	}).ends("img", "src", "/css/sticky.gif").open((i, h, t, a) -> !h.post.setSticky(true).isSticky())
			.ends("img", "src", "/css/locked.gif").open((i, h, t, a) -> !h.post.setClosed(true).isClosed())
			.equals("div", "class", "filesize").content((instance, holder, text) -> {
		holder.attachment = new FileAttachment();
		Matcher matcher = FILE_SIZE.matcher(StringUtils.clearHtml(text));
		if (matcher.find()) {
			float size = Float.parseFloat(matcher.group(1));
			String dim = matcher.group(2);
			if ("KB".equals(dim)) {
				size *= 1024;
			} else if ("MB".equals(dim)) {
				size *= 1024 * 1024;
			}
			holder.attachment.setSize((int) size);
			if (matcher.group(3) != null) {
				holder.attachment.setWidth(Integer.parseInt(matcher.group(3)));
				holder.attachment.setHeight(Integer.parseInt(matcher.group(4)));
			}
		}
	}).contains("a", "href", "/src/").open((instance, holder, tagName, attributes) -> {
		if (holder.attachment != null) {
			String path = holder.convertUriString(attributes.get("href"));
			holder.attachment.setFileUri(holder.locator, holder.locator.buildPath(path));
			holder.post.setAttachments(holder.attachment);
		}
		return false;
	}).equals("img", "class", "thumb").open((instance, holder, tagName, attributes) -> {
		if (holder.attachment != null) {
			String path = holder.convertUriString(attributes.get("src"));
			if (path != null && !path.contains("/inc/filetypes/")) {
				holder.attachment.setThumbnailUri(holder.locator, holder.locator.buildPath(path));
			}
		}
		return false;
	}).name("blockquote").content((instance, holder, text) -> {
		text = text.trim();
		if (text.startsWith("<span style=\"float: left;\">")) {
			int index = text.indexOf("</span>") + 7;
			String embed = text.substring(0, index);
			if (index + 6 <= text.length()) {
				index += 6;
			}
			text = text.substring(index).trim();
			embed = embed.replace("youtube-nocookie", "youtube");
			EmbeddedAttachment attachment = EmbeddedAttachment.obtain(embed);
			if (attachment != null) {
				holder.post.setAttachments(attachment);
			}
		}
		int index = text.lastIndexOf("<div class=\"abbrev\">");
		if (index >= 0) {
			text = text.substring(0, index).trim();
		}
		index = text.lastIndexOf("<font color=\"#FF0000\">");
		if (index >= 0) {
			String message = text.substring(index);
			text = text.substring(0, index);
			if (message.contains("USER WAS BANNED FOR THIS POST")) {
				holder.post.setPosterBanned(true);
			}
		}
		holder.post.setComment(text);
		holder.posts.add(holder.post);
		holder.post = null;
		holder.attachment = null;
	}).equals("span", "class", "omittedposts").open((instance, holder, tagName, attributes) -> holder.threads != null)
			.content((instance, holder, text) -> {
		Matcher matcher = NUMBER.matcher(StringUtils.clearHtml(text));
		if (matcher.find()) {
			holder.thread.addPostsCount(Integer.parseInt(matcher.group()));
			if (matcher.find()) {
				holder.thread.addPostsWithFilesCount(Integer.parseInt(matcher.group()));
			}
		}
	}).equals("div", "class", "logo").content((instance, holder, text) -> {
		int index = text.indexOf("<span class=\"BoardName\">");
		if (index >= 0) {
			text = text.substring(0, index);
		}
		text = StringUtils.clearHtml(text).trim();
		holder.configuration.storeBoardTitle(holder.boardName, text);
	}).equals("div", "class", "_subnav").content((instance, holder, text) -> {
		text = StringUtils.clearHtml(text);
		int index1 = text.lastIndexOf('[');
		int index2 = text.lastIndexOf(']');
		if (index1 >= 0 && index2 > index1) {
			text = text.substring(index1 + 1, index2);
			try {
				int pagesCount = Integer.parseInt(text) + 1;
				holder.configuration.storePagesCount(holder.boardName, pagesCount);
			} catch (NumberFormatException e) {
				// Ignore exception
			}
		}
	}).prepare();
}