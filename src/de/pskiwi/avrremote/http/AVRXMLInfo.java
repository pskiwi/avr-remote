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
package de.pskiwi.avrremote.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.pskiwi.avrremote.core.Selection;
import de.pskiwi.avrremote.core.Zone;
import de.pskiwi.avrremote.log.Logger;

public final class AVRXMLInfo {

	public static class Input {
		public Input(String name, String renamed, boolean use) {
			this.name = name;
			this.rename = renamed;
			this.use = use;
		}

		public String getName() {
			return name;
		}

		public String getRename() {
			return rename;
		}

		public boolean isUse() {
			return use;
		}

		@Override
		public String toString() {
			return "Input [name=" + name + ", rename=" + rename + ", use="
					+ use + "]";
		}

		private final String name;
		private final String rename;
		private final boolean use;
	}

	public void add(String tag, String value) {
		List<String> list = info.get(tag);
		if (list == null) {
			list = new ArrayList<String>();
			info.put(tag, list);
		}
		list.add(value);
	}

	private String getSingleValue(String key) {
		List<String> list = info.get(key);
		if (list == null) {
			return "";
		}
		return list.get(0);
	}

	private List<String> getList(String key) {
		List<String> list = info.get(key);
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	public List<Input> getInputFunctionList() {
		final List<String> inputs = getList("InputFuncList");
		final List<String> rename = getList("RenameSource");
		final List<String> sourceDelete = getList("SourceDelete");
		// mindestens ein USE gefunden ? sonst vollständig ignorieren.
		final boolean ignoreInputUse = ignoreInputUse(sourceDelete);
		final List<Input> ret = new ArrayList<Input>();
		for (int i = 0; i < inputs.size(); i++) {
			final String name = inputs.get(i);
			final String renamed = i < rename.size() ? rename.get(i) : "";
			String cmp = i < sourceDelete.size() ? sourceDelete.get(i) : "";
			final boolean use = USE_INPUT.equals(cmp) || ignoreInputUse;
			ret.add(new Input(name, renamed.length() > 0 ? renamed : name, use));
		}
		return ret;
	}

	public List<String> getZoneRenames() {
		return getList(RENAME_ZONE);
	}

	private boolean ignoreInputUse(final List<String> sourceDelete) {
		for (String s : sourceDelete) {
			if (USE_INPUT.equalsIgnoreCase(s)) {
				return false;
			}
		}
		Logger.info("ignore input uses. #" + sourceDelete.size());
		return true;
	}

	public Selection getInputSelection() {
		int count = 0;
		for (Input i : getInputFunctionList()) {
			if (i.isUse()) {
				count++;
			}
		}
		final String[] values = new String[count];
		final String[] displayValues = new String[count];

		count = 0;
		for (Input i : getInputFunctionList()) {
			if (i.isUse()) {
				values[count] = i.getName();
				displayValues[count] = i.getRename();
				count++;
			}
		}
		return new Selection(values, displayValues);
	}

	public boolean hasPower() {
		return "ON".equalsIgnoreCase(getSingleValue("Power"));
	}

	public boolean hasZonePower() {
		return "ON".equalsIgnoreCase(getSingleValue("ZonePower"));
	}

	public String getInputSelect() {
		return getSingleValue("InputFuncSelect");
	}

	public String getNetInputSelect() {
		return getSingleValue("NetFuncSelect");
	}

	public String getInputSelectMain() {
		return getSingleValue("InputFuncSelectMain");
	}

	public String getVolumeDisplay() {
		return getSingleValue("VolumeDisplay");
	}

	public String getMasterVolume() {
		return getSingleValue("MasterVolume");
	}

	public boolean isMute() {
		return "ON".equalsIgnoreCase(getSingleValue("Mute"));
	}

	public boolean isDefined() {
		return !info.isEmpty();
	}

	public String getInfo() {
		final StringBuilder ret = new StringBuilder();
		ret.append("XMLINFO (\n");
		for (Map.Entry<String, List<String>> e : info.entrySet()) {
			ret.append("     [" + e.getKey() + "] :"
					+ Arrays.toString(e.getValue().toArray()) + "\n");
		}
		ret.append(")");
		return ret.toString();
	}

	@Override
	public String toString() {
		return isDefined() ? "defined:" + info.size() : "not defined";
	}

	public void mergeQuickSelect(Zone z, AVRXMLInfo s) {
		for (String name : s.getList(QUICK_SELECT_NAME)) {
			add(QUICK_SELECT_NAME + z.getZoneNumber(), name);
		}
	}

	public List<String> getQuickNames(Zone z) {
		return getList(QUICK_SELECT_NAME + z.getZoneNumber());
	}

	public void merge(Zone z, AVRXMLInfo s) {
		if (z == Zone.Main) {
			info.clear();
			info.putAll(s.info);
			List<String> l = getList(RENAME_ZONE);
			if (l.isEmpty()) {
				l.add("");
			}
		} else {
			List<String> list = s.getList(RENAME_ZONE);
			if (list.isEmpty()) {
				list.add("");
			}
			getList(RENAME_ZONE).addAll(list);

			for (String name : s.getQuickNames(z)) {
				add(QUICK_SELECT_NAME + z.getZoneNumber(), name);
			}
		}
	}

	private final Map<String, List<String>> info = new HashMap<String, List<String>>();
	public static final String RENAME_ZONE = "RenameZone";
	public static final String QUICK_SELECT_NAME = "QuickSelectName";
	private static final String USE_INPUT = "USE";

}
