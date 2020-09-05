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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

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

		public InData read() throws IOException {
			final char[] line = new char[MAX_LINE];
			int ch = in.read();
			int count;
			do {
				count = 0;
				while (ch != -1 && !detectCR(line, count, ch)
						&& count < MAX_LINE) {
					line[count++] = (char) ch;
					ch = in.read();
				}
				if (count == MAX_LINE) {
					// read garbage
					// avr braucht restart ???
					while (ch != -1 && ch != CR) {
						ch = in.read();
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
						socket.close();
						closeSignal.countDown();
						Logger.info("receiver socket closed -> return");
						return;
					}
					Logger.debug("RECEIVED [" + val.toDebugString() + "] "
							+ (listener != null ? "" : "unregistered"));
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

		private static final int MAX_LINE = 256;
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
		this.connectionConfiguration = connectionConfiguration;
		this.sendDelay = sendDelay;
		listener = eventListener;
		socket = new Socket();
		socket.setTcpNoDelay(true);
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

	public void close() {
		Logger.info("close socket ...");
		try {
			try {
				socket.shutdownInput();
				socket.shutdownOutput();
			} finally {
				socket.close();
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
	private final ArrayBlockingQueue<String> sendQueue = new ArrayBlockingQueue<String>(
			MAX_QUEUE_SIZE);
	private final CountDownLatch closeSignal = new CountDownLatch(1);
	private static final int AVR_CONNECT_TIMEOUT = 2500;
	private final static int MAX_QUEUE_SIZE = 100;
}
