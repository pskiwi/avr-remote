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

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.Toast;
import de.pskiwi.avrremote.log.Logger;

public final class AboutActivity extends TabActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.infotabhost);

		initTab(R.id.panel_about, R.id.info_about, "about",
				getString(R.string.About));
		initTab(R.id.panel_faq, R.id.info_faq, "faq", getString(R.string.FAQ));
		initTab(R.id.panel_whatsnew, R.id.info_whatsnew, "whatsnew",
				getString(R.string.WhatsNew));

		final Button btnOk = (Button) findViewById(R.id.btnAboutOk);
		btnOk.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});	

		// Whatsnew nach vorne
		final Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String param = extras.getString("toShow");
			if ("whatsnew".equals(param)) {
				getTabHost().setCurrentTab(2);
			}
		}
	}

	private void initTab(int panelId, int webviewId, String toShow, String title) {
		// http://code.google.com/p/android/issues/detail?id=10789 #26
		final WebViewDatabase webViewDB = WebViewDatabase.getInstance(this);
		if (webViewDB != null) {

			TabHost tabHost = getTabHost();
			tabHost.addTab(tabHost.newTabSpec(toShow).setIndicator(title)
					.setContent(panelId));

			final WebView webview = (WebView) findViewById(webviewId);
			webview.loadUrl("file:///android_asset/" + toShow + ".html");
		} else {
			Toast.makeText(
					this,
					"Could not display [" + toShow + "]. Please reinstall App.",
					Toast.LENGTH_LONG).show();
			Logger.error("could not create WebViewDatabase [" + toShow
					+ "] issue:10789", null);
		}

	}

	private AVRApplication getApp() {
		return (AVRApplication) getApplication();
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
	}

}
