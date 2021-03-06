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

import de.pskiwi.avrremote.core.IParameterConverter;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;
import de.pskiwi.avrremote.core.ZoneState.LevelType;

public class AVR3311 extends AbstractModel {

	private static final Selection MS = new Selection("DIRECT", "PURE DIRECT",
			"STEREO", "STANDARD", "DOLBY DIGITAL", "DTS SURROUND",
			"MCH STEREO", "ROCK ARENA", "JAZZ CLUB", "MONO MOVIE", "MATRIX",
			"VIDEO GAME", "VIRTUAL");

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("PHONO", "CD");
		if (area != ModelArea.NorthAmerica) {
			b.add("TUNER");
		}
		b.add("DVD", "BD", "HDP", "TV", "SAT/CBL", "DVR", "GAME", "V.AUX",
				"DOCK");
		if (area == ModelArea.NorthAmerica) {
			b.add("SIRIUS", "HDRADIO");
		}
		b.add("IPOD");
		b.add("NET/USB");
		if (area == ModelArea.NorthAmerica) {
			b.add("RHAPSODY");
			b.add("PANDORA");
		}

		if (area == ModelArea.NorthAmerica || area == ModelArea.Europe) {
			b.add("NAPSTER");
		}
		if (area == ModelArea.Europe) {
			b.add("LASTFM");
		}
		b.add("FLICKR");
		b.add("IRADIO", "SERVER", "FAVORITES", "USB/IPOD");
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		return MS;
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "BD", "TV", "SAT/CBL", "DVR", "GAME",
				"V.AUX", "DOCK", "SOURCE");
	}

	public int getZoneCount() {
		return 3;
	}

	@Override
	public IParameterConverter getSleepTransformer() {
		return LEEDING_ZERO_SLEEP_TRANSFORMER;
	}
	
	@Override
	public DynamicEQOnOff getDynamicEQOnOff() {
		return DynamicEQOnOff.DYN_EQ;
	}

	@Override
	public DynamicEQMode getDynamicEQMode() {
		return DynamicEQMode.DYN_VOL;
	}

	public String getName() {
		return "AVR-3311";
	}

	@Override
	public Set<LevelType> getSupportedLevels() {
		return TYPE_9CH;
	}

}