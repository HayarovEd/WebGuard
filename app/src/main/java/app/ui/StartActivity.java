package app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import app.App;
import app.AppService;
import app.internal.Preferences;
import app.Res;
import app.info.Statistics;
import app.internal.Settings;


public class StartActivity extends Activity
{
	private static boolean hashForced = false;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(Res.layout.start_activity);

		//L.a(Settings.TAG_STARTACTIVITY, "start");

		if (Settings.DEBUG_WAIT_ON_START)
			Debug.waitForDebugger();

		if (!App.startProcessed)
		{
			App.startProcessed = true;
			App.deviceBooted = true;
			App.vpnActivated = false;
		}

		if (Settings.EVENTS_LOG)
			if (!App.initedAll()) Statistics.addLogFast(Settings.LOG_INIT_TIMEOUT + "0"); // logs may be not inited

		if (App.isFirstRun() && !hashForced)
		{
			hashForced = true;
			// TODO XXX logs may be not inited
			AppService.hashAllApps(this); // hash all apps on first run
		}

		Intent is = getIntent();
		String action = is.getAction();
		//action = "autoStart";

		if (!App.isLibsLoaded())
		{
			// ooups, native libs loading error
			Dialogs.showInstallError(null);
		} /*else {
			Intent i = LicenseActivity.getIntent(this, false);
			if ("updateStart".equals(action))
				i.putExtra("active", true);
			startActivity(i);
		}*/
		else if (Settings.EULA_ASK &&
					(Preferences.policyUpdated() || Preferences.termsUpdated() ||
					Settings.DEBUG_APP_LICENSE_CHECK))
		{
			Preferences.putBoolean(Settings.PREF_ACTIVE, false);

			Intent i = LicenseActivity.getIntent(this, false);
			if ("updateStart".equals(action))
				i.putExtra("active", true);
			startActivity(i);
		}
		else
		{
			if (Settings.SHOW_BOOT_SCREEN && "autoStart".equals(action))
			{
				Intent i = PromptActivity.getIntent(this, false);
				i.setAction("autoStart");
				startActivity(i);
			}
			else if ("updateStart".equals(action))
			{
				// if we have wifi now we need to reconnect to get local DNSes
//				  if (NetUtil.getStatus() == 1)
//					  WiFi.reconnect(context);

				App.startVpnService(this);
			}
			else
			{
				OptionsActivity.start(this);
			}
		}

		finish();
	}

	public static void start(Context context)
	{
		Intent intent = new Intent(context, StartActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		context.startActivity(intent);
	}

	public static Intent getIntent(Context context, boolean newTask)
	{
		int flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
		if (newTask)
			flags |= (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//			//Intent.FLAG_ACTIVITY_CLEAR_TOP
//
		Intent intent = new Intent(context, StartActivity.class);
		if (flags != 0)
			intent.setFlags(flags);

		return intent;
	}
}
