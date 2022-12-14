package app.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import androidx.annotation.RequiresApi;

import app.App;
import app.common.debug.L;
import app.internal.Settings;
import app.security.Processes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;

import static android.os.Process.INVALID_UID;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

public class NetInfo
{
	public final byte[] localIp;
	public final int localPort;
	public final byte[] serverIp;
	public final int serverPort;
	public final int state;
	public final int uid;

	private static boolean lastFoundOnTcpV6 = true; // ???
	private static boolean lastFoundOnUdpV6 = true;
	private static boolean netlinkTested = false;
	private static boolean netlinkUse = false;

	private static int netlinkErrCount = 0; // may be use AtomicInteger?
	private static int netlinkNotFoundCount = 0;
	private static int procRetryCount = 0;
	private static int procNotFoundCount = 0;

	private NetInfo(byte[] serverIp, int serverPort, byte[] localIp, int localPort, int state, int uid)
	{
		this.serverIp = serverIp;
		this.serverPort = serverPort;
		this.localIp = localIp;
		this.localPort = localPort;
		this.state = state;
		this.uid = uid;
	}

	public static void dumpNetInfo()
	{
		if (Settings.DEBUG)
		{
			try
			{
				FastLineReaderReverse reader = new FastLineReaderReverse("/proc/self/net/tcp6");
				while (true)
				{
					String line = reader.readLine();
					if (line == null)
						break;

					L.i(Settings.TAG_NETINFO, "TCP6 ", line);
				}
				reader.close();
				reader = new FastLineReaderReverse("/proc/self/net/tcp");
				while (true)
				{
					String line = reader.readLine();
					if (line == null)
						break;

					L.i(Settings.TAG_NETINFO, "TCP4 ", line);
				}
				reader.close();
			}
			catch (IOException e) { e.printStackTrace(); }
		}
	}

	public static int findMatchingUidInTcp(int localPort, byte[] remoteIp, int remotePort)
	{
		int res;

		if (Settings.TCPIP_CON_USE_NETLINK)
		{
			if (!netlinkTested)
			{
				netlinkTested = true;
				netlinkUse = LibNative.netlinkIsWork();
			}

			if (netlinkUse)
			{
				res = LibNative.netlinkFindUid(localPort, remoteIp, remotePort);
				if (res != -2)
				{
					if (res == -1)
						netlinkNotFoundCount++;

					//L.d(Settings.TAG_NETINFO, "uid from netlink: " + res);
					return res;
				}

				netlinkErrCount++;
			}
		}

		// netlink subsystem not available try to parse /proc

		res = NetLine.getUidFromProc(remoteIp, remotePort, localPort, true);
		if (res < 0)
		{
			procRetryCount++;
			L.d(Settings.TAG_NETINFO, "Not found uid in proc!");

			// wait, and try again because /proc slow update
			// TODO XXX may be other timeout?

			Utils.sleep(50);
			res = NetLine.getUidFromProc(remoteIp, remotePort, localPort, false);

			if (res < 0)
			{
				procNotFoundCount++;
				L.d(Settings.TAG_NETINFO, "Not found uid in proc second time!");
			}
		}

		return res;
	}

	// Roman Popov
	// for Firewall support in Android 10
	// https://stackoverflow.com/questions/58497492/acccess-to-proc-net-tcp-in-android-q
	// https://github.com/M66B/NetGuard/blob/053c11dc1d1e54ecc244b69084ffb6f1cf107e23/app/src/main/java/eu/faircode/netguard/ServiceSinkhole.java
	@RequiresApi(api = Build.VERSION_CODES.Q)
	public static int findMatchingUidInTcpQ(byte[] localIp, int localPort, byte[] remoteIp, int remotePort)
	{
		String _localIp = NetLine.getIpString(localIp);
		String _remoteIp = NetLine.getIpString(remoteIp);

		InetSocketAddress remoteInetSocketAddress = new InetSocketAddress(_remoteIp, remotePort);
		InetSocketAddress localInetSocketAddress = new InetSocketAddress(_localIp, localPort);

		ConnectivityManager connectivityManager = (ConnectivityManager)  App.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager == null) {
			return 0;
		}

		int uid = connectivityManager.getConnectionOwnerUid(IPPROTO_TCP, localInetSocketAddress, remoteInetSocketAddress);
		if (uid == INVALID_UID) {
			uid = connectivityManager.getConnectionOwnerUid(IPPROTO_UDP, localInetSocketAddress, remoteInetSocketAddress);
		}

		if (uid != INVALID_UID) {
			return uid;
		}

