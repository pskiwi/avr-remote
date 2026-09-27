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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.ConnectException;

import org.junit.Test;

/**
 * Der Ringpuffer ist im Standard-Logmodus das Einzige, was ein
 * Feedback-Bericht vom Verlauf mitbringt. Festgenagelt wird, was ihn
 * auswertbar macht: die Reihenfolge, der Ueberlauf, Thread und Level in jeder
 * Zeile und die Exception bei Fehlern.
 *
 * <p>
 * Der Puffer ist statisch und damit fuer alle Tests dieser JVM derselbe. Jeder
 * Test fuellt ihn deshalb zuerst ganz, dann spielt keine Rolle, was vorher
 * drinstand.
 */
public class LoggerTest {

	@Test
	public void keepsTheLastEntriesOldestFirst() {
		final int overflow = 10;
		final int total = Logger.RING_SIZE + overflow;
		for (int i = 0; i < total; i++) {
			Logger.info("line " + i);
		}
		final String[] lines = Logger.getLastLogEntries().split("\n");
		assertEquals(Logger.RING_SIZE, lines.length);
		assertTrue(lines[0], lines[0].endsWith("line " + overflow));
		final String last = lines[lines.length - 1];
		assertTrue(last, last.endsWith("line " + (total - 1)));
	}

	@Test
	public void lineCarriesLevelAndThread() throws Exception {
		fill();
		final Thread t = new Thread(new Runnable() {
			public void run() {
				Logger.debug("from elsewhere");
			}
		}, "receiver");
		t.start();
		t.join();
		final String last = lastLine();
		assertTrue(last, last.matches(
				"\\d{4}-\\d\\d-\\d\\d  \\d\\d:\\d\\d:\\d\\d\\.\\d{3} - DEBUG : \\[receiver\\] from elsewhere"));
	}

	@Test
	public void errorKeepsTheExceptionButNoTrace() {
		fill();
		Logger.error("Reconnector:IOException [192.168.10.30]",
				new ConnectException("ECONNREFUSED (Connection refused)"));
		assertTrue(lastLine(), lastLine().endsWith(
				"ERROR : [" + Thread.currentThread().getName()
						+ "] Reconnector:IOException [192.168.10.30]"
						+ " -> java.net.ConnectException: ECONNREFUSED (Connection refused)"));
	}

	@Test
	public void errorWithoutException() {
		fill();
		Logger.error("Queue overflow. clear", null);
		assertTrue(lastLine(), lastLine().endsWith("] Queue overflow. clear"));
	}

	private static void fill() {
		for (int i = 0; i < Logger.RING_SIZE; i++) {
			Logger.info("fill " + i);
		}
	}

	private static String lastLine() {
		final String[] lines = Logger.getLastLogEntries().split("\n");
		return lines[lines.length - 1];
	}
}
