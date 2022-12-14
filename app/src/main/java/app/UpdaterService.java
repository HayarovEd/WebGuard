package app;

import app.internal.Preferences;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.util.Base64;
import app.common.Hasher;
import app.common.NetUtil;
import app.info.Statistics;
import app.security.Database;
import app.security.Exceptions;
import app.security.Browsers;
import app.common.debug.L;
import app.common.Utils;
import app.internal.Settings;
import app.security.Policy;
import app.ui.Notifications;
import org.json.JSONException;
import org.json.JSONObject;
import org.squareup.okhttp.OkHttpClient;
import org.squareup.okhttp.OkUrlFactory;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.*;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/*
 * rewrite some parts of code, but maybe some problems with sync.
 * startAlarm and sheduleNext can overwrite values from other threads
 * also on crush some updates may not run (or run after interval) and
 *	   too many calls startUpdate (see PromptActivity LicenseActivity)
 *
 * !!! don't use here any code from native libs
 *
 * TODO XXX refactor and rewrite code
 */

public class UpdaterService extends Service {

	private static final int JOB_ID = 1337; //1343;


	public static final int START_DELAYED		= 1; // time check -> on check -> min interval check -> thread -> exit if running -> delay -> work
	public static final int START_EXACT			= 2; // time check -> on check -> min interval check -> thread -> exit if running -> work
	public static final int START_EXACT_FAST	= 3; // after connect error on START_EXACT
	public static final int START_FORCE_DELAYED = 4; // min interval check -> thread -> exit if running -> delay -> work
	public static final int START_FORCE			= 5; // thread -> wait if running -> work

	public static final int UPDATE_ERRORS_MIN = 2;	 // after that error counter value, update will be started on network change

	private static final int SERVER_ERROR		   = -1;
	private static final int SERVER_OK			   = 0;
	private static final int SERVER_GOT_UPDATE	   = 1;
	private static final int SERVER_GOT_FASTUPDATE = 2;
	private static final int SERVER_GOT_ALLUPDATES = 3;

	private static final long START_DELAY_TIME	   = 15 * 1000;		// 15 sec for delayed start
	private static final long ERROR_DELAY_TIME	   = 5 * 60 * 1000; // 5 min interval for update retry on first error (START_EXACT_FAST)
	private static final long LAST_UPDATE_INTERVAL = 1 * 60 * 1000; // 1 min minimal interval between updates

	public static final String START_UPDATE = "start_update";
	public static final String STOP_SERVICE = "stop_service";

	public static final String TMP_NAME		= "/update.tmp";
	public static final String TMP_FASTNAME = "/updatefast.tmp";
	public static final String CERT_NAME	= "public.pem";

	public static final Object lock0 = new Object();
	private static ReentrantLock lock = new ReentrantLock();

	//static
	//{
	//	  L.a(Settings.TAG_UPDATESERVICE, "--- updater ---");
	//}

