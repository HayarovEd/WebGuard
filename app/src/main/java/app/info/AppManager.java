package app.info;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import app.App;
import app.internal.Preferences;
import app.UpdaterService;
import app.common.Utils;
import app.internal.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class AppManager
{
	private static final HashMap<String, AppInfo> cache = new HashMap<String, AppInfo>();
	private static int ownVersionCode = 0;
	private static Context context = null;
	private static volatile String filesDir = null;
	private static volatile boolean allPackagesHashing = false;

	private static volatile PackageManager packageManager = null;
	private static volatile boolean packageManagerInited = false;

	public static void init(Context context, String packageName)
	{
		AppManager.context = context;

		filesDir = context.getFilesDir().getAbsolutePath() + "/app_data/";
		File dir = new File(filesDir);
		if (!dir.exists())
		{
			dir.mkdirs();
			if (!dir.exists())
				filesDir = null;
		}

		packageManager = context.getPackageManager();
		packageManagerInited = true;

		try
		{
			final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
			if (packageInfo != null)
				ownVersionCode = packageInfo.versionCode;
		}
		catch (PackageManager.NameNotFoundException e) { e.printStackTrace(); }
	}

	// save all info from cache to disk
	public static void saveData()
	{
		//L.d(Settings.TAG_APPMANAGER, "saveData() start");
		if (filesDir == null)
			return;

		synchronized (cache)
		{
			final Set<String> keys = cache.keySet();
			for (String key : keys)
			{
				AppInfo info = cache.get(key);
				saveData(info, key);
			}
		}
		//L.d(Settings.TAG_APPMANAGER, "saveData() finish");
	}

	// save app info to disk
	public static void saveData(final AppInfo info, final String key)
	{
		//L.d(Settings.TAG_APPMANAGER, "saveData() start");
		if (filesDir == null)
			return;

		synchronized (cache)
		{
			try
			{
				Utils.saveFile(info.toString().getBytes(), filesDir + key);
			}
			catch (IOException e) { e.printStackTrace(); }
		}
		//L.d(Settings.TAG_APPMANAGER, "saveData() finish");
	}

/*
	public static AppInfo getAppInfo(String packageName)
	{
		final String hash = hashCode(packageName);
		synchronized (cache)
		{
			if (cache.containsKey(hash))
				return cache.get(hash);

			final AppInfo info = loadAppInfo(packageName);
			if (info != null)
			{
				cache.put(hash, info);
				return info;
			}
		}

		return null;
	}
*/

	public static boolean hasAppInfo(final String[] packageNames)
	{
		if (packageNames == null)
			return true;

		for (String pack : packageNames)
		{
			if (!hasAppInfo(pack))
				return false;
		}

		return true;
	}

	public static boolean hasAppInfo(final String packageName)
	{
		final String hash = hashCode(packageName);
		synchronized (cache)
		{
			if (cache.containsKey(hash))
				return true;

			final AppInfo info = loadAppInfo(packageName);
			if (info != null)
			{
				cache.put(hash, info); // TODO if too many apps use inet, cache will be grow
				return true;
			}
		}

		return false;
	}

	private static AppInfo loadAppInfo(String packageName)
	{
		if (filesDir == null)
			return null;

		final String hash = hashCode(packageName);
		String fileName = filesDir + hash;

		File f = new File(fileName);
		if (f.exists() && f.canRead())
		{
			byte[] buf = new byte[(int) f.length()];
			FileInputStream fis = null;
			try
			{
				fis = new FileInputStream(fileName);
				if (fis.read(buf) == -1)
					return null;
			}
			catch (IOException e) { e.printStackTrace(); }

			finally
			{
				if (fis != null)
					try { fis.close(); } catch (IOException e) { e.printStackTrace(); }
			}

			return (new AppInfo(new String(buf)));
		}

		return null;
	}

	public static void clearMemoryCache()
	{
		synchronized (cache)
		{
			cache.clear();
		}
	}

	public static int getOwnVersionCode()
	{
		return ownVersionCode;
	}

	// forcibly update information about package (packages)
	public static void checkApp(final String[] packageNames)
	{
		if (packageNames != null)
		{
			for (String pack : packageNames)
				checkApp(pack);
		}
	}

	public static void checkApp(final String packageName)
	{
		if (packageName == null || packageName.isEmpty())
			return;

		App.postToHandler(new Runnable()
		{
			@Override
			public void run()
			{
				updateAppList(packageName);
			}
		});
	}

	/*
	 * update info about package or all packages
	 * TODO XXX f*ck! VERY BAD synchronization. rewrite this and all class
	 */
	public static void updateAppList(final String packageName)
	{
		if (packageManagerInited && packageManager == null)
			return;

		if (packageName == null)
		{
			if (allPackagesHashing)
				return;

			allPackagesHashing = true; // TODO XXX ahaha
		}
		else
		{
			if (packageName.indexOf('.') < 0) // ???
				return;
		}

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.minimizeThreadPriority();

				// packageManager is initialized in another thread, so wait
				// TODO XXX if not initialized? this will be BIG number of threads
				while (!packageManagerInited)
					Utils.sleep(3000);
				if (packageManager == null)
				{
					allPackagesHashing = false;
					return;
				}

				if (packageName != null)
				{
					// update one app info

					final AppInfo appInfo = updateAppInfo(packageName);
					final String key = AppManager.hashCode(packageName);
					synchronized (cache)
					{
						cache.put(key, appInfo); // TODO if too many apps use inet, cache will be grow
					}
					appInfo.updateHashes(); // TODO XXX may be need hash before cache.put?
					Statistics.addApp(appInfo);
					saveData(appInfo, key);

					return;
				}

				// hash all apps

				// updateAppInfo
				// TODO XXX may get an empty list because the IPC buffer grew larger than than it's 1MB buffer size and PM died
				//			rewrite to getInstalledPackages(0) and GET_META_DATA for each pkg
				// http://stackoverflow.com/questions/3455781/packagemanager-getinstalledpackages-returns-empty-list/26172008#26172008
				final List<PackageInfo> list = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);
				for (PackageInfo info : list)
				{
					//L.d(Settings.TAG_APPMANAGER, "updateAppList() getinfo");
					final String key = AppManager.hashCode(info.packageName);
					synchronized (cache)
					{
						if (cache.containsKey(key))
							continue;

						final AppInfo appInfo = updateAppInfo(info.packageName);
						cache.put(key, appInfo);
						//Thread.yield();
					}
				}
				// updateHashes. делаем дополнительный массив, чтобы не было локов из из других потоков
				ArrayList<AppInfo> apps = new ArrayList<AppInfo>();
				synchronized (cache)
				{
					apps.addAll(cache.values());
				}
				for (AppInfo info : apps)
				{
					info.updateHashes(); // TODO XXX may be need hash before cache.put?
					//Thread.yield();
				}
				Statistics.addApps(apps);
				saveData(); // TODO XXX get data from cache

				boolean appsHashed = Preferences.get(Settings.PREF_APPS_HASHED);
				if (!appsHashed)
					Preferences.putBoolean(Settings.PREF_APPS_HASHED, true);

				// start updates to send apps data
				//int updateType = (!appsHashed) ? UpdaterService.START_FORCE : UpdaterService.START_DELAYED;
				//UpdaterService.startUpdate(updateType);
				if (!appsHashed)
					UpdaterService.startUpdate(UpdaterService.START_FORCE); // apps info

				clearMemoryCache();
				allPackagesHashing = false;
			}
		}).start();
	}

	private static AppInfo updateAppInfo(String packageName)
	{
		//L.d(Settings.TAG_APPMANAGER, "updateAppList() start");
		try
		{
			PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);

			String name = null;
			try { name = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString(); }
			catch (NullPointerException e) {}

			String verName = packageInfo.versionName;
			int verCode = packageInfo.versionCode;
			long updateTime = packageInfo.lastUpdateTime;
			long installTime = packageInfo.firstInstallTime;

			AppInfo info = new AppInfo(packageName, name, verName, verCode, installTime, updateTime);
			info.apkPath = packageInfo.applicationInfo.sourceDir;

			//L.d(Settings.TAG_APPMANAGER, "updateAppInfo() finish");
			return info;
		}
		catch (PackageManager.NameNotFoundException e)
		{
			// TODO android.process.acore and android.process.media
			// http://stackoverflow.com/questions/6597024/packetmanager-name-not-found-error

			//e.printStackTrace();
			return new AppInfo(packageName, null, null, 0, 0, 0);
		}

		//return null;
	}

	public static String hashCode(final String packageName)
	{
		return Integer.toHexString(packageName.hashCode());
	}
}
