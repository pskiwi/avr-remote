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
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

public final class ScreenInfo {

	public ScreenInfo(Context ctx) {

		display = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
				.getDefaultDisplay();
		metrics = new DisplayMetrics();
		display.getMetrics(metrics);
		phyWidth = metrics.widthPixels / metrics.xdpi;
		phyHeight = metrics.heightPixels / metrics.ydpi;
	}

	public DisplayMetrics getMetrics() {
		return metrics;
	}

	public double getSquareWidth() {
		return Math.sqrt(phyWidth * phyWidth + phyHeight * phyHeight);
	}

	public float getPhyHeight() {
		return phyHeight;
	}

	public float getPhyWidth() {
		return phyWidth;
	}

	public boolean isTablet() {
		return getSquareWidth() > 4.5f;
	}

	@Override
	public String toString() {
		return "ScreenInfo sw:" + getSquareWidth() + " tablet:" + isTablet()
				+ " xw:" + phyWidth + " yw:" + phyHeight + " xp/d:"
				+ metrics.widthPixels + "/" + metrics.xdpi + "   yp/d:"
				+ metrics.heightPixels + "/" + metrics.ydpi;
	}

	private final Display display;
	private final DisplayMetrics metrics;
	private final float phyWidth;
	private final float phyHeight;

}
