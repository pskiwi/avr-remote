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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.pskiwi.avrremote.http.AVRXMLInfo.Input;

/**
 * Sichert das Trefferverhalten der drei Series08-Parser ab.
 *
 * Ein Teil der Fälle stammt aus der Umstellung des OPTION_PATTERN von
 * {@code matches()} mit umschließendem ".*" auf {@code find()} (CodeQL
 * java/polynomial-redos). Entscheidend ist dort die Auswahl bei mehreren
 * Treffern in einer Zeile: das frühere führende gierige ".*" nahm das rechteste
 * Vorkommen, nicht das erste. Genau das muss die Schleife weiterhin tun - sonst
 * liest die App auf 2008er-Geräten stillschweigend die falschen Namen aus.
 * Series08ZoneRenameParser und Series08QuickSelectParser haben dafür einen
 * eigenen Fall, weil sie die Schleife getrennt implementieren. Diese Fälle
 * wurden aus dem Code abgeleitet, nicht aus einer echten Seite.
 *
 * Der andere Teil läuft gegen zwei Mitschnitte eines echten AVR-3808 unter
 * src/test/resources (siehe {@link #capture(String)}). Sie decken
 * Series08InputParser und Series08ZoneRenameParser ab und schreiben fest, was
 * an einer selbst ausgedachten Zeichenkette nicht auffällt - vor allem, dass
 * die Input-Seite noch zwei weitere &lt;select&gt; enthält, die nicht
 * mitgelesen werden dürfen. Für Series08QuickSelectParser gibt es keinen
 * Mitschnitt von d_option1.asp; der bleibt bei den synthetischen Fällen.
 * Series08Reader ist gar nicht abgedeckt, der braucht HTTP.
 */
public final class Series08ParserTest {

	/**
	 * Der Mitschnitt eines AVR-3808: fünf Quellen mit ihren Umbenennungen.
	 *
	 * Die Länge 5 ist dabei der eigentliche Punkt. Dieselbe Seite trägt noch
	 * zwei weitere &lt;select&gt; - listInputMode (Auto, Ext.IN) und
	 * listVideoSelect (SOURCE, HDP, TV/CBL, DVR). Beide stehen in eigenen
	 * Zeilen, und nur deshalb liest der auf START_MARKER laufende Zeilenscan
	 * sie nicht mit. Wer ihn auf das ganze Dokument ausweitet, holt sie
	 * stillschweigend als Quellen herein.
	 *
	 * Nebenbei festgeschrieben: die Auffüll-Leerzeichen ("&gt;TUNER   &lt;")
	 * fallen weg, Werte mit Schrägstrich überstehen das Muster, und das
	 * " selected" am letzten &lt;option&gt; verschiebt den Anzeigenamen nicht.
	 */
	@Test
	public void inputParserReadsCaptureInOrder() throws Exception {
		final Series08InputParser p = new Series08InputParser(
				capture("Series08Input.html"));

		assertEquals("[TUNER->TUNER, HDP->Mediacen, TV/CBL->TV/CBL, "
				+ "DVR->Wii, NET/USB->NET/USB]", format(p.get()));
	}

	/**
	 * Ohne START_MARKER - eine 404 oder eine fremde Seite - bleibt die Liste
	 * leer. Series08InputParser fängt die fehlende Zeile selbst ab, der
	 * aufrufende Series08Reader prüft die Antwort nicht.
	 */
	@Test
	public void inputParserWithoutMarkerReturnsEmpty() throws Exception {
		final Series08InputParser p = new Series08InputParser(
				stream("<html><body>404 Not Found</body></html>\n"));

		assertTrue(p.get().isEmpty());
	}

	/**
	 * get() ist einmalig: der Suchzeiger bleibt hinter dem letzten Treffer
	 * stehen, ein zweiter Aufruf liefert nichts mehr. Series08Reader ruft genau
	 * einmal auf.
	 */
	@Test
	public void inputParserGetIsSingleShot() throws Exception {
		final Series08InputParser p = new Series08InputParser(
				capture("Series08Input.html"));

		assertEquals(5, p.get().size());
		assertTrue(p.get().isEmpty());
	}

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

	/**
	 * Der Mitschnitt eines AVR-3808 - ein Gerät mit drei Zonen. Zone4 und Zone5
	 * stehen gar nicht auf der Seite, dafür treten ihre Vorgabenamen ein; der
	 * Fall darüber deckt nur eine Seite ganz ohne Treffer ab.
	 *
	 * Die echten Namen kommen auf 16 Zeichen aufgefüllt an
	 * (value="ThisIsZone1     ") und werden getrimmt. Nicht angefasst werden
	 * die einfach gequoteten name='radioLinkSetup' value='ON' und name='Color'
	 * value='Gray' derselben Seite - das Muster verlangt value=".
	 */
	@Test
	public void zoneRenameReadsCapture() throws Exception {
		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				capture("Series08Zones.html"));
		p.parse();

		assertEquals("[ThisIsZone1, ThisIsZone2, ThisIsZone3, Zone 4, Zone 5]",
				p.getZoneNames().toString());
	}

	/** getZoneName() nimmt 0..4, alles andere ist ein Programmierfehler. */
	@Test
	public void zoneRenameRejectsInvalidZone() throws Exception {
		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				stream(""));
		p.parse();

		for (int zone : new int[] { -1, 5 }) {
			try {
				p.getZoneName(zone);
				fail("Zone " + zone + " nicht abgewiesen");
			} catch (IllegalArgumentException expected) {
				// so soll es sein
			}
		}
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

	/**
	 * Mitschnitt aus src/test/resources, gleiches Package wie diese Klasse.
	 * Über den Classpath und nicht von der Platte, anders als in
	 * ModelConfiguratorTest: Gradle legt src/test/resources von sich aus dorthin
	 * und meldet es als Task-Input, also braucht es weder einen Eintrag im
	 * inputs.file-Block von build.gradle noch ein bestimmtes
	 * Arbeitsverzeichnis.
	 */
	private static InputStream capture(String name) {
		final InputStream in = Series08ParserTest.class
				.getResourceAsStream(name);
		assertNotNull("Mitschnitt fehlt: " + name, in);
		return in;
	}

	/** Kurzform "name->rename", damit ein Fehlschlag die ganze Liste zeigt. */
	private static String format(List<Input> inputs) {
		final List<String> ret = new ArrayList<String>();
		for (Input i : inputs) {
			ret.add(i.getName() + "->" + i.getRename());
		}
		return ret.toString();
	}
}
