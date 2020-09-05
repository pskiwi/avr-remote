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

import android.os.Handler;
import android.widget.TextView;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.ZoneMode;
import de.pskiwi.avrremote.core.display.IDisplayStatus;

/** Anzeige des aktuellen Titels */
public final class TextDisplayHandler {

	public TextDisplayHandler(TextView view) {
		this.view = view;
	}

	public void update(final ZoneState currentZoneState,
			IDisplayStatus displayStatus) {
		final CharSequence newText;
		if (currentZoneState != null
				&& currentZoneState.getState(ZoneMode.class).isOn()) {
			newText = displayStatus.getPlayInfo();
		} else {
			newText = "";
		}

		handler.post(new Runnable() {
			public void run() {
				view.setText(newText);
			}
		});

	}

	public void updateTheme(AVRTheme avrTheme) {
		avrTheme.setTextColor(view);

	}

	private final TextView view;
	private final Handler handler = new Handler();

}
