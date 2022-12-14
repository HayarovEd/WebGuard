package app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import app.common.Utils;
import app.App;
import app.internal.Preferences;
import app.Res;
import app.info.Statistics;
import app.internal.InAppBilling;
import app.internal.Settings;
import java.util.concurrent.locks.ReentrantLock;

public class SubsNeedDialogActivity extends Activity
{
	private static final int ASKPROMO_FREE_REQUEST_CODE = 19; // see BuyDialogActivity
	private static final int ASKPROMO_BUY_REQUEST_CODE = 21;

	private InAppBilling billing = null;
	private final Handler handler = new Handler();
	private ProgressDialog progress = null;
	private final ReentrantLock lock = new ReentrantLock();

	private boolean promoNeed = false;
	private String promoCode = null;

	private static Activity parentActivity = null; // TODO XXX костыль
	private static RootPrefSwitch rootSwitch = null;
	//private static boolean statsForced = false;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setFinishOnTouchOutside(false);

		if (Settings.LIC_FREE_AUTO)
			// hide activity (setContentView(R.layout.hidden_activity) sometime not work)
			setTheme(Res.style.DialogWindow_Transparent);
		else
			if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
				setTheme(Res.style.DialogWindowTitleText_Light_Multiline);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView((Settings.LIC_FREE_AUTO) ? Res.layout.hidden_activity :
							Res.layout.subsneed_dialog_activity);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		billing = App.getBilling();
		//billing.serviceBind();

		// if have no referrer or have referrer with "showpromo" ask user for promocode
		// also ask promo if refferer restored from recovery (user may come from other pathner with new promo)
		String r = Preferences.getReferrer(false);
		boolean recovery = Preferences.get(Settings.PREF_REFERRER_RECOVERY);

		if (Settings.LIC_ASK_PROMO)
		{
			if (r == null || r.indexOf("utm_medium=showpromo") >= 0 || recovery)
				promoNeed = true;
		}

		Statistics.addLog(Settings.LOG_BUY_NEED);
		// not need now because we try to enable protection on first start
		//if (App.isFirstRun() && !statsForced)
		//{
		//	  statsForced = true;
		//	  UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'buy show' statistic
		//}

		if (Settings.LIC_FREE_AUTO)
			processState(0, true);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	//public static void showAbove(Context context)
	public static void showAbove(Activity activity)
	{
		// TODO XXX see parentActivity
		SubsNeedDialogActivity.parentActivity = activity;
		Context context = activity;
		if (context == null) // crash fix (why null?)
			return;

		Intent intent = new Intent(context, SubsNeedDialogActivity.class);
		//intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		context.startActivity(intent);
	}

	//
	public void onTryFreeClick(View view)
	{
		if (!lock.tryLock())
			return;
		try { processState(0, true); }
		finally { lock.unlock(); }
	}

	public void onBuySubscriptionClick(View view)
	{
		if (!lock.tryLock())
			return;
		try { processState(0, false); }
		finally { lock.unlock(); }
	}

