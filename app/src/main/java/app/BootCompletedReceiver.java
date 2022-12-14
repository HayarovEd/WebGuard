package app;

import app.internal.Preferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import app.info.Statistics;
import app.internal.Settings;
import app.ui.StartActivity;

// may also receive ACTION_USER_PRESENT or ACTION_MEDIA_MOUNTED?

public class BootCompletedReceiver extends BroadcastReceiver {

	public void onReceive(Context context, Intent intent) {

		String action = intent.getAction();

		if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
				!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) &&
				!"android.intent.action.QUICKBOOT_POWERON".equals(action) &&
				!Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
			return;
		}

		if (App.deviceBooted) {
			return;
		}

		//L.a(Settings.TAG_BOOTCOMPLETEDRECEIVER, "start");
		//L.a(Settings.TAG_BOOTCOMPLETEDRECEIVER, "start ", action);
		App.deviceBooted = true;

		if (Settings.DEBUG_WAIT_ON_START) {
			Debug.waitForDebugger();
		}

		Statistics.addLogFast(Settings.LOG_BOOT);

		if (Preferences.isActive()) {

			// TODO XXX don't invoke StartActivity if already show it or will be 'Activity pause timeout'
			// TODO XXX if BootCompletedReceiver -> startActivity(i) -> NetworkStateChangeReceiver -> startActivity(i)???

			if (!App.startProcessed) {
				Intent i = StartActivity.getIntent(context, true);
				i.setAction("autoStart");
				context.startActivity(i);
			}

			if (!App.isLibsLoaded()) {
				return;
			}

			// other actions

			boolean need = Preferences.get(Settings.PREF_CLEARCACHES_NEED);
			if (need) {
				App.cleanCaches(true, false, true, false); // clean+kill was requested (make only clean)
			}

			return;
		}

		//
		App.showDisabledNotification(context);
	}
}
