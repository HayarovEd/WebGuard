package app.netfilter;

import android.content.Context;

public interface IFilterVpnApplication
{
	public abstract FilterVpnOptions getOptions(Context context);

	public abstract IPacketLogger getLogger();

	public abstract IFilterVpnServiceListener getServiceListener();

	public abstract IFilterVpnPolicy getPolicy();

	public abstract IHasherListener getHasherListener();

	public abstract IUserNotifier getUserNotifier();

	public abstract void onPermissionDenied();

	public abstract boolean isActive();
}
