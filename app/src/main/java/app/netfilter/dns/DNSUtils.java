package app.netfilter.dns;

import app.ui.Notifications;
import app.common.debug.L;
import app.internal.Settings;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class DNSUtils
{
	public static boolean resolve(String domain)
	{
		try
		{
			final InetAddress address = InetAddress.getByName(domain);
			//L.d(Settings.TAG_DNSUTILS, "Domain ", domain, " resolved to ", address.toString());
			return true;
		}
		catch (UnknownHostException e)
		{
			//e.printStackTrace();
			L.e(Settings.TAG_DNSUTILS, e.getMessage());
			return false;
		}
		catch (SecurityException e)
		{
			// in case of disabling background data
			e.printStackTrace();

			Notifications.showBackgroundDataError();
			return false;
		}
	}
}
