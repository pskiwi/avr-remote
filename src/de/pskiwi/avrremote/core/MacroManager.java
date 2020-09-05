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
package de.pskiwi.avrremote.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.os.AsyncTask;
import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.log.Logger;

public final class MacroManager {

	private final static String[] IGNORE_CMDS = { "NSE", "IPE" };

	public final class CommandRecorder implements IConnectorListener {

		public void sendData(String data) {
			if (data.endsWith("?")) {
				return;
			}
			for (String ignore : IGNORE_CMDS) {
				if (ignore.equals(data)) {
					return;
				}
			}
			cmds.add(data);
		}

		public Macro toMacro(String name) {
			final Macro macro = new Macro(name);
			for (String s : cmds) {
				macro.add("CMD", s);
			}
			return macro;
		}

		public boolean isDataRecorded() {
			return !cmds.isEmpty();
		}

		private final List<String> cmds = new ArrayList<String>();

	}

	public MacroManager(AVRApplication avrApplication) {
		this.avrApplication = avrApplication;
		read();
	}

	public void save() {
		AsyncTask<String, String, String> asyncTask = new AsyncTask<String, String, String>() {

			@Override
			protected String doInBackground(String... params) {
				doSave();
				return null;
			}

		};
		asyncTask.execute();
	}

	private void doSave() {
		try {
			Logger.info("saving macros ...");
			final PrintWriter out = new PrintWriter(avrApplication
					.openFileOutput(MACRO_FILE, 0));
			out.println("# saved : " + new Date());
			try {
				for (Macro m : macros) {
					m.save(out);
				}
			} finally {
				out.close();
			}
			Logger.info("saving macros ok.");
		} catch (Exception e) {
			Logger.error("macro save failed", e);
		}
	}

	public void read() {
		try {
			final BufferedReader r = new BufferedReader(new InputStreamReader(
					avrApplication.openFileInput(MACRO_FILE)));
			try {
				String line;
				Macro macro = null;
				macros.clear();
				while ((line = r.readLine()) != null) {
					line = line.trim();
					Logger.info("macro read [" + line + "]");
					if (line.length() > 0 && !line.startsWith("#")) {
						String[] split = line.split(":");
						if (split.length == 2) {
							String cmd = split[0].toUpperCase();
							String param = split[1];
							Logger.info("macro read [" + line + "]->[" + cmd
									+ "]=[" + param + "]");
							if (cmd.equals("NAME")) {
								macro = new Macro(param);
								macros.add(macro);
							} else {
								if (macro != null) {
									macro.add(cmd, param);
								}
							}

						} else {
							Logger.error("illegal line[" + line + "]", null);
						}
					}
				}

			} finally {
				r.close();
			}
		} catch (Exception e) {
			Logger.error("reading " + MACRO_FILE + " failed", e);
		}
	}

	public Macro getMacro(String name) {
		for (Macro m : macros) {
			if (m.getName().equals(name)) {
				return m;
			}
		}
		return null;
	}

	public List<Macro> getMacros() {
		return macros;
	}

	public void run(Macro macro) {
		macro.run(avrApplication.getConnector());
	}

	public void add(Macro macro) {
		for (Macro m : macros) {
			if (m.getName().equals(macro.getName())) {
				macro.setName(macro.getName() + "2");
			}
		}
		macros.add(macro);
		save();
	}

	public void startRecording() {
		commandRecorder = new CommandRecorder();
		avrApplication.getConnector().setConnectorListener(commandRecorder);
	}

	public boolean isMacroRecorded() {
		return commandRecorder != null && commandRecorder.isDataRecorded();
	}

	public Macro saveRecording(String name) {
		final CommandRecorder cr = commandRecorder;
		commandRecorder = null;
		avrApplication.getConnector().setConnectorListener(
				IConnectorListener.NULL_LISTENER);
		if (name != null && name.trim().length() > 0) {
			Macro macro = cr.toMacro(name.trim());
			add(macro);
			return macro;
		}
		return null;
	}

	public boolean isRecording() {
		return commandRecorder != null;
	}

	public void cancelRecording() {
		saveRecording(null);
	}

	public void remove(Macro m) {
		macros.remove(m);
		save();
	}

	public void rename(Macro m, String name) {
		if (!m.getName().equals(name)) {
			for (int i = 0; i < 3; i++) {
				if (AVRSettings.getMacro(avrApplication, i).equals(m.getName())) {
					AVRSettings.setMacro(avrApplication, i, name);
				}
			}
			m.setName(name);
			save();
		}
	}

	public void retainAll(List<String> names) {
		Logger.debug("retain [" + Arrays.toString(macros.toArray()) + "]/["
				+ Arrays.toString(names.toArray()) + "]");
		final List<Macro> toRemove = new ArrayList<Macro>();
		for (Macro m : macros) {
			if (!names.contains(m.getName())) {
				toRemove.add(m);
			}
		}
		macros.removeAll(toRemove);
		save();
		Logger.debug("retainAll [" + Arrays.toString(macros.toArray()) + "]");
	}

	private CommandRecorder commandRecorder;

	private final AVRApplication avrApplication;
	// keine Map um leichter umbenennen zu können
	private final List<Macro> macros = new CopyOnWriteArrayList<Macro> ();

	public final static String MACRO_FILE = "macros.txt";

}
