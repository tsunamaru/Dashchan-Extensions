package com.mishiranu.dashchan.chan.cirno;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashSet;

import android.net.Uri;

import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpValidator;

public class ChanStatReader
{
	private final String mDomainName;
	private final HashSet<String> mBoardNames = new HashSet<>();
	
	private String mCachedResponseText;
	private HttpValidator mValidator;
	
	public ChanStatReader(String domainName, String... boardNames)
	{
		mDomainName = domainName;
		Collections.addAll(mBoardNames, boardNames);
	}
	
	public int readBoardSpeed(String boardName, CirnoChanLocator locator, HttpHolder holder) throws HttpException
	{
		if (!mBoardNames.contains(boardName)) return -1;
		Uri uri = locator.buildPathWithSchemeHost(true, "chanstat.ru");
		String responseText;
		try
		{
			responseText = new HttpRequest(uri, holder).setValidator(mValidator).setTimeouts(5000, 5000)
					.read().getString();
			HttpValidator validator = holder.getValidator();
			if (validator != null)
			{
				mCachedResponseText = responseText;
				mValidator = validator;
			}
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) responseText = mCachedResponseText;
			else if (e.isHttpException() || e.isSocketException()) return -1; else throw e;
		}
		int start = responseText.indexOf("graphs/" + mDomainName + "/" + boardName + ".html#perhour");
		if (start >= 0)
		{
			start = responseText.indexOf(':', start) + 1;
			if (start > 0)
			{
				int end = responseText.indexOf('<', start);
				if (end > start)
				{
					try
					{
						return Integer.parseInt(responseText.substring(start, end).trim());
					}
					catch (NumberFormatException e)
					{
						
					}
				}
			}
		}
		return -1;
	}
}