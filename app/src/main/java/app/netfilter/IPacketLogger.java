package app.netfilter;

import app.netfilter.proxy.Packet;

public interface IPacketLogger
{
	public abstract void log(Packet packet, boolean parsed);
}
