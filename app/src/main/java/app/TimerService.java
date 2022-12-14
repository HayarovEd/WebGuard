package app;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import app.common.debug.L;
import app.internal.Preferences;
import app.internal.ProxyBase;
import app.internal.Settings;
import app.security.Policy;
import app.ui.Notifications;


public class TimerService extends IntentService
{
	private static final String updateSettingsAction	= "updateSettings";
	private static final String evaluateNotifyAction	= "evaluateNotify";
	private static final String feedbackNotifyAction	= "feedbackNotify";
	//private static final String feedback2NotifyAction   = "feedback2Notify";
	private static final String firstResultNotifyAction = "firstResultNotify";

	// name - used to name the worker thread, important only for debugging.
	public TimerService(String name)
	{
		super(name);
	}

	public TimerService()
	{
		super("TimerService");
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	public static void startTimers()
	{
		boolean libs = App.isLibsLoaded();

		updateSettingsStartTimer(!libs || !Preferences.isActive()); // before !libs to stop alarm on error

		if (!libs)
			return;

		// TODO XXX this alarms start if inited before error
		evaluateNotifyStartTimer();
		feedbackNotifyStartTimer();
		//feedback2NotifyStartTimer();
		firstResultNotifyStartTimer();
	}

	//

	public static void updateSettingsStartTimer(boolean cancel)
	{
		Intent i = new Intent(App.getContext(), TimerService.class);
		i.setAction(updateSettingsAction);
		PendingIntent pi = PendingIntent.getService(App.getContext(), 1, i, 0);

		AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
		if (cancel)
		{
			am.cancel(pi);
		}
		else
		{
			long t = updateSettingsGetNextTime();
			am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t, AlarmManager.INTERVAL_DAY, pi);
		}
	}

	private static long updateSettingsGetNextTime()
	{
		long curTime = System.currentTimeMillis();
		long curDayStart = AlarmManager.INTERVAL_DAY * (curTime / AlarmManager.INTERVAL_DAY);
		long startTime = curDayStart + Settings.SETTINGS_RELOAD_INTERVAL;

		// TODO XXX is that ok?
		if (startTime > curTime)
			return startTime;
		else
			return (startTime + AlarmManager.INTERVAL_DAY);
	}

	//

	public static void evaluateNotifyStartTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		long startTime = Preferences.get_l(Settings.PREF_EVALUATE_TIME);
		long curTime = System.currentTimeMillis();

		if (startTime == 0)
			return;
		if (startTime <= curTime)
			startTime = curTime + 60 * 1000;

		L.d(Settings.TAG_TIMERSERVICE, "evaluateNotifyStartTimer");

		Intent i = new Intent(App.getContext(), TimerService.class);
		i.setAction(evaluateNotifyAction);
		PendingIntent pi = PendingIntent.getService(App.getContext(), 1, i, 0);

		AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, startTime, pi);
	}

	public static void evaluateNotifyInitTimer(boolean isPaid)
	{
		if (!Settings.USER_FEEDBACK)
			return;

		if (Preferences.get_i(Settings.PREF_EVALUATE_STATUS) == 3)
			return;

		L.d(Settings.TAG_TIMERSERVICE, "evaluateNotifyInitTimer");

		long curTime = System.currentTimeMillis();
		long startTime = isPaid ?
							curTime + 24 * 60 * 60 * 1000 : // 1 day for pay
								curTime + 2 * 24 * 60 * 60 * 1000 + 21 * 60 * 60 * 1000; // ~3 days for free
		//startTime = curTime + 60 * 1000;

		Preferences.putLong(Settings.PREF_EVALUATE_TIME, startTime); // reset in showEvaluate
		evaluateNotifyStartTimer();
	}

	//

	public static void feedbackNotifyStartTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		long startTime = Preferences.get_l(Settings.PREF_FEEDBACK_TIME);
		long curTime = System.currentTimeMillis();

		if (startTime == 0)
			return;
		if (startTime <= curTime)
			startTime = curTime + 60 * 1000;

		L.d(Settings.TAG_TIMERSERVICE, "feedbackNotifyStartTimer");

		Intent i = new Intent(App.getContext(), TimerService.class);
		i.setAction(feedbackNotifyAction);
		PendingIntent pi = PendingIntent.getService(App.getContext(), 1, i, 0);

		AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, startTime, pi);
	}

	public static void feedbackNotifyInitTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		int status = Preferences.get_i(Settings.PREF_FEEDBACK_STATUS);
		//if (status >= 3)
		if (status >= 1) // only one notification
			return;

		L.d(Settings.TAG_TIMERSERVICE, "feedbackNotifyInitTimer");

		long curTime = System.currentTimeMillis();
		long startTime;
		if (status == 0)
			startTime = curTime + 10 * 60 * 1000; // status 0 - 10 min (see showFeedback)
		// not used
		else
			startTime = curTime + 60 * 60 * 1000; // status 1,2 - 60 min
		//startTime = (status == 0) ? curTime + 60 * 1000 : curTime + 2 * 60 * 1000;

		Preferences.putLong(Settings.PREF_FEEDBACK_TIME, startTime); // updated in showFeedback
		feedbackNotifyStartTimer();
	}

	// not used
