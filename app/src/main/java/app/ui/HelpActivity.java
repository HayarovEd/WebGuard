package app.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import app.internal.Preferences;
import app.Res;
import app.info.Statistics;
import app.internal.Settings;


public class HelpActivity extends Activity
{
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(android.R.style.Theme_DeviceDefault_Light);

		setContentView(Res.layout.help_activity);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

		Statistics.addLog(Settings.LOG_HELP_SHOW);
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();

		overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				onBackPressed();
				break;

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}
}
