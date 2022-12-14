package app.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import app.App;
import app.Res;
import app.common.Utils;
import app.common.debug.L;
import app.ScreenStateReceiver;
import app.TimerService;
import app.info.Statistics;
import app.internal.Preferences;
import app.internal.Settings;

public class Notifications
{

    private static final String CHANNEL_ID      = "app.webguard.channel_id";

	public static final String DISABLED_ACTION	= "disabled";
	public static final String NOPROXY_ACTION	= "noproxy";
	public static final String NEWS_ACTION		= "news";
	public static final String EVALUATE_ACTION	= "evaluate";
	public static final String FEEDBACK_ACTION	= "feedback";
	public static final String FEEDBACK2_ACTION = "feedback2";
	public static final String FIRSTRES_ACTION	= "firstres";
	public static final String LICERR_ACTION	= "licerr";
	public static final String NEEDUP_ACTION	= "needup";

	public static final String NEEDUPDATE_BLOCK_ACTION		 = "updateblock";
	public static final String NEEDUPDATE_UPDATE_ACTION		 = "updateupdate";
	public static final String NEEDUPDATE_BLOCKFINAL_ACTION  = "updatefinalblock";
	public static final String NEEDUPDATE_UPDATEFINAL_ACTION = "updatefinalupdate";

	public static final String BUY_ACTION		= "buy";


	public static void showSubsExpired() {
		Utils.startNotification(App.getContext(), Res.string.app_name, Res.string.subs_expired,
									Res.string.app_name, Res.string.subs_expired_text,
									StatisticsActivity.getWorkStatsSmallText(true),
									BUY_ACTION, "subs_expired".hashCode(), true, CHANNEL_ID + "_08");

		Statistics.addLog(Settings.LOG_NOTIFY_SUBSEXP);
	}

	public static void showSubsExpiredFree() {
		int recovery = Preferences.get_i(Settings.PREF_RECOVERY_STATUS);

		// for old users (see isFreeUser) show subsctiption end text with promo

		Utils.startNotification(App.getContext(), Res.string.app_name, Res.string.subs_expired,
									Res.string.app_name, ((recovery == 1) ? Res.string.freeuser_end : Res.string.subs_expired_free_text),
									StatisticsActivity.getWorkStatsSmallText(true),
									BUY_ACTION, "subs_expired".hashCode(), true, CHANNEL_ID + "_09");

		Statistics.addLog(Settings.LOG_NOTIFY_FREEEXP);
	}

	public static void showBackgroundDataError() {
		// don't show error about permission denied to send network data if screen off
		// because many new phones use power manager that disables all background data when screen is off
		if (!ScreenStateReceiver.isScreenOn())
			return;

		Utils.startNotification(App.getContext(), Res.string.error, Res.string.error_background_data,
									Res.string.app_name, Res.string.please_enable_background_data, null,
									null, "background_data".hashCode(), false, CHANNEL_ID + "_10");

		Statistics.addLog(Settings.LOG_NOTIFY_BGERR);
	}

	// updateblock - 1, updateupdate - 2, updatefinalblock - 3, updatefinalupdate - 4
	public static void showNeedUpdate(int type) {
		if (type < 1 || type > 4)
			return;

		int textId = 0;
		int dialogTextId = 0; // TODO XXX not need, we check type and get text from resources, see Dialogs.showNeedUpdate
		String dlgType = null;

		switch (type)
		{
		case 1:
			textId = Res.string.need_update_block;
			dialogTextId = Res.string.need_update_block_text;
			dlgType = NEEDUPDATE_BLOCK_ACTION;
			break;

		case 2:
			textId = Res.string.need_update;
			dialogTextId = Res.string.need_update_text;
			dlgType = NEEDUPDATE_UPDATE_ACTION;
			break;

		case 3:
			textId = Res.string.need_finalupdate_block;
			dialogTextId = Res.string.need_finalupdate_block_text;
			dlgType = NEEDUPDATE_BLOCKFINAL_ACTION;
			break;

		case 4:
			textId = Res.string.need_finalupdate;
			dialogTextId = Res.string.need_finalupdate_text;
			dlgType = NEEDUPDATE_UPDATEFINAL_ACTION;
			break;
		}

		Utils.startNotification(App.getContext(), Res.string.app_name, textId,
									Res.string.app_name, dialogTextId, null,
									dlgType, NEEDUP_ACTION.hashCode(), true, CHANNEL_ID + "_11");

		Statistics.addLog(Settings.LOG_NOTIFY_NEEDUP);
	}

	// not converted

