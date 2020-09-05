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
package de.pskiwi.avrremote.core.display;

import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.log.Logger;

/** Erkennung ob der Receiver nicht mehr korrekt reagiert (Stecker ziehen !) */
public final class ReceiverHangDetector {

	public ReceiverHangDetector(IDisplayListener displayListener) {
		this.displayListener = displayListener;
	}

	public void screenUpdated() {
		lastUpdate = System.currentTimeMillis();
		missed = 0;
		waitingForUpdate = false;
	}

	public void updateExpected() {
		// Wird bereits ein nicht eingetroffenes Update erwartet
		if (waitingForUpdate
				&& System.currentTimeMillis() - lastUpdate > MAX_RESPONSE_TIME) {
			Logger.info("ReceiverHangDetector missed:" + missed);
			// Wieviel ist seit dem letzten Fehler vergangen
			if (System.currentTimeMillis() - lastMiss > MAX_RESPONSE_TIME) {
				missed++;
				lastMiss = System.currentTimeMillis();
			}
		} else {
			waitingForUpdate = true;
		}
		if (missed > 2) {
			missed = 0;
			Logger.error("Receceiver hang detected (" + missed + ")", null);
			displayListener.displayInfo(R.string.ReceiverHangDetected);
		}
	}

	private boolean waitingForUpdate;
	private long lastUpdate = 0;
	private long lastMiss = 0;
	private int missed;
	private final IDisplayListener displayListener;
	private static final int MAX_RESPONSE_TIME = 1000;
}
