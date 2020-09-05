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
/**
 * 
 */
package de.pskiwi.avrremote.extender;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.core.IStateListener;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.AbstractSwitch;

public final class SwitchButtonExtender {

	private SwitchButtonExtender() {
	}

	public static void extend(final Button button, final Context ctx,
			ZoneState zone, final Class<? extends AbstractSwitch> s) {
		final AbstractSwitch state = zone.getState(s);
		zone.setListener(new IStateListener<AbstractSwitch>() {
			public void changedState(AbstractSwitch s) {
				button.setText(ctx.getString(state.getDisplayId())
						+ " : "
						+ (s.isOn() ? ctx.getString(R.string.On) : ctx
								.getString(R.string.Off)));
			}
		}, s);
		button.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				state.switchState();
			}
		});
	}
}