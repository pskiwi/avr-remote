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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.Toast;
import de.pskiwi.avrremote.IScreenMenu;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.ScreenMenu;
import de.pskiwi.avrremote.core.DisplayMoveMode;
import de.pskiwi.avrremote.core.GUIDisplayListener;
import de.pskiwi.avrremote.core.IAVRState;
import de.pskiwi.avrremote.core.ISender;
import de.pskiwi.avrremote.core.InData;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.InputSelect;
import de.pskiwi.avrremote.core.display.DisplayManager.DisplayType;
import de.pskiwi.avrremote.http.AVRHTTPClient;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class NetDisplay implements IAVRState, IDisplay {

	public final static class TrackInfo {
		public TrackInfo(String track, String station) {
			this.station = station.replace('/', ' ').trim();
			final String[] split = splitArtistTrack(track);
			this.artist = split[0];
			this.track = split[1];
			this.album = "";
		}

		private final static String[] SPLITS = { " mit ", " - ", " with ",
				" / " };

		private static String[] splitArtistTrack(String text) {
			for (final String s : SPLITS) {
				if (text.contains(s)) {
					return text.split(s);
				}
			}
			return new String[] { text, "" };
		}

		public TrackInfo(String track, String artist, String album) {
			this.track = track;
			this.artist = artist.replace('/', ' ').trim();
			this.album = album;
			this.station = "";
		}

		public String getStation() {
			return station;
		}

		public String getAlbum() {
			return album;
		}

		public String getArtist() {
			return artist;
		}

		public String getTrack() {
			return track;
		}

		public String getTrackQuery() {
			return artist + " " + album + " " + track;
		}

		@Override
		public String toString() {
			return "TrackInfo [artist=" + artist + ", album=" + album
					+ ", track=" + track + ", station=" + station + "]";
		}

		public boolean isDefined() {
			return track.length() > 0;
		}

		private final String artist;
		private final String album;
		private final String track;
		private final String station;

	}

	private interface IScreenListener {
		void update(DisplayStatus state);
	}

	private void setMoveTimer(final IScreenListener listener) {
		handler.postDelayed(new Runnable() {
			public void run() {
				if (screenMoveListener == listener) {
					Logger.error("max move time exceeded " + listener, null);
					screenMoveListener = NULL_LISTENER;
				}
			}
		}, MAX_MOVE_TIME);
	}

	private final class HomeMover implements IScreenListener {

		public HomeMover() {
			screenMoveListener = this;
			setMoveTimer(this);
		}

		public void update(DisplayStatus state) {
			if (isHome(state)) {
				screenMoveListener = NULL_LISTENER;
			} else {
				move();
			}
		}

		private boolean isHome(DisplayStatus state) {
			for (String line : state.getDisplayLines()) {
				for (String home : HOME_STRINGS) {
					if (home.equalsIgnoreCase(line)) {
						return true;
					}
				}
			}
			return false;
		}

		public void move() {
			displayLeft();
		}
	}

	private final static class CommandBuilder {
		public void add(String cmd, String param) {
			formparams
					.add(new BasicNameValuePair("cmd" + nr, cmd + "/" + param));
			nr++;
		}

		public List<NameValuePair> getFormParams() {
			return formparams;
		}

		@Override
		public String toString() {
			final StringBuilder ret = new StringBuilder("HTTPCommand ");
			for (NameValuePair nvp : formparams) {
				ret.append(nvp.getName() + "=" + nvp.getValue() + "&");
			}
			return ret.toString();
		}

		private int nr = 0;
		private final List<NameValuePair> formparams = new ArrayList<NameValuePair>();
	}

	private final class ScreenMover implements IScreenListener {

		public ScreenMover(int position, boolean absoluteMove) {
			this.position = position;
			this.finalEnter = absoluteMove;
			if (absoluteMove) {
				dist = position - currentStatus.getCursorLine();
				next = dist >= 0;
				dist = Math.abs(dist);
			} else {
				dist = Math.abs(position);
				next = position > 0;
			}

			displayMoveMode = modelConfigurator.getModel().getDisplayMoveMode();
			switch (displayMoveMode) {
			case Series08:
				doHTTPSeries08Move();
				break;
			case HttpMove:
				doHTTPMove();
				break;
			// Classic:
			default:
				screenMoveListener = this;
				setMoveTimer(this);
				break;
			}
		}

		private void doHTTPSeries08Move() {
			// GET
			// just wget http://192.168.6.11/NETAUDIO/SendPat7.asp
			moveExecutor.execute(new Runnable() {
				public void run() {
					try {
						doGet("NETAUDIO/SendPat" + (position + 6) + ".asp");
					} catch (Exception x) {
						Logger.error("HTTP Move failed", x);
					}

				}

			});
		}

		private void doGet(final String toget) throws IOException,
				ClientProtocolException {
			final String baseURL = modelConfigurator.getConnectionConfig()
					.getBaseURL();
			final String url = baseURL + toget;
			Logger.debug("doGet: [" + url + "] ...");
			final HttpGet getRequest = new HttpGet(url);
			final HttpResponse response = httpclient.execute(getRequest);
			final HttpEntity entity = response.getEntity();
			if (entity != null) {
				entity.consumeContent();
			}
			Logger.debug("doGet [" + url + "] code:"
					+ response.getStatusLine().getStatusCode());
		}

		private void doHTTPMove() {
			// POST
			// http://192.168.1.50/NetAudio/index.put.asp
			// down:
			// cmd0=PutNetAudioCommand%2FCurDown&cmd1=osThreadSleep%2F50&cmd2=PutNetAudioCommand%2FCurDown&cmd3=osThreadSleep%2F50&cmd4=PutNetAudioCommand%2FCurDown&cmd5=osThreadSleep%2F50&cmd6=PutNetAudioCommand%2FCurRight&ZoneName=MAIN+ZONE
			// up:
			// cmd0=PutNetAudioCommand%2FCurUp&cmd1=osThreadSleep%2F50&cmd2=PutNetAudioCommand%2FCurUp&cmd3=osThreadSleep%2F50&cmd4=PutNetAudioCommand%2FCurUp&cmd5=osThreadSleep%2F50&cmd6=PutNetAudioCommand%2FCurUp&cmd7=osThreadSleep%2F50&cmd
			moveExecutor.execute(new Runnable() {
				public void run() {
					try {

						doGet("/goform/formNetAudio_StatusXml.xml");
						Thread.sleep(200);

						final String baseURL = modelConfigurator
								.getConnectionConfig().getBaseURL();
						final CommandBuilder cb = new CommandBuilder();
						for (int i = 0; i < dist; i++) {
							cb.add("PutNetAudioCommand", next ? "CurDown"
									: "CurUp");
							cb.add("osThreadSleep", "50");
						}
						cb.add("PutNetAudioCommand", "CurRight");
						UrlEncodedFormEntity form = new UrlEncodedFormEntity(cb
								.getFormParams(), "UTF-8");
						Logger.info("do HTTP POST Move : " + cb.toString());
						HttpPost httppost = new HttpPost(baseURL
								+ "NetAudio/index.put.asp");
						httppost.setEntity(form);

						HttpResponse response = httpclient.execute(httppost);
						Logger.debug("doHTTPMove Code:"
								+ response.getStatusLine().getStatusCode());
						final HttpEntity entity = response.getEntity();
						if (entity != null) {
							entity.consumeContent();
						}
					} catch (Exception x) {
						Logger.error("HTTP Move failed", x);
					}

				}
			});
		}

		public void update(DisplayStatus state) {
			// Bei jedem Update eins weiter ...
			move();
		}

		public void move() {
			if (displayMoveMode != DisplayMoveMode.Classic) {
				return;
			}
			Logger.info("move cursorMoves:" + dist + " next:" + next);
			if (dist == 0) {
				screenMoveListener = NULL_LISTENER;
				if (finalEnter) {
					enter();
					checkExtraUpdate();
				}
			} else {

				if (next) {
					down();
				} else {
					up();
				}
				checkExtraUpdate();
				dist--;
			}

		}

		private final DisplayMoveMode displayMoveMode;
		private int dist;
		private boolean next;
		private final int position;
		private final boolean finalEnter;
	}

	private final static class LineNumber {
		public LineNumber(int current, int total) {
			this.current = current;
			this.total = total;
		}

		@Override
		public String toString() {
			return "current " + current + " total:" + total;
		}

		final int current;
		final int total;
	}

	final static class LineState {

		public LineState(String line, boolean playable, boolean directory,
				boolean cursor, boolean status, boolean picture) {
			// Manche receiver füllen mit Leerzeichen auf...
			this.line = RIGHT_TRIM.matcher(line).replaceAll("");
			this.playable = playable;
			this.directory = directory;
			this.cursor = cursor;
			this.status = status;
			this.picture = picture;
		}

		public String getLine() {
			return line;
		}

		public boolean isDefined() {
			return line.length() > 0;
		}

		public LineNumber parseLineNumber() {
			Matcher matcher = INFO_PATTERN.matcher(line);
			if (matcher.matches()) {
				LineNumber ln = new LineNumber(Integer.parseInt(matcher
						.group(1)) - 1, Integer.parseInt(matcher.group(2)));
				Logger.info("line [" + line + "]->" + ln);
				return ln;
			}
			Logger.info("no line info found[" + line + "]");
			// Fallback
			return new LineNumber(0, 7);
		}

		public boolean isPicture() {
			return picture;
		}

		public boolean isPlayable() {
			return playable;
		}

		public boolean isDirectory() {
			return directory;
		}

		public boolean isCursor() {
			return cursor;
		}

		public boolean isStatus() {
			return status;
		}

		private final String line;
		private final boolean playable;
		private final boolean directory;
		private final boolean cursor;
		private final boolean status;
		private final boolean picture;
		private static final Pattern RIGHT_TRIM = Pattern.compile("\\s+$");
	}

	public final class DisplayStatus implements IDisplayStatus {

		private static final String NOW = "Now ";

		public DisplayStatus() {
			this(new ArrayList<LineState>(), 0, 0, -1);
		}

		public DisplayStatus(final List<LineState> display,
				final int offsetLine, final int totalLines, final int cursorLine) {
			this.offsetLine = offsetLine;
			// erste Zeile
			this.title = display.size() > 0 ? display.get(0).line : "";
			// letzte Zeile
			this.info = display.size() > 0 ? display.get(display.size() - 1).line
					: "";
			// Alles ohne Titel+Info
			for (int i = 1; i < display.size() - 1; i++) {
				this.display.add(display.get(i));
			}
			this.totalLines = totalLines;
			this.cursorLine = cursorLine < display.size() ? cursorLine : -1;
		}

		public boolean isCursorDefined() {
			return cursorLine != -1;
		}

		public List<String> getDisplayLines() {
			final ArrayList<String> ret = new ArrayList<String>(display.size());
			for (LineState ls : display) {
				ret.add(ls.line);
			}
			return ret;
		}

		public DisplayLine getDisplayLine(int nr) {
			if (nr >= display.size()) {
				throw new IllegalStateException("nr out of range:" + nr + ">="
						+ display.size());
			}
			final LineState lineState = display.get(nr);
			return new DisplayLine(lineState.line, lineState.playable,
					lineState.cursor, lineState.directory);
		}

		public int getCursorLine() {
			return cursorLine;
		}

		public int getDisplayCount() {
			return display.size();
		}

		public String getInfoLine() {
			return info;
		}

		public String getTitle() {
			return title;
		}

		public int getOffsetLine() {
			return offsetLine;
		}

		/** Liefert den Text unter dem Cursor, falls undefiniert "null" */
		private String getCursorText() {
			if (cursorLine != -1) {
				final LineState lineState = display.get(cursorLine);
				if (lineState != null) {
					return lineState.getLine();
				}
			}
			return null;
		}

		public boolean isDefined() {
			return !display.isEmpty();
		}

		public boolean isPagingAllowed() {
			// u.U. hängt sich der Receiver auf, oder es kommt zu unnötigem
			// Scrolling
			final boolean allowed = isCursorDefined()
					&& (totalLines == 0 || totalLines > displayRows - 1);
			if (!allowed) {
				Logger.info("paging not allowed cursor:" + isCursorDefined()
						+ " totalLines:" + totalLines);
			}
			return allowed;
		}

		public String getPlayInfo() {
			// Kein momentanes Info
			if (isCursorDefined()) {
				return "";
			}
			if (display.size() > 0) {
				final String ret = title + " : " + display.get(0).getLine();
				if (ret.startsWith(NOW)) {
					return ret.substring(NOW.length());
				}
				return ret;
			}
			return "";
		}

		public String toDebugString() {
			final StringBuilder ret = new StringBuilder();
			ret.append("[title:" + title + "]");
			for (LineState s : display) {
				ret.append("[" + s.getLine() + "]");
			}
			ret.append("[info:" + info + "]");
			return ret.toString();
		}

		public TrackInfo getTrackInfo() {
			if (display.size() > 2) {
				if (title.toUpperCase().contains("IRADIO")) {
					return new TrackInfo(display.get(0).getLine(), display.get(
							1).getLine());
				} else {
					return new TrackInfo(display.get(0).getLine(), display.get(
							1).getLine(), display.get(3).getLine());
				}

			}
			return null;
		}

		/** Ist die angegebene Zeile ein gültiges Click-Ziel ? */
		public boolean isValidPosition(int pos) {
			if (pos >= display.size()) {
				return false;
			}
			return display.get(pos).isDefined();
		}

		@Override
		public String toString() {
			return "DisplayStatus cursor:" + cursorLine + " total:"
					+ totalLines + " offset:" + offsetLine + " displaySize:"
					+ display.size();
		}

		private final String info;
		private final String title;
		// absolute Anzahl Zeilen (aus Info)
		private final int totalLines;
		private final int cursorLine;
		private final int offsetLine;
		private final List<LineState> display = new ArrayList<LineState>();
	}

	/**
	 * Dargestellte Informationen. Wird erst gefüllt und dann wird der Status
	 * erzeugt.
	 */
	public final class DisplayStatusReader {

		/**
		 * Daten dürfen nicht mehr verändert werden
		 * (update->IllegalStateException)
		 */
		public DisplayStatus createStatus() {
			fixed = true;

			// alte Inhalte löschen (Receiver Bug)
			if (cursorLine == -1 && display.size() == displayRows + 1) {
				final int start = findTimeInfoLine();
				if (start != -1) {
					// bis Zeile 7 prüfen, ob leer
					for (int i = start + 1; i < displayRows; i++) {
						if (display.get(i).line.length() > 0) {
							Logger.info("quirks clear screen[" + i + "]");
							display.set(i, EMPTY_LINE);

						}
					}
				}
			}

			return new DisplayStatus(display,
					lineNumber != null ? lineNumber.current : 0,
					lineNumber != null ? lineNumber.total : 0, cursorLine);
		}

		// NSE5..NSE6
		private int findTimeInfoLine() {
			for (int i = 4; i < displayRows - 1; i++) {
				if (TIME_PATTERN.matcher(display.get(i).line).matches()) {
					return i;
				}
			}
			return -1;
		}

		// Rückgabe "sind die übergebenen Daten die letzte Zeile"
		public boolean update(InData v) {
			if (fixed) {
				throw new IllegalStateException("already fixed");
			}
			// 0..8 (NSE0..8)
			final int line = v.getDisplayLineNumber();
			if (v.length() > 1) {
				final boolean playable;
				final boolean directory;
				final boolean cursor;
				final boolean picture;
				final int start;
				final boolean statusLine = line == 0 || line >= displayRows;
				if (statusLine) {
					if (line >= displayRows) {
						// Info-Zeilen-Offset (normal:1,4306:2)->2 sollte immer
						// ok sein (Leerzeichen oder "NULL")
						start = 2;
					} else {
						start = 1;
					}
					directory = false;
					playable = false;
					cursor = false;
					picture = false;
				} else {
					start = 2;
					final int info = v.charAt(1);
					playable = (info & 1) == 1;
					directory = (info & 2) == 2;
					cursor = (info & 8) == 8;
					picture = (info & 128) == 128;
					if (cursor) {
						cursorLine = line - 1;
					}
				}

				final boolean lastLine = (line == displayRows);
				final LineState lineState = new LineState(v.extractLine(start),
						playable, directory, cursor, statusLine, picture);

				display.add(lineState);

				if (lastLine) {
					lineNumber = lineState.parseLineNumber();
				}
				return lastLine;
			}
			return true;
		}

		private boolean fixed;
		private LineNumber lineNumber;
		private int cursorLine = -1;
		private final List<LineState> display = new ArrayList<LineState>(
				displayRows + 1);

	}

	public DisplayStatus getDisplayStatus() {
		return currentStatus;
	}

	/**
	 * @param zoneState
	 */
	public NetDisplay(ISender sender, ModelConfigurator modelConfigurator,
			DisplayType type) {
		AVRHTTPClient.configureHTTPClient(httpclient);
		this.sender = sender;
		this.modelConfigurator = modelConfigurator;
		switch (type) {
		case NETWORK:
			displayRows = 8;
			this.prefix = "NS";
			break;
		case IPOD:
			prefix = "IP";
			displayRows = modelConfigurator.getIPodDisplayRows();
			break;
		default:
			throw new IllegalArgumentException("net-type:" + type);
		}
		Logger.info("init NetDisplay type:" + type + " rows:" + displayRows);
	}

	private void checkExtraUpdate() {
		if (modelConfigurator.getModel().isExtraUpdateNeeded()) {
			doExtraUpdate();
		}
	}

	public int getDisplayId() {
		return R.string.Display;
	}

	public String getCommandPrefix() {
		return prefix + "E";
	}

	public String getReceivePrefix() {
		return getCommandPrefix();
	}

	public boolean isDefined() {
		return currentStatus.isDefined();
	}

	public boolean isCommandSecondaryZoneEncoded() {
		return false;
	}

	public void up() {
		if (menuMode) {
			this.sender.send("MNCUP");
		} else {
			this.sender.send(prefix + "90");
		}
	}

	public void down() {
		if (menuMode) {
			this.sender.send("MNCDN");
		} else {
			this.sender.send(prefix + "91");
		}
	}

	public void left() {
		if (menuMode) {
			this.sender.send("MNCLT");
		} else {
			displayLeft();
		}
	}

	private void displayLeft() {
		this.sender.send(prefix + "92");
	}

	public void right() {
		if (menuMode) {
			this.sender.send("MNCRT");
		} else {
			this.sender.send(prefix + "93");
		}
	}

	public void pause() {
		this.sender.send(prefix + "94");
		checkExtraUpdate();		
	}

	public void stop() {
		this.sender.send(prefix + "9C");
	}

	public void returnLevel() {
		if (menuMode) {
			this.sender.send("MNRTN");
		} else {
			requestInputUpdate = true;
			displayLeft();
		}
		hangDetector.updateExpected();
	}

	public void home() {
		new HomeMover().move();
		hangDetector.updateExpected();
	}

	public void enter() {
		if (menuMode) {
			this.sender.send("MNENT");
		} else {
			final String cursorText = currentStatus.getCursorText();
			Logger.info("selected [" + cursorText + "]");

			// Nach dem nächsten Screen-Update
			requestInputUpdate = true;

			if ("Search by Keyword".equalsIgnoreCase(cursorText)
					|| "Enter Characters".equalsIgnoreCase(cursorText)) {
				displayListener.displayInfo(R.string.UseSearchButton);

			} else {
				if ("Napster".equals(cursorText)) {
					// Ansonsten kommt es u.U. zu merkwürdigem Verhalten
					if (modelConfigurator.isNapsterEnabled()) {
						doEnter();
						handler.postDelayed(new Runnable() {

							public void run() {
								update();
							}
						}, 1000);
					} else {
						Logger.info("Napster disabled");
						displayListener
								.displayInfo("Napster is disabled. Please enable it in the settings.");
					}
				} else {
					doEnter();
				}
			}
		}
		hangDetector.updateExpected();
	}

	private void updateCurrentInput() {
		Logger.info("NetDisplay:updateCurrentInput");
		if (!requestInputUpdate) {
			return;
		}
		if (activeZoneState != null) {
			activeZoneState.updateState(InputSelect.class);
		} else {
			Logger.error("ActiveZoneState==null", new Throwable());
		}
		requestInputUpdate = false;
	}

	private void doEnter() {
		this.sender.send(prefix + "94");
	}

	public void menu(boolean on) {
		this.sender.send("MNMEN " + (on ? "ON" : "OFF"));
		menuMode = on;
	}

	public void menuSourceSelect(boolean on) {
		this.sender.send("MNSRC " + (on ? "ON" : "OFF"));
		menuMode = on;
	}

	public void menuFavourite(boolean on) {
		this.sender.send("MNFAV " + (on ? "ON" : "OFF"));
		menuMode = on;
	}

	public void moveTo(int position) {
		if (currentStatus.isValidPosition(position)) {
			moveTo(position, true);
			hangDetector.updateExpected();
		} else {
			Logger.debug("clicked outside " + position + ">" + currentStatus);
		}
	}

	private void moveTo(int position, boolean finalEnter) {
		if (!isMoving()) {
			Logger.info("moveTo [" + position + "] " + position);
			new ScreenMover(position, finalEnter).move();
		} else {
			Logger.info("move " + position + " ignored");
		}
	}

	private boolean isPagingAllowed() {
		return currentStatus.isPagingAllowed();
	}

	public void pageUp() {
		if (!menuMode && isPagingAllowed()) {
			if (modelConfigurator.getModel().hasPgUpDown()) {
				this.sender.send(prefix + "9Y");
			} else {
				moveTo(-1 * PAGE_EMU_MOVE, false);
			}
		}
		hangDetector.updateExpected();
	}

	public void pageDown() {
		if (!menuMode && isPagingAllowed()) {
			if (modelConfigurator.getModel().hasPgUpDown()) {
				this.sender.send(prefix + "9X");
			} else {
				moveTo(PAGE_EMU_MOVE, false);
			}
		}
		hangDetector.updateExpected();
	}

	public void repeatOne() {
		this.sender.send(prefix + "9H");
	}

	public void repeatAll() {
		this.sender.send(prefix + "9I");
	}

	public void repeatOff() {
		this.sender.send(prefix + "9J");
	}

	public void randomOn() {
		this.sender.send(prefix + "9K");
	}

	public void randomOff() {
		this.sender.send(prefix + "9M");
	}

	public void skipPlus() {
		this.sender.send(prefix + "9D");
	}

	public void skipMinus() {
		this.sender.send(prefix + "9E");
	}

	public void play() {
		this.sender.send(prefix + "9A");
	}

	public void update() {
		this.sender.send(prefix + "E");
	}

	public void search(String text) {
		this.sender.send(prefix + "D" + text);
	}

	/** Rückgabe, haben sich die Daten geändert = Letzte Zeile gelesen */
	public boolean update(InData v) {
		// Erste gelesene Zeile, neuen DisplayStatus anlegen
		if (v.getDisplayLineNumber() == 0) {
			statusReader = new DisplayStatusReader();
		}

		final boolean lastLine = statusReader.update(v);
		if (lastLine) {
			currentStatus = statusReader.createStatus();
			statusReader = new DisplayStatusReader();

			screenMoveListener.update(currentStatus);
			Logger.info("NetDisplay.inform display listener[" + displayListener
					+ "] (" + currentStatus.toDebugString() + ")");
			displayListener.displayChanged(currentStatus);
			updateCurrentInput();
			hangDetector.screenUpdated();
		}

		return lastLine;
	}

	public void reset() {
		statusReader = new DisplayStatusReader();
		currentStatus = new DisplayStatus();
		doExtraUpdate();
	}

	public String getPlayInfo() {
		return currentStatus.getPlayInfo();
	}

	private final Runnable screenUpdater = new Runnable() {
		public void run() {
			// Ist dies eine Play-Anzeige ?
			// (Auswahlen/Listen nicht automatisch aktualisieren)
			if (!NetDisplay.this.getDisplayStatus().isCursorDefined()
					&& displayListener != IDisplayListener.NULL_LISTENER) {
				NetDisplay.this.update();
			} else {
				Logger.debug("NetDisplay: Screen update ignored NL:"
						+ (displayListener == IDisplayListener.NULL_LISTENER)
						+ " cursor:"
						+ NetDisplay.this.getDisplayStatus().isCursorDefined());
			}
			handler.postDelayed(this, SCREEN_UPDATE_TIME);
		}
	};

	public void setAutoUpdate(boolean on) {
		if (on != autoUpdate) {
			Logger.info("NetDisplay.set AutoUpdate " + on);

			autoUpdate = on;

			if (on) {
				handler.removeCallbacks(screenUpdater);
				// Fügt automatisch wieder einen Task ein, der zu einer
				// Wiederholung
				// führt.
				screenUpdater.run();
			} else {
				handler.removeCallbacks(screenUpdater);
			}
		}
	}

	public boolean isAutoUpdate() {
		return autoUpdate && displayListener != IDisplayListener.NULL_LISTENER;
	}

	private final static IScreenListener NULL_LISTENER = new IScreenListener() {
		public void update(DisplayStatus state) {
		}
	};

	public boolean isMoving() {
		return screenMoveListener != NULL_LISTENER;
	}

	public IScreenMenu createMenu(Activity activity, Zone zone) {
		return new ScreenMenu(activity, this, modelConfigurator, zone);
	}

	public void setListener(IDisplayListener listener) {
		Logger.debug("NetDisplay.setListener:" + listener);
		if (listener != IDisplayListener.NULL_LISTENER) {
			this.displayListener = new GUIDisplayListener(listener);
			this.displayListener.displayChanged(currentStatus);
			update();
			setAutoUpdate(true);
		} else {
			this.displayListener = IDisplayListener.NULL_LISTENER;
			setAutoUpdate(false);
		}

	}

	public void clearListener() {
		Logger.debug("NetDisplay.clearListener");
		setListener(IDisplayListener.NULL_LISTENER);
	}

	public boolean isDummy() {
		return false;
	}

	public int getLayoutResource() {
		return de.pskiwi.avrremote.R.layout.osd_screen;
	}

	public void extendView(Activity activity, IStatusComponentHandler handler) {
	}

	public boolean useTunerControls() {
		return false;
	}

	public void setActiveZoneState(ZoneState zoneState) {
		requestInputUpdate = true;
		this.activeZoneState = zoneState;
	}

	public Set<Operations> getSupportedOperations() {
		final Set<Operations> ret = new HashSet<Operations>();
		for (Operations op : Operations.values()) {
			ret.add(op);
		}
		return ret;
	}

	private boolean requestInputUpdate;
	private ZoneState activeZoneState = null;

	// NSE0..8, Index der maximalen Zeile ("0" indiziert) (Net=8,iPod=9)
	private final int displayRows;

	private IDisplayListener displayListener = IDisplayListener.NULL_LISTENER;

	private final ReceiverHangDetector hangDetector = new ReceiverHangDetector(
			new IDisplayListener() {

				public void displayInfo(final String text) {
					handler.post(new Runnable() {

						public void run() {
							displayListener.displayInfo(text);
						}
					});
				}

				public void displayInfo(final int resId) {
					handler.post(new Runnable() {

						public void run() {
							displayListener.displayInfo(resId);
						}
					});

				}

				public void displayChanged(IDisplayStatus display) {
					// ignore
				}
			});

	private void doExtraUpdate() {
		handler.postDelayed(new Runnable() {

			public void run() {
				NetDisplay.this.update();
			}
		}, 1000);
	}

	public TrackInfo getCurrentTrackInfo() {
		return currentStatus.getTrackInfo();
	}

	public void musicSearch(Activity activity) {
		final TrackInfo trackInfo = currentStatus.getTrackInfo();
		Logger.info("musicSearch " + trackInfo);
		if (trackInfo != null) {
			Intent i = new Intent();
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
			i.putExtra(SearchManager.QUERY, trackInfo.getTrackQuery());
			i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, trackInfo.getArtist());
			i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, trackInfo.getAlbum());
			i.putExtra(MediaStore.EXTRA_MEDIA_TITLE, trackInfo.getTrack());
			i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "audio/*");
			activity.startActivity(Intent.createChooser(i, "Search for "
					+ trackInfo.getTrack()));
		} else {
			Toast.makeText(activity, "No track info available",
					Toast.LENGTH_SHORT).show();
		}
	}

	private boolean menuMode;
	private boolean autoUpdate;
	private IScreenListener screenMoveListener = NULL_LISTENER;

	private final ModelConfigurator modelConfigurator;

	private final String prefix;

	private final static LineState EMPTY_LINE = new LineState("", false, false,
			false, false, false);

	private Handler handler = new Handler();
	// Aktuell gültiger Zustand (final und konsistent)
	private DisplayStatus currentStatus = new DisplayStatus();
	// Nächster gelesener Status (im Hintergrund verändert)
	private DisplayStatusReader statusReader = new DisplayStatusReader();

	private final static int PAGE_EMU_MOVE = 7;

	private final ISender sender;

	private final static Pattern INFO_PATTERN = Pattern
			.compile(".*\\[\\s+(\\d+)/\\s*(\\d+).*");

	private final static Pattern TIME_PATTERN = Pattern
			.compile(".*(\\d+):(\\d+)\\s+(\\d+)%.*");

	private static final int SCREEN_UPDATE_TIME = 5000;

	private final static String[] HOME_STRINGS = new String[] { "FAVORITES",
			"MEDIA SERVER", "INTERNET RADIO" };

	private static final int MAX_MOVE_TIME = 20000;

	private final DefaultHttpClient httpclient = new DefaultHttpClient();
	private final Executor moveExecutor = Executors.newSingleThreadExecutor();

}