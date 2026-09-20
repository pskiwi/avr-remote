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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.pskiwi.avrremote.log.Logger;

/**
 * Sucht Receiver per SSDP, statt das Netz abzuklappern.
 *
 * Denon und Marantz sprechen alle UPnP - genau darauf testet
 * {@link AVRTargetTester} schon heute (Port 5000/6666 bei den älteren, 8080 und
 * 111 ab 2016). Was der Sweep mühsam errät, beantwortet ein M-SEARCH in Sekunden
 * und mit einem einzigen Paket statt 254 Adressen mal bis zu vier TCP-Connects.
 * Unter Local Network Protection ist das auch der Unterschied zwischen einer
 * Permission, die man begründen kann, und einem Portscan.
 *
 * Die Antworten filtert der Aufrufer weiter durch {@link AVRTargetTester} -
 * Drucker, Fernseher und Router antworten auf {@code ssdp:all} genauso.
 *
 * Kein MulticastLock nötig: die Antworten auf M-SEARCH kommen unicast zurück,
 * gesperrt wäre nur der Empfang von eingehendem Multicast (NOTIFY). Sollte sich
 * auf einem Gerät zeigen, dass nichts ankommt, ist
 * {@code WifiManager.createMulticastLock()} der nächste Schritt - dann wird auch
 * ACCESS_WIFI_STATE wieder gebraucht.
 */
public final class SSDPDiscovery {

	private SSDPDiscovery() {
	}

	/** Statuszeile und die drei Header, die etwas über den Absender sagen. */
	public static final class Response {

		Response(String location, String st, String usn) {
			this.location = location;
			this.st = st;
			this.usn = usn;
		}

		public String getLocation() {
			return location;
		}

		public String getST() {
			return st;
		}

		public String getUSN() {
			return usn;
		}

		@Override
		public String toString() {
			return "ST:[" + st + "] USN:[" + usn + "] LOCATION:[" + location
					+ "]";
		}

		private final String location;
		private final String st;
		private final String usn;
	}

	/**
	 * Absender-Adressen aller Geräte, die auf M-SEARCH geantwortet haben, ohne
	 * Dubletten und in der Reihenfolge des Eintreffens.
	 *
	 * Dreimal senden, weil UDP verloren gehen darf, und bis
	 * {@code SEARCH_DURATION} einsammeln: die Geräte antworten laut Spezifikation
	 * über MX Sekunden verteilt, um den Fragesteller nicht zu überfahren.
	 */
	public static List<InetAddress> search() {
		final Set<InetAddress> found = new LinkedHashSet<InetAddress>();
		try (DatagramSocket socket = new DatagramSocket()) {
			LocalNetwork.bind(socket);
			socket.setSoTimeout(RECEIVE_TIMEOUT);
			final byte[] request = M_SEARCH.getBytes("US-ASCII");
			final InetAddress group = InetAddress.getByName(SSDP_ADDRESS);
			final long started = System.currentTimeMillis();
			final byte[] buffer = new byte[BUFFER_SIZE];
			int sent = 0;
			while (System.currentTimeMillis() - started < SEARCH_DURATION) {
				if (sent < SEARCH_REPEATS
						&& System.currentTimeMillis() - started >= sent
								* SEND_INTERVAL) {
					socket.send(new DatagramPacket(request, request.length,
							group, SSDP_PORT));
					sent++;
				}
				final DatagramPacket packet = new DatagramPacket(buffer,
						buffer.length);
				try {
					socket.receive(packet);
				} catch (SocketTimeoutException x) {
					continue;
				}
				final Response response = parse(new String(packet.getData(),
						packet.getOffset(), packet.getLength(), "US-ASCII"));
				if (response != null && found.add(packet.getAddress())) {
					Logger.info("SSDP: " + packet.getAddress().getHostAddress()
							+ " " + response);
				}
			}
		} catch (IOException x) {
			Logger.error("SSDP search failed", x);
		}
		Logger.info("SSDP: " + found.size() + " device(s) answered");
		return new ArrayList<InetAddress>(found);
	}

	/**
	 * Antwort auf M-SEARCH zerlegen, oder null wenn es keine ist.
	 *
	 * Android-frei und auf einem String, damit der Teil im JVM-Test zu pinnen
	 * ist. Zwei Eigenheiten, die der Test festhält: Header-Namen sind
	 * unabhängig von der Schreibweise, und ein NOTIFY - das Gerät meldet sich
	 * von sich aus, ohne gefragt worden zu sein - ist keine Antwort und wird
	 * verworfen, obwohl es dieselben Header trägt.
	 */
	static Response parse(String message) {
		final String[] lines = message.split("\r\n|\n");
		if (lines.length == 0 || !lines[0].toUpperCase(Locale.US).startsWith(
				"HTTP/1.1 200")) {
			return null;
		}
		String location = null;
		String st = null;
		String usn = null;
		for (int i = 1; i < lines.length; i++) {
			final int colon = lines[i].indexOf(':');
			if (colon < 0) {
				continue;
			}
			final String name = lines[i].substring(0, colon).trim()
					.toUpperCase(Locale.US);
			final String value = lines[i].substring(colon + 1).trim();
			if ("LOCATION".equals(name)) {
				location = value;
			} else if ("ST".equals(name)) {
				st = value;
			} else if ("USN".equals(name)) {
				usn = value;
			}
		}
		return new Response(location, st, usn);
	}

	private static final String SSDP_ADDRESS = "239.255.255.250";
	private static final int SSDP_PORT = 1900;
	/** MX muss zur Sammeldauer passen, sonst antworten Geräte zu spät. */
	private static final String M_SEARCH = "M-SEARCH * HTTP/1.1\r\n"
			+ "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n"
			+ "MAN: \"ssdp:discover\"\r\n" + "MX: 2\r\n" + "ST: ssdp:all\r\n"
			+ "\r\n";
	private static final int SEARCH_REPEATS = 3;
	private static final int SEND_INTERVAL = 800;
	private static final int SEARCH_DURATION = 3000;
	private static final int RECEIVE_TIMEOUT = 400;
	private static final int BUFFER_SIZE = 2048;
}
