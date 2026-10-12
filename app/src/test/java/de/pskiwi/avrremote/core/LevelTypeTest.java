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
package de.pskiwi.avrremote.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.pskiwi.avrremote.core.ZoneState.LevelGroup;
import de.pskiwi.avrremote.core.ZoneState.LevelType;

/**
 * Die Bereiche der Pegel, die die App sendet. Jedes Denon-Protokoll gibt die
 * Kanalpegel mit "**:38 to 62 by ASCII , 50=0dB" an, also +-12 dB. Die
 * vorderen Höhen- und Breitenkanäle standen hier auf 0 bis 99 - der Regler
 * ging damit bis "CVFHL 99", nominell +24,5 dB auf einem einzelnen Kanal.
 */
public class LevelTypeTest {

	@Test
	public void everyChannelLevelSpansTwelveDecibelsEachWay() {
		for (LevelType type : LevelType.values()) {
			if (type.getGroup() != LevelGroup.Channel || type == LevelType.BAL) {
				continue;
			}
			assertEquals(type + " min", 38, type.getMin());
			assertEquals(type + " max", 62, type.getMax());
		}
	}

	@Test
	public void clampKeepsValuesInsideTheRange() {
		assertEquals(50, LevelType.FHL.clamp(50));
		assertEquals(38, LevelType.FHL.clamp(38));
		assertEquals(62, LevelType.FHL.clamp(62));
	}

	@Test
	public void clampLimitsValuesOutsideTheRange() {
		// Ein gekoppelter Kanal folgt seinem Partner um eine Differenz und
		// kann so über den Rand laufen; gesendet wird höchstens der Rand.
		assertEquals(62, LevelType.FHL.clamp(99));
		assertEquals(38, LevelType.FWR.clamp(0));
		assertEquals(56, LevelType.BAS.clamp(60));
		assertEquals(200, LevelType.DEL.clamp(250));
	}
}
