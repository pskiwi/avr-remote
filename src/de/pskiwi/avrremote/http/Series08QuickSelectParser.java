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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.pskiwi.avrremote.http.AVRXMLInfo.Input;
import de.pskiwi.avrremote.log.Logger;

public final class Series08QuickSelectParser {

	public Series08QuickSelectParser(InputStream in) throws IOException {
		this.in = in;
	}

	private String findLine(BufferedReader r, int toFind) throws IOException {
		final String marker = START_MARKER.replace('$', (char) ('0' + toFind));
		String line;
		while ((line = r.readLine()) != null) {
			if (line.contains(marker)) {
				return line;
			}
		}
		return null;
	}

	private Input parseLine(String line) {
		Logger.debug("Series08QuickSelectRename [" + line + "]");
		Matcher matcher = OPTION_PATTERN.matcher(line);
		if (matcher.matches()) {
			String value = matcher.group(1).trim();
			// group(2)->selected
			String display = matcher.group(2).trim();
			Logger.debug("Series08QuickSelectRename [" + value + "]->["
					+ display + "]");
			return new Input(value, display, true);
		} else {
			return null;
		}
	}

	public void parse() throws IOException {
		final BufferedReader r = new BufferedReader(new InputStreamReader(in));
		try {
			int toFind = 1;
			String line;
			while ((line = findLine(r, toFind)) != null) {
				Input in = parseLine(line);
				if (in != null) {
					list.add(in.getRename());
				}
				toFind++;
			}
		} finally {
			r.close();
		}
	}

	public List<String> get() {
		return list;
	}

	public static void main(String[] args) throws FileNotFoundException,
			IOException {
		Series08QuickSelectParser p = new Series08QuickSelectParser(
				new FileInputStream("d_option1.asp"));
		p.parse();
		for (String s : p.get()) {
			System.out.println(s);
		}
	}

	@Override
	public String toString() {
		return "Series08Parser [" + Arrays.toString(list.toArray()) + "]";
	}

	private final List<String> list = new ArrayList<String>();
	private final InputStream in;

	// <option value='TUNER'>TUNER </option>
	private final Pattern OPTION_PATTERN = Pattern
			.compile(".*name='([^']+)' value=\"([^\"]+)\".*");
	private final static String START_MARKER = "name='textQuickSelectNameSelect$'";
}
