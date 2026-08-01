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
 * Prüft die Eigenschaften, in denen sich HttpURLConnection vom früher benutzten
 * Apache-HttpClient unterscheidet und in denen die Receiver-Webserver (GoAhead,
 * HTTP/1.0) empfindlich sind: Content-Length statt chunked, kein gzip,
 * Content-Type und Kodierung des Formular-Bodys.
 *
 * Der Testserver ist ein roher ServerSocket, damit die Zusicherungen auf den
 * tatsächlich gesendeten Bytes sitzen und nicht auf einer geparsten Sicht
 * darauf. Es wird kein Receiver benötigt.
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
		serverThread.join(5000);
		server.close();
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
	 * HttpURLConnection fragt von sich aus gzip an, der Apache-Client tat das
	 * nie.
	 */
	@Test
	public void getDoesNotAskForGzip() throws Exception {
		HTTPSupport.get(baseURL + "/NETAUDIO/SendPat7.asp");

		assertTrue(request, hasHeader("Accept-Encoding: identity"));
		assertFalse(request, request.toLowerCase().contains("gzip"));
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
		// Der Receiver kann kein chunked - die Länge muss vorab feststehen.
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
	 * Verbindung.
	 */
	private void serveOneRequest() {
		try {
			final Socket socket = server.accept();
			try {
				request = readRequest(socket.getInputStream());
				final byte[] body = responseBody.getBytes("UTF-8");
				final OutputStream out = socket.getOutputStream();
				out.write(("HTTP/1.0 " + responseStatus + "\r\n"
						+ "Content-Type: text/xml; charset=utf-8\r\n"
						+ "Content-Length: " + body.length + "\r\n\r\n")
						.getBytes("US-ASCII"));
				out.write(body);
				out.flush();
			} finally {
				socket.close();
			}
		} catch (IOException x) {
			failure = x;
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
	private volatile IOException failure;

	private String responseStatus = "200 OK";
	private String responseBody = "ok";
}
