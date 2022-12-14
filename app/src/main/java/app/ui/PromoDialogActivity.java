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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import app.common.Utils;
import app.App;
import app.internal.InAppBilling;
import app.internal.Preferences;
import app.Res;
import app.internal.Settings;

// http://promo.webguard.ru/1/getPromoCode.php

public class PromoDialogActivity extends Activity
{
	private InAppBilling billing = null;
	private final Handler handler = new Handler();
	private ProgressDialog progress = null;

	private boolean textEmpty = false;

	//private static String lastPromoCode = "dfdf";
	private static String lastPromoCode = null;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setFinishOnTouchOutside(false);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(Res.style.DialogWindowTitleText_Light_Multiline);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(Res.layout.promo_dialog_activity);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		billing = App.getBilling();
		//billing.serviceBind();

		updateData(true);

		EditText mEditText = (EditText) findViewById(Res.id.code_text);
		mEditText.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(Editable s) { }
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			public void onTextChanged(CharSequence s, int start, int before, int count)
			{
				updateData(false);
			}
		});
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

	public static void showAbove(Context context)
	{
		Intent intent = new Intent(context, PromoDialogActivity.class);
		//intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		context.startActivity(intent);
	}

	//
	private void updateData(boolean firstRun)
	{
		EditText mEditText = (EditText) findViewById(Res.id.code_text);

		if (firstRun && lastPromoCode != null)
			// restore previous valid promocode
			mEditText.setText(lastPromoCode);

		// update button text

		final String promoCodeText = mEditText.getText().toString();
		Button b = (Button) findViewById(Res.id.activate_button);

		boolean e = promoCodeText.isEmpty();
		if (e && !textEmpty)
		{
			b.setText(getString(Res.string.skip));
			textEmpty = true;
		}
		else if (!e && textEmpty)
		{
			b.setText(getString(Res.string.start));
			textEmpty = false;
		}
	}

	public void onCodeClick(View view)
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				showProgressDialog();

				EditText mEditText = (EditText) findViewById(Res.id.code_text);
				final String promoCodeText =
					mEditText.getText().toString().replaceAll("[\\s-]","");

				final int result = billing.promoCheck(PromoDialogActivity.this, promoCodeText);

				hideProgressDialog();

				handler.post(new Runnable() {
					@Override
					public void run()
					{
						if (result == 0)
						{
							// ok, closing. see InAppBilling.promoCheck
							lastPromoCode = promoCodeText;
							PromoDialogActivity.this.finish();
						}
						else if (result == -1)
						{
							showError(getString(Res.string.promo_text_no_connectivity), false);
						}
						else
						{
							// result == 1 or 2
							showError(getString(Res.string.promo_text_invalid_code), false);
						}
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

					progress = new ProgressDialog(PromoDialogActivity.this);
					progress.setIndeterminate(true);
					progress.setCancelable(false);
					progress.setTitle(Res.string.please_wait);
					Utils.dialogShow(PromoDialogActivity.this, progress);
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
						Utils.dialogClose(PromoDialogActivity.this, progress, false);
						progress = null;
					}
				}
			}
		});
	}

	public void showError(final String text, final boolean closeOnDismiss)
	{
		handler.post(new Runnable() {
			@Override
			public void run()
			{
				final AlertDialog.Builder builder = new AlertDialog.Builder(PromoDialogActivity.this);
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
						if (closeOnDismiss)
							PromoDialogActivity.this.finish();
						else
							updateData(false);
					}
				});
				Utils.dialogShow(PromoDialogActivity.this, dialog);
			}
		});
	}
}
