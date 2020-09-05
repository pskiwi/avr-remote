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
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import de.pskiwi.avrremote.http.AVRXMLInfo.Input;
import de.pskiwi.avrremote.log.Logger;

public final class Series08Reader {

	public Series08Reader(DefaultHttpClient httpclient, String baseURL) {
		this.httpclient = httpclient;
		this.baseURL = baseURL;
	}

	public AVRXMLInfo readSeries08Info() throws Exception {

		final AVRXMLInfo info = new AVRXMLInfo();

		readSeries08Renames(info);
		readSeries08ZoneNames(info);
		readSeries08QuickSelect(info);

		return info;

	}

	private void doGet(final String toget) throws IOException,
			ClientProtocolException {
		final String url = baseURL + toget;
		Logger.debug("doGet: [" + url + "] ...");
		final HttpGet getRequest = new HttpGet(url);
		final HttpResponse response = httpclient.execute(getRequest);
		final HttpEntity entity = response.getEntity();
		if (entity != null) {
			entity.consumeContent();
		}
		Logger.debug("doGet [" + url + "] code:"
				+ response.getStatusLine().getStatusCode());
	}

	private void readSeries08Renames(final AVRXMLInfo info) throws IOException,
			ClientProtocolException {
		Logger.debug("readSeries08Renames ...");
		final HttpGet httpGet = new HttpGet(baseURL
				+ "SETUP/01_SOURCESELECT/d_inputsetup.asp");
		final HttpResponse response = httpclient.execute(httpGet);
		final HttpEntity entity = response.getEntity();
		final Series08InputParser p = new Series08InputParser(
				entity.getContent());
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
		Logger.debug("readSeries08Renames "
				+ response.getStatusLine().getStatusCode());
	}

	private void readSeries08ZoneNames(final AVRXMLInfo info)
			throws IOException, ClientProtocolException {
		Logger.debug("readSeries08ZoneNames ...");
		final HttpGet httpGet = new HttpGet(baseURL
				+ "ZONERENAME/d_zonerename.asp");
		final HttpResponse response = httpclient.execute(httpGet);
		final HttpEntity entity = response.getEntity();

		final Series08ZoneRenameParser p = new Series08ZoneRenameParser(
				entity.getContent());
		p.parse();
		for (String s : p.getZoneNames()) {
			info.add(AVRXMLInfo.RENAME_ZONE, s);
		}
		Logger.debug("readSeries08ZoneNames "
				+ response.getStatusLine().getStatusCode());
	}

	private void readSeries08QuickSelect(final AVRXMLInfo info)
			throws IOException, ClientProtocolException {
		Logger.debug("readSeries08QuickSelect init...");
		// sonst sind nachher die Daten nicht enthalten
		doGet("SETUP/04_MANUALSETUP/09_OPTION1/r_option1.asp");
		Logger.debug("readSeries08QuickSelect ...");
		final HttpGet httpGet = new HttpGet(baseURL
				+ "SETUP/04_MANUALSETUP/09_OPTION1/d_option1.asp");
		final HttpResponse response = httpclient.execute(httpGet);
		final HttpEntity entity = response.getEntity();
		final Series08QuickSelectParser p = new Series08QuickSelectParser(
				entity.getContent());
		p.parse();
		for (String s : p.get()) {
			final String key = AVRXMLInfo.QUICK_SELECT_NAME + "0";
			Logger.info("[" + key + "]->[" + s + "]");
			info.add(key, s);
		}
		Logger.debug("readSeries08QuickSelect "
				+ response.getStatusLine().getStatusCode());
	}

	private final DefaultHttpClient httpclient;
	private final String baseURL;
}
