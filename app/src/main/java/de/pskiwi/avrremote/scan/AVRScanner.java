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
package de.pskiwi.avrremote.scan;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.LinkAddress;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.Toast;

import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.EmulationDetector;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.http.DeviceDescription;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelSuggestion;

/** Lokales Netz nach möglichen AVRs durchsuchen */
public final class AVRScanner {

	/**
	 * Wie ein Treffer zustande kam. Steht in der Auswahlliste hinter der
	 * Adresse, weil die beiden Wege verschieden viel aussagen: SSDP heisst, das
	 * Geraet hat sich selbst als UPnP-Geraet gemeldet; der Sweep heisst nur,
	 * dass an dieser Adresse die passenden Ports offen sind. Wer zwischen zwei
	 * Eintraegen zu waehlen hat, entscheidet damit anders.
	 *
	 * Die Kuerzel sind bewusst technisch und damit in beiden Sprachen gleich -
	 * die fehlenden Uebersetzungen sind schon ein eigenes Thema.
	 */
	public enum FoundBy {
		SSDP("SSDP"), SWEEP("Scan");

		private FoundBy(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

		private final String label;
	}

	public final static class ScanResult {

		/**
		 * Mit Beschreibung steht vorn, was das Geraet ueber sich sagt - auch
		 * wenn der Name in der Modell-Liste fehlt, ist er die beste Auskunft,
		 * welcher Eintrag welcher ist. Ohne bleibt es beim Hostnamen; der ist,
		 * was der Router daraus macht, und taugt nicht zum Abgleich.
		 */
		public ScanResult(InetAddress address, FoundBy foundBy,
				DeviceDescription description, String suggestedModel) {
			this.address = address;
			this.ip = address.getHostAddress();
			this.description = description;
			this.suggestedModel = suggestedModel;
			final String label = describe(description);
			this.info = (label != null ? label + " \u2013 " + ip : ip + " / "
					+ address.getHostName())
					+ "  (" + foundBy.getLabel() + ")";
		}

		/** "Denon AVR-3310", oder null wenn das Geraet nichts gesagt hat. */
		public static String describe(DeviceDescription description) {
			if (description == null || description.getModelName() == null) {
				return null;
			}
			final String manufacturer = description.getManufacturer();
			return manufacturer != null ? manufacturer + " "
					+ description.getModelName() : description.getModelName();
		}

		/** Der Eintrag aus {@code @array/modelNames}, oder null. */
		public String getSuggestedModel() {
			return suggestedModel;
		}

		public DeviceDescription getDescription() {
			return description;
		}

		@Override
		public String toString() {
			return info;
		}

		final InetAddress address;
		final String info;
		final String ip;
		private final DeviceDescription description;
		private final String suggestedModel;
	}

	/**
	 * Was nach der Auswahl im Suchlauf geschieht. Bekommt den gewaehlten
	 * Treffer, oder null, wenn der Suchlauf ohne Auswahl endete - Fehler oder
	 * Abbruch. Nicht gerufen, wenn der Suchlauf gar nicht erst lief, weil
	 * zuerst nach der Permission gefragt wurde.
	 */
	public interface ISelectionListener {
		void selected(ScanResult result);
	}

	public interface IScanResultHandler {
		void error(String cause);

		void finished(List<ScanResult> result);
	}

	private final static class ScanThread extends Thread {

		public ScanThread(List<InetAddress> addresses, FoundBy foundBy,
				Map<InetAddress, List<String>> locations, String[] modelNames) {
			super("Scan-Thread [" + addresses.get(0).getHostAddress() + "+"
					+ addresses.size() + "]");
			this.addresses = addresses;
			this.foundBy = foundBy;
			this.locations = locations;
			this.modelNames = modelNames;
		}

		@Override
		public void run() {
			for (InetAddress ia : addresses) {
				try {
					if (AVRTargetTester.testAddress(ia, false)) {
						final DeviceDescription description = readDescription(ia);
						result.add(new ScanResult(ia, foundBy, description,
								suggest(description)));
					}
				} catch (Exception e) {
					Logger.error("scan exception", e);
				}
			}
		}

