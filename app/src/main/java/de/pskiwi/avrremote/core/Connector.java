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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.pskiwi.avrremote.log.Logger;

public final class Connector implements ISender, IConnector {

	private final class Receiver implements Runnable {

		private boolean detectCR(final char[] data, final int count,
				final int ch) {
			// Bei NS[A|E][1-9] das nächste Zeichen ignorieren
			// Bei IP[A|E][1-9] das nächste Zeichen ignorieren
			if (count != 4) {
				return ch == CR;
			}
			if ((data[0] == 'N' && data[1] == 'S')
					|| (data[0] == 'I' && data[1] == 'P')) {
				final int nr = data[3] - '0';
				if (nr == 0) {
					return ch == CR;
				}
				// Steuerzeichen
				return false;

			}
			return ch == CR;

		}

		/**
		 * Liest ein Zeichen und überbrückt dabei den Lese-Timeout. Der ist
		 * nicht dazu da, eine Antwort zu erzwingen - von sich aus sendet ein
		 * Receiver nichts -, sondern damit Schweigen überhaupt auffällt: ohne
		 * ihn stünde read() für immer, auch auf einem Socket, den das Netz
		 * längst gekappt hat, ohne dass ein FIN angekommen wäre.
		 *
		 * @param count
		 *            bereits gelesene Zeichen der laufenden Zeile
		 * @return -1, wenn die Verbindung aufgegeben wird - wie ein Stream-Ende
		 */
		private int readChar(int count) throws IOException {
			while (true) {
				try {
					return in.read();
				} catch (SocketTimeoutException x) {
					// mitten in einer Zeile weiterwarten, sonst ginge die halb
					// gelesene Nachricht verloren
					if (count == 0 && !stillAlive()) {
						return -1;
					}
				}
			}
		}

		/**
		 * Zählt die Stille und klopft einmal an, bevor die Verbindung
		 * aufgegeben wird. Gezählt wird in Timeout-Schritten, nicht nach Uhr:
		 * gemeint ist "wir waren wach und haben nichts gehört", und die Uhr
		 * liefe auch weiter, während Doze den Thread einfriert.
		 *
		 * @return false, wenn auch die Probe unbeantwortet blieb
		 */
		private boolean stillAlive() {
			idleMillis += readTimeout;
			if (idleMillis < idleProbe) {
				return true;
			}
			if (probeSentAt == NO_PROBE) {
				probeSentAt = idleMillis;
				Logger.info("nothing received for " + idleMillis
						+ "ms -> probing");
				doSend(ALIVE_PROBE);
				return true;
			}
			if (idleMillis - probeSentAt < probeGrace) {
				return true;
			}
			Logger.error("no answer to the probe after " + idleMillis
					+ "ms -> closing the connection", null);
			return false;
		}

		public InData read() throws IOException {
			final char[] line = new char[MAX_LINE];
			int ch = readChar(0);
			int count;
			do {
				count = 0;
				while (ch != -1 && !detectCR(line, count, ch)
						&& count < MAX_LINE) {
					line[count++] = (char) ch;
					ch = readChar(count);
				}
				if (count == MAX_LINE) {
					// read garbage
					// avr braucht restart ???
					while (ch != -1 && ch != CR) {
						ch = readChar(count);
					}
					Logger.error("max input size exeeded ! ["
							+ new String(line, 0, count) + "]", null);
				}
			} while (count == MAX_LINE && ch != -1);

			if (ch == -1) {
				Logger.info("Receiver stream closed bytes:" + count);
				return null;
			}

			return new InData(line, count);
		}

