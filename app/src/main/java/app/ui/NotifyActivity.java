package app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import app.scanner.LibScan;
import app.App;
import app.internal.Preferences;
import app.Res;
import app.info.Statistics;
import app.internal.Settings;
import app.security.Policy;

public class NotifyActivity extends Activity
{
	private String domain = null;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setFinishOnTouchOutside(false);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(Res.style.DialogWindowTitleText_Light_Multiline);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(Res.layout.notify_activity);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		Intent intent = getIntent();

		if (intent.hasExtra("title"))
			setTitle(intent.getStringExtra("title"));

		if (intent.hasExtra("text"))
			((TextView) findViewById(Res.id.dialog_text)).setText(intent.getStringExtra("text"));

		if (intent.hasExtra("domain"))
			domain = intent.getStringExtra("domain");
	}

	// recordType == LibScan.RECORD_TYPE_*
	public static void show(int recordType, String domain)
	{
		Context context = App.getContext();

		if (domain == null)
		{
			domain = "";
			Statistics.addLog(Settings.LOG_NOTIFIER_DOMAIN_ERR);
		}

		String text = context.getString(getStringId(recordType, false));
		text = text.replace("%s", domain);

		Intent intent = new Intent(context, NotifyActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		intent.putExtra("title", context.getString(getStringId(recordType, true)));
		intent.putExtra("text", text);
		intent.putExtra("domain", domain);

		context.startActivity(intent);

		if (recordType == LibScan.RECORD_TYPE_CHARGEABLE)
			Policy.statsUpdateChargeable(); // TODO may be move from here? or callbacks to policy?
	}

	// recordType == LibScan.RECORD_TYPE_*
	private static int getStringId(int recordType, boolean title)
	{
		switch(recordType)
		{
			case LibScan.RECORD_TYPE_CHARGEABLE:
				return ((title) ? Res.string.paid_site_detected : Res.string.paid_site_blocked);

			case LibScan.RECORD_TYPE_FRAUD:
				return ((title) ? Res.string.fishing_site_detected : Res.string.fishing_site_blocked);

			case LibScan.RECORD_TYPE_MALWARE:
				return ((title) ? Res.string.malware_site_detected : Res.string.malware_site_blocked);

			default:
				return Res.string.app_name;
		}
	}

	public void onOkayClick(View view)
	{
		finish();
	}

	public void onErrorClick(View view)
	{
		SendMessageActivity.sendMessage(domain, true);
		finish();
	}
}
