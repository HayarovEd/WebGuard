package app.ui;

import app.internal.Preferences;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.Preference;

import app.netfilter.proxy.ProxyManager;
import app.*;
import app.common.debug.L;
import app.common.Utils;
import app.info.Statistics;
import app.internal.Settings;
import app.security.Policy;

public class OptionsActivity extends Activity
{
	private static final int VPN_REQUEST_CODE = 10;

	protected OptionsFragment options = null;

	private String newsText = null;
	private String evaluateText = null;
	private String feedbackText = null;
	private String feedback2Text = null;
	private String firstResultText = null;
	private String dialogText = null;
	private String dialogTitle = null;
	private String dialogType = null;
	private boolean needToCheckPermissions = true;
	private int vpnRightsDialogs = 0;
	private ProgressDialog pd = null;
	private ActivityKillerThread killer = null;

	//private static boolean cleanForced = false;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		L.d(Settings.TAG_OPTIONSACTIVITY, "onCreate");

		if (!App.isLibsLoaded())
		{
			// ooups, native libs loading error
			Dialogs.showInstallError(null);
			finish();
			return;
		}

		Intent intent = getIntent();

		if (intent != null && intent.hasExtra("exit")) // see ActivityKillerThread
		{
			String pkg = intent.getPackage();
			if (pkg != null && pkg.equals(App.packageName()))
			{
				finish();
				return;
			}
		}

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(android.R.style.Theme_DeviceDefault_Light);

		// display the fragment as the main content
		options = new OptionsFragment(); // OptionsFragment options = new OptionsFragment(); fixed by Roman Popov

		// пофиксил кнопку Купить подписку
		if (intent.hasExtra(Notifications.BUY_ACTION)) {
			Bundle bundle = new Bundle();
			bundle.putBoolean(OptionsFragment.BUY, true);
			options.setArguments(bundle);
		}

		getFragmentManager().beginTransaction().replace(android.R.id.content, options).commit();

		PromptActivity.finishAll();

		//

		App.getBilling().serviceBind();

		//

		if (intent.hasExtra(Notifications.NEWS_ACTION) &&
			!Preferences.get(Settings.PREF_NEWS_SHOWN))
		{
			newsText = intent.getStringExtra(Notifications.NEWS_ACTION);
			intent.removeExtra(Notifications.NEWS_ACTION);
		}
		else if (intent.hasExtra(Notifications.EVALUATE_ACTION) &&
					Preferences.get_i(Settings.PREF_EVALUATE_STATUS) == 1) // see Notifications.showEvaluate
		{
			evaluateText = intent.getStringExtra(Notifications.EVALUATE_ACTION);
			intent.removeExtra(Notifications.EVALUATE_ACTION);
		}
		else if (intent.hasExtra(Notifications.FEEDBACK_ACTION))
		{
			feedbackText = intent.getStringExtra(Notifications.FEEDBACK_ACTION);
			intent.removeExtra(Notifications.FEEDBACK_ACTION);
		}
		else if (intent.hasExtra(Notifications.FEEDBACK2_ACTION))
		{
			feedback2Text = intent.getStringExtra(Notifications.FEEDBACK2_ACTION);
			intent.removeExtra(Notifications.FEEDBACK2_ACTION);
		}
		else if (intent.hasExtra(Notifications.FIRSTRES_ACTION))
		{
			firstResultText = intent.getStringExtra(Notifications.FIRSTRES_ACTION);
			intent.removeExtra(Notifications.FIRSTRES_ACTION);
		}

		//
		if (intent.hasExtra(Utils.DIALOG_TEXT))
		{
			dialogText = getString(intent.getIntExtra(Utils.DIALOG_TEXT, 0));
			if (intent.hasExtra(Utils.DIALOG_TEXT_ADD))
				dialogText += intent.getStringExtra(Utils.DIALOG_TEXT_ADD);
			dialogTitle = getString(intent.getIntExtra(Utils.DIALOG_TITLE, 0));
			dialogType = intent.getStringExtra(Utils.DIALOG_TYPE);
		}

		if (dialogText != null || dialogType != null || newsText != null ||
			evaluateText != null || feedbackText != null || feedback2Text != null ||
			firstResultText != null)
		{
			Statistics.addLog(Settings.LOG_NOTIFY_CLICKED);
		}

