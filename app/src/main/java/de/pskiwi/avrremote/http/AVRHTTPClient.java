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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class AVRHTTPClient {

	public AVRHTTPClient(ModelConfigurator cfg) {
		this.baseURL = cfg.getConnectionConfig().getBaseURL();
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
		Map<String, String> formparams = new LinkedHashMap<String, String>();
		formparams.put("cmd0", value);
		// formparams.put("param2", "value2");
		HTTPSupport.postForm(baseURL + "MainZone/index.put.asp", formparams);
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
		Map<String, String> formparams = new LinkedHashMap<String, String>();
		formparams.put("cmd0", "PutNetFuncSearch" + inputType.getKeyword() + "/"
				+ toSearch);
		if (type != SearchType.None) {
			formparams.put("Key", type.getToken());
		}
		HTTPSupport.postForm(baseURL + "NetAudio/index.put.asp", formparams);
	}

	// Zonen-Namen stehen in den verschiedenen Zonen-Infos (Merge)
	public AVRXMLInfo readState(ModelConfigurator configurator)
			throws Exception {
		Logger.info("read XML state");
		if (configurator.getModel().useSeries08Parser()) {
			return new Series08Reader(baseURL).readSeries08Info();
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
		final byte[] content = HTTPSupport.get(baseURL
				+ "goform/formMainZone_MainZoneXml.xml?ZoneName=ZONE"
				+ (z.getZoneNumber() + 1));
		// Die Längenprüfung tritt an die Stelle des früheren "entity != null":
		// AVRXMLInfoParser.parse wirft bei leerem Body. Sie ist nicht exakt
		// gleichbedeutend - Apache lieferte auch für 200 ohne Inhalt eine
		// Entity, so dass eine leere Antwort die gesamte XML-Abfrage aller
		// Zonen per Exception verwarf. Jetzt gilt die Zone als undefiniert und
		// die bereits gelesenen Zonen bleiben erhalten.
		AVRXMLInfo info = new AVRXMLInfo();
		if (content.length > 0) {
			info = new AVRXMLInfoParser().parse(new ByteArrayInputStream(
					content));
		}

		readQuickInfo(z, info);

		if (!info.isDefined()) {
			return null;
		} else {
			return info;
		}
	}

	private void readQuickInfo(Zone z, AVRXMLInfo info) throws IOException {
		final byte[] content = HTTPSupport.get(baseURL
				+ "goform/formMainZone_QuickSelectXml.xml?ZoneName=ZONE"
				+ (z.getZoneNumber() + 1));
		if (content.length > 0) {
			final AVRXMLInfo quickInfo = new AVRXMLInfoParser()
					.parse(new ByteArrayInputStream(content));
			if (quickInfo.isDefined()) {
				info.mergeQuickSelect(z, quickInfo);
			}
		}
	}

	private final String baseURL;
}
