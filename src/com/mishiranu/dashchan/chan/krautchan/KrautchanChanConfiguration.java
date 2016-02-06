package com.mishiranu.dashchan.chan.krautchan;

import android.content.res.Resources;
import android.util.Pair;

import chan.content.ChanConfiguration;

public class KrautchanChanConfiguration extends ChanConfiguration
{
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	
	private static final String REPORT_SPAM = "spam";
	private static final String REPORT_ILLEGAL = "illegal";
	private static final String REPORT_OTHER = "other";
	
	public KrautchanChanConfiguration()
	{
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Bernd");
		setDefaultName("jp", "\u30d9\u30eb\u30f3\u30c8");
		setDefaultName("rvss", "Koti\u2122\u00ae");
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		board.allowReporting = true;
		return board;
	}
	
	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowName = posting.allowTripcode = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 4;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/webm");
		posting.attachmentMimeTypes.add("audio/mpeg");
		posting.attachmentMimeTypes.add("application/ogg");
		posting.attachmentMimeTypes.add("application/pdf");
		posting.attachmentMimeTypes.add("application/zip");
		posting.attachmentMimeTypes.add("application/rar");
		posting.attachmentMimeTypes.add("application/x-shockwave-flash");
		posting.hasCountryFlags = "int".equals(boardName);
		return posting;
	}
	
	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		return deleting;
	}
	
	@Override
	public Reporting obtainReportingConfiguration(String boardName)
	{
		Resources resources = getResources();
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.types.add(new Pair<>(REPORT_SPAM, resources.getString(R.string.text_spam)));
		reporting.types.add(new Pair<>(REPORT_ILLEGAL, resources.getString(R.string.text_illegal)));
		reporting.types.add(new Pair<>(REPORT_OTHER, resources.getString(R.string.text_other)));
		return reporting;
	}
	
	public void storeNamesEnabled(String boardName, boolean namesEnabled)
	{
		set(boardName, KEY_NAMES_ENABLED, namesEnabled);
	}
}