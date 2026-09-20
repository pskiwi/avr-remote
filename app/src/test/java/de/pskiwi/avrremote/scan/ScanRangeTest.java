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
package de.pskiwi.avrremote.scan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;

import org.junit.Test;

/**
 * Die beiden reinen Funktionen hinter dem Suchlauf: {@code hostRange()}
 * bestimmt, welcher Teil des letzten Oktetts zum eigenen Subnetz gehört, und
 * {@code splitRange()} verteilt Arbeit auf die Scan-Threads - beim Sweep die
 * Adressen des Subnetzes, bei SSDP die Indizes der Antwortenden.
 *
 * <p>
 * Der Anlass ist {@link #everyAddressIsScanned()}: die alte Aufteilung lief
 * {@code [i*16, (i+1)*16-1)} und ließ damit jede 16. Adresse aus - ein Receiver
 * auf .15, .31, ... war per Scan nicht zu finden. Ein Test, der nur Anfang und
 * Ende prüft, hätte das nicht gesehen, deshalb zählt der hier jede einzelne
 * Adresse ab.
 */
public class ScanRangeTest {

	@Test
	public void classCCoversTheWholeOctet() {
		assertArrayEquals(new int[] { 0, 256 }, AVRScanner.hostRange(17, 24));
	}

	/**
	 * Ab /25 hängt der Anfang an der eigenen Adresse: .200 in einem /25 liegt in
	 * der oberen Hälfte, .100 in der unteren. Genau diese Netze hat der Scan
	 * früher mit "nicht unterstützt" abgelehnt.
	 */
	@Test
	public void smallerSubnetsStartAtTheirOwnHalf() {
		assertArrayEquals(new int[] { 128, 128 }, AVRScanner.hostRange(200, 25));
		assertArrayEquals(new int[] { 0, 128 }, AVRScanner.hostRange(100, 25));
		assertArrayEquals(new int[] { 4, 4 }, AVRScanner.hostRange(5, 30));
		assertArrayEquals(new int[] { 42, 1 }, AVRScanner.hostRange(42, 32));
	}

	@Test
	public void everyAddressIsScanned() {
		final int[][] ranges = AVRScanner.splitRange(0, 256, 16);
		assertEquals(16, ranges.length);

		final BitSet seen = new BitSet(256);
		for (int[] range : ranges) {
			for (int i = range[0]; i < range[1]; i++) {
				if (seen.get(i)) {
					throw new AssertionError("scanned twice: " + i);
				}
				seen.set(i);
			}
		}
		assertEquals("not scanned: " + missing(seen, 256), 256,
				seen.cardinality());
	}

	@Test
	public void rangesAreContiguousAndEvenlySized() {
		final int[][] ranges = AVRScanner.splitRange(0, 256, 16);
		assertArrayEquals(new int[] { 0, 16 }, ranges[0]);
		assertArrayEquals(new int[] { 240, 256 }, ranges[15]);
	}

	/**
	 * Ein /25 ab .128: halb so viele Adressen, aber immer noch 16 Threads. Der
	 * Versatz muss durchschlagen - {@code from} ist nicht immer 0.
	 */
	@Test
	public void halfASubnetStillUsesAllThreads() {
		final int[][] ranges = AVRScanner.splitRange(128, 128, 16);
		assertEquals(16, ranges.length);
		assertArrayEquals(new int[] { 128, 136 }, ranges[0]);
		assertArrayEquals(new int[] { 248, 256 }, ranges[15]);
	}

	/**
	 * Die zweite Verwendung: Indizes einer Kandidatenliste aus SSDP. Krumme
	 * Groessen sind hier der Normalfall, und keiner der Bereiche darf leer sein
	 * oder ueber das Listenende hinausgehen - beides waere eine
	 * IndexOutOfBoundsException in subList().
	 */
	@Test
	public void candidateListIsSplitWithoutGapsOrOverflow() {
		for (int size = 1; size <= 40; size++) {
			final int[][] ranges = AVRScanner.splitRange(0, size, 16);
			assertEquals("size " + size, Math.min(16, size), ranges.length);
			int next = 0;
			for (int[] range : ranges) {
				assertEquals("size " + size, next, range[0]);
				assertTrue("empty range at size " + size, range[1] > range[0]);
				next = range[1];
			}
			assertEquals("size " + size, size, next);
		}
	}

	/**
	 * Weniger Adressen als Threads: ein /30 hat vier. Leere Bereiche wären
	 * Threads, die nichts tun - und ein {@code to} kleiner als {@code from}
	 * wäre schlimmer als das.
	 */
	@Test
	public void tinySubnetGetsOneThreadPerAddress() {
		final int[][] ranges = AVRScanner.splitRange(4, 4, 16);
		assertEquals(4, ranges.length);
		for (int i = 0; i < ranges.length; i++) {
			assertArrayEquals(new int[] { 4 + i, 5 + i }, ranges[i]);
		}
	}

	private static String missing(BitSet seen, int count) {
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (!seen.get(i)) {
				result.append(i).append(' ');
			}
		}
		return result.toString();
	}
}
