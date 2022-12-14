package app.netfilter;

public interface IHasherListener
{
	public abstract void onFinish(String url, byte[] sha1, byte[] sha256, byte[] md5);
}
