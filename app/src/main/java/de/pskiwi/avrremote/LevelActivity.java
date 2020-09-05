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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import de.pskiwi.avrremote.core.IStateListener;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.core.ZoneState.AbstractLevel;
import de.pskiwi.avrremote.core.ZoneState.LevelGroup;
import de.pskiwi.avrremote.core.ZoneState.LevelType;
import de.pskiwi.avrremote.log.Logger;

public final class LevelActivity extends Activity {

	private final class Level implements OnSeekBarChangeListener {

		public Level(final LevelType ct, SeekBar sb, TextView textView) {
			this.type = ct;
			this.seekbar = sb;
			this.text = textView;
			cv = getApp().getAvrState().getState(zone, ct.getLevelClass());
			cv.reset();
			updateRunnable = new Runnable() {
				public void run() {
					cv.update(ct);
				}
			};

			sb.setOnSeekBarChangeListener(this);
		}

		public void onStopTrackingTouch(SeekBar seekBar) {

			final int progress = seekBar.getProgress();
			final int value = progress + type.getMin();

			final int currentValue = getValue();
			final int diff = value - currentValue;

			Logger.info("new [" + type + "] value -> " + value + " ("
					+ progress + ") diff:" + diff);
			// Check, ob anderer Kanal mit der Differenz gesetzt werden, oder ob
			// er aus
			// dem definierten Bereich laufen würde
			if (linkedTo != null && checkBoxLink.isChecked()) {
				if (!linkedTo.checkDiff(diff)) {
					Logger.info("linked value out of range ["
							+ linkedTo.getValue() + "] diff:" + diff
							+ " currentValue:" + currentValue);
					requestReceiverUpdate();
					Toast.makeText(LevelActivity.this,
							R.string.LinkValueOutOfRange, Toast.LENGTH_SHORT)
							.show();
					return;
				}
			}

			setValue(value);
			if (linkedTo != null && checkBoxLink.isChecked()) {

				Logger.info("update link [" + linkedTo.type + "] diff:" + diff);
				linkedTo.setLinkDiff(diff);
			}
		}

		private boolean checkDiff(int diff) {
			final int newValue = getValue() + diff;
			return newValue >= type.getMin() && newValue <= type.getMax();
		}

		private void setValue(final int value) {
			if (!restore.containsKey(type) && cv.containsValue(type)) {
				restore.put(type, cv.getValue(type));
			}
			if (cv.setLevel(type, value)) {
				requestReceiverUpdate();
			}
		}

		private void requestReceiverUpdate() {
			handler.removeCallbacks(updateRunnable);
			handler.postDelayed(updateRunnable, 1000);
		}

		private void setLinkDiff(int diff) {
			setValue(getValue() + diff);
		}

		private int getValue() {
			return cv.getValue(type);
		}

		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
		}

		public void linkTo(Level linkedTo) {
			this.linkedTo = linkedTo;
			linkedTo.linkedTo = this;
		}

		public void updateDisplay(int value) {
			Logger.info("updated new value:" + type + "=" + value);
			// 335 -> 33.5 (.5 ignorieren)
			if (value > type.getMax() && type.getGroup() == LevelGroup.Channel) {
				value = value / 10;
			}
			seekbar.setProgress(value - type.getMin());
			text.setText(type.getText() + " : "
					+ type.convertToDisplay(value, absoluteMode));

		}

		public Integer getCurrentValue() {
			return seekbar.getProgress() + type.getMin();
		}

		public void setCurrentValue(int value) {
			setValue(value);
		}

