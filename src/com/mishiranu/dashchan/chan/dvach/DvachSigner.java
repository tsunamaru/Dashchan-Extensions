package com.mishiranu.dashchan.chan.dvach;

import chan.content.ChanPerformer;
import chan.http.RequestEntity;

public class DvachSigner
{
	private static DvachSigner sInstance;
	private static boolean sLoaded = false;
	
	public static DvachSigner getInstance()
	{
		synchronized (DvachSigner.class)
		{
			if (!sLoaded)
			{
				sLoaded = true;
				try
				{
					System.loadLibrary("dvachsigner");
					sInstance = new DvachSigner();
				}
				catch (LinkageError e)
				{
					e.printStackTrace();
				}
			}
			return sInstance;
		}
	}
	
	public native String getPublicKey();
	public native void signEntity(ChanPerformer.SendPostData data, RequestEntity entity,
			String checkId, String checkString);
}