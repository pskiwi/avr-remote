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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.pskiwi.avrremote.core.ConnectionConfiguration;
import de.pskiwi.avrremote.log.Logger;

/**
 * Was der Receiver per UPnP über sich selbst sagt.
 *
 * Interessant ist vor allem {@code modelName}: das ist wörtlich die Zeichenkette,
 * die {@code @array/modelNames} führt und die {@link
 * de.pskiwi.avrremote.models.ModelConfigurator} per Reflection auflöst. Damit
 * steht in einem eingeschickten Bericht, welches Gerät tatsächlich am anderen
 * Ende hängt - und nicht nur, was der Anwender in der Liste ausgewählt hat. Ob
 * die beiden auseinanderlaufen, war bisher nicht zu sehen.
 *
 * Bewusst nicht gelesen werden {@code serialNumber} und {@code UDN}: beide
 * tragen die MAC des Geräts, und für die Diagnose sagen sie nichts, was
 * {@code modelName} nicht besser sagt. Ein Feedback-Bericht wandert per Mail
 * durch die Gegend.
 *
 * Geparst wird mit regulären Ausdrücken und nicht mit SAX. Nicht aus Bequem-
 * lichkeit: {@code AVRXMLInfoParser} liest {@code localName}, das auf einem
 * gewöhnlichen JVM-Parser leer bleibt, und ist deshalb nur auf Android
 * lauffähig und gar nicht zu testen (siehe CLAUDE.md). Hier sind es fünf
 * Felder ohne Verschachtelung, dafür lohnt der Handel nicht.
 */
public final class DeviceDescription {

	/**
	 * Holt die Beschreibung, oder null wenn der Receiver keine anbietet.
	 *
	 * Ruft HTTP auf, gehört also auf einen Hintergrund-Thread.
	 *
	 * Der Port ist fest 8080 und nicht der eingestellte HTTP-Port: auf einem
	 * AVR-3310 antwortet Port 80 auf {@code /description.xml} mit 404,
	 * nachgemessen. Autoritativ wäre der {@code LOCATION}-Header, den
	 * {@code scan/SSDPDiscovery} bereits liest - aber den gibt es nur während
	 * eines Suchlaufs, und der läuft bei den meisten Anwendern genau einmal.
	 * Schlägt der Abruf fehl, steht das im Bericht, und genau daran wird
	 * abzulesen sein, ob 8080 über die Baureihen hinweg trägt.
	 */
	public static DeviceDescription read(ConnectionConfiguration config) {
		final String url = "http://" + config.getIP() + ":" + UPNP_PORT
				+ "/description.xml";
		try {
			final DeviceDescription result = parse(new String(
					HTTPSupport.get(url), "UTF-8"));
			if (!result.isUseful()) {
				Logger.info("no UPnP description [" + url
						+ "]: unexpected content");
				return null;
			}
			Logger.info("UPnP description: " + result);
			return result;
		} catch (IOException x) {
			// erwartet, sobald der Receiver aus ist oder kein UPnP auf 8080
			// anbietet - kein Logger.error, das blaeht die Logs auf
			Logger.info("no UPnP description [" + url + "]: " + x);
			return null;
		}
	}

	/**
	 * Ist da eine Beschreibung, oder nur irgendein Text ?
	 *
	 * {@code HTTPSupport.get()} wirft bei 404 nicht, es liefert den Fehlertext -
	 * ein Receiver ohne UPnP auf 8080 antwortet mit der GoAhead-Seite "Site or
	 * Page Not Found". {@link #parse} findet darin kein einziges Tag, und ohne
	 * diese Pruefung stuende im Bericht {@code null / null (null) [null] null}
	 * statt {@code not available}. Genau dieser Unterschied ist die Frage, fuer
	 * die der Wert ueberhaupt erhoben wird.
	 *
	 * Die beiden Felder zusammen, weil einzeln keines sicher ist: ein
	 * Marantz-Geraet ohne {@code modelName} waere denkbar, eines ohne beides
	 * keine Beschreibung mehr.
	 */
	boolean isUseful() {
		return modelName != null || manufacturer != null;
	}

	/** Android-frei und auf einem String, damit der Teil im JVM-Test zu pinnen ist. */
	static DeviceDescription parse(String xml) {
		return new DeviceDescription(tag(xml, "friendlyName"), tag(xml,
				"manufacturer"), tag(xml, "modelName"), tag(xml, "modelNumber"),
				tag(xml, "presentationURL"));
	}

	private static String tag(String xml, String name) {
		final Matcher m = Pattern.compile(
				"<" + name + "\\s*>(.*?)</" + name + "\\s*>", Pattern.DOTALL)
				.matcher(xml);
		return m.find() ? m.group(1).trim() : null;
	}

	private DeviceDescription(String friendlyName, String manufacturer,
			String modelName, String modelNumber, String presentationURL) {
		this.friendlyName = friendlyName;
		this.manufacturer = manufacturer;
		this.modelName = modelName;
		this.modelNumber = modelNumber;
		this.presentationURL = presentationURL;
	}

	public String getFriendlyName() {
		return friendlyName;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	/** Wörtlich der Eintrag aus {@code @array/modelNames}, oder null. */
	public String getModelName() {
		return modelName;
	}

	public String getModelNumber() {
		return modelNumber;
	}

	/** Die Weboberfläche, wie das Gerät sie selbst nennt, oder null. */
	public String getPresentationURL() {
		return presentationURL;
	}

	/** Eine Zeile für Log und Feedback-Bericht. */
	@Override
	public String toString() {
		return manufacturer + " / " + modelName + " (" + modelNumber + ") ["
				+ friendlyName + "] " + presentationURL;
	}

	private final String friendlyName;
	private final String manufacturer;
	private final String modelName;
	private final String modelNumber;
	private final String presentationURL;

	private static final int UPNP_PORT = 8080;
}
