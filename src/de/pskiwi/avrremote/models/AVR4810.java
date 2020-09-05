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

import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;
import de.pskiwi.avrremote.core.ZoneState.LevelType;

public final class AVR4810 extends AbstractModel {

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("PHONO", "CD");
		b.add("TUNER");
		b.add("DVD", "HDP", "TV", "SAT/CBL", "VCR", "DVR", "V.AUX");
		if (area == ModelArea.NorthAmerica) {
			b.add("XM", "SIRIUS", "HDRADIO");
		}
		b.add("IPOD");
		b.add("NET/USB");
		if (area == ModelArea.NorthAmerica) {
			b.add("RHAPSODY");
		}

		b.add("NAPSTER");
		b.add("IRADIO", "SERVER", "USB/IPOD", "FAVORITES");
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("DIRECT", "PURE DIRECT", "STEREO", "STANDARD", "DOLBY DIGITAL",
				"DTS SURROUND");
		if (area == ModelArea.NorthAmerica) {
			b.add("NEURAL");
		}
		b.add("WIDE SCREEN", "MCH STEREO", "SUPER STADIUM", "ROCK ARENA",
				"JAZZ CLUB", "CLASSIC CONCERT", "MONO MOVIE", "MATRIX",
				"VIDEO GAME", "VIRTUAL");
		return b.create();
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "DVR", "HDP", "SAT/CBL", "SOURCE", "TV",
				"V.AUX", "VCR");
	}

	public int getZoneCount() {
		return 4;
	}
	
	@Override
	public Set<LevelType> getSupportedLevels() {
		return TYPE_9CH;
	}

	public String getName() {
		return "AVR-4810";
	}

}