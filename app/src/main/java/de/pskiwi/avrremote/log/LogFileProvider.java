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
import java.io.IOException;

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
 * Gibt das gezippte Log als content-URI heraus. Ein FileProvider von Hand,
 * weil das Projekt bewusst ohne AndroidX auskommt und
 * android.content.FileProvider erst ab API 34 existiert.
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
	 * verwerfen ihn.
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
		final String[] columns = projection != null ? projection
				: new String[] { OpenableColumns.DISPLAY_NAME,
						OpenableColumns.SIZE };
		final Object[] values = new Object[columns.length];
		for (int i = 0; i < columns.length; i++) {
			if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
				values[i] = file.getName();
			} else if (OpenableColumns.SIZE.equals(columns[i])) {
				values[i] = file.length();
			}
		}
		final MatrixCursor cursor = new MatrixCursor(columns, 1);
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
	 * Genau eine Datei ist erreichbar. Der Canonical-Vergleich fängt zusätzlich
	 * alles ab, was per Pfad-Trick aus dem Log-Verzeichnis herausführt.
	 */
	private File resolve(Uri uri) throws FileNotFoundException {
		final Context ctx = getContext();
		if (ctx == null) {
			throw new FileNotFoundException("no context: " + uri);
		}
		if (!SDLogger.ZIP_NAME.equals(uri.getLastPathSegment())
				|| uri.getPathSegments().size() != 1) {
			throw new FileNotFoundException("unknown file: " + uri);
		}
		final File logdir = SDLogger.getLogDir(ctx);
		final File file = new File(logdir, SDLogger.ZIP_NAME);
		try {
			if (!file.getCanonicalFile().getParentFile()
					.equals(logdir.getCanonicalFile())) {
				throw new FileNotFoundException("outside log dir: " + uri);
			}
		} catch (IOException x) {
			throw new FileNotFoundException("cannot resolve: " + uri);
		}
		if (!file.canRead()) {
			throw new FileNotFoundException("not readable: " + uri);
		}
		return file;
	}

	public static final String AUTHORITY = "de.pskiwi.avrremote.fileprovider";

}
