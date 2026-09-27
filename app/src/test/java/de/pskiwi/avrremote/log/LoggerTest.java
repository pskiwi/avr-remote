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
 * Jeder Test baut sich einen eigenen Puffer. Der statische hinter
 * {@link Logger} bekommt auch Zeilen von Threads, die andere Tests dieser JVM
 * zuruecklassen - der Receiver aus ConnectorTest meldet sich nach dessen
 * Ende noch -, und ein Test darauf wuerde gelegentlich eine fremde Zeile
 * lesen.
 */
public class LoggerTest {

	@Test
	public void keepsTheLastEntriesOldestFirst() {
		final Logger.RoundRobinLogger ring = new Logger.RoundRobinLogger();
		final int overflow = 10;
		final int total = Logger.RING_SIZE + overflow;
		for (int i = 0; i < total; i++) {
			ring.append("INFO", "line " + i, null);
		}
		final String[] lines = ring.getLog().split("\n");
		assertEquals(Logger.RING_SIZE, lines.length);
		assertTrue(lines[0], lines[0].endsWith("line " + overflow));
		final String last = lines[lines.length - 1];
		assertTrue(last, last.endsWith("line " + (total - 1)));
	}

	@Test
	public void emptySlotsAreLeftOut() {
		final Logger.RoundRobinLogger ring = new Logger.RoundRobinLogger();
		ring.append("INFO", "only", null);
		final String[] lines = ring.getLog().split("\n");
		assertEquals(1, lines.length);
		assertTrue(lines[0], lines[0].endsWith("] only"));
	}

	@Test
	public void lineCarriesLevelAndThread() throws Exception {
		final Logger.RoundRobinLogger ring = new Logger.RoundRobinLogger();
		final Thread t = new Thread(new Runnable() {
			public void run() {
				ring.append("DEBUG", "from elsewhere", null);
			}
		}, "receiver");
		t.start();
		t.join();
		final String line = ring.getLog().trim();
		assertTrue(line, line.matches(
				"\\d{4}-\\d\\d-\\d\\d  \\d\\d:\\d\\d:\\d\\d\\.\\d{3} - DEBUG : \\[receiver\\] from elsewhere"));
	}

	@Test
	public void errorKeepsTheExceptionButNoTrace() {
		final Logger.RoundRobinLogger ring = new Logger.RoundRobinLogger();
		ring.append("ERROR", "Reconnector:IOException [192.168.10.30]",
				new ConnectException("ECONNREFUSED (Connection refused)"));
		final String log = ring.getLog();
		assertEquals(log, 1, log.split("\n").length);
		assertTrue(log, log.trim().endsWith(
				"ERROR : [" + Thread.currentThread().getName()
						+ "] Reconnector:IOException [192.168.10.30]"
						+ " -> java.net.ConnectException: ECONNREFUSED (Connection refused)"));
	}

	@Test
	public void errorWithoutException() {
		final Logger.RoundRobinLogger ring = new Logger.RoundRobinLogger();
		ring.append("ERROR", "Queue overflow. clear", null);
		assertTrue(ring.getLog(), ring.getLog().trim()
				.endsWith("] Queue overflow. clear"));
	}
}
