package com.mishiranu.dashchan.chan.archiveliom;

import chan.content.ChanConfiguration;

public class ArchiveLiomChanConfiguration extends ChanConfiguration
{
	public ArchiveLiomChanConfiguration()
	{
		setDefaultName("Anonymous");
	}
	
	@Override
	public Statistics obtainStatisticsConfiguration()
	{
		Statistics statistics = new Statistics();
		statistics.postsSent = false;
		statistics.threadsCreated = false;
		return statistics;
	}
}