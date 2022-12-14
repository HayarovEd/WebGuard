package app.info;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.SparseArray;
import app.App;
import app.security.Exceptions;
import app.common.Utils;
import app.internal.Preferences;
import app.internal.Settings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

// !!! don't use here any code from native libs

public class Statistics
{
	public static final String OPTIONS = "options";
	public static final String DEVINFO = "device_info";

	public static final String EXCEPTIONS = "exceptions";
	public static final String URL_STATS  = "url_stats";
	public static final String APPS		  = "apps";
	public static final String LOGS		  = "logs";
	public static final String STATS	  = "stats";
	public static final String APPS_INET  = "apps_inet";

	public static final int FILE_TYPE_ZIP = 1;
	public static final int FILE_TYPE_ARC = 2;
	public static final int FILE_TYPE_EXE = 3;

	// stores all found urls, the index is uid of app
	private static final SparseArray<AppUrlList>  urlStats	 = new SparseArray<AppUrlList>();
	private static final HashSet<ExceptionInfo>   exceptions = new HashSet<ExceptionInfo>();
	private static final HashSet<AppInfo>		  apps		 = new HashSet<AppInfo>();
	private static final HashMap<String, Integer> appsInet	 = new HashMap<String, Integer>();
	private static final ArrayList<String>		  logs		 = new ArrayList<String>();

	private static final Object lock = new Object();
	private static String statsFileName = null;
	private static String logsFileName = null;
	//private static boolean isActive = true;
	public static int versionCode = 0;

	static
	{
		Context context = App.getContext();
		statsFileName = context.getFilesDir().getAbsolutePath() + "/stats.json";
		logsFileName = context.getFilesDir().getAbsolutePath() + "/logs.json";
	}

	public static void init()
	{
		synchronized (lock)
		{
			Context context = App.getContext();

			PackageManager pm = context.getPackageManager();
			try
			{
				final PackageInfo packageInfo = pm.getPackageInfo(App.packageName(), 0);
				if (packageInfo != null)
					versionCode = packageInfo.versionCode;
			}
			catch (PackageManager.NameNotFoundException e) { e.printStackTrace(); }

			//isActive = Preferences.get(Settings.PREF_STATS);

			clear(false);
			load();
		}
	}

	public static void clear(boolean save)
	{
		synchronized (lock)
		{
			urlStats.clear();
			exceptions.clear();
			apps.clear();
			appsInet.clear();
			logs.clear();

			if (save)
				save(true);
		}
	}

	private static void load()
	{
		synchronized (lock)
		{
			try
			{
				// main data

				final String str = Utils.getFileContents(statsFileName);
				if (str != null)
				{
					try
					{
						JSONObject obj = new JSONObject(str);
						JSONArray arr = obj.optJSONArray(URL_STATS);
						if (arr != null)
						{
							for (int i = 0; i < arr.length(); i++)
							{
								AppUrlList urlList = new AppUrlList(arr.getJSONObject(i));
								urlStats.put(urlList.getUid(), urlList);
							}
						}

						arr = obj.optJSONArray(EXCEPTIONS);
						if (arr != null)
						{
							for (int i = 0; i < arr.length(); i++)
							{
								ExceptionInfo info = new ExceptionInfo(arr.getJSONObject(i));
								exceptions.add(info);
							}
						}

						arr = obj.optJSONArray(APPS);
						if (arr != null)
						{
							for (int i = 0; i < arr.length(); i++)
							{
								AppInfo info = new AppInfo(arr.getJSONObject(i));
								apps.add(info);
							}
						}
					}
					catch (JSONException e) { e.printStackTrace(); }
				}

				// logs

				final String logs = Utils.getFileContents(logsFileName);
				if (logs != null)
				{
					try
					{
						JSONArray arr = new JSONArray(logs);
						if (arr.length() > 0)
						{
							Statistics.logs.clear();
							for (int i = 0; i < arr.length(); i++)
								Statistics.logs.add(arr.getString(i));
						}
					}
					catch (JSONException e) { e.printStackTrace(); }

				}
			}
			catch (IOException e) { e.printStackTrace(); }
		}
	}

	// TODO XXX this function called by every data update. VERY SLOW!
	private static void save(boolean saveLogs)
	{
		synchronized (lock)
		{
			// main data

			byte[] data = getAllData(false, false).toString().getBytes();
			try { Utils.saveFile(data, statsFileName); } catch (IOException e) { e.printStackTrace(); }

			if (!saveLogs)
				return;

			// logs

			data = getLogs().toString().getBytes();
			try { Utils.saveFile(data, logsFileName); } catch (IOException e) { e.printStackTrace(); }
		}
	}

/*
	public static boolean hasNewData()
	{
		return (isActive && urlStats.size() > 0);
	}
*/

