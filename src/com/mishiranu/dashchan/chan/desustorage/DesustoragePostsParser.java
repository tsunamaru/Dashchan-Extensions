package com.mishiranu.dashchan.chan.desustorage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class DesustoragePostsParser implements GroupParser.Callback {
	private final String source;
	private final DesustorageChanLocator locator;

	private boolean needResTo = false;

	private String resTo;
	private Posts thread;
	private Post post;
	private FileAttachment attachment;
	private ArrayList<Posts> threads;
	private final ArrayList<Post> posts = new ArrayList<>();

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_FILE_SIZE = 1;
	private static final int EXPECT_SUBJECT = 2;
	private static final int EXPECT_NAME = 3;
	private static final int EXPECT_TRIPCODE = 4;
	private static final int EXPECT_IDENTIFIER = 5;
	private static final int EXPECT_COMMENT = 6;
	private static final int EXPECT_OMITTED_POSTS = 7;
	private static final int EXPECT_OMITTED_IMAGES = 8;

	private int expect = EXPECT_NONE;
	private boolean originalPostFileStart = false;

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZZZZZ", Locale.US);

	private static final Pattern FILE_SIZE = Pattern.compile("(\\d+)(\\w+), (\\d+)x(\\d+)(?:, (.*))?");
	private static final Pattern FLAG = Pattern.compile("flag-([a-z]+)");

	public DesustoragePostsParser(String source, Object linked) {
		this.source = source;
		locator = ChanLocator.get(linked);
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
		GroupParser.parse(source, this);
		closeThread();
		return threads;
	}

	public Posts convertPosts(Uri threadUri) throws ParseException {
		GroupParser.parse(source, this);
		return posts.size() > 0 ? new Posts(posts).setArchivedThreadUri(threadUri) : null;
	}

	public ArrayList<Post> convertSearch() throws ParseException {
		needResTo = true;
		GroupParser.parse(source, this);
		return posts;
	}

	private void ensureFile() {
		if (attachment == null) {
			attachment = new FileAttachment();
			post.setAttachments(attachment);
		}
	}

	private String convertImageSrc(String src) {
		int index = src.indexOf("/", src.indexOf("//") + 2);
		return index >= 0 ? src.substring(index) : null;
	}

	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException {
		if ("article".equals(tagName)) {
			String id = parser.getAttr(attrs, "id");
			if (id != null) {
				String cssClass = parser.getAttr(attrs, "class");
				if (cssClass != null) {
					if (cssClass.contains("thread")) {
						Post post = new Post();
						post.setPostNumber(id);
						resTo = id;
						this.post = post;
						attachment = null;
						if (threads != null) {
							closeThread();
							thread = new Posts();
						}
					} else if (cssClass.contains("post")) {
						Post post = new Post();
						post.setParentPostNumber(resTo);
						post.setPostNumber(id);
						this.post = post;
						attachment = null;
					}
				}
			}
		} else if ("span".equals(tagName)) {
			String cssClass = parser.getAttr(attrs, "class");
			if ("post_author".equals(cssClass) && post != null) {
				expect = EXPECT_NAME;
				return true;
			} else if ("post_tripcode".equals(cssClass) && post != null) {
				expect = EXPECT_TRIPCODE;
				return true;
			} else if ("poster_hash".equals(cssClass) && post != null) {
				expect = EXPECT_IDENTIFIER;
				return true;
			} else if ("omitted_posts".equals(cssClass)) {
				expect = EXPECT_OMITTED_POSTS;
				return true;
			} else if ("omitted_images".equals(cssClass)) {
				expect = EXPECT_OMITTED_IMAGES;
				return true;
			} else if ("post_file_metadata".equals(cssClass)) {
				ensureFile();
				expect = EXPECT_FILE_SIZE;
				return true;
			} else if (cssClass != null && cssClass.contains("flag")) {
				Matcher matcher = FLAG.matcher(cssClass);
				if (matcher.find()) {
					String country = matcher.group(1);
					Uri uri = locator.buildPathWithHost("s.4cdn.org", "image", "country",
							country.toLowerCase(Locale.US) + ".gif");
					String title = StringUtils.clearHtml(parser.getAttr(attrs, "title"));
					post.setIcons(new Icon(locator, uri, title));
				}
			}
		} else if ("h2".equals(tagName)) {
			String cssClass = parser.getAttr(attrs, "class");
			if ("post_title".equals(cssClass)) {
				expect = EXPECT_SUBJECT;
				return true;
			}
		} else if ("time".equals(tagName)) {
			String datetime = parser.getAttr(attrs, "datetime");
			try {
				post.setTimestamp(DATE_FORMAT.parse(datetime).getTime());
			} catch (java.text.ParseException e) {
				throw new RuntimeException(e);
			}
		} else if ("div".equals(tagName)) {
			String cssClass = parser.getAttr(attrs, "class");
			if ("text".equals(cssClass)) {
				expect = EXPECT_COMMENT;
				return true;
			} else if ("post_file".equals(cssClass) && post.getParentPostNumber() == null) {
				ensureFile();
				if (originalPostFileStart) {
					originalPostFileStart = false;
					expect = EXPECT_FILE_SIZE;
					return true;
				} else {
					originalPostFileStart = true;
					parser.mark();
				}
			}
		} else if ("a".equals(tagName)) {
			String cssClass = parser.getAttr(attrs, "class");
			if ("thread_image_link".equals(cssClass)) {
				ensureFile();
				String path = convertImageSrc(parser.getAttr(attrs, "href"));
				attachment.setFileUri(locator, locator.createAttachmentUri(path));
			} else if ("post_file_filename".equals(cssClass)) {
				ensureFile();
				String originalName = parser.getAttr(attrs, "title");
				if (originalName != null) {
					attachment.setOriginalName(StringUtils.clearHtml(originalName));
				}
			} else if (needResTo) {
				String function = parser.getAttr(attrs, "data-function");
				if ("quote".equals(function)) {
					String href = parser.getAttr(attrs, "href");
					String threadNumber = locator.getThreadNumber(Uri.parse(href));
					post.setParentPostNumber(threadNumber);
				}
			}
		} else if ("img".equals(tagName)) {
			String cssClass = parser.getAttr(attrs, "class");
			if (cssClass != null && (cssClass.contains("thread_image") || cssClass.contains("post_image"))) {
				String src = convertImageSrc(parser.getAttr(attrs, "src"));
				attachment.setThumbnailUri(locator, locator.createAttachmentUri(src));
			}
		}
		return false;
	}

	@Override
	public void onEndElement(GroupParser parser, String tagName) {
		if ("div".equals(tagName)) {
			if (originalPostFileStart) {
				parser.reset();
			}
		}
	}

	@Override
	public void onText(GroupParser parser, String source, int start, int end) {}

	@Override
	public void onGroupComplete(GroupParser parser, String text) {
		switch (expect) {
			case EXPECT_FILE_SIZE: {
				text = StringUtils.clearHtml(text).trim();
				Matcher matcher = FILE_SIZE.matcher(text);
				if (matcher.matches()) {
					int size = Integer.parseInt(matcher.group(1));
					String dim = matcher.group(2);
					if ("KiB".equals(dim)) {
						size *= 1024;
					} else if ("MiB".equals(dim)) {
						size *= 1024 * 1024;
					}
					int width = Integer.parseInt(matcher.group(3));
					int height = Integer.parseInt(matcher.group(4));
					String originalName = matcher.group(5);
					attachment.setSize(size);
					attachment.setWidth(width);
					attachment.setHeight(height);
					if (!StringUtils.isEmptyOrWhitespace(originalName)) {
						attachment.setOriginalName(originalName);
					}
				}
				break;
			}
			case EXPECT_SUBJECT: {
				post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_NAME: {
				post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_TRIPCODE: {
				post.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
				break;
			}
			case EXPECT_IDENTIFIER: {
				post.setIdentifier(StringUtils.clearHtml(text).trim().substring(3));
				break;
			}
			case EXPECT_COMMENT: {
				if (text != null) {
					text = text.trim();
				}
				post.setComment(text);
				posts.add(post);
				post = null;
				break;
			}
			case EXPECT_OMITTED_POSTS: {
				thread.addPostsCount(Integer.parseInt(text));
				break;
			}
			case EXPECT_OMITTED_IMAGES: {
				thread.addPostsWithFilesCount(Integer.parseInt(text));
				break;
			}
		}
		expect = EXPECT_NONE;
	}
}