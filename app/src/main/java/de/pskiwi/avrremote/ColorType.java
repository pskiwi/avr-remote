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

import android.graphics.Color;
import android.widget.TextView;
import de.pskiwi.avrremote.log.Logger;

public enum ColorType {
	Default(Color.GRAY), White(Color.WHITE), Gray(Color.GRAY), Black(
			Color.BLACK), Red(Color.RED), Blue(Color.BLUE), Green(Color.GREEN);

	private ColorType(int color) {
		this.color = color;
	}

	public static ColorType fromString(String text) {
		try {
			return valueOf(text);
		} catch (Throwable x) {
			Logger.error("illegal color[" + text + "]", x);
			return Default;
		}
	}

	public int getColor() {
		return color;
	}

	public void setTextColor(TextView tt) {
		tt.setTextColor(color);
	}

	private final int color;

}
