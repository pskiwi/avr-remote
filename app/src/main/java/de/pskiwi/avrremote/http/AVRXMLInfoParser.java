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

import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.pskiwi.avrremote.log.Logger;

public final class AVRXMLInfoParser extends DefaultHandler {

	public AVRXMLInfoParser() {

	}

	public void characters(char[] ch, int start, int length)
			throws SAXException {
		super.characters(ch, start, length);
		value = (value + new String(ch, start, length)).trim();
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		// Rahmen
		if (ROOT_TAG.equals(localName)) {
			return;
		}
		if (VALUE_TAG.equals(localName)) {
			value = "";
			// currentTag beibehalten
			return;
		}
		currentTag = localName;

	}

	public void endElement(String uri, String localName, String name)
			throws SAXException {
		if (localName.equals(VALUE_TAG)) {
			info.add(currentTag, value);
		} else {
			currentTag = null;
		}
	}

	public AVRXMLInfo parse(InputStream in) {

		// Das XML kommt unauthentifiziert per Klartext-HTTP aus dem LAN. Ohne
		// die folgenden Sperren könnte ein vorgetäuschter Receiver über externe
		// Entities lokale Dateien auslesen (XXE).
		//
		// Welche Sperre greift, hängt von der Plattform ab - auf einem Pixel 8
		// durchprobiert: Androids SAXParserFactoryImpl unterstützt nur die
		// beiden external-*-Features und wirft bei den anderen beiden
		// SAXNotRecognizedException. Deshalb jede einzeln absichern: in einem
		// gemeinsamen Block würde die erste nicht unterstützte alle folgenden
		// überspringen.
		final SAXParserFactory factory = SAXParserFactory.newInstance();

		// Die beiden tragenden Sperren, jede für sich. Lässt sich eine nicht
		// setzen, wird nicht geparst - ein ungeschützter Parser auf
		// unauthentifiziertem LAN-XML ist schlimmer als eine fehlende
		// Statusanzeige. Der Aufrufer fängt das ab (StatusAreaManager), die
		// Abfrage entfällt dann laut statt still und unsicher.
		// Die setFeature-Aufrufe stehen bewusst direkt hier und nicht in einer
		// Hilfsmethode: die statische Analyse erkennt die Absicherung sonst
		// womöglich nicht mehr.
		try {
			factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
		} catch (Exception x) {
			throw new RuntimeException("nicht abschaltbar: "
					+ EXTERNAL_GENERAL_ENTITIES, x);
		}
		try {
			factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
		} catch (Exception x) {
			throw new RuntimeException("nicht abschaltbar: "
					+ EXTERNAL_PARAMETER_ENTITIES, x);
		}

		// Zusätzlich auf der JVM; auf Android nicht vorhanden und dort
		// entbehrlich, weil die beiden oben bereits greifen.
		try {
			factory.setFeature(DISALLOW_DOCTYPE, true);
		} catch (Exception x) {
			Logger.debug("XML-Feature nicht unterstützt: " + DISALLOW_DOCTYPE);
		}
		try {
			factory.setFeature(LOAD_EXTERNAL_DTD, false);
		} catch (Exception x) {
			Logger.debug("XML-Feature nicht unterstützt: " + LOAD_EXTERNAL_DTD);
		}
		try {
			SAXParser parser = factory.newSAXParser();
			parser.parse(in, this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		Logger.info("XML parsed: " + info.getInfo());
		return info;
	}

	private String currentTag;
	private String value;
	private final AVRXMLInfo info = new AVRXMLInfo();

	private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
	private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
	private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
	private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
	private static final String VALUE_TAG = "value";
	private static final String ROOT_TAG = "item";
}
