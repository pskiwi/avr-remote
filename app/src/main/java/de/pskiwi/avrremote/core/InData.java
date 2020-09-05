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
package de.pskiwi.avrremote.core;

import de.pskiwi.avrremote.EmulationDetector;
import de.pskiwi.avrremote.log.Logger;

/** Eine gelesene Daten-Zeile */
public final class InData {

	public InData(char[] data, int count) {
		this.data = data;
		this.count = count;
	}

	public InData(String cmd) {
		this.data = cmd.toCharArray();
		count = cmd.length();
	}

	private static boolean isNumber(String s) {
		final int length = s.length();
		if (length == 0) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			char ch = s.charAt(i);
			if (!Character.isDigit(ch)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return new String(data, offset, count - offset);
	}

	public boolean isNumber() {
		return isNumber(toString());
	}

	public boolean isEmpty() {
		return count == 0;
	}

	public int asNumber() {
		try {
			String s = toString().trim();
			if ("OFF".equals(s)) {
				return -1;
			}
			return Integer.parseInt(s);
		} catch (NumberFormatException x) {
			Logger.error("Illegal number [" + toString() + "]", x);
			return -1;
		}
	}

	public char charAt(int i) {
		return data[i + offset];
	}

	public int length() {
		return count;
	}

	public void setOffset(int start) {
		this.offset = start;
	}

	public String extractLine(int toAdd) {
		try {
			final int start = offset + toAdd;
			final byte[] raw = new byte[data.length - start];
			int count = 0;
			for (int i = start; i < data.length; i++) {
				raw[i - start] = (byte) (data[i] > 127 ? data[i] - 256
						: data[i]);
				if (data[i] != 0) {
					count++;
				} else {
					break;
				}
			}
			return new String(raw, 0, count, "UTF-8");
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}

	private String toHexDebug(int max) {
		StringBuilder ret = new StringBuilder("(");
		int maxIndex = count > max ? max : count;
		for (int i = 0; i < maxIndex; i++) {
			if (ret.length() > 1) {
				ret.append(",");
			}
			ret.append(Integer.toHexString(data[i]));
		}
		ret.append(")");
		return ret.toString();
	}

	public String toDebugString() {
		final int max = EXTENDED_DEBUG ? count : (count > MAX_DEBUG ? MAX_DEBUG
				: count);
		return toBaseDebug() + toHexDebug(max);
	}

	private String toBaseDebug() {
		String ret = toString();
		if (ret.length() > MAX_DEBUG) {
			ret = ret.substring(0, MAX_DEBUG) + "...";
		}
		final char[] chars = ret.toCharArray();
		final StringBuilder r = new StringBuilder();
		for (int i = 0; i < chars.length; i++) {
			final char ch = chars[i];
			if (ch < 32 || ch > 127) {
				r.append("{" + Integer.toHexString(ch) + "}");
			} else {
				r.append(ch);
			}
		}
		return r.toString();
	}

	public int getDisplayLineNumber() {
		return charAt(0) - '0';
	}

	private int offset;
	private final char[] data;
	private final int count;
	private static boolean EXTENDED_DEBUG = EmulationDetector.isEmulator();
	private final static int MAX_DEBUG = 15;

}
