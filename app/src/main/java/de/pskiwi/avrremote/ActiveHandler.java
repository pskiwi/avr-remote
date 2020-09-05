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

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import de.pskiwi.avrremote.core.ResilentConnector;
import de.pskiwi.avrremote.log.Logger;

/** Wie lange bleibt die Verbindung zum AVR aktiv ? */
public final class ActiveHandler {

	private final class StopConnectorTask extends TimerTask {
		@Override
		public void run() {
			Logger.info("run stopConnectorRunnable");
			connector.stop();
		}
	}

	public ActiveHandler(ResilentConnector connector) {
		this.connector = connector;
	}

	public void contextResumed(Context context) {
		Logger.info("ActiveHandler.activity resumed " + context);
		activeContext = context;
		cancelCurrentTask();
		if (!connector.isRunning()) {
			connector.reconfigure(context);
		}
	}

	public void contextPaused(Context context) {
		Logger.info("ActiveHandler.activity paused " + context + " / "
				+ activeContext);
		if (context == activeContext) {
			Logger.info("schedule close");
			// doppelte vermeiden
			cancelCurrentTask();
			task = new StopConnectorTask();
			final int disconnectTimeout = AVRSettings
					.getDisconnectTimeout(context);
			Logger.debug("auto disconnect :" + disconnectTimeout + "sec");
			timer.schedule(task, disconnectTimeout * 1000);
			activeContext = null;
		}
	}

	// läuft gerade eine Activity
	public boolean isActive() {
		return activeContext != null;
	}

	private void cancelCurrentTask() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	@Override
	public String toString() {
		return "ActiveHandler " + connector + " " + activeContext;
	}

	// Die Variable task darf nur im EDT verändert werden !
	private TimerTask task;
	private Context activeContext;
	private final Timer timer = new Timer("StopConnector-Timer", true);
	private final ResilentConnector connector;
}
