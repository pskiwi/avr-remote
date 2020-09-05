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

import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.EnableManager.ViewList;
import de.pskiwi.avrremote.core.AVRState;
import de.pskiwi.avrremote.core.IConnectionListener;
import de.pskiwi.avrremote.core.IStateFilter;
import de.pskiwi.avrremote.core.IStateListener;
import de.pskiwi.avrremote.core.OptionType;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.core.RenameService.RenameCategory;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.AbstractSwitch;
import de.pskiwi.avrremote.core.ZoneState.AudioMode;
import de.pskiwi.avrremote.core.ZoneState.InputSelect;
import de.pskiwi.avrremote.core.ZoneState.MuteState;
import de.pskiwi.avrremote.core.ZoneState.PowerState;
import de.pskiwi.avrremote.core.ZoneState.SurroundMode;
import de.pskiwi.avrremote.core.ZoneState.Volume;
import de.pskiwi.avrremote.core.ZoneState.ZoneMode;
import de.pskiwi.avrremote.core.display.DisplayManager;
import de.pskiwi.avrremote.core.display.IDisplay;
import de.pskiwi.avrremote.core.display.IDisplayListener;
import de.pskiwi.avrremote.core.display.IDisplayStatus;
import de.pskiwi.avrremote.extender.SelectButtonExtender;
import de.pskiwi.avrremote.extender.SwitchImageButtonExtender;
import de.pskiwi.avrremote.extender.TouchButtonExtender;
import de.pskiwi.avrremote.log.FeedbackReporter;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.menu.ExtrasMenu;
import de.pskiwi.avrremote.menu.OptionsMenu;
import de.pskiwi.avrremote.menu.QuickMenu;

