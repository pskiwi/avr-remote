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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import de.pskiwi.avrremote.log.Logger;

/** Testet, ob das Ziel ein Denon AVR sein könnte */
public final class AVRTargetTester {

	private AVRTargetTester() {
	}

	public static boolean testAddress(InetAddress address, boolean cfgTest) {
		try {
			// PING
			final boolean reachable = address
					.isReachable(cfgTest ? PING_TIMEOUT * 2 : PING_TIMEOUT);
			if (!reachable) {
				Logger.debug("ping failed " + address);
				return false;
			}
			// HTTP
			if (!testPort(address, 80)) {
				Logger.debug("port 80 failed " + address);
				return false;
			}
			if (cfgTest) {
				return true;
			}
			// UPNP
			if (!testPort(address, 5000)) {
				Logger.debug("port 5000 failed " + address);
				return false;
			}
			// UPNP
			if (!testPort(address, 6666)) {
				Logger.debug("port 6666 failed " + address);
				return false;
			}
			return true;

		} catch (IOException e) {
			Logger.error("testAddress failed", e);
		}
		return false;
	}

	private static boolean testPort(InetAddress ia, int port) {
		try {
			final Socket socket = new Socket();
			try {
				socket
						.connect(new InetSocketAddress(ia, port),
								CONNECT_TIMEOUT);
			} finally {
				socket.close();
			}
			return true;

		} catch (IOException e) {
			return false;
		}
	}

	private final static int PING_TIMEOUT = 250;
	private final static int CONNECT_TIMEOUT = 500;
}
