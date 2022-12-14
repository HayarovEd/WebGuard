package app;

import app.internal.Preferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import app.internal.Settings;

/*
 * adb shell
 * am broadcast -a com.android.vending.INSTALL_REFERRER -n app.webguard/app.ReferalReceiver
 *	   --es "referrer" "utm_source=YourAppName&utm_medium=YourMedium&utm_campaign=YourCampaign&utm_content=YourSampleContent"
 */
public class ReferalReceiver extends BroadcastReceiver
{
	public void onReceive(Context context, Intent intent)
	{
		if (intent.hasExtra(Settings.PREF_REFERRER))
		{
			String referrer = intent.getStringExtra(Settings.PREF_REFERRER);
			if (referrer != null && !referrer.isEmpty())
			{
				Preferences.putString(Settings.PREF_REFERRER, referrer);
				UpdaterService.startUpdate(UpdaterService.START_FORCE); // get referrer
			}
		}
	}
}