	public static JSONArray getLogs()
	{
		JSONArray arr;

		synchronized (lock)
		{
			arr = new JSONArray();
			for (String log : logs)
				arr.put(log);
		}

		return arr;
	}

	private static JSONArray getAppUrls()
	{
		JSONArray arr;

		synchronized (lock)
		{
			arr = new JSONArray();
			int size = urlStats.size();

			for (int i = 0; i < size; i++)
			{
				final int key = urlStats.keyAt(i);
				arr.put(urlStats.get(key).getJSON());
			}
		}

		return arr;
	}

	public static int getAppsCount()
	{
		synchronized (lock)
		{
			return apps.size();
		}
	}

	public static JSONObject getAllData(boolean addLogs, boolean addWorkStats)
	{
		JSONObject obj;

		synchronized (lock)
		{
			obj = new JSONObject();

			// main data

			if (urlStats.size() > 0)
			{
				try { obj.put(URL_STATS, getAppUrls()); }
				catch (JSONException e) { e.printStackTrace(); }
			}

			if (exceptions.size() > 0)
			{
				try
				{
					JSONArray arr = new JSONArray();
					for (ExceptionInfo info : exceptions)
						arr.put(info.getJSON());
					obj.put(EXCEPTIONS, arr);
				}
				catch (JSONException e) { e.printStackTrace(); }
			}

			if (apps.size() > 0)
			{
				//L.w(Settings.TAG_STATISTICS, "Have ", Integer.toString(apps.size()), " apps in Statistics");
				try
				{
					JSONArray arr = new JSONArray();
					for (AppInfo info : apps)
						arr.put(info.getJSON());
					obj.put(APPS, arr);
				}
				catch (JSONException e) { e.printStackTrace(); }
			}

			// logs

			if (addLogs && logs.size() > 0)
			{
				try
				{
					JSONArray arr = new JSONArray();
					for (String log : logs)
						arr.put(log);
					obj.put(LOGS, arr);
				}
				catch (JSONException e) { e.printStackTrace(); }
			}

			// app working stats

			if (addWorkStats)
			{
				try { obj.put(STATS, getStats()); }
				catch (JSONException e) { e.printStackTrace(); }

				try
				{
					JSONArray arr = new JSONArray();
					for (Map.Entry entry : appsInet.entrySet())
					{
						String pkgName = (String) entry.getKey();
						Integer count = (Integer) entry.getValue();

						JSONObject o = new JSONObject();
						o.put(pkgName, count);
						arr.put(o);
					}
					obj.put(APPS_INET, arr);
				}
				catch (JSONException e) { e.printStackTrace(); }
			}
		}

		return obj;
	}

	// TODO XXX rewrite uid->pkgnames conversion, slow!
	public static void addUrl(int uid, boolean isBrowser, String url, String referrer,
								int type, int[] records)
	{
		// type can be -1 - then save it anyway
		if (type >= 0 && Exceptions.exceptFromStats(url))
			return;

		synchronized (lock)
		{
			AppUrlList list = urlStats.get(uid);
			if (list == null)
			{
				list = new AppUrlList(uid, isBrowser);
				urlStats.put(uid, list);
			}

			// TODO XXX hasAppInfo->loadAppInfo that load info from disk to cache, maybe slow
			final String[] packageNames = list.getPackageNames();
			if (!AppManager.hasAppInfo(packageNames))
			{
				// TODO XXX checkApp->updateAppList->new Thread on each unknown App and what if we can't get PackageManager? :(
				AppManager.checkApp(list.getPackageNames()); // collect info about application
			}

			list.addUrl(url, referrer, type, records);
		}
	}

	public static void addApp(AppInfo app)
	{
		synchronized (lock)
		{
			apps.add(app);
			save(false);
		}
	}

	public static void addApps(Collection<AppInfo> apps)
	{
		synchronized (lock)
		{
			//L.w(Settings.TAG_STATISTICS, "Adding ", Integer.toString(apps.size()), " apps");
			Statistics.apps.addAll(apps);
			save(false);
		}
	}

	public static void addException(String text)
	{
		synchronized (lock)
		{
			exceptions.add(new ExceptionInfo(System.currentTimeMillis(), text));
			save(false);
		}
	}

	// TODO XXX rewrite to uid
	// TODO XXX this counters not saved by now (for perfomance). may be add save by other events?
	public static void addAppInet(String pkgName)
	{
		if (pkgName == null)
			return;

		synchronized (lock)
		{
			Integer count = appsInet.get(pkgName);
			if (count == null)
				count = Integer.valueOf(0);
			appsInet.put(pkgName, count + 1);
		}
	}

	/*
	 * TODO XXX this is VERY SLOW!!!
	 * make by default addLogFast or create queue with separate thread
	 */
	public static void addLog(String text)
	{
		if (Settings.EVENTS_LOG)
		{
			synchronized (lock)
			{
				logs.add(text);

				byte[] data = getLogs().toString().getBytes();
				try { Utils.saveFile(data, logsFileName); } catch (IOException e) { e.printStackTrace(); }
			}
		}
	}

