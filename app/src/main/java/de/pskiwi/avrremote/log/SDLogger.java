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
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

public final class SDLogger implements ILogger {

	private final class SDHandler extends FileHandler {

		public SDHandler() throws IOException {
			super(logdir.getAbsolutePath() + File.separator + LOG_NAME
					+ "-%g.log", FILE_SIZE, MAX_FILE, true);
			setFormatter(new Formatter() {

				/**
				 * Die laufende Nummer ist das einzige verlaessliche
				 * Sortierkriterium. Die Zeilenreihenfolge ist es nicht - sie
				 * gibt die Reihenfolge der Schreibzugriffe wieder, und ein
				 * Thread kann zwischen dem Erzeugen des Records (da faellt der
				 * Zeitstempel) und dem Schreiben verdraengt werden. Der
				 * Zeitstempel ist es auch nicht: er hat nur
				 * Millisekundenaufloesung, und im Feld-Log vom 03.08.2026
				 * teilen sich 65% der Zeilen eine Millisekunde mit einer
				 * anderen. LogRecord vergibt die Nummer im Konstruktor, also im
				 * selben Moment wie getMillis().
				 */
				@Override
				public String format(LogRecord r) {
					final StringBuilder ret = new StringBuilder();
					ret.append(DATE_FORMAT.format(new Date(r.getMillis())));
					ret.append(" #").append(r.getSequenceNumber());
					ret.append(" - ").append(r.getLevel());
					ret.append(" : ").append(r.getMessage()).append("\n");
					appendStackTrace(ret, r.getThrown());
					return ret.toString();
				}

				/**
				 * Ohne das steht im eingeschickten Log nur die Meldung, und
				 * "reading macros.txt failed" laesst offen, ob eine erwartete
				 * FileNotFoundException dahintersteckt oder etwas Ernstes. Das
				 * Throwable geht bis hierher mit und wurde bisher verworfen -
				 * nur logcat bekam es, und das schickt kein Anwender mit.
				 */
				private void appendStackTrace(StringBuilder ret, Throwable t) {
					for (Throwable x = t; x != null; x = x.getCause()) {
						ret.append(x == t ? "\t" : "\tCaused by: ");
						ret.append(x).append("\n");
						final StackTraceElement[] trace = x.getStackTrace();
						final int show = Math.min(trace.length, MAX_TRACE);
						for (int i = 0; i < show; i++) {
							ret.append("\t\tat ").append(trace[i]).append("\n");
						}
						if (trace.length > show) {
							ret.append("\t\t... ")
									.append(trace.length - show)
									.append(" more\n");
						}
					}
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
		logdir = getLogDir(ctx);
		logger.setLevel(Level.FINE);
		try {
			currentHandler = new SDHandler();
			logger.addHandler(currentHandler);
			// durch withThread(), damit jede Zeile dasselbe Format hat und ein
			// Parser nicht zwei Formen kennen muss
			logger.log(Level.INFO, withThread("openend at " + new Date()));
		} catch (IOException e) {
			currentHandler = null;
			Log.e(ADBLogger.TAG, "set sdlogger failed", e);
		}
	}

	/**
	 * App-eigenes Verzeichnis auf dem externen Speicher - unter Scoped Storage
	 * (Android 10+) der einzige Ort, an den ohne Permission geschrieben werden
	 * darf. Ist kein externer Speicher da, geht es nach intern.
	 */
	static File getLogDir(Context ctx) {
		final File ext = ctx.getExternalFilesDir(null);
		return ext != null ? ext : ctx.getFilesDir();
	}

	public Uri getLogURI() {
		final File f = new File(logdir, LOG_NAME + "-0.log");
		Log.i(ADBLogger.TAG, "log: " + f.getAbsolutePath() + " " + f.canRead());
		if (f.canRead()) {
			final File copy = createZip(f);
			if (copy != null) {
				return LogFileProvider.uriFor(copy);
			}
		}
		return null;
	}

	private File createZip(File f) {
		final File writeTo = new File(logdir, ZIP_NAME);
		final byte[] buffer = new byte[8192];
		try (InputStream in = new FileInputStream(f);
				ZipOutputStream zout = new ZipOutputStream(
						new FileOutputStream(writeTo))) {
			zout.putNextEntry(new ZipEntry(LOG_NAME + ".log"));
			int read;
			while ((read = in.read(buffer)) > 0) {
				zout.write(buffer, 0, read);
			}
			zout.closeEntry();
		} catch (Exception x) {
			Log.e(ADBLogger.TAG, "copy log failed", x);
			return null;
		}
		return writeTo;
	}

	public void debug(String s) {
		Log.i(ADBLogger.TAG, s);
		logger.log(Level.FINE, withThread(s));
	}

	public void error(String s, Throwable x) {
		logger.log(Level.WARNING, withThread(s), x);
		Log.e(ADBLogger.TAG, s, x);
	}

	public void info(String s) {
		logger.log(Level.FINE, withThread(s));
		Log.i(ADBLogger.TAG, s);
	}

	/**
	 * Der Thread-Name wird hier genommen, nicht im Formatter. Der laeuft heute
	 * zwar auf dem aufrufenden Thread, weil {@code StreamHandler.publish}
	 * synchron ist - das ist aber eine Eigenschaft des Handlers und keine
	 * Zusage. Ein untergeschobener asynchroner Handler wuerde jede Zeile still
	 * falsch beschriften, und das ist der eine Fehler, den ein Diagnose-Log
	 * nicht machen darf. In logcat steht der Name nicht: das hat seine eigene
	 * tid-Spalte.
	 */
	private static String withThread(String s) {
		return "[" + Thread.currentThread().getName() + "] " + s;
	}

	public void close() {
		if (currentHandler != null) {
			// logger ist der prozessweite JUL-Logger "avrremote", nicht einer
			// pro SDLogger. Ohne removeHandler sammelt jedes Umschalten des
			// Log-Modus einen geschlossenen Handler dort an, der über seine
			// äußere Instanz den ganzen SDLogger festhält.
			logger.removeHandler(currentHandler);
			currentHandler.close();
			currentHandler = null;
		}
	}

	private SDHandler currentHandler;

	private static final int MAX_FILE = 3;
	private static final int FILE_SIZE = 500 * 1024;
	// gedeckelt, weil das Log rotiert: ein voller Android-Stacktrace ist
	// schnell 40 Zeilen, und die obersten sagen alles, was man braucht
	private static final int MAX_TRACE = 12;
	private final java.util.logging.Logger logger = java.util.logging.Logger
			.getLogger(LOG_NAME);
	private final File logdir;
	private final static java.text.DateFormat DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd  HH:mm:ss.SSS");
	private final static String LOG_NAME = "avrremote";
	final static String ZIP_NAME = LOG_NAME + ".zip";

}
