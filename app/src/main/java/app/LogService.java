package app;

import app.internal.Preferences;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import app.netfilter.FilterVpnService;
import app.info.Statistics;
import app.common.debug.L;
import app.internal.Settings;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.util.ArrayList;
import java.util.List;

// !!! don't use here any code from native libs

public class LogService extends JobIntentService {

	private static final int JOB_ID = 1339;


	public static final String INTENT = Settings.APP_PACKAGE + ".action.LOG_ERROR";

	public static final String EXTRA_STACKTRACE		  = "stacktrace";
	public static final String EXTRA_FATAL			  = "fatal";
	public static final String EXTRA_TAG			  = "tag";
//	public static final String EXTRA_WIDTH			  = "width";
//	public static final String EXTRA_HEIGHT			  = "height";
//	public static final String EXTRA_DENSITY		  = "density";
	public static final String EXTRA_MANUFACTURER	  = "manufacturer";
	public static final String EXTRA_MODEL			  = "model";
	public static final String EXTRA_BRAND			  = "brand";
	public static final String EXTRA_RELEASE		  = "release";
	public static final String EXTRA_FINGERPRINT	  = "fingerprint";
	public static final String EXTRA_INSTALL_ID		  = "install_id";
	public static final String EXTRA_MEMINFO		  = "meminfo";
	public static final String EXTRA_MEMTOTAL		  = "mem_total";
	public static final String EXTRA_MEMFREE		  = "mem_free";
	public static final String EXTRA_TCP_CLIENT_COUNT = "tcp_clients";
	public static final String EXTRA_UDP_CLIENT_COUNT = "udp_clients";
	public static final String EXTRA_PACKETS_COUNT	  = "packets_count";

	static void startService(Context context, Intent work) {
		enqueueWork(context, work);
	}

	private static void enqueueWork(Context context, Intent work) {
		enqueueWork(context, LogService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {

		if (App.isLibsLoaded()) {
			// other actions on crash

			if (Preferences.isActive()) {
				restartVPNService(this);
			}
		}

		try {
			PackageManager pm = getPackageManager();
			PackageInfo pi = pm.getPackageInfo(App.packageName(), 0);
			final String versionName = pi.versionName;
			final String versionCode = Integer.toString(pi.versionCode);
			final String packageName = pi.packageName;

			final String trace = intent.getStringExtra(EXTRA_STACKTRACE);
			final String tag = intent.getStringExtra(EXTRA_TAG);
			final boolean fatal = intent.getBooleanExtra(EXTRA_FATAL, true);
//			  final int width = intent.getIntExtra(EXTRA_WIDTH, 0);
//			  final int height = intent.getIntExtra(EXTRA_HEIGHT, 0);
//			  final int density = intent.getIntExtra(EXTRA_DENSITY, 0);

			StringBuilder sb = new StringBuilder(500);
			if (fatal) {
				sb.append("Fatal Error in:\n");
			} else {
				sb.append("Catched Error in:\n");
			}

			sb.append("Package: ").append(packageName).append(" Version: ").append(versionName).append(" Code: ").append(versionCode).append('\n');
			sb.append("Tag: ").append(tag).append("\n\n");

			sb.append(trace);

			final Bundle extras = intent.getExtras();

			sb.append("\nManufacturer: ").append(extras.getString(EXTRA_MANUFACTURER));
			sb.append("\nBrand: ").append(extras.getString(EXTRA_BRAND));
			sb.append("\nModel: ").append(extras.getString(EXTRA_MODEL));
			sb.append("\nRelease: ").append(extras.getString(EXTRA_RELEASE));
			sb.append("\nFingerprint: ").append(extras.getString(EXTRA_FINGERPRINT));
			sb.append("\nInstallId: ").append(extras.getString(EXTRA_INSTALL_ID));
			if (extras.containsKey(EXTRA_MEMINFO)) {
				sb.append("\nMemInfo: \n").append(extras.getString(EXTRA_MEMINFO));
			}
			sb.append("\nMemTotal: ").append(extras.getLong(EXTRA_MEMTOTAL));
			sb.append("\nMemFree: ").append(extras.getLong(EXTRA_MEMFREE));
			sb.append("\nTCPClients: ").append(extras.getInt(EXTRA_TCP_CLIENT_COUNT));
			sb.append("\nUDPClients: ").append(extras.getInt(EXTRA_UDP_CLIENT_COUNT));
			sb.append("\nPackets: ").append(extras.getInt(EXTRA_PACKETS_COUNT));
			//sb.append("\nScreen: ").append(width).append("x").append(height).append(" (").append(density).append(" dpi)");

			//postLogData(URL, packageName, MAIL, sb.toString());
			Statistics.addException(sb.toString());
			UpdaterService.startUpdate(UpdaterService.START_FORCE_DELAYED); // exception

			L.d(Settings.TAG_LOGSERVICE, sb.toString());
		}
		// guess this one isn't getting reported...
		catch (final Exception e) { e.printStackTrace(); }

	}

	// TODO XXX check this
	private static void restartVPNService(Context context) {
		long firstCrashTime = Preferences.get_l("firstCrashTime");
		int crashCount = Preferences.get_i("crashCount");
		long curTime = System.currentTimeMillis();

		boolean needRestart = true;

		if (firstCrashTime > 0 && curTime - firstCrashTime < 600000) {
			if (crashCount >= 2) {
				needRestart = false;

				Preferences.editStart();
				Preferences.putLong("firstCrashTime", 0);
				Preferences.putInt("crashCount", 0);
				Preferences.editEnd();
			}
		}

		if (needRestart) {
			FilterVpnService.startVpnByAlarm(context, false, curTime + 10000);
		}
	}

	protected boolean postLogData(final String url, final String subject, final String mail, final String data)
	{
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(url);
		//HttpResponse response;
		//httppost.setHeader("Accept-Language", "ru-RU,en,*");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("subj", subject));
		nvps.add(new BasicNameValuePair("mail", mail));
		nvps.add(new BasicNameValuePair("text", data));

		try
		{
			httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			//response = httpclient.execute(httppost);
			httpclient.execute(httppost);

			L.d(Settings.TAG_LOGSERVICE, "Report sent!");

			return true;
		}
		catch (Exception e)
		{
			// TODO XXX Make it remember the data to send it later
			L.e(Settings.TAG_LOGSERVICE, "Error sending report!");
		}

		return false;
	}

}