	/*
	 * can't wait for statistics up, file write (or other) or will be killed
	 * I/ActivityManager( 1013): Killing 3362:app.webguard/u0a243 (adj 15): empty #18
	 */
	public static void addLogFast(final String text)
	{
		if (Settings.EVENTS_LOG)
		{
			new Thread(new Runnable()
			{
				@Override
				public void run() { addLog(text); }
			}).start();
		}
	}

	//

	/*
	 * netClientsCounts[2], netinfo[4], policy[6] - see FilterVpnService
	 *
	 * TODO XXX move all to json
	 */
	public static void updateStats(int[] netClientsCounts, int[] netinfo, long[] policy)
	{
		synchronized (lock)
		{
			Preferences.editStart();

			if (netClientsCounts != null)
			{
				try
				{
					int tcp = Preferences.get_i(Settings.STATS_NET_TCP_MAX);
					int udp = Preferences.get_i(Settings.STATS_NET_UDP_MAX);

					if (netClientsCounts[0] > tcp)
						Preferences.putInt(Settings.STATS_NET_TCP_MAX, netClientsCounts[0]);
					if (netClientsCounts[1] > udp)
						Preferences.putInt(Settings.STATS_NET_UDP_MAX, netClientsCounts[1]);
				}
				catch (Exception e) { e.printStackTrace(); }
			}

			if (netinfo != null)
			{
				try
				{
					int netlinkErrCount = Preferences.get_i(Settings.STATS_NET_NL_ERR_MAX);
					int netlinkNotFoundCount = Preferences.get_i(Settings.STATS_NET_NL_NF_MAX);
					int procRetryCount = Preferences.get_i(Settings.STATS_NET_PROC_RETRY_MAX);
					int procNotFoundCount = Preferences.get_i(Settings.STATS_NET_PROC_NF_MAX);

					if (netinfo[0] > netlinkErrCount)
						Preferences.putInt(Settings.STATS_NET_NL_ERR_MAX, netinfo[0]);
					if (netinfo[1] > netlinkNotFoundCount)
						Preferences.putInt(Settings.STATS_NET_NL_NF_MAX, netinfo[1]);
					if (netinfo[2] > procRetryCount)
						Preferences.putInt(Settings.STATS_NET_PROC_RETRY_MAX, netinfo[2]);
					if (netinfo[3] > procNotFoundCount)
						Preferences.putInt(Settings.STATS_NET_PROC_NF_MAX, netinfo[3]);

				}
				catch (Exception e) { e.printStackTrace(); }
			}

			if (policy != null)
			{
				try
				{
					int blockedAdsIpCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_ADS_IP);
					int blockedAdsUrlCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_ADS_URL);
					int blockedApkCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_APK);
					int blockedMalwareSiteCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_MALWARE);
					int blockedPaidSiteCount = Preferences.get_i(Settings.STATS_POLICY_BLOCK_PAID);
					long proxyCompressionSave = Preferences.get_l(Settings.STATS_COMPRESSION_SAVE);

