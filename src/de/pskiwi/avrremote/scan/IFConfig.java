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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import de.pskiwi.avrremote.log.Logger;

@SuppressLint("DefaultLocale")
public final class IFConfig {
	public IFConfig(Context ctx) {
		tryIfconfig("eth0");
		if (!isDefined()) {
			tryIfconfig("wlan0");
		}
	}

	private void tryIfconfig(String device) {
		String command = "ifconfig " + device;

		try {
			final Process proc = Runtime.getRuntime().exec(command);
			final BufferedReader outputReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));
			final String readLine = outputReader.readLine();
			if (readLine != null) {
				final String line = readLine.toLowerCase();
				Logger.info("ifconfig line [" + device + "]:[" + line + "]");
				if (line.contains("link encap:")) {
					String a6Line = outputReader.readLine();
					Logger.info("ifconfig line [" + a6Line + "]:[" + line + "]");
					Matcher matcher = MASK_A6_PATTERN.matcher(a6Line);
					if (matcher.matches()) {
						ip = matcher.group(1);
						mask = matcher.group(2);
						Logger.info("ifconfig ip [" + ip + "]:[" + mask + "]");
					} else {
						Logger.info("no pattern match");
					}
				} else
				// see if the netmask is in this line
				if (line.startsWith(device + ":")) {
					String[] parts = line.split("\\s");
					for (int i = 0; i < parts.length; i++) {
						if ("ip".equals(parts[i])) {
							if (i < parts.length - 1) {
								ip = parts[i + 1];
								Logger.info("ifconfig ip[" + ip + "]");
							}
						}
						if ("mask".equals(parts[i])) {
							if (i < parts.length - 1) {
								mask = parts[i + 1];
								Logger.info("ifconfig netmask [" + mask + "]");

							}
						}
					}
				}
			}
			int rc = proc.waitFor();
			Logger.info("ifconfig rc: " + rc);
		} catch (Exception x) {
			Logger.error("ifconfig failed", x);
		}
	}

	public InetAddress getIP() throws UnknownHostException {
		return InetAddress.getByName(ip);
	}

	public String getMask() {
		return mask;
	}

	public boolean isDefined() {
		return ip != null && mask != null;
	}

	@Override
	public String toString() {
		return "IFConfig ip:" + ip + " mask:" + mask;
	}

	private String ip;
	private String mask;
	private final static Pattern MASK_A6_PATTERN = Pattern
			.compile(".*inet addr:([\\d\\.]+)\\s+.*Mask:([\\d\\.]+)\\s+.*");
}
