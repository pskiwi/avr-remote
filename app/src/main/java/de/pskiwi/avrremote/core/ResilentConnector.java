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
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import de.pskiwi.avrremote.EnableManager;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

/** Hält die Verbindung zum AVR. */
public final class ResilentConnector implements ISender {

	// Verwaltung des Verbindungsthreads.
	// Paketprivat statt private, damit ThreadHandlerTest drankommt: der
	// ResilentConnector selbst ist aus einem JVM-Test nicht zu bauen
	// (EnableManager, ModelConfigurator, Context), diese Klasse dagegen kennt
	// nur Thread und Logger. Gleiches Muster wie ModelConfigurator.createModel().
	final static class ThreadHandler {

		// isAlive(), nicht nur "!= null": ein gestorbener Thread wuerde
		// reconfigure() sonst glauben machen, es laufe noch ein Reconnect-Loop,
		// und der Kurzschluss dort startet dann nie einen neuen. Aktuell kann
		// das nicht passieren - stop() nullt das Feld - aber die Fehlerklasse
		// ist genau die, die den Reconnect schon einmal stillschweigend
		// beerdigt hat.
		public synchronized boolean isDefined() {
			return thread != null && thread.isAlive();
		}

		public synchronized void stop() {
			if (thread != null) {
				Logger.info("stop connector");
				thread.interrupt();
				// Bewusst kein join: der Aufrufer ist ueber forceReconnect()
				// der UI-Thread, und der Thread, auf den zu warten waere,
				// steckt typischerweise in checkAddress() - isReachable() und
				// Socket-Connects reagieren nicht auf interrupt(). Das Warten
				// liefe also verlaesslich in seinen Timeout und der Code machte
				// danach ohnehin weiter, ohne dass der Thread gestorben waere.
				// Ueber generation ist er bereits entwertet, kann nichts mehr
				// publizieren und beendet sich beim naechsten isCurrent().
				// "detached", nicht "stopped": der Thread laeuft u.U. noch
				// Sekunden weiter und loggt dabei. Name mitgeben, sonst ist im
				// Log nicht zu unterscheiden, wer da noch schreibt.
				Logger.info("Reconnector:connector detached ("
						+ thread.getName() + ")");
				thread = null;
			}
		}

		public synchronized void start(Runnable runner, int epoch) {
			// Epoche im Namen: nach einem stop() koennen kurzzeitig mehrere
			// Threads leben und ins selbe Log schreiben
			thread = new Thread(runner, "ResilentThreadHandler-" + epoch);
			thread.setDaemon(true);
			thread.start();
		}

		private Thread thread;
	}

	private class Reconnector implements Runnable {

		Reconnector(int epoch) {
			this.epoch = epoch;
		}

		// Ein Thread, der z.B. in einem blockierenden Connect/Ping haengt,
		// reagiert nicht auf interrupt(). Damit so ein "Zombie"-Thread nach
		// einem stop()/reconfigure() nicht spaeter doch noch den geteilten
		// "connector" Status ueberschreibt, darf er nur publizieren, solange
		// seine Generation noch aktuell ist.
		private boolean isCurrent() {
			return generation.get() == epoch;
		}

