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

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Ab targetSdk 36 erzwingt Android edge-to-edge ohne Opt-Out. Keines der hier
 * verwendeten Themes hat eine ActionBar, welche die System-Leisten abfangen
 * würde - der Inhalt würde sonst darunter zeichnen.
 */
public final class EdgeToEdge {

	/**
	 * Legt System-Leisten und Display-Cutout als Padding auf den Content-View.
	 * Nach setContentView aufrufen. Dialog-Themes brauchen das nicht, sie werden
	 * ohnehin nicht bis an den Bildschirmrand gezeichnet.
	 */
	public static void apply(Activity activity) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return;
		}
		final View content = activity.findViewById(android.R.id.content);
		if (content == null) {
			return;
		}
		content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
			public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
				Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
						| WindowInsets.Type.displayCutout());
				v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
				return WindowInsets.CONSUMED;
			}
		});
		content.requestApplyInsets();
	}

	private EdgeToEdge() {
	}

}
