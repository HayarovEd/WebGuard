package app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import app.info.AppManager;
import app.security.Browsers;
import app.security.Firewall;
import app.security.Processes;

/*
 * https://sw6android.googlecode.com/svn/sw6.admin/trunk/src/sw6/admin/PackageHandler.java
 * http://stackoverflow.com/questions/25035552/what-are-the-events-that-cause-intent-action-package-changed-to-be-broadcasted
 *
 * TODO XXX comment out PACKAGE_CHANGED in manifest file to try reproduce bug with duplicates in apps stats
 */

public class AppReceiver extends BroadcastReceiver
{
	public void onReceive(Context context, Intent intent)
	{
		// new package installed, hash it
		//L.d(Settings.TAG_APPRECEIVER, "Action: " + intent.getAction() + " " + intent.getDataString());

		AppUidService.hashAllUid(context); // запускаем службу, которая формирует информацию об uid всех приложений (для определения packageName по uid для Android 7 и выше, тк доступ к /proc гугл запретил)

		String data = intent.getDataString();
		if (data == null)
			return;

		if (!App.isLibsLoaded())
			return;

		//if (LibNative.asciiStartsWith("package:", data) && data.length() > 8)
		if (data.startsWith("package:") && data.length() > 8) {
			data = data.substring(8);
			if (data.length() == 0) {
				return;
			}
//			if (data == null)
//				return;
		}

		Processes.clearCaches();		  // reset processes uids info
		Browsers.clearCaches();			  // reset browsers uids info
		Firewall.clearCaches(true, true); // reset firewall uids info
		//AppManager.updateAppList(data);
		AppManager.checkApp(data);		  // update app info
	}
}
