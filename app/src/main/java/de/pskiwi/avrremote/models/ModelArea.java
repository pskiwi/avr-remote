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
package de.pskiwi.avrremote.models;

import java.util.Locale;

public enum ModelArea {
	Europe, NorthAmerica, Japan, China, Other;

	public static ModelArea autoDetect() {
		final String c = Locale.getDefault().getCountry().toLowerCase();
		if ("us|ca".contains(c)) {
			return NorthAmerica;
		}

		if ("de|nl|it|uk|gb|fr|dk|se|no|ch|at|pl|be|pt|es|lu|hu|gr|fi|cz|hr"
				.contains(c)) {
			return Europe;
		}

		if ("jp".contains(c)) {
			return Japan;
		}

		if ("cn".contains(c)) {
			return China;
		}

		return ModelArea.Other;
	}
}