		public void run() {
			while (!Thread.currentThread().isInterrupted()
					&& !socket.isClosed()) {
				try {
					final InData val = read();
					if (val == null) {
						// close() statt socket.close(): sonst bleibt der
						// Sender-Thread in take() stehen und überlebt die
						// Verbindung, die er bedient hat
						Connector.this.close();
						closeSignal.countDown();
						Logger.info("receiver socket closed -> return");
						return;
					}
					// Lebenszeichen - der Stille-Zähler fängt von vorne an
					idleMillis = 0;
					probeSentAt = NO_PROBE;
					Logger.debug("RECEIVED [" + val.toDebugString() + "] "
							+ (listener != null ? "" : "unregistered"));
					if (!val.isEmpty()) {
						// jedes Datum zählt als Lebenszeichen, nicht nur die
						// Antwort auf ALIVE_PROBE - siehe awaitResponse()
						firstDataSignal.countDown();
					}
					if (listener != null && val != null && !val.isEmpty()) {
						listener.received(val);
					}
				} catch (IOException e) {
					Logger.error(
							"read failed thread:"
									+ Thread.currentThread().isInterrupted()
									+ " con:" + socket.isConnected()
									+ " closed:" + socket.isClosed(), e);
				}
			}
			if (socket.isClosed()) {
				closeSignal.countDown();
			}
		}

		// nur vom Receiver-Thread gelesen und geschrieben
		private int idleMillis;
		private int probeSentAt = NO_PROBE;

		private static final int MAX_LINE = 256;
		private static final int NO_PROBE = -1;
	}

	private final class Sender implements Runnable {

		public void run() {
			while (!Thread.currentThread().isInterrupted()
					&& !socket.isClosed()) {
				try {
					final String take = sendQueue.take();
					out.write(take + CR);
					out.flush();
					Logger.info("SEND [" + take + "] ");
					Thread.sleep(sendDelay);

				} catch (InterruptedException x) {
					Logger.info("sender interrupted return");
					return;
				} catch (Exception e) {
					Logger.error(
							"Sender failed thread:"
									+ Thread.currentThread().isInterrupted()
									+ " con:" + socket.isConnected()
									+ " closed:" + socket.isClosed(), e);
				}
			}
			if (socket.isClosed()) {
				closeSignal.countDown();
			}
		}
	}

	public Connector(ConnectionConfiguration connectionConfiguration,
			int sendDelay, IEventListener eventListener) throws Exception {
		this(connectionConfiguration, sendDelay, eventListener,
				HANDSHAKE_TIMEOUT, READ_TIMEOUT, IDLE_PROBE, PROBE_GRACE);
	}

