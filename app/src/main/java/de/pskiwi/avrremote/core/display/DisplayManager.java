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

import java.util.HashMap;
import java.util.Map;

import de.pskiwi.avrremote.core.AVRState;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState.InputSelect;

public final class DisplayManager {

	public enum DisplayType {
		NETWORK("SERVER"), TUNER("TUNER"), DAB("DAB"), HD("HDRADIO"), XM("XM"), IPOD(
				"IPOD"), SIRIUS("SIRIUS", "SIRIUSXM"), BD(), OTHER();

		private DisplayType(String... forInput) {
			this.forInput = forInput;
		}

		public boolean matches(String input) {
			for (String s : forInput) {
				if (input.equals(s)) {
					return true;
				}
			}
			return false;
		}

		private final String[] forInput;
	};

	public DisplayManager() {
	}

	public void setAvrState(AVRState avrState) {
		this.avrState = avrState;
	}

	public IDisplay getCurrentDisplay(Zone zone) {
		InputSelect selectedInput = avrState.getState(zone, InputSelect.class);
		if (selectedInput.isSource()) {
			selectedInput = avrState.getState(Zone.Main, InputSelect.class);
		}

		if (selectedInput.isNetworked()) {
			return getDisplay(DisplayType.NETWORK);
		} else {
			final String selected = selectedInput.getSelected();

			// Modell-spezifische Displays (IPOD/BD)
			final DisplayType displayTypeForInput = avrState
					.getModelConfigurator().getDisplayTypeForInput(selected);
			if (displayTypeForInput != null) {
				return getDisplay(displayTypeForInput);
			}

			// Standards
			for (DisplayType dt : DisplayType.values()) {
				if (dt.matches(selected)) {
					return getDisplay(dt);
				}
			}

			return getDisplay(DisplayType.OTHER);
		}
	}

	private IDisplay getDisplay(DisplayType type) {
		final IDisplay ret = displays.get(type);
		if (ret == null) {
			return IDisplay.NULL_DISPLAY;
		}
		return ret;
	}

	public void setDisplay(DisplayType type, IDisplay display) {
		displays.put(type, display);
	}

	public void clearAllListener() {
		for (IDisplay d : displays.values()) {
			d.clearListener();
		}
	}

	private AVRState avrState;
	private final Map<DisplayType, IDisplay> displays = new HashMap<DisplayType, IDisplay>();

}
