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
package de.pskiwi.avrremote;

import de.pskiwi.avrremote.log.Logger;

public enum BackgroundTheme {
	Default(R.drawable.background_gray), Black(R.drawable.background_black), Gray(
			R.drawable.background_gray), Red(R.drawable.background_red), Custom(
			R.drawable.background_black), Water(R.drawable.background_black);

	public int getResource() {
		return resource;
	}

	private BackgroundTheme(int resource) {
		this.resource = resource;
	}

	public static BackgroundTheme fromString(String s) {
		return convertTheme(s);
	}

	private static BackgroundTheme convertTheme(String s) {
		if (s == null || s.length() == 0) {
			return Default;
		}
		try {
			return valueOf(s);
		} catch (IllegalArgumentException x) {
			Logger.error("illegal argument:" + s, x);
			return Default;
		}
	}

	private final int resource;
}
