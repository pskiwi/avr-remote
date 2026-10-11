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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.ZoneState.LevelType;

/**
 * Der AVR-3310 ist das einzige Modell, das es von Hand und generiert gibt:
 * {@link AVR3310} wurde gegen das Gerät geschrieben, {@code GeneratedAVR3310}
 * (nur in den Test-Quellen) erzeugt generate_java.py aus dessen
 * Protokolldokument. Gleich sind sie nicht - die Unterschiede unten sind
 * genau die bekannten, jeder einzeln. Ändert sich eine Regel im Generator,
 * wird es hier rot statt still in 26 Modellen.
 */
public final class GeneratedAVR3310Test {

	private final IAVRModel hand = new AVR3310();
	private final IAVRModel generated = new GeneratedAVR3310();

	/**
	 * Das Dokument kennt USB, die Klasse nicht; TUNER bietet es in
	 * Nordamerika nicht an, wo das Gerät HDRADIO hat.
	 */
	@Test
	public void inputsDifferByUsbAndTunerInNorthAmerica() {
		for (ModelArea area : ModelArea.values()) {
			final List<String> expected = values(hand.getInputSelection(area)
					.getValues());
			expected.add("USB");
			if (area == ModelArea.NorthAmerica) {
				expected.remove("TUNER");
			}
			assertEquals(area.toString(), expected,
					values(generated.getInputSelection(area).getValues()));
		}
	}

	@Test
	public void surroundDiffersByVideoGame() {
		for (ModelArea area : ModelArea.values()) {
			final List<String> withoutVideoGame = values(generated
					.getSurroundSelection(area).getValues());
			assertTrue(withoutVideoGame.remove("VIDEO GAME"));
			assertEquals(values(hand.getSurroundSelection(area).getValues()),
					withoutVideoGame);
		}
	}

	/** Dieselben Quellen, nur in Dokument- statt Klassenreihenfolge. */
	@Test
	public void videoSelectHasTheSameValues() {
		for (ModelArea area : ModelArea.values()) {
			assertEquals(
					new TreeSet<String>(values(hand.getVideoSelection(area)
							.getValues())),
					new TreeSet<String>(values(generated.getVideoSelection(
							area).getValues())));
		}
	}

	@Test
	public void levelsDifferBySurroundBack() {
		final Set<LevelType> expected = EnumSet.copyOf(hand
				.getSupportedLevels());
		expected.add(LevelType.SB);
		assertEquals(expected, generated.getSupportedLevels());
	}

	/**
	 * Die Klasse bietet HDMIMonitor nur über den all()-Default an; VSMONI
	 * steht für den 3310 nicht im Dokument.
	 */
	@Test
	public void optionsDifferByHdmiMonitor() {
		final Set<OptionType> expected = EnumSet.copyOf(hand
				.getSupportedOptions());
		expected.remove(OptionType.HDMIMonitor);
		assertEquals(expected, generated.getSupportedOptions());
	}

	/**
	 * Alles andere stimmt überein. translateZoneFlag und
	 * getDisplayTypeForInput fehlen: ihre Typen hängen an Android-Klassen,
	 * und keine der beiden Klassen überschreibt sie.
	 */
	@Test
	public void everythingElseIsTheSame() {
		assertEquals(hand.getName(), generated.getName());
		assertEquals(hand.getZoneCount(), generated.getZoneCount());
		assertEquals(hand.hasZones(), generated.hasZones());
		assertEquals(hand.hasQuick(), generated.hasQuick());
		assertEquals(hand.hasPgUpDown(), generated.hasPgUpDown());
		assertEquals(hand.isExtraUpdateNeeded(),
				generated.isExtraUpdateNeeded());
		assertEquals(hand.needVolumeAdjust(), generated.needVolumeAdjust());
		assertEquals(hand.hasLevels(), generated.hasLevels());
		assertEquals(hand.getIPodDisplayRows(), generated.getIPodDisplayRows());
		for (String s : new String[] { "OFF", "30", "120" }) {
			assertEquals(hand.getSleepTransformer().convert(s), generated
					.getSleepTransformer().convert(s));
		}
		assertEquals(hand.supportsDAB(), generated.supportsDAB());
		assertEquals(hand.supportsDABMode(), generated.supportsDABMode());
		assertEquals(hand.hasMultiTunerMode(), generated.hasMultiTunerMode());
		assertEquals(hand.getEqPrefix(), generated.getEqPrefix());
		assertEquals(hand.getTunerMemory(), generated.getTunerMemory());
		assertEquals(hand.getTunerPresetPrefix(),
				generated.getTunerPresetPrefix());
		assertEquals(hand.getDynamicEQOnOff(), generated.getDynamicEQOnOff());
		assertEquals(hand.getDynamicEQMode(), generated.getDynamicEQMode());
		assertEquals(hand.getDelayCommand(), generated.getDelayCommand());
		assertEquals(hand.useSeries08Parser(), generated.useSeries08Parser());
		assertEquals(hand.getDisplayMoveMode(), generated.getDisplayMoveMode());
	}

	private static List<String> values(String[] values) {
		return new ArrayList<String>(Arrays.asList(values));
	}
}
