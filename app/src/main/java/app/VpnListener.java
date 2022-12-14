package app;

import app.internal.Preferences;
import android.content.Context;
import app.netfilter.FilterVpnService;
import app.netfilter.IFilterVpnApplication;
import app.netfilter.IFilterVpnPolicy;
import app.netfilter.IFilterVpnServiceListener;
import app.netfilter.dns.DNSUtils;
import app.security.Exceptions;
import app.ui.Notifications;
import app.common.debug.L;
import app.common.Utils;
import app.common.WiFi;
import app.info.Statistics;
import app.internal.Settings;
import app.ui.Toasts;


public class VpnListener implements IFilterVpnServiceListener
{
	//private Context context;
	//private Handler handler;

	public VpnListener(Context context)
	{
		//this.context = context;
		//handler = new Handler();
	}

	@Override
	public void onBeforeServiceStart(FilterVpnService service)
	{
		//L.d(Settings.TAG_VPNLISTENER, "Service is about to start...");
	}

	@Override
	public void onServiceStarted(FilterVpnService service)
	{
		L.a(Settings.TAG_VPNLISTENER, "Service started");
	}

	@Override
	public void onServiceStopped(FilterVpnService service)
	{
		L.a(Settings.TAG_VPNLISTENER, "Service stopped");

		//service.stopService(new Intent(service, UpdaterService.class));
	}

	@Override
	public void onVPNStarted(FilterVpnService service)
	{
		L.a(Settings.TAG_VPNLISTENER, "VPN established");

		// TODO XXX may start after App.startVpnService
		int errorCount = Preferences.get_i(Settings.PREF_UPDATE_ERROR_COUNT); // default value < 0
		if (errorCount > UpdaterService.UPDATE_ERRORS_MIN || errorCount < 0)
			UpdaterService.startUpdate(UpdaterService.START_FORCE_DELAYED); // force updates start if have errors

		final IFilterVpnPolicy policy = ((IFilterVpnApplication) service.getApplication()).getPolicy();
		if (policy != null)
			policy.reload();

		startResolvingThread();
		//Utils.startMemoryDrain(32768, 10);
		//Utils.test();
	}

	@Override
	public void onVPNStopped(FilterVpnService service)
	{
		L.a(Settings.TAG_VPNLISTENER, "VPN stopped");

		boolean active = Preferences.isActive();

		if (Settings.UPDATER_STOP_ON_OFF || !active)
			UpdaterService.stopService(service);

		if (Settings.VPN_SERVICE_STOP_ON_OFF || !active)
			FilterVpnService.stopService(service);
	}

	@Override
	public void onVPNRevoked(FilterVpnService service)
	{
		L.a(Settings.TAG_VPNLISTENER, "VPN revoked by user");

		Preferences.setNotifyDisabledAlarm(false);
		App.disable();
	}

	@Override
	public void onVPNEstablishError(FilterVpnService service)
	{
		//L.e(Settings.TAG_VPNLISTENER, "VPN establish error");
		L.a(Settings.TAG_VPNLISTENER, "VPN establish error");

		Toasts.showVpnEstablishError();
	}

	@Override
	public void onVPNEstablishException(FilterVpnService service, Exception e)
	{
		//L.e(Settings.TAG_VPNLISTENER, "Error establishing VPN!");
		L.a(Settings.TAG_VPNLISTENER, "Error establishing VPN!");

		e.printStackTrace();
		Toasts.showVpnEstablishException();
	}

	@Override
	public void onOtherError(String error)
	{
		L.e(Settings.TAG_VPNLISTENER, error);
	}

	@Override
	public void onProxyIsSet(FilterVpnService service)
	{
		//Dialogs.showTryDisableProxy(null);
	}

	@Override
	public void saveStats(int[] clientsCounts, int[] netinfo, long[] policy)
	{
		Statistics.updateStats(clientsCounts, netinfo, policy);

		if (Settings.DEBUG)
		{
			L.d(Settings.TAG_VPNLISTENER, "tcp clients " + clientsCounts[0]);
			L.d(Settings.TAG_VPNLISTENER, "udp clients " + clientsCounts[1]);

			L.d(Settings.TAG_VPNLISTENER, "netlink errors " + netinfo[0]);
			L.d(Settings.TAG_VPNLISTENER, "netlink not found " + netinfo[1]);

			L.d(Settings.TAG_VPNLISTENER, "proc retries " + netinfo[2]);
			L.d(Settings.TAG_VPNLISTENER, "proc not found " + netinfo[3]);

			L.d(Settings.TAG_VPNLISTENER, "ads ips blocked " + policy[0]);
			L.d(Settings.TAG_VPNLISTENER, "ads urls blocked " + policy[1]);
			L.d(Settings.TAG_VPNLISTENER, "apk blocked " + policy[2]);
			L.d(Settings.TAG_VPNLISTENER, "malware blocked " + policy[3]);
			L.d(Settings.TAG_VPNLISTENER, "paid blocked " + policy[4]);
			L.d(Settings.TAG_VPNLISTENER, "traffic saved " + policy[5]);
		}
	}

	@Override
	public void onNoInternetPermission()
	{
		Notifications.showBackgroundDataError();
	}

	private void startResolvingThread()
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				// wait 5 sec and ...
				// TODO XXX if no inet? if not started?
				Utils.sleep(5000);

				// resolve Chrome compression domains (to block ssl connections)
				String[] buf = Exceptions.getCompressed();
				for (String domain : buf)
					DNSUtils.resolve(domain);

				// tun test
				Utils.canConnect(Settings.TEST_TUN_WORK_IP, 80);

				// check proxy bug (traffic -> proxy -> vpn)
				if (WiFi.isProxySet(App.getContext()))
					Utils.canConnect(Settings.TEST_LOCAL_PROXY_IP, 80);
			}
		}).start();
	}
}
