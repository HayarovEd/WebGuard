package app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import app.common.Utils;
import app.App;
import app.internal.InAppBilling;
import app.internal.Preferences;
import app.Res;
import app.UpdaterService;
import app.info.Statistics;
import app.internal.Settings;

public class TryFreeDialogActivity extends Activity
{
	private InAppBilling billing = null;
	private final Handler handler = new Handler();
	private ProgressDialog progress = null;

	// TODO XXX move to common settings class
	private byte[] tokenBytes = new byte[36]; // "free" + GP token length
	private String token = null;
	private boolean appAdsBlock = false;
	private String promoCode = null;

	private static boolean statsForced = false;

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
							Res.layout.tryfree_dialog_activity);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		Intent i = getIntent();
		if (i != null && i.hasExtra(InAppBilling.PROMO_CODE_PARAM)) // see InAppBilling.promoCheck
			promoCode = i.getStringExtra(InAppBilling.PROMO_CODE_PARAM);

		//handler = new Handler();

		billing = App.getBilling();
		//billing.serviceBind();

		//

		if (!Settings.LIC_FREE_AUTO)
		{
			// TODO XXX may not first run if FIRSTRUN_AUTOSTART == false
			if (App.isFirstRun() && !statsForced)
			{
				statsForced = true;
				if (Settings.EVENTS_LOG)
				{
					Statistics.addLog(Settings.LOG_TRY_FREE);
					UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'try free' statistic
				}
			}
		}

		updateData(true);
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

/*
	public static void showAbove(Context context)
	{
		Intent intent = new Intent(context, BuyDialogActivity.class);
		//intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		context.startActivity(intent);
	}
*/

	private void updateData(final boolean create)
	{
		final TextView mTextView = (TextView) findViewById(Res.id.tryfree_text);
		final String tryFree = getString(Res.string.tryfree_text);

		if (create && mTextView != null)
			mTextView.setText(tryFree.replace("XXX", "1")); // default value - 1 day

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				showProgressDialog();

				byte[] result = new byte[3];
				final Bitmap image = billing.freeTry(result, tokenBytes, promoCode, !Settings.LIC_FREE_AUTO);

				hideProgressDialog();

				if (result[0] == -1)
				{
					final String text = (Settings.LIC_FREE_AUTO) ? getString(Res.string.tryfree_text_no_connectivity) :
																	getString(Res.string.tryfree_text_no_connectivity_captcha);
					showError(text, Settings.LIC_FREE_AUTO, false, false);
					return;
				}
				else if (result[0] == 1)
				{
					// no more free subscription
					showError(getString(Res.string.tryfree_text_no_more), true, false, true);
					return;
				}

				// ok
				token = new String(tokenBytes, 0, 0, tokenBytes.length);
				appAdsBlock = (result[1] == 1) ? true : false; // full?
				final String days = Integer.toString(result[2]);

				if (Settings.LIC_FREE_AUTO)
				{
					onCodeClick(null);
				}
				else
				{
					handler.post(new Runnable()
					{
						@Override
						public void run()
						{
							ImageView mImageView = (ImageView) findViewById(Res.id.captcha);
							mImageView.setImageBitmap(image);
							EditText mEditText = (EditText) findViewById(Res.id.captcha_text);
							mEditText.setText("");
							mTextView.setText(tryFree.replace("XXX", days)); // set days text
						}
					});
				}
			}
		}).start();
	}

	public void onRefreshClick(View view)
	{
		updateData(false);
	}

	public void onCodeClick(View view)
	{
		if (token == null)
			return;

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				showProgressDialog();

				EditText mEditText = (EditText) findViewById(Res.id.captcha_text);
				String captchaText = null;
				if (mEditText != null)
					captchaText = mEditText.getText().toString();

				final int result =
					billing.freeConfirm(TryFreeDialogActivity.this, captchaText, token, appAdsBlock, !Settings.LIC_FREE_AUTO);

				hideProgressDialog();

				handler.post(new Runnable() {

					@Override
					public void run()
					{
						if (result == 0)
							// ok, closing. see InAppBilling.freeConfirm
							TryFreeDialogActivity.this.finish();
						else if (result == -1)
							showError(getString(Res.string.tryfree_text_no_connectivity), Settings.LIC_FREE_AUTO, false, false);
						else
							// result == 1
							showError(getString(Res.string.tryfree_text_invalid_captcha), Settings.LIC_FREE_AUTO, true, false);
					}
				});
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

					progress = new ProgressDialog(TryFreeDialogActivity.this);
					progress.setIndeterminate(true);
					progress.setCancelable(false);
					progress.setTitle(Res.string.please_wait);
					Utils.dialogShow(TryFreeDialogActivity.this, progress);
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
						Utils.dialogClose(TryFreeDialogActivity.this, progress, false);
						progress = null;
					}
				}
			}
		});
	}

	public void showError(final String text, final boolean closeOnDismiss,
							final boolean runUpdate, final boolean buyButton)
	{
		handler.post(new Runnable() {
			@Override
			public void run()
			{
				final AlertDialog.Builder builder = new AlertDialog.Builder(TryFreeDialogActivity.this);
//				builder.setCancelable(Settings.NOTIFY_BUY_BUTTON && buyButton); // Roman Popov fixed bug 04/11/2021
				builder.setCancelable(false);
				builder.setIcon(Res.drawable.icon).setMessage(text);
				builder.setNeutralButton((Settings.NOTIFY_BUY_BUTTON && buyButton) ? Res.string.buy_subscription : android.R.string.ok,
											new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (Settings.NOTIFY_BUY_BUTTON && buyButton)
						{
						    // вот тут была проблема - не открывается диалог с вариантами подписок по нажатию Купить подписку
//							Intent intent = OptionsActivity.getIntent(TryFreeDialogActivity.this, false);
							Intent intent = OptionsActivity.getIntent(TryFreeDialogActivity.this, true);
							intent.putExtra(Notifications.BUY_ACTION, 1);
							TryFreeDialogActivity.this.startActivity(intent);
						}

						Utils.dialogClose(null, (Dialog) dialog, false);
					}
				});

				Dialog dialog = builder.create();
				dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
				{
					@Override
					public void onDismiss(DialogInterface dialog) {
						if (closeOnDismiss) {
							TryFreeDialogActivity.this.finish();
						} else if (runUpdate) {
							updateData(false);
						}
					}
				});
				Utils.dialogShow(TryFreeDialogActivity.this, dialog);
			}
		});
	}

}
