package app.netfilter;

import app.security.PolicyRules;
import app.netfilter.dns.DNSRequest;
import app.netfilter.dns.DNSResponse;
import app.netfilter.http.RequestHeader;
import app.netfilter.http.ResponseHeader;
import app.common.memdata.MemoryBuffer;
import app.netfilter.proxy.Packet;
import app.netfilter.proxy.TCPStateMachine;
import app.internal.ProxyBase;

import java.nio.ByteBuffer;

public interface IFilterVpnPolicy
{
	public abstract void reload();

	public abstract PolicyRules getPolicy(Packet packet);

	public abstract PolicyRules getPolicy(Packet packet, int uid);

	public abstract PolicyRules getPolicy(int uid);

	public abstract PolicyRules getPolicy(String domain);

	public abstract PolicyRules getPolicy(DNSResponse response);

	public abstract PolicyRules getPolicy(DNSRequest request);

	public abstract PolicyRules getPolicy(RequestHeader request, ResponseHeader response, byte[] data, int uid, boolean isBrowser, boolean isProxyUsed);

	public abstract PolicyRules getPolicy(RequestHeader request, int uid, String pkgname, boolean isBrowser);

	public abstract int getScanDataBufferSize(RequestHeader requestHeader, ResponseHeader responseHeader);

	public abstract boolean isBrowser(String[] packs);

	public abstract boolean isBrowser(int uid);

	public abstract void addRequestHeaders(RequestHeader header);

	public abstract boolean changeUserAgent();

	public abstract boolean needToAddHeaders();

	public abstract boolean changeReferer();

	public abstract void scan(MemoryBuffer buffer, TCPStateMachine tcpStateMachine);

	public abstract String getUserAgent();

	public abstract boolean isProxyUse(byte[] serverIp, int serverPort, int uid, boolean isBrowser);

	public abstract boolean isProxyCryptUse();

	public abstract ProxyBase.ProxyServer getProxyHost();

	public abstract int getProxyPort();

//	  public abstract void update(long time);

//	  public abstract void refreshToken();

//	  public abstract void clearToken();

	public abstract PolicyRules getPolicyForData(RequestHeader request, ByteBuffer buf);
}
