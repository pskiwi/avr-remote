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
package de.pskiwi.avrremote.models;

import java.util.Locale;

/**
 * Welcher Eintrag aus {@code @array/modelNames} passt zu dem, was der Receiver
 * in seiner UPnP-Beschreibung als {@code modelName} meldet ?
 *
 * Nur ein Vorschlag: der Anwender bestätigt ihn. Bestätigt ist bisher nur, dass
 * ein AVR-3310 wörtlich seinen Listeneintrag meldet. Verglichen wird deshalb
 * so, wie {@link ModelConfigurator#createModel(String)} den Namen ohnehin
 * behandelt - Bindestriche und Anmerkung in Klammern zählen nicht -, dazu ohne
 * Groß/Klein und Leerzeichen. Weitere Regeln (Präfixe, regionale Varianten)
 * erst, wenn ein Bericht sie zeigt.
 */
public final class ModelSuggestion {

	private ModelSuggestion() {
	}

	/** Der passende Listeneintrag, oder null. */
	public static String match(String reportedModelName, String[] modelNames) {
		if (reportedModelName == null) {
			return null;
		}
		final String wanted = normalize(reportedModelName);
		if (wanted.length() == 0) {
			return null;
		}
		for (String name : modelNames) {
			if (wanted.equals(normalize(name))) {
				return name;
			}
		}
		return null;
	}

	/** "ASD-51 (experimental)" -> "ASD51" */
	static String normalize(String name) {
		final int p = name.indexOf('(');
		final String withoutRemark = p == -1 ? name : name.substring(0, p);
		return withoutRemark.replace("-", "").replace(" ", "").trim()
				.toUpperCase(Locale.US);
	}
}
