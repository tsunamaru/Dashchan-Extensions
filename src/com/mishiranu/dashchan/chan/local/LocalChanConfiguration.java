package com.mishiranu.dashchan.chan.local;

import java.io.File;

import chan.content.ChanConfiguration;

public class LocalChanConfiguration extends ChanConfiguration
{
	public LocalChanConfiguration()
	{
		request(OPTION_SINGLE_BOARD_MODE);
		request("disallow_proxy");
		request("disallow_archivation");
		request("disable_serialization");
		setBoardTitle(null, getResources().getString(R.string.text_local_archive));
		obtainStatisticsConfiguration();
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowDeleting = true;
		return board;
	}
	
	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		return new Deleting();
	}
	
	@Override
	public Statistics obtainStatisticsConfiguration()
	{
		Statistics statistics = new Statistics();
		statistics.threadsViewed = false;
		statistics.postsSent = false;
		statistics.threadsCreated = false;
		return statistics;
	}
	
	public File getLocalDownloadDirectory()
	{
		return new File(getDownloadDirectory(), "Archive");
	}
}