	// use this function to start updates (see run() comments!)
	public static void startUpdate(int startId) {
		L.d(Settings.TAG_UPDATESERVICE, "startUpdate");

		if (startId < START_DELAYED || startId > START_FORCE)
			return;

		Intent intent = getIntent(startId);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			App.getContext().startForegroundService(intent);
		} else {
			App.getContext().startService(intent);
		}
	}

	private static Intent getIntent(int startId)
	{
		Intent i = new Intent(App.getContext(), UpdaterService.class);
		i.setAction(START_UPDATE);

		if (startId == START_FORCE || startId == START_FORCE_DELAYED)
			i.putExtra("force", true);
		if (startId == START_DELAYED || startId == START_FORCE_DELAYED)
			i.putExtra("delay", true);
		if (startId == START_EXACT_FAST)
			i.putExtra("fast", true);

		return i;
	}

	public static void stopService(Context context)
	{
//		context.stopService(new Intent(context, UpdaterService.class));

		Intent i = new Intent(context, UpdaterService.class);
		i.setAction(STOP_SERVICE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(i);
		} else {
			context.startService(i);
		}
	}

	private static void stop()
	{
		if (Settings.UPDATER_STOP_ON_IDLE || !Preferences.isActive())
			UpdaterService.stopService(App.getContext());
	}

	//

	// don't call this, call sheduleNext
	private static void sheduleAlarm(long time, int startId)
	{
		Intent i = getIntent(startId);
		PendingIntent pi = PendingIntent.getService(App.getContext(), startId, i, 0);
		AlarmManager am = (AlarmManager) App.getContext().getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, time, pi);
	}

	private static long sheduleNext(long nextTime)
	{
		if (!Preferences.isActive() || nextTime <= 0)
			return -1;

		Preferences.putLong(Settings.PREF_UPDATE_TIME, nextTime);
		sheduleAlarm(nextTime, START_EXACT);

		return nextTime;
	}

	private static long sheduleNext(boolean fastRetry)
	{
		long retryTime = Preferences.getUpdateRetryTime() * 1000L;

		if (!Preferences.isActive() || retryTime <= 0)
			return -1;

		long nextTime = System.currentTimeMillis();
		if (fastRetry)
			// on first error set short interval before next try
			// TODO XXX maybe overwritten by START_FORCE, START_FORCE_DELAYED
			nextTime += ERROR_DELAY_TIME;
		else
			nextTime += retryTime;

		Preferences.putLong(Settings.PREF_UPDATE_TIME, nextTime);
		sheduleAlarm(nextTime, ((fastRetry) ? START_EXACT_FAST : START_EXACT));

		return nextTime;
	}

	// service functions

	public UpdaterService() // comment this to test crashes handler ;))
	{
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Notification n = getNotification29(getApplicationContext());
			startForeground(JOB_ID, n);
		} else {
			startForeground(JOB_ID, new Notification());
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

	}


	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		L.d(Settings.TAG_UPDATESERVICE, "onStartCommand");

		// Roman Popov 06.05.2021 пока убрали - тестер будет тестить
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//			Notification n = getNotification29(getApplicationContext());
//			startForeground(JOB_ID, n);
//		} else {
//			startForeground(JOB_ID, new Notification());
//		}
		//

		final String action = (intent == null) ? null : intent.getAction();

		if (STOP_SERVICE.equals(action))
		{
			stopSelf(); // may be move after sheduleNext?
			return START_NOT_STICKY;
		}

		final boolean enabled = Preferences.isActive();
		final long time = System.currentTimeMillis();
		final long updateTime = Preferences.get_l(Settings.PREF_UPDATE_TIME);
		final long retryTime = Preferences.getUpdateRetryTime() * 1000L;

		boolean update = (time >= updateTime); // use here because sheduleNext changes PREF_UPDATE_TIME

		// TODO XXX always update alarm. sync problems.
		if (!update && (updateTime - time) < retryTime)
			sheduleNext(updateTime);
		else
			sheduleNext(false);

		//int count = Statistics.getAppsCount();
		//if (!enabled && count <= 10)

		final boolean force = (intent == null) ? false : intent.hasExtra("force");
		final boolean delay = (intent == null) ? false : intent.hasExtra("delay");
		final boolean fast = (intent == null) ? false : intent.hasExtra("fast");
		//intent.removeExtra("force");
		//intent.removeExtra("delay");
		//intent.removeExtra("fast");

		if ((!enabled && !force) || !START_UPDATE.equals(action))
		{
			stop();
			return START_NOT_STICKY;
		}

		long lastUpdateTime = Preferences.get_l(Settings.PREF_LAST_UPDATE_TIME);
		if (lastUpdateTime > time)
			lastUpdateTime = 0; // last update time from future, reset

		// check times, see START_DELAYED comments
		if (force || update)
		{
			if ((!force || delay) && (lastUpdateTime + LAST_UPDATE_INTERVAL > time))
				{} // check minimal interval on every type except START_FORCE
			else
			{
				L.w(Settings.TAG_UPDATESERVICE, "Starting work");

				Thread t = new Thread(new Runnable()
				 {
					 @Override
					 public void run()
					 {
						 updateThread(force, delay, fast);
					 }
				 });
				t.start();
			}
		}

		stop();
		return START_NOT_STICKY;
	}

	/*
	 * work on update errors:
	 *
	 * variant 0 (first run): run -> sendData (or no network) -> err -> run on every network change until success
	 *
	 * variant 1: run -> sendData -> err -> errorCount + 1 -> error interval -> run -> sendData -> err -> errorCount + 1 ->
	 *			   normal interval -> run -> sendData -> err -> errorCount + 1 ->
	 *			   run on every network change until success
	 *
	 * variant 2: run -> no network -> errorCount + 2 -> normal interval -> run -> no network -> errorCount + 2 ->
	 *			   run on every network change until success
	 *
	 * variant 3 (force without delay): run -> sendData -> err -> errorCount + 2 -> error interval -> run -> sendData -> err ->
	 *									   run on every network change until success
	 *
	 * variant 4 (force without delay): run -> sendData -> no network -> errorCount + 3 ->
	 *									   run on every network change until success
	 *
	 * work on first run (3 update requests):
	 *
	 * App.onCreate -> if firstRun -> startUpdate START_FORCE_DELAYED (install knock)
	 * AppManager.updateAppList -> startUpdate START_FORCE			  (installed apps list)
	 * App.startVpnService -> startUpdate START_FORCE_DELAYED		  (on vpn start, update with token)
	 */
	public static void updateThread(boolean force, boolean delay, boolean fast)
	{
		if (Settings.DEBUG)
			L.d(Settings.TAG_UPDATESERVICE, "Updates thread f:" + force + " d:" + delay);

		// get lock
		if (!lock.tryLock())
		{
			if (!force || delay) // return, we already running
				return;
			else
				lock.lock(); // only force (without delay)
		}

		try
		{
			sheduleNext(false); // set next time to update

			if (delay)
				Utils.sleep(START_DELAY_TIME);

			L.a(Settings.TAG_UPDATESERVICE, "Check updates " + (new Date()).toString());
			L.w(Settings.TAG_UPDATESERVICE, "Checking network...");

			if (NetUtil.getStatus() == -1)
			{
				// oups, no network, try next time
				// update error count if sheduled update or not critical force
				// big update if critical force (see startUpdate onVPNStarted)

				int errorCount = Preferences.get_i(Settings.PREF_UPDATE_ERROR_COUNT);
				if (!force || delay) errorCount += 2;
				else errorCount += 3; // on START_FORCE
				Preferences.putInt(Settings.PREF_UPDATE_ERROR_COUNT, errorCount);

				sheduleNext(false);
				return;
			}

			L.w(Settings.TAG_UPDATESERVICE, "All is ok, updating...");

			// get statistics data if statistics enabled
			// TODO XXX get data this, but clear all data after send!!! can clear logs
			byte[] data = null;
			if (Preferences.get(Settings.PREF_STATS))
				data = Utils.deflate(statsGetData());

			String url = Preferences.get_s(Settings.PREF_UPDATE_URL);
			String urlFast = url;

			String referrer = Preferences.getReferrer(true);
			if (referrer != null)
				referrer = "&referrer=" + referrer;
			else
				referrer = "";

			if (url != null)
				url += "?publisher=" + Preferences.getPublisher() + referrer + "&action=update&appversion=" + Statistics.versionCode +
						"&version=" + Database.getCurrentVersion() +
						"&rand=" + System.currentTimeMillis();

			if (url != null)
				url += "&up=" + ((force) ? "1" : "0") + ((delay) ? "1" : "0") + ((fast) ? "1" : "0");

			if (urlFast != null)
				urlFast += "?publisher=" + Preferences.getPublisher() + "&action=fastupdate&appversion=" + Statistics.versionCode +
							"&rand=" + System.currentTimeMillis();

			if (Settings.DEBUG)
				L.d(Settings.TAG_UPDATESERVICE, "Sending stats to ", url, ", dataSize = ", (data != null ? Integer.toString(data.length) : null));
			if (Settings.DEBUG)
				L.d(Settings.TAG_UPDATESERVICE, "Fast update at ", url);

			L.a(Settings.TAG_UPDATESERVICE, "Sending data...");

			boolean fastRetry = false;
			int sentStatus = sendData(url, data);

			switch (sentStatus)
			{
			case SERVER_OK:

				Preferences.editStart();
				Preferences.putLong(Settings.PREF_LAST_UPDATE_TIME, System.currentTimeMillis());
				Preferences.putInt(Settings.PREF_UPDATE_ERROR_COUNT, 0);
				Preferences.putInt(Settings.PREF_FASTUPDATE_ERROR_COUNT, 0);
				Preferences.editEnd();

				statsDataSend(true);

				L.a(Settings.TAG_UPDATESERVICE, "Server ok! " + (new Date()).toString());
				break;

			case SERVER_GOT_UPDATE:
			case SERVER_GOT_ALLUPDATES:

				// TODO XXX set update time and reset counter but not check updateDatabases() result!!!
				Preferences.editStart();
				Preferences.putLong(Settings.PREF_LAST_UPDATE_TIME, System.currentTimeMillis());
				Preferences.putInt(Settings.PREF_UPDATE_ERROR_COUNT, 0);
				if (sentStatus == SERVER_GOT_UPDATE)
					Preferences.putInt(Settings.PREF_FASTUPDATE_ERROR_COUNT, 0);
				Preferences.editEnd();

				statsDataSend(true);

				L.a(Settings.TAG_UPDATESERVICE, "Got new update!" + (new Date()).toString());

				if (App.isLibsLoaded())
					updateDatabases();

				if (sentStatus == SERVER_GOT_UPDATE) // continue if have fast update
					break;

			case SERVER_GOT_FASTUPDATE:

				L.a(Settings.TAG_UPDATESERVICE, "Got fast update! " + (new Date()).toString());

				long fastUpdateVersion = getDataFast(urlFast);
				if (fastUpdateVersion != -1)
				{
					// TODO XXX set update time and reset counter but not check updateFastDatabases() result!!!
					Preferences.putInt(Settings.PREF_FASTUPDATE_ERROR_COUNT, 0);
					Database.setCurrentFastVersion(fastUpdateVersion);

					if (App.isLibsLoaded())
						updateFastDatabases();
				}
				else
				{
					int errorCount = Preferences.get_i(Settings.PREF_FASTUPDATE_ERROR_COUNT);
					Preferences.putInt(Settings.PREF_FASTUPDATE_ERROR_COUNT, errorCount + 1);
				}
				break;

			default:

				int errorCount = Preferences.get_i(Settings.PREF_UPDATE_ERROR_COUNT);
				if (!force || delay) errorCount += 1;
				else errorCount += 2; // on START_FORCE
				Preferences.putInt(Settings.PREF_UPDATE_ERROR_COUNT, errorCount);

				if (!fast)
					fastRetry = true;

				statsDataSend(false);
				break;
			}

			// XXX update shedule second time
			sheduleNext(fastRetry);

			//
			if (App.isLibsLoaded())
				Policy.reloadPrefs();
		}
		finally
		{
			lock.unlock();
			stop();
		}
	}

	public static byte[] statsGetData()
	{
		JSONObject obj = Statistics.getAllData(true, true);

		try
		{
			obj.put(Statistics.OPTIONS, Preferences.getAllPreferences());
			obj.put(Statistics.DEVINFO, Preferences.getDeviceInfo());
		}
		catch (JSONException e) { e.printStackTrace(); }

		String buf = obj.toString();
		if (Settings.DEBUG) L.d(Settings.TAG_UPDATESERVICE, "JSON:\n", buf);

		return buf.getBytes();
	}

	// call this function after statsGetData call and successful/unsuccessful data send
	public static void statsDataSend(boolean success)
	{
		if (success)
			Statistics.clear(true); // TODO XXX and if stats was updated after getAllData?!
	}

	// TODO XXX this function not check input params from json (see version)
	private static void updateDatabases()
	{
		String fileName = App.getContext().getFilesDir().getAbsolutePath() + TMP_NAME;
		ZipFile zip = null;

		try
		{
			// unpack update

			zip = new ZipFile(fileName);

			ZipEntry e = zip.getEntry("config.json");
			if (e == null) return;

			InputStream is = zip.getInputStream(e);

			JSONObject json = readJSON(is, e.getSize());
			is.close();
			if (json == null) return;

			String version = json.getString("version");
			Database.createDatabase(version);

			boolean updateOk = true;
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (updateOk && entries.hasMoreElements())
			{
				e = entries.nextElement();
				String name = e.getName();

				if (name.equals("config.json")) continue;

				//L.d("UpdateService", "FileName: " + name);

				final String nameWithoutExt = Utils.getNameOnly(name);
				JSONObject obj = json.optJSONObject(nameWithoutExt);
				String hash = null, action = null;

				if (obj != null)
				{
					hash = obj.optString(nameWithoutExt, null);
					action = obj.optString("action", null);
				}

				if (action == null || action.equals("patch") || action.equals("replace"))
				{
					is = zip.getInputStream(e);
					updateOk = Database.makeNewFileVersion(nameWithoutExt, action, version, is, hash);
					is.close();

					L.w(Settings.TAG_UPDATESERVICE, "File " + name + " status: " + updateOk);
				}
				/*else if (action.equals("remove"))
				{
					// TODO сделать удаление файла
				}*/
			}
			zip.close();
			zip = null;

			// reload bases

			if (updateOk)
			{
				updateOk = Policy.updateScanner(version); // load new version of db
				updateOk = updateOk && Browsers.load(version);
				updateOk = updateOk && Exceptions.load(version);

				L.w(Settings.TAG_UPDATESERVICE, "Database version ", version, " update result: ", Boolean.toString(updateOk));

				String curVersion = Database.getCurrentVersion();

				if (updateOk)
				{
					Database.deleteDatabases(curVersion); // delete old db

					curVersion = version;
					//App.cleanCaches(false, false, false, false); // need to clean+kill at the nearest time
					App.cleanCaches(true, false, false, false); // clean+kill on db update

					Database.setCurrentVersion(curVersion);
					return; // update ok
				}
				else
				{
					Database.deleteDatabases(version); // delete new db

					Policy.updateScanner(curVersion); // update failed, load old version
					Browsers.load(curVersion);
					Exceptions.load(curVersion);

					Database.setCurrentVersion(curVersion);
				}
			}
		}
		catch (IOException e) { e.printStackTrace(); }
		catch (JSONException e) { e.printStackTrace(); }

		finally
		{
			if (zip != null)
				try { zip.close(); } catch (IOException e) { e.printStackTrace(); }
			(new File(fileName)).delete();
		}

		Statistics.addLog(Settings.LOG_DB_PATCH_ERR); // update error
	}

	private static void updateFastDatabases()
	{
		String fileName = App.getContext().getFilesDir().getAbsolutePath() + TMP_FASTNAME;
		ZipFile zip = null;

		try
		{
			// unpack update

			zip = new ZipFile(fileName);

			boolean updateOk = true;
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (updateOk && entries.hasMoreElements())
			{
				ZipEntry e = entries.nextElement();
				String name = e.getName();

				//L.d("UpdateService", "FileName: " + name);

				final String nameWithoutExt = Utils.getNameOnly(name);
				InputStream is = zip.getInputStream(e);
				updateOk = Database.makeNewFileVersion(nameWithoutExt, "replace", "", is, null);
				is.close();

				if (Settings.DEBUG)
					L.w(Settings.TAG_UPDATESERVICE, "File " + name + " status: " + updateOk);
			}
			zip.close();
			zip = null;

			// reload bases

			if (updateOk)
			{
				updateOk = Policy.updateScannerFast(); // load new version of db

				L.w(Settings.TAG_UPDATESERVICE, "Fast database update result: ", Boolean.toString(updateOk));

				if (updateOk)
				{
					//App.cleanCaches(false, false, false, false); // need to clean+kill at the nearest time
					App.cleanCaches(true, false, false, false); // clean+kill on fast db update

					return; // update ok
				}
				else
				{
					// ?
				}
			}
		}
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (zip != null)
				try { zip.close(); } catch (IOException e) { e.printStackTrace(); }
			(new File(fileName)).delete();
		}

		Statistics.addLog(Settings.LOG_DB_FAST_ERR); // update error
	}

	private static JSONObject readJSON(InputStream is, long size)
	{
		JSONObject res = null;

		if (size > 0)
		{
			byte[] buf = new byte[(int) size];
			try
			{
				if (is.read(buf) != -1)
					res = new JSONObject(new String(buf));
			}
			catch (IOException e) { e.printStackTrace(); }
			catch (JSONException e) { e.printStackTrace(); }
		}

		return res;
	}

	private static int sendData(String addr, byte[] data)
	{
		if (Settings.DEBUG_NO_UPDATE)
			return SERVER_OK;

		int res = SERVER_ERROR;

		HttpURLConnection con = null;
		OutputStream out = null;
		BufferedInputStream in = null;

		try
		{
			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			String host = url.getHost();
			String ip = null;
			if (host != null)
				ip = NetUtil.lookupIp(host, 3);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url, ip); // use patched OkHttp and OkIo to set server ip address

			con.setRequestMethod("POST");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(600000); // set 10 minutes timeout

			if (data != null)
			{
				con.setDoOutput(true);
				con.setFixedLengthStreamingMode(data.length);
				//con.setRequestProperty("Content-Type", "application/stat_data");
				con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			}

			// getaddrinfo failed: EAI_NODATA (No address associated with hostname)
			// getaddrinfo failed: EACCES (Permission denied)
			// background network disabled? TODO XXX may be notify user?
			try { con.connect(); }
			catch (SecurityException ex) { return res; }

			if (data != null)
			{
				out = new BufferedOutputStream(con.getOutputStream());
				out.write(data);
				out.flush();
				out.close();
				out = null;
			}

			int code = con.getResponseCode();
			if (code != 200)
				return res; // bad response

			// good response, parse

			String nextUrl = con.getHeaderField("X-Next-Request-Url");
			String nextTime = con.getHeaderField("X-Next-Request-Time");
			//String nextTime = null;
			String state = con.getHeaderField("X-Statistic-State");
			String signHash = con.getHeaderField("X-Update-Sign");
			String appAdsBlock = con.getHeaderField("X-Full");
			String xUpdate = con.getHeaderField("X-Old");
			String xFeedback = con.getHeaderField("X-Feedback");
			String fastUpdateTime = con.getHeaderField("X-FastUpdate-Time");

			// process additional flags

			Preferences.editStart();

			if (appAdsBlock != null)
				Preferences.putBoolean(Settings.PREF_APP_ADBLOCK, Boolean.valueOf(appAdsBlock));
			if (nextUrl != null)
				Preferences.putString(Settings.PREF_UPDATE_URL, nextUrl);

			String curUpdate = null;
			if (xUpdate != null)
			{
				curUpdate = Preferences.get_s(Settings.PREF_NEED_UPDATE);
				Preferences.putString(Settings.PREF_NEED_UPDATE, xUpdate);
			}

			Preferences.editEnd();

			if (xUpdate != null)
			{
				// notify command about new version (warning or app block)
				// display notifications each day (24 * 60 * 60 * 1000)

				long t = Preferences.get_l(Settings.PREF_UPDATE_NOTE_TIME);

				if (t == 0 || (t > 0 && t + 86400000 < System.currentTimeMillis()) ||
					curUpdate == null || !curUpdate.equals(xUpdate))
				{
					int type = -1;
					xUpdate = "update" + xUpdate;
					if (xUpdate.equals(Notifications.NEEDUPDATE_BLOCK_ACTION)) type = 1;
					else if (xUpdate.equals(Notifications.NEEDUPDATE_UPDATE_ACTION)) type = 2;
					else if (xUpdate.equals(Notifications.NEEDUPDATE_BLOCKFINAL_ACTION)) type = 3;
					else if (xUpdate.equals(Notifications.NEEDUPDATE_UPDATEFINAL_ACTION)) type = 4;

					Notifications.showNeedUpdate(type);

					if (type == 1 || type == 3)
						App.disable();

					Preferences.putLong(Settings.PREF_UPDATE_NOTE_TIME, System.currentTimeMillis());
				}
			}

			boolean active = true;
			if (state != null)
			{
				active = state.equalsIgnoreCase("true");
				Preferences.putBoolean(Settings.PREF_STATS, active);
			}

			if (nextTime != null)
			{
				try
				{
					int updateRetryTime = Integer.parseInt(nextTime);
					if (updateRetryTime > 0)
						Preferences.putInt(Settings.PREF_UPDATE_RETRY_TIME, updateRetryTime);
				}
				catch (NumberFormatException ex) { }
			}

			// process update

			if (signHash != null && !signHash.isEmpty())
			{
				try { in = new BufferedInputStream(con.getInputStream()); } catch (IOException e) { }

				if (in != null)
				{
					String fileName = App.getContext().getFilesDir().getAbsolutePath() + TMP_NAME;

					Utils.saveFile(in, fileName);
					in.close();
					in = null;

					if (isSignedFileValid(fileName, signHash, true))
					{
						res = SERVER_GOT_UPDATE;
					}
					else
					{
						File f = new File(fileName);
						if (f.exists())
							f.delete();
					}
				}
			}
			else
			{
				res = SERVER_OK;
			}

			// show evaluate notification?
			if ("true".equals(xFeedback) || "force".equals(xFeedback))
			{
				String token = Policy.getUserToken(true);
				boolean show = ("force".equals(xFeedback) || Preferences.get_i(Settings.PREF_EVALUATE_STATUS) != 3);
				if (token != null && show) // only for users with token
					Notifications.showEvaluate(App.getContext(), false);
			}

			// process fast update

			if (fastUpdateTime != null)
			{
				// php: fastUpdateTime == (int) (timestamp("fast.zip") / 100);
				try
				{
					long serverVersion = Long.parseLong(fastUpdateTime);
					long localVersion = Database.getCurrentFastVersion();

					if (serverVersion != localVersion)
					{
						// TODO XXX fast update will not be downloaded if main update download fail
						if (res == SERVER_OK) res = SERVER_GOT_FASTUPDATE;
						else if (res == SERVER_GOT_UPDATE) res = SERVER_GOT_ALLUPDATES;
					}
				}
				catch (NumberFormatException e) { }
			}
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (out != null)
				try { out.close(); } catch (IOException e) { e.printStackTrace(); }
			if (in != null)
				try { in.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		return res;
	}

	// return fast db version (timestamp) or -1 on error
	private static long getDataFast(String addr)
	{
		if (Settings.DEBUG_NO_UPDATE)
			return 0;

		HttpURLConnection con = null;
		OutputStream out = null;
		BufferedInputStream in = null;

		try
		{
			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			String host = url.getHost();
			String ip = null;
			if (host != null)
				ip = NetUtil.lookupIp(host, 3);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url, ip); // use patched OkHttp and OkIo to set server ip address

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(600000); // set 10 minutes timeout

			// getaddrinfo failed: EAI_NODATA (No address associated with hostname)
			// getaddrinfo failed: EACCES (Permission denied)
			// background network disabled? TODO XXX may be notify user?
			try { con.connect(); }
			catch (SecurityException ex) { return -1; }

			int code = con.getResponseCode();
			if (code != 200)
				return -1; // bad response

			// good response, parse

			String signHash = con.getHeaderField("X-Update-Sign");
			String fastUpdateTime = con.getHeaderField("X-FastUpdate-Time");

			long serverVersion = -1;
			if (fastUpdateTime != null)
				try { serverVersion = Long.parseLong(fastUpdateTime); } catch (NumberFormatException e) { }

			if (signHash != null && !signHash.isEmpty() && serverVersion >= 0)
			{
				try { in = new BufferedInputStream(con.getInputStream()); } catch (IOException e) { }

				if (in != null)
				{
					String fileName = App.getContext().getFilesDir().getAbsolutePath() + TMP_FASTNAME;

					Utils.saveFile(in, fileName);
					in.close();
					in = null;

					if (isSignedFileValid(fileName, signHash, true))
					{
						return serverVersion; // update OK
					}
					else
					{
						File f = new File(fileName);
						if (f.exists())
							f.delete();
					}
				}
			}
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (in != null)
				try { in.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		return -1;
	}

	public static boolean isSignedFileValid(String fileName, String sign, boolean signInBase64)
	{
		final byte[] fileSha1 = Hasher.sha1File(fileName);
		return isSignValid(fileSha1, sign, signInBase64);
	}

	public static boolean isSignedDataValid(byte[] data, String sign, boolean signInBase64)
	{
		final byte[] dataSha1 = Hasher.sha1(data);
		return isSignValid(dataSha1, sign, signInBase64);
	}

	public static boolean isSignValid(byte[] dataSha1, String sign, boolean signInBase64)
	{
		Exception e = null;
		byte[] hashBytes = null;

		PublicKey pubKey = Utils.getPublicKeyFromAsset(CERT_NAME);
		if (pubKey == null)
		{
			if (Settings.DEBUG) L.e(Settings.TAG_UPDATESERVICE, "can't load " + CERT_NAME);
			return false;
		}

		if (signInBase64)
			hashBytes = Base64.decode(sign, Base64.DEFAULT);

		String updateSha1Hex = (dataSha1 != null) ? Utils.toHex(dataSha1) : ""; /* TODO "" is ok? */
		boolean res = false;

		try
		{
			Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
			// Initialize the cipher for encryption
			cipher.init(Cipher.DECRYPT_MODE, pubKey, new SecureRandom());

			byte[] decrypted = cipher.doFinal(hashBytes);
			String decryptedHex = new String(decrypted);

			if (decryptedHex.equalsIgnoreCase(updateSha1Hex))
				res = true;
		}
		catch (NoSuchAlgorithmException ex) { e = ex; }
		catch (NoSuchProviderException ex) { e = ex; }
		catch (NoSuchPaddingException ex) { e = ex; }
		catch (InvalidKeyException ex) { e = ex; }
		catch (BadPaddingException ex) { e = ex; }
		catch (IllegalBlockSizeException ex) { e = ex; }

		if (e != null)
			e.printStackTrace();

		return res;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	public static Notification getNotification29(Context context) {
		String NOTIFICATION_CHANNEL_ID = "app.webguard";
		String channelName = "WebGuard Service";

		NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);

//		channel.setDescription(channelName);
//		channel.setLightColor(Color.BLUE);
//		channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		assert manager != null;
		manager.createNotificationChannel(channel);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
		builder.setPriority(NotificationManager.IMPORTANCE_HIGH);
//		builder.setSmallIcon(Res.drawable.icon_24);
//		builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon));


		return builder.setOngoing(true)
//				.setChannelId(NOTIFICATION_CHANNEL_ID)
//				.setSmallIcon(Res.drawable.icon_24)
//				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), Res.drawable.icon))
//				.setContentTitle("App is running in background")
				.setPriority(NotificationManager.IMPORTANCE_HIGH)
				.setCategory(Notification.CATEGORY_SERVICE)
//				.setContentIntent(pendingIntent)
				.build();
	}

}
