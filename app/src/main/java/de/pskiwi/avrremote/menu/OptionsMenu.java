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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.PopupMenu;

import java.net.HttpURLConnection;

import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.AboutActivity;
import de.pskiwi.avrremote.IActivityShowing;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.RenameActivity;
import de.pskiwi.avrremote.core.ConnectionConfiguration;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.http.HTTPSupport;
import de.pskiwi.avrremote.log.FeedbackReporter;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;
import de.pskiwi.avrremote.scan.AVRScanner;

public final class OptionsMenu implements  PopupMenu.OnMenuItemClickListener {

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

	public void showPopup(View v) {
		PopupMenu popup = new PopupMenu(activity, v);
		popup.setOnMenuItemClickListener(this);
		MenuInflater inflater = popup.getMenuInflater();
		inflater.inflate(R.menu.menu, popup.getMenu());
		popup.show();
	}

	public boolean onMenuItemClick(MenuItem item) {
		return onOptionsItemSelected(item);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.itemSettings:
			showSettings();
			break;
		case R.id.itemInfo:
			showAbout();
			break;
		case R.id.itemProjectPage:
			openProjectPage();
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

	public void openProjectPage() {
			final Intent i = new Intent(Intent.ACTION_VIEW);
			final Uri uri = Uri.parse("https://pskiwi.github.io/avr-remote/");
			i.setData(uri);
			activity.startActivity(i);
	}

	public void openPDAMenu() {
		final ConnectionConfiguration connectionConfig = configurator
				.getConnectionConfig();
		if (!connectionConfig.isDefined()) {
			return;
		}
		final String baseURL = connectionConfig.getBaseURL();

		String pdaWeb = AVRSettings.getPDAWeb(activity);
		if (pdaWeb.startsWith("/") && pdaWeb.length() > 1) {
			pdaWeb = pdaWeb.substring(1);
		}
		if (pdaWeb.length() == 0) {
			openURL(baseURL);
			return;
		}

		// Der voreingestellte Pfad /IPHONE/top.asp existiert nur auf den
		// älteren Receivern; neuere liefern ihn nicht aus, und der Browser
		// zeigte dann nur eine Fehlerseite (Feedback zum AVR-1912, 09/2026).
		// Deshalb im Hintergrund prüfen und sonst die Startseite öffnen.
		final String pageURL = baseURL + pdaWeb;

		// Die Prüfung läuft bis in die Timeouts von HTTPSupport, wenn der
		// Receiver aus ist. Solange nur ein Dialog: ohne ihn wirkt der
		// Menüpunkt tot, und jeder weitere Tipp öffnete einen Browser mehr.
		//
		// Der Einzelflug hängt am eigenen Merkmal und nicht am Dialog:
		// contextPaused() räumt den Dialog bei jeder Pause ab, auch bei einer,
		// die der Anwender übersteht - Bildschirm aus und wieder an. Am Dialog
		// gemessen wäre die Sperre danach weg, und der nächste Tipp startete
		// eine zweite Prüfung mit einem zweiten Browser am Ende.
		if (websiteCheckRunning) {
			return;
		}
		websiteCheckRunning = true;
		websiteProgress = ProgressDialog.show(activity,
				activity.getString(R.string.PleaseWait),
				activity.getString(R.string.OpeningReceiverWebsite), true,
				false);

		new Thread("CheckReceiverWebsite") {
			@Override
			public void run() {
				// Nur die eine gemessene Absage verwirft den eingestellten
				// Pfad: der 404 des GoAhead. Keine Antwort, ein Serverfehler
				// oder eine Passwortabfrage belegen nicht, dass die Seite
				// fehlt - dann bleibt es bei dem, was der Anwender
				// ausdrücklich eingestellt hat.
				final int code = HTTPSupport.status(pageURL);
				final String url = code == HttpURLConnection.HTTP_NOT_FOUND ? baseURL
						: pageURL;
				activity.runOnUiThread(new Runnable() {
					public void run() {
						websiteCheckRunning = false;
						// Kann schon weg sein: contextPaused() räumt ihn ab,
						// sobald die Activity pausiert - siehe dort.
						dismissWebsiteProgress();
						openURL(url);
					}
				});
			}
		}.start();
	}

	/**
	 * Fortschrittsdialog der Website-Prüfung schließen, falls einer steht.
	 *
	 * Wird auch aus onPause() der beiden Activities gerufen, die dieses Menü
	 * halten, und das ist der Punkt: die Prüfung läuft bis in die Timeouts von
	 * HTTPSupport, und eine Drehung in diesem Fenster zerstört die Activity mit
	 * dem Dialog daran. Ohne das Abräumen meldet das Framework beim Zerstören
	 * einen WindowLeaked, und der Dialog wäre ohnehin verloren - die neue
	 * Activity hat ein neues OptionsMenu mit leerem Feld.
	 */
	public void contextPaused() {
		dismissWebsiteProgress();
	}

	private void dismissWebsiteProgress() {
		final ProgressDialog progress = websiteProgress;
		websiteProgress = null;
		if (progress == null) {
			return;
		}
		try {
			progress.dismiss();
		} catch (Exception x) {
			// dismiss() wirft, wenn das Fenster der Activity schon weg ist.
			// Der Dialog ist dann mit ihm verschwunden, es bleibt nichts zu tun.
			Logger.debug("dismiss failed " + x);
		}
	}

	private void openURL(String url) {
		final Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		if (activity.isFinishing() || activity.isDestroyed()) {
			// Nach einer Drehung gehört die Anfrage einer Activity, die es
			// nicht mehr gibt. Über den Application-Context geht sie trotzdem
			// auf - sonst hätte der Anwender getippt und bekäme nichts.
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			activity.getApplicationContext().startActivity(i);
			return;
		}
		activity.startActivity(i);
	}

	private void showAbout() {
		Intent about = new Intent(activity, AboutActivity.class);
		activity.startActivity(about);
	}

	private void showSettings() {
		activity.startActivity(new Intent(activity, AVRSettings.class));
	}

	private void switchPower() {
		ZoneState.PowerState state = getApp().getAvrState().getZone(Zone.Main)
				.getState(ZoneState.PowerState.class);
		state.switchState();
	}

	/** Läuft gerade eine Prüfung? Nur vom UI-Thread angefasst. */
	private ProgressDialog websiteProgress;
	/** Läuft gerade eine Website-Prüfung ? Nur vom Main-Thread angefasst. */
	private boolean websiteCheckRunning;

	private final Activity activity;
	private final ModelConfigurator configurator;
	private final IActivityShowing showing;
}