public final class AVRRemote extends TabActivity implements IActivityShowing,
		ExtrasMenu.IBaseMenuActivity {

	public AVRApplication getApp() {
		return (AVRApplication) getApplication();
	}

	public Activity getActivity() {
		return this;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		showing = true;
		super.onCreate(savedInstanceState);

		Logger.setLocation("AVRRemote-onCreate-1");

		optionsMenu = new OptionsMenu(this, getApp().getModelConfigurator(),
				this);
		configurationAssistant = new ConfigurationAssistant(this, getApp());

		getApp().getAvrState().setStateFilter(IStateFilter.DEFAULT_FILTER);

		viewList = getApp().getEnableManager().createViewList();

		getApp().getAvrState().clearStateAndListener();

		Logger.info("start AVR-Remote " + FeedbackReporter.getVersionInfo(this));
		setContentView(R.layout.tabhost);

		Logger.setLocation("AVRRemote-onCreate-2");

		FeedbackReporter.checkForCrash(this);

		// Nur bei bestehenden anzeigen
		if (getApp().getModelConfigurator().getConnectionConfig().isDefined()
				&& AVRSettings.isShowChangeLog(this)) {
			Logger.setLocation("AVRRemote-onCreate-3");
			Intent intent = new Intent(AVRRemote.this, AboutActivity.class);
			intent.putExtra("toShow", "whatsnew");
			startActivity(intent);
		}

		configurationAssistant.checkSetup();

		AVRState avrState = getApp().getAvrState();

		textDisplayHandler = new TextDisplayHandler(
				(TextView) findViewById(R.id.textDisplay));

		Logger.info("AVRRemote:init tabs");
		final TabHost tabHost = getTabHost();

		final int zoneCount = getApp().getModelConfigurator().getZoneCount();

		// Hier nochmal setzen um evtl. Änderungen zu propagieren
		getApp().getAvrState().setActiveZoneCount(zoneCount);
		Logger.info("init " + zoneCount + " of "
				+ getApp().getModelConfigurator().getModel().getZoneCount()
				+ " zones.");

		getApp().getMacroGUI().clearButtons();
		getApp().getMacroGUI().initButtons(this, null, viewList,
				R.id.btnMacro1, R.id.btnMacro2, R.id.btnMacro3);

		final RenameService renameService = getApp().getRenameService();
		zoneStates = new ZoneState[zoneCount];
		Logger.debug("create " + zoneCount + " zones ...");
		for (int i = 0; i < zoneCount; i++) {
			final Zone zone = Zone.fromNumber(i);
			CharSequence zoneName = renameService.getZoneName(zone);
			Logger.debug("create zone #" + i + " [" + zoneName + "]");
			tabHost.addTab(tabHost.newTabSpec(zone.getTabTag())
					.setIndicator(zoneName).setContent(zone.getLayoutId()));

			zoneStates[i] = avrState.getZone(zone);
			zoneViews[i] = createZoneView(zoneStates[i],
					(LinearLayout) findViewById(zone.getLayoutId()));
		}
		Logger.debug("create " + zoneCount + " zones ok.");

		getApp().getAvrTheme().saveTabSettings(tabHost);

		getApp().getAvrTheme().updateTabSettings(tabHost);

		tabHost.setCurrentTab(0);
		Logger.info("AVRRemote:init tabs ok");

		tabHost.setEnabled(false);

		currentZoneState = zoneStates[0];
		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			public void onTabChanged(String tabId) {
				Logger.info("Tab changed [" + tabId + "]");
				currentZoneState = getCurrentFrontState();

				updateDisplayListener();

				getApp().getAvrTheme().updateTabSettings(tabHost);
			}

		});

		getApp().getEnableManager().setClassListener(
				new StatusAreaManager((Button) findViewById(R.id.btnConnect),
						this));

		connectionProgressMonitor = new ConnectionProgressMonitor(this);
		getApp().getEnableManager().setClassListener(connectionProgressMonitor);

		getApp().getStatusbarManager().update();

		if (savedInstanceState != null) {
			int ct = savedInstanceState.getInt(CURRENT_TAB);
			if (ct < getTabHost().getChildCount()) {
				getTabHost().setCurrentTab(ct);
			}
		}
		Logger.info("init Timer.");
		Logger.info("AVRRemote:init ok.");
	}

	private ZoneState getCurrentFrontState() {
		final int zoneCount = getApp().getModelConfigurator().getZoneCount();
		final TabHost tabHost = getTabHost();
		final String tabTag = tabHost.getCurrentTabTag();
		for (Zone z : Zone.values()) {
			final int zoneNumber = z.getZoneNumber();
			if (z.getTabTag().equals(tabTag) && zoneCount > zoneNumber) {
				return zoneStates[zoneNumber];
			}
		}
		Logger.error("no active zone for " + tabTag, null);
		return null;
	}

	private void updateDisplayListener() {
		// sonst wird u.U der Listener vom "ListScreen" geklaut
		if (!showing) {
			return;
		}
		final DisplayManager displayManager = getApp().getDisplayManager();
		displayManager.clearAllListener();

		final ZoneState state = getCurrentFrontState();
		if (state != null) {
			displayManager.getCurrentDisplay(state.getZone()).setListener(
					new IDisplayListener() {
						public void displayChanged(IDisplayStatus display) {
							if (currentZoneState != null) {
								final Zone zone = currentZoneState.getZone();
								final IDisplay currentDisplay = displayManager
										.getCurrentDisplay(zone);
								textDisplayHandler.update(currentZoneState,
										currentDisplay.getDisplayStatus());
							}
						}

						public void displayInfo(int resId) {
							displayInfo(AVRRemote.this.getString(resId));
						}

						public void displayInfo(String text) {
							Toast.makeText(AVRRemote.this, text,
									Toast.LENGTH_LONG).show();
						}

						@Override
						public String toString() {
							return "AVRRemote:updateDisplayListener";
						}
					});
		}
	}

	/** Wird die Activity gerade angezeigt ? Sind GUI Zugriffe gültig ? */
	public boolean isShowing() {
		return showing;
	}

	@Override
	protected void onResume() {
		super.onResume();
		getApp().getStatusbarManager().setCurrentIntent(AVRRemote.class);
		getApp().getAvrTheme().setBackground(this);
		textDisplayHandler.updateTheme(getApp().getAvrTheme());
		showing = true;
		VolumeDisplay oldVolumeDisplay = volumeDisplay;
		volumeDisplay = AVRSettings.getVolumeDisplay(this);
		AVRState avrState = getApp().getAvrState();
		if (oldVolumeDisplay != volumeDisplay) {
			final int zoneCount = getApp().getModelConfigurator()
					.getZoneCount();
			for (int i = 0; i < zoneCount; i++) {
				avrState.getZone(Zone.fromNumber(i)).notifyListener(
						Volume.class);
			}
		}
		getApp().activityResumed(this);
		getApp().getEnableManager().setClassListener(viewList);
		connectionProgressMonitor.checkOnResume();

		updateDisplayListener();

		final int zoneCount = getApp().getModelConfigurator().getZoneCount();
		for (int i = 0; i < zoneCount; i++) {
			final Zone z = Zone.fromNumber(i);
			// Somst FC nach Zonen-Reduzierung
			if (i < zoneStates.length && i < zoneViews.length) {
				final StatusFlag flag = getApp().getModelConfigurator()
						.getModel().translateZoneFlag(z.getFlag());
				initVolumeButtons(this, zoneStates[i], flag, zoneViews[i],
						viewList, false, getApp().getAvrTheme());
			}
		}

		getApp().getAvrTheme().updateTabSettings(getTabHost());
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(CURRENT_TAB, getTabHost().getCurrentTab());
	}

	@Override
	protected void onPause() {
		super.onPause();
		getApp().getDisplayManager().clearAllListener();
		showing = false;
		getApp().activityPaused(this);
		getApp().getEnableManager().removeClassListener(ViewList.class);
		connectionProgressMonitor.doPause();
	}

	// http://www.ceveni.com/2009/08/how-to-get-screen-orientation-in.html
	public int getScreenOrientation() {
		final Display display = getWindowManager().getDefaultDisplay();

		// if height and widht of screen are equal then
		// it is square orientation
		if (display.getWidth() == display.getHeight()) {
			return Configuration.ORIENTATION_SQUARE;
		} else { // if widht is less than height than it is portrait
			if (display.getWidth() < display.getHeight()) {
				return Configuration.ORIENTATION_PORTRAIT;
			} else { // if it is not any of the above it will defineitly be
				// landscape
				return Configuration.ORIENTATION_LANDSCAPE;
			}
		}

	}

	private View createZoneView(final ZoneState zone, LinearLayout parentLayout) {
		final StatusFlag flag = getApp().getModelConfigurator().getModel()
				.translateZoneFlag(zone.getZone().getFlag());

		final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.remote, null);
		parentLayout.addView(view);

		final int orientation = getScreenOrientation();

		Logger.info("Orientation portrait:"
				+ (orientation == Configuration.ORIENTATION_PORTRAIT));

		final ImageView zoneButton = (ImageView) view
				.findViewById(R.id.btnZone);
		viewList.addView(zoneButton, StatusFlag.Connected);
		final Class<? extends AbstractSwitch> powerClass = getApp()
				.getModelConfigurator().getModel().hasZones()
				|| zone.getZone() != Zone.Main ? ZoneMode.class
				: PowerState.class;
		SwitchImageButtonExtender.extend(zoneButton, this, zone, powerClass,
				R.drawable.power, R.drawable.power);

		final ImageView muteButton = (ImageView) view
				.findViewById(R.id.btnMute);
		viewList.addView(muteButton, flag);
		SwitchImageButtonExtender.extend(muteButton, this, zone,
				MuteState.class, R.drawable.volume_mute, R.drawable.volume);
		final TextView inputSelect = (TextView) view
				.findViewById(R.id.btnInput);
		viewList.addView(inputSelect, flag);
		final Button screenButton = (Button) view.findViewById(R.id.btnDisplay);
		final AtomicBoolean displayEnable = new AtomicBoolean();
		viewList.addView(screenButton, flag, displayEnable);

		getApp().getMacroGUI().initButtons(this, view, viewList,
				R.id.btnMacro1, R.id.btnMacro2, R.id.btnMacro3);

		SelectButtonExtender.extend(inputSelect, this, zone, InputSelect.class,
				new Runnable() {
					public void run() {
						IDisplay display = getApp().getDisplayManager()
								.getCurrentDisplay(zone.getZone());
						updateDisplayListener();

						displayEnable.set(!display.isDummy());
						viewList.update(screenButton);

					}
				}, getApp().getRenameService(), RenameCategory.SOURCE);
		viewList.addView(inputSelect, flag);
		final Button modeSelect = (Button) view.findViewById(R.id.btnMode);

		// Surround nur in der Main-Zone, bei Geräten, die dies unterstützen
		if (zone.getZone() == Zone.Main
				&& !getApp().getModelConfigurator().getSurroundSelection()
						.isEmpty()) {
			SelectButtonExtender.extend(modeSelect, this, zone,
					SurroundMode.class, new Runnable() {
						public void run() {
						}
					}, getApp().getRenameService(), RenameCategory.MODE);
			if (getApp().getModelConfigurator().getModel()
					.getSupportedOptions().contains(OptionType.AudioMode)) {
				modeSelect.setOnLongClickListener(new OnLongClickListener() {
					public boolean onLongClick(View v) {
						new ExtrasMenu(AVRRemote.this).showMenu(zone
								.getState(AudioMode.class));
						return true;
					}
				});
			}
			viewList.addView(modeSelect, flag);
		} else {
			modeSelect.setEnabled(false);
		}

		screenButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				startDisplay();
			}

			private void startDisplay() {
				Intent intent = new Intent(AVRRemote.this,
						OnScreenDisplayActivity.class);
				intent.putExtra(Zone.INTENT_KEY, zone.getZone().getId());
				startActivity(intent);
			}
		});

		final Button quickButton = (Button) view.findViewById(R.id.btnQuick);
		if (getApp().getModelConfigurator().getModel().hasQuick()) {
			viewList.addView(quickButton, flag);
			quickButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					new QuickMenu(zone.getZone(), getApp().getConnector(),
							AVRRemote.this, getApp().getRenameService()).show();
				}
			});
			quickButton.setOnLongClickListener(new OnLongClickListener() {

				public boolean onLongClick(View v) {
					new QuickMenu(zone.getZone(), getApp().getConnector(),
							AVRRemote.this, getApp().getRenameService())
							.showContextMenu();
					return true;
				}
			});
		} else {
			quickButton.setEnabled(false);
			final LinearLayout buttonLayout = (LinearLayout) view
					.findViewById(R.id.panelMenus);
			if (buttonLayout != null) {
				buttonLayout.removeView(quickButton);
			} else {
				Logger.error("panelMenus.removeQuick button Layout not found",
						null);
			}
		}

		final Button levelButton = (Button) view.findViewById(R.id.btnLevels);
		if (zone.getZone() == Zone.Main
				&& getApp().getModelConfigurator().getModel().hasLevels()) {
			viewList.addView(levelButton, flag);
			levelButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent(AVRRemote.this,
							LevelActivity.class);
					startActivity(intent);
				}
			});
		} else {
			levelButton.setEnabled(false);
		}

		final Button extraButton = (Button) view.findViewById(R.id.btnExtras);
		viewList.addView(extraButton, flag);
		extraButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(AVRRemote.this, OptionActivity.class);
				startActivity(intent);
				// new ExtrasMenu(AVRRemote.this).show();
			}
		});

		return view;
	}

	public static void initVolumeButtons(Context ctx, ZoneState zone,
			final StatusFlag flag, final View view, ViewList viewList,
			final boolean small, AVRTheme avrTheme) {
		final VolumeDisplay volumeDisplay = AVRSettings.getVolumeDisplay(ctx);
		final Volume volume = zone.getState(Volume.class);
		final View btnMinus = (View) view.findViewById(R.id.btnVolDown);
		viewList.addView(btnMinus, flag);
		TouchButtonExtender.extend(btnMinus,
				ctx.getString(R.string.VolumeDown), new Runnable() {
					public void run() {
						volume.down();
					}
				});

		final View btnPlus = (View) view.findViewById(R.id.btnVolUp);
		viewList.addView(btnPlus, flag);
		TouchButtonExtender.extend(btnPlus, ctx.getString(R.string.VolumeUp),
				new Runnable() {
					public void run() {
						volume.up();
					}
				});

		final TextView lblVolume = (TextView) view
				.findViewById(R.id.textVolume);

		avrTheme.setTextColor(lblVolume);
		viewList.addView(lblVolume, flag);
		zone.setListener(new IStateListener<Volume>() {
			public void changedState(Volume state) {
				lblVolume.setText(state
						.getPrintableVolume(volumeDisplay, small));
			}
		}, Volume.class);
	}

	@Override
	protected void onStop() {
		super.onStop();
		getApp().getConnector().removeListener(enableListener);
		getApp().getRenameService().save();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return optionsMenu.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return optionsMenu.onOptionsItemSelected(item);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (currentZoneState != null) {
			if (VolumeKeyHandler.handle(currentZoneState, keyCode, event)) {
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public String toString() {
		return "AVRRemote";
	}

	public ConfigurationAssistant getConfigurationAssistant() {
		return configurationAssistant;
	}

	private View[] zoneViews = new View[4];
	private ZoneState[] zoneStates;
	private VolumeDisplay volumeDisplay = VolumeDisplay.Relative;
	private ConnectionProgressMonitor connectionProgressMonitor;
	private ConfigurationAssistant configurationAssistant;
	private boolean showing;
	private ViewList viewList;
	private TextDisplayHandler textDisplayHandler;
	private OptionsMenu optionsMenu;
	private IConnectionListener enableListener;
	// Achtung, kann "null" sein
	private ZoneState currentZoneState;
	private static final String CURRENT_TAB = "CURRENT_TAB";

}