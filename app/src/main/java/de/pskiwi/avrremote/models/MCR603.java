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
import de.pskiwi.avrremote.core.IParameterConverter;
import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.OptionTypeBuilder;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;
import de.pskiwi.avrremote.core.ZoneState.LevelType;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;

/** Marantz M-CR603 */
public final class MCR603 extends AbstractModel {
	
	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();

		b.add("TUNER", "CD", "SERVER", "IRADIO", "USB", "AUXA", "AUXB", "AUXC",
				"AUXD");

		if (area == ModelArea.NorthAmerica || area == ModelArea.Europe) {
			b.add("NAPSTER");
		}
		if (area == ModelArea.Europe) {
			b.add("LASTFM");
		}
		if (area == ModelArea.NorthAmerica) {
			b.add("RHAPSODY");
			b.add("PANDORA");
		}
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
		return "M-CR603";
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

	public boolean isSpecialTunerHandling() {
		return true;
	}

	@Override
	protected Set<OptionType> createSupportedOptions(OptionTypeBuilder builder) {
		builder.clear().add(OptionType.SleepSetting).add(
				OptionType.DynamicBassBoost).add(OptionType.SourceDirect);
		return super.createSupportedOptions(builder);
	}

	@Override
	public Set<LevelType> getSupportedLevels() {
		final Set<LevelType> ret = new HashSet<LevelType>();
		ret.add(LevelType.BAS_EXT);
		ret.add(LevelType.TRE_EXT);
		ret.add(LevelType.BAL);

		return ret;
	}

	public boolean supportsDAB() {
		return true;
	}

	@Override
	public IParameterConverter getSleepTransformer() {
		return LEEDING_ZERO_SLEEP_TRANSFORMER;
	}

	public String getTunerMemory() {
		return "MEMORY";
	}

	public String getTunerPresetPrefix() {
		return "0";
	}

}
