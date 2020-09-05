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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.util.Xml;
import de.pskiwi.avrremote.AVRApplication;
import de.pskiwi.avrremote.core.ZoneState.InputSelect;
import de.pskiwi.avrremote.core.ZoneState.SurroundMode;
import de.pskiwi.avrremote.log.Logger;

public final class RenameService {

	public final class QuickSelect {
		QuickSelect(Zone zone) {
			final List<String> v = new ArrayList<String>();
			for (int i = 0; i < QUICK_COUNT; i++) {
				final RenameEntry re = check("Quick_" + zone + "_" + i,
						"Quick " + zone.getId() + " " + i, RenameCategory.QUICK);
				if (!re.isDelete()) {
					// 1. bekommt die "1" -> korrekt für QUICK
					translate[v.size()] = i + 1;
					v.add(re.getTarget());

				}
			}
			values = v.toArray(new String[v.size()]);
		}

		// Anzuzeigende ELemente
		public String[] getValues() {
			return values;
		}

		// Angezeigtes Element Nr. X entspricht welchem Quick-Mode ?
		public int translate(int which) {
			return translate[which];
		}

		private final String[] values;
		private final int[] translate = new int[QUICK_COUNT];
	}

	public final static class RenameResult implements Comparable<RenameResult> {
		public RenameResult(String key, String value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		public int compareTo(RenameResult another) {
			return value.toUpperCase().compareTo(another.value.toUpperCase());
		}

		@Override
		public String toString() {
			return "[" + key + "]->[" + value + "]";
		}

		private final String key;
		private final String value;

	}

	private final class XMLHandler extends DefaultHandler {
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			if (TAG_ENTRY.equals(localName)) {
				RenameEntry entry = new RenameEntry(attributes);
				entries.put(entry.source, entry);
			}
		}

		public Map<String, RenameEntry> getEntries() {
			return entries;
		}

		private final Map<String, RenameEntry> entries = new HashMap<String, RenameEntry>();

	}

	public enum RenameCategory {
		SOURCE, ZONE, PRESET, UNKNOWN, ALL, MODE, QUICK
	}

	public final static class RenameEntry implements Comparable<RenameEntry> {

		public RenameEntry(Attributes attributes) {
			source = attributes.getValue(ATTR_SOURCE);
			target = attributes.getValue(ATTR_TARGET);
			delete = "true".equalsIgnoreCase(attributes.getValue(ATTR_DELETE));
			category = RenameCategory.valueOf(attributes
					.getValue(ATTR_CATEGORY));
		}

		public RenameEntry(String key, String value, RenameCategory category) {
			this.source = key;
			this.target = value;
			this.delete = false;
			this.category = category;
		}

		public void export(XmlSerializer serializer)
				throws IllegalArgumentException, IllegalStateException,
				IOException {
			serializer.startTag(null, TAG_ENTRY);
			serializer.attribute(null, ATTR_SOURCE, source);
			serializer.attribute(null, ATTR_TARGET, target);
			serializer.attribute(null, ATTR_DELETE, "" + delete);
			serializer.attribute(null, ATTR_CATEGORY, "" + category);
			serializer.endTag(null, TAG_ENTRY);
		}

		public String toString() {
			return "[" + source + "] -> [" + target + "] "
					+ (delete ? "(don't show)" : "");
		}

		public String getSource() {
			return source;
		}

		public String getTarget() {
			return target;
		}

		public boolean isDelete() {
			return delete;
		}

		public void setTarget(String target) {
			this.target = target;
		}

		public void setDelete(boolean delete) {
			this.delete = delete;
		}

		public RenameCategory getCategory() {
			return category;
		}

		public int compareTo(RenameEntry another) {
			return source.toUpperCase().compareTo(another.source.toUpperCase());
		}

		public boolean isRenamed() {
			return !source.equals(target) || delete;
		}

		public boolean isDefined() {
			return delete || target.length() > 0;
		}

		private String source = "";
		private String target = "";
		private boolean delete = false;
		private RenameCategory category = RenameCategory.UNKNOWN;
	}

	public RenameService(Context ctx) {
		this.ctx = ctx;
		load();
	}

	public void save() {
		if (!dirty) {
			return;
		}

		// AsyncTask führt in Hintergrund-Threads zu
		// --java.lang.RuntimeException: Can't create handler inside thread that
		// has not called Looper.prepare()
		final Thread thread = new Thread("RenameThreadSave") {
			public void run() {
				doSave();
			};
		};
		thread.start();
	}

	private void doSave() {
		try {
			Logger.info("RenameService save #" + entries.size() + " dirty:"
					+ dirty);
			if (!dirty) {
				return;
			}
			final FileOutputStream fileos = ctx.openFileOutput(RENAMES_XML,
					Context.MODE_PRIVATE);
			try {

				// we create a XmlSerializer in order to write xml data
				final XmlSerializer serializer = Xml.newSerializer();
				serializer
						.setFeature(
								"http://xmlpull.org/v1/doc/features.html#indent-output",
								true);
				serializer.setOutput(fileos, "UTF-8");
				serializer.startDocument(null, Boolean.TRUE);
				serializer.startTag(null, TAG_RENAME);
				for (RenameEntry re : entries.values()) {
					re.export(serializer);
				}
				serializer.endTag(null, TAG_RENAME);

				// write xml data into the FileOutputStream
				serializer.flush();
				// finally we close the file stream
			} finally {
				fileos.close();
			}
			dirty = false;
		} catch (Exception x) {
			Logger.error("save renames failed", x);
		}
	}