	public static void showNews(Context context, String textExtra) {

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon_24)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
				.setContentTitle(context.getText(Res.string.app_updated))
				.setContentText(context.getText(Res.string.tap_to_read_news))
				.setAutoCancel(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false);
		resultIntent.putExtra(NEWS_ACTION, textExtra);

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class);

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(NEWS_ACTION.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_01"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on
			notificationManager.notify(NEWS_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_NEWS);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_NEWS_ERROR);

		}

	}

	// not converted

	// notification to ask evaluate app in GP (isPaid - different text)
	public static void showEvaluate(Context context, boolean isPaid) {
		L.d(Settings.TAG_NOTIFICATIONS, "showEvaluate");

		// update stats first
		Preferences.putLong(Settings.PREF_EVALUATE_TIME, 0);
		Preferences.putInt(Settings.PREF_EVALUATE_STATUS, 1); // notification started

		if (!Preferences.isActive()) {
			return; // not show if protection disabled
		}

		//

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon_24)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
				.setContentTitle(context.getText(Res.string.evaluate_app))
				.setAutoCancel(true);

		builder.setDefaults(Notification.DEFAULT_ALL).setOnlyAlertOnce(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false);
		resultIntent.putExtra(EVALUATE_ACTION, context.getText((isPaid) ? Res.string.evaluate_app_paid : Res.string.evaluate_app_free) + StatisticsActivity.getWorkStatsSmallText(true));

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class);

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(EVALUATE_ACTION.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_02"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(EVALUATE_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_EVALUATE);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_EVALUATE_ERROR);

		}
	}

	// not converted

	/*
	 * notification to ask send us message if user have problems
	 * user must click notification or show notification again (up 2 times with 60 min interval)
	 * for Android 8 - https://stackoverflow.com/questions/43093260/notification-not-showing-in-oreo
	 */
	public static void showFeedback(Context context) {

		L.d(Settings.TAG_NOTIFICATIONS, "showFeedback");

		// update stats first
		Preferences.putLong(Settings.PREF_FEEDBACK_TIME, 0);
		int status = Preferences.get_i(Settings.PREF_FEEDBACK_STATUS);
		Preferences.putInt(Settings.PREF_FEEDBACK_STATUS, status + 1); // try to show notification (1, 2, 3)
		TimerService.feedbackNotifyInitTimer();

		//
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon_24)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
				.setContentTitle(context.getText(Res.string.feedback_app))
				.setAutoCancel(true);

		builder.setDefaults(Notification.DEFAULT_ALL).setOnlyAlertOnce(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false);
		resultIntent.putExtra(FEEDBACK_ACTION, context.getText(Res.string.feedback_app_full));

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class);

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(FEEDBACK_ACTION.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_03"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(FEEDBACK_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_FEEDBACK);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_FEEDBACK_ERROR);

		}

	}

	// not converted

	/*
	 * notification to show user protection results
	 * user must click notification or show notification again (up 5 times)
	 */
	public static void showFirstResult(Context context) {

		L.d(Settings.TAG_NOTIFICATIONS, "showFirstResult");

		// update stats first
		Preferences.putLong(Settings.PREF_FIRSTRESULT_TIME, 0);
		int status = Preferences.get_i(Settings.PREF_FIRSTRESULT_STATUS);
		Preferences.putInt(Settings.PREF_FIRSTRESULT_STATUS, status + 1);  // try to show notification (1, 2, 3, 4, 5)
		TimerService.firstResultNotifyInitTimer();

		//
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon_24)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
				.setContentTitle(context.getText(Res.string.first_result))
				.setAutoCancel(true);

		builder.setDefaults(Notification.DEFAULT_ALL).setOnlyAlertOnce(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false);
		resultIntent.putExtra(FIRSTRES_ACTION, context.getText(Res.string.first_result_full) + StatisticsActivity.getWorkStatsSmallText(true));

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class);

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(FIRSTRES_ACTION.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_04"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(FIRSTRES_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_FIRSTRESULT);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_FIRSTRESULT_ERROR);

		}


	}

	// not converted

	// can't be clicked
	public static void showLicenseCheckError(Context context) {

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon_24)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
				.setContentTitle(context.getText(Res.string.app_name))
				.setContentText(context.getText(Res.string.purchase_check_error))
				.setAutoCancel(true);

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_05"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(LICERR_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_LICERR);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_LICERR_ERROR);

		}

	}

	public static void hideLicenseCheckError(Context context) {
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		notificationManager.cancel(LICERR_ACTION.hashCode());
	}

	// not converted

	public static void showProxySelectError(Context context, int titleId, int textId, int dialogTextId) {

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.znak_small)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.znak))
				.setContentTitle(context.getText(titleId))
				.setContentText(context.getText(textId))
				.setOngoing(true)
				.setAutoCancel(false)
				//.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
				.setDefaults(Notification.DEFAULT_ALL).setOnlyAlertOnce(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false); // TODO XXX OptionsActivity.class
		resultIntent.putExtra(Utils.DIALOG_TEXT, dialogTextId);
		resultIntent.putExtra(Utils.DIALOG_TITLE, titleId);

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class);					 // TODO XXX OptionsActivity.class

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(NOPROXY_ACTION.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_06"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(NOPROXY_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_PROXYERR);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_PROXYERR_ERROR);

		}

	}

	public static void hideProxySelectError(Context context) {
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(NOPROXY_ACTION.hashCode());
	}

	// not converted

	public static void showAppDisabledNotify(Context context) {

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon_24)
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
				.setContentTitle(context.getText(Res.string.app_name))
				.setContentText(context.getText(Res.string.enable_my_app))
				.setAutoCancel(true)
				.setDefaults(Notification.DEFAULT_ALL).setOnlyAlertOnce(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false); // TODO XXX OptionsActivity.class
		resultIntent.putExtra(Utils.DIALOG_TITLE, Res.string.app_name);
		resultIntent.putExtra(Utils.DIALOG_TEXT, Res.string.long_time_dont_use_text);

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class); // TODO XXX OptionsActivity.class

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(DISABLED_ACTION.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

				String channel_id = CHANNEL_ID + "_07"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(DISABLED_ACTION.hashCode(), builder.build());

			Statistics.addLog(Settings.LOG_NOTIFY_APPDISABLED);

		} else {

			Statistics.addLog(Settings.LOG_NOTIFY_APPDISABLED_ERROR);

		}

	}


}
