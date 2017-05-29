package com.mishiranu.dashchan.chan.dvach;

public class DvachAppCaptcha {
	private static DvachAppCaptcha dvachAppCaptcha;
	private static boolean ready = false;

	public static synchronized DvachAppCaptcha getInstance() {
		if (!ready) {
			ready = true;
			try {
				System.loadLibrary("appcaptcha");
				dvachAppCaptcha = new DvachAppCaptcha();
			} catch (LinkageError e) {
				// Ignore exception
			}
		}
		return dvachAppCaptcha;
	}

	public native String getPublicKey();
	public native String getCaptchaValue(byte[] data);
}
