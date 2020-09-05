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

import de.pskiwi.avrremote.core.InData;

public final class Logger {

	private final static class RoundRobinLogger {

		public synchronized void append(String txt) {
			location[locPos] = txt;
			locPos = (locPos + 1) % MAX_LOC;
		}

		public synchronized String getLog() {
			final StringBuilder ret = new StringBuilder();
			for (int p = 0; p < MAX_LOC; p++) {
				ret.append("[" + p + ":" + location[(locPos + p) % MAX_LOC]
						+ "]\n");
			}
			return ret.toString();
		}

		private final static int MAX_LOC = 25;
		private static int locPos = 0;
		private static String[] location = new String[MAX_LOC];
	}

	public static void debug(String s) {
		DELEGATE.debug(s);
		ROUND_ROBIN_LOGGER.append("DEBUG:" + s);
	}

	public static void info(String s) {
		DELEGATE.info(s);
		ROUND_ROBIN_LOGGER.append("INFO:" + s);
	}

	public static void error(String s, Throwable x) {
		DELEGATE.error(s, x);
		ROUND_ROBIN_LOGGER.append("ERROR:" + s);
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

	private final static RoundRobinLogger ROUND_ROBIN_LOGGER = new RoundRobinLogger();
	private static ILogger DELEGATE = ILogger.NULL_LOGGER;
}
