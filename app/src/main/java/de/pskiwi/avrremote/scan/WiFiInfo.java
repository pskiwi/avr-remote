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

import java.net.Inet4Address;
import java.net.InetAddress;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import de.pskiwi.avrremote.log.Logger;

public class WiFiInfo {
	public WiFiInfo(Context ctx) {
		wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
		connectivity = (ConnectivityManager) ctx
				.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	public boolean isConnected() {
		Logger.info("Scan: start");
		if (!isWiFiConnected()) {
			return false;
		}
		if (!isDHCPAvailable()) {
			return false;
		}
		return true;

	}

	private boolean isWiFiConnected() {
		return isWiFiConnected(connectivity);
	}

	/**
	 * Ist ein WLAN verbunden ?
	 *
	 * Ersetzt getNetworkInfo(TYPE_WIFI).isConnected(): der Aufruf ist seit API
	 * 23 deprecated und liefert null, sobald kein WLAN verbunden ist - also
	 * genau in dem Zustand, den der WifiManager-Broadcast meldet. In
	 * AVRApplication.onReceive hat das den Prozess mitgenommen (Play Console,
	 * 1.5.1, Android 17). Die Frage bleibt dieselbe wie vorher, nur die Antwort
	 * kommt aus NetworkCapabilities und kann nicht mehr null sein.
	 *
	 * Zuerst das aktive Netz, weil das der Normalfall ist und getActiveNetwork()
	 * als einziger der beiden Aufrufe nicht deprecated ist. Der Fallback ist
	 * aber nicht optional: ein WLAN ohne Internet - Router mit totem WAN,
	 * Captive Portal, bewusst isoliertes AV-Netz - bleibt neben aktivem
	 * Mobilfunk verbunden, ohne Default-Netz zu sein. Der Receiver ist dann
	 * trotzdem da, und AVRScanner.scan() verweigert bei "false" den Suchlauf
	 * komplett.
	 *
	 * Achtung beim Lesen eines Logs: getActiveNetwork() liefert auch dann null,
	 * wenn dem Prozess der Netzzugriff entzogen wurde (Data Saver, App-Standby),
	 * nicht nur wenn kein Netz da ist.
	 */
	public static boolean isWiFiConnected(ConnectivityManager connectivity) {
		if (isWiFi(connectivity, connectivity.getActiveNetwork())) {
			return true;
		}
		for (Network network : connectivity.getAllNetworks()) {
			if (isWiFi(connectivity, network)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWiFi(ConnectivityManager connectivity,
			Network network) {
		if (network == null) {
			return false;
		}
		final NetworkCapabilities capabilities = connectivity
				.getNetworkCapabilities(network);
		return capabilities != null
				&& capabilities
						.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
	}

	public String getErrorCause() {
		if (!isWiFiConnected()) {
			return "WiFi not connected";
		}
		if (!isDHCPAvailable()) {
			return "no DHCP info";
		}
		return "no error";
	}

	private boolean isDHCPAvailable() {
		return wifi.getDhcpInfo() != null;
	}
	
	public int getNetmask() {
		return wifi.getDhcpInfo().netmask;
	}

	private static byte[] convertIntToByteArray(int val) {
		final byte[] buffer = new byte[4];

		buffer[3] = (byte) (val >>> 24);
		buffer[2] = (byte) (val >>> 16);
		buffer[1] = (byte) (val >>> 8);
		buffer[0] = (byte) val;

		return buffer;
	}

	public InetAddress getAddress() throws Exception {
		final DhcpInfo dhcpInfo = wifi.getDhcpInfo();
		final InetAddress address = Inet4Address
				.getByAddress(convertIntToByteArray(dhcpInfo.ipAddress));
		return address;
	}

	private final WifiManager wifi;
	private final ConnectivityManager connectivity;
}
