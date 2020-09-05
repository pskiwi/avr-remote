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
package de.pskiwi.avrremote.core;

import java.util.ArrayList;
import java.util.List;

public class SelectionBuilder {
	public void add(String... vals) {
		for (String s : vals) {
			data.add(s);
		}
	}

	public Selection create() {
		return new Selection(data.toArray(new String[data.size()]));
	}

	private final List<String> data = new ArrayList<String>();
}
