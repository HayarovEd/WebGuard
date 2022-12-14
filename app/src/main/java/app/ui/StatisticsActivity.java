package app.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.common.Utils;
import app.App;
import app.internal.Preferences;
import app.Res;
import app.info.Statistics;
import app.internal.Settings;
import app.security.Policy;
import java.util.Random;


public class StatisticsActivity extends Activity
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(android.R.style.Theme_DeviceDefault_Light);

		setContentView(Res.layout.statistics_activity);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

		Statistics.addLog(Settings.LOG_STATS_SHOW);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		// update stats

		String data[] = getWorkStatsData(false);

		View ads_blocked_layout = findViewById(Res.id.ads_blocked_layout);

		TextView ads_blocked = findViewById(Res.id.ads_blocked);
		ads_blocked.setText(data[0]);

		TextView malware_blocked = findViewById(Res.id.malware_blocked);
		malware_blocked.setText(data[1]);

		TextView traffic_saved = findViewById(Res.id.traffic_saved);
		traffic_saved.setText(data[2]);

		if (Preferences.get(Settings.PREF_APP_ADBLOCK)) {
			ads_blocked_layout.setVisibility(LinearLayout.VISIBLE);
			ads_blocked.setVisibility(LinearLayout.VISIBLE);
		} else {
			ads_blocked_layout.setVisibility(LinearLayout.GONE);
			ads_blocked.setVisibility(LinearLayout.GONE);
		}

		//((TextView) findViewById(R.id.proxy_traffic_saved)).setText(proxyTrafficSavedHuman);
	}

	public static String getWorkStatsSmallText(boolean useRandom)
	{
		Context context = App.getContext();

		String data[] = getWorkStatsData(useRandom);

		String text = context.getString(Res.string.work_stats_small);
		text = text.replace("XXX", data[0]).replace("YYY", data[1]).replace("ZZZ", data[2]);

		return text;
	}

	// return array with minimal stats data (ads count, malware count, traffic saved in human form)
	public static String[] getWorkStatsData(boolean useRandom)
	{
		Context context = App.getContext();

		// update used stats

		long[] policyStats = Policy.statsGetInfo();
		Policy.statsReset();
		Statistics.updateStats(null, null, policyStats);

		// get data

		int blockedAdsIpCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_ADS_IP);
		int blockedAdsUrlCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_ADS_URL);
		int blockedApkCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_APK);
		int blockedMalwareSiteCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_MALWARE);
		int blockedPaidSiteCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_PAID);
		long proxyCompressionSave = Preferences.get_l(Settings.STATS_COMPRESSION_SAVE);

		int adsCount = blockedAdsIpCount * 2 + blockedAdsUrlCount;
		if (adsCount == 0 && useRandom)
		{
			// fake values
			adsCount = (new Random()).nextInt((7 - 3) + 1) + 3;
			Preferences.putInt(Settings.STATS_POLICY_BLOCK_ADS_URL, adsCount);
		}

		int malwareCount = blockedApkCount + blockedMalwareSiteCount + blockedPaidSiteCount;
		if (malwareCount == 0 && useRandom)
		{
			// fake values
			malwareCount = (new Random()).nextInt((3 - 1) + 1) + 1;
			Preferences.putInt(Settings.STATS_POLICY_BLOCK_MALWARE, malwareCount);
		}

		//long trafficSaved = adsCount * 51200; // 50kb
		long trafficSaved = adsCount * 51200 + proxyCompressionSave;
		String trafficSavedHuman = Utils.byteCountToHuman(trafficSaved, context);

		//String proxyTrafficSavedHuman = Utils.byteCountToHuman(proxyCompressionSave, context);

		//

		String data[] = new String[3];

		data[0] = Integer.toString(adsCount);
		data[1] = Integer.toString(malwareCount);
		data[2] = trafficSavedHuman;
		//data[3] = proxyTrafficSavedHuman;

		return data;
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
