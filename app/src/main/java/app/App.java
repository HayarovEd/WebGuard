package app;

import app.internal.InAppBilling;
import app.internal.Preferences;
import app.security.PolicyRules;
import app.common.LibNative;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.multidex.MultiDexApplication;

import app.netfilter.*;
import app.common.memdata.ByteBufferPool;
import app.netfilter.proxy.PacketPool;
import app.netfilter.proxy.BlockIPCache;
import app.common.NetUtil;
import app.info.AppManager;
import app.info.Statistics;
import app.internal.ProxyBase;
import app.security.UserAgents;
import app.security.Database;
import app.security.Exceptions;
import app.security.Policy;
import app.ui.Notifications;
import app.ui.NotifyActivity;
import app.ui.OptionsActivity;
import app.security.Browsers;
import app.common.InstallId;
import app.common.debug.L;
import app.common.Utils;
import app.common.debug.DebugUtils;
import app.internal.Settings;
import app.security.Firewall;
import java.util.Hashtable;

public class App extends MultiDexApplication implements IFilterVpnApplication {

	private static Context context = null;
	private static Handler handler = null;
	private static Policy policy = null;
	private static MyUserNotifier notifier = null;
	private static boolean libsLoaded = false;
	private static int myUid = 0;
	private static String installId = null;
	private static InAppBilling billing = null;
	private static boolean firstRun = false;
//	private static String sysLang = null;
	private static volatile Thread uiThread = null;

	private static boolean initedLogs	   = false;
	private static boolean initedOther	   = false;
	private static boolean initedProxyBase = false;
	private static boolean initedBilling   = false;

	private static final Object ccLock = new Object();

	// TODO XXX remove this variables! rewrite activate code (and vpnActivated)
	public static boolean startProcessed = false;
	public static boolean deviceBooted = false;
	// this variable blocks vpn start (see setupVpn) because we waiting user activation (+ rights)
	public static volatile boolean vpnActivated = true;
	private static AppUidManager mAppUidManager;

	private static boolean hack = false;

	public static void setHack(boolean h) { hack = h; }
	public static boolean isHack() { return hack; }


	@Override
	public void onCreate()
	{
		super.onCreate();

		//

		context = this;

		Preferences.init(this);

		Thread.currentThread().setName("Main Thread");
		uiThread = Looper.getMainLooper().getThread();

		ExceptionLogger.start(this);

		if (Settings.DEBUG_PROFILE_START)
			DebugUtils.switchTracing(true, true, true);

		if (handler == null)
			handler = new Handler();

		mAppUidManager = AppUidManager.getInstance(this);
		AppUidService.hashAllUid(this); // запускаем службу, которая формирует информацию об uid всех приложений (для определения packageName по uid для Android 7 и выше, тк доступ к /proc гугл запретил)

		initLibs();
		findMyUid();
		installId = (new InstallId(context)).getInstallIDStr();

		if (Preferences.get_l(Settings.PREF_FIRST_START_TIME) == 0)
		{
			Preferences.putLong(Settings.PREF_FIRST_START_TIME, System.currentTimeMillis());
			firstRun = true;
		}

		initLogs(firstRun);
		Preferences.load(context);
		NetUtil.init(context);

		// ok, minimal init passed

		if (!isLibsLoaded())
		{
			Preferences.putBoolean(Settings.PREF_ACTIVE, false);

			initedOther = true;
			initedProxyBase = true;
			initedBilling = true;
		}
		else
		{
//			refreshSysLang();
//			App.changeLanguage(false);

			LibNative.signalHandlerSet(1); // for tun threads interrupt (see threadSendSignal)

			int status = NetUtil.getStatus();
			boolean haveNetwork = (status != -1);

			if (Settings.DEBUG) L.w(Settings.TAG_APP, "NetUtil.getStatus() = " + status);

//			  if (libsLoaded)
//				  LibNative.netlinkWatchNet("app/webguard/NetworkStateChangeReceiver", "onNetLink");
			//int status = NetUtil.getStatus();

//			  test1();

			initOther(firstRun, haveNetwork);
			initProxyBase(firstRun, haveNetwork);
			initBilling(firstRun, haveNetwork);

			if (!Settings.DEBUG_PROFILE_START)
				DebugUtils.disableDebugInfoCollect(true);
		}

		if (firstRun) // TODO XXX may be force updates if restarted?!
			checkUpdates();
	}

