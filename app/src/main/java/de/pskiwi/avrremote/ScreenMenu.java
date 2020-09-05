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

import java.util.concurrent.atomic.AtomicReference;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.InputSelect;
import de.pskiwi.avrremote.core.display.NetDisplay;
import de.pskiwi.avrremote.core.display.NetDisplay.TrackInfo;
import de.pskiwi.avrremote.http.AVRHTTPClient;
import de.pskiwi.avrremote.http.AVRHTTPClient.SearchInputType;
import de.pskiwi.avrremote.http.AVRHTTPClient.SearchType;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.models.ModelConfigurator;

public final class ScreenMenu implements IScreenMenu {

	public ScreenMenu(Activity activity, NetDisplay screen,
			ModelConfigurator configurator, Zone zone) {
		this.activity = activity;
		this.screen = screen;
		this.configurator = configurator;
		this.zone = zone;
	}

	public void doSearch() {
		final AVRApplication application = (AVRApplication) activity
				.getApplication();
		final InputSelect state = application.getAvrState().getState(zone,
				InputSelect.class);
		final String selected = state.getSelected();
		final SearchInputType inputType = SearchInputType.fromString(selected);
		
		Logger.info("doSearch [" + selected + "] input:" + inputType + " "
				+ inputType);
		if (inputType != SearchInputType.None) {
			doSearch(inputType);
		} else {
			doKeySearch();
		}
	}

