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

import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.os.Handler;
import android.view.KeyEvent;
import de.pskiwi.avrremote.IScreenMenu;
import de.pskiwi.avrremote.MenuBuilder;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.core.IAVRState;
import de.pskiwi.avrremote.core.ISender;
import de.pskiwi.avrremote.core.InData;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.log.Logger;

/** CD/BD-Status Quelle : S5BD-Cara */
public final class BDDisplay implements IDisplay {

	private final static class BDDisplayStatus implements IDisplayStatus {

		public BDDisplayStatus(String info) {
			this.rawInfo = info;

			if (info.length() == "xx2000MBTTTTTTTAhhmmss".length()) {
				Logger.debug("update BDDisplay [" + info + "]");
				// String tray = info.substring(0, 2);
				final String bdMode = getBDMode(info.charAt(6));
				final String playMode = getPlayMode(info.charAt(7));

				final String track = info.substring(8, 15);
				final String timeMode = getTimeMode(info.charAt(15));

				final String hour = info.substring(16, 18);
				final String minute = info.substring(18, 20);
				final String second = info.substring(20);

				this.info = new DisplayLine[] {
						new DisplayLine(bdMode),
						new DisplayLine(playMode),
						new DisplayLine("Track : " + track),
						new DisplayLine("Time : " + hour + ":" + minute + ":"
								+ second + " " + timeMode) };

			} else {
				this.info = new DisplayLine[] { new DisplayLine(info) };
				Logger.debug("update BDDisplay not matching [" + info + "]");
			}

		}

		private String getTimeMode(char timeModeChar) {
			switch (timeModeChar) {
			case '1':
				return "Elapsed";
			case '2':
				return "Remain";
			case '3':
				return "Total";
			case '4':
				return "CD Remaining";
			case '5':
				return "Chapter Elapsed";
			case '6':
				return "Chapter Remain";
			case '7':
				return "Title Elapsed";
			case '8':
				return "Title Remain";
			case '9':
				return "Track";
			case ':':
				return "Track Remaining";
			case ';':
				return "Group Elapsed";
			case '<':
				return "Group Remain";
			}
			Logger.info("unknown TimeMode [" + timeModeChar + "/"
					+ (int) timeModeChar + "]");
			return "";
		}

		private String getPlayMode(final char playmodeChar) {
			switch (playmodeChar) {
			case '1':
				return "Normal Play";
			case '2':
				return "Program";
			case '3':
				return "Random Play";
			case '4':
				return "Track Repeat";
			case '5':
				return "Random Play & CD Repeat";
			case '6':
				return "CD Repeat";
			}
			Logger.info("unknown PlayMode [" + playmodeChar + "/"
					+ (int) playmodeChar + "]");
			return "";
		}

		private String getBDMode(char bdmodeChar) {
			switch (bdmodeChar) {
			case 'A':
				return "No Disc";
			case 'B':
				return "Stop";
			case 'C':
				return "Play";
			case 'D':
				return "Pause";
			case 'E':
				return "Scan Play";
			case 'F':
				return "Slow Search Play";
			case 'G':
				return "Setup";
			case 'H':
				return "Playback Control";
			case 'I':
				return "DVD Resume Stop";
			case 'J':
				return "DVD Menu";
			case '0':
				return "Stand-by";
			case '1':
				return "Check CD";
			case '3':
				return "Tray Open";
			case '4':
				return "Tray Closing";
			}
			return "";
		}

		public int getCursorLine() {
			return -1;
		}

		public int getDisplayCount() {
			return info.length;
		}

		public DisplayLine getDisplayLine(int row) {
			return info[row];
		}

		public String getInfoLine() {
			return "";
		}

		public CharSequence getPlayInfo() {
			return "";
		}

		public String getTitle() {
			return "";
		}

		public boolean isCursorDefined() {
			return false;
		}

		public String toDebugString() {
			return rawInfo;
		}

		private final DisplayLine info[];
		private final String rawInfo;
	}

	private final class BDState implements IAVRState {

