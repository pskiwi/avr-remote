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
package de.pskiwi.avrremote.core.display;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import de.pskiwi.avrremote.IScreenMenu;
import de.pskiwi.avrremote.MenuBuilder;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.core.GUIDisplayListener;
import de.pskiwi.avrremote.core.IAVRState;
import de.pskiwi.avrremote.core.ISender;
import de.pskiwi.avrremote.core.InData;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.MuteState;
import de.pskiwi.avrremote.core.display.HDInfoParser.HDInfo;
import de.pskiwi.avrremote.core.display.HDInfoParser.HDInfoKey;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class TunerDisplay implements IDisplay {

	public enum TunerDisplayMode {
		FMDABMulti("") {
			@Override
			public InData extractPrefix(InData in) {
				return new InData(in.toString().substring(2));
			}
		},
		FM("AN"), Sirius("ST"), HDRadio("HD"), DAB("DA"), XM("XM");

		private TunerDisplayMode(String prefix) {
			this.prefix = prefix;
		}

		public String getPrefix() {
			return prefix;
		}

		public InData extractPrefix(InData in) {
			return in;
		}

		private final String prefix;
	}

	private TunerDisplay(ModelConfigurator configurator, ISender sender,
			TunerDisplayMode tunerMode) {
		this.configurator = configurator;
		this.sender = sender;
		this.tunerMode = tunerMode;
		this.currentPrefix = tunerMode.getPrefix();
		// Kein HDState in diesen Modi
		if (tunerMode != TunerDisplayMode.FM
				&& tunerMode != TunerDisplayMode.FMDABMulti) {
			hdState = new HDState(tunerMode.getPrefix());
		} else {
			hdState = null;
		}
	}

	public final class TunerFrequency implements IAVRState {

		public String getCommandPrefix() {
			return "TF" + currentPrefix;
		}

		public String getReceivePrefix() {
			return "TF" + tunerMode.getPrefix();
		}

		public int getDisplayId() {
			return R.string.TunerFrequency;
		}

		public boolean isAutoUpdate() {
			return doUpdate();
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return false;
		}

		public boolean isDefined() {
			return frequency != null;
		}

		public void reset() {
			frequency = null;
		}

		public void up() {
			sender.send(getCommandPrefix() + "UP");
		}

		public void down() {
			sender.send(getCommandPrefix() + "DOWN");
		}

		void multiCastChannelSelect(int ch) {
			sender.send(getCommandPrefix() + "MC" + ch);
		}

		public boolean update(InData v) {
			v = tunerMode.extractPrefix(v);
			if (currentPrefix.equals(TunerDisplayMode.DAB.getPrefix())) {
				final String freqString = v.toString();
				if (!"CMP".equals(freqString)) {
					frequency = "DAB " + freqString;
				}
			} else {
				if (v.isNumber()) {
					frequency = convertFrequency(v.asNumber());
				} else {
					// IGNORE 'CMP'
				}
			}
			fireListener();
			return true;
		}

		public String getFrequency() {
			return frequency != null ? frequency : "";
		}

		private String convertFrequency(int freq) {
			if (tunerMode == TunerDisplayMode.Sirius) {
				return "" + frequency;
			}
			if (freq > 0) {
				final boolean fm = (freq < 50000);
				return (fm ? "FM" : "AM") + " : "
						+ String.format("%2.2f ", freq / 100f)
						+ (fm ? "MHz" : "kHz");
			}
			return "...";
		}

		private String frequency;

	}

	public final class TunerPreset implements IAVRState {

		public String getCommandPrefix() {
			return "TP" + currentPrefix;
		}

		public String getReceivePrefix() {
			return "TP" + tunerMode.getPrefix();
		}

		public int getDisplayId() {
			return R.string.TunerPreset;
		}

		public boolean isAutoUpdate() {
			return doUpdate();
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return false;
		}

		public boolean isDefined() {
			return preset != null;
		}

		public void reset() {
			preset = null;
		}

		public boolean update(InData v) {
			v = tunerMode.extractPrefix(v);
			preset = v.toString();
			fireListener();
			return true;
		}

		public void up() {
			sender.send(getCommandPrefix() + "UP");
			queryStatus();
		}

		public void down() {
			sender.send(getCommandPrefix() + "DOWN");
			queryStatus();
		}

		public void presetMemory(String preset) {
			sender.send(getCommandPrefix()
					+ configurator.getModel().getTunerMemory());
			preset(preset);
		}

		public void preset(String ch) {
			sender.send(getCommandPrefix() + ch);
		}

		public String getPreset() {
			return "Preset : " + (preset != null ? preset : "n/a");
		}

		private String preset;

	}

	public final class HDState implements IAVRState {

		public HDState(String prefix) {
			Logger.debug("create HDState[" + prefix + "] for ["
					+ Integer.toHexString(TunerDisplay.this.hashCode()) + "]"
					+ tunerMode);
			this.prefix = prefix;
		}

		public String getReceivePrefix() {
			return getCommandPrefix();
		}

		public String getCommandPrefix() {
			return prefix;
		}

		public int getDisplayId() {
			return R.string.HDStatus;
		}

		public boolean isAutoUpdate() {
			return doUpdate();
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return false;
		}

		public boolean isDefined() {
			return values.size() > 0;
		}

		public void reset() {
			values.clear();
		}

		public boolean update(InData v) {
			v = tunerMode.extractPrefix(v);
			final String res = v.toString().trim();
			final HDInfo info = HDInfoParser.parse(res);
			final String oldValue = values.get(info.key);
			final boolean update = oldValue == null
					|| !oldValue.equals(info.value);
			Logger.debug("Tuner.Display [" + prefix + "]->[" + res + "] -> ["
					+ info + "] update:" + update);
			if (update) {
				values.put(info.key, info.value);
				fireListener();
				return true;
			}

			return false;
		}

		public String getStation() {
			return "Station : " + getShortStation();
		}

		private String getShortStation() {
			return getString(HDInfoKey.STATION);
		}

		public String getArtist() {
			return "Artist : " + getShortArtist();
		}

		public String getSignal() {
			return "Signal : " + getShortSignal();
		}

		private String getShortArtist() {
			return getString(HDInfoKey.ARTIST);
		}

		private String getShortSignal() {
			return getString(HDInfoKey.SIGNAL);
		}

		public String getTitle() {
			return "Title : " + getShortTitle();
		}

		private String getShortTitle() {
			return getString(HDInfoKey.TITLE);
		}

		public String getGenre() {
			return "Genre : " + getString(HDInfoKey.GENRE);
		}

		// [SIG]=[LEV 6]
		public String getLevel() {
			return "Level :  " + getString(HDInfoKey.SIGNAL_LEV);
		}

		public String getDABLevel() {
			return "Level :  " + getString(HDInfoKey.SIGNAL);
		}

		// [HDMLT] CAST CH 0 1/3
		public String getMulicastChannel() {
			return "M-Ch :  " + getCurrentMulticast() + "/"
					+ getMaximumMulticast();
		}

		private int getMaximumMulticast() {
			return getNumber(HDInfoKey.MLTCASTMAX);
		}

		private int getCurrentMulticast() {
			return getNumber(HDInfoKey.MLTCASTCURR);
		}

		private int getNumber(HDInfoKey key) {
			final String str = getString(key);
			if (NUMBER_PATTERN.matcher(str).matches()) {
				try {
					return Integer.parseInt(str);
				} catch (NumberFormatException x) {
					Logger.error("could not parse [" + str + "]", x);
				}
			}
			return -1;
		}

		public String getProgramType() {
			return "Type : " + getString(HDInfoKey.PTY);
		}

		// Analog/Digital
		public String getMode() {
			return "Type : " + getShortAnalogDigitalMode();
		}

		public String getShortAnalogDigitalMode() {
			return getString(HDInfoKey.MODE);
		}

		public String getDABChannel() {
			return getString(HDInfoKey.CHANNEL);
		}

		public String getChannelName() {
			return "Channel : " + getString(HDInfoKey.CHANNEL_NAME);
		}

		public String getXMID() {
			return getString(HDInfoKey.XMID);
		}

		public String getPlayInfo() {
			return getShortStation() + " " + getShortArtist() + " "
					+ getShortTitle();
		}

		private String getString(HDInfoKey key) {
			String ret = values.get(key);
			if (ret != null) {
				return ret;
			}
			return "";
		}

		private final String prefix;
		private final Map<HDInfoKey, String> values = new ConcurrentHashMap<HDInfoKey, String>();

	}

	public final class TunerStatus implements IAVRState {

		public String getCommandPrefix() {
			return "TM" + currentPrefix;
		}

		public String getReceivePrefix() {
			return "TM" + tunerMode.getPrefix();
		}

		public int getDisplayId() {
			return R.string.TunerStatus;
		}

		public boolean isAutoUpdate() {
			return doUpdate();
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return false;
		}

		public boolean isDefined() {
			return status != null;
		}

		public void reset() {
			status = null;
		}

		public boolean update(InData v) {
			if (tunerMode == TunerDisplayMode.FMDABMulti) {
				String in = v.toString();
				// Aktuellen Modus "Analog" oder "DAB" bestimmen
				if ("ANFM".equals(in) || "ANAM".equals(in)
						|| TunerDisplayMode.DAB.getPrefix().equals(in)) {
					String oldPrefix = currentPrefix;
					currentPrefix = in.substring(0, 2);
					if (!currentPrefix.equals(oldPrefix)) {
						Logger.info("Tuner prefix changed to [" + currentPrefix
								+ "]");
						queryStatus();
						if (TunerDisplayMode.DAB.getPrefix().equals(
								currentPrefix)) {
							// beim Wechsel braucht der MCR603 etwas
							handler.postDelayed(new Runnable() {
								public void run() {
									queryStatus();
								}
							}, 5000);
						}
					}
				}
				v = tunerMode.extractPrefix(v);
			}
			status = v.toString();
			fireListener();
			return true;
		}

		public String getStatus() {
			return "Mode : " + (status != null ? status : "n/a");
		}

		void setAM() {
			sender.send("SITUNER");
			sender.send("TMANAM");
		}

		void setDAB() {
			sender.send("SITUNER");
			sender.send("TMDA");
		}

		public void setDABMode(String mode) {
			sender.send("TMDA" + mode);
		}

		void setFM() {
			sender.send("SITUNER");
			sender.send("TMANFM");
		}

		void setAuto() {
			setMode("AUTO");
		}

		void setHDAuto() {
			setMode("AUTOHD");
		}

		void setManual() {
			setMode("MANUAL");
		}

		private void setMode(String s) {
			sender.send(getCommandPrefix() + s);
		}

		private String status;

	}

	private class TunerDisplayStatus implements IDisplayStatus {
		public TunerDisplayStatus() {
			switch (tunerMode) {
			case Sirius:
				lines = new String[] { tunerPreset.getPreset(),
						tunerFrequency.getFrequency(), tunerState.getStatus(),
						hdState.getChannelName(), hdState.getArtist(),
						hdState.getTitle(), hdState.getSignal() };
				playInfo = tunerFrequency.getFrequency();
				title = "Sirius";
				break;
			case XM:
				lines = new String[] {
						tunerPreset.getPreset() + " " + tunerState.getStatus(),
						tunerFrequency.getFrequency() + " "
								+ hdState.getDABChannel(),
						hdState.getChannelName(), hdState.getXMID(),
						hdState.getArtist(), hdState.getTitle(),
						hdState.getSignal() };
				playInfo = hdState.getPlayInfo();
				title = "XM Tuner";
				break;
			case DAB:
				lines = new String[] {
						tunerPreset.getPreset() + " " + tunerState.getStatus(),
						tunerFrequency.getFrequency() + " "
								+ hdState.getDABChannel(),
						hdState.getStation(), hdState.getGenre(),
						hdState.getProgramType(), hdState.getDABLevel() };
				playInfo = hdState.getPlayInfo();
				title = "DAB Tuner " + hdState.getShortAnalogDigitalMode();
				break;
			case HDRadio:
				lines = new String[] {
						tunerPreset.getPreset() + " " + tunerState.getStatus(),
						tunerFrequency.getFrequency() + " "
								+ hdState.getMulicastChannel(),
						hdState.getStation(), hdState.getArtist(),
						hdState.getTitle(), hdState.getGenre(),
						hdState.getProgramType(), hdState.getLevel() };
				playInfo = hdState.getPlayInfo();
				title = "HD Tuner " + hdState.getShortAnalogDigitalMode();
				break;
			// FM/FMDABMUlti
			default:
				lines = new String[] { tunerPreset.getPreset(),
						tunerFrequency.getFrequency(), tunerState.getStatus() };
				playInfo = tunerFrequency.getFrequency();
				title = currentPrefix.equals(TunerDisplayMode.DAB.getPrefix()) ? "DAB Tuner"
						: "FM/AM Tuner";
				break;
			}
		}

		public int getCursorLine() {
			return -1;
		}

		public int getDisplayCount() {
			return lines.length;
		}

		public DisplayLine getDisplayLine(int row) {
			if (row < lines.length) {
				return new DisplayLine(lines[row]);
			}
			return new DisplayLine("n/d");
		}

		public CharSequence getPlayInfo() {
			return playInfo;
		}

		public String getTitle() {
			return title;
		}

		public String getInfoLine() {
			return "Preset";
		}

		public boolean isCursorDefined() {
			return false;
		}

		public String toDebugString() {
			StringBuilder ret = new StringBuilder();
			for (int i = 0; i < lines.length; i++) {
				ret.append(i + ":[" + lines[i] + "]");
			}
			return ret.toString();
		}

		private final String title;
		private final String[] lines;
		private final String playInfo;

	}

	public Collection<IAVRState> getStates() {
		final ArrayList<IAVRState> ret = new ArrayList<IAVRState>();
		ret.add(tunerFrequency);
		ret.add(tunerPreset);
		ret.add(tunerState);
		if (hdState != null) {
			ret.add(hdState);
		}
		return ret;
	}

	public IScreenMenu createMenu(final Activity activity, Zone zone) {
		return new IScreenMenu() {

			public void showMenu(boolean extended) {
				final MenuBuilder menuBuilder = new MenuBuilder(activity,
						"Options");

				menuBuilder.add("AM", new Runnable() {
					public void run() {
						tunerState.setAM();
					}
				});
				menuBuilder.add("FM", new Runnable() {
					public void run() {
						tunerState.setFM();
					}
				});

				if (configurator.supportsDAB()) {
					menuBuilder.add("DAB", new Runnable() {
						public void run() {
							tunerState.setDAB();
						}
					});
					if (configurator.getModel().supportsDABMode()) {
						menuBuilder.add("DAB Mode", new Runnable() {
							public void run() {
								final MenuBuilder menuBuilder = new MenuBuilder(
										activity, "DAB Mode");
								menuBuilder.add("Multiplex", new Runnable() {
									public void run() {
										tunerState.setDABMode("MULTIPLEX");
									}
								});
								menuBuilder.add("Alphanumeric", new Runnable() {
									public void run() {
										tunerState.setDABMode("ALPHA");
									}
								});
								menuBuilder.add("Program Type", new Runnable() {
									public void run() {
										tunerState.setDABMode("PTY");
									}
								});
								menuBuilder.add("Active Station",
										new Runnable() {
											public void run() {
												tunerState.setDABMode("AS");
											}
										});
								menuBuilder.add("Favorite Station",
										new Runnable() {
											public void run() {
												tunerState.setDABMode("FS");
											}
										});
							}
						});
					}
				}

				if (tunerMode == TunerDisplayMode.HDRadio) {
					menuBuilder.add("HD Auto Tuning", new Runnable() {
						public void run() {
							tunerState.setHDAuto();
						}
					});
				}

				menuBuilder.add("Auto Tuning", new Runnable() {
					public void run() {
						tunerState.setAuto();
					}
				});

				menuBuilder.add("Manual Tuning", new Runnable() {
					public void run() {
						tunerState.setManual();
					}

				});
				menuBuilder.showMenu();
			}

			public void showExtraMenu() {
				final MenuBuilder menuBuilder = new MenuBuilder(activity,
						"Options");

				menuBuilder.add("Preset", new Runnable() {
					public void run() {
						final MenuBuilder mb = new MenuBuilder(activity,
								"Presets");
						for (char letter = 'A'; letter <= 'G'; letter++) {
							addPresets(mb, "" + letter);
						}
						mb.showMenu();
					}

					private void addPresets(final MenuBuilder mb, String letter) {
						for (int i = 1; i < 8; i++) {
							final String preset = letter + i;
							mb.add("Set " + preset, new Runnable() {
								public void run() {
									tunerPreset.presetMemory(preset);
								}
							});
						}
					}
				});
				menuBuilder.showMenu();
			}

			public boolean handleKey(ZoneState zoneState, int keyCode,
					KeyEvent event) {
				return false;
			}

			public void doSearch() {
			}

			public void doClassicSearch() {
			}
		};
	}

	public IDisplayStatus getDisplayStatus() {
		return displayStatus;
	}

	public void moveTo(int position) {
	}

	public void pageDown() {
		tunerPreset.up();
	}

	public void pageUp() {
		tunerPreset.down();
	}

	public void pause() {
		if (zone != null) {
			Logger.info("tuner pause");
			final MuteState mute = zone.getState(MuteState.class);
			mute.switchState();
		}
	}

	public void reset() {
		sender.send("TMDA?");
		sender.send("TMAN?");
	}

	public void returnLevel() {
	}

	public void home() {
	}

	private final class GUIUpdateListener implements IDisplayListener {

		public GUIUpdateListener(IDisplayListener delegate) {
			this.delegate = delegate;
		}

		public void displayChanged(IDisplayStatus display) {
			delegate.displayChanged(display);
			updateUI(display);
		}

		public void displayInfo(String text) {
			delegate.displayInfo(text);
		}

		public void displayInfo(int resId) {
			delegate.displayInfo(resId);
		}

		@Override
		public String toString() {
			return "GUIUpdateListener [" + delegate + "]";
		}

		private final IDisplayListener delegate;
	}

	public void setListener(IDisplayListener listener) {
		Logger.debug("TunerDisplay.setListener:["
				+ Integer.toHexString(hashCode()) + "]" + tunerMode + ":"
				+ listener);
		this.listener = new GUIDisplayListener(new GUIUpdateListener(listener));
		fireListener();
		for (IAVRState s : getStates()) {
			sender.send(s.getCommandPrefix() + "?");
		}
	}

	public void clearListener() {
		Logger.debug(tunerMode + ".clearListener:["
				+ Integer.toHexString(hashCode()) + "]");
		this.listener = IDisplayListener.NULL_LISTENER;
		for (int i = 0; i < MAX_MCH_PRESET; i++) {
			hd_mch_buttons[i] = null;
		}
	}

	public void skipMinus() {
		tunerFrequency.down();
		if (configurator.getModel().isExtraUpdateNeeded()) {
			queryStatus();
		}
	}

	public void skipPlus() {
		tunerFrequency.up();
		if (configurator.getModel().isExtraUpdateNeeded()) {
			queryStatus();
		}
	}

	public void stop() {
	}

	public void play() {
	}

	private void fireListener() {
		if (listener != IDisplayListener.NULL_LISTENER) {
			displayStatus = new TunerDisplayStatus();
			Logger.debug("Tuner." + tunerMode + "["
					+ Integer.toHexString(hashCode())
					+ "] inform display listener[" + listener + "] ("
					+ displayStatus.toDebugString() + ")");
			listener.displayChanged(displayStatus);
		} else {
			Logger.debug("TunerDisplay.fireListener ["
					+ Integer.toHexString(hashCode()) + "]" + listener);
		}
	}

	public boolean isDummy() {
		return false;
	}

	public static TunerDisplay createFMDAB(ModelConfigurator configurator,
			ISender sender) {
		return new TunerDisplay(configurator, sender,
				TunerDisplayMode.FMDABMulti);
	}

	public static TunerDisplay createFM(ModelConfigurator configurator,
			ISender sender) {
		return new TunerDisplay(configurator, sender, TunerDisplayMode.FM);
	}

	public static TunerDisplay createDAB(ModelConfigurator configurator,
			ISender sender) {
		return new TunerDisplay(configurator, sender, TunerDisplayMode.DAB);
	}

	public static TunerDisplay createHD(ModelConfigurator configurator,
			ISender sender) {
		return new TunerDisplay(configurator, sender, TunerDisplayMode.HDRadio);
	}

	public static TunerDisplay createXM(ModelConfigurator configurator,
			ISender sender) {
		return new TunerDisplay(configurator, sender, TunerDisplayMode.XM);
	}

	public static TunerDisplay createSirius(ModelConfigurator configurator,
			ISender sender) {
		return new TunerDisplay(configurator, sender, TunerDisplayMode.Sirius);
	}

	public int getLayoutResource() {
		return de.pskiwi.avrremote.R.layout.hd_osd_screen;
	}

	public void extendView(Activity activity, IStatusComponentHandler handler) {
		if (tunerMode == TunerDisplayMode.HDRadio) {
			createHDButtons(activity, handler);
		} else {
			createAnalogButtons(activity, handler);
		}
	}

	private void createHDButtons(Activity activity,
			IStatusComponentHandler handler) {
		final android.widget.TableRow.LayoutParams lp = new TableRow.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

		final TableRow group = (TableRow) activity
				.findViewById(R.id.mcast_layout);

		final TextView text = new TextView(activity);
		text.setText("M-Ch");
		text.setGravity(Gravity.CENTER);
		group.addView(text, lp);

		for (int i = 0; i < MAX_MCH_PRESET; i++) {
			final int channel = i;
			final Button button = new Button(activity);

			button.setText("" + i);
			button.setGravity(Gravity.CENTER);
			button.setTypeface(Typeface.DEFAULT_BOLD);
			hd_mch_buttons[i] = button;
			hd_mch_buttons_enable[i] = new AtomicBoolean(true);
			handler.addView(button, hd_mch_buttons_enable[i]);
			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					tunerFrequency.multiCastChannelSelect(channel);
				}
			});
			final android.widget.TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

			group.addView(button, layoutParams);
		}
	}

	private void createAnalogButtons(final Activity activity,
			IStatusComponentHandler handler) {
		final String tunerPresetPrefix = configurator.getModel()
				.getTunerPresetPrefix();
		final TableRow group = (TableRow) activity
				.findViewById(R.id.mcast_layout);
		for (int i = 1; i <= 6; i++) {
			final TextView button = new Button(activity);
			handler.addView(button);
			final String preset = tunerPresetPrefix + i;
			button.setText(preset);
			button.setGravity(Gravity.CENTER);
			button.setTypeface(Typeface.DEFAULT_BOLD);
			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					tunerPreset.preset(preset);
				}
			});

			button.setOnLongClickListener(new OnLongClickListener() {

				public boolean onLongClick(View v) {
					tunerPreset.presetMemory(preset);
					Toast.makeText(activity, "Preset " + preset + " set",
							Toast.LENGTH_SHORT).show();
					return true;
				}
			});
			final android.widget.TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

			group.addView(button, layoutParams);
		}
	}

	private void updateUI(IDisplayStatus status) {
		if (tunerMode == TunerDisplayMode.HDRadio) {
			// Nur die Buttons aktivieren, die auf diesem Kanal einen Sinn
			// haben.
			int max = hdState.getMaximumMulticast();
			if (max == -1) {
				max = MAX_MCH_PRESET;
			} else {
				max++;
			}
			for (int i = 0; i < MAX_MCH_PRESET; i++) {
				final boolean enable = i < max;
				if (hd_mch_buttons[i] != null) {
					hd_mch_buttons[i].setEnabled(enable);
				}
				if (hd_mch_buttons_enable[i] != null) {
					hd_mch_buttons_enable[i].set(enable);
				}
			}
		}
	}

	public void setActiveZoneState(ZoneState zone) {
		this.zone = zone;
	}

	private boolean doUpdate() {
		// Falls kein Prefix definiert ist, macht eine Abfrage keinen Sinn
		return currentPrefix.length() > 0
				&& listener != IDisplayListener.NULL_LISTENER;
	}

	public Set<Operations> getSupportedOperations() {
		final Set<Operations> ret = new HashSet<Operations>();
		for (Operations op : Operations.values()) {
			ret.add(op);
		}
		ret.remove(Operations.Home);
		ret.remove(Operations.Return);
		ret.remove(Operations.Play);
		ret.remove(Operations.Stop);
		ret.remove(Operations.Search);
		return ret;
	}

	private void queryStatus() {
		sender.send("TF" + currentPrefix + "?");
		sender.send("TM" + currentPrefix + "?");
		sender.send("TP" + currentPrefix + "?");
	}

	@Override
	public String toString() {
		return "TunerDisplay [" + tunerMode + "/" + currentPrefix + "]";
	}

	private ZoneState zone;
	private IDisplayListener listener = IDisplayListener.NULL_LISTENER;
	private IDisplayStatus displayStatus = IDisplayStatus.EMPTY_DISPLAY;

	private final Button[] hd_mch_buttons = new Button[MAX_MCH_PRESET];
	private final AtomicBoolean[] hd_mch_buttons_enable = new AtomicBoolean[MAX_MCH_PRESET];

	private String currentPrefix;

	private final TunerDisplayMode tunerMode;

	private final ISender sender;
	private final TunerFrequency tunerFrequency = new TunerFrequency();
	private final TunerStatus tunerState = new TunerStatus();
	private final TunerPreset tunerPreset = new TunerPreset();
	private final HDState hdState;
	private final ModelConfigurator configurator;

	private final Handler handler = new Handler();
	private final static Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

	private final static int MAX_MCH_PRESET = 5;
}