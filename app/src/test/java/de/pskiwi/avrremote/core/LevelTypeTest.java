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
		// Ein Pegel-Preset wird so zurückgespielt, wie es gespeichert wurde -
		// womöglich unter dem alten Bereich; gesendet wird höchstens der Rand.
		assertEquals(62, LevelType.FHL.clamp(99));
		assertEquals(38, LevelType.FWR.clamp(0));
		assertEquals(56, LevelType.BAS.clamp(60));
		assertEquals(200, LevelType.DEL.clamp(250));
	}

	@Test
	public void theCommandSetLevelSendsIsLimitedAndPadded() {
		// Genau dieser String geht in AbstractLevel.setLevel an den Receiver.
		assertEquals("CVFHL 62", LevelType.FHL.command("CV", true, 99));
		assertEquals("CVFHL 50", LevelType.FHL.command("CV", true, 50));
		assertEquals("PSBAS 44", LevelType.BAS.command("PSBAS", false, 10));
		assertEquals("PSLFE 05", LevelType.LFE.command("PSLFE", false, 5));
		assertEquals("PSDELAY 007", LevelType.DEL.command("PSDELAY", false, 7));
	}

	@Test
	public void subwooferOffIsAValueNotBelowTheRange() {
		// "00,38 to 62 by ASCII , 50=0dB,00=OFF" - Abbrechen im Pegeldialog
		// spielt den gemeldeten Wert zurück, "aus" muss "aus" bleiben.
		assertEquals(0, LevelType.SW.clamp(0));
		assertEquals("CVSW 00", LevelType.SW.command("CV", true, 0));
		assertEquals(38, LevelType.SW.clamp(10));
		assertEquals(38, LevelType.FHL.clamp(0));
	}

	@Test
	public void aMacroRecordedUnderTheOldRangeIsLimited() {
		// Ein Makro spielt die aufgezeichneten Befehle wörtlich ab.
		assertEquals("CVFHL 62", LevelType.limit("CVFHL 99"));
		assertEquals("CVFWR 38", LevelType.limit("CVFWR 00"));
		assertEquals("CVFHL 62", LevelType.limit("CVFHL 625"));
		assertEquals("CVFHL 62", LevelType.limit("CVFHL 995"));
	}

	@Test
	public void aMacroInsideTheRangeIsSentAsRecorded() {
		assertEquals("CVFHL 54", LevelType.limit("CVFHL 54"));
		assertEquals("CVFHL 545", LevelType.limit("CVFHL 545"));
		assertEquals("CVFHL 620", LevelType.limit("CVFHL 620"));
		assertEquals("CVSW 00", LevelType.limit("CVSW 00"));
		assertEquals("MVUP", LevelType.limit("MVUP"));
		assertEquals("SIDVD", LevelType.limit("SIDVD"));
		assertEquals("CVZRL", LevelType.limit("CVZRL"));
		assertEquals("CVSW2 50", LevelType.limit("CVSW2 50"));
		assertEquals("Z2CVFL 99", LevelType.limit("Z2CVFL 99"));
	}

	@Test
	public void onlyChannelLevelsAreTouched() {
		// Ton, Effekt und Delay können keinen Lautsprecher überlasten; PSDEL
		// (0-300) und PSDELAY (0-200) sind zudem verschiedene Befehle.
		assertEquals("PSBAS 70", LevelType.limit("PSBAS 70"));
		assertEquals("PSDEL 250", LevelType.limit("PSDEL 250"));
		assertEquals("PSREFLEV 5", LevelType.limit("PSREFLEV 5"));
		// Unsinnig lange Werte passen nicht ins Muster und brechen kein Makro ab.
		assertEquals("CVFHL 99999999999", LevelType.limit("CVFHL 99999999999"));
	}
}
