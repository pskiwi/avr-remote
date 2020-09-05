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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class AVRHTTPClient {

	public AVRHTTPClient(ModelConfigurator cfg) {
		this.baseURL = cfg.getConnectionConfig().getBaseURL();

		configureHTTPClient(httpclient);
	}

	// Workaround für OOM
	// http://stackoverflow.com/questions/5358014/android-httpclient-oom-on-4g-lte-htc-thunderbolt
	public static void configureHTTPClient(DefaultHttpClient httpclient) {
		// Set the timeout in milliseconds until a connection is established.
		int timeoutConnection = 5000;

		// Set the default socket timeout (SO_TIMEOUT)
		// in milliseconds which is the timeout for waiting for data.
		int timeoutSocket = 4000;

		// set timeout parameters for HttpClient
		HttpParams httpParameters = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParameters,
				timeoutConnection);
		HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		HttpConnectionParams.setSocketBufferSize(httpParameters, 8192);// setting
																		// setSocketBufferSize

		httpclient.setParams(httpParameters);
	}

	public void doBackgroundReset(final Runnable runnable) {
		final Thread t = new Thread("reset avr") {
			@Override
			public void run() {
				Logger.info("reset AVR");
				try {
					Logger.info("reset AVR: send standby");
					setStandby();
					Thread.sleep(2000);
					Logger.info("reset AVR: send on");
					setOn();
				} catch (Exception x) {
					Logger.error("reset avr failed", x);
				} finally {
					runnable.run();
				}
			}
		};
		t.start();
	}

	public void setStandby() throws Exception {
		postValue("PutSystem_OnStandby/STANDBY");
	}

	public void setOn() throws Exception {
		postValue("PutSystem_OnStandby/ON");
	}

	private void postValue(String value) throws Exception {
		List<NameValuePair> formparams = new ArrayList<NameValuePair>();
		formparams.add(new BasicNameValuePair("cmd0", value));
		// formparams.add(new BasicNameValuePair("param2", "value2"));
		UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams,
				"UTF-8");
		HttpPost httppost = new HttpPost(baseURL + "MainZone/index.put.asp");
		httppost.setEntity(form);

		HttpResponse response = httpclient.execute(httppost);
		System.out.println("Code:" + response.getStatusLine().getStatusCode());
	}

	public enum SearchInputType {
		Napster("Napster"), IRadio("iRadio"), Rhapsody("Rapsody"), Pandora(
				"Pandora"), None("");

		private SearchInputType(String keyword) {
			this.keyword = keyword;
		}

		public String getKeyword() {
			return keyword;
		}

		public boolean supportsType() {
			return this != IRadio && this != None;
		}

		public static SearchInputType fromString(String text) {
			if (text == null || text.trim().length() == 0) {
				Logger.info("SearchInputType [" + text + "] empty");
				return SearchInputType.None;
			}
			for (SearchInputType t : values()) {
				if (t.keyword.equalsIgnoreCase(text)) {
					return t;
				}
			}
			Logger.info("SearchInputType [" + text + "] unknown");
			return SearchInputType.None;
		}

		private final String keyword;
	}

	public enum SearchType {
		None(""), Artist("ART"), Album("ALB"), Track("TRA"), Keyword("KEY");

		private SearchType(String token) {
			this.token = token;
		}

		public String getToken() {
			return token;
		}

		private final String token;

		public static SearchType fromString(String text) {
			if (text == null || text.trim().length() == 0) {
				return SearchType.Artist;
			}
			try {
				return valueOf(text);
			} catch (Throwable t) {
				Logger.error("convert [" + text + "] failed", t);
				return SearchType.Artist;
			}
		}

	};

	// POST /NetAudio/index.put.asp HTTP/1.1
	// Key=ART&cmd0=PutNetFuncSearchNapster%2Fjayz&cmd1=aspMainZone_WebUpdateStatus%2F&ZoneName=ZONE2HTTP/1.0
	// 200 OK
	//
	// POST /NetAudio/index.put.asp HTTP/1.1
	// cmd0=PutNetFuncSearchiRadio%2Faachen&cmd1=aspMainZone_WebUpdateStatus%2F&ZoneName=MAIN+ZONEHTTP/1.0
	// 200 OK

	public void doSearch(SearchInputType inputType, SearchType type,
			String toSearch) throws Exception {
		Logger.info("doSearch input:" + inputType + " type:" + type + " text["
				+ toSearch + "]");
		List<NameValuePair> formparams = new ArrayList<NameValuePair>();
		formparams.add(new BasicNameValuePair("cmd0", "PutNetFuncSearch"
				+ inputType.getKeyword() + "/" + toSearch));
		if (type != SearchType.None) {
			formparams.add(new BasicNameValuePair("Key", type.getToken()));
		}
		UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams,
				"UTF-8");
		HttpPost httppost = new HttpPost(baseURL + "NetAudio/index.put.asp");
		httppost.setEntity(form);

		HttpResponse response = httpclient.execute(httppost);
		final HttpEntity entity = response.getEntity();
		if (entity != null) {
			entity.consumeContent();
		}
		System.out.println("Code:" + response.getStatusLine().getStatusCode());
	}

	// Zonen-Namen stehen in den verschiedenen Zonen-Infos (Merge)
	public AVRXMLInfo readState(ModelConfigurator configurator)
			throws Exception {
		Logger.info("read XML state");
		if (configurator.getModel().useSeries08Parser()) {
			return new Series08Reader(httpclient, baseURL).readSeries08Info();
		} else {
			final AVRXMLInfo ret = new AVRXMLInfo();
			for (Zone z : Zone.values()) {
				if (z.getZoneNumber() < configurator.getZoneCount()) {
					final AVRXMLInfo s = readState(z);
					if (s != null) {
						ret.merge(z, s);
					} else {
						break;
					}
				}
			}
			Logger.info("[ALL]->" + ret.getInfo());
			return ret;
		}

	}

	// Status für Zone lesen
	private AVRXMLInfo readState(Zone z) throws Exception {
		final HttpGet httpGet = new HttpGet(baseURL
				+ "goform/formMainZone_MainZoneXml.xml?ZoneName=ZONE"
				+ (z.getZoneNumber() + 1));
		final HttpResponse response = httpclient.execute(httpGet);
		final HttpEntity entity = response.getEntity();
		AVRXMLInfo info = new AVRXMLInfo();
		if (entity != null) {
			info = new AVRXMLInfoParser().parse(entity.getContent());
		}

		readQuickInfo(z, info);

		if (!info.isDefined()) {
			return null;
		} else {
			return info;
		}
	}

	private void readQuickInfo(Zone z, AVRXMLInfo info) throws IOException,
			ClientProtocolException {
		final HttpGet quickHttpGet = new HttpGet(baseURL
				+ "goform/formMainZone_QuickSelectXml.xml?ZoneName=ZONE"
				+ (z.getZoneNumber() + 1));
		final HttpResponse quickResponse = httpclient.execute(quickHttpGet);
		final HttpEntity quickEntity = quickResponse.getEntity();
		if (quickEntity != null) {
			final AVRXMLInfo quickInfo = new AVRXMLInfoParser()
					.parse(quickEntity.getContent());
			if (quickInfo.isDefined()) {
				info.mergeQuickSelect(z, quickInfo);
			}
		}
	}

	private final String baseURL;
	private final DefaultHttpClient httpclient = new DefaultHttpClient();
}
