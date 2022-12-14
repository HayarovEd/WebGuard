package app.netfilter;

import app.common.LibNative;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import app.netfilter.proxy.ChannelPool;
import app.netfilter.proxy.UDPClient;
import app.common.NetUtil;
import app.netfilter.proxy.ProxyManager;
import android.os.ParcelFileDescriptor;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import app.common.NetInfo;
import app.common.Utils;
import app.common.debug.DebugUtils;
import app.internal.Preferences;
import app.internal.ProxyBase;
import app.common.debug.L;
import app.App;
import app.info.Statistics;
import app.internal.Settings;
import app.security.Policy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Queue;
import org.squareup.okio.VpnSocketEnumerator;

public class FilterVpnService extends VpnService {
	private static final int JOB_ID = 1337; //1341;

	public static final int CMD_START_VPN				= 1;
	public static final int CMD_STOP_VPN				= 2;
	public static final int CMD_CONFIG_CHANGED			= 3;
	public static final int CMD_NOTIFICATION_CLEARED	= 4;
	public static final int CMD_PROXY_CHANGED			= 5;
	public static final int CMD_DROP_CONNECTS			= 6;
	public static final int CMD_START_VPN_EMERGENCY		= 7;
	public static final int CMD_STOP_SERVICE			= 8;

	public static final int ACTION_IDLE				= 0;
	public static final int ACTION_START			= 1;
	public static final int ACTION_STOP				= 2;
	public static final int ACTION_CONNECTED		= 3;
	public static final int ACTION_DISCONNECTED		= 4;
	public static final int ACTION_CHANGED			= 5;
	public static final int ACTION_TETHERING		= 6;
	public static final int ACTION_LAST				= 7; // must be last

	public static final String EXTRA_CMD = "cmd";

	public static final int TRANSITION_WAIT_TIMEOUT = 15; // 15 sec, timeout to wait transition state
	public static final int VPN_SETUP_DELAY = 3 * 1000; // 3 sec, delay time before async vpn setup
	public static final int VPN_WATCH_DELAY = 10 * 1000; // 10 sec, delay time before watch thread check VPN and network status
	public static final int VPN_STATS_SAVE_DELAY = 5 * 60 * 1000; // every 5 min save stats

	//	private boolean firstStart = true;
	private Handler handler;
	private IFilterVpnServiceListener listener;
	private FilterVpnOptions options = null;
	private IFilterVpnPolicy policy = null;
	private IPacketLogger packetLogger = null;
	private IHasherListener hasherListener = null;
	private IUserNotifier userNotifier = null;
	private static Thread watchThread = null;
	private static Thread statsThread = null;

	private final Object lock = new Object();
	private static volatile int inTransition = 0; // TODO XXX use lock instead
	private static final Queue<Integer> states = new ArrayDeque<Integer>();

	static void startService(Context context, Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}

	public FilterVpnService() {
		super();

		handler = new Handler();
	}

	// TODO XXX synchronization between statesAdd and statesGet, statesIsEmpty!!!!!
	// see startService and onStartCommand

	public static void startVpn(Context context, boolean emergency) {
		Intent i = new Intent(context, FilterVpnService.class);
		int cmd = (emergency) ? CMD_START_VPN_EMERGENCY : CMD_START_VPN;
		i.putExtra(EXTRA_CMD, cmd);

		startService(context, i, ACTION_START);
	}

