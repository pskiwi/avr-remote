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

public class AVR4806 extends AbstractModel {

	private static final Selection MS = new Selection("DIRECT", "PURE DIRECT",
			"STEREO", "MULTI CH IN", "MULTI CH DIRECT", "MULTI CH PURE D",
			"DOLBY PRO LOGIC", "DOLBY PL2", "DOLBY PL2x", "DOLBY DIGITAL",
			"DOLBY D EX", "DTS NEO:6", "DTS SURROUND", "DTS ES DSCRT6.1",
			"DTS ES MTRX6.1", "WIDE SCREEN", "5CH STEREO", "7CH STEREO",
			"9CH STEREO", "SUPER STADIUM", "ROCK ARENA", "JAZZ CLUB",
			"CLASSIC CONCERT", "MONO MOVIE", "MATRIX", "VIDEO GAME", "VIRTUAL",
			"MPEG2 AAC", "AAC+DOLBY EX");

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("PHONO", "CD");
		b.add("TUNER");
		b.add("DVD", "VDP", "TV", "DBS", "VCR-1", "VCR-2", "VCR-3", "V.AUX");
		b.add("CDR/TAPE");
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		return MS;
	}

	public Selection getVideoSelection(ModelArea area) {
		return new Selection("DVD", "VDP", "TV", "DBS", "VCR-1", "VCR-2",
				"VCR-3", "SOURCE", "V.AUX");
	}

	public int getZoneCount() {
		return 3;
	}

	public String getName() {
		return "AVR-4806";
	}

}