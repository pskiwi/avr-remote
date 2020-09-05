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

import java.util.HashSet;
import java.util.Set;

import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;
import de.pskiwi.avrremote.core.ZoneState.LevelType;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;

/** Denon CEOL RCD N7 */
public final class RCDN7 extends AbstractModel {
	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("FAVORITES", "IRADIO", "SERVER");
		b.add("LASTFM");
		b.add("NAPSTER");
		b.add("IPOD", "USB", "TUNER", "FM", "AM", "CD", "AUX1", "AUX2", "AUX3");
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		return new Selection();
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection();
	}

	public int getZoneCount() {
		return 1;
	}

	public StatusFlag translateZoneFlag(StatusFlag flag) {
		return StatusFlag.Power;
	}

	public boolean hasPgUpDown() {
		return false;
	}

	@Override
	public boolean isExtraUpdateNeeded() {
		return true;
	}

	public boolean hasZones() {
		return false;
	}

	@Override
	public boolean needVolumeAdjust() {
		return false;
	}

	public String getName() {
		return "RCD-N7";
	}

	public boolean hasQuick() {
		return false;
	}
	
	@Override
	public DisplayType getDisplayTypeForInput(String input) {
		if ("CD".equals(input)) {
			return DisplayType.BD;
		}
		return super.getDisplayTypeForInput(input);
	}

	@Override
	public Set<LevelType> getSupportedLevels() {
		final Set<LevelType> ret = new HashSet<LevelType>();
		ret.add(LevelType.BAS);
		ret.add(LevelType.TRE);
		ret.add(LevelType.FL);
		ret.add(LevelType.FR);
		return ret;
	}

}
