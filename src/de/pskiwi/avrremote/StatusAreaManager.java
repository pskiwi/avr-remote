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

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import de.pskiwi.avrremote.EnableManager.IStatusListener;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.http.AVRHTTPClient;
import de.pskiwi.avrremote.http.AVRXMLInfo;
import de.pskiwi.avrremote.log.Logger;

public final class StatusAreaManager implements IStatusListener {

	public StatusAreaManager(Button infoView, final AVRRemote activity) {
		this.infoView = infoView;
		this.activity = activity;
		infoView.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {

				MenuBuilder menuBuilder = new MenuBuilder(activity,
						"Select Receiver");
				for (int i = 0; i < AVRSettings.MAX_RECEIVERS; i++) {
					final String ip = AVRSettings.getAVRIP(activity, i);
					final String model = AVRSettings.getAVRModel(activity, i);
					final int currentIndex = i;
					if (ip.length() > 0) {
						menuBuilder.add(
								"[" + (i + 1) + "] " + model + " " + ip,
								new Runnable() {
									public void run() {
										// neu laden der Eingänge ermöglichen
										lastXMLUpdate = -1;
										final AVRApplication app = activity
												.getApp();
										app.getModelConfigurator()
												.selectReceiver(currentIndex);
										app.reconfigure();
									}
								});
					}
				}
				menuBuilder.showMenu();
			}
		});
	}

	public void statusChanged(ReceiverStatus currentStatus) {
		if (currentStatus.is(StatusFlag.Connected)) {
			handleConnected(currentStatus);
		} else {
			handleDisconnected(currentStatus);
		}

		infoView.setText(activity.getApp().getModelConfigurator().getModel()
				.getName()
				+ " - " + infoView.getText());

		if (currentStatus.is(StatusFlag.Logging)) {
			infoView.setText(infoView.getText() + " - Logging");
		}
	}

	private void handleDisconnected(ReceiverStatus currentStatus) {
		Logger.info("handleDisconnected " + currentStatus);
		infoView.setBackgroundResource(R.drawable.connection_problem);
		if (currentStatus.is(StatusFlag.Reachable)) {
			infoView.setText(R.string.DisconnectedReachable);

		} else {
			infoView.setText(R.string.DisconnectedUnreachable);
		}
	}

	private void handleConnected(ReceiverStatus currentStatus) {
		loadXMLStatus();

		if (currentStatus.is(StatusFlag.Power)) {
			infoView.setText(R.string.ConnectedPowerOn);
			infoView.setBackgroundResource(R.drawable.connected_power);
		} else {
			infoView.setText(R.string.ConnectedPowerOff);
			infoView.setBackgroundResource(R.drawable.connected_poweroff);
		}
	}

	private void loadXMLStatus() {
		if (System.currentTimeMillis() - lastXMLUpdate < XML_UPDATE_DELAY) {
			return;
		}
		// vermeidet Mehrfachanfragen
		lastXMLUpdate = System.currentTimeMillis();
		new Thread("LoadXMLStatus") {
			@Override
			public void run() {
				try {
					final AVRXMLInfo state = new AVRHTTPClient(activity
							.getApp().getModelConfigurator())
							.readState(activity.getApp().getModelConfigurator());
					if (state.isDefined()) {
						activity.getApp().getModelConfigurator()
								.setXMLInfol(state);
					}

				} catch (Exception e) {
					Logger.error("Read state failed", e);
				}
			}
		}.start();
	}

	private long lastXMLUpdate = -1;

	private final Button infoView;
	private final AVRRemote activity;
	// 1h
	private static final int XML_UPDATE_DELAY = 60 * 60 * 1000;

}
