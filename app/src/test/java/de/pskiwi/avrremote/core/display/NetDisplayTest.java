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
package de.pskiwi.avrremote.core.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import de.pskiwi.avrremote.core.InData;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;

/**
 * NetDisplay.DisplayStatusReader setzt die neun Zeilen einer Bildschirmseite
 * zusammen. Das Format steht im DENON AVR control protocol Ver. 5.2
 * (AVR-3808CI, Juli 2007):
 *
 * <pre>
 * NSA0**************_?????   Display Line1   - ohne Flag-Byte
 * NSA1※************_?????    Display Line3   - mit Flag-Byte
 * ...
 *   ※:Cursor&amp;Playable Music Infomation Data(1Byte)
 *     Bit1:Playable Music =1
 *     Bit2,3:Don't Care
 *     Bit4:CURSOR SELECT=1
 *     Bit5,6,7,8:Don't Care
 * </pre>
 *
 * Bit1 ist der Wert 1, Bit4 der Wert 8 - genau die Masken, die update()
 * abfragt. Bit2 (Verzeichnis) und Bit8 (Bild) wertet die App zusätzlich aus;
 * für den 3808 sind sie laut Papier "Don't Care", spätere Geräte belegen sie.
 *
 * Das Papier beschreibt NSA/IPA, die App spricht NSE/IPE - eine spätere
 * Generation derselben Kommandos, gleicher Rahmen. Wo beide auseinandergehen,
 * gilt hier der Code, und es steht am Testfall dabei: das Papier setzt das
 * Flag-Byte nur auf die Zeilen 1 bis 6, die App liest es auf 1 bis 7.
 *
 * Alle Fälle laufen gegen den Netzwerk-Bildschirm mit seinen acht Zeilen
 * (displayRows=8), also NSE0 bis NSE8.
 */
public final class NetDisplayTest {

	/** Bit1 - "Playable Music". */
	private final static int PLAYABLE = 1;
	/** Bit2 - Verzeichnis; im 3808-Papier "Don't Care". */
	private final static int DIRECTORY = 2;
	/** Bit4 - "CURSOR SELECT". */
	private final static int CURSOR = 8;
	/** Bit8 - Bild; im 3808-Papier "Don't Care". */
	private final static int PICTURE = 128;
	/** Keines der ausgewerteten Bits gesetzt. */
	private final static int NONE = ' ';