		private Level linkedTo;
		private final LevelType type;
		private final SeekBar seekbar;
		private final TextView text;
		private Runnable updateRunnable;
		private AbstractLevel cv;

	}

	public AVRApplication getApp() {
		return (AVRApplication) getApplication();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		zone = Zone.Main;

		setContentView(R.layout.level);

		absoluteMode = AVRSettings.getVolumeDisplay(this) == VolumeDisplay.Absolute;
		checkBoxLink = (CheckBox) findViewById(R.id.checkLinkChannels);
		if (savedInstanceState != null) {
			checkBoxLink.setChecked(savedInstanceState.getBoolean(
					LINK_CHANNELS, true));
		}

		final Set<LevelType> supportedLevels = getApp().getModelConfigurator()
				.getModel().getSupportedLevels();

		if (!supportedLevels.contains(LevelType.FL)) {
			checkBoxLink.setEnabled(false);
		}

		final ViewGroup content = (ViewGroup) findViewById(R.id.levelContent);
		LevelGroup lastGroup = null;
		for (LevelType ct : LevelType.values()) {
			if (supportedLevels.contains(ct)) {
				if (lastGroup != ct.getGroup()) {
					TextView textView = new TextView(this);
					textView.setText(ct.getGroup().getText());
					textView.setTypeface(Typeface.DEFAULT_BOLD);
					content.addView(textView);
					View ruler = new View(this);
					ruler.setBackgroundColor(0xFFCCCCCC);
					content.addView(ruler, new ViewGroup.LayoutParams(
							ViewGroup.LayoutParams.FILL_PARENT,
							ViewGroup.LayoutParams.WRAP_CONTENT));

					lastGroup = ct.getGroup();
				}
				content.addView(createChannel(ct));
			}
		}

		// Verknüpfte Kanäle
		link(LevelType.FL, LevelType.FR);
		link(LevelType.FHL, LevelType.FHR);
		link(LevelType.FWL, LevelType.FWR);
		link(LevelType.SL, LevelType.SR);
		link(LevelType.SBL, LevelType.SBR);

		getApp().getAvrState().getZone(zone)
				.setAllBaseListener(new IStateListener<AbstractLevel>() {
					public void changedState(AbstractLevel state) {
						update(state);
					}
				}, AbstractLevel.class);

		final Button ok = (Button) findViewById(R.id.btnLevelOk);
		ok.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});

		final Button cancel = (Button) findViewById(R.id.btnLevelCancel);
		cancel.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Logger.info("restore level #" + restore.size());
				for (Map.Entry<LevelType, Integer> e : restore.entrySet()) {
					final LevelType type = e.getKey();
					final Level level = levelSeeker.get(type);
					if (level != null) {
						level.setValue(e.getValue().intValue());
					}
				}
				finish();
			}
		});

		createPresetButton((Button) findViewById(R.id.btnLevelP1), 0);
		createPresetButton((Button) findViewById(R.id.btnLevelP2), 1);
	}

	private void link(LevelType t1, LevelType t2) {
		final Level l1 = levelSeeker.get(t1);
		final Level l2 = levelSeeker.get(t2);
		if (l1 != null && l2 != null) {
			l1.linkTo(l2);
		}

	}

	private void createPresetButton(final Button p1, final int nr) {
		p1.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				restorePreset(nr);
			}
		});
		p1.setOnLongClickListener(new OnLongClickListener() {

			public boolean onLongClick(View v) {
				savePreset(nr);
				return true;
			}

		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(LINK_CHANNELS, checkBoxLink.isChecked());
	}

	private void savePreset(int presetNr) {
		final Map<String, Integer> presets = new HashMap<String, Integer>();
		for (Map.Entry<LevelType, Level> e : levelSeeker.entrySet()) {
			presets.put(e.getKey().getId(), e.getValue().getCurrentValue());
		}
		AVRSettings.setLevelPreset(this, presets, getApp()
				.getModelConfigurator().getCurrentReceiver(), presetNr);
		Toast.makeText(this, getString(R.string.PresetSaved, (presetNr + 1)),
				Toast.LENGTH_SHORT).show();
	}

	private void restorePreset(int presetNr) {
		final Map<String, Integer> presets = AVRSettings.getLevelPresets(
				LevelActivity.this, getApp().getModelConfigurator()
						.getCurrentReceiver(), presetNr);
		if (presets.isEmpty()) {
			Toast.makeText(this, getString(R.string.LongPressToPreset),
					Toast.LENGTH_LONG).show();
			return;
		}
		for (Map.Entry<LevelType, Level> e : levelSeeker.entrySet()) {
			final Integer value = presets.get(e.getKey().getId());
			if (value != null) {
				e.getValue().setCurrentValue(value.intValue());
			}
		}
		Toast.makeText(this,
				getString(R.string.PresetRestored, (presetNr + 1)),
				Toast.LENGTH_SHORT).show();
	}

	private void update(AbstractLevel level) {
		Logger.info("updated " + level.getClass().getSimpleName());
		for (Map.Entry<LevelType, Integer> e : level.getValues().entrySet()) {

			final Level l = levelSeeker.get(e.getKey());
			// nicht parametrierter Wert empfangen
			if (l != null) {
				l.updateDisplay(e.getValue());
			}
		}
	}

	private View createChannel(final LevelType ct) {
		final LinearLayout ll = new LinearLayout(this);

		ll.setOrientation(LinearLayout.VERTICAL);
		ll.setPadding(30, 0, 30, 0);
		TextView textView = new TextView(this);
		textView.setText(ct.getText());
		final LinearLayout textPanel = new LinearLayout(this);
		textPanel.setOrientation(LinearLayout.HORIZONTAL);

		textPanel.addView(textView);
		ll.addView(textPanel);
		SeekBar sb = new SeekBar(this);
		sb.setThumbOffset(8);
		sb.setPadding(8, 0, 8, 0);
		sb.setMax(ct.getMax() - ct.getMin());
		ll.addView(sb);

		levelSeeker.put(ct, new Level(ct, sb, textView));

		return ll;
	}

	@Override
	protected void onResume() {
		super.onResume();
		getApp().activityResumed(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getApp().activityPaused(this);
	}

	private CheckBox checkBoxLink;
	private boolean absoluteMode;
	private Zone zone;
	private final Handler handler = new Handler();
	private final Map<LevelType, Integer> restore = new HashMap<LevelType, Integer>();
	private final Map<LevelType, Level> levelSeeker = new HashMap<LevelType, Level>();
	private static final String LINK_CHANNELS = "LinkChannels";

}
