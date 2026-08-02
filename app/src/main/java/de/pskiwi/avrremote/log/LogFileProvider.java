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
package de.pskiwi.avrremote.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

/**
 * Gibt das gezippte Log als content-URI heraus. Ein FileProvider von Hand, weil
 * es den fertigen nur in AndroidX gibt (androidx.core.content.FileProvider) und
 * das Projekt bewusst ohne Abhängigkeiten auskommt - im Framework selbst
 * existiert keine FileProvider-Klasse.
 */
public final class LogFileProvider extends ContentProvider {

	public static Uri uriFor(File file) {
		return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
				.authority(AUTHORITY).appendPath(file.getName()).build();
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		if (!"r".equals(mode)) {
			throw new FileNotFoundException("read-only: " + uri);
		}
		return ParcelFileDescriptor.open(resolve(uri),
				ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public String getType(Uri uri) {
		return "application/zip";
	}

	/**
	 * Ohne DISPLAY_NAME/SIZE nennen viele Mail-Apps den Anhang "null" oder
	 * verwerfen ihn. Nicht erkannte Spalten fallen aus dem Ergebnis heraus,
	 * statt mit null aufzutauchen: fragt eine App nach MediaColumns.DATA, soll
	 * sie eine fehlende Spalte sehen und nicht einen Pfad, der null ist.
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		final File file;
		try {
			file = resolve(uri);
		} catch (FileNotFoundException x) {
			return null;
		}
		final String[] requested = projection != null ? projection
				: new String[] { OpenableColumns.DISPLAY_NAME,
						OpenableColumns.SIZE };
		final List<String> columns = new ArrayList<>(requested.length);
		final List<Object> values = new ArrayList<>(requested.length);
		for (String column : requested) {
			if (OpenableColumns.DISPLAY_NAME.equals(column)) {
				columns.add(column);
				values.add(file.getName());
			} else if (OpenableColumns.SIZE.equals(column)) {
				columns.add(column);
				values.add(file.length());
			}
		}
		final MatrixCursor cursor = new MatrixCursor(
				columns.toArray(new String[0]), 1);
		cursor.addRow(values);
		return cursor;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Genau eine Datei ist erreichbar. Der Schutz gegen Pfad-Tricks ist der
	 * Vergleich mit ZIP_NAME: der Dateiname kommt nicht aus der URI, er wird
	 * nur gegen sie geprüft. Ein Canonical-Vergleich hinterher wäre toter Code.
	 */
	private File resolve(Uri uri) throws FileNotFoundException {
		final Context ctx = getContext();
		if (ctx == null) {
			throw new FileNotFoundException("no context: " + uri);
		}
		if (uri.getPathSegments().size() != 1
				|| !SDLogger.ZIP_NAME.equals(uri.getLastPathSegment())) {
			throw new FileNotFoundException("unknown file: " + uri);
		}
		final File file = new File(SDLogger.getLogDir(ctx), SDLogger.ZIP_NAME);
		if (!file.canRead()) {
			throw new FileNotFoundException("not readable: " + uri);
		}
		return file;
	}

	public static final String AUTHORITY = "de.pskiwi.avrremote.fileprovider";

}
