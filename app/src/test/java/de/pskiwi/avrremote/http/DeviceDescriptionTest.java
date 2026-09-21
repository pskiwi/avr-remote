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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

/**
 * Läuft gegen die echte {@code description.xml} eines AVR-3310 unter
 * src/test/resources, mitgeschnitten im September 2026. Geändert ist daran nur
 * die MAC, die in {@code serialNumber} und {@code UDN} steht - beide liest der
 * Parser ohnehin nicht, siehe {@link DeviceDescription}.
 *
 * <p>
 * Der Fall, auf den es ankommt, ist {@link #modelNameMatchesTheModelList()}:
 * das Feld muss wörtlich sein, was {@code @array/modelNames} führt, sonst
 * taugt es nicht dazu, dem Anwender die Auswahl aus 60 Einträgen zu ersparen.
 */
public final class DeviceDescriptionTest {

	@Test
	public void realDescriptionIsParsed() throws IOException {
		final DeviceDescription d = DeviceDescription.parse(capture());
		assertEquals("DENON:[AVR-3310]", d.getFriendlyName());
		assertEquals("DENON", d.getManufacturer());
		assertEquals("AVR-3310", d.getModelName());
		assertEquals("3310", d.getModelNumber());
		assertEquals("http://192.168.10.30", d.getPresentationURL());
	}

	/**
	 * Der Modellname des Geräts und der Eintrag in der Auswahlliste sind
	 * dieselbe Zeichenkette - Bindestrich eingeschlossen. Das ist die ganze
	 * Grundlage des TODO-Eintrags zur automatischen Modellwahl, und wenn es
	 * irgendwann nicht mehr stimmt, soll es hier auffallen und nicht beim
	 * Anwender.
	 */
	@Test
	public void modelNameMatchesTheModelList() throws IOException {
		final String modelName = DeviceDescription.parse(capture())
				.getModelName();
		assertEquals("AVR-3310", modelName);
		// wie ModelConfigurator.createModel() daraus einen Klassennamen macht
		assertEquals("AVR3310", modelName.replace("-", ""));
	}

	/**
	 * MAC-tragende Felder bleiben ungelesen: ein Feedback-Bericht geht per Mail
	 * raus, und für die Diagnose sagen sie nichts, was modelName nicht besser
	 * sagt. Die Datei enthält beide, der Bericht darf sie nicht zeigen.
	 */
	@Test
	public void identifiersStayOutOfTheReport() throws IOException {
		final String xml = capture();
		assertTrue(xml.contains("serialNumber"));
		assertTrue(xml.contains("<UDN>"));
		final String line = DeviceDescription.parse(xml).toString();
		assertFalse(line, line.contains("0005"));
		assertFalse(line, line.toLowerCase().contains("uuid"));
	}

	/** Ein Receiver ohne UPnP-Beschreibung darf keine Ausnahme ausloesen. */
	@Test
	public void nothingUsefulGivesNulls() {
		final DeviceDescription d = DeviceDescription
				.parse("<html>Site or Page Not Found</html>");
		assertNotNull(d);
		assertNull(d.getModelName());
		assertNull(d.getPresentationURL());
	}

	private static String capture() throws IOException {
		try (InputStream in = DeviceDescriptionTest.class
				.getResourceAsStream("avr3310-description.xml")) {
			assertNotNull("Mitschnitt fehlt", in);
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
			return out.toString("UTF-8");
		}
	}
}