		public void run() {
			while (!Thread.currentThread().isInterrupted() && isCurrent()) {
				try {
					publishConnector(epoch, IConnector.NULL_CONNECTOR);
					Logger.info("Reconnector:build new connection to ["
							+ connectionConfig + "]");
					boolean reachable = connectionConfig.checkAddress(false);
					Logger.debug("Reconnector:reachable " + connectionConfig
							+ " : " + reachable);
					// reachable nicht direkt in Status setzen, um mehrfache
					// Updates zu vermeiden

					if (!isCurrent()) {
						return;
					}

					// Auf jeden Fall versuchen, u.U. ist der Test auf manchen
					// Modellen nicht eindeutig.
					IConnector newConnector;
					try {
						newConnector = new Connector(connectionConfig,
								SEND_DELAY, eventListener);
					} catch (Throwable x) {
						// Bei Fehler Reachable setzen, sonst wird Reachable
						// über "Connected" mit gesetzt
						if (isCurrent()) {
							enableManager.setStatus(StatusFlag.Reachable,
									reachable);
						}
						throw x;
					}

					if (!publishConnector(epoch, newConnector)) {
						// wurde waehrend des (nicht unterbrechbaren) Connects
						// bereits gestoppt/neu gestartet -> verwerfen
						Logger.info("Reconnector:superseded while connecting -> discard ["
								+ connectionConfig + "]");
						newConnector.close();
						return;
					}

					reconnectDelayIndex = 0;
					Logger.info("Reconnector:connection to ["
							+ connectionConfig + "] established");
					// ab hier newConnector statt des geteilten Feldes: das kann
					// ein anderer Thread laengst wieder geleert haben, und
					// NULL_CONNECTOR.waitUntilClosed() kehrt sofort zurueck -
					// wir wuerden eine zweite Verbindung aufbauen und diese
					// hier offen stehen lassen.
					fireConnected(newConnector, true);
					newConnector.waitUntilClosed();
					Logger.info("Reconnector:Reconnector:connection to ["
							+ connectionConfig + "] closed");

					if (!isCurrent()) {
						return;
					}

					// Reachable-Status direkt aktualisieren, nicht erst 15sec
					// warten (schnelleres Feedback)
					reachable = connectionConfig.checkAddress(true);
					Logger.debug("Reconnector:reachable [" + connectionConfig
							+ "] : " + reachable);
					// Nochmal pruefen: checkAddress blockiert bis zu 1,5sec
					// (Ping- und Port-Timeouts, nicht unterbrechbar). In der
					// Zeit kann laengst ein neuer Thread verbunden haben, und
					// dessen Verbindung wuerden die drei Zeilen hier abraeumen
					// - setStatus(Reachable,false) loescht per Fallthrough
					// Connected, Power und alle Zonen gleich mit.
					if (isCurrent()) {
						// falls !reachable, wird connected direkt gelöscht
						enableManager.setStatus(StatusFlag.Reachable, reachable);
						fireConnected(newConnector, false);
						publishConnector(epoch, IConnector.NULL_CONNECTOR);
					}

				} catch (InterruptedException x) {
					Logger.info("Reconnector:connector interrupted -> return ["
							+ connectionConfig + "]");
					return;
				} catch (IOException x) {
					Logger.error("Reconnector:IOException [" + connectionConfig
							+ "]", x);
					if (isCurrent()) {
						enableManager.setStatus(StatusFlag.Connected, false);
					}
				} catch (Throwable x) {
					Logger.error("Reconnector:connection failed", x);
				}
				if (!isCurrent()) {
					return;
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
		private final int epoch;

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

	/**
	 * Wie {@link #reconfigure(Context)}, aber ohne die "laeuft schon /
	 * Config unveraendert" Kurzschluss-Pruefung. Wird beim Resume einer
	 * Activity benutzt: dort ist "isRunning()" nicht vertrauenswuerdig, da
	 * Android den Socket/Timer waehrend des Hintergrundbetriebs (Doze,
	 * Netzwechsel) einfrieren oder stillschweigend kappen kann, ohne dass
	 * unser Code das mitbekommt.
	 */
	public void forceReconnect() {
		final ConnectionConfiguration newConfig = modelConfigurator
				.getConnectionConfig();
		Logger.info("Connector forceReconnect ip: [" + newConfig + "]");
		clearState();
		connectionConfig = newConfig;
		stopConnector();
		if (newConfig.isDefined()) {
			startConnector();
		}
	}

	private void startConnector() {
		Logger.info("Reconnector:start new connector " + connectionConfig);
		if (connectionConfig.isDefined()) {
			final int epoch = generation.incrementAndGet();
			threadHandler.start(new Reconnector(epoch), epoch);
		} else {
			Logger.info("startConnector ignored: " + connectionConfig);
		}
	}

	private void stopConnector() {
		// invalidiert einen evtl. noch laufenden "Zombie"-Thread (z.B. in
		// einem nicht unterbrechbaren Connect/Ping haengend), so dass dieser
		// keine Werte mehr publizieren darf. threadHandler.stop() wartet nicht
		// auf ihn: die Entwertung muss allein tragen, deshalb konsultiert
		// Reconnector.run() sie vor jedem Schreibzugriff auf geteilten Zustand
		// und publishConnector() macht Pruefung und Zuweisung atomar.
		generation.incrementAndGet();
		try {
			threadHandler.stop();
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

	/**
	 * Setzt das geteilte Feld nur, solange die Generation des Aufrufers noch
	 * aktuell ist - und zwar atomar gegen {@link #closeAndClearConnector()}.
	 * Ein blosses "if (isCurrent()) connector = ..." reicht nicht: faellt das
	 * Entwerten genau zwischen Pruefung und Zuweisung, haengt ein abgeloester
	 * Thread noch eine <em>lebende</em> Verbindung ins Feld, nachdem der
	 * Aufraeumer schon durch ist. Die schliesst dann niemand mehr, und
	 * isRunning() haelt sie fuer die aktuelle.
	 *
	 * @return false, wenn der Aufrufer abgeloest wurde und selbst aufraeumen muss
	 */
	private synchronized boolean publishConnector(int epoch, IConnector c) {
		if (generation.get() != epoch) {
			return false;
		}
		connector = c;
		return true;
	}

	private void closeCurrentConnection() {
		clearState();
		closeAndClearConnector();
	}

	// nicht die ganze closeCurrentConnection() synchronisieren: clearState()
	// feuert Listener, und die sollen den Monitor nicht sehen
	private synchronized void closeAndClearConnector() {
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
	private final AtomicInteger generation = new AtomicInteger();
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
