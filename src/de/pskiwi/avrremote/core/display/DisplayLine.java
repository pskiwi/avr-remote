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

public final class DisplayLine {

	public DisplayLine(String line) {
		this(line, false, false, false);
	}

	public DisplayLine(String line, boolean playable, boolean cursor,
			boolean folder) {
		this.folder = folder;
		this.station = line.startsWith("/");
		this.line = line;
		this.playable = playable;
		this.cursor = cursor;
	}

	@Override
	public String toString() {
		return line;
	}

	public boolean isCursor() {
		return cursor;
	}

	public boolean isPlayable() {
		return playable;
	}

	public boolean isStation() {
		return station;
	}

	public String getLine() {
		return line;
	}

	public boolean isFolder() {
		return folder;
	}

	private final boolean cursor;
	private final String line;
	private final boolean playable;
	private final boolean station;
	private final boolean folder;

	public final static DisplayLine EMPTY = new DisplayLine("");
}
