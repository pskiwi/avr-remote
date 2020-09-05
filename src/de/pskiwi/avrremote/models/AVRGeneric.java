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

import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.Selection;

/** Alles was geht */
public final class AVRGeneric extends AbstractModel {

	public static final String[] SURROUND_MODES = { "DIRECT", "PURE DIRECT",
			"STEREO", "STANDARD", "DOLBY DIGITAL", "DTS SURROUND",
			"7CH STEREO", "MCH STEREO", "ROCK ARENA", "JAZZ CLUB",
			"MONO MOVIE", "MATRIX", "VIRTUAL", "NEURAL", "WIDE SCREEN",
			"MCH STEREO", "SUPER STADIUM", "CLASSIC CONCERT", "VIDEO GAME" };

	public static String[] DEFAULT_INPUTS = { "PHONO", "CD", "BD", "TUNER",
			"DVD", "HDP", "TV", "TV/CBL", "SAT/CBL", "SAT", "VCR", "DVR",
			"V.AUX", "XM", "SIRIUS", "HDRADIO", "IPOD", "GAME", "DOCK",
			"NET/USB", "FAVORITES", "RHAPSODY", "NAPSTER", "PANDORA", "LASTFM",
			"FLICKR", "IRADIO", "SERVER", "USB DIRECT", "USB/IPOD" };

	public static String[] DEFAULT_VIDEO_SELECT = { "DVD", "DVR", "HDP",
			"SAT/CBL", "SOURCE", "TV", "V.AUX", "VCR" };

	public Selection getInputSelection(ModelArea area) {
		return new Selection(DEFAULT_INPUTS);
	}

	public Selection getSurroundSelection(ModelArea area) {
		return new Selection(SURROUND_MODES);
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "DVR", "GAME", "HDP", "SAT/CBL", "SAT",
				"SOURCE", "TV", "V.AUX", "VCR");
	}

	public int getZoneCount() {
		return 3;
	}

	public StatusFlag translateZoneFlag(StatusFlag flag) {
		return flag;
	}

	public String getName() {
		return "AVR-Generic";
	}

}