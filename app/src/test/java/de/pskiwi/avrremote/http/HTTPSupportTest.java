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
package de.pskiwi.avrremote.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Prüft das Drahtformat, das die Receiver-Webserver (GoAhead, HTTP/1.0) sehen:
 * Request-Zeile, Content-Type und Kodierung des Formular-Bodys.
 *
 * Der Testserver ist ein roher ServerSocket, damit die Zusicherungen auf den
 * tatsächlich gesendeten Bytes sitzen und nicht auf einer geparsten Sicht
 * darauf. Es wird kein Receiver benötigt.
 *
 * Grenze der Aussagekraft: das hier ist die Desktop-JVM, nicht Androids
 * OkHttp-basierte HttpURLConnection. Zwei der abgesicherten Entscheidungen
 * lassen sich deshalb hier nicht nachweisen, nur dokumentieren - siehe die
 * Kommentare an den betroffenen Zusicherungen.
 */
public final class HTTPSupportTest {

	@Before
	public void startServer() throws IOException {
		server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
		baseURL = "http://127.0.0.1:" + server.getLocalPort();
		serverThread = new Thread("test-receiver") {
			@Override
			public void run() {
				serveOneRequest();
			}
		};
		serverThread.start();
	}

	@After
	public void stopServer() throws Exception {
		// erst schließen, dann warten: scheitert ein Test vor dem Request,
		// hängt der Server-Thread sonst bis zum Timeout in accept()
		stopping = true;
		if (server == null) {
			// startServer ist gescheitert - dessen Fehler nicht mit einer NPE
			// von hier überdecken
			return;
		}
		server.close();
		serverThread.join(5000);
		if (failure != null) {
			throw failure;
		}
	}

	@Test
	public void getReturnsBodyAndKeepsPathAndQuery() throws Exception {
		responseBody = "<?xml version=\"1.0\"?><item/>";

		final byte[] content = HTTPSupport.get(baseURL
				+ "/goform/formMainZone_MainZoneXml.xml?ZoneName=ZONE1");

		assertEquals(responseBody, new String(content, "UTF-8"));
		assertEquals("GET /goform/formMainZone_MainZoneXml.xml?ZoneName=ZONE1"
				+ " HTTP/1.1", requestLine());
	}

	/** NetDisplay baut genau eine URL mit doppeltem Slash - die muss so rausgehen. */
	@Test
	public void getKeepsDoubleSlashInPath() throws Exception {
		HTTPSupport.get(baseURL + "//goform/formNetAudio_StatusXml.xml");

		assertEquals("GET //goform/formNetAudio_StatusXml.xml HTTP/1.1",
				requestLine());
	}

	/**
	 * Androids HttpURLConnection hängt von sich aus "Accept-Encoding: gzip" an,
	 * der Apache-Client tat das nie. Nachweisbar ist hier nur, dass der Header
	 * gesetzt wird - der Default, gegen den er sich richtet, existiert auf der
	 * Desktop-JVM gar nicht.
	 */
	@Test
	public void getDoesNotAskForGzip() throws Exception {
		HTTPSupport.get(baseURL + "/NETAUDIO/SendPat7.asp");

		assertTrue(request, hasHeader("Accept-Encoding: identity"));
	}

	@Test
	public void postSendsFormBodyWithContentLength() throws Exception {
		final Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("cmd0", "PutSystem_OnStandby/STANDBY");

		HTTPSupport.postForm(baseURL + "/MainZone/index.put.asp", params);

		assertEquals("POST /MainZone/index.put.asp HTTP/1.1", requestLine());
		assertTrue(request,
				hasHeader("Content-Type: application/x-www-form-urlencoded"));
		final String expected = "cmd0=PutSystem_OnStandby%2FSTANDBY";
		assertEquals(expected, requestBody());
		// Der Receiver kann kein chunked. Achtung: diese beiden Zusicherungen
		// halten setFixedLengthStreamingMode NICHT fest - die Desktop-JVM
		// puffert kleine Bodies und setzt Content-Length auch ohne den Aufruf.
		// Sie dokumentieren die Anforderung; nachgewiesen wurde sie auf dem
		// Gerät. Ein echter Schutz bräuchte einen Instrumentation-Test.
		assertTrue(request,
				hasHeader("Content-Length: " + expected.length()));
		assertFalse(request, request.toLowerCase().contains(
				"transfer-encoding"));
	}

	/**
	 * Kodierung und Reihenfolge wie beim früheren UrlEncodedFormEntity:
	 * Leerzeichen als '+', '/' als %2F, Parameter in Einfügereihenfolge.
	 */
	@Test
	public void postEncodesLikeUrlEncodedFormEntity() throws Exception {
		final Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("cmd0", "PutNetFuncSearchNapster/jay z");
		params.put("Key", "ART");
		params.put("ZoneName", "MAIN ZONE");

		HTTPSupport.postForm(baseURL + "/NetAudio/index.put.asp", params);

		assertEquals("cmd0=PutNetFuncSearchNapster%2Fjay+z&Key=ART"
				+ "&ZoneName=MAIN+ZONE", requestBody());
	}