	public static void startVpnByAlarm(Context context, boolean emergency, long time) {
		Intent i = new Intent(context, FilterVpnService.class);
		int cmd = (emergency) ? CMD_START_VPN_EMERGENCY : CMD_START_VPN;
		i.putExtra(EXTRA_CMD, cmd);
		i.putExtra("action", ACTION_START);

		PendingIntent pi = PendingIntent.getService(context, 1, i, 0);
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, time, pi);
	}

	public static void stopVpn(Context context) {
		Intent i = new Intent(context, FilterVpnService.class);
		i.putExtra(EXTRA_CMD, CMD_STOP_VPN);

		startService(context, i, ACTION_STOP);
	}

	// state - see NetUtil.ACTION_* and NetUtil.update
	public static void notifyConfigChanged(Context context, int state) {
		if (state < 0)
			return;

		Intent i = new Intent(context, FilterVpnService.class);
		i.putExtra(EXTRA_CMD, CMD_CONFIG_CHANGED);
		//i.putExtra("action", state);

		startService(context, i, state);
	}

	public static void notifyProxyChanged(Context context) {
		Intent i = new Intent(context, FilterVpnService.class);
		i.putExtra(EXTRA_CMD, CMD_PROXY_CHANGED);
		//i.putExtra("all", true);

		startService(context, i); //context.startService(i); // may be replace with local startService?
	}

	public static void notifyDropConnections(Context context) {
		Intent i = new Intent(context, FilterVpnService.class);
		i.putExtra(EXTRA_CMD, CMD_DROP_CONNECTS);
		i.putExtra("all", true);

		startService(context, i); //context.startService(i); // may be replace with local startService?
	}

	private static void startService(Context context, Intent i, int state) {
		synchronized (states) {
			//L.printBacktrace(Settings.TAG_FILTERVPNSERVICE, 'a');

			if (statesAdd(state) || state == ACTION_START || state == ACTION_STOP) {
				startService(context, i); //context.startService(i); // start service because queue was empty (or gui START/STOP)
			}
		}
	}

	public static void stopService(Context context) {
		//context.stopService(new Intent(context, FilterVpnService.class));

		Intent i = new Intent(context, FilterVpnService.class);
		i.putExtra(EXTRA_CMD, CMD_STOP_SERVICE);

		startService(context, i); //context.startService(i);
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

		if (!App.isLibsLoaded()) {
			return;
		}

		final IFilterVpnApplication app = (IFilterVpnApplication) getApplication();
		options = app.getOptions(this);
		if (options == null) {
			options = new FilterVpnOptions("VPN Service");
		}

		listener = app.getServiceListener();
		policy = app.getPolicy();
		packetLogger = app.getLogger();
		hasherListener = app.getHasherListener();
		userNotifier = app.getUserNotifier();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

//		if (Settings.VPN_SERVICE_FOREGROUND) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true); // see startForeground
		}
