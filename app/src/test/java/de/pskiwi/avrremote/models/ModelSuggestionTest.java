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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Pinnt den Abgleich zwischen dem {@code modelName} einer UPnP-Beschreibung
 * und {@code @array/modelNames}. Der einzige echte Fall ist der AVR-3310, der
 * sich wörtlich meldet ({@code DeviceDescriptionTest}); die Schreibvarianten
 * hier sind das, was die Normalisierung von {@link ModelConfigurator} ohnehin
 * gleich behandelt.
 */
public final class ModelSuggestionTest {

	private static final String[] NAMES = { "AVR-Generic", "AVR-3310",
			"SR-6008", "ASD-51 (experimental)" };

	@Test
	public void exactNameMatches() {
		assertEquals("AVR-3310", ModelSuggestion.match("AVR-3310", NAMES));
	}

	@Test
	public void dashesCaseAndSpacesDoNotCount() {
		assertEquals("SR-6008", ModelSuggestion.match("SR6008", NAMES));
		assertEquals("AVR-3310", ModelSuggestion.match(" avr 3310 ", NAMES));
	}

	@Test
	public void remarkInTheListDoesNotCount() {
		assertEquals("ASD-51 (experimental)",
				ModelSuggestion.match("ASD-51", NAMES));
	}

	@Test
	public void unknownOrMissingGivesNoSuggestion() {
		assertNull(ModelSuggestion.match("AVR-X2700H", NAMES));
		assertNull(ModelSuggestion.match("", NAMES));
		assertNull(ModelSuggestion.match(null, NAMES));
	}

	/**
	 * Zwei Einträge, die nach der Normalisierung gleich aussehen, machten den
	 * Vorschlag von der Reihenfolge in der Liste abhängig - und
	 * {@link ModelConfigurator} löste beide ohnehin in dieselbe Klasse auf.
	 */
	@Test
	public void everyListEntryIsUniqueAndFindsItself() throws Exception {
		final List<String> names = ModelConfiguratorTest.readModelNames();
		final String[] array = names.toArray(new String[0]);
		final Map<String, String> seen = new HashMap<String, String>();
		for (String name : names) {
			assertNull("gleich nach Normalisierung: " + name,
					seen.put(ModelSuggestion.normalize(name), name));
			assertEquals(name, ModelSuggestion.match(name, array));
		}
	}
}
