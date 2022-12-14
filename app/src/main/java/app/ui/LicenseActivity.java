package app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import app.App;
import app.internal.Preferences;
import app.Res;
import app.UpdaterService;
import app.info.Statistics;
import app.internal.Settings;


public class LicenseActivity extends Activity
{
	private boolean active = false;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setFinishOnTouchOutside(false);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(Res.style.DialogWindowTitleText_Light_Multiline);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(Res.layout.license_dialog);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		active = getIntent().getBooleanExtra("active", false);

		TextView tv = (TextView) findViewById(Res.id.dialog_text);
		String s = Preferences.getLicenseActivityText();
		tv.setText(Html.fromHtml(s));

		boolean changed_terms = Preferences.termsUpdated();
		boolean changed_policy = Preferences.policyUpdated();

		if (!Preferences.hasSavedPolicy())
		{
			// user didn't check license yet, show 'cancel', 'agree' buttons
			setTitle(Res.string.license_argeement);
			findViewById(Res.id.ok).setVisibility(View.GONE);
		}
		else if (changed_terms || changed_policy)
		{
			// user agree with license but we change it on update, show 'ok' button
			setTitle(Res.string.license_agreement_changes);
			findViewById(Res.id.ok_cancel).setVisibility(View.GONE);
			findViewById(Res.id.ok).setVisibility(View.VISIBLE);
		}
		else
		{
			// hmm, strange case, show 'cancel', 'agree' buttons
			setTitle(Res.string.license_argeement);
			findViewById(Res.id.ok).setVisibility(View.GONE);
		}

/*
		if (!App.initedAll())
		{
			// app not initialized. ahaha, disable button
			findViewById(R.id.agree_button).setEnabled(false);

			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					// TODO XXX waiting for initialization (5 sec)
					if (!App.waitForInitedAll(5))
						Statistics.addLogFast(Settings.LOG_INIT_TIMEOUT + "0");

					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							findViewById(R.id.agree_button).setEnabled(true);
						}
					});
				}
			}).start();
		}
*/

		//Linkify.addLinks(tv, Linkify.ALL); // exception on Be Pro
		try { Linkify.addLinks(tv, Linkify.WEB_URLS); } catch (NullPointerException e) { }
		tv.setMovementMethod(LinkMovementMethod.getInstance());
	}

	public void onAgreeClick(View view)
	{
		Preferences.savePolicyAndTerms();

		if (Settings.EVENTS_LOG)
		{
			Statistics.addLog(Settings.LOG_LICENSE_ACCEPTED);
			UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'agree' statistic
		}

		if (active)
			App.startVpnService(this);
		else
			OptionsActivity.start(this);

		finish();
	}

	public void onRefuseClick(View view)
	{
		finish();
	}

	public void onOkayClick(View view)
	{
		onAgreeClick(view);
	}

	public static void start(Context context)
	{
		Intent intent = new Intent(context, LicenseActivity.class);
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

		Intent intent = new Intent(context, LicenseActivity.class);
		if (flags != 0)
			intent.setFlags(flags);

		return intent;
	}
}
