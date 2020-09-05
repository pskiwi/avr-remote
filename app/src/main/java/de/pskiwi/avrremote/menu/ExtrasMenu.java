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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.EditText;
import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.IActivityShowing;
import de.pskiwi.avrremote.MenuBuilder;
import de.pskiwi.avrremote.R;
import de.pskiwi.avrremote.core.IAVRState;
import de.pskiwi.avrremote.core.IStateListener;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.AbstractManualSelect;
import de.pskiwi.avrremote.core.ZoneState.OptionGroup;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class ExtrasMenu {

	public interface IBaseMenuActivity extends IActivityShowing {
		AVRApplication getApp();

		Activity getActivity();
	}

	private interface IMenu {
		void show(String currentValue);
	}

	public ExtrasMenu(IBaseMenuActivity activity) {
		this.activity = activity;
	}

	public void show() {

		MenuBuilder mb = new MenuBuilder(activity.getActivity(), activity
				.getActivity().getString(R.string.Options));

		for (final OptionGroup ot : ZoneState.OptionGroup.values()) {
			if (activity.getApp().getModelConfigurator().hasOptionGroup(ot)) {
				mb.add(ot.getTitle(), new Runnable() {
					public void run() {
						showOptionMenu(ot);
					}
				});
			}
		}

		mb.showMenu();
	}

	private void showOptionMenu(ZoneState.OptionGroup optionGroup) {
		final List<String> values = getValues(optionGroup);

		final String[] valueArray = values.toArray(new String[values.size()]);

		new AlertDialog.Builder(activity.getActivity())
				.setTitle(activity.getActivity().getString(R.string.Options))
				.setItems(valueArray, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (which >= 0) {
							final int mode = which;
							Logger.info("quick " + mode);
							select(mode);
						}
					}
				}).show();
	}

	private  List<String> getValues(ZoneState.OptionGroup optionGroup) {
		

		final List<IAVRState> manualStates = getStatesForGroup(optionGroup);
		int c = 0;
		final List<String> values = new ArrayList<String>();
		for (IAVRState s : manualStates) {
			values.add(activity.getActivity().getString(s.getDisplayId()));
			map.put(c, s);
			c++;
		}

		if (AVRSettings.isDevelopMode(activity.getActivity())) {
			values.add("Direct command");
			manualId = c;
		}
		return values;
	}

	public List<IAVRState> getStatesForGroup(ZoneState.OptionGroup optionGroup) {
		final ZoneState mainZone = activity.getApp().getAvrState()
				.getZone(Zone.Main);
		final List<IAVRState> rawStates = mainZone.getManualStates();
		final List<IAVRState> manualStates = new ArrayList<IAVRState>();
		final ModelConfigurator modelConfigurator = activity.getApp()
				.getModelConfigurator();
		for (IAVRState s : rawStates) {
			if (s instanceof AbstractManualSelect) {
				final AbstractManualSelect ams = (AbstractManualSelect) s;
				if (ams.getOptionGroup() == optionGroup
						&& modelConfigurator.hasOption(ams.getOptionType())) {
					manualStates.add(s);
				}
			}
		}

		Collections.sort(manualStates, new Comparator<IAVRState>() {

			public int compare(IAVRState s1, IAVRState s2) {
				return activity
						.getActivity()
						.getString(s1.getDisplayId())
						.compareTo(
								activity.getActivity().getString(
										s2.getDisplayId()));
			}
		});
		return manualStates;
	}

	private void select(int id) {
		if (AVRSettings.isDevelopMode(activity.getActivity()) && id == manualId) {
			showCommandMenu();
			return;
		}
		try {
			final IAVRState state = map.get(id);
			Logger.info("selected " + id + " " + state);
			if (state != null && state instanceof AbstractManualSelect) {
				showMenu(state);

				return;
			}
			return;
		} finally {
			map.clear();
		}
	}

	public void showMenu(final IAVRState state) {
		query(state, new IMenu() {
			public void show(String currentValue) {
				showMenu(state, currentValue);
			}
		});
	}

	private void showMenu(final IAVRState state, String currentValue) {
		final AbstractManualSelect ms = (AbstractManualSelect) state;
		final String[] displayValues = ms.getDisplayValues();
		new AlertDialog.Builder(activity.getActivity())
				.setTitle(
						activity.getActivity().getString(R.string.Select)
								+ " ["
								+ ms.getSelection().getDisplay(currentValue)
								+ "]")
				.setItems(displayValues, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Logger.info("selected " + which);
						if (which >= 0) {
							Logger.info("selected " + ms.getValues()[which]);
							ms.select(ms.getValues()[which]);
						}
					}

				}).show();
	}

	public void showCommandMenu() {
		final EditText input = new EditText(activity.getActivity());
		new AlertDialog.Builder(activity.getActivity())
				.setTitle("Denon protocol command")
				.setMessage("Enter Denon protocol command")
				.setView(input)
				.setPositiveButton(R.string.OK,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								String code = input.getText().toString().trim()
										.toUpperCase();
								activity.getApp().getConnector().send(code);
							}
						})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								// Do nothing.
							}
						}).show();
	}

	private void query(final IAVRState state, final IMenu menu) {
		final ProgressDialog progress = ProgressDialog.show(activity
				.getActivity(),
				activity.getActivity().getString(R.string.PleaseWait), activity
						.getActivity().getString(R.string.QueryValue), true,
				false);

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<String> result = new AtomicReference<String>(null);
		Logger.info("query :" + state);
		state.reset();
		Logger.info("add listener :" + state);
		activity.getApp().getAvrState().getZone(Zone.Main)
				.setListener(new IStateListener<AbstractManualSelect>() {
					public void changedState(AbstractManualSelect state) {
						// wird im GUI-Thread aufgerufen.
						Logger.info("got result :" + state.getSelected());
						result.set(state.getSelected());
						latch.countDown();
					}
				}, state.getClass(), false);

		final AsyncTask<String, String, String> asyncTask = new AsyncTask<String, String, String>() {

			@Override
			protected String doInBackground(String... params) {

				try {
					latch.await(3, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
				}
				activity.getApp().getAvrState().getZone(Zone.Main)
						.removeListener(state.getClass());

				return result.get();
			}

			@Override
			protected void onPostExecute(String result) {
				super.onPostExecute(result);
				if (activity.isShowing()) {
					Logger.setLocation("ExtrasMenu-query-1");
					progress.dismiss();

					if (result == null) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								activity.getActivity());
						builder.setTitle(R.string.QueryResult);
						builder.setMessage(R.string.QueryFailed);
						builder.setInverseBackgroundForced(true);
						builder.setNeutralButton(R.string.OK,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										Logger.setLocation("ExtrasMenu-query-2");
									}
								});
						AlertDialog alert = builder.create();
						alert.show();
					} else {
						menu.show(result.trim());
					}
				}
			}

		};
		asyncTask.execute();

	}

	private final IBaseMenuActivity activity;

	private final Map<Integer, IAVRState> map = new HashMap<Integer, IAVRState>();
	private int manualId = -1;

}
