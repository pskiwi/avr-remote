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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.Context;
import de.pskiwi.avrremote.EnableManager;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

/** Hält die Verbindung zum AVR. */
public final class ResilentConnector implements ISender {

	// Verwaltung des Verbindungsthreads
	private final static class ThreadHandler {

		public synchronized boolean isDefined() {
			return thread != null;
		}

		public synchronized void join() {
			if (thread != null) {
				Logger.info("stop connector");
				thread.interrupt();
				try {
					// 1000 wg. ANR Broadcast of Intent {
					// act=android.intent.action.SCREEN_OFF
					thread.join(1000);
				} catch (InterruptedException e) {
					Logger.error("Reconnector:join failed", e);
				} finally {
					thread = null;
				}
				Logger.info("Reconnector:connector stopped");
			}
		}

		public synchronized void start(Runnable runner) {
			thread = new Thread(runner, "ResilentThreadHandler");
			thread.setDaemon(true);
			thread.start();
		}

		private Thread thread;
	}

	private class Reconnector implements Runnable {

		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					connector = IConnector.NULL_CONNECTOR;
					Logger.info("Reconnector:build new connection to ["
							+ connectionConfig + "]");
					boolean reachable = connectionConfig.checkAddress(false);
					Logger.debug("Reconnector:reachable " + connectionConfig
							+ " : " + reachable);
					// reachable nicht direkt in Status setzen, um mehrfache
					// Updates zu vermeiden

					// Auf jeden Fall versuchen, u.U. ist der Test auf manchen
					// Modellen nicht eindeutig.
					try {
						connector = new Connector(connectionConfig, SEND_DELAY,
								eventListener);
					} catch (Throwable x) {
						// Bei Fehler Reachable setzen, sonst wird Reachable
						// über "Connected" mit gesetzt
						enableManager
								.setStatus(StatusFlag.Reachable, reachable);
						throw x;
					}
					reconnectDelayIndex = 0;
					Logger.info("Reconnector:connection to ["
							+ connectionConfig + "] established");
					fireConnected(connector, true);
					connector.waitUntilClosed();
					Logger.info("Reconnector:Reconnector:connection to ["
							+ connectionConfig + "] closed");
					// Reachable-Status direkt aktualisieren, nicht erst 15sec
					// warten (schnelleres Feedback)
					reachable = connectionConfig.checkAddress(true);
					Logger.debug("Reconnector:reachable [" + connectionConfig
							+ "] : " + reachable);
					// falls !reachable, wird connected direkt gelöscht
					enableManager.setStatus(StatusFlag.Reachable, reachable);
					fireConnected(connector, false);
					connector = IConnector.NULL_CONNECTOR;

				} catch (InterruptedException x) {
					Logger.info("Reconnector:connector interrupted -> return ["
							+ connectionConfig + "]");
					return;
				} catch (IOException x) {
					Logger.error("Reconnector:IOException [" + connectionConfig
							+ "]", x);
					enableManager.setStatus(StatusFlag.Connected, false);
				} catch (Throwable x) {
					Logger.error("Reconnector:connection failed", x);
				}
				try {
					if (reconnectDelayIndex < RECONNECT_DELAY.length - 1) {
						reconnectDelayIndex++;
					}
					Logger.info("Reconnector:wait "
							+ RECONNECT_DELAY[reconnectDelayIndex]
							+ " sec. for reconnect");
					Thread.sleep(RECONNECT_DELAY[reconnectDelayIndex] * 1000);
				} catch (InterruptedException e) {
					Logger.info("Reconnector:connector interrupted -> return");
					return;
				}
			}
		}

		private int reconnectDelayIndex = 0;

	}

	public ResilentConnector(EnableManager enableManager,
			IEventListener eventListener, ModelConfigurator modelConfigurator) {
		this.enableManager = enableManager;
		this.eventListener = eventListener;
		this.modelConfigurator = modelConfigurator;
	}

	public void reconfigure(Context ctx) {
		final ConnectionConfiguration newConfig = modelConfigurator
				.getConnectionConfig();
		Logger.info("Connector reconfigure ip: [" + connectionConfig + ":"
				+ "]");
		if (!threadHandler.isDefined() || !newConfig.equals(connectionConfig)) {
			clearState();
			connectionConfig = newConfig;

			stopConnector();
			if (newConfig.isDefined()) {
				startConnector();
			}
		}

	}

	private void startConnector() {
		Logger.info("Reconnector:start new connector " + connectionConfig);
		if (connectionConfig.isDefined()) {
			threadHandler.start(new Reconnector());
		} else {
			Logger.info("startConnector ignored: " + connectionConfig);
		}
	}

	private void stopConnector() {
		try {
			threadHandler.join();
		} finally {
			closeCurrentConnection();
		}
	}

	public void triggerReconnect() {
		stopConnector();
		startConnector();
	}

	public boolean isConnnected() {
		return connector.isConnected();
	}

	public void query(Zone zone, IAVRState s) {
		connector.query(zone, s);
	}

	public void send(String command) {
		connector.send(command);
	}

	public void setConnectorListener(IConnectorListener l) {
		connector.setConnectorListener(l);
	}

	public boolean isQueueEmpty() {
		return connector.isQueueEmpty();
	}

	public void sendCommand(Zone zone, IAVRState s, String cmd) {
		connector.sendCommand(zone, s, cmd);
	}

	public void addListener(IConnectionListener l) {
		listener.add(l);
		initListener(l);
	}

	private void initListener(IConnectionListener l) {
		if (isConnnected()) {
			l.openedConnection(connector);
		} else {
			l.closedConnection(connector);
		}
	}

	public void addListenerFirst(IConnectionListener l) {
		listener.add(0, l);
		initListener(l);
	}

	public void removeListener(IConnectionListener l) {
		listener.remove(l);
	}

	private void fireConnected(IConnector con, boolean connected) {
		enableManager.setStatus(StatusFlag.Connected, connected);
		for (IConnectionListener cl : listener) {
			if (connected) {
				cl.openedConnection(con);
			} else {
				cl.closedConnection(con);
			}
		}
	}

	private void closeCurrentConnection() {
		clearState();

		try {
			connector.close();
		} finally {
			connector = IConnector.NULL_CONNECTOR;
		}
	}

	public void stop() {
		clearState();
		fireConnected(connector, false);
		stopConnector();
	}

	public void reconnect() {
		closeCurrentConnection();
	}

	public boolean isRunning() {
		return connector != IConnector.NULL_CONNECTOR && isConnnected();
	}

	private void clearState() {
		enableManager.reset();
	}

	private final IEventListener eventListener;
	private final EnableManager enableManager;
	private final ModelConfigurator modelConfigurator;
	private final List<IConnectionListener> listener = new CopyOnWriteArrayList<IConnectionListener>();;
	private volatile IConnector connector = IConnector.NULL_CONNECTOR;
	private ConnectionConfiguration connectionConfig = ConnectionConfiguration.UNDEFINED;
	private final ThreadHandler threadHandler = new ThreadHandler();
	private static int[] RECONNECT_DELAY = { 1, 2, 4, 8, 16 };
	// totale Wartezeit für einen Connect-Test
	public final static int RECONNECT_WAIT_TIME;
	static {
		int sum = 0;
		for (int i = 0; i < RECONNECT_DELAY.length; i++) {
			sum += RECONNECT_DELAY[i];
		}
		RECONNECT_WAIT_TIME = (sum + 2) * 1000;
	}
	private static final int SEND_DELAY = 100;

}
