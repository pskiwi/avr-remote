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
package de.pskiwi.avrremote.core.display;

import de.pskiwi.avrremote.log.Logger;

public final class HDInfoParser {

	public enum HDInfoKey {
		STATION("ST NAME"), SIGNAL_LEV("SIG LEV"), MLTCASTCURR("MLT CURRCH"), MLTCASTMAX(
				"MLT CAST CH"), MODE("MODE"), ALBUM("ALBUM"), PTY("PTY"), GENRE(
				"GENRE"), TITLE("TITLE"), ARTIST("ARTIST"), CHANNEL(
				"CHANNEL"), SIGNAL("SIGNAL"), UNKNWON("XXXXX"), CHANNEL_NAME(
				"CH NAME"), XMID("XMID");

		HDInfoKey(String key) {
			this.key = key;
		}

		public HDInfo toInfo(String s) {
			if (s.length() >= key.length()) {
				return new HDInfo(this, s.substring(key.length()).trim());
			}
			Logger.info("unrecognized hdinfo [" + s + "]");
			return null;
		}

		private final String key;
	}

	public final static class HDInfo {

		public HDInfo(HDInfoKey key, String value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public String toString() {
			return "HDInfo [key=" + key + ", value=" + value + "]";
		}

		public final HDInfoKey key;
		public final String value;
	}

	public static HDInfo parse(String s) {
		for (HDInfoKey hi : HDInfoKey.values()) {
			if (s.startsWith(hi.key)) {
				return hi.toInfo(s);
			}
		}
		return new HDInfo(HDInfoKey.UNKNWON, s);
	}

}
