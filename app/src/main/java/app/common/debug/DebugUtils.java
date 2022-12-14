package app.common.debug;

import android.os.Debug;
import app.common.NetUtil;
import app.common.Utils;
import app.netfilter.IFilterVpnPolicy;
import app.netfilter.proxy.ProxyWorker;
import app.scanner.Scanner;
import app.App;
import app.ExceptionLogger;
import app.internal.Preferences;
import app.internal.Settings;
import app.ui.Toasts;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DebugUtils
{
	private static Process procDumpUplink = null; // for dumpTraffic
	private static String procDumpUplinkPid = null;
	private static Process procDumpTun = null;
	private static String procDumpTunPid = null;

	// enable/disable debug info collecting, return filepath (debug options)
	public static String switchDebugInfoCollect(final boolean enable, final boolean wait)
	{
		final String filePath = App.getContext().getFilesDir().getAbsolutePath() + "/debug.trace";
		//final String filePath = "/data/local/tmp/debug.trace";
		//try { Runtime.getRuntime().exec("chmod 0666 " + filePath); } catch (IOException ex) {}
		final String filePathPcap = filePath + ".pcap";

		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("DebugInfo Thread");
				L.a(Settings.TAG_DEBUGINFO, String.valueOf(enable));

				if (enable)
				{
					ExceptionLogger.disable();
					ProxyWorker.pktEnableDump(filePathPcap, 1048576); // packets from tun, ~1mb max
					Debug.startMethodTracing(filePath, 3145728); // methods traces, ~3mb max (lower not contains info that needed)
				}
				else
				{
					Debug.stopMethodTracing();
					ProxyWorker.pktDisableDump();
					ExceptionLogger.start();

					// append pcap data to traces
					File pcap = new File(filePathPcap);
					if (pcap.exists())
					{
						try
						{
							Process p;
							p = Runtime.getRuntime().exec(new String[] {"sh", "-c", "echo -n SPLIT_HERE >> \"" + filePath + "\""});
							try { p.waitFor(); } catch (InterruptedException e) {}
							p = Runtime.getRuntime().exec(new String[] {"sh", "-c", "cat \"" + filePathPcap + "\" >> \"" + filePath + "\""});
							try { p.waitFor(); } catch (InterruptedException e) {}
						}
						catch (IOException e) { e.printStackTrace(); }
						pcap.delete();
					}
				}
			}
		});
		t.start();

		if (wait)
			try { t.join(); } catch (InterruptedException ex) { }

		return filePath;
	}

	// disable debug (and wait until disabled)
	public static String disableDebugInfoCollect(boolean wait)
	{
		final String debugFile = switchDebugInfoCollect(false, wait);
		Preferences.putBoolean(Settings.PREF_COLLECT_DEBUGINFO, false);

		return debugFile;
	}

	// update debug DB for scanner (debug options)
	public static void updateDebugDB(final IFilterVpnPolicy policy, final boolean wait)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("Update DDB Thread");

				L.d("Options", "Updating debug DB");

				byte[] buf = null;
				if (Preferences.getDebugUrl() != null)
					buf = Utils.getData(Preferences.getDebugUrl() + Scanner.debugDBName);

				if (buf == null)
				{
					Toasts.showToast("Error loading Debug DB!");
					return;
				}

				try
				{
					String debugDbPath =
						App.getContext().getFilesDir().getAbsolutePath() + "/" + Scanner.debugDBName;
					Utils.saveFile(buf, debugDbPath);

					if (policy != null)
						policy.reload();

					Toasts.showToast("Debug DB loaded!");
				}
				catch (IOException e) { e.printStackTrace(); }
			}
		});
		t.start();

		if (wait)
			try { t.join(); } catch (InterruptedException ex) { }
	}

	// enable/disable tracing (debug options)
	public static void switchTracing(final boolean enable, final boolean big,
										final boolean wait)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("Tracing Thread");
				//Utils.sleep(2000); // wtf?

				if (enable)
				{
					ExceptionLogger.disable();
					String filePath = Utils.getWritablePath(Settings.APP_FILES_PREFIX + ".trace");
					L.a(Settings.TAG_TRACE, filePath);
					Debug.startMethodTracing(filePath, (big) ? 20000000 : 5242880);  // ~20mb (~5mb) max
					try { Runtime.getRuntime().exec("chmod 0666 " + filePath); } catch (IOException ex) {}
				}
				else
				{
					Debug.stopMethodTracing();
					ExceptionLogger.start();
				}
			}
		});
		t.start();

		if (wait)
			try { t.join(); } catch (InterruptedException ex) { }
	}

	public static void disableTracing(boolean wait)
	{
		switchTracing(false, false, wait);
	}

	// dump hprof data (debug options)
	public static void dumpHprof(final String tag, final boolean wait)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("Hprof Thread");
				//Utils.sleep(2000); // wtf?

				String filePath = Utils.getWritablePath(Settings.APP_FILES_PREFIX + tag + ".hprof");
				L.a(Settings.TAG_HPROF, filePath);
				try
				{
					Debug.dumpHprofData(filePath);
					Runtime.getRuntime().exec("chmod 0666 " + filePath);
				}
				catch (IOException ex) { }
			}
		});
		t.start();

		if (wait)
			try { t.join(); } catch (InterruptedException ex) { }
	}

	/*
	 * dump tun traffic with internal code (debug options)
	 *
	 * I/WG_DUMP (16850): /data/data/app.webguard/files/webguard.dump
	 */
	public static void dumpTunTraffic(final boolean enable, final boolean wait)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("Dump Thread");

				if (enable)
				{
					String filePath = Utils.getWritablePath(Settings.APP_FILES_PREFIX + ".dump");
					L.a(Settings.TAG_DUMP, filePath);
					ProxyWorker.pktEnableDump(filePath, 104857600); // packets from tun, ~100mb max
					try { Runtime.getRuntime().exec("chmod 0666 " + filePath); } catch (IOException ex) { }
				}
				else
				{
					ProxyWorker.pktDisableDump();
				}
			}
		});
		t.start();

		if (wait)
			try { t.join(); } catch (InterruptedException ex) { }
	}

	/*
	 * dump wlan/tun traffic with tcpdump (debug options)
	 * NOTE: need ROOT and '/data/local/tmp/tcpdump'
	 */
	public static void dumpTraffic(final boolean enable, final boolean uplink,
									final boolean wait)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("TcpDump Thread");
				//Utils.sleep(2000); // wtf?

				try
				{
					if (enable)
					{
						if ((uplink && procDumpUplink != null) || (!uplink && procDumpTun != null))
							return;

						BufferedReader out;
						Process proc;
						String cmd;
						String currentTime = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());

						if (uplink)
						{
							String ifname = (NetUtil.isMobile()) ? "rmnet0" : "wlan0";
							cmd = "/data/local/tmp/tcpdump -i " + ifname + " -p -s 0 -w /data/local/tmp/dump_" +
									currentTime + "_" + ifname;
						}
						else
						{
							cmd = "/data/local/tmp/tcpdump -i tun0 -p -s 0 -w /data/local/tmp/dump_" + currentTime + "_tun0";
						}

						proc = Runtime.getRuntime().exec(new String[] {"su", "-c", "echo $$ ; " + cmd});
						if (proc == null)
							return;

						out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
						String pid = out.readLine();
						out.close();

						if (uplink)
						{
							procDumpUplink = proc;
							procDumpUplinkPid = pid;
						}
						else
						{
							procDumpTun = proc;
							procDumpTunPid = pid;
						}
					}
					else
					{
						if ((uplink && procDumpUplink == null) || (!uplink && procDumpTun == null))
							return;

						if (uplink)
						{
							procDumpUplink.destroy();
							procDumpUplink = null;
							Runtime.getRuntime().exec(new String[] {"su", "-c", "pkill -9 -P " + procDumpUplinkPid});
							procDumpUplinkPid = null;
						}
						else
						{
							procDumpTun.destroy();
							procDumpTun = null;
							Runtime.getRuntime().exec(new String[] {"su", "-c", "pkill -9 -P " + procDumpTunPid});
							procDumpTunPid = null;
						}
					}
				}
				catch (IOException ex) {}
			}
		});
		t.start();

		if (wait)
			try { t.join(); } catch (InterruptedException ex) { }
	}
}