	private void doKeySearch() {
		final AtomicReference<AlertDialog> dialog = new AtomicReference<AlertDialog>();

		final TableLayout layout = new TableLayout(activity);
		layout.setStretchAllColumns(true);
		layout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
		int i = 0;
		while (i < KEYBOARD.length) {
			final TableRow innerLayout = new TableRow(activity);
			layout.addView(innerLayout);
			final int colums = 5;
			for (int j = 0; j < colums; j++) {
				if (i >= KEYBOARD.length) {
					break;
				}
				final String ch = Character.toString(KEYBOARD[i]);
				final TextView button = new TextView(activity);
				button.setText(ch);
				button.setGravity(Gravity.CENTER);
				Logger.setLocation("doKeySearch-1");
				button.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						Logger.setLocation("doKeySearch-2");
						screen.search(ch);
						final AlertDialog alertDialog = dialog.get();
						if (alertDialog != null) {
							Logger.setLocation("doKeySearch-3");
							alertDialog.dismiss();
						}
					}
				});
				final android.widget.TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(
						LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				// Für die Umrandung
				layoutParams.setMargins(j > 0 ? 1 : 5, 1, j == colums - 1 ? 5
						: 0, 0);

				button.setBackgroundColor(Color.BLACK);
				button.setPadding(10, 5, 10, 5);
				button.setTypeface(Typeface.DEFAULT_BOLD);
				innerLayout.addView(button, layoutParams);
				i++;
			}
		}
		final Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.JumpTo)
				.setView(layout)
				.setNegativeButton(R.string.Cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
							}
						});

		AlertDialog d = builder.create();
		dialog.set(d);
		d.show();
	}

	private void doSearch(final SearchInputType type) {
		final LinearLayout layout = new LinearLayout(activity);
		layout.setOrientation(LinearLayout.VERTICAL);
		final Spinner spinner;
		if (type.supportsType()) {
			spinner = new Spinner(activity);
			final ArrayAdapter<CharSequence> adapter = ArrayAdapter
					.createFromResource(activity, R.array.searchType,
							android.R.layout.simple_spinner_item);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spinner.setAdapter(adapter);
			layout.addView(spinner);
		} else {
			spinner = null;
		}
		final EditText input = new EditText(activity);
		layout.addView(input);

		new AlertDialog.Builder(activity)
				.setTitle(R.string.Search)
				.setView(layout)
				.setPositiveButton(R.string.OK,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Editable value = input.getText();
								AVRHTTPClient.SearchType searchType = spinner != null ? AVRHTTPClient.SearchType
										.fromString(""
												+ spinner.getSelectedItem())
										: SearchType.None;
								search(type, searchType, value.toString());
							}
						})
				.setNegativeButton(R.string.Cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								// Do nothing.
							}
						}).show();

	}

	protected void search(final SearchInputType inputType,
			final SearchType type, final String text) {
		Thread thread = new Thread("Search") {
			@Override
			public void run() {
				Logger.info("Search :" + inputType + "/" + type + " [" + text
						+ "]");
				try {
					new AVRHTTPClient(configurator).doSearch(inputType, type,
							text);
				} catch (Exception e) {
					Logger.error("Search [" + inputType + "/" + type
							+ "] text: [" + text + "] failed", e);
				}

			};
		};
		thread.start();
	}

	public void doClassicSearch() {
		final EditText input = new EditText(activity);
		new AlertDialog.Builder(activity)
				.setTitle(R.string.Search)
				.setView(input)
				.setPositiveButton(R.string.OK,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Editable value = input.getText();
								screen.search(value.toString());
							}
						})
				.setNegativeButton(R.string.Cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								// Do nothing.
							}
						}).show();
	}

	public void showExtraMenu() {
		if (!AVRSettings.isDevelopMode(activity)) {
			return;
		}

		final MenuBuilder extras = new MenuBuilder(activity, "Options");

		extras.add("Menu on", new Runnable() {
			public void run() {
				screen.menu(true);
			}
		});

		extras.add("Menu off", new Runnable() {
			public void run() {
				screen.menu(false);
			}
		});

		extras.add("Source on", new Runnable() {
			public void run() {
				screen.menuSourceSelect(true);
			}
		});
		extras.add("Source off", new Runnable() {
			public void run() {
				screen.menuSourceSelect(false);
			}
		});
		extras.add("Favourite on", new Runnable() {
			public void run() {
				screen.menuFavourite(true);
			}
		});
		extras.add("Favourite off", new Runnable() {
			public void run() {
				screen.menuFavourite(false);
			}
		});

		extras.showMenu();
	}

	public void showMenu(boolean longMenu) {
		final MenuBuilder extras = new MenuBuilder(activity, "Options");
		if (longMenu) {
			extras.add("Play", new Runnable() {
				public void run() {
					screen.play();
				}
			});

			extras.add("Pause", new Runnable() {
				public void run() {
					screen.pause();
				}
			});

			extras.add("Stop", new Runnable() {
				public void run() {
					screen.stop();
				}
			});

			extras.add("Search", new Runnable() {
				public void run() {
					doSearch();
				}
			});
		}

		final TrackInfo currentTrackInfo = screen.getCurrentTrackInfo();
		if (currentTrackInfo != null && currentTrackInfo.isDefined()) {
			extras.add("Music search '" + currentTrackInfo.getTrack() + "'",
					new Runnable() {
						public void run() {
							screen.musicSearch(activity);
						}
					});
			if (currentTrackInfo.getStation().length() > 0) {
				extras.add("Station search '" + currentTrackInfo.getStation()
						+ "'", new Runnable() {
					public void run() {
						Intent search = new Intent(Intent.ACTION_WEB_SEARCH);
						search.putExtra(SearchManager.QUERY,
								currentTrackInfo.getStation());
						activity.startActivity(search);
					}
				});
			}
		}

		extras.add("Repeat one", new Runnable() {

			public void run() {
				screen.repeatOne();
			}
		});
		extras.add("Repeat all", new Runnable() {

			public void run() {
				screen.repeatAll();
			}
		});
		extras.add("Repeat off", new Runnable() {

			public void run() {
				screen.repeatOff();
			}
		});
		extras.add("Random on", new Runnable() {

			public void run() {
				screen.randomOn();
			}
		});
		extras.add("Random off", new Runnable() {

			public void run() {
				screen.randomOff();
			}
		});

		if (longMenu) {
			extras.add("Skip plus", new Runnable() {

				public void run() {
					screen.skipPlus();
				}
			});
			extras.add("Skip minus", new Runnable() {

				public void run() {
					screen.skipMinus();
				}
			});
		}

		extras.showMenu();
	}

	public boolean handleKey(ZoneState zoneState, int keyCode, KeyEvent event) {
		Logger.info("ScreenMenu:keycode [" + keyCode + "]");

		if (VolumeKeyHandler.handle(zoneState, keyCode, event)) {
			return true;
		}
		// Nach Buchstaben direkt suchen
		if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
			screen.search("" + (char) ('A' + (keyCode - KeyEvent.KEYCODE_A)));
			return true;
		}
		switch (keyCode) {
		case KeyEvent.KEYCODE_SEARCH:
			doSearch();
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			screen.up();
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			screen.down();
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			screen.left();
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			screen.right();
			break;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			screen.enter();
			break;
		default:
			return false;
		}
		return true;
	}

	private final Zone zone;
	private final ModelConfigurator configurator;
	private final NetDisplay screen;
	private final Activity activity;
	private final static char[] KEYBOARD = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
			.toCharArray();
}
