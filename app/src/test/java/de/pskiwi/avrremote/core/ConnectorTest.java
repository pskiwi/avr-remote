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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Prüft den {@link Connector} gegen einen Fake-Receiver auf einem lokalen
 * ServerSocket - kein Gerät nötig. Der Connector kennt nur java.net, InData
 * und den Logger (der per Default an ILogger.NULL_LOGGER delegiert), deshalb
 * lässt er sich als eine der wenigen Klassen hier überhaupt auf der JVM bauen.
 *
 * Der Fake-Receiver spricht das Nötigste vom Telnet-Protokoll: Kommandos
 * enden mit CR, Antworten ebenso.
 */
public final class ConnectorTest {

	@Before
	public void startServer() throws IOException {
		server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
		serverThread = new Thread("test-receiver") {
			@Override
			public void run() {
				serveOneConnection();
			}
		};
		serverThread.start();
	}

	@After
	public void stopServer() throws Exception {
		// erst schließen, dann warten: scheitert ein Test vor dem Connect,
		// hängt der Server-Thread sonst bis zum Timeout in accept()
		stopping = true;
		if (connector != null) {
			connector.close();
		}
		if (server == null) {
			return;
		}
		server.close();
		serverThread.join(5000);
		if (failure != null) {
			throw failure;
		}
	}

	/** Der Normalfall: der Receiver antwortet auf die Probe. */
	@Test
	public void aReceiverThatAnswersIsAccepted() throws Exception {
		reply = "PWSTANDBY";
		connector = connect();

		assertTrue(connector.awaitResponse());

		assertTrue(connector.isConnected());
		// die Probe muss die Power-Abfrage sein: sie ist die einzige, die auch
		// ein Receiver im Standby beantwortet
		assertEquals("PW?", received.poll(5, TimeUnit.SECONDS));
	}

	/**
	 * Der Fall, um den es geht: der Receiver haelt noch eine alte Sitzung, nimmt
	 * den Socket trotzdem an und schweigt. Frueher galt das als Verbindung.
	 */
	@Test
	public void aSilentReceiverIsNotAccepted() throws Exception {
		reply = null;
		connector = connect();

		final long start = System.currentTimeMillis();
		final boolean answered = connector.awaitResponse();
		final long waited = System.currentTimeMillis() - start;

		assertFalse(answered);
		// gewartet werden muss, aber nicht laenger als noetig
		assertTrue("wartete nur " + waited + "ms", waited >= HANDSHAKE_TIMEOUT);
		assertTrue("wartete " + waited + "ms", waited < HANDSHAKE_TIMEOUT + 2000);
		// und der Socket muss sich abraeumen lassen, sonst haelt der Aufrufer
		// beim naechsten Versuch selbst die Sitzung besetzt, die er sucht
		connector.close();
		serverThread.join(5000);
		assertFalse("Server sah kein Verbindungsende", serverThread.isAlive());
	}

	/** Nicht nur die Antwort auf die Probe zaehlt, sondern jedes Datum. */
	@Test
	public void anyDataCountsAsAnAnswer() throws Exception {
		reply = "MVMAX 80";
		connector = connect();

		assertTrue(connector.awaitResponse());
	}

	/**
	 * Der Fall, den es ohne Lese-Timeout gar nicht gäbe: die Verbindung steht
	 * noch als Socket, aber es bedient sie niemand mehr. Vorher blieb der
	 * Receiver-Thread dafür für immer in read() stehen.
	 */
	@Test
	public void aConnectionThatGoesSilentIsGivenUp() throws Exception {
		reply = "PWSTANDBY";
		connector = connect();
		assertTrue(connector.awaitResponse());

		// ab hier antwortet der Receiver auf nichts mehr
		reply = null;

		final Thread waiter = new Thread("waitUntilClosed") {
			@Override
			public void run() {
				try {
					connector.waitUntilClosed();
				} catch (InterruptedException x) {
					Thread.currentThread().interrupt();
				}
			}
		};
		final long start = System.currentTimeMillis();
		waiter.start();
		// begrenzt, damit ein Fehler den Build nicht hängen lässt
		waiter.join(10000);
		final long waited = System.currentTimeMillis() - start;

		assertFalse("Verbindung blieb stehen", waiter.isAlive());
		// nicht vor der Probe aufgeben, sonst wäre eine bloß ruhige
		// Verbindung nicht von einer toten zu unterscheiden
		assertTrue("gab schon nach " + waited + "ms auf", waited >= IDLE_PROBE);
		assertEquals("PW?", received.poll(5, TimeUnit.SECONDS));
		assertEquals("PW?", received.poll(5, TimeUnit.SECONDS));
	}

	private Connector connect() throws Exception {
		return new Connector(new ConnectionConfiguration("127.0.0.1:"
				+ server.getLocalPort()), SEND_DELAY, new IEventListener() {
			public void received(InData s) {
				events.add(s);
			}
		}, HANDSHAKE_TIMEOUT, READ_TIMEOUT, IDLE_PROBE, PROBE_GRACE);
	}

	/**
	 * Nimmt genau eine Verbindung an, liest CR-terminierte Kommandos und
	 * antwortet - falls {@link #reply} gesetzt ist - auf jedes davon.
	 */
	private void serveOneConnection() {
		final Socket socket;
		try {
			socket = server.accept();
		} catch (IOException x) {
			// einzige Stelle, an der beim Abbau eine Exception erwartet ist
			if (!stopping) {
				failure = x;
			}
			return;
		}
		try {
			final InputStream in = socket.getInputStream();
			final OutputStream out = socket.getOutputStream();
			final StringBuilder line = new StringBuilder();
			int ch;
			while ((ch = in.read()) != -1) {
				if (ch == '\r') {
					received.add(line.toString());
					line.setLength(0);
					final String answer = reply;
					if (answer != null) {
						out.write((answer + "\r").getBytes("US-ASCII"));
						out.flush();
					}
				} else {
					line.append((char) ch);
				}
			}
		} catch (IOException x) {
			// beim Abbau schließt der Test den Socket unter dem read() weg
			if (!stopping) {
				failure = x;
			}
		} finally {
			try {
				socket.close();
			} catch (IOException ignore) {
				// beim Schließen nicht mehr interessant
			}
		}
	}

	private ServerSocket server;
	private Thread serverThread;
	private volatile boolean stopping;
	private volatile IOException failure;
	private volatile String reply;
	private Connector connector;
	private final BlockingQueue<String> received = new LinkedBlockingQueue<String>();
	private final BlockingQueue<InData> events = new LinkedBlockingQueue<InData>();
	private static final int SEND_DELAY = 10;
	// kurz gehalten: der echte Wert von 3000ms wuerde die Suite unnoetig
	// verlaengern, geprueft wird das Verhalten und nicht die Zahl
	private static final int HANDSHAKE_TIMEOUT = 300;
	private static final int READ_TIMEOUT = 100;
	private static final int IDLE_PROBE = 300;
	private static final int PROBE_GRACE = 200;
}
