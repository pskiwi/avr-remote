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

import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;

public class AVR3310 extends AbstractModel {

	private static final Selection MS = new Selection("DIRECT", "PURE DIRECT",
			"STEREO", "STANDARD", "DOLBY DIGITAL", "DTS SURROUND",
			"7CH STEREO", "ROCK ARENA", "JAZZ CLUB", "MONO MOVIE", "MATRIX",
			"VIRTUAL");

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("PHONO", "CD");
		b.add("TUNER");
		b.add("DVD", "HDP", "TV", "SAT/CBL", "VCR", "DVR", "V.AUX");
		if (area == ModelArea.NorthAmerica) {
			b.add("SIRIUS", "HDRADIO");
		}
		b.add("IPOD");
		if (area != ModelArea.Japan) {
			b.add("NET/USB");
		}
		if (area == ModelArea.NorthAmerica) {
			b.add("RHAPSODY");
		}

		if (area != ModelArea.Japan && area != ModelArea.China) {
			b.add("NAPSTER");
		}
		if (area != ModelArea.Japan) {
			b.add("IRADIO", "SERVER", "FAVORITES", "USB DIRECT");
		}
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		return MS;
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "DVR", "HDP", "SAT/CBL", "SOURCE", "TV",
				"V.AUX", "VCR");
	}

	public int getZoneCount() {
		return 3;
	}

	public String getDelayCommand() {
		return "PSDELAY";
	}

	@Override
	public DynamicEQOnOff getDynamicEQOnOff() {
		return DynamicEQOnOff.DYN_VOL;
	}

	@Override
	public DynamicEQMode getDynamicEQMode() {
		return DynamicEQMode.DYN_SET;
	}

	public String getName() {
		return "AVR-3310";
	}

}