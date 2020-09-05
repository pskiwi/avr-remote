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
package de.pskiwi.avrremote.core;

import de.pskiwi.avrremote.core.ZoneState.OptionGroup;

public enum OptionType {
	NightMode(OptionGroup.MISC, true), AudioMode(OptionGroup.AUDIO), MultiEQ(
			OptionGroup.AUDIO), AudioRestorer(OptionGroup.AUDIO), CinemaEQMode(
			OptionGroup.AUDIO), SurroundBackSPMode(OptionGroup.AUDIO), HDMIMonitor(
			OptionGroup.VIDEO), DCOSetting(OptionGroup.AUDIO), SleepSetting(
			OptionGroup.MISC), ToneControl(OptionGroup.AUDIO), DRCSetting(
			OptionGroup.AUDIO), DynamicVolume(OptionGroup.AUDIO), ReferenceLevelOffset(
			OptionGroup.AUDIO), DynamicVolumeSetting(OptionGroup.AUDIO), FrontSpeakerSetting(
			OptionGroup.AUDIO), VideoMode(OptionGroup.VIDEO), VideoSelect(
			OptionGroup.VIDEO), VideoResolution(OptionGroup.VIDEO), HDMIVideoResolution(
			OptionGroup.VIDEO), HDMIAudioOutputMode(OptionGroup.VIDEO), SourceDirect(
			OptionGroup.AUDIO, true), DynamicBassBoost(OptionGroup.AUDIO, true);

	OptionType(OptionGroup optionGroup) {
		this(optionGroup, false);
	}

	OptionType(OptionGroup optionGroup, boolean optional) {
		this.optionGroup = optionGroup;
		this.optional = optional;
	}

	public OptionGroup getOptionGroup() {
		return optionGroup;
	}

	public boolean isOptional() {
		return optional;
	}

	private final boolean optional;
	private final OptionGroup optionGroup;
}