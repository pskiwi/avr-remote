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

import java.util.LinkedList;
import java.util.List;

public final class Selection {

	public interface ISelectionListener {
		// ACHTUNG: wird im Hintergrund aufgerufen
		void selectionChanged(String[] values);
	}

	public Selection(String... values) {
		this(IParameterConverter.ID_TRANSFORMER, values);
	}

	public Selection(IParameterConverter converter, String... values) {
		this(converter, values, values);
	}

	public Selection(String[] values, String[] displayValues) {
		this(IParameterConverter.ID_TRANSFORMER, values, displayValues);
	}

	public Selection(IParameterConverter converter, String[] values,
			String[] displayValues) {
		this.converter = converter;
		this.values = values;
		this.displayValues = displayValues;
	}

	public String[] getValues() {
		if (converter != IParameterConverter.ID_TRANSFORMER) {
			final String[] ret = new String[values.length];
			for (int i = 0; i < values.length; i++) {
				ret[i] = converter.convert(values[i]);
			}
			return ret;
		}
		return values;
	}

	public String[] getDisplayValues() {
		return displayValues;
	}

	public void update(Selection selection) {
		// Dann lieber Standard behalten
		if (selection.isEmpty()) {
			return;
		}
		this.values = selection.values;
		this.displayValues = selection.displayValues;
		for (ISelectionListener l : listener) {
			l.selectionChanged(selection.values);
		}
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (ret.length() > 0) {
				ret.append(", ");
			}
			ret.append(values[i] + "=" + displayValues[i]);
		}
		return ret.toString();
	}

	public void addListener(ISelectionListener l) {
		listener.add(l);
		l.selectionChanged(values);
	}

	public String getDisplay(String selected) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equalsIgnoreCase(selected)) {
				return displayValues[i];
			}
		}
		return selected;
	}

	public boolean isEmpty() {
		return values.length == 0;
	}

	private String[] values;
	private String[] displayValues;
	private final IParameterConverter converter;
	private final List<ISelectionListener> listener = new LinkedList<ISelectionListener>();

}
