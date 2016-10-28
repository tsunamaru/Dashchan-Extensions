package com.mishiranu.dashchan.chan.cirno;

import android.net.Uri;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class CirnoCatalogParser
{
	private final String mSource;
	private final CirnoChanLocator mLocator;

	private Post mPost;
	private final ArrayList<Posts> mThreads = new ArrayList<>();

	private static final Pattern LINK_TITLE = Pattern.compile("#(\\d+) \\((.*)\\)");

	public CirnoCatalogParser(String source, Object linked)
	{
		mSource = source;
		mLocator = CirnoChanLocator.get(linked);
	}

	public ArrayList<Posts> convert() throws ParseException
	{
		PARSER.parse(mSource, this);
		return mThreads;
	}

	private static final TemplateParser<CirnoCatalogParser> PARSER = new TemplateParser<CirnoCatalogParser>()
			.starts("a", "title", "#").open((instance, holder, tagName, attributes) ->
	{
		Matcher matcher = LINK_TITLE.matcher(attributes.get("title"));
		if (matcher.matches())
		{
			String number = matcher.group(1);
			String date = matcher.group(2);
			Post post = new Post();
			post.setPostNumber(number);
			try
			{
				post.setTimestamp(CirnoPostsParser.DATE_FORMAT.parse(date).getTime());
			}
			catch (java.text.ParseException e)
			{

			}
			holder.mPost = post;
			holder.mThreads.add(new Posts(post));
		}
		return false;

	}).name("img").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mPost != null)
		{
			String src = attributes.get("src");
			if (src != null)
			{
				FileAttachment attachment = new FileAttachment();
				Uri thumbnailUri = src.contains("/thumb/") ? holder.mLocator.buildPath(src) : null;
				attachment.setThumbnailUri(holder.mLocator, thumbnailUri);
				attachment.setSpoiler(src.contains("extras/icons/spoiler.png"));
				if (thumbnailUri != null)
				{
					Uri fileUri = holder.mLocator.buildPath(src.replace("/thumb/", "/src/").replace("s.", "."));
					attachment.setFileUri(holder.mLocator, fileUri);
				}
				holder.mPost.setAttachments(attachment);
			}
		}
		return false;

	}).equals("span", "class", "filetitle").content((instance, holder, text) ->
	{
		if (holder.mPost != null) holder.mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));

	}).equals("span", "class", "cattext").content((instance, holder, text) ->
	{
		if (holder.mPost != null)
		{
			text = StringUtils.nullIfEmpty(text);
			if (text != null) text = text.trim() + '\u2026';
			holder.mPost.setComment(text);
			holder.mPost = null;
		}

	}).prepare();
}