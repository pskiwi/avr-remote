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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Locale;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.ScreenInfo;
import de.pskiwi.avrremote.core.MacroManager;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.scan.WiFiInfo;

public final class FeedbackReporter {

	public FeedbackReporter(Context ctx, Thread thread, Throwable xpt) {
		this.ctx = ctx;
		this.thread = thread;
		this.xpt = xpt;
	}

	public void saveCrash() {
		try {
			final FileOutputStream trace = ctx.openFileOutput(STACK_TRACE,
					Context.MODE_PRIVATE);
			Logger.info("save crash data...");
			try {
				trace.write((createInfoString(ctx, CRASH_HEADER) + createCrashInfo())
						.getBytes());
			} finally {
				trace.close();
			}
			Logger.info("save crash data ok.");
		} catch (IOException x) {
			Logger.error("save crash failed", x);
		}
	}

	public static void sendFeedback(Context ctx, AVRApplication avr,
			boolean attachLogs) {
		final String xmlInfo = avr.getModelConfigurator().getXMLInfo();
		sendMail(ctx, FEEDBACK_SUBJECT,
				createInfoString(ctx, ctx.getString(R.string.FeedbackText))
						+ "\n" + xmlInfo, attachLogs);
	}

	public static void sendMail(Context ctx, String subject, String message,
			boolean attachLogs) {
		final Intent emailIntent = new Intent(
				android.content.Intent.ACTION_SEND);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
				new String[] { EMAIL });
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);
		if (attachLogs) {
			final SDLogger sdLogger = Logger.getSDLogger();
			if (sdLogger != null) {
				Logger.info("attach files...");
				final Uri uri = sdLogger.getLogURI();
				if (uri != null) {
					emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
				}
			}

		}
		try {
			ctx.startActivity(emailIntent);
		} catch (ActivityNotFoundException x) {
			Logger.error("No EMAIL-APP found", x);
		}
	}

	public static String getVersionInfo(Context ctx) {
		try {
			PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(
					ctx.getPackageName(), 0);
			return "Version : " + packageInfo.versionName + " ("
					+ packageInfo.versionCode + ")";

		} catch (NameNotFoundException e) {
			e.printStackTrace();
			return "Version info not found";
		}
	}

	private static String createInfoString(Context ctx, String header) {
		final String pmInfo = getVersionInfo(ctx);
		final StringWriter result = new StringWriter();
		final PrintWriter out = new PrintWriter(result);
		out.println("\n\n\n");
		out.println(header);
		out.println("-----------------------");
		out.println("Date    : " + new Date());
		out.println("-----------------------");
		out.println("Revision: " + pmInfo);

		final Runtime rt = Runtime.getRuntime();
		out.println("Memory  : max:" + mem(rt.maxMemory()) + "/free:"
				+ mem(rt.freeMemory()) + "/total:" + mem(rt.totalMemory()));

		out.println("Region  : " + AVRSettings.getAVRModelArea(ctx));
		out.println("Zones   : " + AVRSettings.getAVRZoneCount(ctx));

		for (int i = 0; i < AVRSettings.MAX_RECEIVERS; i++) {
			out.println("IP-" + i + "    : [" + AVRSettings.getAVRIP(ctx, i)
					+ "]");
			out.println("Model-" + i + " : " + AVRSettings.getAVRModel(ctx, i));
		}

		try {
			final WiFiInfo wiFiInfo = new WiFiInfo(ctx);
			out.println("WiFi    : "
					+ (wiFiInfo.isConnected() ? "connected" : "not connected")
					+ " (" + wiFiInfo.getErrorCause() + ") "
					+ wiFiInfo.getAddress().getHostAddress() + "/"
					+ Integer.toHexString(wiFiInfo.getNetmask()));
		} catch (Exception x) {
			out.println("Wifi Info not available [" + x.getMessage() + "]");
		}

		out.println("Log     : " + AVRSettings.getDebugMode(ctx));
		out.println("Theme   : " + AVRSettings.getBackgroundTheme(ctx));
		out.println("Rec.Set.: " + AVRSettings.isUseReceiverSettings(ctx));
		out.println("Volume  : " + AVRSettings.getVolumeDisplay(ctx));
		out.println("Napster : " + AVRSettings.isNapsterEnabled(ctx));
		out.println("Notification   : " + AVRSettings.isNapsterEnabled(ctx));

		out.println("-----------------------");
		out.println("Model   : " + android.os.Build.MODEL);
		out.println("SDK     : " + Build.VERSION.SDK);
		out.println("Version : " + android.os.Build.VERSION.RELEASE);
		out.println("Inc     : " + Build.VERSION.INCREMENTAL);

		final ScreenInfo screenInfo = new ScreenInfo(ctx);
		final DisplayMetrics metrics = screenInfo.getMetrics();

		out.println("PXPI    : " + metrics.density);
		out.println("Density : " + metrics.xdpi + "/" + metrics.ydpi);
		out.println("ScaleD  : " + metrics.scaledDensity);
		out.println("phy  : " + screenInfo.getPhyWidth() + "/"
				+ screenInfo.getPhyHeight() + " " + screenInfo.getSquareWidth());
		out.println("ScreenInfo   : " + screenInfo);

		out.println("Locale  : " + Locale.getDefault());

		out.println("-----------------------");
		out.println(AVRSettings.getAll(ctx));
		out.println("-----------------------");
		dumpMacros(out, ctx);

		RenameService.dump(ctx, out);

		out.close();

		return result.toString();
	}

	private static String mem(long m) {
		final long kb = m / 1024;
		final long mb = kb / 1024;
		return mb > 0 ? mb + "MB" : kb + "kB";
	}

	private static void dumpMacros(PrintWriter out, Context ctx) {
		out.println("Macros:");
		out.println("-------");
		try {
			final BufferedReader r = new BufferedReader(new InputStreamReader(
					ctx.openFileInput(MacroManager.MACRO_FILE)));
			try {
				String l;
				int nr = 1;
				while ((l = r.readLine()) != null) {
					out.println(nr + ":[" + l + "]");
				}
			} finally {
				r.close();
			}
		} catch (Exception e) {
			out.println("Macro file:" + e);
		}
		out.println("-------");
	}

	public String createCrashInfo() {
		final StringWriter result = new StringWriter();
		final PrintWriter out = new PrintWriter(result);
		try {
			out.println("Thread id : " + thread.getId() + "/ name : "
					+ thread.getName());
			xpt.printStackTrace(out);
			Throwable cause = xpt.getCause();
			while (cause != null) {
				cause.printStackTrace(out);
				cause = cause.getCause();
			}

			out.println("-----------------------");
			out.println("Last log entries:");
			out.println(Logger.getLastLogEntries());
			out.println("-----------------------");
		} finally {
			out.close();
		}

		return result.toString();
	}

	public static void checkForCrash(Context ctx) {
		final File file = new File(ctx.getFilesDir(), STACK_TRACE);
		if (file.exists()) {
			Logger.info("found crash file");
			try {
				final BufferedReader reader = new BufferedReader(
						new InputStreamReader(ctx.openFileInput(STACK_TRACE)));
				String line;
				String trace = "";
				while ((line = reader.readLine()) != null) {
					trace += line + "\n";
				}
				reader.close();

				boolean deleted = file.delete();
				if (!deleted) {
					Logger.error("could not delete stack trace",
							new Throwable());
				}
				Logger.info("send mail ...");
				sendMail(ctx, CRASH_SUBJECT, trace, false);

			} catch (Exception x) {
				Logger.error("sendmail failed", x);
			}
		}

	}

	private final Context ctx;

	private final Throwable xpt;
	private final Thread thread;

	private static final String STACK_TRACE = "stack.trace";

	private static final String EMAIL = "andreas.pillath@gmail.com";
	private static final String FEEDBACK_SUBJECT = "AVR-Remote Feedback";
	private static final String CRASH_SUBJECT = "AVRRemote Crash Report";
	private static final String CRASH_HEADER = "** AVRRemote crash report - please send **";

}
