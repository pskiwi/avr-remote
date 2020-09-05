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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.pskiwi.avrremote.EnableManager.StatusFlag;

public final class ReceiverStatus {

	public void clear() {

		// WLAN-Status sichern
		Boolean wlan = status.get(StatusFlag.WLAN);
		status.clear();
		if (wlan != null) {
			status.put(StatusFlag.WLAN, wlan);
		}
	}

	public boolean is(StatusFlag f) {
		final Boolean is = status.get(f);
		return is != null && is;
	}

	public ReceiverStatus copy() {
		final ReceiverStatus ret = new ReceiverStatus();
		ret.status.putAll(status);
		return ret;
	}

	@Override
	public String toString() {
		final StringBuilder ret = new StringBuilder("[");
		for (Map.Entry<StatusFlag, Boolean> e : status.entrySet()) {
			if (ret.length() > 1) {
				ret.append(", ");
			}
			ret.append(e.getKey() + ":" + e.getValue());
		}
		ret.append("]");
		return ret.toString();
	}

	public void reset(StatusFlag key) {
		status.remove(key);
	}

	public void unset(StatusFlag key) {
		status.put(key, Boolean.FALSE);
	}

	public void set(StatusFlag s) {
		status.put(s, Boolean.TRUE);
	}

	public void set(StatusFlag s, boolean state) {
		status.put(s, state);
	}

	public boolean defined(StatusFlag s) {
		return status.get(s) != null;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof ReceiverStatus)) {
			return false;
		}
		ReceiverStatus other = (ReceiverStatus) o;
		if (other.status.size() != status.size()) {
			return false;
		}
		for (Map.Entry<StatusFlag, Boolean> e : status.entrySet()) {
			if (!e.getValue().equals(other.status.get(e.getKey()))) {
				return false;
			}
		}
		return true;
	}

	// ConcurrentHashMapConcurrentHashMap gegen mehrfache CME.
	private final Map<StatusFlag, Boolean> status = new ConcurrentHashMap<StatusFlag, Boolean>();

}
