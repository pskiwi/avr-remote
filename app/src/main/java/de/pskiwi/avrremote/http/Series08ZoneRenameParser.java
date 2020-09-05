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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.pskiwi.avrremote.log.Logger;

public final class Series08ZoneRenameParser {

	public Series08ZoneRenameParser(InputStream in) throws IOException {
		this.in = in;
	}

	public void parse() throws IOException {
		final BufferedReader r = new BufferedReader(new InputStreamReader(in));
		try {
			String line;
			while ((line = r.readLine()) != null) {
				System.out.println(line);
				Matcher matcher = OPTION_PATTERN.matcher(line);
				if (matcher.matches()) {
					String key = matcher.group(1).trim();
					String value = matcher.group(2).trim();
					map.put(key, value);
					Logger.info("Series08ZoneRename[" + key + "]->[" + value
							+ "]");

				}
			}
		} finally {
			r.close();
		}
	}

	public static void main(String[] args) throws FileNotFoundException,
			IOException {
		Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				new FileInputStream("Series08Zones.html"));
		p.parse();
		for (Map.Entry<String, String> e : p.map.entrySet()) {
			System.out.println("[" + e.getKey() + "]->[" + e.getValue() + "]");
		}
	}

	public String getZoneName(int zone) {
		if (zone < 0 || zone > ZONE_KEYS.length) {
			throw new IllegalArgumentException("zone:" + zone);
		}
		return map.get(ZONE_KEYS[zone]);
	}

	public List<String> getZoneNames() {
		List<String> ret = new ArrayList<String>();
		for (int i = 0; i < ZONE_KEYS.length; i++) {
			String name = map.get(ZONE_KEYS[i]);
			ret.add(name != null ? name : DEFAULT_NAMES[i]);
		}

		return ret;
	}

	@Override
	public String toString() {
		return "Series08RenameParser";
	}

	private final InputStream in;
	private final Map<String, String> map = new HashMap<String, String>();

	// name='Main' value="ThisIsZone1     "
	private final Pattern OPTION_PATTERN = Pattern
			.compile(".*name='([^']+)' value=\"([^\"]+)\".*");
	private final static String[] ZONE_KEYS = { "Main", "Zone2", "Zone3",
			"Zone4", "Zone5" };
	private final static String[] DEFAULT_NAMES = { "Main", "Zone 2", "Zone 3",
			"Zone 4", "Zone 5" };

}