	public synchronized void load() {

		SAXParserFactory factory = SAXParserFactory.newInstance();
		final XMLHandler xmlHandler = new XMLHandler();
		try {
			final SAXParser parser = factory.newSAXParser();
			File checkFile = new File(ctx.getFilesDir(), RENAMES_XML);
			if (checkFile.exists()) {
				parser.parse(ctx.openFileInput(RENAMES_XML), xmlHandler);
				entries.clear();
				for (Map.Entry<String, RenameEntry> e : xmlHandler.getEntries()
						.entrySet()) {
					if (e.getValue().isDefined()) {
						entries.put(e.getKey(), e.getValue());
					}
				}

			} else {
				Logger.info("rename file not found:" + RENAMES_XML);
			}
		} catch (Exception e) {
			Logger.error("read failed", e);
		}
		Logger.info("RenameService loaded #" + entries.size());
		dirty = false;
	}

	// wird u.a. aus verschiedenen Threads aufgerufen
	public synchronized RenameEntry check(String key, String value,
			RenameCategory category) {
		final RenameEntry e = entries.get(key);
		if (e != null) {
			return e;
		}
		final RenameEntry renameEntry = new RenameEntry(key, value, category);
		entries.put(key, renameEntry);
		markDirty();
		return renameEntry;
	}

	/**
	 * Ergebnis der Umbennenung. Falls als geslöcht markiert, wird key
	 * zurückgeliefert.
	 */
	public String rename(String key, String value, RenameCategory category) {
		final RenameEntry e = check(key, value, category);
		if (e.delete) {
			return e.source;
		}
		return e.target;
	}

	public RenameEntry[] getAll(RenameCategory renameCategory) {
		final List<RenameEntry> ret = new ArrayList<RenameEntry>();
		for (RenameEntry c : entries.values()) {
			if (renameCategory == RenameCategory.ALL
					|| c.getCategory() == renameCategory) {
				ret.add(c);
			}
		}
		Collections.sort(ret);
		return ret.toArray(new RenameEntry[ret.size()]);
	}

	public List<RenameResult> adapt(Selection selection, RenameCategory category) {
		return adapt(selection.getValues(), selection.getDisplayValues(),
				category);
	}

	public List<RenameResult> adapt(String[] values, String[] displayValues,
			RenameCategory category) {
		final List<RenameResult> ret = new ArrayList<RenameResult>();
		for (int i = 0; i < values.length; i++) {
			final String key = values[i];
			final String value = displayValues[i];
			final RenameEntry c = check(key, value, category);
			if (!c.isDelete()) {
				String toDisplay = c.getTarget();
				// u.U. kommt vom Receiver etwas anderes
				// Umbennung auf Default ist damit nicht möglich !
				if (toDisplay.equals(c.getSource())) {
					toDisplay = value;
				}
				ret.add(new RenameResult(c.getSource(), toDisplay));
			}
		}
		Collections.sort(ret);
		return ret;
	}

	public synchronized void reset() {
		Logger.info("reset renames");
		entries.clear();
		markDirty();
		save();
	}

	public void checkDefaults(AVRApplication app) {
		Logger.info("**********RenameService init Defaults");
		InputSelect state = app.getAvrState().getState(Zone.Main,
				InputSelect.class);
		adapt(state.getSelection(), RenameCategory.SOURCE);

		SurroundMode smode = app.getAvrState().getState(Zone.Main,
				SurroundMode.class);
		adapt(smode.getSelection(), RenameCategory.MODE);

		for (Zone z : Zone.values()) {
			getQuickSelect(z);
		}
	}

	public QuickSelect getQuickSelect(Zone zone) {
		return new QuickSelect(zone);
	}

	public CharSequence getZoneName(Zone z) {
		return rename(z.getRenameKey(), ctx.getString(z.getRessource()),
				RenameCategory.ZONE);
	}

	public void setZoneName(Zone zone, String s) {
		final RenameEntry ret = check(zone.getRenameKey(), s,
				RenameCategory.ZONE);
		if (!s.equals(ret.target)) {
			dirty = true;
		}
		ret.target = s;
	}

	public void setQuickName(Zone zone, List<String> list) {
		int i = 0;
		for (String name : list) {
			if (name.length() > 0) {
				final String key = "Quick_" + zone + "_" + i;
				final RenameEntry ret = check(key, name, RenameCategory.QUICK);
				if (!name.equals(ret.getTarget())) {
					dirty = true;
				}
				ret.setTarget(name);
			}
			i++;
		}

	}

	public static void dump(Context ctx, PrintWriter out) {
		final RenameService rs = new RenameService(ctx);
		if (rs.entries.isEmpty()) {
			out.println("No Renames");
		} else {
			out.println("Rename Service");
			out.println("--------------");
			for (Map.Entry<String, RenameEntry> e : rs.entries.entrySet()) {
				if (e.getValue().isRenamed()) {
					out.println("[" + e.getKey() + "] -> " + e.getValue());
				}
			}
			out.println("--------------");
		}
	}

	public void markDirty() {
		dirty = true;
	}

	private boolean dirty = true;

	private final Context ctx;

	// Concurrent, weil Elemente tlw. im Hintergrund hinzugefügt werden
	private final Map<String, RenameEntry> entries = new ConcurrentHashMap<String, RenameEntry>();

	private static final String TAG_ENTRY = "Entry";
	private static final String TAG_RENAME = "RenameList";

	private static final String ATTR_SOURCE = "source";
	private static final String ATTR_TARGET = "target";
	private static final String ATTR_DELETE = "delete";
	private static final String ATTR_CATEGORY = "category";

	private static final String RENAMES_XML = "avrrenames.xml";

	public static final int QUICK_COUNT = 5;
}