	@Test
	public void flagByteMarksPlayableDirectoryAndCursor() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, PLAYABLE, "Ein Titel"), line(2, DIRECTORY, "Ein Ordner"),
				line(3, PLAYABLE | CURSOR, "Unter dem Cursor"),
				line(4, PICTURE, "Mit Bild"), line(5, NONE, ""),
				line(6, NONE, ""), line(7, NONE, ""), "8 [   4/  17]");

		assertTrue(s.getDisplayLine(0).isPlayable());
		assertFalse(s.getDisplayLine(0).isFolder());

		assertTrue(s.getDisplayLine(1).isFolder());
		assertFalse(s.getDisplayLine(1).isPlayable());

		assertTrue(s.getDisplayLine(2).isPlayable());
		assertTrue(s.getDisplayLine(2).isCursor());

		assertFalse(s.getDisplayLine(3).isPlayable());
	}

	/**
	 * Das Cursor-Bit setzt zugleich die Cursorzeile, und zwar um eins versetzt:
	 * NSE1 ist der erste Eintrag der Liste, weil NSE0 der Titel ist.
	 */
	@Test
	public void cursorBitSetsTheCursorLine() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Eins"), line(2, NONE, "Zwei"),
				line(3, CURSOR, "Drei"), line(4, NONE, ""), line(5, NONE, ""),
				line(6, NONE, ""), line(7, NONE, ""), "8 [   4/  17]");

		assertTrue(s.isCursorDefined());
		assertEquals(2, s.getCursorLine());
		assertEquals("Drei", s.getDisplayLine(s.getCursorLine()).getLine());
	}

	/** Ohne gesetztes Bit gibt es keine Cursorzeile. */
	@Test
	public void withoutCursorBitThereIsNoCursorLine() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Eins"), line(2, NONE, "Zwei"),
				line(3, NONE, "Drei"), line(4, NONE, ""), line(5, NONE, ""),
				line(6, NONE, ""), line(7, NONE, ""), "8 [   4/  17]");

		assertFalse(s.isCursorDefined());
		assertEquals(-1, s.getCursorLine());
	}

	/**
	 * Erste und letzte Zeile tragen kein Flag-Byte, haben aber trotzdem
	 * verschiedene Textanfänge: NSE0 ab Index 1, NSE8 ab Index 2. Würde für die
	 * erste Zeile ebenfalls 2 gelten, fehlte dem Titel sein erstes Zeichen.
	 */
	@Test
	public void firstAndLastLineCarryNoFlagByte() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Eins"), line(2, NONE, ""), line(3, NONE, ""),
				line(4, NONE, ""), line(5, NONE, ""), line(6, NONE, ""),
				line(7, NONE, ""), "8 [   4/  17]");

		assertEquals("Internet Radio", s.getTitle());
		assertEquals("[   4/  17]", s.getInfoLine());
	}

	/**
	 * Die letzte Zeile trägt die Blätterinformation "[ n/ m]". Daraus wird die
	 * Nummer der ersten angezeigten Zeile, eins abgezogen - der Receiver zählt
	 * ab 1, die App ab 0.
	 */
	@Test
	public void lastLineCarriesThePageOffset() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Eins"), line(2, NONE, ""), line(3, NONE, ""),
				line(4, NONE, ""), line(5, NONE, ""), line(6, NONE, ""),
				line(7, NONE, ""), "8 [   4/  17]");

		assertEquals(3, s.getOffsetLine());
	}

	/** Ohne erkennbare Blätterinformation bleibt es beim Anfang der Liste. */
	@Test
	public void withoutPageInfoTheOffsetStaysAtZero() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Eins"), line(2, NONE, ""), line(3, NONE, ""),
				line(4, NONE, ""), line(5, NONE, ""), line(6, NONE, ""),
				line(7, NONE, ""), "8 Nichts Verwertbares");

		assertEquals(0, s.getOffsetLine());
	}

	/** "Manche receiver füllen mit Leerzeichen auf" - rechts wird gekürzt. */
	@Test
	public void trailingPaddingIsTrimmed() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Jazz Radio        "), line(2, NONE, ""),
				line(3, NONE, ""), line(4, NONE, ""), line(5, NONE, ""),
				line(6, NONE, ""), line(7, NONE, ""), "8 [   4/  17]");

		assertEquals("Jazz Radio", s.getDisplayLine(0).getLine());
	}

	/**
	 * Die Null-Regel des Papiers gilt auch hier: alles hinter dem Nullbyte ist
	 * zu verwerfen, nicht anzuzeigen.
	 */
	@Test
	public void textEndsAtTheNullByte() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Jazz Radio\0RESTMUELL"), line(2, NONE, ""),
				line(3, NONE, ""), line(4, NONE, ""), line(5, NONE, ""),
				line(6, NONE, ""), line(7, NONE, ""), "8 [   4/  17]");

		assertEquals("Jazz Radio", s.getDisplayLine(0).getLine());
	}

	/**
	 * Zwischen Titel und Infozeile bleiben die sieben Listenzeilen NSE1 bis
	 * NSE7 stehen.
	 */
	@Test
	public void titleAndInfoLineAreNotPartOfTheList() {
		final NetDisplay.DisplayStatus s = read("0Internet Radio",
				line(1, NONE, "Eins"), line(2, NONE, "Zwei"),
				line(3, NONE, "Drei"), line(4, NONE, ""), line(5, NONE, ""),
				line(6, NONE, ""), line(7, NONE, ""), "8 [   4/  17]");

		assertEquals(7, s.getDisplayCount());
		assertEquals("[Eins, Zwei, Drei, , , , ]", s.getDisplayLines()
				.toString());
	}

	/**
	 * Nur die letzte Zeile meldet sich als solche - daran erkennt der Aufrufer,
	 * dass die Seite vollständig ist.
	 */
	@Test
	public void onlyTheLastLineReportsCompletion() {
		final NetDisplay.DisplayStatusReader r = reader();
		for (int nr = 0; nr < 8; nr++) {
			assertFalse("Zeile " + nr + " meldete sich als letzte",
					r.update(new InData(line(nr, NONE, "x"))));
		}
		assertTrue(r.update(new InData("8 [   4/  17]")));
	}

	/** Nach createStatus() ist der Zustand fest und darf nicht weiterlaufen. */
	@Test
	public void readerRefusesUpdatesAfterCreateStatus() {
		final NetDisplay.DisplayStatusReader r = reader();
		r.update(new InData("0Internet Radio"));
		r.createStatus();

		try {
			r.update(new InData(line(1, NONE, "Zu spaet")));
			fail("Aenderung nach createStatus() nicht abgewiesen");
		} catch (IllegalStateException expected) {
			// so soll es sein
		}
	}

	/**
	 * Eine NSE-Zeile aus der Mitte: Zeilenziffer, Flag-Byte, Text. Zeile 0 und
	 * Zeile 8 haben kein Flag-Byte und werden in den Tests ausgeschrieben.
	 */
	private static String line(int nr, int flags, String text) {
		return "" + nr + (char) flags + text;
	}

	/**
	 * NetDisplay selbst braucht Sender und ModelConfigurator erst beim
	 * Bedienen, nicht beim Bauen - für den Netzwerk-Bildschirm setzt der
	 * Konstruktor nur Zeilenzahl und Präfix.
	 */
	private static NetDisplay.DisplayStatusReader reader() {
		return new NetDisplay(null, null, DisplayType.NETWORK).new DisplayStatusReader();
	}

	/** Füttert eine ganze Seite und liefert den fertigen Zustand. */
	private static NetDisplay.DisplayStatus read(String... lines) {
		final NetDisplay.DisplayStatusReader r = reader();
		for (String l : lines) {
			r.update(new InData(l));
		}
		return r.createStatus();
	}
}
