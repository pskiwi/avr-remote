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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import de.pskiwi.avrremote.log.Logger;

/** Laden von Drawables über URI mit Cache für ein Element */
public final class DrawableLoader {

	public void clearCache() {
		Logger.info("DrawableLoader:clear cache");
		cachedDrawable = null;
		cachedURI = null;
	}

	// Laden mit Cache
	public Drawable getDrawable(Context ctx, String uri) {
		// passt der Cache ?
		if (uri != null && uri.equals(cachedURI) && cachedDrawable != null) {
			Logger.info("DrawableLoader:got drawble [" + uri + "] from cache");
			return cachedDrawable;
		}
		// vorsorglich löschen, um nicht in einen falschen Status zu geraten
		clearCache();

		cachedDrawable = loadImageFromUri(ctx, uri);
		cachedURI = uri;
		return cachedDrawable;
	}

	private static Drawable loadImageFromUri(Context ctx, String uri) {
		try {
			Logger.info("DrawableLoader:load image from [" + uri + "] ...");
			final Drawable drawable = Drawable
					.createFromStream(ctx.getContentResolver().openInputStream(
							Uri.parse(uri)), null);
			Logger.info("DrawableLoader:loaded image from [" + uri + "] : " + drawable);
			return drawable;

		} catch (Throwable e) {

			Logger.error("DrawableLoader:load uri [" + uri + "] failed", e);

			return null;
		}

	}

	private String cachedURI;
	private Drawable cachedDrawable;

}
