package app;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;
import app.ui.PromptActivity;
import app.security.Browsers;
import app.common.debug.L;
import app.common.Utils;
import app.internal.Settings;

// show PromptActivity on timer (see PromptActivity.updateTimer)

public class PromptActivityStartService extends IntentService
{
	/**
	 * Creates an IntentService.  Invoked by your subclass's constructor.
	 *
	 * @param name Used to name the worker thread, important only for debugging.
	 */
	public PromptActivityStartService(String name)
	{
		super(name);
	}

	public PromptActivityStartService()
	{
		super("PromptActivityStartService");
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		if (!App.isLibsLoaded())
			return;

		// on boot activity was dismissed and time to notify

		boolean checkCurrentApp = intent.getBooleanExtra("checkCurrentApp", false);
		if (!checkCurrentApp)
			return;

		PromptActivity.onTimer();

		final String topActivityPackage = Utils.getTopActivityPkgName(this);
		final String desktop = Utils.getDefaultLauncherPkgName(this);

		L.d(Settings.TAG_PROMPTACTIVITYSTARTSERVICE, "topActivity = ", topActivityPackage, " desktop = ", desktop);

		// if user running app not browser, not show activity and waiting
		if (topActivityPackage != null && !Browsers.isBrowser(topActivityPackage))
		{
			if (desktop != null && !topActivityPackage.equals(desktop))
			{
				PromptActivity.updateTimer(false); // user now running app, try another time
				return;
			}
		}

		Intent i = PromptActivity.getIntent(this, true);
		i.setAction("autoStart");
			startActivity(i);
	}
}
