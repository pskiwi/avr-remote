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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.view.View;
import de.pskiwi.avrremote.log.Logger;

public final class EnableManager {

	public enum StatusFlag {
		Logging, WLAN, Reachable, Connected, Power, Zone1, Zone2, Zone3, Zone4
	}

	private final static class StatusView {

		public StatusView(View v, StatusFlag f, AtomicBoolean enabled) {
			this.view = v;
			this.flag = f;
			this.enabled = enabled;
		}

		public boolean isEnabled() {
			return enabled.get();
		}

		private final AtomicBoolean enabled;
		private final View view;
		private final StatusFlag flag;

	}

	public interface IStatusListener {
		void statusChanged(ReceiverStatus currentStatus);
	}

	public final class ViewList implements IStatusListener {

		public View addView(View v, StatusFlag f, AtomicBoolean enable) {
			list.add(new StatusView(v, f, enable));
			return v;
		}

		public View addView(View v, StatusFlag f) {
			list.add(new StatusView(v, f, new AtomicBoolean(true)));
			return v;
		}

		public void setEnabled(Set<StatusFlag> statusSet) {
		}

		public int getSize() {
			return list.size();
		}

		public void statusChanged(ReceiverStatus currentStatus) {
			for (StatusView v : list) {
				updateView(currentStatus, v);
			}
		}

		private void updateView(ReceiverStatus currentStatus, StatusView v) {
			final Boolean status = currentStatus.is(v.flag);
			v.view.setEnabled(v.isEnabled() && status != null && status);
		}

		public void update(View view) {
			for (StatusView v : list) {
				if (v.view == view) {
					updateView(connectionStatus, v);
				}
			}
		}

		@Override
		public String toString() {
			return "ListView #" + list.size();
		}

		private final List<StatusView> list = new ArrayList<StatusView>();

	}

	public ViewList createViewList() {
		return new ViewList();
	}

	/**
	 * Synchronisiert, weil es ein Lesen-Aendern-Vergleichen ist und die
	 * Aufrufer der UI-Thread, der StopConnector-Timer und ein oder mehrere
	 * Reconnect-Threads zugleich sind: ueberlappend ging sonst ein Flag
	 * verloren, oder die Listener bekamen einen halb gebauten Status - Buttons,
	 * die grau blieben bis zum naechsten Wechsel. Die Sperre deckt nur ein paar
	 * Map-Zugriffe und ein post() ab, die Listener laufen danach auf dem
	 * UI-Thread und ohne sie.
	 */
	public synchronized void setStatus(StatusFlag s, boolean add) {
		final ReceiverStatus oldStatus = connectionStatus.copy();
		if (add) {
			// Automatisch mit setzen
			switch (s) {
			case Power:
				connectionStatus.set(StatusFlag.Connected);
				// BREAK fehlt absichtlich !
			case Connected:
				connectionStatus.set(StatusFlag.Reachable);
				break;
			}
			connectionStatus.set(s);
		} else {
			// REMOVED
			switch (s) {
			case Reachable:
				connectionStatus.unset(StatusFlag.Reachable);
				// BREAK fehlt absichtlich !
			case Connected:
				connectionStatus.unset(StatusFlag.Connected);
				// BREAK fehlt absichtlich !
			case Power:
				connectionStatus.unset(StatusFlag.Power);
				connectionStatus.reset(StatusFlag.Zone1);
				connectionStatus.reset(StatusFlag.Zone2);
				connectionStatus.reset(StatusFlag.Zone3);
				connectionStatus.reset(StatusFlag.Zone4);
				break;
			default:
				connectionStatus.unset(s);
				break;
			}
		}
		if (!oldStatus.equals(connectionStatus)) {
			Logger.info("EnableManager setEnabled " + s + " =  " + add + " -> "
					+ connectionStatus);
			fireListener();
		}
	}

	// Immer nur ein Listener pro Klasse
	public void setClassListener(IStatusListener l) {
		listener.put(l.getClass(), l);
		fireListener();
	}

	public void removeClassListener(Class<? extends IStatusListener> class1) {
		listener.remove(class1);
	}

	// synchronized wie setStatus(): eine Kopie mitten aus dessen Umbau, die
	// nach dessen eigenem post() eingereiht wird, bliebe sonst als letzter
	// Stand stehen
	private synchronized void fireListener() {
		final ReceiverStatus current = connectionStatus.copy();
		handler.post(new Runnable() {
			public void run() {
				for (IStatusListener l : listener.values()) {
					l.statusChanged(current);
				}
			}
		});
	}

	public synchronized ReceiverStatus getCurrentStatus() {
		return connectionStatus.copy();
	}

	public void reinitListener() {
		fireListener();
	}

	public synchronized void reset() {
		connectionStatus.clear();

		fireListener();
	}

	@Override
	public String toString() {
		return "EnableManager " + connectionStatus;
	}

	private final Handler handler = new Handler();
	private final ReceiverStatus connectionStatus = new ReceiverStatus();
	private final Map<Class<?>, IStatusListener> listener = new ConcurrentHashMap<Class<?>, IStatusListener>();

}
