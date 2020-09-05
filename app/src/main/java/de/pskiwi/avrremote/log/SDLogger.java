/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.pskiwi.avrremote.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public final class SDLogger implements ILogger {

	private final class SDHandler extends FileHandler {

		public SDHandler() throws IOException {
			super(logdir.getAbsolutePath() + File.separator + LOG_NAME
					+ "-%g.log", FILE_SIZE, MAX_FILE, true);
			setFormatter(new SimpleFormatter());
			setFormatter(new Formatter() {

				@Override
				public String format(LogRecord r) {
					return DATE_FORMAT.format(new Date(r.getMillis())) + " - "
							+ r.getLevel() + " : " + r.getMessage() + "\n";
				}
			});
			setLevel(Level.ALL);
			Log.i(ADBLogger.TAG, "create SDHandler");
		}

		@Override
		public void publish(LogRecord record) {
			super.publish(record);
		}

		@Override
		public void close() {
			super.close();
			Log.i(ADBLogger.TAG, "close SDHandler");
		}

	}

	public SDLogger(Context ctx) {
		this.ctx = ctx;
		logdir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
		logger.setLevel(Level.FINE);
		startWatchingExternalStorage();
	}

	void updateExternalStorageState() {
		boolean oldState = mExternalStorageWriteable;
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}

		if (oldState != mExternalStorageAvailable) {
			Log.i(ADBLogger.TAG, "external storage writable " + oldState + "->"
					+ mExternalStorageAvailable);
			if (mExternalStorageWriteable) {
				try {
					if (!logdir.exists()) {
						boolean mkdir = logdir.mkdir();
						if (!mkdir) {
							Log.e(ADBLogger.TAG,
									"could not create "
											+ logdir.getAbsolutePath());
						}
					}
					currentHandler = new SDHandler();
					logger.addHandler(currentHandler);
					logger.info("openend at " + new Date());
				} catch (IOException e) {
					currentHandler = null;
					Log.e(ADBLogger.TAG, "set sdlogger failed", e);
				}

			} else {
				if (currentHandler != null) {
					logger.removeHandler(currentHandler);
					currentHandler.close();
					currentHandler = null;
				}

			}
		}

	}

	public Uri getLogURI() {
		File f = new File(logdir.getAbsolutePath() + File.separator + LOG_NAME
				+ "-" + 0 + ".log");
		Log.i(ADBLogger.TAG, "log: " + f.getAbsolutePath() + " " + f.canRead());
		if (f.canRead()) {
			final File copy = createZip(f);
			if (copy != null) {
				return Uri.fromFile(copy);
			}
		}
		return null;
	}

	private File createZip(File f) {
		final File writeTo = new File(logdir.getAbsolutePath() + File.separator
				+ LOG_NAME + ".zip");
		long length = f.length();
		byte[] buffer = new byte[(int) length];
		try {
			final FileOutputStream out = new FileOutputStream(writeTo);
			final FileInputStream in = new FileInputStream(f);
			final ZipOutputStream zout = new ZipOutputStream(out);
			try {
				zout.putNextEntry(new ZipEntry(LOG_NAME + ".log"));
				in.read(buffer);
				zout.write(buffer);
				zout.closeEntry();
			} catch (Exception x) {
				Log.e(ADBLogger.TAG, "copy log failed", x);
				return null;
			} finally {
				in.close();
				zout.close();
			}
		} catch (Exception x) {
			Log.e(ADBLogger.TAG, "copy log failed", x);
			return null;
		}
		return writeTo;
	}

	void startWatchingExternalStorage() {
		mExternalStorageReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.i(ADBLogger.TAG, "Storage: " + intent.getData());
				updateExternalStorageState();
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		ctx.registerReceiver(mExternalStorageReceiver, filter);
		updateExternalStorageState();
	}

	void stopWatchingExternalStorage() {
		ctx.unregisterReceiver(mExternalStorageReceiver);
	}

	public void debug(String s) {
		Log.i(ADBLogger.TAG, s);
		logger.fine(s);
	}

	public void error(String s, Throwable x) {
		logger.log(Level.WARNING, s, x);
		Log.e(ADBLogger.TAG, s, x);
	}

	public void info(String s) {
		logger.log(Level.FINE, s);
		Log.i(ADBLogger.TAG, s);
	}

	public void close() {
		if (currentHandler != null) {
			currentHandler.close();
			currentHandler = null;
		}
	}

	private SDHandler currentHandler;
	private BroadcastReceiver mExternalStorageReceiver;
	private boolean mExternalStorageAvailable = false;
	private boolean mExternalStorageWriteable = false;
	private final Context ctx;

	private static final int MAX_FILE = 3;
	private static final int FILE_SIZE = 500 * 1024;
	private final java.util.logging.Logger logger = java.util.logging.Logger
			.getLogger(LOG_NAME);
	private final File logdir;
	private final static java.text.DateFormat DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd  HH:mm:ss.SSS");
	private final static String LOG_NAME = "avrremote";
	private final static String DIR_NAME = "AVRRemote";

}
