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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import static android.content.Context.NOTIFICATION_SERVICE;

public final class StatusbarManager {

	public StatusbarManager(AVRApplication app) {
		this.app = app;
		notificationManager = (NotificationManager) app
				.getSystemService(NOTIFICATION_SERVICE);

		int icon = R.drawable.icon;
		CharSequence tickerText = "AVR-Remote";
		long when = System.currentTimeMillis();

		notification = new Notification(icon, tickerText, when);
		notification.flags |= Notification.FLAG_ONGOING_EVENT;

		updateNotification(app, AVRRemote.class);
	}

	private void updateNotification(AVRApplication app, Class<?> cl) {
		Context context = app.getApplicationContext();
		CharSequence contentTitle = "AVR-Remote";
		CharSequence contentText = "Switch to AVR-Remote !";
		Intent notificationIntent = new Intent(app, cl);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(app, 0,
				notificationIntent, 0);

        // https://stackoverflow.com/questions/32345768/cannot-resolve-method-setlatesteventinfo
		// https://developer.android.com/guide/topics/ui/notifiers/notifications.html
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			Notification.Builder builder = new Notification.Builder(context);

			builder.setAutoCancel(false);
			builder.setContentIntent(contentIntent);
			builder.setContentTitle(contentTitle);
			builder.setContentText(contentText);
			builder.setSmallIcon(R.drawable.icon_small);
			builder.setOngoing(true);

			NotificationManager manager = (NotificationManager) app.getSystemService(NOTIFICATION_SERVICE);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			{
				String channelId = "avr_remote_channel";
				NotificationChannel channel = new NotificationChannel(
						channelId,
						"AVR-Remote",
						NotificationManager.IMPORTANCE_DEFAULT);
				manager.createNotificationChannel(channel);
				builder.setChannelId(channelId);
			}

			Notification notification=builder.build();

			manager.notify(0,notification);
		}
	}

	public void update() {
		if (AVRSettings.isShowNotification(app)) {
			notificationManager.notify(HELLO_ID, notification);
		} else {
			notificationManager.cancel(HELLO_ID);
		}
	}

	public void setCurrentIntent(Class<?> class1) {
		updateNotification(app, class1);
		update();
	}

	private NotificationManager notificationManager;
	private Notification notification;

	private final AVRApplication app;
	private static final int HELLO_ID = 1;

}
