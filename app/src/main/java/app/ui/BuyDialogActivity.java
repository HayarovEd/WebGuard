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
import android.widget.Button;
import android.widget.TextView;
import app.common.Utils;
import app.App;
import app.internal.InAppBilling;
import app.internal.Preferences;
import app.Res;
import app.TimerService;
import app.UpdaterService;
import app.info.Statistics;
import app.internal.PublisherSettings;
import app.internal.Settings;
import app.security.Policy;
import java.util.concurrent.locks.ReentrantLock;

public class BuyDialogActivity extends Activity
{
	public static final int SUBS_REQUEST_CODE	 = 15; // GP but
	public static final int GETFREE_REQUEST_CODE = 17; // get free

	private InAppBilling billing = null;
	private final Handler handler = new Handler();
	private ProgressDialog progress = null;
	private final ReentrantLock lock = new ReentrantLock();

	private String	firstName;
	private boolean firstInapp;
	private String	secondName;
	private boolean secondInapp;
	private String	thirdName;
	private boolean thirdInapp;
	private String	freeName;
	private String	promoCode = null;

	private static boolean statsForced = false;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setFinishOnTouchOutside(false);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(Res.style.DialogWindowTitleText_Light_Multiline);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(Res.layout.buy_dialog_activity);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		Intent i = getIntent();
		if (i != null && i.hasExtra(InAppBilling.PROMO_CODE_PARAM)) // see InAppBilling.promoCheck
			promoCode = i.getStringExtra(InAppBilling.PROMO_CODE_PARAM);

		//handler = new Handler();

		billing = App.getBilling();
		billing.serviceBind();

		firstName	= billing.getFirstName();
		firstInapp	= billing.isFirstInapp();
		secondName	= billing.getSecondName();
		secondInapp = billing.isSecondInapp();
		thirdName	= billing.getThirdName();
		thirdInapp	= billing.isThirdInapp();
		freeName	= billing.getFreeName();

		if (billing.hasFirst())
		{
			Button b = (Button) findViewById(Res.id.first_button);
			b.setText(billing.getFirstText());
			b.setVisibility(View.VISIBLE);
		}
		else
		{
			findViewById(Res.id.first_button).setVisibility(View.GONE);
		}

		if (billing.hasSecond())
		{
			Button b = (Button) findViewById(Res.id.second_button);
			b.setText(billing.getSecondText());
			b.setVisibility(View.VISIBLE);
		}
		else
		{
			findViewById(Res.id.second_button).setVisibility(View.GONE);
		}

		if (billing.hasThird())
		{
			Button b = (Button) findViewById(Res.id.third_button);
			b.setText(billing.getThirdText());
			b.setVisibility(View.VISIBLE);
		}
		else
		{
			findViewById(Res.id.third_button).setVisibility(View.GONE);
		}

		if (billing.hasFree())
		{
			Button b = (Button) findViewById(Res.id.tryfree_button);
			b.setVisibility(View.VISIBLE);
		}
		else
		{
			findViewById(Res.id.tryfree_button).setVisibility(View.GONE);
		}

		if (!billing.hasGooglePlay())
		{
			findViewById(Res.id.first_button).setVisibility(View.GONE);
			findViewById(Res.id.second_button).setVisibility(View.GONE);
			findViewById(Res.id.third_button).setVisibility(View.GONE);
			findViewById(Res.id.tryfree_button).setVisibility(View.GONE); // TODO XXX show try free if no GP
			findViewById(Res.id.ok_button).setVisibility(View.VISIBLE);

			TextView text = (TextView) findViewById(Res.id.dialog_text);
			text.setText(Res.string.buy_text_no_googleplay);
		}
		else if (billing.hasNoItems())
		{
			findViewById(Res.id.first_button).setVisibility(View.GONE);
			findViewById(Res.id.second_button).setVisibility(View.GONE);
			findViewById(Res.id.third_button).setVisibility(View.GONE);
			findViewById(Res.id.tryfree_button).setVisibility(View.GONE);
			findViewById(Res.id.ok_button).setVisibility(View.VISIBLE);

			TextView text = (TextView) findViewById(Res.id.dialog_text);
			text.setText(Res.string.buy_text_no_connectivity); // TODO XXX no connectivity? but may be no subcriptions
		}