	/** Der Apache-Client warf bei 4xx nicht - dieses Verhalten bleibt. */
	@Test
	public void errorResponseReturnsBodyInsteadOfThrowing() throws Exception {
		responseStatus = "404 Not Found";
		responseBody = "not found";

		final byte[] content = HTTPSupport.get(baseURL + "/nope.asp");

		assertEquals("not found", new String(content, "UTF-8"));
	}

	/**
	 * So antwortet der echte Receiver: HTTP/1.0, kein Content-Length, das Ende
	 * des Bodys ist der Verbindungsschluss. Nachgemessen an einem AVR-3310
	 * (GoAhead-Webs). Das ist die Antwortform, an der ein Austausch des
	 * HTTP-Stacks am ehesten scheitert.
	 */
	@Test
	public void getReadsBodyDelimitedByConnectionClose() throws Exception {
		sendContentLength = false;
		responseBody = "<?xml version=\"1.0\"?><item><Power>ON</Power></item>";

		final byte[] content = HTTPSupport.get(baseURL
				+ "/goform/formMainZone_MainZoneXml.xml?ZoneName=ZONE1");

		// ohne diese Zusicherung wäre der Test auch dann grün, wenn der
		// Testserver doch ein Content-Length geschickt hätte
		assertFalse(responseHead, responseHead.toLowerCase().contains(
				"content-length"));
		assertEquals(responseBody, new String(content, "UTF-8"));
	}

	private String requestLine() {
		return request.substring(0, request.indexOf("\r\n"));
	}

	private String requestBody() {
		return request.substring(request.indexOf("\r\n\r\n") + 4);
	}

	private boolean hasHeader(String header) {
		return request.contains("\r\n" + header + "\r\n");
	}

	/**
	 * Antwortet wie der Receiver mit HTTP/1.0 und schließt danach die
	 * Verbindung. Bedient genau einen Request - ein Test, der zwei absetzt,
	 * läuft in den Lese-Timeout statt klar zu scheitern.
	 */
	private void serveOneRequest() {
		final Socket socket;
		try {
			socket = server.accept();
		} catch (IOException x) {
			// einzige Stelle, an der beim Abbau eine Exception erwartet ist -
			// der Guard steht bewusst nur hier, sonst verschlucken die Tests
			// echte Server-Fehler
			if (!stopping) {
				failure = x;
			}
			return;
		}
		try {
			// wird der Test abgebrochen, während der Client verbunden ist,
			// schließt server.close() diesen Socket nicht - ohne Timeout
			// bliebe der Thread für immer in read() hängen
			socket.setSoTimeout(SO_TIMEOUT);
			request = readRequest(socket.getInputStream());
			final byte[] body = responseBody.getBytes("UTF-8");
			final StringBuilder head = new StringBuilder("HTTP/1.0 ");
			head.append(responseStatus).append("\r\n");
			head.append("Content-Type: text/xml; charset=utf-8\r\n");
			if (sendContentLength) {
				head.append("Content-Length: ").append(body.length)
						.append("\r\n");
			}
			head.append("\r\n");
			responseHead = head.toString();
			final OutputStream out = socket.getOutputStream();
			out.write(responseHead.getBytes("US-ASCII"));
			out.write(body);
			out.flush();
		} catch (IOException x) {
			// ab hier ist jede Exception echt
			failure = x;
		} finally {
			try {
				socket.close();
			} catch (IOException ignore) {
				// beim Schließen nicht mehr interessant
			}
		}
	}

	/** Liest Header und - falls angekündigt - den Body als rohen Text. */
	private static String readRequest(InputStream in) throws IOException {
		final ByteArrayOutputStream raw = new ByteArrayOutputStream();
		int read;
		while (!contains(raw, "\r\n\r\n") && (read = in.read()) != -1) {
			raw.write(read);
		}
		final String head = raw.toString("US-ASCII");
		final int contentLength = parseContentLength(head);
		for (int i = 0; i < contentLength && (read = in.read()) != -1; i++) {
			raw.write(read);
		}
		return raw.toString("UTF-8");
	}

	private static boolean contains(ByteArrayOutputStream buffer, String s) {
		try {
			return buffer.toString("US-ASCII").contains(s);
		} catch (IOException x) {
			throw new RuntimeException(x);
		}
	}

	private static int parseContentLength(String head) {
		for (String line : head.split("\r\n")) {
			if (line.toLowerCase().startsWith("content-length:")) {
				return Integer.parseInt(line.substring(
						line.indexOf(':') + 1).trim());
			}
		}
		return 0;
	}

	private ServerSocket server;
	private Thread serverThread;
	private String baseURL;

	private volatile String request;
	private volatile String responseHead;
	private volatile IOException failure;
	private volatile boolean stopping;

	// vom Server-Thread gelesen, von der Testmethode gesetzt - der Thread läuft
	// schon, wenn zugewiesen wird, also volatile
	private volatile String responseStatus = "200 OK";
	private volatile String responseBody = "ok";
	private volatile boolean sendContentLength = true;

	/** knapp unter dem join(5000) im Abbau, damit der Thread sicher endet */
	private static final int SO_TIMEOUT = 4000;
}