	// init functions

	private static boolean initLibs()
	{
		if (Settings.DEBUG_NO_LIBS)
			return false;

		try
		{
			// try to load libraries
			System.loadLibrary("native");
			System.loadLibrary("bspatch");

			long libsVersion = LibNative.getLibsVersion();
			if (libsVersion == LibNative.LIBS_VERSION)
			{
				libsLoaded = true;
				return true;
			}
		}
		catch (UnsatisfiedLinkError e) { e.printStackTrace(); }
		catch (Exception e) { e.printStackTrace(); }

		return false;
	}

	private void initLogs(final boolean firstRun)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Statistics.init(); // TODO XXX and if we start write in another threads before init called?
				if (Settings.EVENTS_LOG)
				{
					if (Preferences.isActive())
						Statistics.addLog(Settings.LOG_RESTARTED + System.currentTimeMillis());
					else
						Statistics.addLog(Settings.LOG_STARTED + System.currentTimeMillis());
				}

				initedLogs = true;
			}
		});
		t.setName("initLogs");
		t.start();
	}

	private void initOther(final boolean firstRun, final boolean haveNetwork)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Firewall.init();
				AppManager.init(context, packageName());
				UserAgents.load(context);

				// check db versions
				boolean newer = Database.distribHaveNewer();
				if (newer)
				{
					// distrib version > current version (may be update? may be call MyUpdateReceiver?)
					Database.deleteDatabases();
					Database.setCurrentVersion(null); // force db reset
					Database.setCurrentFastVersion(0);
				}

				// init db
				final boolean changed = Database.init(); // must be first, before Exceptions.load and Browsers.load

				Exceptions.load();
				Browsers.load();

				if (changed)
				{
					// database reinstalled
					final boolean inited = Policy.updateScanner();

					if (!inited)
						Statistics.addLog(Settings.LOG_DB_UPDATE_ERR);
					else
						//App.cleanCaches(Settings.DEBUG_CC_ON_UPDATE, false, false, false); // need to clean+kill at the nearest time
						App.cleanCaches(true, false, false, false); // clean+kill on db update
				}

				//
				NetworkStateChangeReceiver.startTetherReceiver(context);
				ScreenStateReceiver.startScreenStateReceiver(context);

				TimerService.startTimers();
				if (firstRun)
				{
					//TimerService.evaluateNotifyInitTimer(false); // for test
					TimerService.feedbackNotifyInitTimer();
					//TimerService.feedback2NotifyInitTimer();
					//TimerService.firstResultNotifyInitTimer(); // for test
				}

				initedOther = true;
			}
		});
		t.setName("initOther");
		t.start();
	}

	private void initProxyBase(final boolean firstRun, final boolean haveNetwork)
	{
		ProxyBase.load();

		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				if (haveNetwork)
					ProxyBase.updateServers(true);

				initedProxyBase = true;
			}
		});
		t.setName("initProxyBase");
		t.start();
	}

	private void initBilling(final boolean firstRun, final boolean haveNetwork)
	{
		billing = new InAppBilling(this);
		//billing.serviceBind();

		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				initedBilling = true;

				if (!haveNetwork)
					return;
				if (!firstRun)
					Utils.sleep(15000); // for start on boot, time for all service start

				//if (haveNetwork /*&& (firstRun || billing.licenseIsCheckTime())*/)
				//billing.licenseCheckAsync(false);
				Policy.refreshToken(firstRun);
			}
		});
		t.setName("initBilling");
		t.start();
	}

	public static void checkUpdates()
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				// TODO XXX waiting for initialization (15 sec)
				// before check updates because we may unpack new db from distrib now! (see App.initOther)
				if (Settings.EVENTS_LOG)
					if (!App.waitForInitedAll(15)) Statistics.addLogFast(Settings.LOG_INIT_TIMEOUT + "2");

				UpdaterService.startUpdate(UpdaterService.START_FORCE); // first run or update
			}
		});
		t.start();
	}

	public static boolean initedAll()
	{
		return (initedLogs && initedOther && initedProxyBase && initedBilling);
	}

	/*
	 * waiting for initialization (timeMax sec)
	 * NOTE: timeout for onReceive, onCreate ~ 10 sec
	 */
	public static boolean waitForInitedAll(int timeMax)
	{
		if (timeMax <= 0)
			return initedAll();

		int i = 0;
		while (!App.initedAll() && i++ < timeMax)
			Utils.sleep(1000);

		return initedAll();
	}

	// other functions

	public static void postToHandler(Runnable r)
	{
		if (handler != null)
			handler.post(r);
	}

	public static int getMyUid()
	{
		return myUid;
	}

	private void findMyUid()
	{
		PackageManager pm = getPackageManager();
		try
		{
			final ApplicationInfo info = pm.getApplicationInfo(packageName(), PackageManager.GET_META_DATA);
			myUid = info.uid;
		}
		catch (PackageManager.NameNotFoundException e) { e.printStackTrace(); }
	}

	public static Context getContext()
	{
		return context;
	}

	public static boolean isLibsLoaded()
	{
		return libsLoaded;
	}

	public static String getInstallId()
	{
		return installId;
	}

	public static boolean isUIThread()
	{
		if (uiThread == null)
			return false;

		// may be check thread name ("Main Thread")?
		return (Thread.currentThread() == uiThread);
	}

	public static boolean isUIActive()
	{
		String name = Utils.getTopActivityPkgName(context);
		return (name.toLowerCase().contains(Settings.APP_NAME.toLowerCase())); // || name.isEmpty()
	}

	/*
	 * start VPN service
	 * see: LicenseActivity, OptionsActivity, PromptActivity, StartActivity
	 */
	public static void startVpnService(Context context)
	{
		vpnActivated = true; // enable vpn
		if (!Preferences.isActive())
			Preferences.putBoolean(Settings.PREF_ACTIVE, true);

		FilterVpnService.startVpn(context, false);

		// also start update service on service start
		// TODO XXX may be not need?
		UpdaterService.startUpdate(UpdaterService.START_DELAYED); // service wake up
	}

	public static void disable()
	{
		if (Preferences.isActive())
			Preferences.putBoolean(Settings.PREF_ACTIVE, false);

		FilterVpnService.stopVpn(context);
		FilterVpnService.stopService(context);

		UpdaterService.stopService(context);
	}

	public static String packageName()
	{
		return context.getPackageName();
	}

	public static InAppBilling getBilling()
	{
		return billing;
	}

	public static boolean isFirstRun()
	{
		return firstRun;
	}

	// return "ru", "en", etc. or null
