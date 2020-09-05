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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.pskiwi.avrremote.http.AVRXMLInfo.Input;
import de.pskiwi.avrremote.log.Logger;

public final class Series08InputParser {

	public Series08InputParser(InputStream in) throws IOException {
		inputs = findLine(in);
		matcher = OPTION_PATTERN.matcher(inputs);
		Logger.debug(toString());
	}

	private String findLine(InputStream in) throws IOException {
		final BufferedReader r = new BufferedReader(new InputStreamReader(in));
		try {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.contains(START_MARKER)) {
					return line;
				}
			}
		} finally {
			r.close();
		}
		return null;
	}

	private Input next() {
		if (matcher.find(start)) {
			String value = matcher.group(1).trim();
			// group(2)->selected
			String display = matcher.group(3).trim();
			start = matcher.end();
			Logger.debug("Series08Rename [" + value + "]->[" + display + "]");
			return new Input(value, display, true);
		} else {
			return null;
		}
	}

	public List<Input> get() {
		final ArrayList<Input> ret = new ArrayList<Input>();
		Input id;
		while ((id = next()) != null) {
			ret.add(id);
		}
		return ret;
	}

	public static void main(String[] args) throws FileNotFoundException,
			IOException {
		Series08InputParser p = new Series08InputParser(new FileInputStream(
				"Series08Input.html"));
		for (Input id : p.get()) {
			System.out.println(id);
		}
	}

	@Override
	public String toString() {
		return "Series08Parser [" + inputs + "]";
	}

	private int start = 0;
	
	private final Matcher matcher;
	private final String inputs;

	// <option value='TUNER'>TUNER </option>
	private final Pattern OPTION_PATTERN = Pattern
			.compile("<option value='([^']+)'( selected)?>([^>]*)</option>");
	private final static String START_MARKER = "name='listInputFunction'";
}
