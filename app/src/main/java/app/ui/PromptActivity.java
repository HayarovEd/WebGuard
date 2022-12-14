package app.ui;

import app.internal.Preferences;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import app.common.Utils;
import app.*;
import app.common.debug.L;
import app.internal.Settings;

public class PromptActivity extends Activity
{
	public static final int VPN_REQUEST_CODE = 256;
	public static final String ACTION_KILL = Settings.APP_PACKAGE + ".action.kill";

	private boolean blocking = false;
	private boolean noTimer = false;
	private Button activateBtn = null;
	private Dialog dialog = null;
	private KillReceiver killReceiver;

	private static final Object lock = new Object() {};
	private static boolean timerStarted = false;

	public static void updateTimer(boolean cancel)
	{
		synchronized (lock)
		{
			if (cancel)
			{
				timerStarted = false;
			}
			else
			{
				if (timerStarted)
					return;
				timerStarted = true;
			}

			L.d(Settings.TAG_PROMPTACTIVITY, "updateTimer()");

			Intent i = new Intent(App.getContext(), PromptActivityStartService.class);
			//i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			i.setAction("autoStart");
			i.putExtra("checkCurrentApp", true);
			PendingIntent pi = PendingIntent.getService(App.getContext(), 1, i, 0);

			AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
			if (cancel)
				am.cancel(pi);
			else
				am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + Settings.ACTIVATE_REQUEST_INTERVAL, pi);
		}
	}

	public static void onTimer()
	{
		synchronized (lock)
		{
			if (timerStarted)
				timerStarted = false;
		}
	}

	/**
	 * Called when the activity is first created.
	 *
	 * adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		L.d(Settings.TAG_PROMPTACTIVITY, "onCreate()");

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(android.R.style.Theme_DeviceDefault_Light);

		setContentView(Res.layout.prompt_activity);

		activateBtn = (Button) findViewById(Res.id.activate);

		String action = getIntent().getAction();
		if ("autoStart".equals(action))
		{
			blocking = true; // ???
			// TODO XXX on S3 sometimes I see this activity after block screen when WG already enabled!!!!!!!!!
		}

		// check vpn rights (see startVPN)
		// TODO XXX make common code
		try
		{
			if (VpnService.prepare(PromptActivity.this) == null)
				// we have a rights! emulate return from vpndialogs.ConfirmDialog
				onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
		}
		// can be thrown on boot (Sony 4.4.4) or other app try prepare (adguard)
		catch (NullPointerException e) { }

		killReceiver = new KillReceiver();
		registerReceiver(killReceiver, new IntentFilter(ACTION_KILL));

		// set disabled time if no rights and wait user
		if (!App.vpnActivated)
			Preferences.putLong(Settings.PREF_DISABLE_TIME, System.currentTimeMillis());
	}

	@Override
	public void onStart()
	{
		// our activity fully visible
		super.onStart();
		L.d(Settings.TAG_PROMPTACTIVITY, "onStart()");

		updateTimer(true); // onStart cancel timer
	}

	@Override
	public void onStop()
	{
		// our activity hidden
		super.onStop();
		L.d(Settings.TAG_PROMPTACTIVITY, "onStop()");

		if (!App.vpnActivated && !noTimer)
			updateTimer(false); // onStop start
		//finish();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (killReceiver != null)
			unregisterReceiver(killReceiver);
	}

/*
	// https://code.google.com/p/android/issues/detail?id=25517
	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
	}
*/

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_BACK && blocking)
			return true;

		return (super.onKeyLongPress(keyCode, event));
	}

	public static void start(Context context)
	{
		Intent intent = new Intent(context, PromptActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		context.startActivity(intent);
	}

	public static Intent getIntent(Context context, boolean newTask)
	{
		int flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
		//int flags = 0;
		if (newTask)
			flags |= (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			//Intent.FLAG_ACTIVITY_CLEAR_TOP

		Intent intent = new Intent(context, PromptActivity.class);
		if (flags != 0)
			intent.setFlags(flags);

		return intent;
	}

	private void finishInternal()
	{
		noTimer = true;
		finish();
	}

	public static void finishAll() // finish all PromtActivities (why need? f*ck)
	{
		PromptActivity.updateTimer(true); // stop all, cancel timer

		Intent intent = new Intent(ACTION_KILL);
		//intent.setType("app");
		App.getContext().sendBroadcast(intent);
	}

	//

	// TODO XXX call OptionsActivity.startVPN instead
	public void startVPNClick(View view)
	{
		activateBtn.setEnabled(false);

		// prepare VPN (see startVPN)
		// TODO XXX make common code

		Intent intent = VpnService.prepare(PromptActivity.this);
		if (intent == null)
		{
			L.d(Settings.TAG_PROMPTACTIVITY, "have rights");
			onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
			return;
		}

		try
		{
			startActivityForResult(intent, VPN_REQUEST_CODE);
		}
		catch (ActivityNotFoundException e)
		{
			//Toasts.showFirmwareVpnUnavailable();
			Dialogs.showFirmwareVpnUnavailable(this);
		}
	}

	// stop protection from dialog on device boot
	public void stopClick(View view)
	{
		if (dialog != null)
		{
			//OptionsActivity.start(this);
			//finish();
			return;
		}

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false).setIcon(Res.drawable.icon).setMessage(Res.string.do_you_want_to_disable);
		builder.setPositiveButton(Res.string.yes, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				Utils.dialogClose(null, (Dialog) dialog, false);
				PromptActivity.this.dialog = null;

				Preferences.putBoolean(Settings.PREF_ACTIVE, false);

				PromptActivity.this.finishInternal();
				updateTimer(true); // disable WG
			}
		});
		builder.setNegativeButton(Res.string.no, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				Utils.dialogClose(null, (Dialog) dialog, false);
				PromptActivity.this.dialog = null;
			}
		});
		// TODO XXX onDismiss?
		dialog = builder.create();
		Utils.dialogShow(this, dialog);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		L.d(Settings.TAG_PROMPTACTIVITY, "onActivityResult");

		if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
		{
			//App.cleanCaches(true, false, false, true); // kill+clean (interval check) or kill on boot
			App.cleanCaches(true, false, false, false); // kill+clean on boot
			App.startVpnService(this);

			finish();
		}
		else
		{
			// didn't get rights
			Dialogs.showVpnRightsCancel(this);
			activateBtn.setEnabled(true);
		}
	}

	private final class KillReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			finishInternal();
		}
	}
}
