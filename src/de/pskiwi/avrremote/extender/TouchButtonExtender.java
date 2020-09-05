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

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/** Längerer Druck auf Button führt die Aktion mehrfach (alle 200ms) durch */
public final class TouchButtonExtender {
	private TouchButtonExtender() {
	}

	public static void extend(View button, String s, final Runnable r) {
		final Handler mHandler = new Handler();
		final Runnable down = new Runnable() {

			public void run() {
				r.run();
				mHandler.postDelayed(this, 200);
			}
		};
		button.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					mHandler.post(down);
				}

				if (event.getAction() == MotionEvent.ACTION_UP
						|| event.getAction() == MotionEvent.ACTION_CANCEL) {
					mHandler.removeCallbacks(down);
				}
				return false;
			}
		});

	}

}
