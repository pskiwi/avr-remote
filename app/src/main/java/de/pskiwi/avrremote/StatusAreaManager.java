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
import de.pskiwi.avrremote.core.ConnectionConfiguration;
import de.pskiwi.avrremote.http.AVRHTTPClient;
import de.pskiwi.avrremote.http.DeviceDescription;
import de.pskiwi.avrremote.models.ModelConfigurator;
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
										nextXMLUpdate = 0;
										xmlRetried = false;
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
			// Auch ohne Verbindung holen: erreichbar heisst, Ping und Port 80
			// antworten, und genau aus diesem Zustand - Receiver da, Telnet
			// belegt oder stumm - kommen die Berichte, in denen die Angabe am
			// meisten wert ist. Die Stundenbremse in loadXMLStatus() gilt auch
			// hier.
			loadXMLStatus();
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
		if (System.currentTimeMillis() < nextXMLUpdate) {
			return;
		}
		// vermeidet Mehrfachanfragen
		nextXMLUpdate = System.currentTimeMillis() + XML_UPDATE_DELAY;
		new Thread("LoadXMLStatus") {
			@Override
			public void run() {
				try {
					final ModelConfigurator configurator = activity.getApp()
							.getModelConfigurator();
					// Vor readState(), nicht dahinter: das wirft, sobald das
					// HTTP-Scraping scheitert, und der Zustand, aus dem die
					// Beschreibung am meisten wert ist - Receiver da, Telnet
					// belegt oder stumm -, ist oft genau der, in dem das
					// Scraping scheitert. Haengt trotzdem an diesem Thread und
					// nicht an einem eigenen: derselbe Receiver, dieselbe
					// Stundenbremse, und gebraucht wird der Wert nur fuer den
					// Feedback-Bericht.
					final ConnectionConfiguration config = configurator
							.getConnectionConfig();
					configurator.setDeviceDescription(
							DeviceDescription.read(config), config.getIP());
					final AVRXMLInfo state = new AVRHTTPClient(configurator)
							.readState(configurator);
					if (state.isDefined()) {
						configurator.setXMLInfol(state);
					}
					xmlRetried = false;
				} catch (Exception e) {
					// Ein gescheiterter Versuch darf die Stunde nicht
					// verbrauchen: beim Start meldet die App "erreichbar, nicht
					// verbunden", bevor die Verbindung steht, und wenn dieser
					// erste Versuch die Bremse anzieht, fehlen Zonen- und
					// Eingangsnamen danach eine volle Stunde.
					//
					// Aber nur ein einziges Mal. Ein Receiver, der auf Ping und
					// Port 80 antwortet und dessen Web-Oberflaeche trotzdem
					// nichts hergibt - im Standby bei etlichen Modellen so -,
					// steht stabil auf "erreichbar, nicht verbunden", und jede
					// Statusaenderung kommt hier heraus. Ohne die Grenze waere
					// das dauerhaft ein Versuch pro Minute statt einer pro
					// Stunde.
					if (xmlRetried) {
						Logger.info("XML retry failed, waiting the full hour");
					} else {
						xmlRetried = true;
						nextXMLUpdate = System.currentTimeMillis()
								+ XML_RETRY_DELAY;
					}
					Logger.error("Read state failed", e);
				}
			}
		}.start();
	}

	/**
	 * Wann fruehestens wieder gelesen werden darf. Wird auf dem Thread oben
	 * geschrieben und auf dem UI-Thread gelesen.
	 */
	private volatile long nextXMLUpdate;
	/** War der letzte Versuch schon der Nachschlag ? Siehe loadXMLStatus(). */
	private volatile boolean xmlRetried;

	private final Button infoView;
	private final AVRRemote activity;
	// 1h
	private static final int XML_UPDATE_DELAY = 60 * 60 * 1000;
	// 1min, nach einem Fehlversuch
	private static final int XML_RETRY_DELAY = 60 * 1000;

}