		//overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
		L.d(Settings.TAG_OPTIONSACTIVITY, "onCreate finish");
	}

	@Override
	public void onStart()
	{
		super.onStart();
		L.d(Settings.TAG_OPTIONSACTIVITY, "onStart");
		//L.printBacktrace(Settings.TAG_OPTIONSACTIVITY, 'd');

		killerStop();

		if (Preferences.isActive() && !ProxyManager.isStarted())
			startVPN();

		//
		if (newsText != null)
		{
			Dialogs.showWhatsNew(this, newsText);
			Preferences.putBoolean(Settings.PREF_NEWS_SHOWN, true);
			newsText = null;
		}
		else if (evaluateText != null)
		{
			Dialogs.showEvaluate(this, evaluateText);
			evaluateText = null;
		}
		else if (feedbackText != null)
		{
			Dialogs.showFeedback(this, feedbackText);
			feedbackText = null;
		}
		else if (feedback2Text != null)
		{
			Dialogs.showFeedback2(this, feedback2Text);
			feedback2Text = null;
		}
		else if (firstResultText != null)
		{
			Dialogs.showFirstResult(this, firstResultText);
			firstResultText = null;
		}

		//
		if (dialogType != null && !dialogType.equals(Notifications.BUY_ACTION)) {
			// click on notify about new version (warning or app block)

			int type = -1;
			if (dialogType.equals(Notifications.NEEDUPDATE_BLOCK_ACTION)) type = 1;
			else if (dialogType.equals(Notifications.NEEDUPDATE_UPDATE_ACTION)) type = 2;
			else if (dialogType.equals(Notifications.NEEDUPDATE_BLOCKFINAL_ACTION)) type = 3;
			else if (dialogType.equals(Notifications.NEEDUPDATE_UPDATEFINAL_ACTION)) type = 4;

			Dialogs.showNeedUpdate(this, type);

			dialogText = null;
			dialogTitle = null;

		}  else if (Settings.NOTIFY_BUY_BUTTON && dialogType != null && dialogType.equals(Notifications.BUY_ACTION)) {
			// TODO XXX remove this

			MessageDialogActivity.show(this, dialogTitle, dialogText, getString(Res.string.buy_subscription),
										Notifications.BUY_ACTION, true);
			dialogText = null;
			dialogTitle = null;

		} else if (dialogText != null) {

			// click on other notifications

			MessageDialogActivity.show(this, dialogTitle, dialogText);

			dialogText = null;
			dialogTitle = null;
		}
		else
		{
			final String curUpdate = Preferences.get_s(Settings.PREF_NEED_UPDATE);
			if (curUpdate != null)
			{
				// app block because of new version

				int type = -1;
				if (curUpdate.equals("block")) type = 1;
				else if (curUpdate.equals("finalblock")) type = 3;

				Dialogs.showNeedUpdate(this, type);
			}
		}

		L.d(Settings.TAG_OPTIONSACTIVITY, "onStart finish");
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// В очередной раз добавили проверку тут, но надо быть готовым к какому-то багу, из-за которого мы это убрали
		//App.getBilling().licenseCheckAsync();

		if (needToCheckPermissions)
		{
			// TODO XXX why check only on resume? user can activate vpn!
			needToCheckPermissions = false;

			final String[] permissions = new String[] {
					"android.permission.GET_TASKS",
					//"android.permission.KILL_BACKGROUND_PROCESSES",
					//"android.permission.CLEAR_APP_CACHE",
					//"android.permission.RECEIVE_BOOT_COMPLETED",

					"android.permission.INTERNET",
					"android.permission.ACCESS_NETWORK_STATE",
					"android.permission.ACCESS_WIFI_STATE",
					"android.permission.CHANGE_WIFI_STATE",

					"android.permission.VIBRATE",
					"android.permission.WAKE_LOCK"
					//"com.android.vending.BILLING"

					//"android.permission.ACCESS_COARSE_LOCATION", // just for test
				};

			if (!Utils.hasPermissions(this, permissions, App.packageName()))
			{
				Dialogs.showPermissionsError(this);
				App.disable();
			}
		}


		AppUidService.hashAllUid(this); // запускаем службу, которая формирует информацию об uid всех приложений (для определения packageName по uid для Android 7 и выше, тк доступ к /proc гугл запретил)

		//billing.licenseCheckAsync();


	}

	@Override
	public void onStop()
	{
		super.onStop();
		L.d(Settings.TAG_OPTIONSACTIVITY, "onStop");

		killerStart();
	}

	private void killerStart()
	{
		if (killer != null)
			return;

		// Используем хитрый таймер, чтобы он прикончил активити через 15 секунд, ибо из-за бага в ведроиде (точно ?!)
		// она остается жить и считается, что работает и тратит ресурсы если нажать home, а не back
		// (это да, но может быть это все таки наш баг)
		// отключил нафиг это киллер
//		if (!App.isUIActive())
//			killer = new ActivityKillerThread(this, 15000);
	}

	private void killerStop()
	{
		if (killer == null)
			return;

		killer.stop();
		killer = null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		killerStop();

		if (!Preferences.isActive())
			Preferences.setNotifyDisabledAlarm(false);

		if (!App.isLibsLoaded())
			return;

		//closeProgressDialog();
		notifyAboutSubscriptionFeatures();

		App.getBilling().serviceUnbind(); // TODO XXX service binding may be in progress (see onCreate) and we don't stop GP
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();

		finish();
	}

	public static void start(Context context)
	{
		//L.printBacktrace(Settings.TAG_OPTIONSACTIVITY, 'd');

		Intent intent = new Intent(context, OptionsActivity.class);
		//intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		context.startActivity(intent);
	}

	public static Intent getIntent(Context context, boolean newTask)
	{
		//L.printBacktrace(Settings.TAG_OPTIONSACTIVITY, 'd');

		//int flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
		int flags = 0;
		if (newTask)
			flags |= (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			//Intent.FLAG_ACTIVITY_CLEAR_TOP

		Intent intent = new Intent(context, OptionsActivity.class);
		if (flags != 0)
			intent.setFlags(flags);

		return intent;
	}

	public void finishAll()
	{
		//L.printBacktrace(Settings.TAG_OPTIONSACTIVITY, 'd');

		Intent i = new Intent(getApplicationContext(), OptionsActivity.class);
		//Intent i = new Intent(getApplicationContext(), StartActivity.class);
		//Intent i = new Intent(OptionsActivity.this, StartActivity.class);
		//Intent i = new Intent(getApplicationContext(), StartActivity.class);
		//Intent i = new Intent(this, StartActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		//i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		//i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		//i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		//i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		//i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

		i.putExtra("exit", true);
		startActivity(i);

		finish();
	}

	//

	public static void notifyAboutSubscriptionFeatures()
	{
		// if user has subscribed or closing activity with disabled service
		if (Policy.getUserToken(true) != null || !Preferences.isActive())
			return;

		long firstStartTime = Preferences.get_l(Settings.PREF_FIRST_START_TIME);
		final int count = Preferences.get_i(Settings.PREF_NO_SUBS_AD_COUNT);
		if (count == 0)
		{
			// no subscription? wait 5 min from first start and show promo notification

			final long timeToWait = (firstStartTime + 300000) - System.currentTimeMillis();
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					if (timeToWait > 0)
						Utils.sleep(timeToWait);

//					  Notifications.showGetSubscription();
					Preferences.putInt(Settings.PREF_NO_SUBS_AD_COUNT, 1);
				}
			}).start();
		}
		else if (count == 1)
		{
			// still no subscription? wait 2 weeks(?) from first start show promo notification second time

			if (System.currentTimeMillis() - firstStartTime > 86400 * 7 * 1000)
			{
//				  Notifications.showGetSubscription();
				Preferences.putInt(Settings.PREF_NO_SUBS_AD_COUNT, 2);
			}
		}
	}

