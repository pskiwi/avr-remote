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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.pskiwi.avrremote.core.InData;

public final class Logger {

	/**
	 * Die letzten Logzeilen, unabhaengig vom Logmodus. Ab Werk schreibt die App
	 * nur nach logcat, und das schickt kein Anwender mit - dieser Puffer ist
	 * dann das Einzige, was ein Feedback- oder Crash-Bericht vom Verlauf
	 * zeigen kann.
	 *
	 * Groesse siehe {@link Logger#RING_SIZE}. Paketprivat, damit LoggerTest eine
	 * eigene Instanz pruefen kann - der statische Puffer unten bekommt auch
	 * Zeilen von Threads, die andere Tests zuruecklassen.
	 *
	 * Zeit und Thread stehen mit drin, wie im Datei-Log - nur die Level heissen
	 * hier DEBUG/INFO/ERROR, dort FINE/WARNING. Ohne Zeit und Thread laesst sich
	 * weder eine Pause erkennen noch sagen, welche Generation des
	 * Reconnect-Loops eine Zeile geschrieben hat (siehe CONNECTION.md).
	 */
	static final class RoundRobinLogger {

		/**
		 * Bei einer Exception nur Typ und Meldung, kein Stacktrace: der Puffer
		 * soll Verlauf zeigen. Aber ohne sie bliebe offen, ob hinter
		 * "Reconnector:IOException" ein ECONNREFUSED oder ein Timeout steckt.
		 */
		public synchronized void append(String level, String txt, Throwable x) {
			location[locPos] = dateFormat.format(new Date()) + " - " + level
					+ " : [" + Thread.currentThread().getName() + "] " + txt
					+ (x == null ? "" : " -> " + x);
			locPos = (locPos + 1) % RING_SIZE;
		}

		/** Aelteste zuerst, eine Zeile pro Eintrag. */
		public synchronized String getLog() {
			final StringBuilder ret = new StringBuilder();
			for (int p = 0; p < RING_SIZE; p++) {
				final String entry = location[(locPos + p) % RING_SIZE];
				if (entry != null) {
					ret.append(entry).append('\n');
				}
			}
			return ret.toString();
		}

		private int locPos = 0;
		private final String[] location = new String[RING_SIZE];
		// nur unter dem Monitor benutzt - SimpleDateFormat ist nicht threadsicher
		private final DateFormat dateFormat = new SimpleDateFormat(
				"yyyy-MM-dd  HH:mm:ss.SSS", Locale.US);
	}

	public static void debug(String s) {
		DELEGATE.debug(s);
		ROUND_ROBIN_LOGGER.append("DEBUG", s, null);
	}

	public static void info(String s) {
		DELEGATE.info(s);
		ROUND_ROBIN_LOGGER.append("INFO", s, null);
	}

	public static void error(String s, Throwable x) {
		DELEGATE.error(s, x);
		ROUND_ROBIN_LOGGER.append("ERROR", s, x);
	}
	
	public static void received(InData val) {
	}

	public static void setDelegate(ILogger delegate) {
		if (delegate == null) {
			throw new NullPointerException("delegate is null");
		}
		if (DELEGATE != null) {
			DELEGATE.close();
		}
		DELEGATE = delegate;
	}

	public static SDLogger getSDLogger() {
		if (DELEGATE instanceof SDLogger) {
			return (SDLogger) DELEGATE;
		}
		return null;
	}

	/** Merker für Crash-Dumps */
	public static void setLocation(String loc) {
		info("LOCATION:" + loc);
	}

	public static String getLastLogEntries() {
		return ROUND_ROBIN_LOGGER.getLog();
	}

	/**
	 * Zeilen im Ringpuffer. Frueher 25, und bei stehender Verbindung reichte
	 * das fuer wenige Sekunden: eine einzige Zustandsabfrage des Receivers sind
	 * schon mehr SEND- und RECEIVED-Zeilen, und was vorher geschah - ein
	 * Abbruch, die Frage nach der Permission - war dann verdraengt. Eine Zeile
	 * hat typisch um 150 Byte, der Puffer also rund 75 KB. Paketprivat fuer
	 * LoggerTest.
	 */
	static final int RING_SIZE = 500;

	private final static RoundRobinLogger ROUND_ROBIN_LOGGER = new RoundRobinLogger();
	private static ILogger DELEGATE = ILogger.NULL_LOGGER;
}
