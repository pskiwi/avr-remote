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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import de.pskiwi.avrremote.log.Logger;

/**
 * GET und POST gegen den Receiver. Ersetzt den früheren Apache-HttpClient, der
 * nur noch als optionale Plattform-Bibliothek existierte. Mehr als diese beiden
 * Operationen hat die App nie benutzt.
 */
public final class HTTPSupport {

	public static byte[] get(String url) throws IOException {
		return execute(url, null);
	}

	/** POST mit application/x-www-form-urlencoded-Body. */
	public static byte[] postForm(String url, Map<String, String> formParams)
			throws IOException {
		return execute(url, encodeForm(formParams).getBytes("US-ASCII"));
	}

	private static byte[] execute(String url, byte[] body) throws IOException {
		final String method = body != null ? "POST" : "GET";
		// vor dem Request loggen: bei Timeout oder Exception taucht die URL
		// sonst nirgends im Log auf, das FeedbackReporter verschickt
		Logger.debug(method + " [" + url + "] ...");
		final HttpURLConnection connection = (HttpURLConnection) new URL(url)
				.openConnection();
		try {
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);
			// HttpURLConnection fragt von sich aus gzip an, der Apache-Client
			// tat das nie. Für die betagten Receiver-Webserver abschalten.
			connection.setRequestProperty("Accept-Encoding", "identity");
			if (body != null) {
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", FORM_CONTENT_TYPE);
				// ohne feste Länge wird chunked gesendet, das versteht der
				// Receiver nicht. Nebeneffekt: mit gestreamtem Body kann
				// getResponseCode() den Request nicht wiederholen und wirft bei
				// 3xx/401 eine HttpRetryException. Apache lieferte den Status
				// einfach zurück. Bisher an keinem Receiver beobachtet.
				connection.setFixedLengthStreamingMode(body.length);
				final OutputStream out = connection.getOutputStream();
				try {
					out.write(body);
				} finally {
					out.close();
				}
			}
			final int code = connection.getResponseCode();
			final byte[] content = readAll(code >= HttpURLConnection.HTTP_BAD_REQUEST ? connection
					.getErrorStream() : connection.getInputStream());
			Logger.debug(method + " [" + url + "] code:" + code + " bytes:"
					+ content.length);
			return content;
		} finally {
			connection.disconnect();
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		if (in == null) {
			return new byte[0];
		}
		try {
			final ByteArrayOutputStream ret = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				ret.write(buffer, 0, read);
			}
			return ret.toByteArray();
		} finally {
			in.close();
		}
	}

	private static String encodeForm(Map<String, String> formParams)
			throws UnsupportedEncodingException {
		final StringBuilder ret = new StringBuilder();
		for (Map.Entry<String, String> e : formParams.entrySet()) {
			if (ret.length() > 0) {
				ret.append('&');
			}
			ret.append(URLEncoder.encode(e.getKey(), "UTF-8"));
			ret.append('=');
			ret.append(URLEncoder.encode(e.getValue(), "UTF-8"));
		}
		return ret.toString();
	}

	private HTTPSupport() {
	}

	private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
	private static final int CONNECT_TIMEOUT = 5000;
	private static final int READ_TIMEOUT = 4000;
}
