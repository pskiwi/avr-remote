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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.ZoneState.LevelType;

/**
 * Die Modelle, die generate_java.py aus avr-remote-specs erzeugt. Die Regeln
 * selbst stehen dort in APP_MAPPING.md; hier wird geprüft, dass ihr Ergebnis
 * in der App ankommt - an Modellen, bei denen die Regel einen Unterschied zum
 * AbstractModel-Default macht.
 */
public final class GeneratedModelTest {

	/** Ein Modell ohne Eingänge wäre in keiner Region bedienbar. */
	@Test
	public void everyGeneratedModelHasInputsInEveryArea() throws Exception {
		final List<IAVRModel> models = generatedModels();
		assertEquals(26, models.size());
		for (IAVRModel model : models) {
			for (ModelArea area : ModelArea.values()) {
				assertFalse(model.getName() + " hat keine Eingänge in " + area,
						model.getInputSelection(area).isEmpty());
			}
		}
	}

	/** getName() ist der Listeneintrag ohne "(experimental)". */
	@Test
	public void nameMatchesClass() throws Exception {
		for (IAVRModel model : generatedModels()) {
			assertEquals(model.getClass().getSimpleName(),
					model.getName().replace("-", ""));
		}
	}

	/** Die einzige Model-Datei mit nur einer Zone; der Default sagt true. */
	@Test
	public void s650hHasNoZones() {
		final IAVRModel model = new AVRS650H();
		assertEquals(1, model.getZoneCount());
		assertFalse(model.hasZones());
	}

	@Test
	public void x3700hFollowsTheFy21Document() {
		final IAVRModel model = new AVRX3700H();
		// Stilles Ende bei 00, nicht bei 99: keine Korrektur
		assertFalse(model.needVolumeAdjust());
		assertSame(DynamicEQOnOff.DYN_EQ, model.getDynamicEQOnOff());
		assertSame(DynamicEQMode.DYN_VOL_HEV, model.getDynamicEQMode());
		assertEquals("030", model.getSleepTransformer().convert("30"));
		assertEquals("0", model.getTunerPresetPrefix());
		assertEquals("PSDELAY", model.getDelayCommand());
		// Steht für kein Modell im Dokument; all() hätte es behauptet
		assertFalse(model.getSupportedOptions().contains(OptionType.NightMode));
		assertTrue(model.getSupportedOptions().contains(OptionType.DRCSetting));
		assertTrue(model.getSupportedLevels().contains(LevelType.SB));
		// DAB-Dialekt der neuen Geräte spricht die App nicht, siehe
		// APP_MAPPING.md in avr-remote-specs
		assertFalse(model.supportsDAB());
	}

	/** Ohne Raumkorrektur und ohne Dynamic EQ: der Stereo-Receiver. */
	@Test
	public void dra800hHasNoDynamicEq() {
		final IAVRModel model = new DRA800H();
		assertSame(DynamicEQOnOff.UNAVAILABLE, model.getDynamicEQOnOff());
		assertSame(DynamicEQMode.UNAVAILABLE, model.getDynamicEQMode());
		assertFalse(model.getSupportedOptions().contains(OptionType.MultiEQ));
	}

	static List<IAVRModel> generatedModels() throws Exception {
		final File[] files = ModelConfiguratorTest.moduleFile(
				"src/generated/java/de/pskiwi/avrremote/models").listFiles();
		assertTrue("generierte Modelle nicht lesbar", files != null);
		final List<IAVRModel> models = new ArrayList<IAVRModel>();
		for (File f : files) {
			final String name = f.getName();
			if (name.endsWith(".java")) {
				models.add((IAVRModel) Class.forName(
						"de.pskiwi.avrremote.models."
								+ name.substring(0, name.length() - 5))
						.getDeclaredConstructor().newInstance());
			}
		}
		return models;
	}
}
