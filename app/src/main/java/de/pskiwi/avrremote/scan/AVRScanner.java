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

	public final static class ScanResult {

		public ScanResult(InetAddress address) {
			this.address = address;
			this.info = address.getHostAddress() + " / "
					+ address.getHostName();
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

		public ScanThread(String prefix, int from, int to) {
			super("Scan-Thread [" + from + ":" + to + "]");
			this.prefix = prefix;
			this.from = from;
			this.to = to;
		}

		@Override
		public void run() {
			for (int i = from; i < to; i++) {
				try {
					final InetAddress ia = Inet4Address.getByName(prefix + i);
					if (AVRTargetTester.testAddress(ia, false)) {
						result.add(new ScanResult(ia));
					}
				} catch (Exception e) {
					Logger.error("scan exception", e);
				}
			}
		}

		public List<ScanResult> getResult() {
			return result;
		}

		private final List<ScanResult> result = new LinkedList<ScanResult>();
		private final int from;
		private final int to;
		private final String prefix;

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

		final LocalNetwork localNetwork = app.getLocalNetwork();
		final InetAddress ip;
		final int prefixLength;
		if (EmulationDetector.isEmulator()) {
			ip = InetAddress.getByName("192.168.10.1");
			prefixLength = 24;
		} else {
			if (!localNetwork.isConnected()) {
				final String errorCause = localNetwork.getErrorCause();
				Logger.info("Scan: " + errorCause);
				handler.error(errorCause);
				return;
			}
			final LinkAddress address = localNetwork.getIPv4();
			ip = address.getAddress();
			prefixLength = address.getPrefixLength();
		}
		Logger.info("Scan: " + ip.getHostAddress() + "/" + prefixLength);

		// Alles ab /24 liegt im letzten Oktett und ist damit in einem Durchgang
		// absuchbar; darunter waeren es mindestens 512 Adressen.
		if (prefixLength < 24) {
			handler.error(ctx.getString(R.string.AutoScanNotSupported));
			return;
		}

		Logger.setLocation("scan-1");
		final ProgressDialog progress = ProgressDialog.show(ctx,
				ctx.getString(R.string.PleaseWait),
				ctx.getString(R.string.ScanningNetwork), true, false);
		final AsyncTask<InetAddress, Integer, List<ScanResult>> asyncTask = new AsyncTask<InetAddress, Integer, List<ScanResult>>() {

			@Override
			protected List<ScanResult> doInBackground(InetAddress... params) {
				Logger.setLocation("scan-1a");
				try {
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
					} else {
						handler.error(ctx.getString(R.string.NoIpFound));
					}
				}
			}
		};
		asyncTask.execute();
	}

	private List<ScanResult> scanNetwork(InetAddress i4, int prefixLength)
			throws InterruptedException {
		final String[] parts = i4.getHostAddress().split("\\.");
		final String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
		final int[] hostRange = hostRange(Integer.parseInt(parts[3]),
				prefixLength);
		final int[][] ranges = splitRange(hostRange[0], hostRange[1],
				SCAN_THREADS);
		final ScanThread[] threads = new ScanThread[ranges.length];
		for (int i = 0; i < ranges.length; i++) {
			threads[i] = new ScanThread(prefix, ranges[i][0], ranges[i][1]);
			threads[i].start();
		}
		final List<ScanResult> result = new ArrayList<ScanResult>();
		for (int i = 0; i < threads.length; i++) {
			threads[i].join(JOIN_TIMEOUT);
			result.addAll(threads[i].getResult());
		}
		return result;
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

	public static void scanIP(final Context ctx,
			final IActivityShowing showing, final AVRApplication app,
			final Runnable runFinished, final int nr) {
		try {
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