/*
	private void showProgressDialog()
	{
		pd = new ProgressDialog(OptionsActivity.this);
		//pd.setTitle("Processing...");
		pd.setMessage(getString(Res.string.please_wait));
		pd.setCancelable(false);
		pd.setIndeterminate(true);
		Utils.dialogShow(this, pd);
	}
*/
/*
	private void closeProgressDialog()
	{
		if (pd != null)
		{
			Utils.dialogCancel(this, pd);
			pd = null;
		}
	}
*/

	public void startVPN() {
		if (App.isHack()) {
			App.setHack(false);
			options.updateSettings();
			startVPNInternal();
			return;
		}

		options.updateSettings();
		startVPNInternal();
	}

	public static void startVPN(final Activity activity) {
		Preferences.putBoolean(Settings.PREF_ACTIVE, true);

		// TODO XXX check if already on main thread
		Utils.runOnUiThread(activity, new Runnable() {
			@Override
			public void run() {
				((OptionsActivity) activity).startVPN(); // TODO XXX
			}
		});
	}

	private void startVPNInternal() {
		L.d(Settings.TAG_OPTIONSACTIVITY, "startVPN");

		boolean haveToken = (Policy.getUserToken(true) != null);
		boolean freeUser = Policy.isFreeUser();
		if (!haveToken && !freeUser) {
			return;
		}

		Context context = App.getContext();

		if (Utils.isHaveAndroidBug1900()) {
			// have bug :( disable protection and show vpndialogs.ManageDialog to disconnect VPN
			Preferences.putBoolean(Settings.PREF_ACTIVE, false);
			Dialogs.showGetVpnBug1900(this);

			return;
		}

		// prepare VPN (get rights through com.android.vpndialogs)
		// TODO XXX make common code

		Intent intent = null;
		try {
			intent = VpnService.prepare(context);
		} catch (NullPointerException e) {
			Statistics.addLog(Settings.LOG_VPNPREPARE_NULL_ERR);

			// TODO XXX second try and if this fail too?
			try {
				intent = VpnService.prepare(context);
			} catch (NullPointerException ex) {
				Statistics.addLog(Settings.LOG_VPNPREPARE_NULL_ERR);
			}
		}

		// fix: onStart -> startVPN -> startActivityForResult -> onStart finish -> onStop ->
		//      onStart -> startVPN -> onStart finish ->
		//      onActivityResult 10 0 (android auto close even if not call startVPN)
		//
		// TODO XXX may be sync ?
		// TODO XXX may be < 0 (miss onActivityResult call) ?
		vpnRightsDialogs++;

		if (intent == null) {
			// already have rights, emulate return from vpndialogs.ConfirmDialog
			onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
			return; // WAIT dialog
		}

		// no rights, start activity
		try {
			startActivityForResult(intent, VPN_REQUEST_CODE);
			return; // OK
		} catch (ActivityNotFoundException e) {
		} catch (NullPointerException e) {// bug. some phones firmware doesn't have VpnDialogs.apk!
		} // another bug?

		vpnRightsDialogs--;

		Preferences.putBoolean(Settings.PREF_ACTIVE, false);

		Dialogs.showFirmwareVpnUnavailable(this);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		super.onActivityResult(requestCode, resultCode, data);

		if (Settings.DEBUG)
			L.d(Settings.TAG_OPTIONSACTIVITY, "onActivityResult " + requestCode + " " + resultCode + " " + vpnRightsDialogs);

		vpnRightsDialogs--;

		boolean isCancel = true;

		if (requestCode != VPN_REQUEST_CODE) {
			if (requestCode == 1024) { // ???
				Toasts.showToast("Install result: " + resultCode);
			}
			return;
		} else if (resultCode != RESULT_OK) {
			// didn't get rights

			// ignore bad result if have another dialog, see startVPN
			if (vpnRightsDialogs <= 0) {
				Dialogs.showVpnRightsCancel(this);
			}
		} else {
			isCancel = false;
		}

		if (isCancel) {
			saveState(false);
			return;
		}

		// ok, get rights
		saveState(true);

		new Thread(new Runnable() {
			@Override
			public void run() {
				L.d(Settings.TAG_OPTIONSACTIVITY, "onActivityResult");

				App.cleanCaches(true, false, false, false); // kill+clean on switch on
				App.startVpnService(OptionsActivity.this);
			}
		}).start();
	}

	private void saveState(boolean state) {
		Preferences.putBoolean(Settings.PREF_ACTIVE, state);
	}

}
