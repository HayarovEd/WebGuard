package app;

import android.app.IntentService;
import android.content.Intent;
import app.netfilter.IFilterVpnApplication;
import app.common.Utils;
import app.scanner.Scanner;
import app.security.Policy;

public class PolicyReloadService extends IntentService
{
	/**
	 * Запускается извне командою:
	 * adb shell am startservice -n app.webguard/app.PolicyReloadService
	 *
	 * @param name Used to name the worker thread, important only for debugging.
	 */
	public PolicyReloadService(String name)
	{
		super(name);
	}

	public PolicyReloadService()
	{
		super("PolicyReloadService");
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		if (!App.isLibsLoaded())
			return;

		if (Policy.isDebugBuild())
		{
			Utils.copyFile("/data/local/tmp/" + Scanner.debugDBName,
							App.getContext().getFilesDir() + "/" + Scanner.debugDBName);
			((IFilterVpnApplication) App.getContext()).getPolicy().reload();
		}
	}
}
