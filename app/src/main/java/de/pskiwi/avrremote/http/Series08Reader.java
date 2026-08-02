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
import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.List;

import de.pskiwi.avrremote.http.AVRXMLInfo.Input;
import de.pskiwi.avrremote.log.Logger;

public final class Series08Reader {

	public Series08Reader(String baseURL) {
		this.baseURL = baseURL;
	}

	public AVRXMLInfo readSeries08Info() throws Exception {

		// Der frühere DefaultHttpClient wurde je Lesevorgang neu angelegt,
		// seine Cookies galten also nur für diesen einen Durchlauf. Der
		// prozessweite CookieHandler aus AVRApplication lebt dagegen bis zum
		// Prozessende - vor jedem Durchlauf leeren, sonst würde eine veraltete
		// Sitzungs-ID weiter mitgeschickt.
		final CookieHandler cookies = CookieHandler.getDefault();
		if (cookies instanceof CookieManager) {
			((CookieManager) cookies).getCookieStore().removeAll();
		}

		final AVRXMLInfo info = new AVRXMLInfo();

		readSeries08Renames(info);
		readSeries08ZoneNames(info);
		readSeries08QuickSelect(info);

		return info;

	}

	private void doGet(final String toget) throws IOException {
		HTTPSupport.get(baseURL + toget);
	}

	private void readSeries08Renames(final AVRXMLInfo info) throws IOException {
		Logger.debug("readSeries08Renames ...");
		final byte[] content = HTTPSupport.get(baseURL
				+ "SETUP/01_SOURCESELECT/d_inputsetup.asp");
		final Series08InputParser p = new Series08InputParser(
				new ByteArrayInputStream(content));
		final List<Input> inputs = p.get();
		if (!inputs.isEmpty()) {
			Logger.debug("Series08 inputs#" + inputs.size());
			for (Input i : inputs) {
				info.add("InputFuncList", i.getName());
				info.add("RenameSource", i.getRename());
			}
		} else {
			Logger.debug("Series08 no info");
		}
		Logger.debug("readSeries08Renames done");
	}

	private void readSeries08ZoneNames(final AVRXMLInfo info)
			throws IOException {
		Logger.debug("readSeries08ZoneNames ...");
		final byte[] content = HTTPSupport.get(baseURL
				+ "ZONERENAME/d_zonerename.asp");

		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				new ByteArrayInputStream(content));
		p.parse();
		for (String s : p.getZoneNames()) {
			info.add(AVRXMLInfo.RENAME_ZONE, s);
		}
		Logger.debug("readSeries08ZoneNames done");
	}

	private void readSeries08QuickSelect(final AVRXMLInfo info)
			throws IOException {
		Logger.debug("readSeries08QuickSelect init...");
		// sonst sind nachher die Daten nicht enthalten
		doGet("SETUP/04_MANUALSETUP/09_OPTION1/r_option1.asp");
		Logger.debug("readSeries08QuickSelect ...");
		final byte[] content = HTTPSupport.get(baseURL
				+ "SETUP/04_MANUALSETUP/09_OPTION1/d_option1.asp");
		final Series08QuickSelectParser p = new Series08QuickSelectParser(
				new ByteArrayInputStream(content));
		p.parse();
		for (String s : p.get()) {
			final String key = AVRXMLInfo.QUICK_SELECT_NAME + "0";
			Logger.info("[" + key + "]->[" + s + "]");
			info.add(key, s);
		}
		Logger.debug("readSeries08QuickSelect done");
	}

	private final String baseURL;
}
