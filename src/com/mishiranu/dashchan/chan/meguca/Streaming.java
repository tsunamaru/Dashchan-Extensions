package com.mishiranu.dashchan.chan.meguca;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import android.net.Uri;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;

import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.SimpleEntity;

public class Streaming
{
	private static final Random RANDOM = new Random(System.currentTimeMillis());
	
	private static Pair<String, String> generateStreamingPair()
	{
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 3; i++) builder.append(RANDOM.nextInt('9' - '0' + 1) + '0');
		String s1 = builder.toString();
		builder.setLength(0);
		for (int i = 0; i < 8; i++) builder.append(RANDOM.nextInt('z' - 'a' + 1) + 'a');
		String s2 = builder.toString();
		return new Pair<>(s1, s2);
	}
	
	public static long generateId()
	{
		return Math.abs(RANDOM.nextLong()) % 9000000000000000L + 1000000000000000L;
	}
	
	private final MegucaChanLocator mLocator;
	private final Pair<String, String> mStreamingPair = generateStreamingPair();
	
	private final HttpHolder mHolder = new HttpHolder();
	private volatile HttpException mThreadHttpException;
	
	public Streaming(MegucaChanLocator locator)
	{
		mLocator = locator;
	}
	
	public void start() throws HttpException
	{
		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() ->
		{
			try
			{
				Uri uri = createUri("xhr_streaming");
				new HttpRequest(uri, mHolder).setTimeouts(45000, 45000).setPostMethod(null)
						.setOutputStream(mOutputStream).execute();
			}
			catch (HttpException e)
			{
				mThreadHttpException = e;
				return;
			}
			finally
			{
				latch.countDown();
			}
			try
			{
				mHolder.read();
			}
			catch (HttpException e)
			{
				mThreadHttpException = e;
			}
			
		}).start();
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new HttpException(0, null); // Will be ignored by client
		}
		if (mThreadHttpException != null) throw mThreadHttpException;
		readStringData('o');
	}
	
	public void disconnect()
	{
		mHolder.disconnect();
	}
	
	private Uri createUri(String action)
	{
		return mLocator.buildPath("hana", mStreamingPair.first, mStreamingPair.second, action).buildUpon()
				.appendQueryParameter("t", Long.toString(System.currentTimeMillis())).build();
	}
	
	public Uri createSendUri()
	{
		return createUri("xhr_send");
	}
	
	public String readStringData(char blockIdentifier) throws HttpException
	{
		try
		{
			while (true)
			{
				int b = readByte();
				if (b == -1) throw new IOException();
				if (b == blockIdentifier)
				{
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					while ((b = readByte()) != '\n' && b != -1) outputStream.write(b);
					if (b == -1) throw new IOException();
					return new String(outputStream.toByteArray());
				}
			}
		}
		catch (IOException e)
		{
			if (mThreadHttpException != null) throw mThreadHttpException;
			throw new HttpException(0, null); // Will be ignored by client
		}
	}
	
	public JSONArray readAndFindData(int ci0, int ci1) throws HttpException, JSONException, StreamingException
	{
		while (true)
		{
			String text = readStringData('a');
			JSONArray jsonArray = new JSONArray(text);
			jsonArray = new JSONArray(jsonArray.getString(0)).getJSONArray(0);
			int i0 = jsonArray.getInt(0);
			int i1 = jsonArray.getInt(1);
			if (i0 == 0 && i1 == 0)
			{
				String message = jsonArray.getString(2);
				throw new StreamingException(message);
			}
			if ((ci0 == -1 || ci0 == i0) && (ci1 == -1 || ci1 == i1)) return jsonArray;
		}
	}
	
	public void send(HttpHolder holder, JSONArray jsonArray) throws HttpException
	{
		JSONArray outerArray = new JSONArray();
		outerArray.put(jsonArray.toString());
		SimpleEntity entity = new SimpleEntity();
		entity.setContentType("text/plain");
		entity.setData(outerArray.toString());
		Uri uri = createSendUri();
		new HttpRequest(uri, holder).setPostMethod(entity).read();
	}
	
	public void send(HttpHolder holder, Object... data) throws HttpException
	{
		JSONArray jsonArray = new JSONArray();
		for (Object object : data) jsonArray.put(object);
		send(holder, jsonArray);
	}
	
	public static class StreamingException extends Exception
	{
		public StreamingException(String detailMessage)
		{
			super(detailMessage);
		}
	}
	private final Object mDataBufferLock = new Object();
	private byte[] mDataBuffer = new byte[1024];
	private boolean mDataBufferEnd = false;
	private int mDataBufferCount = 0;
	private int mDataBufferIndex = 0;
	
	private boolean mClosed = false;
	
	private final byte[] mOneByteBuffer = new byte[1];
	
	private int readByte() throws IOException
	{
		synchronized (mDataBufferLock)
		{
			while (mDataBufferCount - mDataBufferIndex < 1 && !mDataBufferEnd && !mClosed)
			{
				try
				{
					mDataBufferLock.wait();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IOException();
				}
			}
			if (mDataBufferEnd && mDataBufferIndex >= mDataBufferCount || mClosed) return -1;
			return mDataBuffer[mDataBufferIndex++];
		}
	}
	
	private final OutputStream mOutputStream = new OutputStream()
	{
		@Override
		public void write(int oneByte) throws IOException
		{
			synchronized (mDataBufferLock)
			{
				mOneByteBuffer[0] = (byte) oneByte;
				write(mOneByteBuffer, 0, 1);
			}
		}
		
		@Override
		public void write(byte[] buffer) throws IOException
		{
			write(buffer, 0, buffer.length);
		}
		
		@Override
		public void write(byte[] buffer, int offset, int count) throws IOException
		{
			synchronized (mDataBufferLock)
			{
				if (mDataBufferEnd || mClosed) throw new IOException("Stream is closed");
				if (count > 0)
				{
					int newCount = mDataBufferCount + count;
					if (newCount > mDataBuffer.length)
					{
						mDataBuffer = Arrays.copyOf(mDataBuffer, Math.max(mDataBuffer.length * 2, newCount));
					}
					System.arraycopy(buffer, offset, mDataBuffer, mDataBufferCount, count);
					mDataBufferCount = newCount;
					mDataBufferLock.notifyAll();
				}
			}
		}
		
		@Override
		public void close() throws IOException
		{
			synchronized (mDataBufferLock)
			{
				if (!mDataBufferEnd && !mClosed)
				{
					mDataBufferEnd = true;
					mDataBufferLock.notifyAll();
				}
			}
		}
	};
}