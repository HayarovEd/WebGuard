package app.netfilter;

public interface IFilterVpnServiceListener
{
	public abstract void onServiceStarted(FilterVpnService service);

	public abstract void onServiceStopped(FilterVpnService service);

	public abstract void onBeforeServiceStart(FilterVpnService service);

	public abstract void onVPNStarted(FilterVpnService service);

	public abstract void onVPNStopped(FilterVpnService service);

	public abstract void onVPNRevoked(FilterVpnService service);

	public abstract void onVPNEstablishError(FilterVpnService service);

	public abstract void onVPNEstablishException(FilterVpnService service, Exception e);

	public abstract void onOtherError(String error);

	public abstract void onProxyIsSet(FilterVpnService service);

	public abstract void saveStats(int[] clientsCounts, int[] netinfo, long[] policy);

	public abstract void onNoInternetPermission();
}
