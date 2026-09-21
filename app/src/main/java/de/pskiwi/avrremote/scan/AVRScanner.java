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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.LinkAddress;
import android.os.AsyncTask;
import android.os.Build;

import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.EmulationDetector;
import de.pskiwi.avrremote.IActivityShowing;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.log.Logger;

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

		public ScanResult(InetAddress address, FoundBy foundBy) {
			this.address = address;
			this.info = address.getHostAddress() + " / "
					+ address.getHostName() + "  (" + foundBy.getLabel() + ")";
			this.ip = address.getHostAddress();
		}

		@Override
		public String toString() {
			return info;
		}

		final InetAddress address;
		final String info;
		final String ip;
	}

	public interface IScanResultHandler {
		void error(String cause);

		void finished(List<ScanResult> result);
	}

	private final static class ScanThread extends Thread {

		public ScanThread(List<InetAddress> addresses, FoundBy foundBy) {
			super("Scan-Thread [" + addresses.get(0).getHostAddress() + "+"
					+ addresses.size() + "]");
			this.addresses = addresses;
			this.foundBy = foundBy;
		}

		@Override
		public void run() {
			for (InetAddress ia : addresses) {
				try {
					if (AVRTargetTester.testAddress(ia, false)) {
						result.add(new ScanResult(ia, foundBy));
					}
				} catch (Exception e) {
					Logger.error("scan exception", e);
				}
			}
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

	}

	public AVRScanner(Context ctx, IActivityShowing showing, AVRApplication app) {
		this.ctx = ctx;
		this.showing = showing;
		this.app = app;
	}

	public void scan(final IScanResultHandler handler) throws Exception {
		Logger.info("Scan: start");
		// emulator
		Logger.info(Build.PRODUCT + "/" + Build.DEVICE);

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
					final List<ScanResult> viaSSDP = testAll(
							SSDPDiscovery.search(), FoundBy.SSDP);
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
				if (showing.isShowing()) {
					Logger.setLocation("scan-3");
					progress.dismiss();
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
			FoundBy foundBy) throws InterruptedException {
		if (addresses.isEmpty()) {
			return Collections.emptyList();
		}
		final int[][] ranges = splitRange(0, addresses.size(), SCAN_THREADS);
		final ScanThread[] threads = new ScanThread[ranges.length];
		for (int i = 0; i < ranges.length; i++) {
			threads[i] = new ScanThread(addresses.subList(ranges[i][0],
					ranges[i][1]), foundBy);
			threads[i].start();
		}
		final List<ScanResult> result = new ArrayList<ScanResult>();
		for (int i = 0; i < threads.length; i++) {
			threads[i].join(JOIN_TIMEOUT);
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
		return testAll(addresses, FoundBy.SWEEP);
	}

	/**
	 * Erster zu scannender Wert im letzten Oktett und Anzahl, als {from, count}.
	 * Nur fuer prefixLength >= 24 definiert - darunter reicht das letzte Oktett
	 * nicht aus.
	 */
	static int[] hostRange(int lastOctet, int prefixLength) {
		final int hostBits = 32 - prefixLength;
		final int mask = (0xff << hostBits) & 0xff;
		return new int[] { lastOctet & mask, 1 << hostBits };
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

	public static void scanIP(final Activity ctx,
			final IActivityShowing showing, final AVRApplication app,
			final Runnable runFinished, final int nr) {
		try {
			// Der Scan ist ueber das Menue und den Einrichtungs-Assistenten
			// erreichbar, ohne dass AVRRemote.onCreate gelaufen waere.
			if (AVRSettings.requestLocalNetworkPermission(ctx)) {
				// Die Antwort kommt asynchron. Jetzt zu scannen hiesse, die
				// Permission als fehlend zu sehen und den Fehlerdialog unter
				// den gerade aufgegangenen System-Dialog zu legen. Also hier
				// aufhoeren: der Anwender beantwortet erst die Frage und
				// startet den Suchlauf danach neu.
				Logger.info("Scan: asked for local network permission first");
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
								}
							})
							.setTitle(R.string.SelectAVRIP)
							.setItems(values,
									new DialogInterface.OnClickListener() {
										public void onClick(
												DialogInterface dialog,
												int which) {
											try {
												if (which >= 0) {
													String ip = result
															.get(which).ip;
													Logger.info("selected ["
															+ ip + "]");
													AVRSettings.setAVRIP(ctx,
															ip, nr);
													app.reconfigure();
												}
											} finally {
												runFinished.run();
											}
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
								}
							});
					AlertDialog alert = builder.create();
					alert.show();
				}
			};

			final AVRScanner scan = new AVRScanner(ctx, showing, app);
			scan.scan(resultHandler);

		} catch (Exception e) {
			Logger.error("scan failed", e);
		}
	}

	private final Context ctx;

	private final IActivityShowing showing;
	private final AVRApplication app;
	private static final int JOIN_TIMEOUT = 10000;
	private static final int SCAN_THREADS = 16;

}
