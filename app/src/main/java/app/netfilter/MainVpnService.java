package app.netfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import app.Res;


public class MainVpnService extends Service {

	public static final int NOTIFICATION_ID = 1337;

	private static final String startAction = "start";
	private static final String stopAction  = "stop";

	static void startService(Context context, Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

	}

	public static void start(Context context) {
		Intent i = new Intent(context, MainVpnService.class);
		i.setAction(startAction);
		startService(context, i); //context.startService(i);
	}

	public static void stop(Context context) {
		Intent i = new Intent(context, MainVpnService.class);
		i.setAction(stopAction);
		startService(context, i); //context.startService(i);
	}

	public static Notification getNotification(Context context) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

		// set minimal priority, so android may not show us
		builder.setPriority(-2); // TODO XXX Notification.PRIORITY_MIN constant from api16
		if (Build.VERSION.SDK_INT >= 18) {
			// < Android 4.3 allow use notification without icon, and don't show it
			// TODO XXX also we can use transparent icon. AHAHA!
			builder.setSmallIcon(Res.drawable.icon_24);
			builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon));
		}

		//.setSmallIcon(Res.drawable.icon_24)
		//.setContentTitle(context.getText(Res.string.app_name))
		//.setContentText(context.getText(active ? Res.string.active : Res.string.disabled));

		final Notification n = builder.build();
		return n;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	public static Notification getNotification29(Context context) {
		String NOTIFICATION_CHANNEL_ID = "app.webguard";
		String channelName = "WebGuard Service";

		NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);

//		channel.setDescription(channelName);
//		channel.setLightColor(Color.BLUE);
//		channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		assert manager != null;
		manager.createNotificationChannel(channel);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
		builder.setPriority(NotificationManager.IMPORTANCE_HIGH);
//		builder.setSmallIcon(Res.drawable.icon_24);
//		builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon));


		return builder.setOngoing(true)
//				.setChannelId(NOTIFICATION_CHANNEL_ID)
//				.setSmallIcon(Res.drawable.icon_24)
//				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
//				.setContentTitle("App is running in background")
				.setPriority(NotificationManager.IMPORTANCE_HIGH)
				.setCategory(Notification.CATEGORY_SERVICE)
//				.setContentIntent(pendingIntent)
				.build();
	}

	//

	public MainVpnService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = (intent == null) ? null : intent.getAction();


		if (startAction.equals(action)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				Notification n = getNotification29(getApplicationContext());
				startForeground(NOTIFICATION_ID, n);
			} else {
				Notification n = getNotification(this);
				startForeground(NOTIFICATION_ID, n);
			}
		} else if (stopAction.equals(action)) {
			stopSelf();
		}

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}
	}


}
