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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Der AVR-990 steht im selben Protokolldokument wie der AVR-3310, und beide
 * Seiten bauen darauf auf: {@link AVR990} ist ein {@link AVR3310} mit zwei
 * Zonen, {@link AVR990G} wird aus denselben Angaben erzeugt wie
 * {@link AVR3310G}. Die Unterschiede zwischen Hand und Generator stehen
 * deshalb in {@link GeneratedAVR3310Test}; hier nur, was beim 990 dazukommt.
 */
public final class GeneratedAVR990Test {

	private final IAVRModel hand = new AVR990();
	private final IAVRModel generated = new AVR990G();

	/**
	 * Das Dokument nimmt den 990 aus allen Zone-3-Kommandos heraus ("Z3
	 * COMMAND: Invalid at AVR-990"), also zwei Zonen wie in der
	 * handgeschriebenen Klasse.
	 */
	@Test
	public void zoneCountAgreesWithTheHandWrittenClass() {
		assertEquals(2, hand.getZoneCount());
		assertEquals(2, generated.getZoneCount());
	}

	/** Darüber hinaus ist der generierte 990 der generierte 3310, eine Zone weniger. */
	@Test
	public void generatedVariantMatchesTheGenerated3310() {
		final IAVRModel g3310 = new AVR3310G();
		assertEquals("AVR-990-G", generated.getName());
		for (ModelArea area : ModelArea.values()) {
			assertArrayEquals(g3310.getInputSelection(area).getValues(),
					generated.getInputSelection(area).getValues());
			assertArrayEquals(g3310.getSurroundSelection(area).getValues(),
					generated.getSurroundSelection(area).getValues());
			assertArrayEquals(g3310.getVideoSelection(area).getValues(),
					generated.getVideoSelection(area).getValues());
		}
		assertEquals(g3310.getZoneCount() - 1, generated.getZoneCount());
		assertEquals(g3310.getSupportedLevels(), generated.getSupportedLevels());
		assertEquals(g3310.getSupportedOptions(),
				generated.getSupportedOptions());
		assertEquals(g3310.getDynamicEQOnOff(), generated.getDynamicEQOnOff());
		assertEquals(g3310.getDynamicEQMode(), generated.getDynamicEQMode());
		assertEquals(g3310.getDelayCommand(), generated.getDelayCommand());
	}
}
