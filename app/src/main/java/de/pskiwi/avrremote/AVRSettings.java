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

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import de.pskiwi.avrremote.core.ConnectionConfiguration;
import de.pskiwi.avrremote.log.LogMode;
import de.pskiwi.avrremote.log.Logger;

public final class AVRSettings extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);

		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);

		summaryUpdater = new PreferenceSummaryUpdater(this);

		Preference customPref = (Preference) findPreference("customBackground");
		customPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {

					public boolean onPreferenceClick(Preference preference) {

						AVRSettings.this
								.startActivityForResult(
										new Intent(
												Intent.ACTION_PICK,
												android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI),
										SELECT_IMAGE);

						return true;
					}

				});
		updateEnablement();
	}

	private void updateEnablement() {
		boolean enable = getBackgroundTheme(this) == BackgroundTheme.Custom;
		Preference customPref = (Preference) findPreference("customBackground");
		customPref.setEnabled(enable);
		Preference colorPref = (Preference) findPreference("AVRTextColor");
		colorPref.setEnabled(enable);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == SELECT_IMAGE)
			if (resultCode == Activity.RESULT_OK) {
				Uri selectedImage = data.getData();
				Logger.info("set background uri[" + selectedImage + "]");
				AVRSettings.setCustomBackground(this, selectedImage.toString());
			}
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		updateEnablement();
		summaryUpdater.updateSummaryForKey(key);
		checkIPSetting();
		getApp().getAvrTheme().update();
	}

	@Override
	protected void onResume() {
		super.onResume();
		visible = true;
		getApp().activityResumed(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		getApp().reconfigure();
		Logger.info("stop preferences");
	}

	private void checkIPSetting() {
		final ConnectionConfiguration cc = new ConnectionConfiguration(
				getAVRIP(this, 0));
		if (cc.isDefined()) {
			final AsyncTask<String, String, String> asyncTask = new AsyncTask<String, String, String>() {

				@Override
				protected String doInBackground(String... params) {
					String error = null;
					try {
						boolean checked = cc.checkAddress(true);
						if (!checked) {
							error = "[" + cc + "] "
									+ getString(R.string.SeemsNotValid);
						}

					} catch (UnknownHostException e) {
						error = "[" + cc + "] "
								+ getString(R.string.CouldNotResolve) + " ("
								+ e.getMessage() + ")";
					}

					return error;
				}

				@Override
				protected void onPostExecute(String error) {
					Logger.debug("check [" + cc + "] [" + error + "] visible:"
							+ visible);
					if (error != null && visible) {
						final AlertDialog.Builder builder = new AlertDialog.Builder(
								AVRSettings.this);
						builder.setTitle(R.string.AVRIPInvalid);
						builder.setMessage(error);
						builder.setInverseBackgroundForced(true);
						builder.setNeutralButton(R.string.OK,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
									}
								}).create().show();
					}
				}

			};
			asyncTask.execute();

		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		visible = false;
		getApp().activityPaused(this);
	}

	private AVRApplication getApp() {
		return (AVRApplication) getApplication();
	}

	public static void resetSettings(Context ctx) {
		Logger.info("reset all settings ...");
		// nur 1. IP behalten
		final String avrip = getAVRIP(ctx, 0);
		PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear()
				.commit();
		setAVRIP(ctx, avrip, 0);
		Logger.info("reset all settings ok.");
	}

	public static String getAVRIP(Context ctx, int receiverNr) {
		return PreferenceManager.getDefaultSharedPreferences(ctx)
				.getString(AVRIP + receiverSuffix(receiverNr), "").trim();
	}

	public static String getMacro(Context ctx, int nr) {
		return PreferenceManager.getDefaultSharedPreferences(ctx)
				.getString(AVRMACRO + nr, "").trim();
	}

	public static void setMacro(Context ctx, int nr, String macro) {
		PreferenceManager.getDefaultSharedPreferences(ctx).edit()
				.putString(AVRMACRO + nr, macro).commit();
	}

	private static String receiverSuffix(int receiverNr) {
		if (receiverNr == 0) {
			return "";
		}
		return "_" + (receiverNr + 1);
	}

	public static String getAVRModel(Context ctx, int receiverNr) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getString(
				"AVRModel" + receiverSuffix(receiverNr), "");
	}

	public static String getAVRModelArea(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getString(
				"AVRModelArea", "");
	}

	public static String getIPodInput(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getString(
				"AVRIpodInput", "");
	}

	public static VolumeDisplay getVolumeDisplay(Context ctx) {
		return VolumeDisplay.fromString(PreferenceManager
				.getDefaultSharedPreferences(ctx).getString("AVRVolumeDisplay",
						""));
	}

	public static int getDisconnectTimeout(Context ctx) {
		String value = PreferenceManager.getDefaultSharedPreferences(ctx)
				.getString("AVRDisconnectTimeout", "10");
		try {
			return Integer.parseInt(value);
		} catch (Exception x) {
			Logger.error("parse DisconnectTimeout [" + value + "] failed", x);
			return 10;
		}
	}

	public static ColorType getTextColor(Context ctx) {
		return ColorType
				.fromString(PreferenceManager.getDefaultSharedPreferences(ctx)
						.getString("AVRTextColor", ""));
	}

	public static BackgroundTheme getBackgroundTheme(Context ctx) {
		return BackgroundTheme.fromString(PreferenceManager
				.getDefaultSharedPreferences(ctx).getString(
						"AVRBackgroundTheme", ""));
	}

	public static boolean isDevelopMode(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(
				"AVRDevelop", false);
	}

	public static boolean isUseReceiverSettings(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(
				"AVRUseReceiverSettings", true);
	}

	public static boolean isShowChangeLog(Context ctx) {
		final String settingsKey = "AVRLastVersionCode";

		final int versionCode;
		try {
			versionCode = ctx.getPackageManager().getPackageInfo(
					ctx.getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
			Logger.error("failed to get info", e);
			return false;
		}
		int lastCode = PreferenceManager.getDefaultSharedPreferences(ctx)
				.getInt(settingsKey, 0);
		if (lastCode == versionCode) {
			return false;
		}
		PreferenceManager.getDefaultSharedPreferences(ctx).edit()
				.putInt(settingsKey, versionCode).commit();
		return true;
	}

	public static boolean isShowNotification(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(
				"AVRNotification", false);
	}

	public static int getAVRIndex(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getInt(
				AVR_INDEX, 0);
	}

	public static void setAVRIndex(Context ctx, int index) {
		PreferenceManager.getDefaultSharedPreferences(ctx).edit()
				.putInt(AVR_INDEX, index).commit();
	}

	private static void setCustomBackground(Context ctx, String uri) {
		PreferenceManager.getDefaultSharedPreferences(ctx).edit()
				.putString(AVR_BACKGROUND, uri).commit();
	}

	public static String getCustomBackground(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getString(
				AVR_BACKGROUND, "");
	}

	public static boolean isNapsterEnabled(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(
				"AVRNapster", false);
	}

	public static boolean isShowHelp(Context ctx, HelpType help) {
		final String settingsKey = "AVRHelpShown";
		String settings = PreferenceManager.getDefaultSharedPreferences(ctx)
				.getString(settingsKey, "");

		if (settings.contains("" + help)) {
			return false;
		}
		PreferenceManager.getDefaultSharedPreferences(ctx).edit()
				.putString(settingsKey, settings + " " + help).commit();
		return true;

	}

	public static String getAVRZoneCount(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getString(
				"AVRZoneCount", "");
	}

	public static void setAVRIP(Context ctx, String ip, int receiverNr) {
		PreferenceManager.getDefaultSharedPreferences(ctx).edit()
				.putString(AVRIP + receiverSuffix(receiverNr), ip.trim())
				.commit();
	}

	public static void setAVRModel(Context ctx, String model, int receiverNr) {
		PreferenceManager
				.getDefaultSharedPreferences(ctx)
				.edit()
				.putString("AVRModel" + receiverSuffix(receiverNr),
						model.trim()).commit();
	}

	public static void setLevelPreset(Context ctx,
			Map<String, Integer> presets, int receiverNr, int presetNr) {
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String, Integer> e : presets.entrySet()) {
			if (b.length() > 0) {
				b.append(",");
			}
			b.append(e.getKey() + ":" + e.getValue());
		}
		PreferenceManager
				.getDefaultSharedPreferences(ctx)
				.edit()
				.putString(
						LEVEL_PRESET + presetNr + receiverSuffix(receiverNr),
						b.toString()).commit();
	}

	public static Map<String, Integer> getLevelPresets(Context ctx,
			int receiverNr, int presetNr) {
		final String v = PreferenceManager
				.getDefaultSharedPreferences(ctx)
				.getString(
						LEVEL_PRESET + presetNr + receiverSuffix(receiverNr),
						"").trim();
		final HashMap<String, Integer> ret = new HashMap<String, Integer>();
		for (String s : v.split(",")) {
			String[] entry = s.split(":");
			if (entry.length == 2) {
				ret.put(entry[0], Integer.parseInt(entry[1]));
			}
		}
		return ret;
	}

	public static String getPDAWeb(Context ctx) {
		return PreferenceManager.getDefaultSharedPreferences(ctx).getString(
				"pdaweb", "");
	}

	public static String getAll(Context ctx) {
		try {
			final StringBuilder ret = new StringBuilder();
			ret.append("All settings:\n");
			ret.append("-------------\n");
			final Map<String, ?> all = PreferenceManager
					.getDefaultSharedPreferences(ctx).getAll();
			for (Map.Entry<String, ?> e : all.entrySet()) {
				ret.append("[" + e.getKey() + "]=["
						+ e.getValue().getClass().getSimpleName() + ":"
						+ e.getValue() + "]\n");
			}
			ret.append("-------------\n");
			return ret.toString();
		} catch (Throwable x) {
			return "Settings failed:" + x;
		}
	}

	public static LogMode getDebugMode(Context ctx) {
		return LogMode
				.fromString(PreferenceManager.getDefaultSharedPreferences(ctx)
						.getString("debugMode", "adb"));

	}

	private PreferenceSummaryUpdater summaryUpdater;
	private boolean visible;
	private static final String AVR_INDEX = "AVRIndex";
	private static final String AVR_BACKGROUND = "AVRBackground";
	private static final String LEVEL_PRESET = "LevelPreset";
	private final static String AVRIP = "avrip";
	private final static String AVRMACRO = "avrmacro_";
	protected static final int SELECT_IMAGE = 6789;
	
	public static final int MAX_RECEIVERS = 3;

}
