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
package de.pskiwi.avrremote.menu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.AboutActivity;
import de.pskiwi.avrremote.IActivityShowing;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.RenameActivity;
import de.pskiwi.avrremote.core.ConnectionConfiguration;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState.PowerState;
import de.pskiwi.avrremote.log.FeedbackReporter;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;
import de.pskiwi.avrremote.scan.AVRScanner;

public final class OptionsMenu {

	public OptionsMenu(Activity activity, ModelConfigurator configurator,
			IActivityShowing showing) {
		this.activity = activity;
		this.configurator = configurator;
		this.showing = showing;
	}

	private AVRApplication getApp() {
		return (AVRApplication) activity.getApplication();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		final MenuInflater inflater = activity.getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.itemSettings:
			showSettings();
			break;
		case R.id.itemInfo:
			showAbout();
			break;
		case R.id.itemPDAMenu:
			openPDAMenu();
			break;
		case R.id.itemPower:
			switchPower();
			break;
		case R.id.itemReconnect:
			getApp().getConnector().reconnect();
			break;
		case R.id.itemRename:
			activity.startActivity(new Intent(activity, RenameActivity.class));
			break;	
		case R.id.itemScan:
			AVRScanner.scanIP(activity, showing, getApp(), new Runnable() {
				public void run() {
				}
			}, 0);
			break;

		case R.id.itemScan2:
			AVRScanner.scanIP(activity, showing, getApp(), new Runnable() {
				public void run() {
				}
			}, 1);
			break;
		/**
		 * case R.id.itemTimer: activity.startActivity(new Intent(activity,
		 * TimerSettings.class)); break;
		 */
		case R.id.itemResetAVR:
			resetAVR();
			break;
		case R.id.itemFeedback:
			sendFeedback();
			break;
		case R.id.itemExit:
			android.os.Process.killProcess(android.os.Process.myPid());
			break;
		// case R.id.itemiTach:
		// activity.startActivity(new Intent(activity, ITachActivity.class));
		// break;
		case R.id.itemResetSettings:
			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle(R.string.Confirm);
			builder.setCancelable(true);
			builder.setMessage(R.string.ResetAllSettings);
			builder.setPositiveButton(R.string.OK, new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					getApp().getRenameService().reset();
					AVRSettings.resetSettings(activity);
					getApp().reconfigure();
				}
			});
			builder.setNegativeButton(R.string.No, new OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
				}
			});
			builder.create().show();
			break;

		}
		return false;
	}

	private void sendFeedback() {
		final CheckBox cb = new CheckBox(activity);
		cb.setText(R.string.FeedbackAttachLogs);
		cb.setChecked(true);
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.Confirm);
		builder.setCancelable(true);
		builder.setMessage(R.string.FeedbackQuestion);
		if (Logger.getSDLogger() != null) {
			builder.setView(cb);
		}
		builder.setPositiveButton(R.string.OK, new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				FeedbackReporter.sendFeedback(activity, getApp(),
						cb.isChecked() && Logger.getSDLogger() != null);
			}
		});
		builder.setNegativeButton(R.string.Cancel, new OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
			}
		});

		builder.create().show();

	}

	private void resetAVR() {
		getApp().resetAVR(activity, showing, new Runnable() {
			public void run() {
			}
		});
	}

	public void openPDAMenu() {
		final ConnectionConfiguration connectionConfig = configurator
				.getConnectionConfig();
		if (connectionConfig.isDefined()) {
			final Intent i = new Intent(Intent.ACTION_VIEW);

			String pdaWeb = AVRSettings.getPDAWeb(activity);
			if (pdaWeb.startsWith("/") && pdaWeb.length() > 1) {
				pdaWeb = pdaWeb.substring(1);
			}
			final Uri uri = Uri.parse(connectionConfig.getBaseURL() + pdaWeb);
			i.setData(uri);
			activity.startActivity(i);
		}
	}

	private void showAbout() {
		Intent about = new Intent(activity, AboutActivity.class);
		activity.startActivity(about);
	}

	private void showSettings() {
		activity.startActivity(new Intent(activity, AVRSettings.class));
	}

	private void switchPower() {
		PowerState state = getApp().getAvrState().getZone(Zone.Main)
				.getState(PowerState.class);
		state.switchState();
	}

	private final Activity activity;
	private final ModelConfigurator configurator;
	private final IActivityShowing showing;
}
