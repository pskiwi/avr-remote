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
import android.os.SystemClock;
import de.pskiwi.avrremote.core.ResilentConnector;
import de.pskiwi.avrremote.log.Logger;

/** Wie lange bleibt die Verbindung zum AVR aktiv ? */
public final class ActiveHandler {

	private final class StopConnectorTask extends TimerTask {
		@Override
		public void run() {
			Logger.info("run stopConnectorRunnable");
			// Der Task kann durch Doze/App-Standby beliebig verzoegert werden
			// und dann erst feuern, wenn laengst wieder eine Activity im
			// Vordergrund ist - und wuerde die Verbindung abraeumen, die
			// contextResumed() gerade aufgebaut hat. cancelCurrentTask()
			// gewinnt dieses Rennen nur manchmal.
			if (isActive()) {
				Logger.info("stopConnectorRunnable: activity active -> skip");
				return;
			}
			connector.stop();
		}
	}

	public ActiveHandler(ResilentConnector connector) {
		this.connector = connector;
	}

	public void contextResumed(Context context) {
		Logger.info("ActiveHandler.activity resumed " + context);
		activeContext = context;
		// "task != null" allein reicht nicht: der Timer kann durch Doze/App-
		// Standby beliebig verzoegert werden, auch weit ueber disconnectTimeout
		// hinaus, ohne dass StopConnectorTask je gefeuert hat. Stattdessen die
		// tatsaechlich vergangene Zeit seit contextPaused() gegen den Timeout
		// pruefen.
		final boolean quickReturn = pausedAtElapsedRealtime >= 0
				&& SystemClock.elapsedRealtime() - pausedAtElapsedRealtime < AVRSettings
						.getDisconnectTimeout(context) * 1000L;
		pausedAtElapsedRealtime = -1;
		cancelCurrentTask();
		if (quickReturn) {
			if (!connector.isRunning()) {
				connector.reconfigure(context);
			}
		} else {
			// Laenger im Hintergrund (oder der Task konnte durch Doze/App-
			// Standby verzoegert werden) -> "isRunning()" beruht evtl. auf
			// einem Socket, den Android stillschweigend gekappt hat. Statt
			// dem zu vertrauen, Verbindung immer frisch aufbauen.
			connector.forceReconnect();
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
			pausedAtElapsedRealtime = SystemClock.elapsedRealtime();
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
	// -1 = kein Pause-Zeitpunkt gemerkt (z.B. allererstes contextResumed())
	private long pausedAtElapsedRealtime = -1;
	// volatile: wird vom Timer-Thread in StopConnectorTask gelesen
	private volatile Context activeContext;
	private final Timer timer = new Timer("StopConnector-Timer", true);
	private final ResilentConnector connector;
}