		/**
		 * Erst die LOCATIONs aus SSDP - die sind autoritativ -, dann der feste
		 * Port 8080, auf dem auch {@link DeviceDescription#read} sucht. Ein
		 * Sweep-Treffer kennt nur den. Nur fuer bestaetigte Receiver, also
		 * eine Handvoll Abrufe.
		 */
		private DeviceDescription readDescription(InetAddress ia) {
			final List<String> urls = new ArrayList<String>();
			final List<String> fromSSDP = locations.get(ia);
			if (fromSSDP != null) {
				urls.addAll(fromSSDP);
			}
			final String fallback = DeviceDescription.defaultURL(ia
					.getHostAddress());
			if (!urls.contains(fallback)) {
				urls.add(fallback);
			}
			for (String url : urls) {
				final DeviceDescription description = DeviceDescription
						.read(url);
				if (description != null && description.getModelName() != null) {
					return description;
				}
			}
			return null;
		}

		private String suggest(DeviceDescription description) {
			if (description == null) {
				return null;
			}
			final String model = ModelSuggestion.match(
					description.getModelName(), modelNames);
			if (model == null) {
				// Das Signal, auf das TODO.md wartet: ein Geraet, dessen
				// Name in der Liste fehlt oder anders geschrieben ist.
				Logger.info("no list entry for reported model ["
						+ description.getModelName() + "]");
			}
			return model;
		}

		/**
		 * Der Thread kann noch laufen: das {@code join(JOIN_TIMEOUT)} in
		 * {@link AVRScanner#testAll} darf ablaufen, und in einem /24 hat jeder
		 * Thread 16 Adressen zu je bis zu 2,25 s - Ping plus vier
		 * Connect-Timeouts. Ohne die Sperre liest der Aufrufer dann eine Liste,
		 * in die nebenher geschrieben wird, und {@code addAll()} wirft eine
		 * ConcurrentModificationException oder sieht einen halben Stand.
		 */
		public List<ScanResult> getResult() {
			synchronized (result) {
				return new ArrayList<ScanResult>(result);
			}
		}

		// Der Mutex der Huelle ist die Huelle selbst - dasselbe Schloss, das
		// getResult() nimmt.
		private final List<ScanResult> result = Collections
				.synchronizedList(new LinkedList<ScanResult>());
		private final List<InetAddress> addresses;
		private final FoundBy foundBy;
		private final Map<InetAddress, List<String>> locations;
		private final String[] modelNames;

	}

	public AVRScanner(Activity ctx, AVRApplication app) {
		this.ctx = ctx;
		this.app = app;
	}

