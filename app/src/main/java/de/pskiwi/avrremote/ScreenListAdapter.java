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
package de.pskiwi.avrremote;

import android.content.Context;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;
import de.pskiwi.avrremote.core.display.DisplayLine;
import de.pskiwi.avrremote.core.display.IDisplay;
import de.pskiwi.avrremote.core.display.IDisplayListener;
import de.pskiwi.avrremote.core.display.IDisplayStatus;
import de.pskiwi.avrremote.log.Logger;

/** Screen Model */
public final class ScreenListAdapter extends BaseAdapter {

	private final Runnable UPDATE_SCREEN = new Runnable() {
		public void run() {
			displayStatus = nextDisplayStatus;
			titleListener.titleChanged(displayStatus.getTitle());
			titleListener.infoChanged(displayStatus.getInfoLine());
			Logger.info("do update ScreenListAdapter cursor:"
					+ displayStatus.getCursorLine() + " {"
					+ displayStatus.toDebugString() + "}");
			ScreenListAdapter.this.notifyDataSetChanged();
			lastUpdateTime = System.currentTimeMillis();
		}
	};

	public ScreenListAdapter(final Context context, final IDisplay screen,
			final ITitleListener titleListener, AVRTheme avrTheme) {
		this.context = context;
		this.avrTheme = avrTheme;
		this.iconManager = new IconManager(context);
		this.titleListener = titleListener;
		Logger.info("init ListScreenAdapter ...");
		this.screen = screen;
		screen.reset();

		screen.setListener(new IDisplayListener() {
			public void displayChanged(final IDisplayStatus display) {
				// nicht direkt umsetzen um IllegalStateException zu vermeiden
				// http://stackoverflow.com/questions/3132021/android-listview-illegalstateexception-the-content-of-the-adapter-has-changed
				nextDisplayStatus = display;
				// min alle 2*TIME_BETWEEN_UPDATES ein Update zulassen
				if (System.currentTimeMillis() - lastUpdateTime < TIME_BETWEEN_UPDATES) {
					handler.removeCallbacks(UPDATE_SCREEN);
				}

				// Updates zusammenfassen um Flackern zu vermeiden
				handler.postDelayed(UPDATE_SCREEN, TIME_BETWEEN_UPDATES);
				Logger.info("post update ScreenListAdapter cursor:"
						+ displayStatus.getCursorLine());
			}

			public void displayInfo(final String text) {
				handler.post(new Runnable() {
					public void run() {
						Toast.makeText(context, text, Toast.LENGTH_LONG).show();
					}
				});
			}

			public void displayInfo(int resId) {
				displayInfo(context.getString(resId));
			}

			@Override
			public String toString() {
				return "ScreenListAdapter.IDisplayListener";
			}

		});

		Logger.info("init ListScreenAdapter ok.");
	}

	public int getCount() {
		return displayStatus.getDisplayCount();
	}

	public DisplayLine getItem(int row) {
		if (row < displayStatus.getDisplayCount()) {
			return displayStatus.getDisplayLine(row);
		}
		return DisplayLine.EMPTY;
	}

	public long getItemId(int id) {
		return id;
	}

	public void clicked(int position) {
		if (displayStatus.isCursorDefined()) {
			screen.moveTo(position);
		} else {
			Logger.info("clicked: no cursor defined :" + position);
		}
	}

	public View getView(int row, View oldView, ViewGroup parentView) {
		View v = oldView;
		if (v == null) {
			final LayoutInflater vi = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.listrow, null);
		}

		final TextView tt = (TextView) v.findViewById(R.id.rowtext);
		if (tt != null) {
			iconManager.styleView(tt, getItem(row), avrTheme);
			avrTheme.setTextColor(tt);
		} else {
			Logger.info("rowtext not found");
		}
		final DisplayMetrics displayMetrics = parentView.getResources()
				.getDisplayMetrics();

		final ScreenInfo screenInfo = new ScreenInfo(context);
		if (screenInfo.isTablet()) {
			tt.setTextSize(22);
		} else {
			if (displayMetrics.heightPixels < 800) {
				if (displayMetrics.heightPixels < 480) {
					tt.setTextSize(12);
				} else {
					tt.setTextSize(15);
				}
			} else {
				tt.setTextSize(21);
			}
		}

		return v;
	}

	@Override
	public String toString() {
		return "ScreenListAdapter";
	}

	private final AVRTheme avrTheme;
	private final IconManager iconManager;
	// aktuelles Modell
	private IDisplayStatus displayStatus = IDisplayStatus.EMPTY_DISPLAY;
	// nächstes Modell (max alle 100ms aktualisiert)
	private IDisplayStatus nextDisplayStatus = IDisplayStatus.EMPTY_DISPLAY;

	private long lastUpdateTime = 0;
	private final Handler handler = new Handler();
	private final Context context;
	private final IDisplay screen;
	private final ITitleListener titleListener;

	private static final int TIME_BETWEEN_UPDATES = 100;
}
