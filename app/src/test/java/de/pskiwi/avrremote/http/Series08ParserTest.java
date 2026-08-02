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
package de.pskiwi.avrremote.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Test;

/**
 * Sichert das Trefferverhalten der beiden Series08-Parser ab, deren
 * OPTION_PATTERN von {@code matches()} mit umschließendem ".*" auf
 * {@code find()} umgestellt wurde (CodeQL java/polynomial-redos).
 *
 * Entscheidend ist die Auswahl bei mehreren Treffern in einer Zeile: das
 * frühere führende gierige ".*" nahm das rechteste Vorkommen, nicht das erste.
 * Genau das muss die Schleife weiterhin tun - sonst liest die App auf
 * 2008er-Geräten stillschweigend die falschen Namen aus. Beide Parser haben
 * dafür einen eigenen Fall, weil sie die Schleife getrennt implementieren.
 *
 * Diese Klassen laufen nur gegen Receiver der 2008er-Serie, die zum Zeitpunkt
 * der Änderung nicht zum Testen zur Verfügung standen.
 */
public final class Series08ParserTest {

	@Test
	public void zoneRenameReadsNameAndValue() throws Exception {
		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				stream("<html>\n"
						+ "<input name='Main' value=\"Wohnzimmer\" />\n"
						+ "<input name='Zone2' value=\"Kueche\" />\n"
						+ "</html>\n"));
		p.parse();

		assertEquals("Wohnzimmer", p.getZoneName(0));
		assertEquals("Kueche", p.getZoneName(1));
	}

	/** Bei mehreren Treffern je Zeile gewinnt der letzte - wie bisher. */
	@Test
	public void zoneRenameTakesRightmostMatchPerLine() throws Exception {
		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				stream("<td name='Main' value=\"Falsch\"></td>"
						+ "<td name='Main' value=\"Richtig\"></td>\n"));
		p.parse();

		assertEquals("Richtig", p.getZoneName(0));
	}

	/**
	 * Ohne Treffer liefert getZoneNames() die Vorgabenamen - das ist die
	 * Methode, die Series08Reader benutzt. getZoneName(int) gibt in dem Fall
	 * null zurück, ohne Rückfall auf die Vorgabe.
	 */
	@Test
	public void zoneRenameFallsBackToDefaults() throws Exception {
		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				stream("<html>nichts passendes</html>\n"));
		p.parse();

		final List<String> names = p.getZoneNames();
		assertEquals("Main", names.get(0));
		assertEquals("Zone 2", names.get(1));
	}

	@Test
	public void quickSelectReadsNames() throws Exception {
		final Series08QuickSelectParser p = new Series08QuickSelectParser(
				stream("<html>\n"
						+ "<input name='textQuickSelectNameSelect1'"
						+ " value=\"CD hoeren\" />\n"
						+ "<input name='textQuickSelectNameSelect2'"
						+ " value=\"Film\" />\n"
						+ "</html>\n"));
		p.parse();

		final List<String> names = p.get();
		assertEquals(2, names.size());
		assertEquals("CD hoeren", names.get(0));
		assertEquals("Film", names.get(1));
	}

	/** Auch hier gewinnt der letzte Treffer je Zeile. */
	@Test
	public void quickSelectTakesRightmostMatchPerLine() throws Exception {
		final Series08QuickSelectParser p = new Series08QuickSelectParser(
				stream("<td name='textQuickSelectNameSelect1'"
						+ " value=\"Falsch\"></td>"
						+ "<td name='textQuickSelectNameSelect1'"
						+ " value=\"Richtig\"></td>\n"));
		p.parse();

		assertEquals(1, p.get().size());
		assertEquals("Richtig", p.get().get(0));
	}

	/**
	 * Grosse Zeilen bleiben in linearer Zeit. Ausdrücklich KEIN Nachweis gegen
	 * das von CodeQL gemeldete ReDoS: das alte Muster besteht diesen Test
	 * ebenfalls in wenigen Millisekunden - ein Eingabestring, bei dem es
	 * tatsächlich explodiert, liess sich nicht konstruieren, weil Java das
	 * führende ".*" per Literalsuche abkürzt.
	 */
	@Test
	public void largeLineStaysFast() throws Exception {
		final StringBuilder evil = new StringBuilder();
		for (int i = 0; i < 5000; i++) {
			evil.append("name='&' value=\"!");
		}
		evil.append("\n");

		final long start = System.currentTimeMillis();
		new Series08ZoneRenameParser(stream(evil.toString())).parse();
		final long took = System.currentTimeMillis() - start;

		assertTrue("Parsen dauerte " + took + " ms", took < 2000);
	}

	private static InputStream stream(String s) throws IOException {
		return new ByteArrayInputStream(s.getBytes("UTF-8"));
	}
}
