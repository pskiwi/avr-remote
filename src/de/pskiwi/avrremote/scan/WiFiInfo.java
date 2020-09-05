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
		return connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
				.isConnected();
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