		public String getCommandPrefix() {
			return BD + "STATUS";
		}

		public String getReceivePrefix() {
			return getCommandPrefix();
		}

		public int getDisplayId() {
			return R.string.BDStatus;
		}

		public boolean isAutoUpdate() {
			return listener != IDisplayListener.NULL_LISTENER;
		}

		public boolean isCommandSecondaryZoneEncoded() {
			return false;
		}

		public boolean isDefined() {
			return info.length() > 0;
		}

		public void reset() {
			info = "";
		}

		public boolean update(InData v) {
			String old = info;
			info = v.toString().trim();
			if (!old.equals(info)) {
				displayStatus = new BDDisplayStatus(info);
				fireListener();
				return true;
			} else {
				return false;
			}
		}

		private String info = "";
	}

	public BDDisplay(ISender sender) {
		this.sender = sender;
	}

	public IAVRState getState() {
		return state;
	}

	public void clearListener() {
		listener = IDisplayListener.NULL_LISTENER;
	}

	public IScreenMenu createMenu(final Activity activity, Zone zone) {
		return new IScreenMenu() {

			public void showMenu(boolean extended) {
				final MenuBuilder menuBuilder = new MenuBuilder(activity,
						"Options");

				menuBuilder.add("Repeat", new Runnable() {
					public void run() {
						sender.send("BDREPEAT");
						queryStatus();
					}
				});
				menuBuilder.add("Random", new Runnable() {
					public void run() {
						sender.send("BDRANDOM");
						queryStatus();
					}
				});

				menuBuilder.showMenu();

			}

			public void showExtraMenu() {
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

	public void extendView(Activity activity, IStatusComponentHandler handler) {
	}

	public IDisplayStatus getDisplayStatus() {
		return displayStatus;
	}

	public int getLayoutResource() {
		return de.pskiwi.avrremote.R.layout.bd_screen;
	}

	public boolean isDummy() {
		return false;
	}

	public void moveTo(int position) {
	}

	public void pageDown() {
		sender.send(BD + "MANUAL SEARCH -");
	}

	public void pageUp() {
		sender.send(BD + "MANUAL SEARCH +");
	}

	public void pause() {
		sender.send(BD + "PAUSE");
	}

	public void play() {
		sender.send(BD + "PLAY");
	}

	public void reset() {
		queryStatus();
	}

	public void returnLevel() {
		sender.send(BD + "RETURN");
	}

	public void home() {
		sender.send("BDTOP MENU");
	}

	private void queryStatus() {
		handler.postDelayed(new Runnable() {
			public void run() {
				sender.send(BD + "STATUS?");
			}
		}, 1000);
	}

	public void setActiveZoneState(ZoneState zone) {
	}

	public void setListener(IDisplayListener listener) {
		this.listener = listener;
		fireListener();
	}

	public void skipMinus() {
		sender.send(BD + "SKIP -");
		queryStatus();
	}

	public void skipPlus() {
		sender.send(BD + "SKIP +");
		queryStatus();
	}

	public void stop() {
		sender.send(BD + "STOP");
		queryStatus();
	}

	public boolean useTunerControls() {
		return false;
	}

	public Set<Operations> getSupportedOperations() {
		final Set<Operations> ret = new HashSet<Operations>();
		for (Operations op : Operations.values()) {
			ret.add(op);
		}
		ret.remove(Operations.Search);
		return ret;
	}

	private void fireListener() {
		if (listener != IDisplayListener.NULL_LISTENER) {
			Logger.info("BDDisplay.inform display listener[" + listener + "] ("
					+ displayStatus.toDebugString() + ")");
			listener.displayChanged(displayStatus);
		}
	}

	private IDisplayStatus displayStatus = IDisplayStatus.EMPTY_DISPLAY;
	private IDisplayListener listener = IDisplayListener.NULL_LISTENER;
	private final ISender sender;
	private final Handler handler = new Handler();
	private final BDState state = new BDState();
	private final static String BD = "BD";
}
