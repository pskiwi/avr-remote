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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import de.pskiwi.avrremote.core.display.DisplayLine;

public final class IconManager {

	public IconManager(Context ctx) {
		folder = ctx.getResources().getDrawable(R.drawable.folder_small);
		listen = ctx.getResources().getDrawable(R.drawable.listen_small);
		music = ctx.getResources().getDrawable(R.drawable.music_small);
		cursor = ctx.getResources().getDrawable(R.drawable.cursor_small);
	}

	public void styleView(TextView v, DisplayLine dl, AVRTheme avrTheme) {
		String text = dl.getLine();
		Drawable icon = null;
		if (dl.isFolder()) {
			icon = folder;
		}
		if (dl.isPlayable()) {
			icon = music;
		}

		if (dl.isPlayable()) {
			icon = music;
		}
		if (text.startsWith("/")) {
			icon = listen;
			text = text.substring(1);
		}
		if (text.startsWith("*")) {
			text = text.substring(1);
		}

		if (dl.isCursor()) {
			icon = cursor;
		}
		if (icon != null) {
			text = " " + text;
		}

		// leere Zeilen (ohne Text) -> kein Icon , Media Server Bug?
		if (text.trim().length() == 0) {
			icon = null;
		}
		if (icon != null) {
			icon.setAlpha(avrTheme.getIconAlpha());
		}

		v.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
		v.setText(text);
	}

	private final Drawable folder;
	private final Drawable listen;
	private final Drawable music;
	private final Drawable cursor;
}
