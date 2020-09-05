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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.ExpandableListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import de.pskiwi.avrremote.core.IAVRState;
import de.pskiwi.avrremote.core.ZoneState;
import de.pskiwi.avrremote.core.ZoneState.OptionGroup;
import de.pskiwi.avrremote.log.Logger;
import de.pskiwi.avrremote.menu.ExtrasMenu;

public final class OptionActivity extends ExpandableListActivity implements
		ExtrasMenu.IBaseMenuActivity {

	public AVRApplication getApp() {
		return (AVRApplication) getApplication();
	}

	public List<String> getValues(ZoneState.OptionGroup optionGroup, int nr) {

		final List<String> values = new ArrayList<String>();
		final List<IAVRState> manualStates = extrasMenu
				.getStatesForGroup(optionGroup);
		int c = 0;
		for (IAVRState s : manualStates) {
			values.add(getString(s.getDisplayId()));
			map.put(nr + "_" + c, s);
			c++;
		}

		return values;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		extrasMenu = new ExtrasMenu(this);

		final List<Map<String, String>> groupData = new ArrayList<Map<String, String>>();
		final List<List<Map<String, String>>> childData = new ArrayList<List<Map<String, String>>>();
		int i = 0;
		for (final OptionGroup ot : ZoneState.OptionGroup.values()) {
			Map<String, String> curGroupMap = new HashMap<String, String>();
			groupData.add(curGroupMap);
			curGroupMap.put(NAME, ot.getTitle());

			final List<Map<String, String>> children = new ArrayList<Map<String, String>>();
			for (String s : getValues(ot, i)) {
				final Map<String, String> curChildMap = new HashMap<String, String>();
				children.add(curChildMap);
				curChildMap.put(NAME, s);
				// curChildMap.put(DESC,"");
			}
			if (ot == OptionGroup.MISC && AVRSettings.isDevelopMode(this)) {
				final Map<String, String> curChildMap = new HashMap<String, String>();
				manualKey = i + "_" + children.size();
				children.add(curChildMap);
				curChildMap.put(NAME, "Direct command");
			}

			childData.add(children);
			i++;

		}

		// Set up our adapter
		mAdapter = new SimpleExpandableListAdapter(this, groupData,
				android.R.layout.simple_expandable_list_item_1, new String[] {
						NAME, DESC }, new int[] { android.R.id.text1,
						android.R.id.text2 }, childData,
				android.R.layout.simple_expandable_list_item_2, new String[] {
						NAME, DESC }, new int[] { android.R.id.text1,
						android.R.id.text2 });
		setListAdapter(mAdapter);
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {
		String key = groupPosition + "_" + childPosition;
		if (key.equals(manualKey)) {
			extrasMenu.showCommandMenu();
			return true;
		} else {
			IAVRState state = map.get(key);
			if (state != null) {
				Logger.info("[[" + state + "]]");
				extrasMenu.showMenu(state);
				return true;
			}
		}
		return super.onChildClick(parent, v, groupPosition, childPosition, id);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getApp().activityResumed(this);
		showing = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		getApp().activityPaused(this);
		showing = false;
	}

	public boolean isShowing() {
		return showing;
	}

	public Activity getActivity() {
		return this;
	}

	private boolean showing;
	private static final String NAME = "NAME";
	private static final String DESC = "DESC";

	private ExpandableListAdapter mAdapter;
	private ExtrasMenu extrasMenu;
	private final Map<String, IAVRState> map = new HashMap<String, IAVRState>();
	private String manualKey = "";
}