//		}
	}

	public void onRevoke() {

		if (listener != null) {
			listener.onVPNRevoked(this);
		}

		stopVpn();
		//stopSelf();
	}

	/*
	 * TODO XXX add use START_REDELIVER_INTENT
	 * http://stackoverflow.com/questions/2785843/how-can-i-prevent-my-android-app-service-from-being-killed-from-a-task-manager
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
//		if (firstStart) {
//			firstStart = false;
//			if (Settings.VPN_SERVICE_FOREGROUND)
//				startForeground();
		//MainVpnService.start(this);
//		}

		if (!App.isLibsLoaded()) {
			statesClear();
			return START_NOT_STICKY;
		}

		if (intent == null) {
			// TODO XXX wtf? why start vpn on empty intent?

			//L.d(Settings.TAG_FILTERVPNSERVICE, "Empty intent!");
			intent = new Intent();
			//intent.putExtra(EXTRA_CMD, CMD_START_VPN);
			intent.putExtra(EXTRA_CMD, CMD_START_VPN_EMERGENCY);
			intent.putExtra("action", ACTION_CONNECTED);
		}

		int command = intent.getIntExtra(EXTRA_CMD, -1);
		int action = intent.getIntExtra("action", -1);
		if (action != -1) {
			statesAdd(action); // for startByAlarm
		}

		boolean allConnections = intent.getBooleanExtra("all", false);

		//L.d(Settings.TAG_FILTERVPNSERVICE, "action = " + action, " cmd = " + command);

		switch (command) {
			// work with states

			case CMD_START_VPN:
			case CMD_START_VPN_EMERGENCY:

				if (Settings.DEBUG_STATE)
					L.a(Settings.TAG_FILTERVPNSERVICE, "CMD_STARTVPN " + (new Date()).toString());

				if (listener != null) {
					listener.onServiceStarted(this);
				}

				//setupVpnAsync();
				//startForeground(1, Utils.getServiceNotification(this, true));
				break;

			case CMD_STOP_VPN:
				if (Settings.DEBUG_STATE)
					L.a(Settings.TAG_FILTERVPNSERVICE, "CMD_STOPVPN " + (new Date()).toString());

				if (listener != null) {
					listener.onServiceStopped(this);
				}

				//stopVpn();
				//stopSelf(startId);
				break;

			case CMD_CONFIG_CHANGED:
				if (Settings.DEBUG_STATE)
					L.a(Settings.TAG_FILTERVPNSERVICE, "CMD_CONFIG_CHANGED " + (new Date()).toString());
				break;

			// not work with states

			case CMD_NOTIFICATION_CLEARED:
				//if (!ProxyManager.getInstance().notifyBlockingRuleChanged())
				//	  stopSelf();
				return START_STICKY;

			case CMD_PROXY_CHANGED:
			case CMD_DROP_CONNECTS:
				if (Settings.DEBUG_STATE) {
					String cmd = (command == CMD_PROXY_CHANGED) ?
							"CMD_PROXY_CHANGED " : "CMD_DROP_CONNECTS ";
					L.a(Settings.TAG_FILTERVPNSERVICE, cmd + (new Date()).toString());
				}

				// close proxy connections (or all connections) if proxy config changed
				if (allConnections) {
					ProxyManager.getInstance().closeAllConnections();
				} else {
					ProxyManager.getInstance().closeProxyConnections();
				}

				ChannelPool.clear(false);
				return START_STICKY;

			// service stop

			case CMD_STOP_SERVICE:
				stopSelf(); // TODO XXX and if VPN started???
				return START_NOT_STICKY;

			// aaaaa, err

			default:
				statesClear();
				throw new RuntimeException("Unknown command in FilterVpnService: " + command);
		}

		// on CMD_STARTVPN (CMD_STARTVPN_EMERGENCY), CMD_STOPVPN, CMD_CONFIG_CHANGED

		if (statesIsEmpty())
			return START_STICKY;

		// parse states
		// TODO XXX if CMD_STOP_VPN and network up (ACTION_START)? vpn not stopped!

//		Utils.sleep(1000); // wait, maybe new states will be added // fixed by Roman Popov
		int state = ACTION_IDLE;

//		while (true) { // fixed by Roman Popov
		while (!statesIsEmpty()) { // fixed by Roman Popov
			int state0 = statesGet();
			if (state0 == -1)
				break;
			if (state0 < ACTION_IDLE || state0 >= ACTION_LAST)
				continue; // invalid state

			if (Settings.DEBUG_STATE) L.a(Settings.TAG_FILTERVPNSERVICE, "state = " + state0);

			if (state0 == ACTION_TETHERING || state0 == ACTION_DISCONNECTED)
				state0 = ACTION_CHANGED; // nothing bad, just safety net
			else if (state0 == ACTION_IDLE)
				state0 = ACTION_CONNECTED; // sometimes NetworkStateChangeReceiver send IDLE on phone start

			if (state == ACTION_IDLE ||                                     // initial
					state0 == ACTION_STOP ||                                // switch off
					(state0 == ACTION_START && state != ACTION_CHANGED) ||  // switch on
					(state != ACTION_CHANGED && state != ACTION_STOP))      // other
			{
				if (state == ACTION_STOP && state0 == ACTION_START) state = ACTION_CHANGED;
				else state = state0;
			}

//			if (statesIsEmpty()) // fixed by Roman Popov
//				Utils.sleep(1000); // wait, maybe new states will be added // fixed by Roman Popov
		}

		if (state != ACTION_IDLE) {
			if (Settings.DEBUG_STATE) L.a(Settings.TAG_FILTERVPNSERVICE, "new state = " + state);

			if (state == ACTION_STOP || state == ACTION_DISCONNECTED || state == ACTION_TETHERING) {
				stopVpnAsync();
				//stopVpn();
			} else if (state == ACTION_START || state == ACTION_CONNECTED) {
				setupVpnAsync(false);
			} else {
				stopVpnAsync();
				//stopVpn();
				setupVpnAsync(true); // wait to miss VPN on/off bugs
			}
		}

		// костыль
		if (command != CMD_START_VPN_EMERGENCY)
			startWatch();

		return START_STICKY;
	}

	/*
	 * startForeground trick (to avoid killing by process cleaners and don't show icon)
	 *
	 * http://stackoverflow.com/questions/10962418/startforeground-without-showing-notification
	 * http://stackoverflow.com/questions/6397754/android-implementing-startforeground-for-a-service
	 * check with 'adb shell dumpsys activity services' -> FilterVpnService -> isForeground=true
	 *
	 * Clean Master kill result on rooted device:
	 * I/cm.log.uipro(24320): [KillTask][D]/ ForceStop:app.webguard oom:1 uid:10118 mem:20335 servces:2
	 * I/ActivityManager(  774):  removeProcessLocked app.persistent = false callerWillRestart = false
	 * I/ActivityManager(  774): Force stopping package app.webguard appid=10118 user=0
	 * I/ActivityManager(  774): Killing proc 23359:app.webguard/u0a10118: force stop app.webguard
	 * W/ActivityManager(  774): Scheduling restart of crashed service app.webguard/.UpdaterService in 83119ms
	 * W/ActivityManager(  774): Scheduling restart of crashed service app.webguard/netfilter.FilterVpnSe
	 * I/ActivityManager(  774):  removeProcessLocked app.persistent = false callerWillRestart = false
	 * I/ActivityManager(  774):   Force stopping service ServiceRecord{45ddfa38 u0 app.webguard/netfilte
	 */
