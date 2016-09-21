package com.mishiranu.dashchan.chan.dvach;

public class DvachAppCaptcha
{
	private static DvachAppCaptcha sDvachAppCaptcha;
	private static boolean sReady = false;

	public static synchronized DvachAppCaptcha getInstance()
	{
		if (!sReady)
		{
			sReady = true;
			try
			{
				System.loadLibrary("appcaptcha");
				sDvachAppCaptcha = new DvachAppCaptcha();
			}
			catch (LinkageError e)
			{

			}
		}
		return sDvachAppCaptcha;
	}

	public native String getPublicKey();
	public native String getCaptchaValue(byte[] data);
}