					Preferences.putInt(Settings.STATS_POLICY_BLOCK_ADS_IP, blockedAdsIpCount + (int) policy[0]);
					Preferences.putInt(Settings.STATS_POLICY_BLOCK_ADS_URL, blockedAdsUrlCount + (int) policy[1]);
					Preferences.putInt(Settings.STATS_POLICY_BLOCK_APK, blockedApkCount + (int) policy[2]);
					Preferences.putInt(Settings.STATS_POLICY_BLOCK_MALWARE, blockedMalwareSiteCount + (int) policy[3]);
					Preferences.putInt(Settings.STATS_POLICY_BLOCK_PAID, blockedPaidSiteCount + (int) policy[4]);
					Preferences.putLong(Settings.STATS_COMPRESSION_SAVE, proxyCompressionSave + policy[5]);
				}
				catch (Exception e) { e.printStackTrace(); }
			}

			Preferences.editEnd();
		}
	}

	// TODO XXX move to getAllData
	public static JSONObject getStats()
	{
		JSONObject obj;

		synchronized (lock)
		{
			obj = new JSONObject();

			try
			{
				obj.put(Settings.STATS_NET_TCP_MAX, Preferences.get_i(Settings.STATS_NET_TCP_MAX));
				obj.put(Settings.STATS_NET_UDP_MAX, Preferences.get_i(Settings.STATS_NET_UDP_MAX));

				obj.put(Settings.STATS_NET_NL_ERR_MAX, Preferences.get_i(Settings.STATS_NET_NL_ERR_MAX));
				obj.put(Settings.STATS_NET_NL_NF_MAX, Preferences.get_i(Settings.STATS_NET_NL_NF_MAX));
				obj.put(Settings.STATS_NET_PROC_RETRY_MAX, Preferences.get_i(Settings.STATS_NET_PROC_RETRY_MAX));
				obj.put(Settings.STATS_NET_PROC_NF_MAX, Preferences.get_i(Settings.STATS_NET_PROC_NF_MAX));

				obj.put(Settings.STATS_POLICY_BLOCK_ADS_IP, Preferences.get_i(Settings.STATS_POLICY_BLOCK_ADS_IP));
				obj.put(Settings.STATS_POLICY_BLOCK_ADS_URL, Preferences.get_i(Settings.STATS_POLICY_BLOCK_ADS_URL));
				obj.put(Settings.STATS_POLICY_BLOCK_APK, Preferences.get_i(Settings.STATS_POLICY_BLOCK_APK));
				obj.put(Settings.STATS_POLICY_BLOCK_MALWARE, Preferences.get_i(Settings.STATS_POLICY_BLOCK_MALWARE));
				obj.put(Settings.STATS_POLICY_BLOCK_PAID, Preferences.get_i(Settings.STATS_POLICY_BLOCK_PAID));

				obj.put(Settings.STATS_COMPRESSION_SAVE, Preferences.get_l(Settings.STATS_COMPRESSION_SAVE));
			}
			catch (JSONException e) { e.printStackTrace(); }
		}

		return obj;
	}

	//

	static class ExceptionInfo
	{
		long dateTime = 0;
		String text = null;

		public ExceptionInfo(long dateTime, String text)
		{
			this.dateTime = dateTime;
			this.text = text;
		}

		public ExceptionInfo(String json)
		{
			try
			{
				JSONObject obj = new JSONObject(json);
				dateTime = obj.optLong("date_time");
				text = obj.optString("text");
			}
			catch (JSONException e) { e.printStackTrace(); }
		}

		public ExceptionInfo(JSONObject json)
		{
			dateTime = json.optLong("date_time");
			text = json.optString("text");
		}

		@Override
		public String toString()
		{
			return getJSON().toString();
		}

		public JSONObject getJSON()
		{
			JSONObject obj = new JSONObject();
			try
			{
				obj.put("date_time", Utils.formatDateTime(dateTime, true, true, true));
				obj.put("text", text);

				return obj;
			}
			catch (JSONException e) { e.printStackTrace(); }

			return null;
		}

		@Override
		public int hashCode()
		{
			if (text == null)
				return 0;
			else
				return text.hashCode();
		}

		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof ExceptionInfo)
			{
				ExceptionInfo info = (ExceptionInfo) obj;
				return (info.hashCode() == this.hashCode());
			}

			return false;
		}
	}

	//

	private static class UrlInfo
	{
		String url = null;
		String referer = null;
		int type = 0;
		int[] records = null;
		String[] packageNames = null;
		boolean isBrowser = false;

		public UrlInfo(String[] packageNames, boolean isBrowser, String url, String referer, int[] records)
		{
			this.url = url;
			this.referer = referer;
			this.records = records;
			this.packageNames = packageNames;
			this.isBrowser = isBrowser;
		}

		public UrlInfo(String[] packageNames, boolean isBrowser, String url, String referer, int type)
		{
			this(packageNames, isBrowser, url, referer, null);
			this.type = type;
		}

		public UrlInfo(String[] packageNames, boolean isBrowser, String url, String referer, int[] records, int type)
		{
			this(packageNames, isBrowser, url, referer, records);
			this.type = type;
		}

		public UrlInfo(String json)
		{
			try
			{
				JSONObject obj = new JSONObject(json);
				url = obj.optString("url");
				referer = obj.optString("referer", null);
				type = obj.optInt("type", -1);

				JSONArray arr = obj.optJSONArray("records");
				if (arr != null && arr.length() > 0)
				{
					records = new int[arr.length()];
					for (int i = 0; i < arr.length(); i++)
						records[i] = arr.getInt(i);
				}
			}
			catch (JSONException e) { e.printStackTrace(); }

		}

		public JSONObject getJSON()
		{
			JSONObject obj = new JSONObject();
			try
			{
				obj.put("url", url);
				obj.put("referer", referer);
				obj.put("type", type);

				JSONArray arr = new JSONArray();
				if (records != null)
				{
					for (int i : records)
						arr.put(i);
				}
				obj.put("records", arr);

				return obj;
			}
			catch (JSONException e) { e.printStackTrace(); }

			return null;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof UrlInfo)
			{
				UrlInfo info = (UrlInfo) obj;
				return (info.hashCode() == this.hashCode());
			}

			return false;
		}

		@Override
		public int hashCode()
		{
			return (url + referer).hashCode();
		}

		@Override
		public String toString()
		{
			return getJSON().toString();
		}
	}
}
