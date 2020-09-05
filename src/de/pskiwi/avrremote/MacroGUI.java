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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import de.pskiwi.avrremote.EnableManager.StatusFlag;
import de.pskiwi.avrremote.EnableManager.ViewList;
import de.pskiwi.avrremote.core.Macro;
import de.pskiwi.avrremote.core.MacroManager;
import de.pskiwi.avrremote.log.Logger;

public final class MacroGUI {

	private final class StopRecord implements Runnable {

		private StopRecord(Activity activity, ButtonHandler buttonHandler) {
			this.activity = activity;
			this.buttonHandler = buttonHandler;
		}

		public void run() {
			// Titel erstmal zurücksetzen
			updateMacroFromSettings();

			// Nichts aufgenommen -> abbrechen
			if (!macroManager.isMacroRecorded()) {
				Toast.makeText(activity, R.string.NoMacroRecorded,
						Toast.LENGTH_SHORT).show();
				macroManager.cancelRecording();

				return;
			}
			final Macro oldMacro = buttonHandler.getMacro();
			final EditText input = new EditText(activity);
			input.setText(oldMacro.getName());
			new AlertDialog.Builder(activity).setTitle(R.string.MacroName)
					.setMessage(R.string.EnterMacroName).setView(input)
					.setPositiveButton(R.string.OK,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									final String name = input.getText()
											.toString().trim();
									macroManager.remove(oldMacro);
									final Macro newMacro = macroManager
											.saveRecording(name);
									if (newMacro != null) {
										if (buttonHandler != null) {
											buttonHandler.setMacro(newMacro);
										}
									} else {
										macroManager.add(oldMacro);
									}
									reduceMacros();
								}
							}).setNegativeButton(R.string.Cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									macroManager.cancelRecording();
								}
							}).show();
		}

		private final Activity activity;
		private final ButtonHandler buttonHandler;

	}

	private final class ButtonHandler {

		public ButtonHandler(Activity activity, Button button, int nr) {
			this.activity = activity;
			this.button = button;
			this.nr = nr;

			loadMacroSettings();

			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					runMacro();
				}

			});
			button.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View v) {
					runLongClick();
					return true;
				}
			});
		}

		public void setMacro(Macro m) {
			AVRSettings.setMacro(activity, nr, m.getName());
			updateMacroFromSettings();
		}

		public Macro getMacro() {
			return macro;
		}

		// nur aus updateMacroFromSettings aufrufen um Mehrfachbuttons zu
		// erfassen
		public void loadMacroSettings() {
			macro = macroManager.getMacro(AVRSettings.getMacro(activity, nr));
			if (macro == null) {
				// Null-Object
				macro = new Macro("F" + nr);
			}
			button.setText(macro.getName());
		}

		protected void runLongClick() {
			new AlertDialog.Builder(activity).setTitle(
					R.string.RecordMacroTitle).setMessage(R.string.RecordMacro)
					.setPositiveButton(R.string.OK,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									macroManager.startRecording();
									setAction(activity
											.getString(R.string.StopRecording),
											new StopRecord(activity,
													ButtonHandler.this));
								}
							}).setNegativeButton(R.string.Cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									// ignore
								}
							}).create().show();
		}

		void runMacro() {
			if (action != null) {
				try {
					action.run();
				} finally {
					action = null;
					updateMacroFromSettings();
				}
			} else {
				if (!macro.isEmpty()) {
					macroManager.run(macro);
				} else {
					Toast.makeText(activity, R.string.LongPressToDefine,
							Toast.LENGTH_LONG).show();
				}
			}
		}

		public void setAction(String title, Runnable runnable) {
			MacroGUI.this.setAction(nr, title, runnable);
		}

		public void setButtonAction(String title, Runnable runnable) {
			button.setText(title);
			action = runnable;
		}

		private Runnable action;
		private Macro macro;
		private final Activity activity;
		private final Button button;
		// 1..n
		private final int nr;
	}

	public MacroGUI(MacroManager macroManager) {
		this.macroManager = macroManager;
	}

	public void clearButtons() {
		Logger.info("ClearButtons");
		currentButtons.clear();
	}

	public void initButtons(final Activity activity, View baseView,
			ViewList viewList, int... btnIds) {

		int nr = 0;
		// nr:1..n
		for (int btnRef : btnIds) {
			nr++;
			final Button button;
			if (baseView != null) {
				button = (Button) baseView.findViewById(btnRef);
			} else {
				button = (Button) activity.findViewById(btnRef);
			}
			Logger.info("Init Button [" + nr + "]  (" + btnRef + ") view:"
					+ (baseView != null));
			if (button != null) {
				viewList.addView(button, StatusFlag.Connected);
				currentButtons.add(new ButtonHandler(activity, button, nr));
			} else {
				Logger.error("Macro Button [" + nr + "] not found (" + btnRef
						+ ") view:" + (baseView != null), null);
			}

		}
	}

	private void reduceMacros() {
		final List<String> names = new ArrayList<String>();
		for (ButtonHandler h : currentButtons) {
			Macro macro = h.getMacro();
			if (!macro.isEmpty()) {
				names.add(macro.getName());
			}
		}
		macroManager.retainAll(names);
	}

	public void updateMacroFromSettings() {
		for (ButtonHandler h : currentButtons) {
			h.loadMacroSettings();
		}
	}

	public void setAction(int nr, String title, Runnable runnable) {
		for (ButtonHandler h : currentButtons) {
			if (h.nr == nr) {
				h.setButtonAction(title, runnable);
			}
		}
	}

	private final List<ButtonHandler> currentButtons = new ArrayList<ButtonHandler>();
	private final MacroManager macroManager;

}