		return 0;
	}

	public static int findMatchingUidInUdp(int localPort, byte[] remoteIp, int remotePort)
	{
		int res = 0;
		FastLineReader reader = new FastLineReader("/proc/self/net/udp");

		String line;
		while ((line = reader.readLine()) != null)
		{
			if (LibNative.asciiIndexOf("sl", line) >= 0)
				continue;

			//L.w(Settings.TAG_NETINFO, "Line v4: " + line);

			NetInfo info = NetInfo.parseLineUdp4(line);
			if (info != null && info.state == 2 && info.localPort == localPort &&
				info.serverPort == remotePort && Arrays.equals(info.serverIp, remoteIp))
			{
				res = info.uid;
			}
		}

		if (res < 0)
		{
			reader = new FastLineReader("/proc/self/net/udp6");

			while ((line = reader.readLine()) != null)
			{
				if (LibNative.asciiIndexOf("sl", line) >= 0)
					continue;

				//L.w(Settings.TAG_NETINFO, "Line v6: " + line);

				NetInfo info = NetInfo.parseLineUdp6(line);
				if (info != null && info.state == 2 && info.localPort == localPort &&
					info.serverPort == remotePort && Arrays.equals(info.serverIp, remoteIp))
				{
					res = info.uid;
				}
			}
		}

		reader.close();

		return res;
	}

	public static NetInfo findMatchingTcpNetInfo(byte[] serverIp, int serverPort, byte[] localIp, int localPort)
	{
		int v4 = 0;
		int v1 = 0;
		boolean tcpV6;
		FastLineReader reader = null;
		NetInfo netInfo = null;
		byte[] v8 = new byte[4];

		while (v1 < 2)
		{
			if (v1 != 0)
				tcpV6 = !NetInfo.lastFoundOnTcpV6;
			else
				tcpV6 = NetInfo.lastFoundOnTcpV6;

			if (!tcpV6)
				reader = new FastLineReader("/proc/self/net/tcp");
			else
				reader = new FastLineReader("/proc/self/net/tcp6");

			String line;
			do
			{
				line = reader.readLine();
				if (line != null)
				{
					// prevent from parsing the title line
					if (LibNative.asciiIndexOf("sl", line) >= 0)
						continue;

					if (!tcpV6)
						netInfo = NetInfo.parseLineTcp4(line);
					else
						netInfo = NetInfo.parseLineTcp6(line);

					if ((netInfo != null) && ((netInfo.state == 2) && ((netInfo.serverPort == serverPort) &&
						((netInfo.localPort == localPort) && (Arrays.equals(netInfo.serverIp, serverIp))))))
					{
						if (Arrays.equals(netInfo.localIp, localIp))
						{
							NetInfo.lastFoundOnTcpV6 = tcpV6;
							if (reader != null)
								reader.close();

							return netInfo;
						}
					}
				}
				else
				{
					v4 = 0;
					v1 = (v1 + 1);
				}

				//L.e(Settings.TAG_NETINFO, "line = " + line);
//				  if (netInfo != null)
//					  netInfo.dump();
			} while ((netInfo == null || !Arrays.equals(netInfo.localIp, v8)) && line != null);

			if (reader != null)
				reader.close();

			v4 = (v4 + 1);
			if (v4 >= 3)
			{
				v4 = 0;
				v1 = (v1 + 1);
			}
		}

		return netInfo;
	}

	public static NetInfo findMatchingUdpNetInfo(byte[] serverIp, int serverPort, byte[] localIp, int localPort)
	{
		int v4 = 0;
		int v1 = 0;
		boolean udpV6;
		FastLineReader lineReader;
		byte[] ip = null;
		NetInfo netInfo = null;

		while (v1 < 2)
		{
			if (v1 != 0)
				udpV6 = !NetInfo.lastFoundOnUdpV6;
			else
				udpV6 = NetInfo.lastFoundOnUdpV6;

			if (udpV6)
				lineReader = new FastLineReader("/proc/self/net/udp6");
			else
				lineReader = new FastLineReader("/proc/self/net/udp");

			String line;
			do
			{
				line = lineReader.readLine();
				// пропускаем заголовочную строку
				if (line != null && LibNative.asciiIndexOf("sl", line) >= 0)
					line = lineReader.readLine();

				if (line != null)
				{
					if (udpV6)
						netInfo = NetInfo.parseLineUdp6(line);
					else
						netInfo = NetInfo.parseLineUdp4(line);

					if ((netInfo != null) && ((netInfo.serverPort == serverPort) && ((netInfo.localPort == localPort) &&
						(Arrays.equals(netInfo.serverIp, serverIp)))))
					{
						if (Arrays.equals(netInfo.localIp, localIp))
						{
							if (lineReader != null)
								lineReader.close();

							NetInfo.lastFoundOnUdpV6 = udpV6;
							return netInfo;
						}
						else
						{
							ip = new byte[4];
						}
					}
				}
				else
				{
					v4 = 0;
					v1 = (v1 + 1);
				}
				//netInfo = null;
				//L.w(Settings.TAG_NETINFO, "The ip is: " + ip + " and netInfo = " + netInfo);
			} while ((netInfo == null || !Arrays.equals(netInfo.localIp, ip)) && line != null);

			if (lineReader != null)
				lineReader.close();

			v4++;
			if (v4 >= 3)
			{
				v4 = 0;
				v1 = (v1 + 1);
			}
		}

		return netInfo;
	}

	static int getIntFromHexString(String string, int length, int offset)
	{
		char[] bytes = new char[length];
		string.getChars(offset, (offset + length), bytes, 0);
		int res = 0;
		int i = 0;

		while (i < length)
		{
			res = (res << 4);
			char ch = bytes[i];

			if ((ch < 48) || (ch > 57))
			{
				if ((ch < 97) || (ch > 102))
				{
					if ((ch < 65) || (ch > 70))
						throw new NumberFormatException(ch + " is invalid\nThe string is:\n" + string);
					else
						res = (res + ((ch - 65) + 10));
				}
				else
				{
					res = (res + ((ch - 97) + 10));
				}
			}
			else
			{
				res = (res + (ch - 48));
			}

			i = (i + 1);
		}

		return res;
	}

	static int getIntFromString(String string, int offset)
	{
		int i = 0;

		while (true)
		{
			int j = offset + 1;
			int k = string.charAt(offset);

			if ((k < 48) || (k > 57))
				break;

			i = i * 10 + (k - 48);
			offset = j;
		}

		return i;
	}

	static byte[] getIp4AddressFromHexString(String string, int offset)
	{
		byte[] ip = new byte[4];
		int i = NetInfo.getIntFromHexString(string, 8, offset);

		ip[3] = ((byte) (i >> 24));
		ip[2] = ((byte) (i >> 16));
		ip[1] = ((byte) (i >> 8));
		ip[0] = ((byte) i);

		return ip;
	}

	/*
	static int getIntFromString(String p4, int p5)
	{
		v2 = 0;

		while(true)
		{
			v1 = (p5 + 1);
			v0 = p4.charAt(p5);
			if ((v0 >= 48) && (v0 <= 57))
			{
				v2 = ((v2 * 10) + (v0 - 48));
				p5 = v1;
			}
		}

		return v2;
	}
	*/

	// TODO XXX wtf? when close reader and add info?!
	public static ArrayList getNetInfoList()
	{
		ArrayList<NetInfo> list = new ArrayList<NetInfo>();

		try
		{
			NetInfo info;
			FastLineReaderReverse reader = new FastLineReaderReverse("/proc/self/net/tcp6");

			do
			{
				String line = reader.readLine();

				if (line != null)
				{
					info = NetInfo.parseLineTcp6(line);
				}
				else
				{
					reader.close();
					reader = new FastLineReaderReverse("/proc/self/net/tcp");
					do
					{
						line = reader.readLine();
						if (line != null)
						{
							info = NetInfo.parseLineTcp4(line);
						}
						else
						{
							reader.close();
							return list;
						}
					}
					while (info == null);

					list.add(info);
				}
			}
			while (info == null);

			list.add(info);
		}
		catch (IOException e) { e.printStackTrace(); }

		return list;
	}

	public static String getStateStr(int p1)
	{
		switch (p1)
		{
			case 1: return "ESTABLISHED";
			case 2: return "SYN_SENT";
			case 3: return "SYN_RECV";
			case 4: return "FIN_WAIT1";
			case 5: return "FIN_WAIT2";
			case 6: return "TIME_WAIT";
			case 7: return "CLOSE";
			case 8: return "CLOSE_WAIT";
			case 9: return "LAST_ACK";
			case 10: return "LISTEN";
			case 11: return "CLOSING";
			default: return "UNKNOWN";
		}
	}

	public static NetInfo parseLineTcp4(String line)
	{
		int[] buf = new int[8];
		NetInfo info = null;

		if (NetInfo.splitBySpace(line, buf) >= 8)
		{
			info = new NetInfo(NetInfo.getIp4AddressFromHexString(line, buf[2]),
								NetInfo.getIntFromHexString(line, 4, (buf[2] + 9)),
								NetInfo.getIp4AddressFromHexString(line, buf[1]),
								NetInfo.getIntFromHexString(line, 4, (buf[1] + 9)),
								NetInfo.getIntFromHexString(line, 2, buf[3]),
								NetInfo.getIntFromString(line, buf[7]));
		}

		return info;
	}

	public static NetInfo parseLineTcp6(String line)
	{
		int[] buf = new int[8];
		NetInfo info = null;

		if (NetInfo.splitBySpace(line, buf) >= 8)
		{
			info = new NetInfo(NetInfo.getIp4AddressFromHexString(line, (buf[2] + 24)),
								NetInfo.getIntFromHexString(line, 4, (buf[2] + 33)),
								NetInfo.getIp4AddressFromHexString(line, (buf[1] + 24)),
								NetInfo.getIntFromHexString(line, 4, (buf[1] + 33)),
								NetInfo.getIntFromHexString(line, 2, buf[3]),
								NetInfo.getIntFromString(line, buf[7]));
		}

		return info;
	}

	public static NetInfo parseLineUdp4(String line)
	{
		int[] buf = new int[8];
		NetInfo info = null;

		if (NetInfo.splitBySpace(line, buf) >= 8)
		{
			info = new NetInfo(NetInfo.getIp4AddressFromHexString(line, buf[2]),
								NetInfo.getIntFromHexString(line, 4, (buf[2] + 9)),
								NetInfo.getIp4AddressFromHexString(line, buf[1]),
								NetInfo.getIntFromHexString(line, 4, (buf[1] + 9)),
								NetInfo.getIntFromHexString(line, 2, buf[3]),
								NetInfo.getIntFromString(line, buf[7]));
		}

		return info;
	}

	public static NetInfo parseLineUdp6(String line)
	{
		int[] buf = new int[8];
		NetInfo info = null;

		if (NetInfo.splitBySpace(line, buf) >= 8)
		{
			info = new NetInfo(NetInfo.getIp4AddressFromHexString(line, (buf[2] + 24)),
								NetInfo.getIntFromHexString(line, 4, (buf[2] + 33)),
								NetInfo.getIp4AddressFromHexString(line, (buf[1] + 24)),
								NetInfo.getIntFromHexString(line, 4, (buf[1] + 33)),
								NetInfo.getIntFromHexString(line, 2, buf[3]),
								NetInfo.getIntFromString(line, buf[7]));
		}

		return info;
	}

	// TODO XXX догадаться, что делает метод и починить
	private static int splitBySpace(String string, int[] res)
	{

		int length, pos, i6, resCurPos;
		boolean resHasMore;
		char[] chars;
		char ch;
		length = string.length();
		resHasMore = false;
		chars = new char[length];
		string.getChars(0, length, chars, 0);
		pos = 0;
		i6 = 0;

		label_25:
		{
			while (pos < length)
			{
				ch = chars[pos];

				label_24:
				{
					label_23:
					if (resHasMore)
					{
						if (ch != ' ')
						{
							if (ch != '\t')
								break label_23;
						}

						resHasMore = false;
						resCurPos = i6;

						break label_24;
					}
					else
					{
						if (ch != ' ')
						{
							if (ch != '\t')
							{
								resCurPos = i6 + 1;
								res[i6] = pos;

								if (resCurPos < res.length)
								{
									resHasMore = true;
									break label_24;
								}
								else
								{
									i6 = resCurPos;
									break label_25;
								}
							}
							else
							{
								resCurPos = i6;
								break label_24;
							}
						}
					}

					resCurPos = i6;
				} //end label_24:


				pos = pos + 1;
				i6 = resCurPos;
			}
		} //end label_25:

		return i6;
	}

	public static void statsReset()
	{
		netlinkErrCount = 0;
		netlinkNotFoundCount = 0;
		procRetryCount = 0;
		procNotFoundCount = 0;
	}

	public static int[] statsGetInfo()
	{
		int[] res = new int[4];
		res[0] = netlinkErrCount;
		res[1] = netlinkNotFoundCount;
		res[2] = procRetryCount;
		res[3] = procNotFoundCount;

		return res;
	}

	@Override
	public String toString()
	{
		if (Settings.DEBUG)
		{
			Object[] buf = new Object[4];
			buf[0] = Utils.ipToString(localIp, localPort);
			buf[1] = Utils.ipToString(serverIp, serverPort);
			buf[2] = getStateStr(state);
			buf[3] = uid > 0 ? Utils.concatStrings(Processes.getNamesFromUid(uid)) : "";

			//L.i(Settings.TAG_NETINFO, res);
			return String.format("src=%s, dst=%s, state=%s, pkg=%s", buf);
		}

		return "";
	}

	public void dump()
	{
		if (Settings.DEBUG) L.d(Settings.TAG_NETINFO, this.toString());
	}
}
