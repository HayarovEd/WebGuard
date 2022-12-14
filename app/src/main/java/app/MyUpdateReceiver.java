package app;

import app.internal.Preferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import app.info.Statistics;
import app.common.Utils;
import app.internal.Settings;
import app.ui.Notifications;
import app.ui.StartActivity;

// TODO XXX sometimes (< 1%) this broadcast not recived (may be OOM kill us? or we crash on db unload?)
// may be add catch ACTION_PACKAGE_REPLACED too?
// http://stackoverflow.com/questions/2133986/how-to-know-my-android-application-has-been-upgraded-in-order-to-reset-an-alarm

public class MyUpdateReceiver extends BroadcastReceiver
{
	public void onReceive(Context context, Intent intent)
	{
		//L.a(Settings.TAG_MYUPDATERECEIVER, "start");

		Preferences.editStart();
		//Preferences.putBoolean(Settings.PREF_BLOCK_TP_CONTENT, false);
		Preferences.putString(Settings.PREF_NEED_UPDATE, null);
		//Preferences.putBoolean(AppManager.HASHING_ON_INSTALL, false); // force first app update send data to server
		Preferences.editEnd();
		//Preferences.load(App.getContext());

		// clear statistics
		// TODO XXX what about not sended info about apps? may rehash all apps?
		//Statistics.clear();

		// refresh databases (temporary in App.initOther)

		Statistics.addLogFast(Settings.LOG_UPDATED);

		// show news

		int recovery = Preferences.get_i(Settings.PREF_RECOVERY_STATUS);
		final String news;

		if (recovery == 1) // for old users (see isFreeUser) show news with promo text
			news = context.getString(Res.string.freeuser_whatsnew);
		else
			news = Utils.getLocalizedAssetAsString(context, "news");

		if (news != null && news.length() > 0)
		{
			Preferences.putBoolean(Settings.PREF_NEWS_SHOWN, false);
			Notifications.showNews(context, news);
		}

		AppUidService.hashAllUid(context); // запускаем службу, которая формирует информацию об uid всех приложений (для определения packageName по uid для Android 7 и выше, тк доступ к /proc гугл запретил)

		// check updates

		App.checkUpdates();
		if (Settings.UPDATE_APPS_REHASH) {
			AppService.hashAllApps(context);
		}

		if (!Preferences.isActive())
			return;

		// restart protection

		if (!App.startProcessed) // see BootCompletedReceiver
		{
			Intent i = StartActivity.getIntent(context, true);
			i.setAction("updateStart");
			context.startActivity(i);
		}
	}
}