	// Paketprivat mit expliziten Zeiten, damit ConnectorTest "der Receiver
	// antwortet nicht" und "die Verbindung verstummt" in Millisekunden
	// durchspielen kann statt in Sekunden und Minuten. Gleiches Muster wie
	// ResilentConnector.ThreadHandler und ModelConfigurator.createModel(String).
	Connector(ConnectionConfiguration connectionConfiguration, int sendDelay,
			IEventListener eventListener, int handshakeTimeout,
			int readTimeout, int idleProbe, int probeGrace) throws Exception {
		this.connectionConfiguration = connectionConfiguration;
		this.sendDelay = sendDelay;
		this.handshakeTimeout = handshakeTimeout;
		this.readTimeout = readTimeout;
		this.idleProbe = idleProbe;
		this.probeGrace = probeGrace;
		listener = eventListener;
		socket = new Socket();
		socket.setTcpNoDelay(true);
		socket.setSoTimeout(readTimeout);
		socket.connect(connectionConfiguration.getSocketAddress(),
				AVR_CONNECT_TIMEOUT);

		boolean ok = false;
		try {
			in = socket.getInputStream();
			out = new OutputStreamWriter(socket.getOutputStream());
			Thread.sleep(1000);
			readThread = new Thread(new Receiver(), "receiver");
			readThread.setDaemon(true);
			readThread.start();
			sender = new Sender();
			sendThread = new Thread(sender, "sender");
			sendThread.setDaemon(true);
			sendThread.start();
			ok = true;
		} finally {
			if (!ok) {
				try {
					socket.close();
				} catch (Exception x) {
					Logger.error("init close failed", x);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.pskiwi.avrremote.core.IConnector#send(java.lang.String)
	 */
	public void send(String command) {
		connectorListener.sendData(command);
		doSend(command);
	}

	private void doSend(String cmd) {
		// sonst falls voll "IllegalStateException"
		if (sendQueue.size() < MAX_QUEUE_SIZE) {
			sendQueue.add(cmd);
		} else {
			// hier stimmt was nicht ...
			Logger.error("Queue overflow. clear", null);
			sendQueue.clear();
		}
	}

	public void query(Zone zone, IAVRState s) {

		String prefix = zone.getCommandPrefix(s);
		if ((prefix.startsWith("VS") || prefix.startsWith("PS"))
				&& !prefix.endsWith(" ")) {
			prefix += " ";
		}
		// Ausnahmen (besser Methode)
		if (prefix.startsWith("PSFRONT")) {
			prefix = prefix.trim();
		}
		if (!prefix.equals("NSE")) {
			prefix += "?";
		}
		doSend(prefix);
	}

	public void sendCommand(Zone zone, IAVRState s, String cmd) {
		final String command = zone.getCommandPrefix(s) + cmd;
		send(command);
	}

	public void waitUntilClosed() throws InterruptedException {
		closeSignal.await();
	}

	/**
	 * Fragt den Receiver etwas und wartet auf sein erstes Datum. Ein
	 * geglückter Connect allein sagt nichts: der Receiver erlaubt nur eine
	 * Sitzung und nimmt den Socket auch dann an, wenn er noch eine alte hält -
	 * er schweigt danach nur. Ohne diese Prüfung gilt die Verbindung als
	 * hergestellt, und die Oberfläche bleibt grau, weil nie ein Status kommt.
	 *
	 * ALIVE_PROBE ist die Power-Abfrage, weil sie als einzige von jedem Modell
	 * und in jedem Zustand beantwortet wird - im Standby mit PWSTANDBY.
	 *
	 * @return false, wenn innerhalb der Wartezeit nichts kam
	 */
	public boolean awaitResponse() throws InterruptedException {
		doSend(ALIVE_PROBE);
		return firstDataSignal.await(handshakeTimeout, TimeUnit.MILLISECONDS);
	}

	public void close() {
		Logger.info("close socket ...");
		try {
			try (socket) {
				socket.shutdownInput();
				socket.shutdownOutput();
			}
			Logger.info("socket closed:" + socket.isClosed() + " connected:"
					+ socket.isConnected());
		} catch (IOException e) {
			Logger.debug("close socket failed " + e);
		}
		readThread.interrupt();
		sendThread.interrupt();
	}

	public boolean isConnected() {
		return socket.isConnected();
	}

	public void clearQueue() {
		sendQueue.clear();
	}

	public boolean isQueueEmpty() {
		return sendQueue.isEmpty();
	}

	@Override
	public String toString() {
		return "Connector " + connectionConfiguration + " connected:"
				+ socket.isConnected();
	}

	public void setConnectorListener(IConnectorListener l) {
		this.connectorListener = l;
	}

	private IConnectorListener connectorListener = IConnectorListener.NULL_LISTENER;
	private final IEventListener listener;
	private final InputStream in;
	private final Writer out;
	private final static char CR = '\r';
	private final Socket socket;
	private final Sender sender;
	private final Thread readThread;
	private final Thread sendThread;
	private final ConnectionConfiguration connectionConfiguration;
	private final int sendDelay;
	private final int handshakeTimeout;
	private final int readTimeout;
	private final int idleProbe;
	private final int probeGrace;
	private final ArrayBlockingQueue<String> sendQueue = new ArrayBlockingQueue<String>(
			MAX_QUEUE_SIZE);
	private final CountDownLatch closeSignal = new CountDownLatch(1);
	private final CountDownLatch firstDataSignal = new CountDownLatch(1);
	private static final int AVR_CONNECT_TIMEOUT = 2500;
	// Im Feld liegen zwischen Connect und erster Antwort Millisekunden;
	// reichlich Luft für ein WLAN, das gerade aus dem Powersave kommt.
	private static final int HANDSHAKE_TIMEOUT = 3000;
	// Taktrate des Receiver-Threads, wenn nichts hereinkommt - nicht die
	// Zeit, nach der etwas passiert, das sind IDLE_PROBE und PROBE_GRACE.
	private static final int READ_TIMEOUT = 5000;
	// so lange darf es still sein, bevor nachgefragt wird ...
	private static final int IDLE_PROBE = 60000;
	// ... und so lange darf die Antwort darauf ausbleiben
	private static final int PROBE_GRACE = 10000;
	private static final String ALIVE_PROBE = "PW?";
	private final static int MAX_QUEUE_SIZE = 100;
}
