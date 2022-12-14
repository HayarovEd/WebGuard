package app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import app.internal.Preferences;
import app.internal.Settings;


/*
 * TODO XXX enable PowerManager.getState (use new sdk, see isScreenOn)
 *
 * also in doc:
 * This broadcast is sent when the device becomes interactive which may have nothing to do with the screen turning on.
 * To determine the actual state of the screen, use Display.getState() >= API20
 */

public class ScreenStateReceiver extends BroadcastReceiver
{
	private static boolean screenOn;
	private static boolean receivedState = false;

	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (Intent.ACTION_SCREEN_OFF.equals(action))
			screenOn = false;
		else if (Intent.ACTION_SCREEN_ON.equals(action))
			screenOn = true;

		receivedState = true;

		//L.a(Settings.TAG_SCREENSTATERECEIVER, "state " + screenOn);

		if (!App.isLibsLoaded())
			return;

		if (!screenOn)
		{
			boolean need = Preferences.get(Settings.PREF_CLEARCACHES_NEED);
			if (need)
				App.cleanCaches(true, false, false, false); // clean+kill was requested
		}
	}

	public static boolean isScreenOn()
	{
		if (receivedState)
			return screenOn;

		Context context = App.getContext();
		try
		{
			PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

			//if (Build.VERSION.SDK_INT >= 20)
			//{
			//	  // STATE_OFF, STATE_ON, STATE_DOZE, STATE_DOZE_SUSPEND or STATE_UNKNOWN
			//	  int state = powerManager.getState();
			//}
			//else
			{
				screenOn = powerManager.isScreenOn(); // == isInteractive() API20 or more
				return screenOn;
			}
		}
		catch (Exception e) { }

		return true;
	}

	/*
	 * register receiver that handles screen on and screen off logic
	 * ACTION_SCREEN_OFF and ACTION_SCREEN_ON you can not be declared in your Android manifest
	 */
	public static void startScreenStateReceiver(Context context)
	{
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		BroadcastReceiver mReceiver = new ScreenStateReceiver();
		context.registerReceiver(mReceiver, filter);
	}
}
