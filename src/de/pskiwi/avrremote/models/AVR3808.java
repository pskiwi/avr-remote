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

import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.OptionTypeBuilder;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;

public class AVR3808 extends AbstractModel {

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("PHONO", "CD", "TUNER", "DVD", "HDP", "TV/CBL", "SAT", "VCR",
				"DVR", "V.AUX", "NET/USB");
		if (area == ModelArea.NorthAmerica) {
			b.add("XM");
		}
		b.add("IPOD");
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("DIRECT", "PURE DIRECT", "STEREO", "STANDARD", "DOLBY DIGITAL",
				"DTS SURROUND");
		if (area == ModelArea.NorthAmerica) {
			b.add("NEURAL");
		}
		b.add("WIDE SCREEN", "7CH STEREO", "SUPER STADIUM", "ROCK ARENA",
				"JAZZ CLUB", "CLASSIC CONCERT", "MONO MOVIE", "MATRIX",
				"VIDEO GAME", "VIRTUAL");
		if (area == ModelArea.Japan) {
			b.add("MPEG2 AAC");
		}
		return b.create();
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "DVR", "HDP", "TV/CBL", "SAT", "SOURCE",
				"V.AUX", "VCR");
	}

	public int getZoneCount() {
		return 3;
	}

	public String getName() {
		return "AVR-3808";
	}

	public int getIPodDisplayRows() {
		return 8;
	}

	public String getTunerMemory() {
		return "MEMORY";
	}

	public String getDelayCommand() {
		return "PSDELAY";
	}

	public boolean useSeries08Parser() {
		return true;
	}

	@Override
	public DynamicEQOnOff getDynamicEQOnOff() {
		return DynamicEQOnOff.DYN;
	}

	@Override
	public DynamicEQMode getDynamicEQMode() {
		return DynamicEQMode.DYN_SET;
	}

	@Override
	protected Set<OptionType> createSupportedOptions(OptionTypeBuilder builder) {
		return super.createSupportedOptions(builder.add(OptionType.NightMode));
	}

}