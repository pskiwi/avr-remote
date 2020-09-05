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
package de.pskiwi.avrremote.extender;

import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import de.pskiwi.avrremote.core.IStateListener;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.core.RenameService.RenameCategory;
import de.pskiwi.avrremote.core.RenameService.RenameResult;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.AbstractSelect;
import de.pskiwi.avrremote.log.Logger;

public class SelectButtonExtender {

	private SelectButtonExtender() {
	}

	public static void extend(final TextView button, final Context ctx,
			ZoneState zone, final Class<? extends AbstractSelect> s,
			final Runnable onUpdate, final RenameService renameService,
			final RenameCategory category) {
		final AbstractSelect in = zone.getState(s);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				final List<RenameResult> selection = renameService.adapt(
						in.getValues(), in.getDisplayValues(), category);
				final String[] show = new String[selection.size()];
				int p = 0;
				for (RenameResult r : selection) {
					show[p] = r.getValue();
					p++;
				}

				new AlertDialog.Builder(ctx).setTitle("Select")
						.setItems(show, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								Logger.info("selected " + which);
								if (which >= 0) {
									Logger.info("selected "
											+ selection.get(which));
									in.select(selection.get(which).getKey());
								}
							}
						}).show();
			}
		});
		zone.setListener(new IStateListener<AbstractSelect>() {
			public void changedState(final AbstractSelect state) {

				String selected = state.getSelected();
				String toShow = selected;
				final List<RenameResult> selection = renameService.adapt(
						in.getValues(), in.getDisplayValues(), category);
				for (RenameResult rr : selection) {
					if (selected.equals(rr.getKey())) {
						toShow = rr.getValue();
					}
				}

				Logger.debug("updateSelect: : " + selected + "->" + toShow);
				button.setText(ctx.getString(state.getDisplayId()) + " : "
						+ toShow);
				onUpdate.run();
			}
		}, s);

	}
}
