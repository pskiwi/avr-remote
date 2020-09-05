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
package de.pskiwi.avrremote.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.pskiwi.avrremote.EnableManager;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.VolumeDisplay;
import de.pskiwi.avrremote.core.display.BDDisplay;
import de.pskiwi.avrremote.core.display.DisplayManager;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;
import de.pskiwi.avrremote.core.display.NetDisplay;
import de.pskiwi.avrremote.core.display.TunerDisplay;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.DynamicEQMode;
import de.pskiwi.avrremote.models.DynamicEQOnOff;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class ZoneState {

	private final static class ClassMap {

		public Collection<IAVRState> values() {
			return classMap.values();
		}

		@SuppressWarnings("unchecked")
		public <T> T get(Class<T> cl) {
			T t = (T) classMap.get(cl);
			if (t == null) {
				throw new IllegalArgumentException(cl.getName());
			}
			return t;
		}

		public Iterable<? extends IAVRState> getAll(
				Class<? extends IAVRState> cl) {
			List<IAVRState> ret = new ArrayList<IAVRState>();
			for (IAVRState s : classMap.values()) {
				if (cl.isInstance(s)) {
					ret.add(s);
				}
			}
			return ret;
		}

		public void addClass(IAVRState s) {
			checkDuplicatePrefix(s);
			classMap.put(s.getClass(), s);
		}

		public void addObject(IAVRState state) {
			checkDuplicatePrefix(state);
			classMap.put(state, state);
		}

		private void checkDuplicatePrefix(IAVRState check) {
			for (IAVRState s : classMap.values()) {
				if (s.getCommandPrefix().equals(check.getReceivePrefix())) {
					throw new RuntimeException("duplicate prefix ["
							+ check.getReceivePrefix() + "] " + s + " " + check);
				}
			}

		}

		private final Map<Object, IAVRState> classMap = new LinkedHashMap<Object, IAVRState>();

	}

	public abstract class AbstractSelect implements IAVRState {

		public AbstractSelect(int id, String prefix, boolean encoded,
				Selection selection) {
			this.id = id;
			this.prefix = prefix;
			this.encoded = encoded;
			this.selection = selection;
		}

		public int getDisplayId() {
			return id;
		}

		public String[] getValues() {
			return selection.getValues();
		}

		public String getDisplay(String text) {
			return selection.getDisplay(text);
		}

		public String[] getDisplayValues() {
			return selection.getDisplayValues();
		}

		public Selection getSelection() {
			return selection;
		}

		public String getCommandPrefix() {
			return prefix;
		}

		public String getReceivePrefix() {
			return getCommandPrefix();
		}

		public boolean isDefined() {
			return selected != null;
		}

		public boolean update(InData v) {
			final String old = selected;
			selected = v.toString();

			return !selected.equals(old);
		}

		public void select(String newSelected) {
			if (selected == null || !newSelected.equals(selected)) {
				getSender().sendCommand(zone, this, newSelected);
			}
		}

		public String getSelected() {
			return selected != null ? selected : N_D;
		}

		public String getSelectedDisplay() {
			if (selected == null) {
				return N_D;
			}
			return selection.getDisplay(selected);
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return encoded;
		}

		public void reset() {
			selected = null;
		}

		public boolean isAutoUpdate() {
			return true;
		}

		private String selected;

		private final int id;
		private final String prefix;
		private final boolean encoded;
		private final Selection selection;
	}

	public enum OptionGroup {
		AUDIO("Audio"), VIDEO("Video"), MISC("Misc");

		private OptionGroup(String title) {
			this.title = title;
		}

		public String getTitle() {
			return title;
		}

		@Override
		public String toString() {
			return title;
		}

		private final String title;
	}

	public abstract class AbstractManualSelect extends AbstractSelect {

		public AbstractManualSelect(OptionType optionType, int id,
				String prefix, boolean encoded, Selection selection) {
			super(id, prefix, encoded, selection);
			this.optionType = optionType;
		}

		@Override
		public boolean isAutoUpdate() {
			return false;
		}

		public OptionGroup getOptionGroup() {
			return optionType.getOptionGroup();
		}

		public OptionType getOptionType() {
			return optionType;
		}

		private final OptionType optionType;
	}

	public class AudioMode extends AbstractManualSelect {
		public AudioMode() {
			super(OptionType.AudioMode, R.string.AudioMode, "PSMODE:", false,
					new Selection("MUSIC", "CINEMA", "GAME", "PRO LOGIC",
							"HEIGHT"));
		}
	}

	public class SourceDirect extends AbstractManualSelect {
		public SourceDirect() {
			super(OptionType.SourceDirect, R.string.SourceDirect, "PSSDI ",
					false, new Selection("ON", "OFF"));
		}
	}

	public class DynamicBassBoost extends AbstractManualSelect {
		public DynamicBassBoost() {
			super(OptionType.DynamicBassBoost, R.string.DynamicBassBoost,
					"PSSDB ", false, new Selection("ON", "OFF"));
		}
	}

	public class MultEQMode extends AbstractManualSelect {
		public MultEQMode(ModelConfigurator modelConfigurator) {
			super(
					OptionType.MultiEQ,
					R.string.MultiEQMode,
					"PS" + modelConfigurator.getModel().getEqPrefix(),
					false,
					new Selection("AUDYSSEY", "BYP.LR", "FLAT", "MANUAL", "OFF"));
		}
	}

	public class AudioRestorer extends AbstractManualSelect {
		public AudioRestorer() {
			super(OptionType.AudioRestorer, R.string.AudioRestorer, "PSRSTR ",
					false, new Selection(new String[] { "OFF", "MODE1",
							"MODE2", "MODE3" }, new String[] { "OFF",
							"Restorer 64", "Restorer 96", "Restorer HQ" }));
		}
	}

	public class NightMode extends AbstractManualSelect {
		public NightMode() {
			super(OptionType.NightMode, R.string.NightMode, "PSNIGHT ", false,
					new Selection(new String[] { "OFF", "LOW", "MID", "HI" }));
		}
	}

	public class CinemaEQMode extends AbstractManualSelect {
		public CinemaEQMode() {
			super(OptionType.CinemaEQMode, R.string.CinemaEQMode,
					"PSCINEMA EQ.", false, new Selection("ON", "OFF"));
		}
	}

	public class SurroundBackMode extends AbstractManualSelect {
		public SurroundBackMode() {
			super(OptionType.SurroundBackSPMode, R.string.SurroundBackSPMode,
					"PSSB:", false, new Selection("MTRX ON", "PL2X CINEMA",
							"PL2X MUSIC", "ON", "OFF"));
		}
	}

	public class ToneControl extends AbstractManualSelect {
		public ToneControl() {
			super(OptionType.ToneControl, R.string.ToneControl, "PSTONE CTRL ",
					false, new Selection("ON", "OFF"));
		}
	}

	public class DynamicEQ extends AbstractManualSelect {
		public DynamicEQ(DynamicEQOnOff mode) {
			super(OptionType.DynamicVolume, R.string.DynamicEQ, mode
					.getCommand(), false, new Selection(mode.getValues()));
		}
	}

	public class DynamicEQSetting extends AbstractManualSelect {
		public DynamicEQSetting(DynamicEQMode mode) {
			super(OptionType.DynamicVolumeSetting,
					R.string.DynamicVolumeSetting, mode.getCommand(), false,
					new Selection(mode.getValues(), mode.getDesc()));
		}
	}

	public class DRCSetting extends AbstractManualSelect {
		public DRCSetting() {
			super(OptionType.DRCSetting, R.string.DRCSetting, "PSDRC ", false,
					new Selection("AUTO", "LOW", "MID", "HI", "OFF"));
		}
	}

	public class ReferenceLevelOffsetSetting extends AbstractManualSelect {
		public ReferenceLevelOffsetSetting() {
			super(OptionType.ReferenceLevelOffset,
					R.string.ReferenceLevelOffsetSetting, "PSREFLEV ", false,
					new Selection("0", "5", "10", "15"));
		}
	}

	public class DCOSetting extends AbstractManualSelect {
		public DCOSetting() {
			super(OptionType.DCOSetting, R.string.DCOSetting, "PSDCO ", false,
					new Selection("LOW", "MID", "HI", "OFF"));
		}
	}

	public class FrontSpeakerSetting extends AbstractManualSelect {
		public FrontSpeakerSetting() {
			super(OptionType.FrontSpeakerSetting, R.string.FrontSpeakerSetting,
					"PSFRONT ", false, new Selection(new String[] { "SPA",
							"SPB", "A+B" }, new String[] { "A", "B", "A+B" }));
		}
	}

	public class SleepSetting extends AbstractManualSelect {
		public SleepSetting(ModelConfigurator modelConfigurator) {
			super(OptionType.SleepSetting, R.string.SleepSetting, "SLP", false,
					new Selection(modelConfigurator.getSleepTransformer(),
							"OFF", "120", "90", "60", "30"));
		}
	}

	public class VideoSelect extends AbstractManualSelect {
		public VideoSelect(ModelConfigurator configurator) {
			super(OptionType.VideoSelect, R.string.VideoSelect, "SV", false,
					configurator.getVideoSelection());
		}
	}

	public class VideoMode extends AbstractManualSelect {
		public VideoMode() {
			super(OptionType.VideoMode, R.string.VideoMode, "VSASP", false,
					new Selection(new String[] { "NRM", "FUL" }, new String[] {
							"Normal", "Full" }));
		}
	}

	// nur 4311, nicht 3310 !
	public class HDMIMonitor extends AbstractManualSelect {
		public HDMIMonitor() {
			super(OptionType.HDMIMonitor, R.string.HDMIMonitor, "VSMONI",
					false, new Selection(new String[] { "AUTO", "1", "2" }));
		}
	}

	private final static String[] VIDEO_MODES = { "AUTO", "48P", "10I", "72P",
			"10P", "10P24" };

	private final static String[] VIDEO_MODES_DISPLAY = { "AUTO", "480p",
			"1080i", "720p", "1080p", "1080p24" };

	public class VideoResolution extends AbstractManualSelect {
		public VideoResolution() {
			super(OptionType.VideoResolution, R.string.VideoResolution, "VSSC",
					false, new Selection(VIDEO_MODES, VIDEO_MODES_DISPLAY));
		}
	}

	public class HDMIVideoResolution extends AbstractManualSelect {
		public HDMIVideoResolution() {
			super(OptionType.HDMIVideoResolution, R.string.HDMIVideoResolution,
					"VSSCH", false, new Selection(VIDEO_MODES,
							VIDEO_MODES_DISPLAY));
		}
	}

	public class HDMIAudioOutputMode extends AbstractManualSelect {
		public HDMIAudioOutputMode() {
			super(OptionType.HDMIAudioOutputMode, R.string.HDMIAudioOutputMode,
					"VSAUDIO ", false, new Selection("AMP", "TV"));
		}
	}

	public class InputSelect extends AbstractSelect {
		public InputSelect(ModelConfigurator configurator) {
			super(R.string.Input, "SI", true, configurator.getInputSelection());
		}

		public boolean isSource() {
			return "SOURCE".equals(getSelected());
		}

		public boolean isNetworked() {
			final String s = getSelected();
			for (String c : NETWORK_SOURCES) {
				if (s.contains(c)) {
					return true;
				}
			}
			return false;
		}
	}

	public class SurroundMode extends AbstractSelect {
		public SurroundMode(ModelConfigurator configurator) {
			super(R.string.SurroundMode, "MS", false, configurator
					.getSurroundSelection());
		}
	}

	public abstract class AbstractSwitch implements IAVRState {

		public AbstractSwitch(int id, String prefix, String on, String off,
				boolean encoded) {
			this.id = id;
			this.prefix = prefix;
			this.on = on;
			this.off = off;
			this.encoded = encoded;
		}

		public String getCommandPrefix() {
			return prefix;
		}

		public String getReceivePrefix() {
			return getCommandPrefix();
		}

		public boolean isDefined() {
			return state != null;
		}

		public boolean update(InData v) {
			final String old = state;
			state = v.toString();
			return !state.equals(old);
		}

		public boolean isOn() {
			return on.equals(state);
		}

		public void switchState() {
			setState(!on.equals(state));
		}

		public void setState(boolean newState) {
			final String send = newState ? on : off;
			getSender().sendCommand(zone, this, send);
			getSender().query(zone, this);
		}

		public int getDisplayId() {
			return id;
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return encoded;
		}

		public void reset() {
			state = null;
		}

		public boolean isAutoUpdate() {
			return true;
		}

		private final boolean encoded;

		private final int id;
		private String state;
		private final String prefix;
		private final String on;
		private final String off;

	}

	public abstract class FlagSwitch extends AbstractSwitch {
		public FlagSwitch(EnableManager enableManager, StatusFlag statusFlag,
				int id, String prefix, String on, String off, boolean encoded) {
			super(id, prefix, on, off, encoded);
			this.enableManager = enableManager;
			this.statusFlag = statusFlag;
		}

		@Override
		public boolean update(InData v) {
			final boolean ret = super.update(v);
			enableManager.setStatus(statusFlag, isOn());
			return ret;
		}

		private final StatusFlag statusFlag;
		private final EnableManager enableManager;
	}

	public final class ZoneMode extends FlagSwitch {

		public ZoneMode(EnableManager enableManager, StatusFlag statusFlag) {
			super(enableManager, statusFlag, R.string.Zone, "ZM", "ON", "OFF",
					true);
		}
	}

	public final class PowerState extends FlagSwitch {
		public PowerState(EnableManager enableManager) {
			super(enableManager, StatusFlag.Power, R.string.Power, "PW", "ON",
					"STANDBY", false);
		}
	}

	public final class MuteState extends AbstractSwitch {
		public MuteState() {
			super(R.string.Mute, "MU", "ON", "OFF", false);
		}
	}

	public final class Volume implements IAVRState {
		public Volume(ModelConfigurator config) {
			adjust = config.getModel().needVolumeAdjust() ? 10 : 0;
		}

		public String getCommandPrefix() {
			return "MV";
		}

		public String getReceivePrefix() {
			return getCommandPrefix();
		}

		public boolean isDefined() {
			return vol != -1;
		}

		public boolean update(InData v) {
			if (v.isNumber()) {
				final int old = vol;
				vol = v.asNumber();
				int length = v.toString().length();
				if (length < 3) {
					vol *= 10;
				}
				// Absolut ist der Wert "1" zu niedig,
				// 0=99,0.5=995,1=00,1.5=005...
				vol = (vol + adjust) % 1000;
				return old != vol;
			}
			return false;
		}

		public void up() {
			if (getSender().isQueueEmpty()) {
				getSender().sendCommand(zone, this, "UP");
			}
		}

		public void down() {
			if (getSender().isQueueEmpty()) {
				getSender().sendCommand(zone, this, "DOWN");
			}
		}

		public String getPrintableVolume(VolumeDisplay display, boolean small) {
			if (vol == -1) {
				return "--";
			}
			if (display == VolumeDisplay.Relative) {
				// abs -> rel
				final int db = (800+adjust - vol) * -1;
				// silent
				if (db == -800-adjust) {
					return "---";
				}
				return (db / 10) + "." + Math.abs(db % 10)
						+ (small ? "" : " dB");
			} else {
				return (vol / 10) + "." + Math.abs(vol % 10);
			}
		}

		public int getDisplayId() {
			return R.string.Volume;
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return true;
		}

		public void reset() {
			vol = -1;
		}

		public boolean isAutoUpdate() {
			return true;
		}

		// absoluter Wert * 10 (0-995)
		private int vol = -1;
		private final int adjust;
	}

	public enum LevelGroup {
		Tone("Tone control"), Effect("Effect control"), Channel(
				"Channel control");

		private LevelGroup(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

		private final String text;
	}

	public enum LevelType {
		BAL(LevelGroup.Channel, BalanceLevel.class, "BAL", "Balance", 44, 56,
				dB, 50), BAS(LevelGroup.Tone, BassLevel.class, "BAS", "Bass",
				44, 56, dB, 50), TRE(LevelGroup.Tone, TrebleLevel.class, "TRE",
				"Treble", 44, 56, dB, 50), BAS_EXT(LevelGroup.Tone,
				BassLevel.class, "BAS", "Bass", 40, 60, dB, 50), TRE_EXT(
				LevelGroup.Tone, TrebleLevel.class, "TRE", "Treble", 40, 60,
				dB, 50), LFE(LevelGroup.Effect, LFELevel.class, "LFE",
				"Low Frequency Effect", 0, 10, dB, 0), EFF(LevelGroup.Effect,
				EFFLevel.class, "EFF", "Effect", 1, 15, dB, 10), DEL(
				LevelGroup.Effect, DELLevel.class, "DEL", "Delay", 0, 200,
				"ms", 0), FL(LevelGroup.Channel, ChannelVolume.class, "FL",
				"Front left", 38, 62, dB, 50), FR(LevelGroup.Channel,
				ChannelVolume.class, "FR", "Front right", 38, 62, dB, 50), C(
				LevelGroup.Channel, ChannelVolume.class, "C", "Center", 38, 62,
				dB, 50), SW(LevelGroup.Channel, ChannelVolume.class, "SW",
				"Subwoofer", 38, 62, dB, 50), SL(LevelGroup.Channel,
				ChannelVolume.class, "SL", "Surround left", 38, 62, dB, 50), SR(
				LevelGroup.Channel, ChannelVolume.class, "SR",
				"Surround right", 38, 62, dB, 50), SBL(LevelGroup.Channel,
				ChannelVolume.class, "SBL", "Surround back left", 38, 62, dB,
				50), SB(LevelGroup.Channel, ChannelVolume.class, "SB",
				"Surround back", 38, 62, dB, 50), SBR(LevelGroup.Channel,
				ChannelVolume.class, "SBR", "Surround back right", 38, 62, dB,
				50), FHL(LevelGroup.Channel, ChannelVolume.class, "FHL",
				"Front height left", 0, 99, dB, 50), FHR(LevelGroup.Channel,
				ChannelVolume.class, "FHR", "Front height right", 0, 99, dB, 50), FWL(
				LevelGroup.Channel, ChannelVolume.class, "FWL",
				"Front wide left", 0, 99, dB, 50), FWR(LevelGroup.Channel,
				ChannelVolume.class, "FWR", "Front wide right", 0, 99, dB, 50);

		private LevelType(LevelGroup group,
				Class<? extends AbstractLevel> levelClass, String key,
				String text, int min, int max, String unit, int zero) {
			this.group = group;
			this.levelClass = levelClass;
			this.key = key;
			this.text = text;
			this.min = min;
			this.max = max;
			this.unit = unit;
			this.zero = zero;
		}

		public int getMax() {
			return max;
		}

		public int getMin() {
			return min;
		}

		public String getText() {
			return text;
		}

		public String getKey() {
			return key;
		}

		public Class<? extends AbstractLevel> getLevelClass() {
			return levelClass;
		}

		public LevelGroup getGroup() {
			return group;
		}

		public String convertToDisplay(int value, boolean absolute) {
			if (value < min || value > max) {
				return "undefined";
			}

			if (absolute) {
				return "" + value + (dB.equals(unit) ? "" : unit);
			}
			if (this == LFE) {
				value = value * -1;
			}
			return (value - zero) + unit;
		}

		public String getId() {
			return super.toString();
		}

		private final LevelGroup group;
		private final Class<? extends AbstractLevel> levelClass;
		private final String key;
		private final String text;
		private final int min;
		private final int max;
		private final String unit;
		private final int zero;

	}

	public final class ChannelVolume extends AbstractLevel {
		public ChannelVolume(ISender sender) {
			super("CV", sender, null);
		}
	}

	public final class EFFLevel extends AbstractLevel {
		public EFFLevel(ISender sender) {
			super("PSEFF", sender, LevelType.EFF);
		}

		public int getValue() {
			return getValue(getSingleType());
		}
	}

	public final class DELLevel extends AbstractLevel {
		public DELLevel(ISender sender, ModelConfigurator cfg) {
			super(cfg.getModel().getDelayCommand(), sender, LevelType.DEL);
		}

		public int getValue() {
			return getValue(getSingleType());
		}
	}

	public final class LFELevel extends AbstractLevel {
		public LFELevel(ISender sender) {
			super("PSLFE", sender, LevelType.LFE);
		}

		public int getValue() {
			return getValue(getSingleType());
		}
	}

	public final class BassLevel extends AbstractLevel {
		public BassLevel(ISender sender, ModelConfigurator cfg) {
			super("PSBAS", sender, cfg.getModel().getSupportedLevels()
					.contains(LevelType.BAS_EXT) ? LevelType.BAS_EXT
					: LevelType.BAS);
		}

		public int getValue() {
			return getValue(getSingleType());
		}
	}

	public final class BalanceLevel extends AbstractLevel {
		public BalanceLevel(ISender sender) {
			super("PSBAL", sender, LevelType.BAL);
		}

		public int getValue() {
			return getValue(getSingleType());
		}
	}

	public final class TrebleLevel extends AbstractLevel {
		public TrebleLevel(ISender sender, ModelConfigurator cfg) {
			super("PSTRE", sender, cfg.getModel().getSupportedLevels()
					.contains(LevelType.TRE_EXT) ? LevelType.TRE_EXT
					: LevelType.TRE);
		}

		public int getValue() {
			return getValue(getSingleType());
		}
	}

	public abstract class AbstractLevel implements IAVRState {

		public AbstractLevel(String cmd, ISender sender, LevelType singleType) {
			this.cmd = cmd;
			this.sender = sender;
			this.singleType = singleType;
		}

		public String getCommandPrefix() {
			return cmd;
		}

		public String getReceivePrefix() {
			return getCommandPrefix();
		}

		public int getDisplayId() {
			return R.string.Volume;
		}

		public boolean isAutoUpdate() {
			return false;
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return false;
		}

		public boolean isDefined() {
			return !values.isEmpty();
		}

		public void reset() {
			values.clear();
		}

		public boolean setLevel(LevelType ct, int value) {
			Logger.info("setLevel [" + getValue(ct) + "/" + value + "]");
			if (getValue(ct) != value) {
				sender.send(getCommandPrefix()
						+ (singleType != null ? "" : ct.getKey()) + " "
						+ fillZeros(value, ct.getMax()));
				return true;
			}
			return false;
		}

		private String fillZeros(int value, int max) {
			// Mit Nullen auffüllen, sonst nicht akzeptiert
			String ret = "" + value;
			if (max > 9 && ret.length() == 1) {
				ret = "0" + ret;
			}
			if (max > 99 && ret.length() == 2) {
				ret = "0" + ret;
			}
			return ret;
		}

		public void update(LevelType ct) {
			// Update auf jeden Fall
			values.remove(ct);
			sender.send(getCommandPrefix() + (singleType != null ? " " : "")
					+ "?");
		}

		public boolean update(InData v) {
			final String s = v.toString().trim();
			if (s.equals("ON")) {
				// ignoerieren
				return false;
			}

			if (singleType != null) {
				final int newNumber = v.asNumber();
				final Integer oldNumber = values.put(singleType, newNumber);
				Logger.info("update " + this + " [" + oldNumber + "]["
						+ newNumber + "]");
				return oldNumber == null || oldNumber.intValue() != newNumber;
			} else {
				String[] p = s.split(" ");
				if (p.length == 2) {
					String key = p[0];
					try {
						LevelType ct = LevelType.valueOf(key);
						Logger.info("CV [" + key + "]=[" + p[1] + "]");
						// Bei Angaben wie 445 = 44,5 Nachkommastellen
						// ignorieren
						if (ct.getMax() < 100 && p[1].length() == 3) {
							p[1] = p[1].substring(0, 2);
						}
						int newNumber = Integer.parseInt(p[1]);

						Integer oldNumber = values.put(ct, newNumber);
						return oldNumber == null
								|| oldNumber.intValue() != newNumber;
					} catch (Exception x) {
						Logger.debug("invalid CV[" + s + "]" + x);
					}
					return false;
				} else {
					Logger.debug("unknown CV[" + s + "]");
					return false;
				}
			}
		}

		public Map<LevelType, Integer> getValues() {
			return values;
		}

		public boolean containsValue(LevelType ct) {
			return values.containsKey(ct);
		}

		public int getValue(LevelType ct) {
			final Integer v = values.get(ct);
			if (v != null) {
				return v.intValue();
			}
			return ct.getMin();
		}

		public LevelType getSingleType() {
			return singleType;
		}

		@Override
		public String toString() {
			return "Level " + singleType + " (" + cmd + ")";
		}

		private final LevelType singleType;
		private final ISender sender;
		private final String cmd;
		private final Map<LevelType, Integer> values = new ConcurrentHashMap<LevelType, Integer>();
	}

	public ZoneState(ISender sender, Zone zone, EnableManager enableManager,
			IGUIExecutor guiExecutor, DisplayManager displayManager,
			ModelConfigurator modelConfigurator) {
		this.sender = sender;
		this.zone = zone;
		this.guiExecutor = guiExecutor;

		if (zone == Zone.Main) {
			addState(new PowerState(enableManager));
			addState(new SurroundMode(modelConfigurator));

			// Manual Settings
			addState(new VideoSelect(modelConfigurator));
			addState(new VideoMode());
			addState(new HDMIMonitor());
			addState(new VideoResolution());
			addState(new HDMIVideoResolution());
			addState(new HDMIAudioOutputMode());
			addState(new SleepSetting(modelConfigurator));
			addState(new AudioMode());
			addState(new SourceDirect());
			addState(new DynamicBassBoost());
			addState(new CinemaEQMode());
			addState(new MultEQMode(modelConfigurator));

			DynamicEQOnOff dynamicEQOnOff = modelConfigurator.getModel()
					.getDynamicEQOnOff();
			if (dynamicEQOnOff != DynamicEQOnOff.UNAVAILABLE) {
				addState(new DynamicEQ(dynamicEQOnOff));
			}
			DynamicEQMode dynamicEQMode = modelConfigurator.getModel()
					.getDynamicEQMode();
			if (dynamicEQMode != DynamicEQMode.UNAVAILABLE) {
				addState(new DynamicEQSetting(dynamicEQMode));
			}

			addState(new ReferenceLevelOffsetSetting());

			addState(new FrontSpeakerSetting());
			addState(new SurroundBackMode());
			addState(new ToneControl());

			addState(new AudioRestorer());
			addState(new NightMode());
			addState(new DCOSetting());
			addState(new DRCSetting());

			addState(new ChannelVolume(sender));
			addState(new LFELevel(sender));
			addState(new DELLevel(sender, modelConfigurator));
			addState(new EFFLevel(sender));
			addState(new BassLevel(sender, modelConfigurator));
			addState(new BalanceLevel(sender));
			addState(new TrebleLevel(sender, modelConfigurator));

			// Display Types
			addNetDisplay(modelConfigurator, displayManager,
					DisplayType.NETWORK);
			addNetDisplay(modelConfigurator, displayManager, DisplayType.IPOD);

			if (modelConfigurator.getModel().hasMultiTunerMode()) {
				addTuner(TunerDisplay.createFMDAB(modelConfigurator, sender),
						DisplayManager.DisplayType.TUNER, sender,
						displayManager);
			} else {
				addTuner(TunerDisplay.createFM(modelConfigurator, sender),
						DisplayManager.DisplayType.TUNER, sender,
						displayManager);
				addTuner(TunerDisplay.createDAB(modelConfigurator, sender),
						DisplayManager.DisplayType.DAB, sender, displayManager);
			}

			addBDDisplay(modelConfigurator, displayManager);

			addTuner(TunerDisplay.createHD(modelConfigurator, sender),
					DisplayManager.DisplayType.HD, sender, displayManager);

			addTuner(TunerDisplay.createXM(modelConfigurator, sender),
					DisplayManager.DisplayType.XM, sender, displayManager);

			addTuner(TunerDisplay.createSirius(modelConfigurator, sender),
					DisplayManager.DisplayType.SIRIUS, sender, displayManager);

		}
		addState(new Volume(modelConfigurator));
		addState(new ZoneMode(enableManager, zone.getFlag()));
		addState(new MuteState());
		addState(new InputSelect(modelConfigurator));

		prefixResolver = new PrefixResolver(classMap.values());
	}

	private void addBDDisplay(ModelConfigurator modelConfigurator,
			DisplayManager displayManager) {
		final BDDisplay bdDisplay = new BDDisplay(this.getSender());
		displayManager.setDisplay(DisplayType.BD, bdDisplay);
		addStateObject(bdDisplay.getState());

	}

	private void addNetDisplay(ModelConfigurator modelConfigurator,
			DisplayManager displayManager, DisplayType type) {
		final NetDisplay netDisplay = new NetDisplay(this.getSender(),
				modelConfigurator, type);
		displayManager.setDisplay(type, netDisplay);
		addStateObject(netDisplay);
	}

	private void addTuner(TunerDisplay tunerState,
			DisplayManager.DisplayType type, ISender sender,
			DisplayManager displayManager) {
		for (IAVRState s : tunerState.getStates()) {
			addStateObject(s);
		}
		displayManager.setDisplay(type, tunerState);
	}

	/**
	 * Stati zurücksetzen und abfragen
	 * 
	 * @param partialStateInit
	 */
	public void initState(IStateFilter stateFilter) {
		for (IAVRState s : classMap.values()) {
			if (stateFilter.accept(s.getClass())) {
				s.reset();
				if (s.isAutoUpdate()) {
					getSender().query(zone, s);
					fireListener(s);
				}
			}
		}
	}

	/**
	 * Checken, ob alle von initState() angefragten States eine Antwort bekommen
	 * haben
	 * 
	 * @param stateFilter
	 */
	public void checkDefined(IStateFilter stateFilter) {
		for (IAVRState s : classMap.values()) {
			if (stateFilter.accept(s.getClass())) {
				if (s.isAutoUpdate() && !s.isDefined()) {
					Logger.error("requery [" + s.getCommandPrefix() + "]", null);
					getSender().query(zone, s);
				}
			}
		}
	}

	public void updateState(Class<? extends IAVRState> stateClass) {
		getSender().query(zone, getState(stateClass));
	}

	public void resetState(IStateFilter stateFilter) {
		for (IAVRState s : classMap.values()) {
			if (stateFilter.accept(s.getClass())) {
				s.reset();
				fireListener(s);
			}
		}
	}

	public void notifyListener() {
		for (IAVRState s : classMap.values()) {
			fireListener(s);
		}
	}

	public <T extends IAVRState> void notifyListener(Class<T> cl) {
		fireListener(getState(cl));
	}

	public void clearStateAndListener() {
		for (IAVRState s : getAllStates()) {
			s.reset();
		}
		listener.clear();
	}

	private void addState(IAVRState s) {
		classMap.addClass(s);
	}

	private void addStateObject(IAVRState s) {
		classMap.addObject(s);
	}

	public List<IAVRState> getManualStates() {
		LinkedList<IAVRState> ret = new LinkedList<IAVRState>();
		for (IAVRState s : getAllStates()) {
			if (!s.isAutoUpdate()) {
				ret.add(s);
			}
		}
		return ret;
	}

	public <T extends IAVRState> void setAllBaseListener(IStateListener<T> l,
			Class<? extends IAVRState> cl) {
		for (IAVRState c : classMap.getAll(cl)) {
			setListener(l, c.getClass(), true);
		}
	}

	public <T extends IAVRState> void setListener(IStateListener<T> l,
			Class<? extends IAVRState> cl) {
		setListener(l, cl, true);
	}

	public void removeListener(Class<? extends IAVRState> cl) {
		setListener(null, cl, false);
	}

	@SuppressWarnings("unchecked")
	public <T extends IAVRState> void setListener(IStateListener<T> l,
			Class<? extends IAVRState> cl, boolean initialValue) {
		T t = (T) classMap.get(cl);
		if (l != null) {
			listener.put(t, l);
			if (initialValue) {
				l.changedState(t);
			}
			if (!t.isDefined()) {
				getSender().query(zone, t);
			}

		} else {
			listener.remove(t);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends IAVRState> T getState(Class<T> cl) {
		final IAVRState s = classMap.get(cl);
		if (s != null) {
			return (T) s;
		}
		throw new IllegalArgumentException(cl.getName());
	}

	public Collection<IAVRState> getAllStates() {
		return classMap.values();
	}

	public void update(InData data) {
		IAVRState e = prefixResolver.find(data.toString());
		if (e != null) {
			data.setOffset(e.getReceivePrefix().length());
			boolean stateChange = e.update(data);
			Logger.info(zone + " ->[" + e.getClass().getSimpleName()
					+ "] state:" + (stateChange ? "change" : "no change"));
			if (stateChange) {
				fireListener(e);
			}
		} else {
			Logger.info(zone + "?(" + data.toDebugString() + ")");
		}

	}

	private void fireListener(final IAVRState state) {
		@SuppressWarnings("rawtypes")
		final IStateListener l = listener.get(state);
		if (l != null) {
			guiExecutor.execute(new Runnable() {

				@SuppressWarnings("unchecked")
				public void run() {
					l.changedState(state);
				}
			});

		}
	}

	public Zone getZone() {
		return zone;
	}

	public ISender getSender() {
		return sender;
	}

	private final ISender sender;
	private final Zone zone;
	private final ClassMap classMap = new ClassMap();
	@SuppressWarnings({ "rawtypes" })
	private final Map<IAVRState, IStateListener> listener = new HashMap<IAVRState, IStateListener>();
	private final PrefixResolver prefixResolver;
	private final IGUIExecutor guiExecutor;

	private static final String N_D = "n/d";
	private static final String dB = "db";

	private final static String[] NETWORK_SOURCES = { "NAPSTER", "RHAPSODY",
			"PANDORA", "LASTFM", "SPOTIFY", "NET", "SERVER", "IRADIO", "USB",
			"FAVORITES", "IRP", "FVP", "TOP", "MPLAY" };
}
