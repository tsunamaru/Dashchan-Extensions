package com.mishiranu.dashchan.chan.rulet;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CountDownLatch;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;

public class RuletCssTestPasser implements Handler.Callback
{
	private static final int MESSAGE_INIT_WEB_VIEW = 1;
	private static final int MESSAGE_START_TEST = 2;
	
	private static volatile RuletCssTestPasser sInstance;
	
	public static RuletCssTestPasser getInstance(Object linked)
	{
		if (sInstance == null)
		{
			synchronized (RuletCssTestPasser.class)
			{
				if (sInstance == null)
				{
					sInstance = new RuletCssTestPasser(linked);
				}
			}
		}
		return sInstance;
	}
	
	private final ChanLocator mLocator;
	private final Handler mHandler;
	
	private WebView mWebView;
	
	private RuletCssTestPasser(Object linked)
	{
		mLocator = ChanLocator.get(linked);
		mHandler = new Handler(Looper.getMainLooper(), RuletCssTestPasser.this);
		Context context = ChanConfiguration.get(linked).getContext();
		CountDownLatch latch = new CountDownLatch(1);
		mHandler.obtainMessage(MESSAGE_INIT_WEB_VIEW, new Object[] {context, latch}).sendToTarget();
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return;
		}
	}
	
	private static class TestRequest
	{
		public final String boardName;
		public final String sidCookie;
		public final HttpHolder holder;
		
		public volatile boolean complete = false;
		public volatile boolean success = false;
		public volatile HttpException exception;
		
		public TestRequest(String boardName, String sidCookie, HttpHolder holder)
		{
			this.boardName = boardName;
			this.sidCookie = sidCookie;
			this.holder = holder;
		}
	}
	
	private TestRequest mWorkTestRequest;
	
	private class CssTestWebViewClient extends WebViewClient
	{
		@Override
		@Deprecated
		public WebResourceResponse shouldInterceptRequest(WebView view, String url)
		{
			TestRequest testRequest = mWorkTestRequest;
			if (testRequest != null)
			{
				Uri uri = Uri.parse(url);
				if (mLocator.isChanHostOrRelative(uri))
				{
					String path = uri.getPath();
					if (("/" + testRequest.boardName + "/csstest.foo").equals(path))
					{
						String code = uri.getQueryParameter("code");
						if (code == null)
						{
							try
							{
								String responseText = new HttpRequest(uri, testRequest.holder, null)
										.addCookie("sid", testRequest.sidCookie).read().getString();
								byte[] data = responseText.getBytes();
								return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(data));
							}
							catch (HttpException e)
							{
								testRequest.exception = e;
								synchronized (testRequest)
								{
									testRequest.complete = true;
									testRequest.notifyAll();
								}
							}
						}
						else
						{
							boolean success = false;
							try
							{
								String responseText = new HttpRequest(uri, testRequest.holder, null)
										.addCookie("sid", testRequest.sidCookie).read().getString();
								success = StringUtils.isEmpty(responseText);
							}
							catch (HttpException e)
							{
								testRequest.exception = e;
							}
							finally
							{
								synchronized (testRequest)
								{
									testRequest.success = success;
									testRequest.complete = true;
									testRequest.notifyAll();
								}
							}
						}
					}
				}
			}
			return new WebResourceResponse("text/html", "UTF-8", null);
		}
	}
	
	public boolean performTest(String boardName, String sidCookie, HttpHolder holder) throws HttpException
	{
		synchronized (this)
		{
			TestRequest testRequest = new TestRequest(boardName, sidCookie, holder);
			mWorkTestRequest = testRequest;
			mHandler.sendEmptyMessage(MESSAGE_START_TEST);
			synchronized (testRequest)
			{
				long time = 10000L;
				while (time > 0L && !testRequest.complete)
				{
					try
					{
						long t = System.currentTimeMillis();
						testRequest.wait(time);
						time -= System.currentTimeMillis() - t;
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
					}
				}
			}
			mWorkTestRequest = null;
			if (testRequest.success) return true;
			if (testRequest.exception != null) throw testRequest.exception;
			return false;
		}
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what)
		{
			case MESSAGE_INIT_WEB_VIEW:
			{
				if (mWebView == null)
				{
					Object[] objects = (Object[]) msg.obj;
					Context context = (Context) objects[0];
					CountDownLatch latch = (CountDownLatch) objects[1];
					try
					{
						WebView webView = new WebView(context);
						webView.setWebViewClient(new CssTestWebViewClient());
						WebSettings settings = webView.getSettings();
						settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
						settings.setAppCacheEnabled(false);
						mWebView = webView;
					}
					finally
					{
						latch.countDown();
					}
				}
				return true;
			}
			case MESSAGE_START_TEST:
			{
				TestRequest testRequest = mWorkTestRequest;
				if (testRequest != null)
				{
					Uri uri = mLocator.buildPath(testRequest.boardName, "csstest.foo");
					CookieManager cookieManager = CookieManager.getInstance();
					cookieManager.removeAllCookie();
					mWebView.clearCache(true);
					mWebView.loadUrl(uri.toString());
				}
				return true;
			}
		}
		return false;
	}
}