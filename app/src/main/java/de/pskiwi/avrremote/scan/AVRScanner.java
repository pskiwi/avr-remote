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
import java.util.concurrent.atomic.AtomicReference;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.Toast;

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

	public AVRScanner(Context ctx, IActivityShowing showing) {
		this.ctx = ctx;
		this.showing = showing;
	}

	private interface  IPProvider {
		InetAddress getIP() throws Exception;
	}

	public void scan(final IScanResultHandler handler) throws Exception {
		Logger.info("Scan: start");
		final WiFiInfo wiFiInfo = new WiFiInfo(ctx);
		if (!EmulationDetector.isEmulator() && !wiFiInfo.isConnected()) {
			String errorCause = wiFiInfo.getErrorCause();
			Logger.info("Scan: " + errorCause);
			handler.error(errorCause);
			return;
		}

		final AtomicReference<IPProvider> provider=new AtomicReference<>();
		// emulator
		Logger.info(Build.PRODUCT + "/" + Build.DEVICE);

		String netmask="";
		if (wiFiInfo.getNetmask() == 0) {
			if (EmulationDetector.isEmulator()) {
				provider.set(new IPProvider() {
					@Override
					public InetAddress getIP() throws  Exception{
						return InetAddress.getByName("192.168.10.1");
					}
				});
				netmask=CLASS_C_MASK;
			} else {
				// no wifi/dhcp
				final  IFConfig ifConfig = new IFConfig(ctx);
				if (ifConfig.isDefined()) {
					provider.set(new IPProvider() {
						@Override
						public InetAddress getIP() throws Exception {
							return ifConfig.getIP();
						}
					});
					netmask=ifConfig.getMask();
				}
			}
		} else {
			// WiFi/DHCP
			provider.set(new IPProvider() {
				@Override
				public InetAddress getIP() throws Exception {
					return wiFiInfo.getAddress();
				}

				});
			if (wiFiInfo.getNetmask() == 0xffffff) {
				netmask=CLASS_C_MASK;
			}

		}

		if (provider.get()==null) {
			Logger.info("no ip found");
			CharSequence text = "Scan not possible!";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(ctx, text, duration);
			toast.show();
			return;
		}


		// 255.255.255.0 / 24
		if (CLASS_C_MASK.equals(netmask)) {
			Logger.setLocation("scan-1");
			final ProgressDialog progress = ProgressDialog.show(ctx,
					ctx.getString(R.string.PleaseWait),
					ctx.getString(R.string.ScanningNetwork), true, false);
			final AsyncTask<InetAddress, Integer, List<ScanResult>> asyncTask = new AsyncTask<InetAddress, Integer, List<ScanResult>>() {

				@Override
				protected List<ScanResult> doInBackground(InetAddress... params) {
					IPProvider p=provider.get();
					Logger.setLocation("scan-1a");

					try {
						Logger.info("1:"+p);
						Logger.info("2:"+p.getIP());
						InetAddress toScan=provider.get().getIP();
						Logger.info("scan: ip:" + toScan.getHostAddress());

						return scanNetwork(toScan);
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
		} else {
			handler.error(ctx.getString(R.string.AutoScanNotSupported));
		}
	}

	private List<ScanResult> scanNetwork(InetAddress i4)
			throws InterruptedException {
		final String[] parts = i4.getHostAddress().split("\\.");
		final String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
		final ScanThread[] threads = new ScanThread[SCAN_THREADS];
		for (int i = 0; i < SCAN_THREADS; i++) {
			threads[i] = new ScanThread(prefix, i * SCAN_THREADS, (i + 1)
					* SCAN_THREADS - 1);
			threads[i].start();

		}
		final List<ScanResult> result = new ArrayList<ScanResult>();
		for (int i = 0; i < SCAN_THREADS; i++) {
			threads[i].join(JOIN_TIMEOUT);
			result.addAll(threads[i].getResult());
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

			final AVRScanner scan = new AVRScanner(ctx, showing);
			scan.scan(resultHandler);

		} catch (Exception e) {
			Logger.error("scan failed", e);
		}
	}

	private final Context ctx;

	private final IActivityShowing showing;
	private static final String CLASS_C_MASK = "255.255.255.0";
	private static final int JOIN_TIMEOUT = 10000;
	private static final int SCAN_THREADS = 16;

}