//	public static String getSysLang()
//	{
//		return sysLang;
//	}

//	public static String refreshSysLang()
//	{
//		// TODO XXX lock with getSysLang
//		//try { sysLang = this.getResources().getConfiguration().locale.getLanguage(); } catch (Exception e) { }
//		sysLang = Utils.langGet();
//		return sysLang;
//	}

	public static void changeLanguage(boolean canRevert)
	{
		// отключил возможность переключения с русского на английский тк добавили другие языке
//		boolean useRu = Preferences.get(Settings.PREF_USE_RU_LANG);
//		boolean useEn = Preferences.get(Settings.PREF_USE_EN_LANG);
//
//		String lang = null;
//		if (useRu || useEn)
//			lang = (useRu) ? "ru" : "en";
//		else if (canRevert)
//			lang = getSysLang();
//
//		Utils.langChange(lang);
	}

	/*
	 * force - always run actions
	 * onlyKill - only apps kill, no cache clean
	 * onlyClean - only cache clean, no apps kill
	 * checkTime - check PREF_DISABLE_TIME and interval to select kill+clean or only kill
	 *
	 * return true if action executed
	 *
	 * with checkTime call before startVpnService because PREF_DISABLE_TIME will be reset in setupVpn
	 */
	public static boolean cleanCaches(boolean force, boolean onlyKill, boolean onlyClean,
										boolean checkTime)
	{
		if (!Settings.CLEAR_CACHES)
			return false;

		synchronized (ccLock)
		{
			if (!force && ScreenStateReceiver.isScreenOn())
			{
				// not force and screen enabled -> wait for screen off (see ScreenStateReceiver)
				if (!onlyKill)
					Preferences.putBoolean(Settings.PREF_CLEARCACHES_NEED, true);
				return false;
			}

			if (checkTime)
			{
				long disableTime = Preferences.get_l(Settings.PREF_DISABLE_TIME);
				long time = System.currentTimeMillis();

				if (disableTime <= 0 || disableTime + Settings.CLEAR_CACHES_INTERVAL > time)
					onlyKill = true; // interval not expire, kill only

				//Preferences.putLong(Settings.PREF_DISABLE_TIME, 0); // needed?
			}

			if (!onlyKill)
			{
				Preferences.putLong(Settings.PREF_CLEARCACHES_TIME, System.currentTimeMillis());
				Preferences.putBoolean(Settings.PREF_CLEARCACHES_NEED, false);

				if (Utils.clearCaches())
					Statistics.addLog(Settings.LOG_CACHES_CLEAR);
				else
					Statistics.addLog(Settings.LOG_CACHES_CLEAR_ERR);
				//L.a(Settings.TAG_APP, "clearCaches");
			}

			if (!onlyClean)
			{
				Utils.killBackgroundApps(Settings.APP_PACKAGE);
				//L.a(Settings.TAG_APP, "killApps");
			}

			return true;
		}
	}

	public static void showDisabledNotification(Context context)
	{
		if (!Settings.USER_FEEDBACK)
			return;

		long time = Preferences.get_l(Settings.PREF_NOTIFY_DISABLED);
		if (time > 0 && time < System.currentTimeMillis())
		{
			// time to show app disabled notification
			Notifications.showAppDisabledNotify(context);
			Preferences.setNotifyDisabledAlarm(false); // update time (to next time)
		}
	}

	//

	@Override
	public void onTrimMemory(int level)
	{
		//L.d(Settings.TAG_APP, "onTrimMemory, level = ", Integer.toString(level));

		if (Build.VERSION.SDK_INT >= 16)
		{
			//	TRIM_MEMORY_RUNNING_MODERATE and TRIM_MEMORY_RUNNING_CRITICAL
			if (level <= 15 && level >= 5)
			{
				ByteBufferPool.clear();
				PacketPool.compact();
			}
		}
		else
		{
			ByteBufferPool.clear();
			PacketPool.compact();
		}

		System.gc();

		super.onTrimMemory(level);

	}

	// TODO XXX move this to separate class

	@Override
	public FilterVpnOptions getOptions(Context context)
	{
		Intent i = OptionsActivity.getIntent(context, true);

		// why 10.250.69.1 ???
		FilterVpnOptions opts = new FilterVpnOptions(context.getString(Res.string.app_name),
														Settings.TUN_IP, 24, 0, i);
		opts.setDNSServers(FilterVpnOptions.DEFAULT_DNS_AFTER,
							Settings.DNS1_IP, Settings.DNS2_IP);

		if (NetUtil.isMobile())
			opts.mtu = 1400;
		else
			opts.mtu = 4096;

		return opts;
	}

	@Override
	public boolean isActive()
	{
		return Preferences.isActive();
	}

	@Override
	public IPacketLogger getLogger()
	{
		return null;
	}

	@Override
	public IFilterVpnServiceListener getServiceListener()
	{
		return (new VpnListener(this));
	}

	@Override
	public IFilterVpnPolicy getPolicy()
	{
		// TODO XXX waiting for initialization (5 sec, see waitForInitedAll)
		// before creating Policy because we may unpack new db from distrib now! (see App.initOther)
		if (Settings.EVENTS_LOG)
			if (!App.waitForInitedAll(5)) Statistics.addLogFast(Settings.LOG_INIT_TIMEOUT + "1");

		if (policy == null)
			policy = new Policy();

		return policy;
	}

	@Override
	public IHasherListener getHasherListener()
	{
		return new IHasherListener()
		{
			@Override
			public void onFinish(String url, byte[] sha1, byte[] sha256, byte[] md5)
			{
				if (Settings.DEBUG)
				{
					L.d(Settings.TAG_APP, "Hash url: ", url);
					L.d(Settings.TAG_APP, "Hash sha1: ", Utils.toHex(sha1));
					L.d(Settings.TAG_APP, "Hash sha256: ", Utils.toHex(sha256));
					L.d(Settings.TAG_APP, "Hash md5: ", Utils.toHex(md5));
				}
			}
		};
	}

	@Override
	public void onPermissionDenied()
	{
		Notifications.showBackgroundDataError();
	}

	//

	@Override
	public IUserNotifier getUserNotifier()
	{
		if (notifier == null)
			notifier = new MyUserNotifier();
		return notifier;
	}

	public static void clearNotifier()
	{
		if (notifier != null)
			notifier.clear();
	}

	private static class MyUserNotifier implements IUserNotifier
	{
		Hashtable<String, Long> map = new Hashtable<String, Long>();

		// notify from IP
		@Override
		public void notify(PolicyRules rules, byte[] serverIp)
		{
			NotifyActivity.show(rules.getVerdict(), BlockIPCache.getDomain(serverIp));
		}

		// notify from DNS and HTTP responce
		@Override
		public void notify(PolicyRules rules, String domain)
		{
			NotifyActivity.show(rules.getVerdict(), domain);
		}

		// notify from HTTP request
		@Override
		public void notify(PolicyRules rules, String domain, String refDomain)
		{
			if (!isReadyToShow(domain, refDomain))
				return;

			NotifyActivity.show(rules.getVerdict(), domain);
			map.put(domain + refDomain, System.currentTimeMillis());
		}

		boolean isReadyToShow(String domain, String refDomain)
		{
			long curTime = System.currentTimeMillis();
			final String key = domain + refDomain;

			if (map.containsKey(key))
			{
				long time = map.get(key);
				if (curTime - time < 5 * 60 * 1000) // 5 min timeout
					return false;
			}

			return true;
		}

		void clear()
		{
			map.clear();
		}
	}
