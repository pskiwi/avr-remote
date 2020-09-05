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

		final SAXParserFactory factory = SAXParserFactory.newInstance();
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

	private static final String VALUE_TAG = "value";
	private static final String ROOT_TAG = "item";
}
