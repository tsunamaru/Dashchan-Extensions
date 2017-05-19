package com.mishiranu.dashchan.chan.nullone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class NullonePostsParser {
	private final String source;
	private final NulloneChanConfiguration configuration;
	private final NulloneChanLocator locator;
	private final String boardName;

	private String parent;
	private Posts thread;
	private Post post;
	private FileAttachment attachment;
	private ArrayList<Posts> threads;
	private final ArrayList<Post> posts = new ArrayList<>();

	private boolean headerHandling = false;
	private boolean parentFromRefLink = false;

	private static final Pattern FILE_SIZE = Pattern.compile("\\(([\\d\\.]+)(\\w+)(?: *, *(\\d+)x(\\d+))?" +
			"(?: *, *(.+))? *\\) *$");
	private static final Pattern EMBED = Pattern.compile("data-id=\"(.*?)\" data-site=\"(.*?)\"");
	private static final Pattern NUMBER = Pattern.compile("(\\d+)");

	private static final Pattern DATE = Pattern.compile("(\\d{4}) (\\w+) (\\d{1,2}) (\\d{2}):(\\d{2}):(\\d{2})");
	private static final List<String> MONTHS = Arrays.asList("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг",
			"Сен", "Окт", "Ноя", "Дек");
	private static final TimeZone TIMEZONE_GMT = TimeZone.getTimeZone("Etc/GMT");

	public NullonePostsParser(String source, Object linked, String boardName) {
		this.source = source;
		configuration = ChanConfiguration.get(linked);
		locator = ChanLocator.get(linked);
		this.boardName = boardName;
	}

	public NullonePostsParser(String source, Object linked, String boardName, String parent) {
		this(source, linked, boardName);
		this.parent = parent;
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

	private static final TemplateParser<NullonePostsParser> PARSER = TemplateParser.<NullonePostsParser>builder()
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
	}).equals("span", "class", "admin").content((instance, holder, text) -> holder.post.setCapcode("Admin"))
			.equals("span", "class", "mod").content((instance, holder, text) -> holder.post.setCapcode("Mod"))
			.contains("a", "href", "/res/").open((instance, holder, tagName, attributes) -> {
		if (holder.parentFromRefLink) {
			String href = attributes.get("href");
			String threadNumber = holder.locator.getThreadNumber(Uri.parse(href));
			if (threadNumber != null) {
				holder.post.setParentPostNumber(threadNumber);
			}
		}
		return false;
	}).text((instance, holder, source, start, end) -> {
		if (holder.headerHandling) {
			String text = source.substring(start, end).trim();
			if (text.length() > 0) {
				Matcher matcher = DATE.matcher(text);
				if (matcher.find()) {
					int year = Integer.parseInt(matcher.group(1));
					String monthString = matcher.group(2);
					int month = MONTHS.indexOf(monthString);
					int day = Integer.parseInt(matcher.group(3));
					int hour = Integer.parseInt(matcher.group(4));
					int minute = Integer.parseInt(matcher.group(5));
					int second = Integer.parseInt(matcher.group(6));
					GregorianCalendar calendar = new GregorianCalendar(year, month, day, hour, minute, second);
					calendar.setTimeZone(TIMEZONE_GMT);
					calendar.add(GregorianCalendar.HOUR, -3);
					holder.post.setTimestamp(calendar.getTimeInMillis());
				}
				holder.headerHandling = false;
			}
		}
	}).name("label").close((instance, holder, tagName) -> holder.headerHandling = false)
			.ends("img", "src", "/css/sticky.gif").open((i, h, t, a) -> !h.post.setSticky(true).isSticky())
			.ends("img", "src", "/css/locked.gif").open((i, h, t, a) -> !h.post.setClosed(true).isClosed())
			.equals("span", "class", "filesize").content((instance, holder, text) -> {
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
			String fileName = matcher.group(5);
			holder.attachment.setOriginalName(StringUtils.isEmptyOrWhitespace(fileName) ? null : fileName.trim());
		}
	}).contains("a", "href", "/src/").open((instance, holder, tagName, attributes) -> {
		if (holder.attachment != null) {
			String uriString = holder.convertUriString(attributes.get("href"));
			holder.attachment.setFileUri(holder.locator, Uri.parse(uriString));
			holder.post.setAttachments(holder.attachment);
		}
		return false;
	}).equals("img", "class", "thumb").open((instance, holder, tagName, attributes) -> {
		if (holder.attachment != null) {
			String uriString = holder.convertUriString(attributes.get("src"));
			if (uriString != null && !uriString.contains("/inc/filetypes/")) {
				holder.attachment.setThumbnailUri(holder.locator, Uri.parse(uriString));
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
			EmbeddedAttachment attachment = null;
			Matcher matcher = EMBED.matcher(embed);
			if (matcher.find()) {
				String uriString = null;
				String id = matcher.group(1);
				String site = matcher.group(2);
				if ("youtube".equals(site)) {
					uriString = "https://www.youtube.com/watch?v=" + id;
				} else if ("vimeo".equals(site)) {
					uriString = "https://vimeo.com/" + id;
				} else if ("coub".equals(site)) {
					uriString = "https://coub.com/view/" + id;
				}
				if (uriString != null) {
					attachment = EmbeddedAttachment.obtain(uriString);
					if (attachment == null) {
						if ("coub".equals(site)) {
							attachment = new EmbeddedAttachment(Uri.parse(uriString), null, "COUB",
									EmbeddedAttachment.ContentType.VIDEO, false, null);
						}
					}
				}
			} else {
				index = embed.indexOf("vimeo.com");
				if (index >= 0) {
					index = embed.indexOf("clip_id=");
					if (index >= 0) {
						int end = embed.indexOf('&', index);
						if (end > index) {
							String uriString = "https://vimeo.com/" + embed.substring(index + 8, end);
							attachment = EmbeddedAttachment.obtain(uriString);
						}
					}
				}
			}
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
		text = holder.removePrettyprintBreaks(text);
		text = CommonUtils.restoreCloudFlareProtectedEmails(text);
		holder.post.setComment(text);
		holder.posts.add(holder.post);
		holder.post = null;
		holder.attachment = null;
	}).equals("span", "class", "omittedposts").open((instance, holder, tagName, attributes) -> holder.threads != null)
			.content((instance, holder, text) -> {
		Matcher matcher = NUMBER.matcher(StringUtils.clearHtml(text));
		if (matcher.find()) {
			holder.thread.addPostsCount(Integer.parseInt(matcher.group(1)));
			if (matcher.find()) {
				holder.thread.addPostsWithFilesCount(Integer.parseInt(matcher.group(1)));
			}
		}
	}).equals("div", "class", "logo").content((instance, holder, text) -> {
		text = StringUtils.clearHtml(text).trim();
		text = text.substring(5 + holder.boardName.length()); // Skip "/boardname/ - "
		holder.configuration.storeBoardTitle(holder.boardName, text);
	}).equals("table", "border", "1").content((instance, holder, text) -> {
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

	private String removePrettyprintBreaks(String string) {
		// brs inside pre.prettyprint has "display: none" style
		// Also br after pre will get "display: none" with javascript
		// Dashchan doesn't handle css styles and js, so hide these tags manually
		StringBuilder builder = new StringBuilder(string);
		int from = 0;
		while (true) {
			int index1 = builder.indexOf("<pre class=\"prettyprint\"", from);
			int index2 = builder.indexOf("</pre>", from);
			if (index2 > index1 && index1 >= 0) {
				while (true) {
					int brIndex = builder.indexOf("<br", index1 + 1);
					if (brIndex > index1) {
						int brEndIndex = builder.indexOf(">", brIndex) + 1;
						builder.delete(brIndex, brEndIndex);
						if (brIndex >= index2) {
							break;
						}
						index2 -= brEndIndex - brIndex;
					} else {
						break;
					}
				}
				from = index2 + 6;
				if (from >= builder.length()) {
					break;
				}
			} else {
				break;
			}
		}
		return builder.toString();
	}
}
