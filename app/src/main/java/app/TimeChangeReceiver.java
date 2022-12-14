package app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import app.info.Statistics;
import app.internal.Settings;

/*
 * this intent disabled (see manifest) because on some phones it may be recived every second!!!
 * http://stackoverflow.com/questions/16113459/timezone-changed-intent-being-received-every-few-seconds
 */

public class TimeChangeReceiver extends BroadcastReceiver
{
	public void onReceive(Context context, Intent intent)
	{
		if (!App.isLibsLoaded())
			return;

		Statistics.addLog(Settings.LOG_TIME_CHANGE);
		UpdaterService.startUpdate(UpdaterService.START_FORCE); // time change

		App.clearNotifier();
	}
}