/*
	public static void feedback2NotifyStartTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		long startTime = Preferences.get_l(Settings.PREF_FEEDBACK2_TIME);
		long curTime = System.currentTimeMillis();

		if (startTime == 0)
			return;
		if (startTime <= curTime)
			startTime = curTime + 60 * 1000;

		L.d(Settings.TAG_TIMERSERVICE, "feedback2NotifyStartTimer");

		Intent i = new Intent(App.getContext(), TimerService.class);
		i.setAction(feedback2NotifyAction);
		PendingIntent pi = PendingIntent.getService(App.getContext(), 1, i, 0);

		AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, startTime, pi);
	}

	public static void feedback2NotifyInitTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		int status = Preferences.get_i(Settings.PREF_FEEDBACK2_STATUS);
		if (status >= 3)
			return;

		L.d(Settings.TAG_TIMERSERVICE, "feedback2NotifyInitTimer");

		long curTime = System.currentTimeMillis();
		long startTime;
		if (status == 0)
			startTime = curTime + 9 * 60 * 60 * 1000; // status 0 - 9 hours (see showFeedback2)
		else
			startTime = curTime + 60 * 60 * 1000; // status 1,2 - 60 min
		//startTime = (status == 0) ? curTime + 60 * 1000 : curTime + 2 * 60 * 1000;

		Preferences.putLong(Settings.PREF_FEEDBACK2_TIME, startTime); // updated in showFeedback2
		feedback2NotifyStartTimer();
	}
*/
	//

	public static void firstResultNotifyStartTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		long startTime = Preferences.get_l(Settings.PREF_FIRSTRESULT_TIME);
		long curTime = System.currentTimeMillis();

		if (startTime == 0)
			return;
		if (startTime <= curTime)
			startTime = curTime + 60 * 1000;

		L.d(Settings.TAG_TIMERSERVICE, "firstResultNotifyStartTimer");

		Intent i = new Intent(App.getContext(), TimerService.class);
		i.setAction(firstResultNotifyAction);
		PendingIntent pi = PendingIntent.getService(App.getContext(), 1, i, 0);

		AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, startTime, pi);
	}

	public static void firstResultNotifyInitTimer()
	{
		if (!Settings.USER_FEEDBACK)
			return;

		int status = Preferences.get_i(Settings.PREF_FIRSTRESULT_STATUS);
		//if (status >= 6)
		if (status >= 1) // only one notification
			return;

		L.d(Settings.TAG_TIMERSERVICE, "firstResultNotifyInitTimer");

		long curTime = System.currentTimeMillis();
		long startTime;
		if (status == 0)
			//startTime = curTime + 20 * 60 * 1000; // status 0 - 20 min (see showFirstResult)
			startTime = curTime + 9 * 60 * 60 * 1000; // status 0 - 9 hours
		// not used
		else if (status < 3)
			startTime = curTime + 60 * 60 * 1000; // status 1,2 - 60 min
		else if (status == 3)
			startTime = curTime + 9 * 60 * 60 * 1000; // status 3 - 9 hours
		else
			startTime = curTime + 60 * 60 * 1000; // status 4,5 - 60 min
		//startTime = (status == 0) ? curTime + 60 * 1000 : curTime + 2 * 60 * 1000;

		Preferences.putLong(Settings.PREF_FIRSTRESULT_TIME, startTime); // updated in showFirstResult
		firstResultNotifyStartTimer();
	}

	//

	@Override
	protected void onHandleIntent(Intent intent)
	{
		L.d(Settings.TAG_TIMERSERVICE, "Timer fired!");

		String action = (intent == null) ? null : intent.getAction();
		if (action == null)
			return;

		if (action.equals(updateSettingsAction))
		{
			if (!Preferences.isActive())
				return;

			//final IFilterVpnPolicy policy = ((IFilterVpnApplication) getApplication()).getPolicy();
			//if (policy != null)
			//	  policy.reload();

			Policy.refreshToken(false);
			ProxyBase.updateServers(true);
			App.cleanCaches(true, false, false, false); // ebanutaya huinya

			// TODO XXX may be not need?
			UpdaterService.startUpdate(UpdaterService.START_DELAYED); // service wake up

			return;
		}

		if (action.equals(evaluateNotifyAction))
		{
			String token = Policy.getUserToken(true);
			boolean isFree = (token == null || Policy.isFreeToken(token));
			Notifications.showEvaluate(App.getContext(), !isFree);

			return;
		}

		if (action.equals(feedbackNotifyAction))
		{
			Notifications.showFeedback(App.getContext());
			return;
		}

/*
		if (action.equals(feedback2NotifyAction))
		{
			Notifications.showFeedback2(App.getContext());
			return;
		}
*/

		if (action.equals(firstResultNotifyAction))
		{
			Notifications.showFirstResult(App.getContext());
			return;
		}
	}
}