	// requestCode == 0: call from button click
	// requestCode != 0: call from onActivityResult
	private void processState(final int requestCode, final boolean tryFree)
	{
		if (requestCode == 0 && promoNeed && promoCode == null)
		//if (requestCode == 0 && promoNeed) // reask promocode if already enter
		{
			// click on try or buy, ask promo if need
			int code = (tryFree) ? ASKPROMO_FREE_REQUEST_CODE : ASKPROMO_BUY_REQUEST_CODE;
			billing.promoAsk(SubsNeedDialogActivity.this, code);
			return;
		}

		// processing try or buy
		// if asking promo and promoCode == null ???

		handler.post(new Runnable() {
			@Override
			public void run()
			{
				if ((requestCode == 0 && tryFree) ||
						requestCode == ASKPROMO_FREE_REQUEST_CODE)
				{
					if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BUY_SELECTED + "tryfree");

					billing.freeGet(SubsNeedDialogActivity.this, "tryfree",
										BuyDialogActivity.GETFREE_REQUEST_CODE, promoCode);
				}
				else
				{
					if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BUY_SELECTED + "buysubs");

					billing.itemsLoadAndShowBuyDialog(parentActivity, promoCode);
					// TODO XXX closing this dialog because itemsLoadAndShowBuyDialog async
					SubsNeedDialogActivity.this.finish();
				}
			}
		});
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		// for GETFREE_REQUEST_CODE processing code see BuyDialogActivity

		if (requestCode != BuyDialogActivity.GETFREE_REQUEST_CODE &&
			requestCode != ASKPROMO_FREE_REQUEST_CODE && requestCode != ASKPROMO_BUY_REQUEST_CODE)
		{
			return;
		}

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				if (requestCode != BuyDialogActivity.GETFREE_REQUEST_CODE)
				{
					// result from promocode ask
					if (data == null)
						return; // no result from promoAsk

					promoCode = data.getStringExtra(InAppBilling.PROMO_CODE_PARAM);
					processState(requestCode, false);
					return;
				}

				// processing "try free" (GETFREE_REQUEST_CODE)

				showProgressDialog();
				boolean result =
					BuyDialogActivity.processBuyResult(requestCode, resultCode, data,
														billing, promoCode);
				hideProgressDialog();

				// end
				boolean close = false;

				if (resultCode == RESULT_OK && result)
				{
					if (Settings.LIC_FREE_AUTO)
						close = true;
					else
						showMessage(getString(Res.string.purchase_ok));

					// enable VPN
					//String token = Policy.getUserToken(true);
					//if (token != null && (parentActivity instanceof OptionsActivity))

					/////// this fixed by Roman Popov
//					if (parentActivity instanceof OptionsActivity)
//					{
//						Preferences.putBoolean(Settings.PREF_ACTIVE, true);
//						((OptionsActivity) parentActivity).startVPN(); // TODO XXX
//					}
					App.setHack(true);
					OptionsActivity.startVPN(parentActivity);
					///////
				}
				else
				{
					if (resultCode == RESULT_OK && !result)
						// oops, problems with activating full version
						showMessage(getString(Res.string.purchase_activate_error));
					else
						// TODO XXX close if resultCode != RESULT_OK, e.g.: close captcha dialog
						close = true;
				}

				if (close)
				{
					handler.post(new Runnable() {
						@Override
						public void run() { SubsNeedDialogActivity.this.finish(); }
					});
				}
			}
		}).start();
	}

	//
	public void showProgressDialog()
	{
		handler.post(new Runnable() {
			@Override
			public void run()
			{
				synchronized (handler)
				{
					if (progress != null)
						return;

					progress = new ProgressDialog(SubsNeedDialogActivity.this);
					progress.setIndeterminate(true);
					progress.setCancelable(false);
					progress.setTitle(Res.string.please_wait);
					Utils.dialogShow(SubsNeedDialogActivity.this, progress);
				}
			}
		});
	}

	public void hideProgressDialog()
	{
		handler.post(new Runnable() {
			@Override
			public void run()
			{
				synchronized (handler)
				{
					if (progress != null)
					{
						Utils.dialogClose(SubsNeedDialogActivity.this, progress, false);
						progress = null;
					}
				}
			}
		});
	}

	public void showMessage(final String text)
	{
		handler.post(new Runnable() {
			@Override
			public void run()
			{
				final AlertDialog.Builder builder = new AlertDialog.Builder(SubsNeedDialogActivity.this);
				builder.setCancelable(false).setIcon(Res.drawable.icon).setMessage(text);
				builder.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						Utils.dialogClose(null, (Dialog) dialog, false);
					}
				});

				Dialog dialog = builder.create();
				dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
				{
					@Override
					public void onDismiss(DialogInterface dialog)
					{
						SubsNeedDialogActivity.this.finish();
					}
				});
				Utils.dialogShow(SubsNeedDialogActivity.this, dialog);
			}
		});
	}
}
