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

import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;

/** MARANTZ Media Player */
public class DNP720AE extends AbstractModel {

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("TUNER");
		if (area == ModelArea.NorthAmerica) {
			b.add("RHAPSODY");
		}
		b.add("NAPSTER");
		b.add("PANDORA");
		b.add("LASTFM");
		b.add("IRADIO", "SERVER", "USB");
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

	public boolean hasQuick() {
		return false;
	}

	@Override
	public boolean hasLevels() {
		return false;
	}

	public String getName() {
		return "DNP-720AE";
	}

}