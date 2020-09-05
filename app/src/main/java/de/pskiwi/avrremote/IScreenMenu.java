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

import android.view.KeyEvent;
import de.pskiwi.avrremote.core.ZoneState;

public interface IScreenMenu {

	boolean handleKey(ZoneState zoneState, int keyCode, KeyEvent event);

	void doSearch();

	void doClassicSearch();

	void showExtraMenu();

	void showMenu(boolean extended);

	public final static IScreenMenu NULL_MENU = new IScreenMenu() {

		public void showMenu(boolean extended) {
		}

		public void showExtraMenu() {
		}

		public boolean handleKey(ZoneState zoneState, int keyCode,
				KeyEvent event) {
			return false;
		}

		public void doSearch() {
		}

		public void doClassicSearch() {
		}
	};

}
