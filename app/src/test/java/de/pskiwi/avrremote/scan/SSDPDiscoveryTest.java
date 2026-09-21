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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import de.pskiwi.avrremote.scan.SSDPDiscovery.Response;

/**
 * Pinnt den Antwort-Parser der SSDP-Suche. Der Rest von
 * {@link SSDPDiscovery} braucht ein Netz und bleibt ungetestet.
 *
 * Die beiden Dateien unter src/test/resources sind byte-exakt mit CRLF
 * abgelegt, weil SSDP-Header genau so enden und der Parser das aushalten muss -
 * ein Editor, der sie auf LF normalisiert, nimmt dem Test seinen Sinn.
 *
 * <p>
 * {@code ssdp-search-response.txt} ist die Antwort eines AVR-3310 auf ein
 * M-SEARCH, mitgeschnitten im September 2026. Geändert ist daran genau ein
 * Feld: die zweite Hälfte der UUID ist die MAC des Geräts und steht hier auf
 * {@code 0005cd000000} statt auf der echten - gleiche Länge, gleiche Hex-Form,
 * für den Parser nicht zu unterscheiden. Alles andere ist unberührt, auch das
 * {@code ST}, das kein selbst ausgedachtes Beispiel getroffen hätte:
 * {@code urn:schemas-dm-holdings-com:...} - D&amp;M Holdings, also Denon und
 * Marantz selbst, nicht {@code schemas-upnp-org}.
 *
 * <p>
 * {@code ssdp-notify.txt} ist <em>kein</em> Mitschnitt, sondern aus demselben
 * Gerät abgeleitet: dieselben Header, aber Statuszeile und {@code NT}/{@code NTS}
 * eines unaufgeforderten Advertisements. Der Receiver wiederholt seine
 * NOTIFY-Runde nur etwa alle 900 Sekunden (max-age 1800), das war nicht
 * abzuwarten.
 */
public final class SSDPDiscoveryTest {

	@Test
	public void searchResponseIsParsed() throws IOException {
		final Response response = SSDPDiscovery
				.parse(resource("ssdp-search-response.txt"));
		assertNotNull(response);
		assertEquals("http://192.168.10.30:8080/description.xml",
				response.getLocation());
		assertEquals(
				"urn:schemas-dm-holdings-com:service:X_HtmlPageHandler:1",
				response.getST());
		assertTrue(response.getUSN(),
				response.getUSN().startsWith("uuid:5f9ec1b3-"));
	}

	/**
	 * Das LOCATION dieser Antwort zeigt auf die description.xml, aus der sich
	 * der Modellname lesen liesse - siehe TODO.md. Der Test haelt fest, dass
	 * der Parser es unverändert durchreicht, Port und Pfad eingeschlossen.
	 */
	@Test
	public void locationSurvivesWithPortAndPath() throws IOException {
		final Response response = SSDPDiscovery
				.parse(resource("ssdp-search-response.txt"));
		assertNotNull(response);
		assertTrue(response.getLocation(),
				response.getLocation().endsWith(":8080/description.xml"));
	}

	/**
	 * Ein NOTIFY trägt dieselben Header, ist aber keine Antwort auf die eigene
	 * Frage - das Gerät meldet sich von sich aus. Es hat kein ST, sondern NT,
	 * und wer nur auf die Header sieht statt auf die Statuszeile, nimmt es
	 * trotzdem an.
	 */
	@Test
	public void notifyIsNotAnAnswer() throws IOException {
		assertNull(SSDPDiscovery.parse(resource("ssdp-notify.txt")));
	}

	@Test
	public void headerNamesAreCaseInsensitive() {
		final Response response = SSDPDiscovery
				.parse("HTTP/1.1 200 OK\r\nLocation: http://host/d.xml\r\n"
						+ "st: upnp:rootdevice\r\nUsn: uuid:1234\r\n\r\n");
		assertNotNull(response);
		assertEquals("http://host/d.xml", response.getLocation());
		assertEquals("upnp:rootdevice", response.getST());
		assertEquals("uuid:1234", response.getUSN());
	}

	/**
	 * "EXT:" ohne Wert steht in jeder Antwort, und LOCATION enthält selbst
	 * Doppelpunkte - getrennt wird am ersten.
	 */
	@Test
	public void emptyAndColonBearingValuesSurvive() {
		final Response response = SSDPDiscovery
				.parse("HTTP/1.1 200 OK\r\nEXT:\r\nLOCATION: http://10.0.0.5:8080/d.xml\r\n\r\n");
		assertNotNull(response);
		assertEquals("http://10.0.0.5:8080/d.xml", response.getLocation());
		assertNull(response.getST());
	}

	@Test
	public void anythingElseIsRejected() {
		assertNull(SSDPDiscovery.parse(""));
		assertNull(SSDPDiscovery.parse("HTTP/1.1 404 Not Found\r\n\r\n"));
		assertNull(SSDPDiscovery.parse("<html>hello</html>"));
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = SSDPDiscoveryTest.class
				.getResourceAsStream(name)) {
			assertNotNull("Datei fehlt: " + name, in);
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
			return out.toString("US-ASCII");
		}
	}
}
