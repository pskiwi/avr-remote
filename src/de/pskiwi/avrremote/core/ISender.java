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

public interface ISender {
	void send(String command);

	void sendCommand(Zone zone, IAVRState s, String cmd);

	void query(Zone zone, IAVRState s);

	boolean isQueueEmpty();

	public final static ISender NULL_SENDER = new ISender() {

		public void sendCommand(Zone zone, IAVRState s, String cmd) {
		}

		public void send(String command) {
		}

		public void query(Zone zone, IAVRState s) {
		}

		public boolean isQueueEmpty() {
			return true;
		}
	};

}
