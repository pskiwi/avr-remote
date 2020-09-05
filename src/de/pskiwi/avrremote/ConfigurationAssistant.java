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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.scan.AVRScanner;

public final class ConfigurationAssistant {

	public ConfigurationAssistant(final AVRRemote ctx, final AVRApplication app) {
		this.ctx = ctx;
		this.app = app;
	}

	public void checkSetup() {
		// keine IP, direkt
		Logger.setLocation("checkSetup-1");
		if (!app.getModelConfigurator().getConnectionConfig().isDefined()) {
			Logger.setLocation("checkSetup-2");
			showSetupDialog();
		}
	}

	private void showSetupDialog() {
		final AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		visible.set(true);
		builder.setCancelable(true);
		builder.setTitle(R.string.SetupMessageModel);
		Logger.setLocation("showSetupDialog-1");

		builder.setItems(R.array.modelNames, new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				final String[] names = ctx.getResources().getStringArray(
						R.array.modelNames);
				if (which >= 0 && which < names.length) {
					final String selectedModel = names[which];
					Logger.info("setup model [" + selectedModel + "]");
					AVRSettings.setAVRModel(ctx, selectedModel, 0);
					visible.set(false);
					showIPDialog(true);
				}
			}
		});

		builder.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				Logger.setLocation("showSetupDialog-4");
				visible.set(false);

			}
		});
		builder.create().show();
	}

	public void checkStatus(ReceiverStatus currentStatus) {
		if (currentStatus.is(StatusFlag.WLAN) || EmulationDetector.isEmulator()) {
			Logger.info("checkStatus WLAN/EMU");
			if (currentStatus.is(StatusFlag.Reachable)) {
				checkReset();
			} else {
				showIPDialog(false);
			}
		} else {
			Logger.info("checkStatus !WLAN/EMU "
					+ currentStatus.defined(StatusFlag.WLAN));
			if (currentStatus.defined(StatusFlag.WLAN)) {
				alertNoWLan();
			}
		}

	}

	private void showIPDialog(final boolean setupMode) {
		if (visible.get() || !ctx.isShowing()) {
			return;
		}
		visible.set(true);
		final AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setCancelable(true);
		if (setupMode) {
			builder.setTitle(R.string.SetupTitle);
			builder.setMessage(R.string.SetupMessageIP);
		} else {
			builder.setTitle(R.string.ConfigProblem);
			builder.setMessage(R.string.NoIpDefined);
		}
		builder.setInverseBackgroundForced(true);
		Logger.setLocation("showIPDialog-1");
		builder.setPositiveButton(ctx.getString(R.string.AutoScan),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.setLocation("showIPDialog-2");
						AVRScanner.scanIP(ctx, ctx, app, new Runnable() {
							public void run() {
								Logger.setLocation("showIPDialog-3");
								visible.set(false);
							}
						}, 0);
					}
				});
		builder.setNegativeButton(ctx.getString(R.string.ManualSettings),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.setLocation("showIPDialog-4");
						visible.set(false);
						ctx.startActivity(new Intent(ctx, AVRSettings.class));
					}
				});
		builder.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				Logger.setLocation("showIPDialog-5");
				visible.set(false);
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void alertNoWLan() {
		if (visible.get() || !ctx.isShowing()) {
			return;
		}
		visible.set(true);
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(ctx.getString(R.string.ConnectivityProblem));
		builder.setMessage(ctx.getString(R.string.WLANNotAvtive));
		builder.setInverseBackgroundForced(true);
		Logger.setLocation("alertNoWLan-1");

		builder.setNeutralButton(R.string.OK,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.setLocation("alertNoWLan-2");
						visible.set(false);
					}
				});

		builder.setPositiveButton(R.string.WiFiSettings,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.setLocation("alertNoWLan-3");
						visible.set(false);
						final Intent intent = new Intent(
								android.provider.Settings.ACTION_WIRELESS_SETTINGS);
						ctx.startActivity(intent);
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void checkReset() {
		if (visible.get() || !ctx.isShowing()) {
			return;
		}
		visible.set(true);
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(ctx.getString(R.string.ConnectivityProblem));
		builder.setMessage(ctx.getString(R.string.AVRReset));
		builder.setInverseBackgroundForced(true);
		builder.setCancelable(false);
		Logger.setLocation("checkReset-1");
		builder.setPositiveButton(ctx.getString(R.string.TryAutoRestart),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.setLocation("checkReset-2");
						app.resetAVR(ctx, ctx, new Runnable() {
							public void run() {
								Logger.setLocation("checkReset-3");
								visible.set(false);
							}
						});
					}
				});
		builder.setNegativeButton(ctx.getString(R.string.TryToConnect),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.setLocation("checkReset-4");
						visible.set(false);
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public boolean isVisible() {
		return visible.get();
	}

	private final AVRRemote ctx;
	private final AVRApplication app;
	private final AtomicBoolean visible = new AtomicBoolean();
}
