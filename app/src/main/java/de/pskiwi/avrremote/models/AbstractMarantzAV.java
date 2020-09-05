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
import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.OptionTypeBuilder;
import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.SelectionBuilder;
import de.pskiwi.avrremote.core.ZoneState.LevelType;

public abstract class AbstractMarantzAV extends AbstractModel {

	protected enum AVModel {
		AV7701("AV-7701", 3, TYPE_9CH), AV8801("AV-8801", 3, TYPE_9CH), AV7005(
				"AV-7005", 3, TYPE_9CH), SR7008("SR-7008", 3, TYPE_9CH), SR7007(
				"SR-7007", 3, TYPE_9CH), SR7005("SR-7005", 3, TYPE_9CH), SR6005(
				"SR-6005", 2, TYPE_7CH), SR6006("SR-6006", 3, TYPE_9CH), SR6007(
				"SR-6007", 3, TYPE_9CH), SR6008("SR-6008", 3, TYPE_9CH), SR5006(
				"SR-5006", 2, TYPE_7CH), SR5007("SR-5007", 2, TYPE_7CH), SR5008(
				"SR-5008", 2, TYPE_7CH), NR1602("NR-1602", 2, TYPE_7CH), NR1603(
				"NR-1603", 2, TYPE_7CH), NR1604("NR-1604", 2, TYPE_7CH), NR1504(
				"NR-1504", 2, TYPE_7CH);

		private AVModel(String name, int zoneCount, Set<LevelType> levelTypes) {
			this.name = name;
			this.zoneCount = zoneCount;
			this.levelTypes = levelTypes;
		}

		public int getZoneCount() {
			return zoneCount;
		}

		public String getName() {
			return name;
		}

		public Set<LevelType> getLevelTypes() {
			return levelTypes;
		}

		private final Set<LevelType> levelTypes;
		private final int zoneCount;
		private final String name;
	};

	public AbstractMarantzAV(AVModel model) {
		this.model = model;
	}

	@Override
	public IParameterConverter getSleepTransformer() {
		return LEEDING_ZERO_SLEEP_TRANSFORMER;
	}

	private boolean match(AVModel... models) {
		for (AVModel m : models) {
			if (m == model) {
				return true;
			}
		}
		return false;
	}

	public Selection getInputSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.AV7005, AVModel.SR7005, AVModel.SR6008,
				AVModel.SR6007, AVModel.SR6006)) {
			b.add("PHONO");
		}
		b.add("CD", "TUNER");
		b.add("DVD", "BD", "TV");
		addSATCBL(b);

		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.SR6008, AVModel.SR6007, AVModel.SR5008,
				AVModel.SR5007, AVModel.NR1603, AVModel.NR1604, AVModel.NR1504)) {
			b.add("MPLAY");
		}

		b.add("VCR", "DVR", "GAME");
		addAUX(b);
		b.add("DOCK");
		if (area == ModelArea.NorthAmerica) {
			b.add("SIRIUS", "HDRADIO");
		}
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
		b.add("IRADIO", "SERVER", "FAVORITES", "CDR", "M-XPORT", "USB", "IPD");
		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.SR6008, AVModel.SR6007, AVModel.SR5008,
				AVModel.SR5007, AVModel.NR1603, AVModel.NR1604, AVModel.NR1504)) {
			b.add("INET");
		}

		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.SR6008, AVModel.SR6007, AVModel.SR6006,
				AVModel.SR5006, AVModel.SR5008, AVModel.SR5007, AVModel.NR1603,
				AVModel.NR1604, AVModel.NR1504)) {
			b.add("FVP", "OTP");
		}
		return b.create();
	}

	public Selection getSurroundSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("DIRECT", "PURE DIRECT", "STEREO", "AUTO");
		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.SR6008, AVModel.SR6007, AVModel.SR6006,
				AVModel.SR6005, AVModel.SR5008, AVModel.SR5007, AVModel.NR1603,
				AVModel.NR1604, AVModel.NR1504)) {
			b.add("MOVIE", "MUSIC", "GAME");

		}
		if (match(AVModel.AV7005, AVModel.SR7005, AVModel.SR6005)) {
			b.add("NEURAL", "STANDARD");
		}
		b.add("DOLBY DIGITAL");

		b.add("DTS SURROUND");
		if (match(AVModel.AV7005, AVModel.SR7005, AVModel.SR6005)) {
			b.add("MATRIX");
		}
		b.add("MCH STEREO", "VIRTUAL");
		return b.create();
	}

	public Selection getVideoSelection(ModelArea area) {
		final SelectionBuilder b = new SelectionBuilder();
		b.add("DVD", "BD", "TV");
		addSATCBL(b);
		b.add("VCR", "GAME");
		addAUX(b);
		b.add("SOURCE");
		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.SR6008, AVModel.SR6007, AVModel.SR5008,
				AVModel.SR5007, AVModel.NR1603, AVModel.NR1604, AVModel.NR1504)) {
			b.add("MPLAY");
		}
		return b.create();

	}

	private void addAUX(final SelectionBuilder b) {
		if (match(AVModel.AV7005, AVModel.SR7005, AVModel.SR6005)) {
			b.add("V.AUX");
		} else {
			if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
					AVModel.SR7007, AVModel.SR6008, AVModel.SR6007,
					AVModel.SR6006, AVModel.SR5008, AVModel.SR5007,
					AVModel.SR5006, AVModel.NR1604, AVModel.NR1603,
					AVModel.NR1504)) {
				b.add("AUX1");
				if (!match(AVModel.NR1504)) {
					b.add("AUX2");
					if (match(AVModel.AV8801)) {
						b.add("AUX3", "AUX4", "AUX5", "AUX6", "AUX7");
					}
				}
			}
		}
	}

	private void addSATCBL(final SelectionBuilder b) {
		if (match(AVModel.AV7701, AVModel.AV8801, AVModel.SR7008,
				AVModel.SR7007, AVModel.AV7005, AVModel.SR7005, AVModel.SR6008,
				AVModel.SR6007, AVModel.SR5008, AVModel.SR5007, AVModel.SR6005,
				AVModel.NR1603, AVModel.NR1604, AVModel.NR1504)) {
			b.add("SAT/CBL");
		} else {
			b.add("SAT");
		}
	}

	public int getZoneCount() {
		return model.getZoneCount();
	}

	public boolean hasQuick() {
		return false;
	}

	@Override
	public Set<LevelType> getSupportedLevels() {
		return model.getLevelTypes();
	}

	public String getName() {
		return model.getName();
	}

	@Override
	protected Set<OptionType> createSupportedOptions(OptionTypeBuilder options) {
		if (match(AVModel.NR1602, AVModel.SR5008, AVModel.SR5007,
				AVModel.SR5006, AVModel.NR1504)) {
			options.remove(OptionType.HDMIMonitor);
			options.remove(OptionType.HDMIVideoResolution);
			options.remove(OptionType.VideoResolution);
			options.remove(OptionType.AudioMode);
		}
		return super.createSupportedOptions(options);
	}

	private final AVModel model;
}
