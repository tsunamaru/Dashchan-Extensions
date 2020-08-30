package com.mishiranu.dashchan.chan.local;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.ChanFile;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpResponse;
import chan.text.ParseException;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalChanPerformer extends ChanPerformer {
	private static final int THREADS_PER_PAGE = 10;

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		Thread thread = Thread.currentThread();
		ArrayList<Posts> threads = new ArrayList<>();
		byte[] buffer = new byte[4096];
		ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
		int from = THREADS_PER_PAGE * data.pageNumber;
		int to = from + THREADS_PER_PAGE;
		int current = 0;
		ChanFile localDownloadDirectory = configuration.getLocalDownloadDirectory();
		List<String> names = localDownloadDirectory.getChildrenNames(ChanFile.Filter.LastModified.INSTANCE);
		if (names != null) {
			Collections.reverse(names);
			for (String name : names) {
				if (name.endsWith(".html")) {
					if (current >= from && current < to) {
						ChanFile file = localDownloadDirectory.getChild(name);
						if (!file.isDirectory()) {
							String threadNumber = name.substring(0, name.length() - 5);
							byte[] fileData = readFile(file, output, buffer);
							if (fileData != null && fileData.length > 0) {
								try {
									threads.add(new LocalPostsParser(new String(fileData), this,
											threadNumber).convertThread());
								} catch (ParseException e) {
									// Ignore
								}
							}
							if (thread.isInterrupted()) {
								return null;
							}
						}
					}
					current++;
				}
			}
		}
		if (threads.size() == 0) {
			if (data.pageNumber == 0) {
				return null;
			} else {
				throw HttpException.createNotFoundException();
			}
		} else {
			return new ReadThreadsResult(threads);
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		if (data.cachedPosts != null) {
			return new ReadPostsResult(data.cachedPosts); // Do not allow models merging
		}
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		ChanFile localDownloadDirectory = configuration.getLocalDownloadDirectory();
		ChanFile file = localDownloadDirectory.getChild(data.threadNumber + ".html");
		if (file.exists()) {
			byte[] fileData = readFile(file, null, null);
			if (fileData != null && fileData.length > 0) {
				try {
					return new ReadPostsResult(new LocalPostsParser(new String(fileData), this,
							data.threadNumber).convertPosts());
				} catch (ParseException e) {
					// Ignore
				}
			}
			throw new InvalidResponseException();
		}
		throw HttpException.createNotFoundException();
	}

	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		String path = data.uri.getPath();
		if ("localhost".equals(data.uri.getAuthority()) && path != null) {
			ChanFile file = configuration.getLocalDownloadDirectory().getChild(path);
			if (file.exists()) {
				return new ReadContentResult(new HttpResponse(readFile(file, null, null)));
			} else {
				throw HttpException.createNotFoundException();
			}
		} else {
			return super.onReadContent(data);
		}
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws ApiException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		ChanFile localDownloadDirectory = configuration.getLocalDownloadDirectory();
		ChanFile file = localDownloadDirectory.getChild(data.threadNumber + ".html");
		byte[] fileData = readFile(file, null, null);
		Posts thread = null;
		if (fileData != null && fileData.length > 0) {
			try {
				thread = new LocalPostsParser(new String(fileData), this, data.threadNumber).convertThread();
			} catch (ParseException e) {
				// Ignore
			}
		}
		if (thread != null) {
			Post[] posts = thread.getPosts();
			if (posts.length > 0 && data.postNumbers.get(0).equals(posts[0].getPostNumber())) {
				removeDirectory(localDownloadDirectory.getChild(data.threadNumber));
				if (!Thread.currentThread().isInterrupted()) {
					file.delete();
				}
				return null;
			}
		}
		throw new ApiException(ApiException.DELETE_ERROR_NO_ACCESS);
	}

	private void removeDirectory(ChanFile directory) {
		Thread thread = Thread.currentThread();
		List<String> names = directory.getChildrenNames(null);
		if (names != null) {
			for (String name : names) {
				if (thread.isInterrupted()) {
					return;
				}
				ChanFile file = directory.getChild(name);
				if (file.isDirectory()) {
					removeDirectory(file);
				} else {
					file.delete();
				}
			}
		}
		directory.delete();
	}

	private byte[] readFile(ChanFile file, ByteArrayOutputStream output, byte[] buffer) {
		if (output != null) {
			output.reset();
		} else {
			output = new ByteArrayOutputStream();
		}
		if (buffer == null) {
			buffer = new byte[4096];
		}
		InputStream input = null;
		try {
			input = file.openInputStream();
			int count;
			while ((count = input.read(buffer)) > 0) {
				output.write(buffer, 0, count);
			}
			return output.toByteArray();
		} catch (IOException e) {
			return null;
		} finally {
			closeStream(input);
		}
	}

	private void closeStream(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				// Ignore
			}
		}
	}
}