		if (!billing.serviceIsBound())
		{
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					showProgressDialog();

					// try to bound google play, wait 15 sec
					// TODO XXX why not use counter instead currentTimeMillis?
					long t = System.currentTimeMillis();
					while (!billing.serviceIsBound() && (System.currentTimeMillis() - t < 15000))
						Utils.sleep(1000);

					hideProgressDialog();
					if (!billing.serviceIsBound())
						showMessage(getString(Res.string.cant_connect_to_google_play));
				}
			}).start();
		}

		Statistics.addLog(Settings.LOG_BUY_SHOW);
		// TODO XXX may not first run if FIRSTRUN_AUTOSTART == false
		if (App.isFirstRun() && !statsForced)
		{
			statsForced = true;
			if (Settings.EVENTS_LOG)
				UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'buy show' statistic
		}
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

		if (Policy.getUserToken(true) == null)
			Preferences.clearProFunctions(false); // clear settings
	}

	public static void showAbove(Context context, String param, String value)
	{
		Intent intent = new Intent(context, BuyDialogActivity.class);
		//intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		if (param != null)
			intent.putExtra(param, value);

		context.startActivity(intent);
	}

	//
	public void onFirstClick(View view)
	{
		if (!lock.tryLock())
			return;
		try { onPurchaseClick(firstName, firstInapp); }
		finally { lock.unlock(); }
	}

	public void onSecondClick(View view)
	{
		if (!lock.tryLock())
			return;
		try { onPurchaseClick(secondName, secondInapp); }
		finally { lock.unlock(); }
	}

	public void onThirdClick(View view)
	{
		if (!lock.tryLock())
			return;
		try { onPurchaseClick(thirdName, thirdInapp); }
		finally { lock.unlock(); }
	}

	private void onPurchaseClick(final String name, final boolean isInapp)
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BUY_CLICKED + name);
				if (!billing.purchase(BuyDialogActivity.this, name, isInapp, SUBS_REQUEST_CODE)) {
					showMessage(getString(Res.string.cant_connect_to_google_play));
				}
                if (PublisherSettings.PUBLISHER.equals(Settings.PUBLISHERS.SAMSUNG)) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							BuyDialogActivity.this.finish();
						}
					});
                }
			}
		}).start();
	}

	public void onFreeClick(View view)
	{
		if (!lock.tryLock())
			return;
		try
		{
			if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BUY_CLICKED + freeName);
			billing.freeGet(BuyDialogActivity.this, freeName, GETFREE_REQUEST_CODE);
		}
		finally { lock.unlock(); }
	}

	public void onOkClick(View view)
	{
		finish();
	}

	//
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		if (requestCode != SUBS_REQUEST_CODE && requestCode != GETFREE_REQUEST_CODE)
			return;

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				showProgressDialog();
				boolean result = processBuyResult(requestCode, resultCode, data, billing, promoCode);
				hideProgressDialog();

				// end
				boolean close = false;

				if (resultCode == RESULT_OK && requestCode == GETFREE_REQUEST_CODE && result)
				{
					if (Settings.LIC_FREE_AUTO)
						close = true;
					else
						showMessage(getString(Res.string.purchase_ok));
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
						public void run() { BuyDialogActivity.this.finish(); }
					});
				}
			}
		}).start();
	}

	// return true on successful buy result processing
	// called also from SubsNeedDialogActivity
	public static boolean processBuyResult(final int requestCode, final int resultCode,
											final Intent data, final InAppBilling billing,
											final String promoCode)
	{
		if (requestCode != SUBS_REQUEST_CODE && requestCode != GETFREE_REQUEST_CODE)
			return false;

		boolean result = false;

		if (resultCode == RESULT_OK)
		{
			// GP buy or free captcha accepted, activating subscription
			Statistics.addLog(Settings.LOG_BUY_ACCEPTED);

			//if (requestCode == SUBS_REQUEST_CODE)
				billing.referrerUpdate(promoCode);

			result = (requestCode == SUBS_REQUEST_CODE) ?
						billing.purchaseComplete(data) :
							billing.freeComplete(data);

			// init evaluate and first result notifications
			if (result)
			{
				TimerService.evaluateNotifyInitTimer((requestCode == SUBS_REQUEST_CODE));
				TimerService.firstResultNotifyInitTimer();
			}
		}

		// check activate result
		if (result)
		{
			Statistics.addLog(Settings.LOG_BUY_COMPLETED);
		}
		else
		{
			if (resultCode == RESULT_OK)
				Statistics.addLog(Settings.LOG_BUY_ERR); // activate error
			else
				Statistics.addLog(Settings.LOG_BUY_DECLINED);

			//Preferences.clearProFunctions(false); // need???
		}

		if (Settings.EVENTS_LOG && (result || resultCode == RESULT_OK))
			UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'buy completed' statistic

		return result;
	}

/*
	public void showPurchaseResult(boolean ok)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false).setIcon(Res.drawable.icon).setMessage(ok ? Res.string.purchase_ok : Res.string.purchase_error);
		builder.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				Utils.dialogCancel(null, (Dialog) dialog);
			}
		});
		Dialog dialog = builder.create();
		Utils.dialogShow(this, dialog);
	}
*/

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

					progress = new ProgressDialog(BuyDialogActivity.this);
					progress.setIndeterminate(true);
					progress.setCancelable(false);
					progress.setTitle(Res.string.please_wait);
					Utils.dialogShow(BuyDialogActivity.this, progress);
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
						Utils.dialogClose(BuyDialogActivity.this, progress, false);
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
				final AlertDialog.Builder builder = new AlertDialog.Builder(BuyDialogActivity.this);
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
						BuyDialogActivity.this.finish();
					}
				});
				Utils.dialogShow(BuyDialogActivity.this, dialog);
			}
		});
	}
}
