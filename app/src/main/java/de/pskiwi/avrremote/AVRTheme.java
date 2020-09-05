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

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import de.pskiwi.avrremote.log.Logger;

public final class AVRTheme {

	public interface IThemeChangeListener {
		void changedTheme();
	}

	public AVRTheme(Context ctx) {
		this.ctx = ctx;
		update();
	}

	public void update() {
		theme = AVRSettings.getBackgroundTheme(ctx);
		if (theme == BackgroundTheme.Custom) {
			textColor = AVRSettings.getTextColor(ctx).getColor();
		} else {
			textColor = ctx.getResources().getColor(android.R.color.primary_text_dark);
		}
	}

	public void setTextColor(TextView tt) {
		tt.setTextColor(textColor);
	}

	public void setBackground(Activity activity) {
		final BackgroundTheme theme = AVRSettings.getBackgroundTheme(activity);
		boolean cacheOk = false;
		try {
			if (theme != BackgroundTheme.Custom) {
				// bestehenden Cache vor dem Laden der Resource löschen
				drawableLoader.clearCache();
				if (theme == BackgroundTheme.Water) {
					activity.getWindow().setBackgroundDrawable(
							activity.getResources().getDrawable(
									de.pskiwi.avrremote.R.drawable.background));
				} else {
					activity.getWindow().setBackgroundDrawableResource(
							theme.getResource());
				}
			} else {
				final String uri = AVRSettings.getCustomBackground(activity);
				cacheOk = setBackgroundByURI(activity, uri);
			}
			// hier kann es zu OutOfMemory kommen
		} catch (Throwable t) {
			Logger.error("set background failed. theme:" + theme, t);
			Toast.makeText(activity, "Theme failed. Please try again",
					Toast.LENGTH_LONG).show();
		} finally {
			if (!cacheOk) {
				drawableLoader.clearCache();
			}
		}
	}

	private boolean setBackgroundByURI(Activity activity, final String uri) {
		if (uri.length() > 0) {
			final Drawable drawable = drawableLoader.getDrawable(activity, uri);
			if (drawable != null) {
				activity.getWindow().setBackgroundDrawable(drawable);
				return true;
			} else {
				activity.getWindow().setBackgroundDrawableResource(
						theme.getResource());
			}
		}
		return false;
	}

	public Drawable getTabDrawable(Activity activity) {
		return activity.getResources().getDrawable(
				de.pskiwi.avrremote.R.drawable.background);
	}

	public int getIconAlpha() {
		if (theme == BackgroundTheme.Water) {
			return 240;
		}

		return 90;
	}

	public void saveTabSettings(TabHost tabHost) {
	}

	public void updateTabSettings(final TabHost tabHost) {
		int selected;
		int unselected;
		if (theme == BackgroundTheme.Custom || theme == BackgroundTheme.Water) {
			selected = 0x70;
			unselected = 0xb0;
		} else {
			selected = 0xff;
			unselected = 0xff;
		}

		int currentTab = tabHost.getCurrentTab();
		for (int i = 0; i < tabHost.getTabWidget().getChildCount(); i++) {
			final View childAt = tabHost.getTabWidget().getChildAt(i);
			final Drawable background = childAt.getBackground();
			if (background != null) {
				if (i == currentTab) {
					background.setAlpha(selected);
				} else {
					background.setAlpha(unselected);
				}
			} else {
				Logger.error("no tab background found", null);
			}
		}
	}

	private int textColor;
	private BackgroundTheme theme;
	private final Context ctx;
	private final DrawableLoader drawableLoader = new DrawableLoader();

}
