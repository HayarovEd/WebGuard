package app;

import app.internal.Preferences;
import app.common.Usb;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import app.netfilter.FilterVpnService;
import app.common.NetUtil;
import app.info.Statistics;
import app.internal.Settings;
import app.security.Firewall;
import app.security.Policy;
import app.ui.StartActivity;
import java.util.HashMap;

public class NetworkStateChangeReceiver extends BroadcastReceiver
{
	private static final HashMap<Integer, NetworkInfo.State> states = new HashMap<Integer, NetworkInfo.State>();
	private static boolean noConnectivity = true;

	static
	{
		updateNetworkInfo();
	}

	public NetworkStateChangeReceiver()
	{
		super();
	}

	public void onReceive(Context context, Intent intent) {

		if (!App.isLibsLoaded())
			return;

		int state = updateNetworkState(intent); // TODO XXX see func comment
		//L.a(Settings.TAG_NETWORKSTATECHANGERECEIVER, "onReceive " + state);

		boolean active = Preferences.isActive();
		if (!active)
		{
			// show notification about disabled protection
			App.showDisabledNotification(context);

			return;
		}

		// protection active, run actions

		int status = NetUtil.getStatus();
		boolean netOn = (status != -1);

		if (netOn)
		{
			Firewall.onNetworkChanged();
			Policy.refreshToken(false);

			//if (Policy.getUserToken(true) == null)
			//	  OptionsActivity.notifyAboutSubscriptionFeatures(); // moved to InAppBilling

			/*
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					InAppBilling billing = App.getBilling();
					if (billing.hasNoItems())
					{
						billing.loadItemsAsync();
						Utils.sleep(500);
					}

					ProxyBase.update(System.currentTimeMillis());
				}
			}).start();
			*/
		}

		// CONNECTIVITY_CHANGE can recived before BOOT_COMPLETED, so show dialog
		if (!App.startProcessed) // see BootCompletedReceiver
		{
			Intent i = StartActivity.getIntent(context, true);
			i.setAction("autoStart");
			context.startActivity(i);

			return;
		}

		//L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "Connectivity state: " + state);

		//if (state == FilterVpnService.ACTION_IDLE)
		//	  return;
		FilterVpnService.notifyConfigChanged(context, state);
	}

	public static void startTetherReceiver(Context context)
	{
		context.registerReceiver(new BroadcastReceiver()
		{
			private static final int NO_TETHER = -1;
			private static final int USB_TETHER = 1;
			private static final int WIFI_TETHER = 2;

			private int state = NO_TETHER;

			@Override
			public void onReceive(Context context, Intent intent)
			{
				//L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "TetherReceiver ", intent.toString());

				if (Preferences.isActive())
				{
					int curState;

					if (NetUtil.isSharingWiFi((WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)))
						curState = WIFI_TETHER;
					else if (Usb.isUsbTethered())
						curState = USB_TETHER;
					else
						curState = NO_TETHER;

					int action = FilterVpnService.ACTION_CHANGED;
					if (curState != state)
					{
						if (curState != NO_TETHER)
							action = FilterVpnService.ACTION_TETHERING;
						state = curState;

						//L.e(Settings.TAG_NETWORKSTATECHANGERECEIVER, "Starting from TETHER RECEIVER!!!");

						FilterVpnService.notifyConfigChanged(context, state);
						if (curState != NO_TETHER)
							Statistics.addLog(Settings.LOG_TETHERING);
					}
				}
			}
		}, new IntentFilter("android.net.conn.TETHER_STATE_CHANGED"));
	}

	public static void onNetLink(int type)
	{
		//L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "netlink: " + type);
	}

	//
	private static void updateNetworkInfo()
	{
		NetworkInfo[] infos = NetUtil.getAllNetworkInfo();
		if (infos == null)
			return;

		for (NetworkInfo info : infos)
		{
			states.put(info.getType(), info.getState());
			if (info.getState() == NetworkInfo.State.CONNECTED)
				noConnectivity = false;
		}
	}

	/*
	 * return network state bases on intent content
	 *
	 * TODO XXX incorrect work int some cases (on gprs->Wifi switch return ACTION_CONNECTED)
	 * may be remove it!?
	 */
	public static int updateNetworkState(Intent intent)
	{
		boolean noConnectivity0 = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
//		  String reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
//		  boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);
		int status = NetUtil.getStatus();

		NetworkInfo extraNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
		NetworkInfo otherNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);

//		  L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "noConnectivity: " + noConnectivity + " reason: " + reason + " isFailover: " + isFailover +
//				" EXTRA_EXTRA_INFO: " + intent.getStringExtra(ConnectivityManager.EXTRA_EXTRA_INFO));
//		  L.w(Settings.TAG_NETWORKSTATECHANGERECEIVER, "extraNetworkInfo: " + extraNetworkInfo);
//		  L.w(Settings.TAG_NETWORKSTATECHANGERECEIVER, "otherNetworkInfo: " + otherNetworkInfo);

		int res = FilterVpnService.ACTION_IDLE;

		if (!noConnectivity0 && status == -1)
			// android 5 fix
			// http://stackoverflow.com/questions/29677852/connectivitymanager-extra-no-connectivity-is-always-false-on-android-lollipop
			noConnectivity0 = true;

		if (extraNetworkInfo != null)
		{
			NetworkInfo.State state = extraNetworkInfo.getState();
			int type = extraNetworkInfo.getType();

			if (states.get(type) != state)
			{
				if (state == NetworkInfo.State.CONNECTED && otherNetworkInfo == null && !noConnectivity0)
				{
					//L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "Sending action: connected");
					res = FilterVpnService.ACTION_CONNECTED;
				}

				if (state == NetworkInfo.State.DISCONNECTED && otherNetworkInfo != null &&
					otherNetworkInfo.getState() == NetworkInfo.State.CONNECTED && !noConnectivity0)
				{
					//L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "Sending action: changed");
					res = FilterVpnService.ACTION_CHANGED;
				}

				states.put(type, state);
			}

			if (noConnectivity != noConnectivity0 && state != NetworkInfo.State.SUSPENDED)
			{
				res = (noConnectivity0) ? FilterVpnService.ACTION_DISCONNECTED : FilterVpnService.ACTION_CONNECTED;
				noConnectivity = noConnectivity0;
				//L.d(Settings.TAG_NETWORKSTATECHANGERECEIVER, "Sending action: ", Integer.toString(res));
			}
		}

		if (status == -2)
		{
			//L.e(Settings.TAG_NETWORKSTATECHANGERECEIVER, "Tethering detected!");
			res = FilterVpnService.ACTION_TETHERING;
		}

		return res;
	}
}
