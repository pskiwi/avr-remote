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
package de.pskiwi.avrremote.models;

public enum DynamicEQMode {
	/** DEFAULT */
	DYN_SET("PSDYNSET ", new String[] { "NGT", "EVE", "DAY" }, new String[] {
			"Night", "Evening", "Day" }), DYN_VOL("PSDYNVOL ", new String[] {
			"NGT", "EVE", "DAY", "OFF" }, new String[] { "Night", "Evening",
			"Day", "Off" }), DYN_VOL_HEV("PSDYNVOL ", new String[] { "HEV",
			"MED", "LIT", "OFF" }, new String[] { "Heavy", "Medium", "Light",
			"Off" }), UNAVAILABLE("", new String[] {}, new String[] {});

	public String getCommand() {
		return command;
	}

	public String[] getValues() {
		return values;
	}

	public String[] getDesc() {
		return desc;
	}

	private DynamicEQMode(String command, String[] values, String[] desc) {
		if (values.length != desc.length) {
			throw new RuntimeException(values.length + "!=" + desc.length);
		}
		this.command = command;
		this.values = values;
		this.desc = desc;
	}

	private final String command;
	private final String[] values;
	private final String[] desc;
}
