package com.mishiranu.dashchan.chan.infinite;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.net.Uri;

import chan.content.model.Post;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class InfiniteSearchParser
{
	private final String mSource;
	private final InfiniteChanLocator mLocator;

	private Post mPost;
	private final ArrayList<Post> mPosts = new ArrayList<>();
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
	
	public InfiniteSearchParser(String source, Object linked)
	{
		mSource = source;
		mLocator = InfiniteChanLocator.get(linked);
	}
	
	public ArrayList<Post> convertPosts() throws ParseException
	{
		PARSER.parse(mSource, this);
		return mPosts;
	}
	
	private static final TemplateParser<InfiniteSearchParser> PARSER = new TemplateParser<InfiniteSearchParser>()
			.starts("div", "id", "reply_").starts("div", "id", "op_").open((instance, holder, tagName, attributes) ->
	{
		String id = attributes.get("id");
		holder.mPost = new Post().setPostNumber(id.substring(id.indexOf('_') + 1, id.length()));
		return false;
		
	}).equals("a", "class", "post_no").open((instance, holder, tagName, attributes) ->
	{
		String resto = holder.mLocator.getThreadNumber(Uri.parse(attributes.get("href")));
		if (!holder.mPost.getPostNumber().equals(resto)) holder.mPost.setParentPostNumber(resto);
		return false;
		
	}).equals("span", "class", "subject").content((instance, holder, text) ->
	{
		holder.mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
		
	}).equals("span", "class", "name").content((instance, holder, text) ->
	{
		holder.mPost.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
		
	}).equals("span", "class", "tripcode").content((instance, holder, text) ->
	{
		holder.mPost.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
		
	}).equals("span", "class", "capcode").content((instance, holder, text) ->
	{
		String capcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim());
		if (capcode != null && capcode.startsWith("## ")) holder.mPost.setCapcode(capcode.substring(3));
		
	}).equals("a", "class", "email").open((instance, holder, tagName, attributes) ->
	{
		String email = attributes.get("href");
		if (email != null)
		{
			email = StringUtils.clearHtml(email);
			if (email.startsWith("mailto:")) email = email.substring(7);
			if (email.equalsIgnoreCase("sage")) holder.mPost.setSage(true); else holder.mPost.setEmail(email);
		}
		return false;
		
	}).contains("time", "datetime", "").open((instance, holder, tagName, attributes) ->
	{
		if (holder.mPost != null)
		{
			try
			{
				holder.mPost.setTimestamp(DATE_FORMAT.parse(attributes.get("datetime")).getTime());
			}
			catch (java.text.ParseException e)
			{
				
			}
		}
		return false;
		
	}).equals("div", "class", "body").content((instance, holder, text) ->
	{
		holder.mPost.setComment(text);
		holder.mPosts.add(holder.mPost);
		holder.mPost = null;
		
	}).prepare();
}