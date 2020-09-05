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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import de.pskiwi.avrremote.core.RenameService;
import de.pskiwi.avrremote.core.RenameService.RenameCategory;
import de.pskiwi.avrremote.core.RenameService.RenameEntry;

public final class RenameActivity extends ListActivity {

	private AVRApplication getApp() {
		return (AVRApplication) getApplication();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		renameService = getApp().getRenameService();
		setContentView(R.layout.rename);
		updateModel();
		Spinner spinner = (Spinner) findViewById(R.id.categorySpinner);
		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> v, View arg1,
					int position, long id) {
				String cat = ("" + v.getItemAtPosition(position)).toUpperCase();

				renameCategory = RenameCategory.valueOf(cat);
				updateModel();
			}

			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	}

	private void updateModel() {
		arrayAdapter = new ArrayAdapter<RenameEntry>(this,
				android.R.layout.simple_list_item_1, renameService
						.getAll(renameCategory));
		setListAdapter(arrayAdapter);
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
		getApp().getRenameService().save();
	}

	@Override
	protected void onStop() {
		super.onStop();
		getApp().reconfigure();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		final RenameEntry item = arrayAdapter.getItem(position);
		LayoutInflater factory = LayoutInflater.from(this);
		final View textEntryView = factory.inflate(R.layout.rename_edit, null);
		final EditText text = (EditText) textEntryView
				.findViewById(R.id.rename);
		text.setText(item.getTarget());
		final CheckBox delete = (CheckBox) textEntryView
				.findViewById(R.id.delete);
		delete.setChecked(item.isDelete());
		new AlertDialog.Builder(this).setTitle(
				getString(R.string.renameItem) + " " + item.getSource())
				.setView(textEntryView).setPositiveButton(R.string.OK,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								item.setTarget(text.getText().toString());
								item.setDelete(delete.isChecked());
								renameService.markDirty();
								updateModel();
							}
						}).create().show();

	}

	private RenameCategory renameCategory = RenameCategory.ALL;
	private ArrayAdapter<RenameEntry> arrayAdapter;
	private RenameService renameService;

}