	public void scan(final IScanResultHandler handler) throws Exception {
		Logger.info("Scan: start");
		// emulator - HARDWARE ist das, woran EmulationDetector ihn erkennt
		Logger.info(Build.PRODUCT + "/" + Build.DEVICE + "/" + Build.HARDWARE
				+ " emulator:" + EmulationDetector.isEmulator());

		// Ohne die Permission kommt unter Local Network Protection weder ein
		// SSDP-Paket raus noch ein TCP-Connect durch - und zwar lautlos, per
		// Timeout. Dann lieber hier abbrechen und sagen warum, statt den
		// Anwender zehn Sekunden auf "kein Receiver gefunden" warten zu lassen.
		if (AVRSettings.isLocalNetworkBlocked(ctx)) {
			Logger.info("Scan: local network permission missing");
			handler.error(ctx.getString(R.string.LocalNetworkPermission));
			return;
		}

		final LocalNetwork localNetwork = app.getLocalNetwork();
		final LinkAddress address = localNetwork.getIPv4();
		final InetAddress ip;
		final int prefixLength;
		if (address != null) {
			ip = address.getAddress();
			prefixLength = address.getPrefixLength();
		} else if (EmulationDetector.isEmulator()) {
			// Je nach Image meldet der Emulator kein WLAN. Ein Netz annehmen,
			// damit der Scan-Pfad dort ueberhaupt durchlaufen werden kann.
			ip = InetAddress.getByName("192.168.10.1");
			prefixLength = 24;
		} else {
			final String errorCause = localNetwork.getErrorCause();
			Logger.info("Scan: " + errorCause);
			handler.error(errorCause);
			return;
		}
		Logger.info("Scan: " + ip.getHostAddress() + "/" + prefixLength);

		Logger.setLocation("scan-1");
		final ProgressDialog progress = ProgressDialog.show(ctx,
				ctx.getString(R.string.PleaseWait),
				ctx.getString(R.string.ScanningNetwork), true, false);
		final AsyncTask<InetAddress, Integer, List<ScanResult>> asyncTask = new AsyncTask<InetAddress, Integer, List<ScanResult>>() {

			@Override
			protected List<ScanResult> doInBackground(InetAddress... params) {
				Logger.setLocation("scan-1a");
				try {
					// Erst fragen, dann suchen: SSDP hat die Antwort in
					// Sekunden, der Sweep braucht Minuten und trifft ohnehin
					// nur Netze ab /24. Die Antworten kommen von allem, was
					// UPnP spricht, und laufen deshalb durch denselben Filter
					// wie der Sweep.
					final Map<InetAddress, List<String>> answered = SSDPDiscovery
							.search();
					final List<ScanResult> viaSSDP = testAll(
							new ArrayList<InetAddress>(answered.keySet()),
							FoundBy.SSDP, answered);
					if (!viaSSDP.isEmpty()) {
						return viaSSDP;
					}
					Logger.info("SSDP found nothing, falling back to sweep");
					// Alles ab /24 liegt im letzten Oktett und ist damit in
					// einem Durchgang absuchbar; darunter waeren es mindestens
					// 512 Adressen. Die Grenze gilt aber nur fuer den Sweep:
					// SSDP fragt das ganze Netz mit einem Paket und ist damit
					// von der Subnetzgroesse unabhaengig - in einem /16 ist es
					// das einzige, was ueberhaupt etwas finden kann.
					if (prefixLength < 24) {
						Logger.info("no sweep below /24");
						return Collections.emptyList();
					}
					return scanNetwork(ip, prefixLength);
				} catch (Exception e) {
					e.printStackTrace();
					Logger.error("Scan failed", e);
				}
				return Collections.emptyList();
			}

			@Override
			protected void onPostExecute(List<ScanResult> result) {
				Logger.setLocation("scan-2");
				// Nicht "wird die Activity angezeigt", sondern "gibt es ihr
				// Fenster noch": der Sweep laeuft Minuten, und wer
				// zwischendurch auf Home drueckt, kam sonst zu einem
				// Fortschrittsdialog zurueck, der sich nicht abbrechen laesst,
				// ohne Ergebnis - und der Assistent wartete fuer den Rest
				// seines Lebens auf runFinished. Ein Dialog darf auch im
				// Hintergrund aufgehen, er ist dann beim Zurueckkommen da.
				// Nur ein zerstoertes Fenster nimmt weder dismiss() noch
				// show() an.
				if (ctx.isDestroyed()) {
					Logger.info("Scan: finished after activity is gone");
					return;
				}
				Logger.setLocation("scan-3");
				progress.dismiss();
				// Ein Fenster, das noch steht, aber geht: den Dialog schliessen,
				// sonst bleibt er als geleaktes Fenster liegen - nur nichts
				// Neues mehr aufmachen
				if (ctx.isFinishing()) {
					Logger.info("Scan: finished while activity is finishing");
					return;
				}
				if (result.size() > 0) {
					handler.finished(result);
				} else if (prefixLength < 24) {
					// SSDP hat nichts gefunden, und der Sweep ist hier
					// nicht gelaufen - das ist der Unterschied zu
					// "nichts gefunden"
					handler.error(ctx
							.getString(R.string.AutoScanNotSupported));
				} else {
					handler.error(ctx.getString(R.string.NoIpFound));
				}
			}
		};
		asyncTask.execute();
	}

