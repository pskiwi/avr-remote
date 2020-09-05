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

public interface IDisplayStatus {
	int getDisplayCount();

	boolean isCursorDefined();

	DisplayLine getDisplayLine(int row);

	int getCursorLine();

	String getTitle();

	String getInfoLine();

	CharSequence getPlayInfo();

	public String toDebugString();

	public final IDisplayStatus EMPTY_DISPLAY = new IDisplayStatus() {

		public boolean isCursorDefined() {
			return false;
		}

		public String getTitle() {
			return null;
		}

		public String getInfoLine() {
			return "";
		}

		public DisplayLine getDisplayLine(int row) {
			return new DisplayLine("");
		}

		public int getDisplayCount() {
			return 0;
		}

		public int getCursorLine() {
			return -1;
		}

		public CharSequence getPlayInfo() {
			return "";
		}

		public String toDebugString() {
			return "[EMPTY]";
		};
	};

}
