package com.mishiranu.dashchan.chan.candydollchan;

import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class CandydollchanPostsParser {
	private final String source;
	private final CandydollchanChanConfiguration configuration;
	private final CandydollchanChanLocator locator;
	private final String boardName;

	private String parent;
	private Posts thread;
	private Post post;
	private FileAttachment attachment;
	private ArrayList<Posts> threads;
	private final ArrayList<Post> posts = new ArrayList<>();

	private boolean headerHandling = false;

	private static final Pattern FILE_SIZE = Pattern.compile("\\( *([\\d\\.]+)(\\w+)(?: *, *(\\d+)x(\\d+))?" +
			"(?: *, *(.+))? *\\) *$");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private static final SimpleDateFormat DATE_FORMAT;

	static {
		DATE_FORMAT = new SimpleDateFormat("yy/MM/dd(EEE)hh:mm", Locale.US);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT-5"));
	}

	public CandydollchanPostsParser(String source, Object linked, String boardName) {
		this.source = source;
		configuration = CandydollchanChanConfiguration.get(linked);
		locator = CandydollchanChanLocator.get(linked);
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

	public Posts convertPosts() throws ParseException {
		PARSER.parse(source, this);
		return posts.size() > 0 ? new Posts(posts) : null;
	}

	private static final TemplateParser<CandydollchanPostsParser> PARSER =
			new TemplateParser<CandydollchanPostsParser>()
			.starts("div", "id", "thread").open((instance, holder, tagName, attributes) -> {
		String id = attributes.get("id");
		String number = id.substring(6, id.length() - holder.boardName.length());
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
	}).name("label").open((instance, holder, tagName, attributes) -> {
		if (holder.post != null) {
			holder.headerHandling = true;
		}
		return false;
	}).equals("span", "class", "filesize").content((instance, holder, text) -> {
		if (holder.post == null) {
			holder.post = new Post();
		}
		holder.attachment = new FileAttachment();
		text = StringUtils.clearHtml(text);
		Matcher matcher = FILE_SIZE.matcher(text);
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
			String fileName = matcher.group(5);
			holder.attachment.setOriginalName(StringUtils.isEmptyOrWhitespace(fileName) ? null : fileName.trim());
		}
	}).contains("a", "href", "/src/").open((instance, holder, tagName, attributes) -> {
		holder.attachment.setFileUri(holder.locator, Uri.parse(attributes.get("href")));
		holder.post.setAttachments(holder.attachment);
		return false;
	}).equals("img", "class", "thumb").open((instance, holder, tagName, attributes) -> {
		holder.attachment.setThumbnailUri(holder.locator, Uri.parse(attributes.get("src")));
		return false;
	}).starts("input", "name", "del_").open((instance, holder, tagName, attributes) -> {
		if (holder.post == null) {
			holder.post = new Post();
		}
		String number = attributes.get("value");
		holder.post.setPostNumber(number);
		if (holder.parent == null) {
			holder.parent = number;
		}
		return false;
	}).ends("img", "src", "/css/sticky.gif").open((i, h, t, a) -> !h.post.setSticky(true).isSticky())
			.ends("img", "src", "/css/locked.gif").open((i, h, t, a) -> !h.post.setClosed(true).isClosed())
			.equals("span", "class", "filetitle").content((instance, holder, text) -> {
		holder.post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).equals("span", "class", "postername").content((instance, holder, text) -> {
		holder.post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).equals("span", "class", "postertrip").content((instance, holder, text) -> {
		holder.post.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
	}).starts("a", "href", "mailto:").open((instance, holder, tagName, attributes) -> {
		if (holder.headerHandling) {
			String href = attributes.get("href");
			String email = href.substring(7);
			if (email.toLowerCase(Locale.US).equals("sage")) {
				holder.post.setSage(true);
			} else {
				holder.post.setEmail(StringUtils.clearHtml(email));
			}
		}
		return false;
	}).equals("span", "class", "admin").content((instance, holder, text) -> holder.post.setCapcode("Admin"))
			.equals("span", "class", "mod").content((instance, holder, text) -> holder.post.setCapcode("Mod"))
			.text((instance, holder, source, start, end) -> {
		if (holder.headerHandling) {
			String text = source.substring(start, end).trim();
			if (text.length() > 0) {
				try {
					holder.post.setTimestamp(DATE_FORMAT.parse(text).getTime());
				} catch (java.text.ParseException e) {
					// Ignore exception
				}
				holder.headerHandling = false;
			}
		}
	}).name("label").close((instance, holder, tagName) -> holder.headerHandling = false)
			.equals("span", "class", "omittedposts").open((i, h, t, a) -> h.threads != null)
			.content((instance, holder, text) -> {
		text = StringUtils.clearHtml(text);
		Matcher matcher = NUMBER.matcher(text);
		if (matcher.find()) {
			holder.thread.addPostsCount(Integer.parseInt(matcher.group()));
			if (matcher.find()) {
				holder.thread.addPostsWithFilesCount(Integer.parseInt(matcher.group()));
			}
		}
	}).name("blockquote").content((instance, holder, text) -> {
		text = text.trim();
		if (text.startsWith("<span style=\"float: left;\">")) {
			int index = text.indexOf("</span>") + 7;
			String embed = text.substring(0, index);
			if (index + 6 <= text.length()) {
				index += 6;
			}
			text = text.substring(index).trim();
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
		text = CommonUtils.restoreCloudFlareProtectedEmails(text);
		holder.post.setComment(text);
		holder.posts.add(holder.post);
		holder.post = null;
	}).equals("table", "border", "0").content((instance, holder, text) -> {
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