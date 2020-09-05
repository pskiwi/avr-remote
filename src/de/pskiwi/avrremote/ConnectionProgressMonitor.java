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

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Handler;
import de.pskiwi.avrremote.EnableManager.IStatusListener;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.core.ResilentConnector;
import de.pskiwi.avrremote.log.Logger;

public final class ConnectionProgressMonitor implements IStatusListener {

	public ConnectionProgressMonitor(final AVRRemote activity) {

		this.activity = activity;
	}

	private void startProgressDialog() {
		Logger.info("startProgressDialog visible:"
				+ activity.getConfigurationAssistant().isVisible()
				+ " progress:"
				+ (connectProgress != null)
				+ " showing:"
				+ (connectProgress != null ? connectProgress.isShowing()
						: "n/d"));
		if (activity.getConfigurationAssistant().isVisible()
				|| (connectProgress != null && connectProgress.isShowing())) {
			return;
		}

		Logger.setLocation("startProgressDialog-1");
		connectProgress = ProgressDialog.show(activity, activity.getString(R.string.PleaseWait),
				activity.getString(R.string.TryingToConnect), true, true,
				new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface dialog) {
						Logger.setLocation("startProgressDialog-2");
						checkReceiverStatus();
					}
				});

		handler.postDelayed(new Runnable() {
			public void run() {
				Logger.setLocation("startProgressDialog-3");
				if (!paused && connectProgress != null
						&& connectProgress.isShowing()) {
					Logger.setLocation("startProgressDialog-4");
					connectProgress.dismiss();
					checkReceiverStatus();
				}
			}
		}, ResilentConnector.RECONNECT_WAIT_TIME);
	}

	private void checkReceiverStatus() {

		final ReceiverStatus currentStatus = activity.getApp()
				.getEnableManager().getCurrentStatus();
		Logger.info("checkReceiverStatus " + activity.isShowing() + " / "
				+ currentStatus);
		if (activity.isShowing() && !currentStatus.is(StatusFlag.Connected)
				&& currentStatus.defined(StatusFlag.Connected)) {
			Logger.info("checkStatus");
			activity.getConfigurationAssistant().checkStatus(currentStatus);
		}
	}

	public void checkOnResume() {
		paused = false;
		final ReceiverStatus currentStatus = activity.getApp()
				.getEnableManager().getCurrentStatus();
		if (currentStatus.is(StatusFlag.Connected)) {
			return;
		}
		startProgressDialog();
	}

	public void doPause() {
		paused = true;
	}

	public void statusChanged(ReceiverStatus currentStatus) {
		Logger.info("ConnectionProgressMonitor " + currentStatus);
		// keine Base-Activity verfügbar
		if (paused) {
			return;
		}
		Logger.setLocation("statusChanged-1");
		if (currentStatus.is(StatusFlag.Connected)) {
			if (connectProgress != null && connectProgress.isShowing()) {
				Logger.setLocation("statusChanged-2");
				connectProgress.dismiss();
				connectProgress = null;
			}
		} else {
			startProgressDialog();
		}
	}

	private ProgressDialog connectProgress = null;
	private boolean paused = true;
	private final AVRRemote activity;
	private final Handler handler = new Handler();

}
