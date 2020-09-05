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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class PrefixResolver {

	public PrefixResolver(Collection<IAVRState> allStates) {
		for (IAVRState s : allStates) {
			final String commandPrefix = s.getCommandPrefix();
			maxPrefix = Math.max(maxPrefix, commandPrefix.length());
			prefixMap.put(commandPrefix, s);
		}
	}

	public IAVRState find(String s) {
		int max = Math.min(maxPrefix, s.length());
		for (int i = max; i >= 2; i--) {
			String p = s.substring(0, i);
			IAVRState cl = prefixMap.get(p);
			if (cl != null) {
				return cl;
			}
		}
		return null;
	}

	private int maxPrefix = 0;
	private final Map<String, IAVRState> prefixMap = new HashMap<String, IAVRState>();
}
