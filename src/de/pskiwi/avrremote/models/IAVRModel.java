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

import java.util.Set;

import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.DisplayMoveMode;
import de.pskiwi.avrremote.core.IParameterConverter;
import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;

/** Geräte Abstraktion */
public interface IAVRModel {
	// Mögliche Inputs
	Selection getInputSelection(ModelArea area);

	// Mögliche Surround-Modi
	Selection getSurroundSelection(ModelArea area);

	// Mögliche Video-Modi
	Selection getVideoSelection(ModelArea area);

	// Maximale Anzahl Zonen
	int getZoneCount();

	// Lesbarer Name
	String getName();

	// Enable-Flag übersetzen
	StatusFlag translateZoneFlag(StatusFlag flag);

	// Hat das Gerät Zonen
	boolean hasZones();

	// Quick Menu verfügbar
	boolean hasQuick();

	// PgUp/Down verfügbar oder muss es emuliert werden
	boolean hasPgUpDown();

	// Screen-Befehle benötigen Extra-Update
	boolean isExtraUpdateNeeded();

	// Lautstärken-Korrektur
	boolean needVolumeAdjust();

	// Werden Level überhaupt unterstützt
	boolean hasLevels();

	// Unterstützte Levels
	Set<ZoneState.LevelType> getSupportedLevels();

	// IPA 0..n (9=3310,8 3308)
	int getIPodDisplayRows();

	// Display für Input, bei Default "null"
	DisplayType getDisplayTypeForInput(String input);

	/** Welche Optionen werden vom Modell unterstützt */
	Set<OptionType> getSupportedOptions();

	/** Parameter vom Sleep-Command ("30" oder "030") */
	IParameterConverter getSleepTransformer();

	/** Unterstützt das Modell DAB */
	boolean supportsDAB();

	/** "MULTEQ:" oder "ROOM EQ:" */
	String getEqPrefix();

	/** "MEM" oder "MEMORY" */
	String getTunerMemory();

	/** Sind die ersten Presets "A1"-"A6" oder "01"-"06" */
	String getTunerPresetPrefix();

	/** Werden DAB-Modes unterstützt */
	boolean supportsDABMode();

	DynamicEQOnOff getDynamicEQOnOff();
	DynamicEQMode getDynamicEQMode();

	/**
	 * Analog/DAB in einem Modus TUNER (true) ? (MCR603) oder extra(4308)
	 * TUNER/DAB(false)
	 */
	boolean hasMultiTunerMode();

	/** PSDEL[DELAY] */
	String getDelayCommand();

	/** HTML/Parser für 08er Modelle verwenden */
	boolean useSeries08Parser();

	/** Wie wird die Zeile ausgewählt */
	DisplayMoveMode getDisplayMoveMode();
}
