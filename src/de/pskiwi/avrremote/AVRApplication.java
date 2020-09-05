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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.AVRState;
import de.pskiwi.avrremote.core.IGUIExecutor;
import de.pskiwi.avrremote.core.MacroManager;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.core.ResilentConnector;
import de.pskiwi.avrremote.core.SenderBridge;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState.MuteState;
import de.pskiwi.avrremote.core.ZoneState.Volume;
import de.pskiwi.avrremote.core.display.DisplayManager;
import de.pskiwi.avrremote.http.AVRHTTPClient;
import de.pskiwi.avrremote.log.ADBLogger;
import de.pskiwi.avrremote.log.FeedbackReporter;
import de.pskiwi.avrremote.log.ILogger;
import de.pskiwi.avrremote.log.LogMode;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.log.SDLogger;
import de.pskiwi.avrremote.models.ModelConfigurator;

/** Global State */
public final class AVRApplication extends Application {

	private BroadcastReceiver wifiEventReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			Logger.info("Broadcast " + intent);
			final boolean newConnected = connectivityManager.getNetworkInfo(
					ConnectivityManager.TYPE_WIFI).isConnected();
			enableManager.setStatus(StatusFlag.WLAN, newConnected);
			Logger.info("AVRApplication.Wifi connected:" + newConnected + " "
					+ activeHandler + " " + enableManager);

