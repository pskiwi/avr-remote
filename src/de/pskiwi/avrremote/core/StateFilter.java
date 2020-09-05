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

import java.util.Arrays;

public final class StateFilter implements IStateFilter {

	public StateFilter(Class<? extends IAVRState>... clazz) {
		this.clazz = clazz;
	}

	public boolean accept(Class<? extends IAVRState> toCheck) {
		for (Class<? extends IAVRState> c : clazz) {
			if (c == toCheck) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "StateFilter " + Arrays.toString(clazz);
	}

	private final Class<? extends IAVRState>[] clazz;
}
