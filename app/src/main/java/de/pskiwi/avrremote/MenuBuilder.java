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

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;

public final class MenuBuilder {

	private final class MenuEntry {

		public MenuEntry(String text, Runnable r) {
			this.text = text;
			this.r = r;
		}

		private final String text;
		private final Runnable r;

	}

	public MenuBuilder(Context activity, String title) {
		this.ctx = activity;
		this.title = title;
	}

	public void add(String text, Runnable r) {
		entries.add(new MenuEntry(text, r));
	}

	public void showMenu() {
		final String[] texts = new String[entries.size()];
		int c = 0;
		for (MenuEntry e : entries) {
			texts[c] = e.text;
			c++;
		}

		Builder builder = new AlertDialog.Builder(ctx).setTitle(title)
				.setItems(texts, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (which >= 0) {
							MenuEntry menuEntry = entries.get(which);
							menuEntry.r.run();
						}
					}
				});
		builder.show();
	}

	private final Context ctx;
	private final String title;
	private final List<MenuEntry> entries = new ArrayList<MenuEntry>();
}
