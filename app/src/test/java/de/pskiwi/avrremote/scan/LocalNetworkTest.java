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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;

import org.junit.Test;

/**
 * Der Reconnect-Loop überspringt eine Runde, wenn die Receiver-Adresse in keinem
 * Subnetz liegt, an dem das Gerät direkt hängt - ohne Bindung nimmt der Socket
 * sonst die Default-Route ins Mobilfunknetz und zahlt dort den vollen
 * Connect-Timeout auf eine Adresse, die von da nicht erreichbar ist.
 *
 * <p>
 * Geprüft wird hier der Prefix-Vergleich, der diese Entscheidung trägt. Die
 * Fälle sind die echten: ein /24-Heimnetz, die /30 einer Mobilfunk-Schnittstelle
 * samt der Adresse aus dem Fehlerbericht, und die Oktettgrenzen, an denen eine
 * Maskenrechnung typischerweise falsch wird.
 *
 * <p>
 * Dass {@link LocalNetwork#mayConnect} ohne Application-Context durchlässt, ist
 * kein Detail, sondern die Voraussetzung dafür, dass die übrigen Tests laufen
 * können: {@code HTTPSupportTest} spricht über {@code openConnection} mit seinem
 * eigenen lokalen Socket, {@code ConnectorTest} baut echte Verbindungen auf. Ein
 * Nachschärfen der Null-Prüfung in {@link LocalNetwork#mayConnect} soll deshalb
 * hier scheitern und nicht dort.
 */
public class LocalNetworkTest {

	@Test
	public void addressesInTheSameSubnet() throws Exception {
		assertTrue(same("192.168.10.5", "192.168.10.30", 24));
		assertTrue(same("192.168.10.5", "192.168.10.5", 32));
		assertTrue(same("10.41.229.113", "10.41.229.114", 30));
		// /0 vergleicht nichts und passt deshalb immer
		assertTrue(same("10.41.229.114", "192.168.10.30", 0));
	}

	@Test
	public void addressesInDifferentSubnets() throws Exception {
		// der gemessene Fall: LAN-Adresse gegen die Adresse der
		// Mobilfunk-Schnittstelle
		assertFalse(same("10.41.229.114", "192.168.10.30", 30));
		assertFalse(same("192.168.10.5", "192.168.11.30", 24));
		assertFalse(same("192.168.10.5", "192.168.10.30", 32));
	}

	/** Die Oktettgrenzen und die Bits innerhalb eines Oktetts. */
	@Test
	public void prefixLengthIsCountedInBits() throws Exception {
		assertTrue(same("172.16.0.1", "172.31.255.254", 12));
		assertFalse(same("172.16.0.1", "172.32.0.1", 12));
		assertTrue(same("10.0.0.1", "10.255.255.254", 8));
		assertFalse(same("10.0.0.1", "11.0.0.1", 8));
		assertTrue(same("192.168.10.100", "192.168.10.126", 25));
		assertFalse(same("192.168.10.100", "192.168.10.130", 25));
	}

	@Test
	public void nonsenseIsNoMatch() throws Exception {
		assertFalse(same("192.168.10.5", "192.168.10.5", 33));
		assertFalse(same("192.168.10.5", "192.168.10.5", -1));
		assertFalse(LocalNetwork.sameSubnet(new byte[] { 1, 2, 3 },
				new byte[] { 1, 2, 3 }, 24));
	}

	@Test
	public void withoutContextConnectingStaysAllowed() {
		assertTrue(LocalNetwork.mayConnect("192.168.10.30"));
	}

	private static boolean same(String a, String b, int prefixLength)
			throws Exception {
		return LocalNetwork.sameSubnet(InetAddress.getByName(a).getAddress(),
				InetAddress.getByName(b).getAddress(), prefixLength);
	}
}
