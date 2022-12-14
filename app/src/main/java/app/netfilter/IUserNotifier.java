package app.netfilter;

import app.security.PolicyRules;

public interface IUserNotifier
{
	public abstract void notify(PolicyRules rules, byte[] serverIp);

	public abstract void notify(PolicyRules rules, String domain);

	public abstract void notify(PolicyRules rules, String domain, String refDomain);
}
