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
package de.pskiwi.avrremote.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * InData zerlegt eine Zeile des Anzeige-Protokolls. Die Regeln stehen im
 * DENON AVR control protocol Ver. 5.2 (AVR-3808CI, Juli 2007) beim Ereignis
 * NSA, dort als Muster notiert:
 *
 * <pre>
 * NSA0**************_?????&lt;CR&gt;
 *   *:Character Length MAX96
 *   _:Null
 *   ?:Exclusion(The character after Null should be disregarded)
 * </pre>
 *
 * Die Ziffer hinter dem Kommando ist die Zeilennummer, danach folgt der Text,
 * und ein Nullbyte beendet ihn - alles dahinter ist zu verwerfen. Genau das
 * bilden getDisplayLineNumber() und extractLine() ab.
 *
 * Zur Einordnung: das Papier beschreibt NSA/IPA, die App spricht mit NSE/IPE
 * eine spätere Generation derselben Kommandos. Der Rahmen - Ziffer, Flag-Byte,
 * nullterminierter Text - ist in beiden derselbe, die Bedeutung der einzelnen
 * Zeilennummern nicht (siehe NetDisplayTest).
 */
public final class InDataTest {

	@Test
	public void displayLineNumberIsTheLeadingDigit() {
		assertEquals(0, new InData("0Internet Radio").getDisplayLineNumber());
		assertEquals(8, new InData("8 [   4/  17]").getDisplayLineNumber());
	}

	/** "_:Null / ?:The character after Null should be disregarded". */
	@Test
	public void extractLineStopsAtTheNullByte() {
		final InData d = new InData("1 Jazz Radio\0RESTMUELL");

		assertEquals("Jazz Radio", d.extractLine(2));
	}

	/** Ohne Nullbyte reicht der Text bis zum Ende der Daten. */
	@Test
	public void extractLineWithoutNullTakesEverything() {
		assertEquals("Jazz Radio", new InData("1 Jazz Radio").extractLine(2));
	}

	/**
	 * Die Bytes des Receivers stehen einzeln in je einem char. extractLine
	 * schiebt sie zurück in ein byte[] und liest das als UTF-8 - hier C3 A9 für
	 * "é". Ohne den Rückschub käme "Ã©" heraus.
	 */
	@Test
	public void extractLineDecodesUtf8() {
		final InData d = new InData("1 Caf\u00c3\u00a9");

		assertEquals("Caf\u00e9", d.extractLine(2));
	}

	/**
	 * Der Versatz überspringt das Kommando, so wie es AVRState und ZoneState
	 * vor der Weitergabe tun. Danach zählen charAt() und extractLine() ab dem
	 * ersten Zeichen dahinter.
	 */
	@Test
	public void offsetSkipsTheCommand() {
		final InData d = new InData("NSE1 Jazz Radio");
		d.setOffset("NSE".length());

		assertEquals(1, d.getDisplayLineNumber());
		assertEquals(' ', d.charAt(1));
		assertEquals("Jazz Radio", d.extractLine(2));
	}
}
