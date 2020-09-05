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
/**
 * 
 */
package de.pskiwi.avrremote.models;

import java.util.Set;

import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.OptionTypeBuilder;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;

public class AVR5308 extends AbstractModel {

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("PHONO", "CD", "TUNER", "DVD", "HDP", "TV/CBL", "SAT", "VCR",
				"DVR-1", "DVR-2", "V.AUX", "NET/USB");
		if (area == ModelArea.NorthAmerica) {
			b.add("XM");
			b.add("SIRIUS");
			b.add("HDRADIO");
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
		b.add("DOLBY H/P", "HOME THX CINEMA");
		b.add("WIDE SCREEN", "7CH STEREO", "SUPER STADIUM", "ROCK ARENA",
				"JAZZ CLUB", "CLASSIC CONCERT", "MONO MOVIE", "MATRIX",
				"VIDEO GAME");
		if (area == ModelArea.Japan) {
			b.add("MPEG2 AAC");
		}
		return b.create();
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "HDP", "TV/CBL", "SAT", "VCR", "DVR-1",
				"DVR-2", "SOURCE", "V.AUX");
	}

	public int getZoneCount() {
		return 4;
	}

	public String getName() {
		return "AVR-5308";
	}

	public String getEqPrefix() {
		return "ROOM EQ:";
	}

	public boolean useSeries08Parser() {
		return true;
	}

	@Override
	protected Set<OptionType> createSupportedOptions(OptionTypeBuilder builder) {
		return super.createSupportedOptions(builder.add(OptionType.NightMode));
	}

}