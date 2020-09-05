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
/**
 * 
 */
package de.pskiwi.avrremote.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.scan.AVRTargetTester;

public final class ConnectionConfiguration {

	public ConnectionConfiguration(String config) {
		config = config.trim();
		String[] parts = config.split(":");
		if (parts.length <= 1) {
			extendedConfig = false;
			ip = config;
			httpIP = config;
			port = AVR_PROTOCOL_PORT;
			httpPort = AVR_HTTP_PORT;
		} else {
			// IP[:controlport[:httpport[:httpip]]]
			extendedConfig = true;
			ip = parts[0];
			port = parseInt(parts[1], AVR_PROTOCOL_PORT);
			if (parts.length > 2) {
				httpPort = parseInt(parts[2], AVR_HTTP_PORT);
				if (parts.length > 3) {
					httpIP = parts[3];
				} else {
					httpIP = ip;
				}

			} else {
				httpPort = AVR_HTTP_PORT;
				httpIP = ip;
			}

		}
	}

	private static int parseInt(String string, int defaultValue) {
		try {
			return Integer.parseInt(string);
		} catch (Throwable e) {
			Logger.error("unable to parse [" + string + "/" + defaultValue
					+ "]", e);
		}

		return defaultValue;
	}

	public String getIP() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public String getBaseURL() {
		return "http://" + httpIP + (httpPort != 80 ? ":" + httpPort : "")
				+ "/";
	}

	public SocketAddress getSocketAddress() {
		return new InetSocketAddress(ip, port);
	}

	public boolean isDefined() {
		return ip != null && ip.length() > 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((httpIP == null) ? 0 : httpIP.hashCode());
		result = prime * result + httpPort;
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime * result + port;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConnectionConfiguration other = (ConnectionConfiguration) obj;
		if (httpIP == null) {
			if (other.httpIP != null)
				return false;
		} else if (!httpIP.equals(other.httpIP))
			return false;
		if (httpPort != other.httpPort)
			return false;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		if (port != other.port)
			return false;
		return true;
	}

	public boolean checkAddress(boolean complete) throws UnknownHostException {
		if (extendedConfig) {
			return true;
		}
		return AVRTargetTester.testAddress(InetAddress.getByName(ip), complete);
	}

	private boolean isDefaultConfig() {
		return ip.equals(httpIP) && port == AVR_PROTOCOL_PORT
				&& httpPort == AVR_HTTP_PORT;
	}

	@Override
	public String toString() {
		if (!isDefined()) {
			return "undefined";
		}
		if (isDefaultConfig()) {
			return ip;
		} else {
			return ip + ":" + port + "/" + httpIP + ":" + httpPort;

		}
	}

	private final String ip;
	private final String httpIP;
	private final int port;
	private final int httpPort;
	private final boolean extendedConfig;

	public static final ConnectionConfiguration UNDEFINED = new ConnectionConfiguration(
			"");

	private static final int AVR_PROTOCOL_PORT = 23;
	private static final int AVR_HTTP_PORT = 80;

}