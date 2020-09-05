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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import de.pskiwi.avrremote.EnableManager;
import de.pskiwi.avrremote.ReceiverStatus;
import de.pskiwi.avrremote.EnableManager.IStatusListener;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.display.DisplayManager;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class AVRState implements IEventListener {

	public AVRState(final ISender sender, EnableManager enableManager,
			IGUIExecutor guiExecutor, DisplayManager displayManager,
			ModelConfigurator modelConfigurator) {

		this.modelConfigurator = modelConfigurator;
		ZoneState mainZone = new ZoneState(sender, Zone.Main, enableManager,
				guiExecutor, displayManager, modelConfigurator);
		zoneState.put(Zone.Main, mainZone);
		zoneState.put(Zone.Z2, new ZoneState(sender, Zone.Z2, enableManager,
				guiExecutor, displayManager, modelConfigurator));
		zoneState.put(Zone.Z3, new ZoneState(sender, Zone.Z3, enableManager,
				guiExecutor, displayManager, modelConfigurator));
		zoneState.put(Zone.Z4, new ZoneState(sender, Zone.Z4, enableManager,
				guiExecutor, displayManager, modelConfigurator));
		activeZoneCount = zoneState.size();
		final AtomicReference<Thread> checkThread = new AtomicReference<Thread>();

		enableManager.setClassListener(new IStatusListener() {
			private boolean lastConnected;
			private boolean lastPower;

			public void statusChanged(ReceiverStatus currentStatus) {
				Logger.info("AVRState:zone state update ..." + currentStatus
						+ " stateFilter:" + stateFilter);

				// Nicht verbunden, alles zurücksetzen ...
				if (!currentStatus.is(StatusFlag.Connected)) {
					Logger.info("AVRState:zone state reset");
					for (ZoneState zs : allActive()) {
						zs.resetState(stateFilter);
					}
					lastConnected = false;
					lastPower = false;
					return;
				}

				// Nur falls Verbindung oder Power sich geändert hat, alles neu
				// anfragen.
				if (lastConnected != currentStatus.is(StatusFlag.Connected)
						|| lastPower != currentStatus.is(StatusFlag.Power)) {
					lastConnected = currentStatus.is(StatusFlag.Connected);
					lastPower = currentStatus.is(StatusFlag.Power);

					Logger.info("AVRState:zone state update");

					for (ZoneState zs : allActive()) {
						zs.initState(stateFilter);
					}
					if (checkThread.get() != null
							&& checkThread.get().isAlive()) {
						checkThread.get().interrupt();
					}

					final Thread thread = new Thread("StateCheckThread") {
						public void run() {
							// nochmal checken ob alle Antworten da sind...
							if (!waitForReplies(sender)) {
								return;
							}
							// und ein drittes und letztes Mal ..
							waitForReplies(sender);

						};
					};
					thread.start();
					checkThread.set(thread);
				}
			}

			private boolean waitForReplies(final ISender sender) {
				// max. 5sec warten
				int count = 0;
				while (!sender.isQueueEmpty() && count < 20
						&& !Thread.currentThread().isInterrupted()) {
					try {
						Thread.sleep(250);
					} catch (InterruptedException e) {
						Logger.info("interrupted state checkthread");
						return false;
					}
					count++;
					Logger.info("wait for empty queue " + count + " empty:"
							+ sender.isQueueEmpty());
				}
				if (Thread.currentThread().isInterrupted()) {
					return false;
				}
				// Auf letzte Antworten warten
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					Logger.info("interrupted state checkthread");
					return false;
				}
				// Alle checken,ob Antwort da
				for (ZoneState zs : allActive()) {
					zs.checkDefined(stateFilter);
				}
				return true;
			}
		});
	}

	/** Liefert alle aktiven Zonen */
	private Iterable<ZoneState> allActive() {
		final List<ZoneState> ret = new ArrayList<ZoneState>(activeZoneCount);
		for (ZoneState z : zoneState.values()) {
			ret.add(z);
			if (ret.size() >= activeZoneCount) {
				return ret;
			}
		}
		return ret;
	}

	/** Setzt die Anzahl aktiver Zonen */
	public void setActiveZoneCount(int count) {
		activeZoneCount = count;
	}

	public void received(InData inData) {

		final Zone zone;
		if (inData.charAt(0) == 'Z' && inData.length() > 1
				&& Character.isDigit(inData.charAt(1))) {
			{
				zone = getZone(inData);

				inData.setOffset(2);
				String cmd = null;
				String event = inData.toString();
				if (inData.isNumber()) {
					cmd = "MV" + event;
				} else if ("ON".equals(event) || "OFF".equals(event)) {
					cmd = "ZM" + event;
				} else if (event.startsWith("QUICK")) {
					cmd = "ZM" + event;
				} else if (event.startsWith("SOURCE")) {
					cmd = "SRC" + event;
				} else {
					if (modelConfigurator.isInput(event)) {
						cmd = "SI" + event;
					} else {
						cmd = event;
					}
				}
				if (cmd != null) {
					inData = new InData(cmd);
				}
			}
		} else {
			zone = Zone.Main;
		}
		final ZoneState zs = zoneState.get(zone);
		if (zs == null) {
			throw new RuntimeException("zone not found " + zone);
		}
		zs.update(inData);
	}

	private Zone getZone(InData s) {
		char zoneChar = s.charAt(1);
		switch (zoneChar) {
		case '2':
			return Zone.Z2;
		case '3':
			return Zone.Z3;
		case '4':
			return Zone.Z4;
		}
		throw new IllegalArgumentException(s.toString());
	}

	public <T extends IAVRState> T getState(Zone z, Class<T> cl) {
		final ZoneState zz = zoneState.get(z);
		if (zz == null) {
			throw new IllegalArgumentException("" + z);
		}
		return zz.getState(cl);

	}

	public ZoneState getZone(Zone z) {
		return zoneState.get(z);
	}

	public void clearStateAndListener() {
		for (ZoneState zs : allActive()) {
			zs.clearStateAndListener();
		}
	}

	public void updateAll() {
		for (ZoneState zs : allActive()) {
			zs.notifyListener();
		}
	}

	public ModelConfigurator getModelConfigurator() {
		return modelConfigurator;
	}

	public void setStateFilter(IStateFilter stateFilter) {
		this.stateFilter = stateFilter;
	}

	private IStateFilter stateFilter = IStateFilter.DEFAULT_FILTER;
	// LinkedHashMap, damit die Zonen nach Priorität(MAIN=höchste) upgedated
	// werden.
	private int activeZoneCount;
	private final ModelConfigurator modelConfigurator;
	private final Map<Zone, ZoneState> zoneState = new LinkedHashMap<Zone, ZoneState>();

}
