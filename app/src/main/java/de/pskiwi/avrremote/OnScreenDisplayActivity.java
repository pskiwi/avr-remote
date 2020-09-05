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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.ListActivity;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ListView;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.EnableManager.ViewList;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.display.DisplayManager;
import de.pskiwi.avrremote.core.display.IDisplay;
import de.pskiwi.avrremote.core.display.IStatusComponentHandler;
import de.pskiwi.avrremote.core.display.IDisplay.Operations;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.menu.OptionsMenu;

public final class OnScreenDisplayActivity extends ListActivity implements
		IActivityShowing {

	private AVRApplication getApp() {
		return (AVRApplication) getApplication();
	}

	private void attach(int id, boolean supported, final Runnable r) {
		final View view = (View) findViewById(id);
		if (view != null) {
			if (supported) {
				viewList.addView(view, statusFlag);
				view.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						r.run();
					}
				});
			} else {
				view.setEnabled(supported);
			}
		}
	}

	private void attachLongPress(int id, boolean supported, final Runnable r) {
		final View view = (View) findViewById(id);
		if (view != null) {
			if (supported) {
				view.setOnLongClickListener(new OnLongClickListener() {
					public boolean onLongClick(View v) {
						r.run();
						return true;
					}
				});
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final ScreenInfo screenInfo = new ScreenInfo(this);
		Logger.info("create OSD " + screenInfo.toString());
		if (!screenInfo.isTablet()) {
			this
					.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		optionsMenu = new OptionsMenu(this, getApp().getModelConfigurator(),
				this);
		viewList = getApp().getEnableManager().createViewList();

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			zone = Zone.fromId(extras.getString(Zone.INTENT_KEY));

		}
		if (zone == null) {
			if (savedInstanceState != null) {
				zone = Zone.fromId(savedInstanceState
						.getString(Zone.INTENT_KEY));
			}
			Logger.info("Zone from bundle:" + zone);
			if (zone == null) {
				zone = Zone.Main;
			}
		}

		statusFlag = getApp().getModelConfigurator().getModel()
				.translateZoneFlag(zone.getFlag());

		viewList.addView(getListView(), statusFlag);

		Logger.info("start ListView for " + zone);

		zoneState = getApp().getAvrState().getZone(zone);

		DisplayManager displayManager = getApp().getDisplayManager();
		displayManager.clearAllListener();
		final IDisplay screen = displayManager.getCurrentDisplay(zone);
		Logger.info("start screen" + screen);
		screen.setActiveZoneState(zoneState);
		setContentView(screen.getLayoutResource());

		screen.extendView(this, new IStatusComponentHandler() {
			public void addView(View v, AtomicBoolean enable) {
				viewList.addView(v, statusFlag, enable);
			}
			
			public void addView(View v) {
				viewList.addView(v, statusFlag);
			}
		});

		listAdapter = new ScreenListAdapter(this, screen, new ITitleListener() {
			public void titleChanged(String title) {
				viewUp.setText(title);
			}

			public void infoChanged(String title) {
				viewDown.setText(title);
			}
		}, getApp().getAvrTheme());
		setListAdapter(listAdapter);

		screenMenu = screen.createMenu(this, zone);
		Set<Operations> ops = screen.getSupportedOperations();

		attach(R.id.btnBack, ops.contains(Operations.Return), new Runnable() {
			public void run() {
				screen.returnLevel();
			}
		});
		attach(R.id.btnUp, ops.contains(Operations.Up), new Runnable() {
			public void run() {
				screen.pageUp();
			}
		});
		attach(R.id.btnPlay, ops.contains(Operations.Play), new Runnable() {
			public void run() {
				screen.play();
			}
		});

		viewUp = (Button) findViewById(R.id.btnUp);
		viewDown = (Button) findViewById(R.id.btnDown);
		Drawable pgUp = getResources().getDrawable(R.drawable.page_up);
		Drawable pgDown = getResources().getDrawable(R.drawable.page_down);
		viewUp.setCompoundDrawablesWithIntrinsicBounds(pgUp, null, pgUp, null);
		viewDown.setCompoundDrawablesWithIntrinsicBounds(pgDown, null, pgDown,
				null);

		attach(R.id.btnHome, ops.contains(Operations.Home), new Runnable() {
			public void run() {
				screen.home();
			}
		});
		attach(R.id.btnDown, ops.contains(Operations.Down), new Runnable() {
			public void run() {
				screen.pageDown();
			}
		});

		attach(R.id.btnPrev, ops.contains(Operations.SkipMinus),
				new Runnable() {
					public void run() {
						screen.skipMinus();
					}
				});

		attach(R.id.btnPause, ops.contains(Operations.Pause), new Runnable() {
			public void run() {
				screen.pause();
			}
		});

		attach(R.id.btnStop, ops.contains(Operations.Stop), new Runnable() {
			public void run() {
				screen.stop();
			}
		});

		attach(R.id.btnNext, ops.contains(Operations.SkipPlus), new Runnable() {
			public void run() {
				screen.skipPlus();
			}
		});

		attach(R.id.btnMenu, ops.contains(Operations.Menu), new Runnable() {
			public void run() {
				screenMenu.showMenu(false);
			}
		});

		attachLongPress(R.id.btnMenu, ops.contains(Operations.ExtraMenu),
				new Runnable() {
					public void run() {
						screenMenu.showExtraMenu();
					}
				});

		attach(R.id.btnSearch, ops.contains(Operations.Search), new Runnable() {
			public void run() {
				screenMenu.doSearch();
			}
		});

		attachLongPress(R.id.btnSearch, ops.contains(Operations.Search),
				new Runnable() {
					public void run() {
						screenMenu.doClassicSearch();
					}
				});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (zone != null) {
			outState.putString(Zone.INTENT_KEY, zone.getId());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		showing = true;
		getApp().activityResumed(this);
		getApp().getEnableManager().setClassListener(viewList);
		final StatusFlag flag = getApp().getModelConfigurator().getModel()
				.translateZoneFlag(zone.getFlag());
		AVRRemote.initVolumeButtons(this, zoneState, flag,
				findViewById(R.id.OSDView), viewList, true, getApp()
						.getAvrTheme());
		getApp().getStatusbarManager().setCurrentIntent(
				OnScreenDisplayActivity.class);
		getApp().getAvrTheme().setBackground(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		showing = false;
		getApp().activityPaused(this);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (screenMenu.handleKey(zoneState, keyCode, event)) {
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Logger.info("clicked " + position);
		listAdapter.clicked(position);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return optionsMenu.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return optionsMenu.onOptionsItemSelected(item);
	}

	public boolean isShowing() {
		return showing;
	}

	@Override
	public String toString() {
		return "ListScreenActivity " + zone;
	}

	private Button viewUp;
	private Button viewDown;
	private boolean showing;
	private ScreenListAdapter listAdapter;
	private IScreenMenu screenMenu;
	private ZoneState zoneState;
	private ViewList viewList;
	private Zone zone;
	private OptionsMenu optionsMenu;
	private StatusFlag statusFlag;

}
