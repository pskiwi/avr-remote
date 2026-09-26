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

import static org.junit.Assert.assertTrue;

import java.net.DatagramSocket;
import java.net.Socket;

import org.junit.Test;

/**
 * Ohne WLAN verbietet {@code LocalNetwork} den Verbindungsaufbau, solange
 * "Mobilfunk nutzen" aus ist. Was hier geprüft wird, ist die Ausnahme davon:
 * ohne Application-Context - also in genau diesen JVM-Tests - bleibt es offen.
 *
 * <p>
 * Das ist kein Detail, sondern die Voraussetzung dafür, dass die übrigen Tests
 * überhaupt laufen: {@code HTTPSupportTest} spricht über
 * {@link LocalNetwork#openConnection} mit seinem eigenen lokalen Socket, und
 * {@code ConnectorTest} baut echte Verbindungen auf. Würde die Sperre hier
 * greifen, fielen beide aus - und zwar mit einer Meldung, die auf das WLAN des
 * Testrechners zeigt statt auf die fehlende Einstellung. Ein Nachschärfen der
 * Null-Prüfung in {@code isMobileAllowed()} soll deshalb hier scheitern und
 * nicht dort.
 *
 * <p>
 * Der Zustand ist der Startzustand der Klasse: {@code boundNetwork} und
 * {@code appContext} sind statisch und null, weil kein
 * {@code new LocalNetwork(ctx)} gelaufen ist. Einen Context könnte ein JVM-Test
 * auch nicht liefern - {@code PreferenceManager} ist Android.
 */
public class LocalNetworkTest {

	@Test
	public void withoutContextConnectingStaysAllowed() {
		assertTrue(LocalNetwork.mayConnect());
	}

	@Test
	public void withoutContextBindingDoesNotRefuse() throws Exception {
		try (Socket socket = new Socket()) {
			LocalNetwork.bind(socket);
		}
		try (DatagramSocket socket = new DatagramSocket()) {
			LocalNetwork.bind(socket);
		}
	}
}