/*
	void test1()
	{
		Thread licenseThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				int i = 0;

				try
				{
					String vendor = "1";
					String model = "1";
					String ref = "utm_source=1&utm_medium=banner&utm_campaign=aff";
					String data = "[{\"orderId\":\"12999763169054705758.1349636449630471\",\"packageName\":\"app.webguard\",\"productId\":\"pro_year_20150129\",\"purchaseTime\":1429636549534,\"purchaseState\":0,\"developerPayload\":\"2ae31615\",\"purchaseToken\":\"bhhilmailienjpejhhpkgkii.AO-J1Ow0GTHjB_OFlSm2dItALRvxvX6PfX1RJYh3QDkeg045eGNDJ5BXqr4kT1HuVJMTvGopJq_KNey6pLkKMREcE5EqsWWCs_k6uqEsPFnyUGvvx6rfCoJ-_MU3AKpgSPrwUIXYnAXD\",\"autoRenewing\":true}]";
					String signatures = "[\"bhhilmailienjpejhhpkgkii.AO-J1Ow0GTHjB_OFlSm2dItALRvxvX6PfX1RJYh3QDkeg045eGNDJ5BXqr4kT1HuVJMTvGopJq_KNey6pLkKMREcE5EqsWWCs_k6uqEsPFnyUGvvx6rfCoJ-_MU3AKpgSPrwUIXYnAXD\"]";
					String publisher = "google";
					String dev = "2ae31615";

					String request = "dev=" + dev +
							"&publisher=" + publisher +
							"&bug=1";

					request += "&json=" + URLEncoder.encode(data, "UTF-8");
					request += "&sig=" + URLEncoder.encode(signatures, "UTF-8");

					request += "&ref=" + URLEncoder.encode(ref, "UTF-8");
					request += "&vendor=" + URLEncoder.encode(vendor, "UTF-8") +
								"&model=" + URLEncoder.encode(model, "UTF-8");

					String billing_url = Preferences.getBillingUrl();
					UtilsHttpResult response = Utils.postData(billing_url, request.getBytes());

					int code = response.getResultCode();
					byte[] responseData = response.getData();

					i += 1;
				}
				catch (Exception e)
				{
					i += 1;
				}
			}
		});

		licenseThread.setName("test1");
		licenseThread.start();
	}
*/
}
