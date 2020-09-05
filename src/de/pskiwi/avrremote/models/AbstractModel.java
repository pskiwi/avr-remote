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
import de.pskiwi.avrremote.core.DisplayMoveMode;
import de.pskiwi.avrremote.core.IParameterConverter;
import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.OptionTypeBuilder;
import de.pskiwi.avrremote.core.ZoneState.LevelType;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;

public abstract class AbstractModel implements IAVRModel {

	public AbstractModel() {
	}

	protected Set<OptionType> createSupportedOptions(OptionTypeBuilder builder) {
		return builder.get();
	}

	public StatusFlag translateZoneFlag(StatusFlag flag) {
		return flag;
	}

	public boolean hasPgUpDown() {
		return true;
	}

	public boolean hasZones() {
		return true;
	}

	public boolean isExtraUpdateNeeded() {
		return false;
	}

	public boolean needVolumeAdjust() {
		return true;
	}

	public boolean hasQuick() {
		return true;
	}

	public boolean hasLevels() {
		return true;
	}

	public Set<LevelType> getSupportedLevels() {
		return TYPE_7CH;
	}

	public int getIPodDisplayRows() {
		return 9;
	}

	public DisplayType getDisplayTypeForInput(String input) {
		return null;
	}

	public boolean isSpecialTunerHandling(ModelArea area) {
		return false;
	}

	public final Set<OptionType> getSupportedOptions() {
		if (options == null) {
			options = createSupportedOptions(new OptionTypeBuilder().all());
		}
		return options;
	}

	public IParameterConverter getSleepTransformer() {
		return IParameterConverter.ID_TRANSFORMER;
	}

	public boolean supportsDAB() {
		return false;
	}

	public String getEqPrefix() {
		return "MULTEQ:";
	}

	public String getTunerMemory() {
		return "MEM";
	}

	public String getTunerPresetPrefix() {
		return "A";
	}

	public boolean supportsDABMode() {
		return false;
	}

	public DynamicEQMode getDynamicEQMode() {
		return DynamicEQMode.DYN_SET;
	}

	public DynamicEQOnOff getDynamicEQOnOff() {
		return DynamicEQOnOff.DYN_VOL;
	}

	public boolean hasMultiTunerMode() {
		return true;
	}

	public String getDelayCommand() {
		return "PSDEL";
	}

	public boolean useSeries08Parser() {
		return false;
	}

	public DisplayMoveMode getDisplayMoveMode() {
		return DisplayMoveMode.Classic;
	}

	protected Set<OptionType> options;

	protected final static Set<LevelType> TYPE_9CH = new HashSet<LevelType>();
	protected final static Set<LevelType> TYPE_7CH = new HashSet<LevelType>();
	static {
		for (LevelType lt : LevelType.values()) {
			if (lt != LevelType.SB) {
				TYPE_9CH.add(lt);
				TYPE_7CH.add(lt);
			}
		}
		TYPE_7CH.remove(LevelType.FWL);
		TYPE_7CH.remove(LevelType.FWR);

		// Hat nur MCR603
		TYPE_7CH.remove(LevelType.BAL);
		TYPE_9CH.remove(LevelType.BAL);

		TYPE_7CH.remove(LevelType.BAS_EXT);
		TYPE_7CH.remove(LevelType.TRE_EXT);

		TYPE_9CH.remove(LevelType.TRE_EXT);
		TYPE_9CH.remove(LevelType.BAS_EXT);
	}

	protected final static IParameterConverter LEEDING_ZERO_SLEEP_TRANSFORMER = new IParameterConverter() {

		public String convert(String s) {
			if (s.length() >= 3) {
				return s;
			}
			if (s.length() == 2) {
				return "0" + s;
			}
			return "00" + s;
		}
	};

}