	/**
	 * Adressen auf SCAN_THREADS Threads verteilt durch {@link AVRTargetTester}
	 * schicken. Beide Suchwege enden hier, und zwar aus demselben Grund: ein
	 * Kandidat kostet bis zu vier Connect-Timeouts, und nacheinander summiert
	 * sich das auch bei den wenigen SSDP-Antworten eines Haushalts voller
	 * UPnP-Geraete auf mehr, als der Sweep insgesamt braucht.
	 */
	private List<ScanResult> testAll(List<InetAddress> addresses,
			FoundBy foundBy, Map<InetAddress, List<String>> locations)
			throws InterruptedException {
		if (addresses.isEmpty()) {
			return Collections.emptyList();
		}
		final int[][] ranges = splitRange(0, addresses.size(), SCAN_THREADS);
		final ScanThread[] threads = new ScanThread[ranges.length];
		final String[] modelNames = ctx.getResources().getStringArray(
				R.array.modelNames);
		for (int i = 0; i < ranges.length; i++) {
			threads[i] = new ScanThread(addresses.subList(ranges[i][0],
					ranges[i][1]), foundBy, locations, modelNames);
			threads[i].start();
		}
		// Erst auf alle warten, dann einsammeln. Zusammen in einer Schleife
		// bekaeme der erste Thread nur die ersten JOIN_TIMEOUT Millisekunden,
		// der zweite die naechsten und so fort - waehrend ein /24 jedem 16
		// Adressen zu je bis zu 2,25 s gibt. Wer laenger braucht als sein
		// eigenes Zeitfenster, haette seine Funde also verloren, obwohl der
		// Suchlauf insgesamt noch minutenlang weiterlaeuft: ein Receiver auf
		// einer niedrigen Adresse waere als "nicht gefunden" gemeldet worden.
		for (int i = 0; i < threads.length; i++) {
			threads[i].join(JOIN_TIMEOUT);
		}
		final List<ScanResult> result = new ArrayList<ScanResult>();
		for (int i = 0; i < threads.length; i++) {
			if (threads[i].isAlive()) {
				// Das Ergebnis ist dann unvollstaendig, und in einem
				// eingeschickten Log ist das sonst nicht zu sehen.
				Logger.info("scan timed out: " + threads[i].getName());
			}
			result.addAll(threads[i].getResult());
		}
		Logger.info("found " + result.size() + " receiver(s) via "
				+ foundBy.getLabel());
		return result;
	}

	private List<ScanResult> scanNetwork(InetAddress i4, int prefixLength)
			throws Exception {
		final String[] parts = i4.getHostAddress().split("\\.");
		final String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
		final int[] hostRange = hostRange(Integer.parseInt(parts[3]),
				prefixLength);
		final List<InetAddress> addresses = new ArrayList<InetAddress>();
		for (int i = hostRange[0]; i < hostRange[0] + hostRange[1]; i++) {
			// Literal, also kein DNS - das hier kostet nichts.
			addresses.add(Inet4Address.getByName(prefix + i));
		}
		return testAll(addresses, FoundBy.SWEEP,
				Collections.<InetAddress, List<String>> emptyMap());
	}

	/**
	 * Erster zu scannender Wert im letzten Oktett und Anzahl, als {from, count}.
	 * Nur fuer prefixLength >= 24 definiert - darunter reicht das letzte Oktett
	 * nicht aus.
	 *
	 * Netzadresse und Broadcast bleiben aussen vor: auf ihnen kann kein Receiver
	 * sitzen, und die Broadcast-Adresse kostet nicht nur die vergebliche Probe -
	 * ein ICMP-Echo dorthin erreicht jedes Geraet im Netz. Ein /31 hat beides
	 * nicht (RFC 3021, Punkt-zu-Punkt) und ein /32 ist eine einzelne Adresse,
	 * deshalb die Grenze bei count > 2.
	 */
	static int[] hostRange(int lastOctet, int prefixLength) {
		final int hostBits = 32 - prefixLength;
		final int mask = (0xff << hostBits) & 0xff;
		final int network = lastOctet & mask;
		final int count = 1 << hostBits;
		return count > 2 ? new int[] { network + 1, count - 2 } : new int[] {
				network, count };
	}

	/**
	 * Teilt [from, from+count) in hoechstens parts halboffene Bereiche auf.
	 *
	 * Reine Funktion, weil hier bis August 2026 ein Off-by-one sass: die
	 * Bereiche liefen [i*16, (i+1)*16-1), also 0..14, 16..30, ... - jede 16.
	 * Adresse (.15, .31, ...) wurde nie geprueft. Ein Receiver auf einer davon
	 * war per Scan nicht zu finden. ScanRangeTest nagelt das fest.
	 */
	static int[][] splitRange(int from, int count, int parts) {
		final int n = Math.min(parts, count);
		final int[][] result = new int[n][];
		for (int i = 0; i < n; i++) {
			result[i] = new int[] { from + (int) ((long) count * i / n),
					from + (int) ((long) count * (i + 1) / n) };
		}
		return result;
	}

	public static void scanIP(final Activity ctx, final AVRApplication app,
			final Runnable runFinished, final int nr) {
		scanIP(ctx, app, runFinished, nr, null);
	}

