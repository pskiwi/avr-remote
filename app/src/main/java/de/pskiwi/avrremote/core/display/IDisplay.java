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

import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import de.pskiwi.avrremote.IScreenMenu;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;

public interface IDisplay {
	public enum Operations {
		Home, Return, Pause, Play, Stop, PageUp, PageDown, SkipPlus, SkipMinus, Up, Down, Menu, ExtraMenu, Search
	}

	Set<Operations> getSupportedOperations();

	void home();

	void returnLevel();

	void pageUp();

	void pageDown();

	void skipMinus();

	void pause();

	void play();

	void stop();

	void skipPlus();

	IScreenMenu createMenu(Activity activity, Zone zone);

	void reset();

	IDisplayStatus getDisplayStatus();

	void moveTo(int position);

	void setListener(IDisplayListener listener);

	void clearListener();

	boolean isDummy();

	int getLayoutResource();

	void setActiveZoneState(ZoneState zone);

	void extendView(Activity activity, IStatusComponentHandler handler);

	public final IDisplay NULL_DISPLAY = new IDisplay() {

		public void home() {
		}

		public void stop() {
		}

		public void skipPlus() {
		}

		public void skipMinus() {
		}

		public void setListener(IDisplayListener listener) {
			listener.displayChanged(IDisplayStatus.EMPTY_DISPLAY);
		}

		public void clearListener() {
		}

		public void returnLevel() {
		}

		public void reset() {
		}

		public void pause() {
		}

		public void play() {
		}

		public void pageUp() {
		}

		public void pageDown() {
		}

		public void moveTo(int position) {
		}

		public IDisplayStatus getDisplayStatus() {
			return IDisplayStatus.EMPTY_DISPLAY;
		}

		public IScreenMenu createMenu(Activity activity, Zone zone) {
			return IScreenMenu.NULL_MENU;
		}

		public boolean isDummy() {
			return true;
		}

		public int getLayoutResource() {
			return de.pskiwi.avrremote.R.layout.osd_screen;
		}

		public void extendView(Activity activity,
				IStatusComponentHandler handler) {
		}

		public void setActiveZoneState(ZoneState zone) {
		}

		public java.util.Set<Operations> getSupportedOperations() {
			return new HashSet<Operations>();
		}

		public String toString() {
			return "Null-Display";
		};
	};

}
