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
package de.pskiwi.avrremote.menu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.HelpType;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.core.ResilentConnector;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.RenameService.QuickSelect;
import de.pskiwi.avrremote.log.Logger;

public final class QuickMenu {

	public QuickMenu(Zone zone, ResilentConnector connector, Activity activity,
			RenameService renameService) {
		this.zone = zone;
		this.connector = connector;
		this.activity = activity;
		this.renameService = renameService;
	}

	public void showContextMenu() {
		show(true);
	}

	public void show() {
		show(false);
	}

	private void show(final boolean context) {

		if (!context && AVRSettings.isShowHelp(activity, HelpType.Quick)) {
			Toast
					.makeText(activity, R.string.longPressHelp,
							Toast.LENGTH_SHORT).show();
		}
		final QuickSelect quickSelect = renameService.getQuickSelect(zone);

		final String title;
		if (context) {
			title = activity.getString(R.string.Define) + " "
					+ activity.getString(R.string.QuickSelect);
		} else {
			title = activity.getString(R.string.QuickSelect);
		}

		new AlertDialog.Builder(activity).setTitle(title).setItems(
				quickSelect.getValues(), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (which >= 0) {
							final int mode = quickSelect.translate(which);
							if (mode > 0 && mode <= RenameService.QUICK_COUNT) {
								Logger.info("quick " + mode);
								if (!context) {
									quick(mode);
								} else {
									quickMemory(mode);
								}
							} else {
								Logger.info("illegal quick res:" + mode
										+ " pos:" + which);
							}
						}
					}
				}).show();
	}

	private void quick(int i) {
		connector.send(quickString(i));
	}

	private String quickString(int i) {
		return zone.getQuickPrefix() + "QUICK" + i;
	}

	private void quickMemory(int i) {
		connector.send(quickString(i) + " MEMORY");
	}

	private final RenameService renameService;
	private final Activity activity;
	private final Zone zone;
	private final ResilentConnector connector;
}
