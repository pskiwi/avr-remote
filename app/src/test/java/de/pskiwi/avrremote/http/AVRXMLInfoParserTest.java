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

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

/**
 * Hält fest, warum {@code AVRHTTPClient.readState} und
 * {@code readQuickInfo} den Body auf Länge prüfen, bevor sie parsen: bei leerer
 * Antwort wirft der Parser. Vor dem Wegfall des Apache-Clients übernahm diese
 * Rolle die Prüfung {@code entity != null}.
 */
public final class AVRXMLInfoParserTest {

	@Test
	public void parseThrowsOnEmptyBody() {
		try {
			new AVRXMLInfoParser().parse(stream(""));
			fail("leerer Body muss zu einer Exception führen -"
					+ " sonst ist die Längenprüfung in AVRHTTPClient überflüssig");
		} catch (RuntimeException expected) {
			// so gewollt: der Aufrufer darf gar nicht erst hierher kommen
		}
	}

	// Eine Gegenprobe mit echtem Receiver-XML ist hier bewusst nicht möglich:
	// AVRXMLInfoParser wertet in startElement/endElement nur localName aus. Auf
	// einer normalen JVM ist SAXParserFactory nicht namespace-aware, localName
	// bleibt leer und der Parser sammelt nichts ein. Auf Android füllt der
	// Expat-basierte SAX localName trotzdem, deshalb funktioniert der Pfad im
	// Betrieb. Der Parser lässt sich damit nur auf dem Gerät prüfen.

	private static InputStream stream(String xml) {
		try {
			return new ByteArrayInputStream(xml.getBytes("UTF-8"));
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}
}