	/**
	 * Wie oben; {@code listener} (darf null sein) kommt nach
	 * {@code runFinished} dran, damit der Assistent seinen naechsten Dialog
	 * aufmachen kann, wenn der Suchlauf sich schon abgemeldet hat.
	 */
	public static void scanIP(final Activity ctx, final AVRApplication app,
			final Runnable runFinished, final int nr,
			final ISelectionListener listener) {
		try {
			// Der Scan ist ueber das Menue und den Einrichtungs-Assistenten
			// erreichbar, ohne dass AVRRemote.onCreate gelaufen waere.
			if (AVRSettings.requestLocalNetworkPermission(ctx)) {
				// Ein Dialog ist aufgegangen und die Antwort kommt asynchron.
				// Jetzt zu scannen hiesse, die Permission als fehlend zu sehen
				// und den Fehlerdialog unter den System-Dialog zu legen. Also
				// hier aufhoeren: der Anwender beantwortet erst die Frage und
				// startet den Suchlauf danach neu. Ist die Permission
				// endgueltig abgelehnt, geht kein Dialog auf, und dann laeuft
				// der Scan weiter bis zu seinem eigenen Hinweis - siehe
				// requestLocalNetworkPermission().
				// Aus dem Optionsmenue fuehrt dieser Rueckweg sonst ins Leere:
				// dort ist runFinished leer, der Anwender beantwortet die
				// Frage und sieht danach nichts - kein Fortschritt, keine
				// Meldung, keine Liste. Der Hinweis legt sich ueber den
				// System-Dialog und sagt, was zu tun ist.
				Logger.info("Scan: asked for local network permission first");
				Toast.makeText(ctx, R.string.ScanAfterPermission,
						Toast.LENGTH_LONG).show();
				runFinished.run();
				return;
			}
			final IScanResultHandler resultHandler = new AVRScanner.IScanResultHandler() {

				public void finished(final List<ScanResult> result) {
					final String[] values = new String[result.size()];
					for (int i = 0; i < result.size(); i++) {
						values[i] = result.get(i).info;
					}
					new AlertDialog.Builder(ctx)
							.setCancelable(true)
							.setOnCancelListener(new OnCancelListener() {

								public void onCancel(DialogInterface dialog) {
									runFinished.run();
									reportSelection(listener, null);
								}
							})
							.setTitle(R.string.SelectAVRIP)
							.setItems(values,
									new DialogInterface.OnClickListener() {
										public void onClick(
												DialogInterface dialog,
												int which) {
											ScanResult selected = null;
											try {
												if (which >= 0) {
													selected = result
															.get(which);
													Logger.info("selected ["
															+ selected.ip
															+ "] suggested ["
															+ selected
																	.getSuggestedModel()
															+ "]");
													AVRSettings.setAVRIP(ctx,
															selected.ip, nr);
													app.reconfigure();
												}
											} finally {
												runFinished.run();
											}
											reportSelection(listener, selected);
										}
									}).show();

				}

				public void error(String cause) {
					AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
					builder.setTitle(R.string.ScanFailed);
					builder.setMessage(cause);
					builder.setInverseBackgroundForced(true);
					builder.setNeutralButton(R.string.OK,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									runFinished.run();
									reportSelection(listener, null);
								}
							});
					// Auch der Abbruch muss runFinished erreichen: der
					// Assistent haelt darin fest, dass wieder kein Dialog
					// sichtbar ist, und bliebe sonst fuer den Rest seines
					// Lebens stumm - dieser Dialog ist abbrechbar.
					builder.setOnCancelListener(new OnCancelListener() {
						public void onCancel(DialogInterface dialog) {
							runFinished.run();
							reportSelection(listener, null);
						}
					});
					AlertDialog alert = builder.create();
					alert.show();
				}
			};

			final AVRScanner scan = new AVRScanner(ctx, app);
			scan.scan(resultHandler);

		} catch (Exception e) {
			// dito: ohne das bleibt der Assistent an einem Suchlauf haengen,
			// der gar nicht erst angelaufen ist
			runFinished.run();
			Logger.error("scan failed", e);
		}
	}

	private static void reportSelection(ISelectionListener listener,
			ScanResult result) {
		if (listener != null) {
			listener.selected(result);
		}
	}

	private final Activity ctx;

	private final AVRApplication app;
	private static final int JOIN_TIMEOUT = 10000;
	private static final int SCAN_THREADS = 16;

}