			// Nur falls aktiv, Reconnect auslösen
			if (activeHandler.isActive()) {
				if (newConnected != connected) {
					connected = newConnected;
					Logger.info("Wifi-Connection State changed:" + newConnected);
					if (connected) {
						connector.triggerReconnect();
					}
				}
			}
		}

		private boolean connected;
	};

	private final BroadcastReceiver standByEventReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Logger.info("System StandBy ...");
			connector.stop();
		}

	};

	@Override
	public void onCreate() {

		Logger.setDelegate(ADBLogger.INSTANCE);

		final Handler handler = new Handler();
		final UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread
				.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

			public void uncaughtException(final Thread thread,
					final Throwable ex) {
				Logger.error(
						"caught unexpected exception in Thread "
								+ thread.getId() + "/" + thread.getName(), ex);
				new FeedbackReporter(AVRApplication.this, thread, ex)
						.saveCrash();
				if (defaultUncaughtExceptionHandler != null) {
					defaultUncaughtExceptionHandler.uncaughtException(thread,
							ex);
				}
			}
		});

		connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		Logger.info("init handler ...");

		updateDebugMode();

		Logger.info(AVRSettings.getAll(this));

		avrTheme = new AVRTheme(this);

		renameService = new RenameService(this);

		modelConfigurator = new ModelConfigurator(this, renameService);

		macroManager = new MacroManager(this);
		macroGUI = new MacroGUI(macroManager);

		final SenderBridge senderBridge = new SenderBridge();
		Logger.info("init state ...");
		displayManager = new DisplayManager();
		avrState = new AVRState(senderBridge, enableManager,
				new IGUIExecutor() {
					public void execute(Runnable r) {
						handler.post(r);
					}
				}, displayManager, modelConfigurator);
		avrState.setActiveZoneCount(getModelConfigurator().getZoneCount());

		connector = new ResilentConnector(enableManager, avrState,
				modelConfigurator);
		senderBridge.setDelegate(connector);
		displayManager.setAvrState(avrState);

		activeHandler = new ActiveHandler(connector);

		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(wifiEventReceiver, filter);

		IntentFilter standbyFilter = new IntentFilter();
		standbyFilter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(standByEventReceiver, standbyFilter);

		statusbarManager = new StatusbarManager(this);

		// nach Init
		renameService.checkDefaults(this);

		Logger.info("init gui ...");
	}

	private void updateDebugMode() {
		LogMode oldMode = debugMode;
		debugMode = AVRSettings.getDebugMode(this);
		if (oldMode != debugMode) {
			Log.i(ADBLogger.TAG, "set logger from " + oldMode + " to "
					+ debugMode);
			switch (debugMode) {
			case ADB:
				enableManager.setStatus(StatusFlag.Logging, false);
				Logger.setDelegate(ADBLogger.INSTANCE);
				break;
			case LogFile:
				enableManager.setStatus(StatusFlag.Logging, true);
				Logger.setDelegate(new SDLogger(this));
				break;
			default:
				enableManager.setStatus(StatusFlag.Logging, false);
				Logger.setDelegate(ILogger.NULL_LOGGER);
			}
		}
	}

	public AVRState getAvrState() {
		return avrState;
	}

	public ResilentConnector getConnector() {
		return connector;
	}

	public void reconfigure() {
		Logger.info("reconfigure ...");
		modelConfigurator.update();
		connector.reconfigure(this);
		enableManager.reinitListener();
		
		renameService.checkDefaults(this);
		statusbarManager.update();

		avrState.updateAll();
		
		updateDebugMode();
		Logger.info("reconfigure ...");
	}

	public void activityResumed(Context context) {
		activeHandler.contextResumed(context);
	}

	public ActiveHandler getActiveHandler() {
		return activeHandler;
	}

	public void activityPaused(Context context) {
		activeHandler.contextPaused(context);
	}

	public EnableManager getEnableManager() {
		return enableManager;
	}

	public void toggleMute() {
		final int zoneCount = modelConfigurator.getZoneCount();
		for (Zone z : Zone.values()) {
			final int zoneNumber = z.getZoneNumber();
			if (zoneNumber < zoneCount) {
				MuteState state = avrState.getState(z, MuteState.class);
				Logger.info("toggle mute :" + state);
				state.switchState();
			}
		}
	}

	public void adjustVolume(boolean plus) {
		final int zoneCount = modelConfigurator.getZoneCount();
		for (Zone z : Zone.values()) {
			final int zoneNumber = z.getZoneNumber();
			if (zoneNumber < zoneCount) {
				Volume state = avrState.getState(z, Volume.class);
				Logger.info("toggle mute :" + state);
				if (plus) {
					state.up();
				} else {
					state.down();
				}
			}
		}
	}

	public void resetAVR(Activity activity, final IActivityShowing showing,
			final Runnable runnable) {
		final ProgressDialog progress = ProgressDialog.show(activity,
				getString(R.string.PleaseWait),
				getString(R.string.ResettingReceiver), true, true);

		final AtomicBoolean canceled = new AtomicBoolean();
		progress.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				canceled.set(true);
			}
		});
		progress.setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				canceled.set(true);
			}
		});
		final Handler handler = new Handler();
		Logger.setLocation("resetAVR-1");
		final Runnable runReconfigure = new Runnable() {
			public void run() {
				Logger.setLocation("resetAVR-2");
				if (!canceled.get()) {
					Logger.setLocation("resetAVR-3");
					if (showing.isShowing()) {
						Logger.setLocation("resetAVR-4");
						progress.dismiss();
						reconfigure();
						runnable.run();
					}
				}
			}
		};
		new AVRHTTPClient(modelConfigurator).doBackgroundReset(new Runnable() {
			public void run() {
				handler.post(runReconfigure);
			}
		});
	}

	public ModelConfigurator getModelConfigurator() {
		return modelConfigurator;
	}

	public RenameService getRenameService() {
		return renameService;
	}

	public DisplayManager getDisplayManager() {
		return displayManager;
	}

	public StatusbarManager getStatusbarManager() {
		return statusbarManager;
	}

	public AVRTheme getAvrTheme() {
		return avrTheme;
	}

	public MacroManager getMacroManager() {
		return macroManager;
	}

	public MacroGUI getMacroGUI() {
		return macroGUI;
	}

	private MacroManager macroManager;
	private MacroGUI macroGUI;
	private AVRTheme avrTheme;
	private StatusbarManager statusbarManager;
	private DisplayManager displayManager;
	private RenameService renameService;
	private LogMode debugMode;
	private EnableManager enableManager = new EnableManager();
	private ActiveHandler activeHandler;
	private AVRState avrState;
	private ConnectivityManager connectivityManager;
	private ResilentConnector connector;
	private ModelConfigurator modelConfigurator;

}