//	private void startForeground() {
//		MainVpnService.start(this);
//		Notification n = MainVpnService.getNotification(this);
//		startForeground(MainVpnService.NOTIFICATION_ID, n);
//		MainVpnService.stop(this);
//	}

	private void startWatch() {
		// on vpn enable start watch thread and wait, then enable vpn if have network and vpn not enable
		// TODO XXX spike, maybe remove?
		if (watchThread != null)
			return;

		watchThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Utils.sleep(VPN_WATCH_DELAY);

				final IFilterVpnApplication app = (IFilterVpnApplication) getApplication();

				if (!ProxyManager.isStarted() &&
						NetUtil.getStatus() >= 0 && App.vpnActivated && app.isActive()) {
					startVpn(getApplication(), true);
				}

				watchThread = null;
			}
		});
		watchThread.start();
	}

	public int getMtu() {
		return options.mtu;
	}

	private void setDnsServers(Builder vpnBuilder) {
		final String fakeDNS = Settings.DNS_FAKE_IP;

		// first set DNS servers from settings (see FilterVpnOptions.setDNSServers)
		UDPClient.setDNSServers(options.dnsServers);

		// next add DNS servers from provider
		ArrayList<String> servers = NetUtil.getDNSServers();
		if (servers.size() == 1 && servers.get(0).equals(fakeDNS)) {
			servers = Preferences.getDNSServers();
		} else {
			Preferences.putDNSServers(servers);
		}
		UDPClient.addDNSServers(servers);

		// for VPN set our fakeDNS server
		try {
			vpnBuilder.addDnsServer(fakeDNS);
		} catch (IllegalArgumentException e) {
			logAddDnsServerError(fakeDNS);
		}

		/*
		if ((options.useDefaultDNSServers == FilterVpnOptions.DEFAULT_DNS_BEFORE ||
				options.useDefaultDNSServers == FilterVpnOptions.DEFAULT_DNS_ONLY) && servers != null)
		{
			for (String server : servers)
			{
				try
				{
					vpnBuilder.addDnsServer(server);
				}
				catch (IllegalArgumentException e)
				{
					logAddDnsServerError(server);
				}
			}
		}

		for (String server : options.dnsServers)
		{
			try
			{
				vpnBuilder.addDnsServer(server);
			}
			catch (IllegalArgumentException e)
			{
				logAddDnsServerError(server);
			}
		}

		if (options.useDefaultDNSServers == FilterVpnOptions.DEFAULT_DNS_AFTER && servers != null)
		{
			for (String server : servers)
			{
				try
				{
					vpnBuilder.addDnsServer(server);
				}
				catch (IllegalArgumentException e)
				{
					logAddDnsServerError(server);
				}
			}
		}
		*/
	}

	private void logAddDnsServerError(String server) {
		if (listener != null)
			listener.onOtherError("Error setting DNS server: " + server);
	}

	private void setupVpnAsync(final boolean wait) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (wait)
					Utils.sleep(VPN_SETUP_DELAY);

				waitInTransition();
				setupVpn();
			}
		}).start();
	}

	// start VPN (didn't start VPN if no network, see NetUtil.getStatus)
	private void setupVpn() {
		//L.a("setupVpn", "a " + PromptActivity.vpnActivated);
		startTransition();

		// check states needed to activate VPN (network state and Preferences.PREF_ACTIVE)
		// also check vpnActivated because CMD_CONFIG_CHANGED can be sended, before user click PromptActivity (LicenseActivity)

		final IFilterVpnApplication app = (IFilterVpnApplication) getApplication();
		//NetUtil.init(FilterVpnService.this.getApplicationContext());

		if (NetUtil.getStatus() < 0 || !App.vpnActivated || !app.isActive()) {
			endTransition();
			return;
		}

		/*
		if (!NetUtil.hasInternetPermission())
		{
			if (listener != null)
				listener.onNoInternetPermission();
			inTransition = false;
			return;
		}
		*/

		/*
		if (WiFi.isProxySet(this))
		{
			if (listener != null)
				listener.onProxyIsSet(this);
			return;
		}
		*/

		synchronized (lock) // TODO XXX maybe replace with normal lock?
		{
			if (ProxyManager.isStarted()) {
				endTransition();
				return;
			}

			//WiFi.setWifiTetheringEnabled(App.getContext(), false);

			//
			if (listener != null)
				listener.onBeforeServiceStart(this);

			Builder vpnBuilder = new Builder();
			if (options.sessionName != null)
				vpnBuilder.setSession(options.sessionName);
			if (options.mtu != 0)
				vpnBuilder.setMtu(options.mtu);

			vpnBuilder.addAddress(options.address, options.maskBits);
			if (options.addDefaultRoute)
				vpnBuilder.addRoute("0.0.0.0", 0);

			if (options.configureIntent != null) {
				PendingIntent pi = PendingIntent.getActivity(this, 5, options.configureIntent, 0);
				vpnBuilder.setConfigureIntent(pi);
			}

			setDnsServers(vpnBuilder);

			try {
				ParcelFileDescriptor pfd = null;

				try {
					pfd = vpnBuilder.establish();
				} catch (NullPointerException e) {
					Statistics.addLog(Settings.LOG_VPNESTABLISH_NULL_ERR);
				}

				if (pfd == null) {
					if (listener != null)
						listener.onVPNEstablishError(this);

					Statistics.addLog(Settings.LOG_VPNESTABLISH_UNK_ERR);
					return;
				}
				LibNative.fileSetBlocking(pfd.getFd(), true);

				// start network processing
				VpnSocketEnumerator.setVpnService(this); // use patched OkHttp and OkIo to bypass TUN on our http request
				ProxyManager.getInstance().start(this, pfd, policy, packetLogger, hasherListener, userNotifier);

				statsStart();

				//
				if (listener != null)
					listener.onVPNStarted(this);

				// clear vpn stop time
				Preferences.putLong(Settings.PREF_DISABLE_TIME, 0);
			} catch (IllegalStateException e) {
				onVPNEstablishException(e);
			} catch (IllegalArgumentException e) {
				onVPNEstablishException(e);
			}

		} // sync

		endTransition();
	}

	private void onVPNEstablishException(Exception e) {
		if (listener != null)
			listener.onVPNEstablishException(this, e);

		Statistics.addLog(Settings.LOG_VPNESTABLISH_TUN_ERR);

		if (handler != null) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					FilterVpnService.this.stopSelf();
				}
			});
		}
	}

	private void stopVpnAsync() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				waitInTransition();
				stopVpn();
			}
		}).start();
	}

	private void stopVpn() {
		startTransition();

		if (Settings.DEBUG_PROFILE_MEM)
			DebugUtils.dumpHprof("vpn", true);

		synchronized (lock) // TODO XXX maybe replace with normal lock?
		{
			VpnSocketEnumerator.setVpnService(null); // use patched OkHttp and OkIo to bypass TUN on our http request

			statsSave(); // save stats before close connections

			ProxyManager.getInstance().closeAllConnections(); // close all connections before vpn shutdown
			ProxyManager.getInstance().stop();

			statsStop();

			if (listener != null)
				listener.onVPNStopped(this);
		} // sync

		ProxyBase.notifyServersUp();
		System.gc();

		//stopForeground(true);

		if (Settings.DEBUG_PROFILE_MEM) {
			Utils.sleep(10000);
			DebugUtils.dumpHprof("vpn_gc", true);
		}

		if (Settings.DEBUG_PROFILE_START)
			DebugUtils.disableTracing(true);

		// save vpn stop time if app disabled
		if (!Preferences.isActive())
			Preferences.putLong(Settings.PREF_DISABLE_TIME, System.currentTimeMillis());

		endTransition();
	}

	public static boolean isInTransition() {
		return (inTransition != 0);
	}

	public static void startTransition() {
		inTransition++;
	}

	public static void endTransition() {
		inTransition--;
	}

	public static boolean waitInTransition() {
		// TODO XXX what do if reach counter max?
		int counter = 0;
		while (inTransition != 0 && counter++ < TRANSITION_WAIT_TIMEOUT)
			Utils.sleep(1000);

		return (inTransition != 0);
	}

	// add state to states queue (return true if queue was empty)
	private static boolean statesAdd(int state) {
		synchronized (states) {
			boolean empty = states.isEmpty();
			states.add(state);

			if (Settings.DEBUG_STATE) L.a(Settings.TAG_FILTERVPNSERVICE, "state add " + state);

			return empty;
		}
	}

	private static boolean statesIsEmpty() {
		synchronized (states) {
			return states.isEmpty();
		}
	}

	private static int statesGet() {
		synchronized (states) {
			Integer value = states.poll();
			return ((value == null) ? -1 : (int) value);
		}
	}

	private static void statesClear() {
		synchronized (states) {
			states.clear();
		}
	}

	//
	private void statsSave() {
		// TODO XXX may be move to ProxyWorker?
		int[] counts = ProxyManager.getInstance().getClientCounts();
		int[] netinfoStats = NetInfo.statsGetInfo(); // Roman Popov
//		int[] netinfoStats = new int[4];
		long[] policyStats = Policy.statsGetInfo();

//		NetInfo.statsReset();
		Policy.statsReset();

		if (listener != null)
			listener.saveStats(counts, netinfoStats, policyStats);
	}

	private void statsStart() {
		if (statsThread != null)
			return;

		statsThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (ProxyManager.isStarted()) {
						statsSave();
						//L.a(Settings.TAG_FILTERVPNSERVICE, "stats saved");

						Utils.sleep(VPN_STATS_SAVE_DELAY);
					}
				} catch (Exception e) {
				}

				statsThread = null;
			}
		});
		statsThread.start();
	}

	// TODO by now only interrupt sleep, so call this after VPN stop
	private void statsStop() {
		if (statsThread != null)
			return;

		try {
			statsThread.interrupt();
		} catch (Exception e) {
		}
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
