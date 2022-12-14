package app.security;

import app.scanner.Scanner;
import app.scanner.ScanResult;
import app.scanner.LibScan;
import app.common.LibNative;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import app.netfilter.*;
import app.netfilter.dns.DNSAnswer;
import app.netfilter.dns.DNSResponse;
import app.common.memdata.MemoryBuffer;
import app.netfilter.proxy.TCPStateMachine;
import app.App;
import app.internal.InAppBilling;
import app.internal.Preferences;
import app.info.Statistics;
import app.netfilter.dns.DNSRequest;
import app.netfilter.http.RequestHeader;
import app.netfilter.http.ResponseHeader;
import app.netfilter.proxy.BlockIPCache;
import app.netfilter.proxy.Packet;
import app.internal.ProxyBase;
import app.common.Utils;
import app.common.debug.L;
import app.common.WiFi;
import app.common.memdata.MemoryCache;
import app.internal.Settings;
import app.ui.Dialogs;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
//import java.net.InetAddress;


public class Policy implements IFilterVpnPolicy
{
	private static final boolean CHECK_ON_DNS_REQUEST = false;
	private static final boolean DROP_BROWSERPROXY_CONNECTS = true;
	private static boolean DROP_BROWSERADS_ONLY = true;

	private static Random random = new Random(System.currentTimeMillis());
	private static Hashtable<String, PolicyRules> policies = new Hashtable<String, PolicyRules>();
	private static Scanner scanner;
	private static String forwardedFor = null;
	private static String via = null;

	private static boolean changeUserAgentAndIp;
	private static boolean desktopUserAgent;
	private static boolean allowSomeAds;
	private static boolean blockMalicious;
	private static boolean internetOnlyForBrowsers;
	private static boolean blockThirdPartyData;
	private static boolean blockAPKDownload;
	private static boolean blockSocialOther, blockSocialGPlus, blockSocialVK, blockSocialFB,
			blockSocialTwi, blockSocialOdn, blockSocialMailRu, blockSocialLinkedIn, blockSocialMoiKrug;
	private static boolean isSamsung;

	private static byte[] youtube1 = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
	private static byte[] youtube2 = {0x49, 0x73, 0x6F, 0x4D, 0x65, 0x64, 0x69, 0x61, 0x20, 0x46, 0x69, 0x6C,
			0x65, 0x20, 0x50, 0x72, 0x6F, 0x64, 0x75, 0x63, 0x65, 0x64, 0x20, 0x62,
			0x79, 0x20, 0x47, 0x6F, 0x6F, 0x67, 0x6C, 0x65};

	private static boolean proxyUsed = true;		   // changes on gui settings
	private static boolean proxyCompression = false;   // on gui
	private static boolean proxyAnonymize = false;	   // on gui
	private static boolean proxyAnonymizeApps = false; // on gui
	private static int proxyFlags = 0;

	private static AtomicInteger blockedAdsIpCount = new AtomicInteger(); // may be AtomicInteger not need?
	private static AtomicInteger blockedAdsUrlCount = new AtomicInteger();
	private static AtomicInteger blockedApkCount = new AtomicInteger();
	private static AtomicInteger blockedMalwareSiteCount = new AtomicInteger();
	private static AtomicInteger blockedPaidSiteCount = new AtomicInteger();
	private static AtomicLong	 proxyCompressionSave = new AtomicLong();

/*
	private static String iviConfig = null;

	static
	{
		try
		{
			Context context = App.getContext();
			iviConfig = Utils.getFileContents(context.getFilesDir().getAbsolutePath() + "/icj2");
			if (iviConfig == null)
				iviConfig = Utils.getAssetAsString(context, "/icj2");
		}
		catch (Exception e) { }
	}
*/

	public Policy()
	{
		load();
	}

	private static void load()
	{
		//L.d(Settings.TAG_POLICY, "Load. Context = " + activity);

		// code for firewall
		//
		//FileInputStream fis = null;
		//try
		//{
		//	  fis = new FileInputStream("inet_policy");
		//	  DataInputStream dis = new DataInputStream(fis);

		//	  while (dis.available() > 0)
		//	  {
		//		  String name = dis.readUTF();
		//		  int policy = dis.readInt();
		//		  policies.put(name, (new PolicyRules(policy)));
		//	  }
		//}
		//catch (FileNotFoundException e) { /*e.printStackTrace();*/ }
		//catch (IOException e) { e.printStackTrace(); }

		//finally
		//{
		//	  if (fis != null)
		//		  try { fis.close(); } catch (IOException e) { e.printStackTrace(); }
		//}

		// scanner
		if (scanner == null)
		{
			final boolean inited = updateScanner();
			if (!inited)
				Statistics.addLog(Settings.LOG_DB_LOAD_ERR);

			if (Settings.DEBUG) L.d(Settings.TAG_POLICY, "Scanner inited = ", Boolean.toString(inited));
		}
		if (isDebugBuild())
		{
			boolean reloaded = updateScannerDebug();

			if (Settings.DEBUG) L.d(Settings.TAG_POLICY, "Scanner debug reloaded = ", Boolean.toString(reloaded));
		}

		// samsun detect
		String manufacturer = android.os.Build.MANUFACTURER;
		if (manufacturer != null && LibNative.asciiToLower(manufacturer).indexOf("samsung") >= 0)
			isSamsung = true;
		else
			isSamsung = false;

		// update preferences
		reloadPrefs();
/*
		if (changeUserAgentAndIp)
		{
			byte[] buf = new byte[4];

			random.nextBytes(buf);
			ensureConsistentIp(buf);
			forwardedFor = (buf[0] & 0xff) + "." + (buf[1] & 0xff) + "." + (buf[2] & 0xff) + "." + (buf[3] & 0xff);

			random.nextBytes(buf);
			ensureConsistentIp(buf);
			via = (buf[3] & 0xff) + "." + (buf[2] & 0xff) + "." + (buf[1] & 0xff) + "." + (buf[0] & 0xff);
		}
*/
		//ProxyBase.updateServers(true);

		//L.d(Settings.TAG_POLICY, "blockThirdPartyData: " + blockThirdPartyData + ", blockAPKDownload: " +
		//		  blockAPKDownload + ", changeUserAgentAndIp: " + changeUserAgentAndIp);
	}

	public static boolean updateScanner()
	{
		return updateScanner(Database.getCurrentVersion());
	}

	public static boolean updateScanner(String version)
	{
		if (Policy.scanner != null)
			Policy.scanner.clean();

		String dbPath;
		if (Settings.DEBUG_NO_SCAN)
			dbPath = "/";
		else
			dbPath = App.getContext().getFilesDir().getAbsolutePath() + "/" + version + "/";

		Policy.scanner = new Scanner(dbPath, Preferences.getAppVersion());

		final boolean inited = (Settings.DEBUG_NO_SCAN) ? true : scanner.isInited();

		if (Settings.DEBUG) L.d(Settings.TAG_POLICY, "db is pro: " + scanner.isProVersion());

		// other actions

		// TODO XXX and if user use app with ssl ads? we miss!
		BlockIPCache.clear(); // domain to block may be in clean list

		return inited;
	}

	public static boolean updateScannerFast()
	{
		boolean result = false;

		if (Policy.scanner != null)
			result = Policy.scanner.reloadFast();

		BlockIPCache.clear(); // see updateScanner

		return result;
	}

	public static boolean updateScannerDebug()
	{
		boolean result = false;

		if (Policy.scanner != null)
			result = Policy.scanner.reloadDebug();

		BlockIPCache.clear(); // see updateScanner

		return result;
	}

	public static void reloadPrefs()
	{
		blockMalicious = Preferences.get(Settings.PREF_BLOCK_MALICIOUS);
		allowSomeAds = false; //Preferences.get(Settings.PREF_ALLOW_SOME_ADS);

		blockSocialOther = Preferences.get(Settings.PREF_SOCIAL_OTHER);
		blockSocialGPlus = Preferences.get(Settings.PREF_SOCIAL_GPLUS);
		blockSocialVK = Preferences.get(Settings.PREF_SOCIAL_VK);
		blockSocialFB = Preferences.get(Settings.PREF_SOCIAL_FB);
		blockSocialTwi = Preferences.get(Settings.PREF_SOCIAL_TWITTER);
		blockSocialOdn = Preferences.get(Settings.PREF_SOCIAL_OK);
		blockSocialMailRu = Preferences.get(Settings.PREF_SOCIAL_MAILRU);
		blockSocialLinkedIn = Preferences.get(Settings.PREF_SOCIAL_LINKEDIN);
		blockSocialMoiKrug = Preferences.get(Settings.PREF_SOCIAL_MOIKRUG);

		blockThirdPartyData = Preferences.get(Settings.PREF_BLOCK_TP_CONTENT);
		blockAPKDownload = Preferences.get(Settings.PREF_BLOCK_APKS);
		changeUserAgentAndIp = Preferences.get(Settings.PREF_CHANGE_USERAGENT);
		desktopUserAgent = Preferences.get(Settings.PREF_DESKTOP_USERAGENT);

		proxyCompression = Preferences.get(Settings.PREF_USE_COMPRESSION);
		proxyAnonymize = Preferences.get(Settings.PREF_ANONYMIZE);
		proxyAnonymizeApps = !Preferences.get(Settings.PREF_ANONYMIZE_ONLY_BRW); // Roman Popov // true;
		internetOnlyForBrowsers = Preferences.get(Settings.PREF_BLOCK_APPS_DATA);

		proxyFlags = 0;
		if (proxyCompression) proxyFlags |= 1;
		if (proxyAnonymize) proxyFlags |= 2;

		proxyUsed = (proxyCompression || proxyAnonymize);
		if (proxyUsed)
		{
			String country = Preferences.get_s(Settings.PREF_PROXY_COUNTRY);
			ProxyBase.setCurrentCountry(country);
		}

		String token = getUserToken(true);
		if (token != null)
		{
			// disable ad blocking everywhere if == true and APPS_EXCLUDE_BROWSERS == false
			DROP_BROWSERADS_ONLY = !Preferences.get(Settings.PREF_APP_ADBLOCK);
			if (Settings.LIC_DISABLE)
				DROP_BROWSERADS_ONLY = false;
		}
	}

//	  @Override
//	  public void update(long time)
//	  {
//		  ProxyBase.updateServers(false);
//	  }

	public static void refreshToken(boolean force)
	{
		boolean update = force;
		final InAppBilling billing = App.getBilling();

		if (!force)
			update = billing.licenseIsCheckTime();

		if (update)
		{
			//Preferences.putString(Preferences.PREF_USER_TOKEN, null);
			billing.licenseCheckAsync(false);
		}
	}

	public static void clearToken()
	{
		Preferences.clearToken();
	}

	// see getScanDataBufferSize
	//
	@Override
	public PolicyRules getPolicyForData(RequestHeader request, ByteBuffer buf)
	{
		/* not used by now
		byte[] data = buf.array();

		if (Utils.indexOf(data, youtube1, 0, 1000) >= 0)
		{
			if (Utils.indexOf(data, youtube2, 1000, 17000) >= 0)
			{
				if (DEBUG)
				{
					String url = (request != null) ? request.url : "null";
					L.d(Settings.TAG_POLICY, "AHAHA youtube url " + url);
				}

				return new PolicyRules(PolicyRules.DROP);
			}
		}
		*/

		return new PolicyRules();
	}

	private static void ensureConsistentIp(byte[] buf)
	{
		for (int i = 0; i < buf.length; i++)
		{
			if (buf[i] == 0 || (buf[i] & 255) == 255)
				buf[i] = (byte) (random.nextInt(250) + 3);
		}
	}

	// check if need to use proxy on connection with such params
	public boolean isProxyUse(byte[] serverIp, int serverPort, int uid, boolean isBrowser)
	{
		if (!(proxyUsed && (isBrowser || proxyAnonymizeApps))) // proxy disabled, not browser and proxify apps disabled
			return false;

		if (uid == App.getMyUid()) // skip webguard connects
			return false;

		if (serverIp != null)
		{
			// check for local connects (not use proxy)

			if (Utils.ip4Cmp(serverIp, Settings.LOOPBACK_IP_AR)) // 127.0.0.1
				return false;

			int b0 = Utils.unsignedByte(serverIp[0]);
			int b1 = Utils.unsignedByte(serverIp[1]);

			// 10.0.0.0 — 10.255.255.255
			// 192.168.0.0 — 192.168.255.255
			// 172.16.0.0 — 172.31.255.255
			if (b0 == 10 || (b0 == 192 && b1 == 168) || (b0 == 172 && b1 >= 16 && b1 <= 31))
				return false;
		}

		// check token and active proxy server
		String token = getUserToken(true);
		return (token != null && ProxyBase.getCurrentServer() != null);
	}

	public ProxyBase.ProxyServer getProxyHost()
	{
		//try { return new ProxyBase.ProxyServer("ru", "localhost", InetAddress.getByName(proxyHost)); }
		//catch (java.net.UnknownHostException e) { return null; }

		if (Settings.DEBUG_LOCAL_WGPROXY)
			return ProxyBase.getLocalServer();
		else
			return ProxyBase.getCurrentServer();
	}

	public int getProxyPort()
	{
		return Settings.WGPROXY_PORT;
	}

	public boolean isProxyCryptUse()
	{
		return Settings.WGPROXY_CRYPT_USE;
	}

	public static int getProxyFlags()
	{
		return proxyFlags;
	}

	/*
	 * TODO XXX Content-type может придти как "text/html; charset=utf-8", этот метод вырежет только первую часть
	 *
	 * @param type - пришедший тип из заголовка
	 * @return - очищенный тип
	 */
	private static String getContentType(String type, boolean addSemicolon)
	{
		if (type == null)
			return type;

		int pos = type.indexOf(';');
		if (pos > 0)
			return type.substring(0, ((addSemicolon) ? pos + 1 : pos)).toLowerCase();
		else if (pos == 0)
			return null;
		else
			return ((addSemicolon) ? type + ';' : type);
	}

	// use getContentType(type, true) before
	private static boolean isScannableContentType(String type)
	{
		if (type == null)
			return false;

		// TODO rewrite this
		if (("text/plain;text/html;text/xml;text/css;application/xml;application/xhtml+xml;application/rss+xml;" +
				"text/javascript;text/x-javascript;application/javascript;application/x-javascript;" +
				"text/ecmascript;text/x-ecmascript;application/ecmascript;application/x-ecmascript;" +
				"text/javascript1.0;text/javascript1.1;text/javascript1.2;text/javascript1.3;" +
				"text/javascript1.4;text/javascript1.5;text/jscript;text/livescript;").indexOf(type) >= 0)
		{
			return true;
		}

		return false;
	}

	// use getContentType(type, true) before
	private static boolean isHashableContentType(String type)
	{
		if (type == null)
			return false;

		// application/vnd.android.package-archive
		// application/zip
		// application/java-archive
		if ("application/vnd.android.package-archive;application/zip;application/java-archive;".indexOf(type) >= 0)
			return true;

		return false;
	}

	// use getContentType(type, true) before
	private static boolean isRunnableContentType(String type)
	{
		if (type == null)
			return false;

		// application/vnd.android.package-archive
		if ("application/vnd.android.package-archive;".indexOf(type) >= 0)
			return true;

		return false;
	}

	// use getContentType(type, true) before
	private static boolean isImageContentType(String type, boolean proxySupport)
	{
		if (type == null)
			return false;

		// image/jpeg
		// image/pjpeg
		// image/png
		// image/gif
		// image/bmp
		// image/svg+xml
		// image/tiff

		if (proxySupport)
		{
			// proxy support image minimize: jpeg, png
			if ("image/jpeg;image/png;".indexOf(type) >= 0)
				return true;
		}
		else
		{
			if ("image/jpeg;image/pjpeg;image/png;image/gif;image/bmp;image/svg+xml;image/tiff;".indexOf(type) >= 0)
				return true;
		}

		return false;
	}

	// use getContentType(type, true) before
	private static boolean isCompressedEncoding(String encoding)
	{
		if (encoding == null)
			return false;

		// x-gzip,gzip,x-bzip2,x-bzip
		if ("gzip;bzip2;deflate;".indexOf(encoding) >= 0)
			return true;

		return false;
	}

	public void reload()
	{
		L.a(Settings.TAG_POLICY, "Reloading...");
		load();
	}

	public void save()
	{
		// code for firewall
		//
		//FileOutputStream fos = null;
		//try
		//{
		//	  fos = new FileOutputStream("inet_policy");
		//	  DataOutputStream dos = new DataOutputStream(fos);
		//	  Enumeration<String> keys = policies.keys();
		//	  while (keys.hasMoreElements())
		//	  {
		//		  String key = keys.nextElement();
		//		  PolicyRules policy = policies.get(key);
		//		  dos.writeUTF(key);
		//		  dos.writeInt(policy.getPolicy());
		//	  }
		//}
		//catch (FileNotFoundException e) { e.printStackTrace(); }
		//catch (IOException e) { e.printStackTrace(); }

		//finally
		//{
		//	  if (fos != null)
		//		  try { fos.close(); } catch (IOException e) { e.printStackTrace(); }
		//}
	}

	/*
	public static PolicyRules getPolicy(byte[] data, byte[] servIp, int servPort, byte[] localIp, int localPort)
	{
		PolicyRules res = getPolicy(servIp, servPort);

		if (res.getPolicy() == PolicyRules.NORMAL)
		{
			NetInfo info = NetInfo.findMatchingTcpNetInfo(servIp, servPort, localIp, localPort);
			if (info != null)
			{
				res = getPolicy(info.uid);

				if (res.getPolicy() == PolicyRules.NORMAL)
				{
					res = getPolicy(data);
				}
			}
		}

		return res;
	}
	*/

	/**
	 * Check policy for Packet before other work in ProxyWorker
	 *
	 * @param packet - Packet that is coming from TUN
	 * @return PolicyRules for that packet
	 */
	public PolicyRules getPolicy(Packet packet)
	{
		return (new PolicyRules());
	}

	/**
	 * Check policy for packet before other work in ProxyWorker (before create TCPClient)
	 *
	 * @param packet - Packet that is coming from TUN
	 * @param uid	 - found uid for a process with that packet (read from /proc or kernel)
	 * @return PolicyRules for that packet
	 */
	public PolicyRules getPolicy(Packet packet, int uid)
	{
		boolean proxyTest = false;
		boolean tunTest = false;

		if (packet.dstPort == 80)
		{
			proxyTest = Utils.ip4Cmp(Settings.TEST_LOCAL_PROXY_IP_AR, packet.dstIp);
			tunTest = Utils.ip4Cmp(Settings.TEST_TUN_WORK_IP_AR, packet.dstIp);
		}

		if (!proxyTest && !tunTest)
		{
			// normal connection

			boolean isBrowser = Browsers.isBrowser(uid);

			// internet only for browsers?
			// didn't block APPS connects if no token or we block google play subscriptions
			if (internetOnlyForBrowsers && !isBrowser && uid != App.getMyUid() && getUserToken(false) != null)
				return (new PolicyRules(PolicyRules.DROP));

			// firewall block?
			// TODO XXX connections to localhost?!!!
			if (!internetOnlyForBrowsers && !Firewall.appIsAllowed(uid))
				return (new PolicyRules(PolicyRules.DROP));

			final PolicyRules res = getPolicy(packet.dstIp, packet.dstPort, uid);
			return res;
		}

		// WG internal test connections

		if (proxyTest)
		{
			// proxy detect packet (android bug with proxy app use before VPN)

			//L.d(Settings.TAG_POLICY, "<PK> ", "My UID = " + App.getMyUid(), " App UID = " + uid);

			if (App.getMyUid() == uid)
			{
				Preferences.putBoolean(Settings.PREF_PROXY_DEL_TRY, false);
			}
			else
			{
				boolean disabledAlready = Preferences.get(Settings.PREF_PROXY_DEL_TRY); // try to del wifi proxy?
				if (Settings.DEBUG)
					L.e(Settings.TAG_POLICY, "<PXY> ", "Found uid = ", Integer.toString(uid), " my uid = ", Integer.toString(App.getMyUid()));

				if (!disabledAlready)
				{
					Preferences.putBoolean(Settings.PREF_PROXY_DEL_TRY, true);
					Statistics.addLog(Settings.LOG_LOCAL_PROXY_DELETE);

					WiFi.unsetWifiProxySettings(App.getContext()); // network will be reconnected
				}
				else
				{
					//Preferences.putBoolean(Settings.PREF_PROXY_DEL_TRY, false);
					Statistics.addLog(Settings.LOG_LOCAL_PROXY_DELETE_ERR);

					App.disable();

					Context c = App.getContext();
					PackageManager pm = c.getPackageManager();

					String[] packages = Processes.getNamesFromUid(uid);
					String pkgName = null;
					if (packages != null && packages.length > 0)
					{
						try
						{
							ApplicationInfo info = pm.getApplicationInfo(packages[0], 0);
							//pkgName = pm.getApplicationLabel(info);
							pkgName = info.name;

							if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_LOCAL_PROXY_APP + pkgName);
						}
						catch (PackageManager.NameNotFoundException e)
						{
							e.printStackTrace();

							//message = "";
							pkgName = packages[0];
						}

					}

					Dialogs.showProxyDeleteError(null, pkgName);
				}
			}
		}
		else
		{
			// tun work test

			int[] netClientsCounts = new int[2];
			netClientsCounts[0] = 1; // tcp
			netClientsCounts[1] = 0; // udp
			Statistics.updateStats(netClientsCounts, null, null);
		}

		return (new PolicyRules(PolicyRules.DROP));
	}

	public PolicyRules getPolicy(byte[] servIp, int servPort, int uid)
	{
		boolean isBrowser = Browsers.isBrowser(uid);

		// TODO XXX check only first name now
		String packageNames[] = Processes.getNamesFromUid(uid);
		String pkgname = null;
		if (packageNames != null && packageNames.length > 0)
			pkgname = packageNames[0];

		Statistics.addAppInet(pkgname);

		//
		if (Settings.DEBUG_ALLOW_APP)
		{
			if (!Settings.DEBUG_ALLOW_APP_NAME.equals(pkgname) && App.getMyUid() != uid)
				return (new PolicyRules(PolicyRules.DROP));
		}
		if (Settings.DEBUG_DROP_WG)
		{
			if (App.getMyUid() == uid)
				return (new PolicyRules(PolicyRules.DROP));
		}

		PolicyRules res = getPolicy(servIp, servPort, isBrowser, pkgname);

/*
 * code for firewall
 *
		if (res.getPolicy() == PolicyRules.NORMAL)
		{
			if (packageNames != null && packageNames.length > 0)
			{
				for (String packageName : packageNames)
				{
					if (policies.containsKey(packageName))
					{
						res = policies.get(packageName);
						break;
					}
				}
			}
		}
*/
		//if (Settings.DEBUG_POLICY) L.a(Settings.TAG_POLICY, "<IPU> ", Utils.ipToString(servIp, servPort) + " -> " + res.toString());

		return res;
	}

	/*
	 * TODO XXX use one pkgname but for one uid can be several packages (browser plugins)
	 */
	public PolicyRules getPolicy(byte[] servIp, int servPort, boolean isBrowser, String pkgname)
	{
		if (!Settings.APPS_EXCLUDE_BROWSERS)
		{
			// don't block by ip if no separate ad blocking and apps blocking disabled
			// TODO XXX check ip verdict and drop only ad block
			if (DROP_BROWSERADS_ONLY)
				return (new PolicyRules());
		}

//		  if (proxyUsed && !ProxyBase.isReady() && servPort != 53)
//			  return new PolicyRules(PolicyRules.DROP);

		// block ads only in browsers? (with this check browsers may show ssl ads!)
		if (!isBrowser && DROP_BROWSERADS_ONLY)
			return (new PolicyRules());
//		  else if ("adbd".equals(pkgname)) return (new PolicyRules()); // PROFILE

		// android browser bug workaround (on some sites back command not work if block ip connects)
		// also need to disable DNS_NO_ADS_IP
//		  else if (isBrowser && "com.android.browser".equals(pkgname)) // && isSamsung
//			  return (new PolicyRules());

		// This is for ads blocking with hosts, to drop connection before trying
		if (Settings.LOOPBACK_DROP_CONNECTS)
		{
			if (Utils.ip4Cmp(servIp, Settings.LOOPBACK_IP_AR))
				return (new PolicyRules(PolicyRules.DROP));
		}

		PolicyRules res = new PolicyRules();

		PolicyRules tmp = BlockIPCache.getPolicy(servIp);
		if (tmp != null)
		{
			//boolean compressed = tmp.hasPolicy(PolicyRules.COMPRESSED);
			int recordType = tmp.getVerdict();

			if (DROP_BROWSERPROXY_CONNECTS && servPort == 443 && tmp.hasPolicy(PolicyRules.COMPRESSED))
				tmp.addPolicy(PolicyRules.DROP);

			if (isBrowser)
			{
				// do not block browser access to ip with normal or third party ads
				if (!((allowSomeAds &&
						recordType == LibScan.RECORD_TYPE_ADS_OK) || recordType == LibScan.RECORD_TYPE_ADS_TPARTY))
				{
					res = tmp;
				}
			}
			else
			{
				// block not browsers access to any ip
				// TODO XXX if ADS_OK or NOTIFY???
				res = tmp;
			}

			// stats
			if (res.hasPolicy(PolicyRules.DROP))
			{
				if (LibScan.recordTypeIsAds(recordType))
					blockedAdsIpCount.incrementAndGet();
				else if (LibScan.recordTypeIsDangerous(recordType))
					blockedMalwareSiteCount.incrementAndGet();
			}
		}

		if (Settings.DEBUG_POLICY) L.a(Settings.TAG_POLICY, "<IPB> ", Utils.ipToString(servIp, servPort) + " -> " + res.toString());

		return res;
	}

	public PolicyRules getPolicy(int uid)
	{
		PolicyRules res = new PolicyRules();
/*
 * code for firewall
 *
		String packageNames[] = LibNative.getNamesFromUid(uid);

		if (packageNames != null && packageNames.length > 0)
		{
			for (String packageName : packageNames)
			{
				if (policies.containsKey(packageName))
				{
					res = policies.get(packageName);
					break;
				}
			}
		}
*/
		return res;
	}

	/*
	 * check policy for request
	 *
	 * order:
	 * - get policy for url + referer and if have policy (!= NORMAL) return it
	 * - process 'paranoid' private mode
	 * - check for .apk request
	 *
	 * TODO XXX use one pkgname but for one uid can be several packages (browser plugins)
	 * TODO XXX add cache for last request
	 */
	public PolicyRules getPolicy(RequestHeader request, int uid, String pkgname, boolean isBrowser)
	{
		PolicyRules rules = new PolicyRules();

		if (!request.isHttp())
		{
			return rules;
		}
		else if (request.isPartial())
		{
			rules.addPolicy(PolicyRules.WAIT);
			return rules;
		}

		String url = request.getUrl();

		if (Settings.DEBUG_YOUTUBE) {
			if ((pkgname != null && LibNative.asciiToLower(pkgname).indexOf("youtube") >= 0) ||
					url.indexOf(".googlevideo.com/") >= 0) {
				L.a(Settings.TAG_POLICY, "<YouTube> ", url);
			}
		}

//		  if (url.indexOf("/popads/") >= 0) // DEBUG
//			  return new PolicyRules(PolicyRules.DROP);

		do
		{
			// check properties
			if (url == null)
				break;

//			  if (url.indexOf("googleads.g.doubleclick.net") >= 0) // DEBUG
//				  uid = (uid == 0) ? 0 : uid;

			// for applications domain and referer always not equal
			boolean isSameDomain = false;
			final String referer_http = request.referer;
			String referer = null;
			if (isBrowser)
			{
				if (referer_http == null || referer_http.isEmpty())
				{
					isSameDomain = true;
					// some records need referrer! see 1700001
					// TODO XXX if no host also?
					if (request.host != null && !request.host.isEmpty())
						referer = Utils.getMainDomain(request.host);
				}
				else
				{
					referer = Utils.getMainDomain(Utils.getDomain(referer_http));
					isSameDomain = request.isSameDomain(referer);
				}
			}
			else
			{
				referer = pkgname;
			}

			String name = request.getFilename();
			boolean isApk = (name != null && LibNative.asciiEndsWith(".apk", LibNative.asciiToLower(name))); // TODO XXX test with russian apk name
			boolean urlBlocked = false;

			//L.d(Settings.TAG_POLICY, "referrer: ", request.referer, " isSameDomain: ", Boolean.toString(isSameDomain) + " uid: " + uid);

			// scan if browser or block any apps ads
			if (isBrowser || !DROP_BROWSERADS_ONLY)
			{
				//
				PolicyRules rules0 = getPolicy(url, referer, isSameDomain, referer_http);
				int recordType = rules0.getVerdict();

				if (!Settings.APPS_EXCLUDE_BROWSERS)
				{
					// reset ad block in browsers if no separate ad blocking and apps blocking disabled
					if (DROP_BROWSERADS_ONLY && LibScan.recordTypeIsAds(recordType))
						rules0 = new PolicyRules();
				}

				if (rules0.hasPolicy(PolicyRules.DROP))
					urlBlocked = true;

				//L.d(Settings.TAG_POLICY, "rules: ", rules.toString());

				if (rules0.hasPolicy(PolicyRules.NOTIFY_SERVER)) // TODO remove from here
					Statistics.addUrl(uid, isBrowser, url, referer_http, -1, rules0.getRecords()); // TODO add pkgname

				if (!isBrowser && urlBlocked && LibScan.recordTypeIsSocial(recordType))
					// don't block social networks in applications
					// TODO XXX exclude official applications in db instead of this
					rules0 = null;

				if (isBrowser && ((blockThirdPartyData && !isSameDomain) || (isApk && blockAPKDownload)))
					// block all requests to other sites + block apk from browsers
					rules0 = new PolicyRules(PolicyRules.DROP);

				if (rules0 != null)
				{
					rules = rules0;

					// stats
					if (rules.hasPolicy(PolicyRules.DROP))
					{
						if (isApk)
							blockedApkCount.incrementAndGet();
						else if (LibScan.recordTypeIsAds(recordType))
							blockedAdsUrlCount.incrementAndGet();
						else if (LibScan.recordTypeIsDangerous(recordType))
							blockedMalwareSiteCount.incrementAndGet();
					}
				}
			}

			// apk statistics
			// statistics on AV blocked downloads or from exceptions not needed
			if (isApk && !urlBlocked && !Exceptions.exceptFromStats(url))
				Statistics.addUrl(uid, isBrowser, url, referer_http, Statistics.FILE_TYPE_ZIP, null);
		}
		while (false);

		//if (request.isPartial())
		//	  rules.addPolicy(PolicyRules.WAIT);

		if (Settings.DEBUG_POLICY) L.a(Settings.TAG_POLICY, "<REQ> ", "'" + url + "' -> " + rules.toString());

		return rules;
	}

	// return minimal buffer data size before send data to client
	@Override
	public int getScanDataBufferSize(RequestHeader requestHeader, ResponseHeader responseHeader)
	{
		//if (responseHeader.responseCode == 200 && LibNative.asciiIndexOf(".googlevideo.com/videoplayback?", requestHeader.getUrl()) >= 0)
		//	  return 17000;
		//else
		return 0;
	}

	@Override
	public boolean isBrowser(String[] packs)
	{
		return Browsers.isBrowser(packs);
	}

	@Override
	public boolean isBrowser(int uid)
	{
		return Browsers.isBrowser(uid);
	}

	/*
	public static PolicyRules getPolicy(byte[] data)
	{
		PolicyRules res = new PolicyRules(PolicyRules.NORMAL);

		String line;
		boolean resultOk = false;
		FastLineReader reader = new FastLineReader(data, 0);

		while ((line = reader.readLine()) != null)
		{
			if (!resultOk)
			{
				if (LibNative.asciiIndexOf("200 OK", line) >= 0)
					resultOk = true;
				else
					break;
			}

			final int len = line.length();
			if (LibNative.asciiStartsWith("Content-Type:", line) && len > 13)
			{
//				  if (isScannableContentType(line.substring(13).trim()))
//					  res = SCAN;
				if (isHashableContentType(line.substring(13).trim()))
					res = new PolicyRules(PolicyRules.HASH);
			}
			else if (LibNative.asciiStartsWith("Content-Disposition:", line))
			{
				if (LibNative.asciiEndsWith(".apk", line))
					res = new PolicyRules(PolicyRules.HASH);
			}
		}
		return res;
	}
	*/

	@Override
	public void addRequestHeaders(RequestHeader header)
	{
		if (changeUserAgentAndIp && proxyFlags == 0)
		{
			// emulate browser through proxy and add generated proxy IP

			if (forwardedFor != null)
				header.addHeader("X-Forwarded-For: " + forwardedFor);
			//sb.append("X-Forwarded-For: 192.168.1.101\r\n");

			if (via != null)
				header.addHeader("Via: " + via);
		}
	}

	@Override
	public boolean changeUserAgent()
	{
		return (changeUserAgentAndIp || desktopUserAgent);
	}

	@Override
	public boolean needToAddHeaders()
	{
		return (changeUserAgentAndIp || desktopUserAgent);
	}

	@Override
	public boolean changeReferer()
	{
		//return changeUserAgentAndIp;
		return false;
	}

	@Override
	public void scan(MemoryBuffer buffer, TCPStateMachine tcpStateMachine)
	{
		//Scanner.scan(buffer, tcpStateMachine);
		MemoryCache.release(buffer); // TODO XXX move from here
	}

	@Override
	public String getUserAgent()
	{
		String ua = null;

		if (desktopUserAgent && !changeUserAgentAndIp)
			ua = UserAgents.getAgentDesktop();
		else if (changeUserAgentAndIp)
			ua = UserAgents.getAgent(desktopUserAgent);

		return ua;
	}

	/*
	private boolean isMarketUrl(String url)
	{
		if (url != null && LibNative.asciiStartsWith("http://", url))
		{
			final int slashPos = url.indexOf('/', 7);
			if (slashPos > 0 && url.length() > 7)
				url = url.substring(7, slashPos);

			if (LibNative.asciiEndsWith("android.clients.google.com", url))
				return true;
		}

		return false;
	}
	*/

	/*
	 * Анализ запроса и ответа при ответе сервера
	 *
	 * @param request	- запрос к серверу, разбитый на нужные поля
	 * @param response	- ответ от сервера, разбитый по заголовкам
	 * @param data		- ответ от сервера целиком, может содержать только заголовок, или даже кусок файла
	 * @param isBrowser - браузер это, или нет
	 * @return - возвращает политику дальнейших действий с данным соединением
	 */
	public PolicyRules getPolicy(RequestHeader request, ResponseHeader response, byte[] data,
									int uid, boolean isBrowser, boolean isProxyUsed)
	{
		PolicyRules res = new PolicyRules();

		if (request == null || response == null)
			return res;

		if (response.responseCode != 200)
			return res;

		// check for android application
		final String type = getContentType(response.contentType, true);
		final String name = response.fileName;
		final String encoding = getContentType(response.contentEncoding, true);

		// check for archive
		boolean isArch = false;
		boolean isExe = false;
		boolean isApk = (isRunnableContentType(type) ||
							(name != null && LibNative.asciiEndsWith(".apk", LibNative.asciiToLower(name))));

		if (!isApk && !isCompressedEncoding(encoding) && !isScannableContentType(type))
		{
			int binaryType = LibScan.binaryDataSearchType(data, Math.min(1024, data.length));
			if (LibScan.binaryTypeIsArchive(binaryType) || LibScan.binaryTypeIsExecutable(binaryType))
			{
				//L.i(Settings.TAG_POLICY, "<RES> ", LibScan.binaryTypeToString(binaryType) + " " + request.getUrl() + " " + name);

				//if (binaryType != LibScan.BINARY_TYPE_GZIP && binaryType != LibScan.BINARY_TYPE_ZIP)
				//if (binaryType != LibScan.BINARY_TYPE_GZIP && binaryType != LibScan.BINARY_TYPE_ZIP &&
				//	  binaryType != LibScan.BINARY_TYPE_BZIP2)
				isArch = true;

				if (LibScan.binaryTypeIsExecutable(binaryType))
					isExe = true;
			}

			// fake counter of traffic saved by proxy
			if (isProxyUsed && proxyCompression && isImageContentType(type, true))
				proxyCompressionSave.addAndGet(response.contentLength);
		}

		// statistics + block apk
		final String url = request.getUrl();
		if (url != null)
		{
			if (isApk || isArch)
			{
				if (!Exceptions.exceptFromStats(url))
				{
					int stype = (isExe) ? Statistics.FILE_TYPE_EXE : ((isApk) ? Statistics.FILE_TYPE_ZIP : Statistics.FILE_TYPE_ARC);
					Statistics.addUrl(uid, isBrowser, url, request.referer, stype, null);
				}

				if (isBrowser && isApk && blockAPKDownload)
				{
					res = new PolicyRules(PolicyRules.DROP);
					blockedApkCount.incrementAndGet();
				}
			}
		}

		if (Settings.DEBUG_POLICY) L.a(Settings.TAG_POLICY, "<RES> ", "'" + url + "' -> " + res.toString());

		return res;
	}

	/*
	 * get domain policy on DNS response, return policy and verdict
	 *
	 * check this function with resolving domains:
	 *
	 * gld.push.samsungosp.com -> alias to many lb-gld-777274664.eu-west-1.elb.amazonaws.com ip
	 * cdn-tags.brainient.com -> alias to cdn-tags-brainient.global.ssl.fastly.net -> alias to global-ssl.fastly.net ->
	 *	   alias to fallback.global-ssl.fastly.net ip
	 *
	 * TODO XXX add ip scan
	 * TODO XXX incorrect work with several domains mixed with aliases
	 * TODO XXX add cache for last request
	 */
	public PolicyRules getPolicy(DNSResponse resp)
	{
		ArrayList<DNSAnswer> answers = resp.getAnswers();
		if (answers == null || answers.isEmpty())
			return null;

		String last = "";
		String main = null;
		String alias = null;
		PolicyRules rules = null;

		//L.a(Settings.TAG_POLICY, "domain answers");

		for (DNSAnswer answer : answers)
		{
			if (answer == null || answer.domain == null)
				continue;

			final String domain = answer.domain;
			final String cname = answer.cname;
			final byte[] ip = answer.ip;

			if (Settings.DEBUG_POLICY)
				L.a(Settings.TAG_POLICY, "<DIP> ", "'" + answer.domain + "' (" + main + ") " + Utils.ipToString(answer.ip, 0) +
						" " + Integer.toString(answer.ttl));

//				  if (answer.domain.indexOf("tpc.googlesyndication.com") >= 0) // DEBUG
//					  alias = (alias == null) ? null : alias;

			if (domain.equals(last))
			{
				// skip same domain scan

				if (ip == null)
					continue;
				else if (rules != null && rules.verdict != LibScan.RECORD_TYPE_WHITE)
					BlockIPCache.addIp((main == null) ? domain : main, ip, answer.ttl, rules);
				else
					BlockIPCache.addCleanIp(ip);

				continue;
			}

			// 'gld.push.samsungosp.com' (null) null 300 ->
			//	   main,last = gld.push.samsungosp.com, alias = lb-gld-777274664.eu-west-1.elb.amazonaws.com
			// 'lb-gld-777274664.eu-west-1.elb.amazonaws.com' (gld.push.samsungosp.com) 46.137.87.239 300 ->
			//	   last = lb-gld-777274664.eu-west-1.elb.amazonaws.com

			// 'cdn-tags.brainient.com' (null) null 300 ->
			//	   main,last = cdn-tags.brainient.com, alias = cdn-tags-brainient.global.ssl.fastly.net
			// 'cdn-tags-brainient.global.ssl.fastly.net' (cdn-tags.brainient.com) null 21571 ->
			//	   last = cdn-tags-brainient.global.ssl.fastly.net, alias = global-ssl.fastly.net
			// 'global-ssl.fastly.net' (cdn-tags.brainient.com) null 300 ->
			//	   last = global-ssl.fastly.net, alias = fallback.global-ssl.fastly.net
			// 'fallback.global-ssl.fastly.net' (cdn-tags.brainient.com) 185.31.17.249 300 ->
			//	   last = fallback.global-ssl.fastly.net

			last = domain;
			if (!domain.equals(alias))
			{
				// domain switch (new domain, not alias to previous domains)
				main = domain;
				rules = null;
				alias = null;
				//L.a(Settings.TAG_POLICY, "domain switch");
			}
			if (cname != null)
				alias = cname;

			// scan

			PolicyRules this_rules = null;
			if (rules != null)
				this_rules = rules; // skip scan if upper domain detected (even if TPARTY ads and not scan for WHITE)
			else
				this_rules = getPolicy(domain);

			if (this_rules.hasPolicy(PolicyRules.DROP) || this_rules.hasPolicy(PolicyRules.COMPRESSED))
			{
				rules = this_rules;
				if (ip != null)
					BlockIPCache.addIp((main == null) ? domain : main, ip, answer.ttl, rules);
			}
			else
			{
				if (this_rules.verdict == LibScan.RECORD_TYPE_WHITE)
					rules = this_rules;
				if (ip != null)
					BlockIPCache.addCleanIp(ip);
			}

			//L.i(Settings.TAG_POLICY, "<DnsRes> ", answer.domain);
		}

		if (Settings.DNS_NO_ADS_IP)
		{
			//if (!Settings.APPS_EXCLUDE_BROWSERS)

			if (!DROP_BROWSERADS_ONLY && rules != null &&
				(rules.verdict == LibScan.RECORD_TYPE_ADS ||
				rules.verdict == LibScan.RECORD_TYPE_FRAUD ||
				rules.verdict == LibScan.RECORD_TYPE_MALWARE))
			{
				// return rules to drop answers from dns response
				return rules;
			}
		}

		return null;
	}

	/*
	 * get domain policy on DNS request, return policy and verdict
	 * on dns request block only ads (without third party and ok domains) or
	 *	   dangerous (fraud/malware) domains
	 *
	 * return policy NORMAL and type CLEAN on normal domains
	 *
	 * TODO XXX what to do on different domains request ?!
	 * TODO XXX request to compression proxy
	 */
	public PolicyRules getPolicy(DNSRequest req)
	{
		if (!CHECK_ON_DNS_REQUEST)
			return (new PolicyRules());

		ScanResult sc = new ScanResult();

		if (req.domains != null && req.flags == 0x0100)
		{
			int i = -1;
			for (String domain : req.domains)
			{
				i++;
				if (domain == null)
					continue;

				if (i > 0 && req.domains[i - 1] != null && req.domains[i - 1].equals(domain))
					continue;

				int recordType = Policy.scanner.scanDomain(domain);
				sc.addType(recordType);
			}
		}

		// convert scan result + preferences to policy
		int policy = PolicyRules.NORMAL;
		int recordType = LibScan.RECORD_TYPE_CLEAN;

		if (!sc.hasType(LibScan.RECORD_TYPE_WHITE))
		{
			if (sc.hasAds())
			{
				if (sc.hasType(LibScan.RECORD_TYPE_ADS_OK) && !allowSomeAds)
				{
					policy = PolicyRules.DROP;
					recordType = LibScan.RECORD_TYPE_ADS_OK;
				}
				else
				{
					if (sc.hasType(LibScan.RECORD_TYPE_ADS))
					{
						policy = PolicyRules.DROP;
						recordType = LibScan.RECORD_TYPE_ADS;
					}
				}
			}

			if (blockMalicious && policy == PolicyRules.NORMAL && sc.hasDangerous())
			{
				policy = PolicyRules.DROP | PolicyRules.NOTIFY_USER;
				recordType = sc.getMajorType(); // XXX
			}
		}

		PolicyRules rules = new PolicyRules(policy, recordType);

		if (Settings.DEBUG_POLICY)
		{
			String tmp = (req.domains != null) ? req.domains.toString() : "";
			L.a(Settings.TAG_POLICY, "<DNS> ", tmp + " -> " + sc.toString() + " " + rules.toString());
		}

		return rules;
	}

	/*
	 * get domain policy, return policy and verdict
	 *
	 * copy/paste from getPolicy(DNSRequest req)
	 * see getPolicy(DNSResponse resp)
	 */
	public PolicyRules getPolicy(String domain)
	{
		ScanResult sc = new ScanResult();
		int recordType = Policy.scanner.scanDomain(domain);
		sc.addType(recordType);

		// convert scan result + preferences to policy
		int policy = PolicyRules.NORMAL;
		recordType = LibScan.RECORD_TYPE_CLEAN;

		if (!sc.hasType(LibScan.RECORD_TYPE_WHITE))
		{
			if (sc.hasAds())
			{
				policy = PolicyRules.DROP;
				recordType = sc.getMajorType();
			}
			else if (blockMalicious && sc.hasDangerous())
			{
				policy = PolicyRules.DROP | PolicyRules.NOTIFY_USER;
				recordType = sc.getMajorType();
			}
		}

		PolicyRules rules = new PolicyRules(policy, recordType);

		if (Exceptions.isCompressed(domain)/*"proxy.googlezip.net".equals(domain) || "compress.googlezip.net".equals(domain)*/)
			rules.addPolicy(PolicyRules.COMPRESSED);

		if (Settings.DEBUG_POLICY) L.a(Settings.TAG_POLICY, "<DNM> ", domain + " -> " + sc.toString() + " " + rules.toString());

		return rules;
	}

	/*
	 * get url policy on HTTP request
	 *
	 * return policy NORMAL and type CLEAN on normal urls
	 *
	 * NOTE pass as referer main domain (if request from browser) or app package name
	 *
	 * TODO add social networks settings check
	 * TODO add server notify on 'regexp' records detect
	 */
	public PolicyRules getPolicy(String url, String referer, boolean isSameDomain, String referer_http)
	{
		//L.e(Settings.TAG_POLICY, "<URL> ", "Url: " + url + "\t" + isSameDomain);

		ScanResult sc = Policy.scanner.scanUrl(url, referer);

		// convert scan result + preferences to policy

		int policy = PolicyRules.NORMAL;
		int recordType = LibScan.RECORD_TYPE_CLEAN;
		String redirect = null;

		boolean haveTparty = sc.hasType(LibScan.RECORD_TYPE_ADS_TPARTY);
		boolean tparty = (isSameDomain && haveTparty);

		while (!sc.hasType(LibScan.RECORD_TYPE_WHITE))
		{
			// first check for silent verdicts (ADS, SOCIAL) then for verdicts with notification

			if (!tparty && sc.hasAds() &&
				(!allowSomeAds || (allowSomeAds && !sc.hasType(LibScan.RECORD_TYPE_ADS_OK))))
			{
				// ads (but allow to open urls with ADS_TPARTY detect in new tab)

				policy = PolicyRules.DROP;
				recordType = LibScan.RECORD_TYPE_ADS;
				if (haveTparty)
					redirect = url; // see PolicyRules

				break;
			}

			if (!isSameDomain && sc.hasSocial())
			{
				// social (but allow to open urls in new tab)

				if		(blockSocialOther	 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_OTHER))	recordType = LibScan.RECORD_TYPE_SOCIAL_OTHER;
				else if (blockSocialGPlus	 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_GPLUS))	recordType = LibScan.RECORD_TYPE_SOCIAL_GPLUS;
				else if (blockSocialVK		 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_VK))		recordType = LibScan.RECORD_TYPE_SOCIAL_VK;
				else if (blockSocialFB		 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_FB))		recordType = LibScan.RECORD_TYPE_SOCIAL_FB;
				else if (blockSocialTwi		 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_TWI))		 recordType = LibScan.RECORD_TYPE_SOCIAL_TWI;
				else if (blockSocialOdn		 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_ODNKLASS)) recordType = LibScan.RECORD_TYPE_SOCIAL_ODNKLASS;
				else if (blockSocialMailRu	 && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_MAILRU))	 recordType = LibScan.RECORD_TYPE_SOCIAL_MAILRU;
				else if (blockSocialLinkedIn && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_LINKEDIN)) recordType = LibScan.RECORD_TYPE_SOCIAL_LINKEDIN;
				else if (blockSocialMoiKrug  && sc.hasType(LibScan.RECORD_TYPE_SOCIAL_MOIKRUG))  recordType = LibScan.RECORD_TYPE_SOCIAL_MOIKRUG;

				if (LibScan.recordTypeIsSocial(recordType))
				{
					policy = PolicyRules.DROP;
					redirect = url; // see PolicyRules
				}

				break;
			}

			if (blockMalicious && sc.hasDangerous())
			{
				// malware

				policy = PolicyRules.DROP | PolicyRules.NOTIFY_USER;
				recordType = sc.getMajorType(); // WHITE > MALWARE > FRAUD > all other

				break;
			}

			if (sc.hasType(LibScan.RECORD_TYPE_CHARGEABLE) &&
				(!isSameDomain || referer_http == null || referer_http.isEmpty()))
			{
				// chargeable (skip notification if user already on site)

				policy = PolicyRules.NOTIFY_USER;
				recordType = LibScan.RECORD_TYPE_CHARGEABLE;

				break;
			}

			break;
		} // while

		if (sc.hasType(LibScan.RECORD_TYPE_TEST))
			policy |= PolicyRules.NOTIFY_SERVER;

		// +signature

		PolicyRules rules = new PolicyRules(policy, recordType, sc.getIds(), redirect);

		if (Settings.DEBUG_POLICY)
		{
			// TODO XXX check proguard cutoff (false || false)
			String tmp = (referer != null) ? "'" + referer + "'" : "''";
			tmp += (referer_http != null) ? " '" + referer_http + "'" : " ''";
			tmp += " (" + isSameDomain + ", " + tparty + ")";

			L.a(Settings.TAG_POLICY, "<URL> ", "'" + url + "' " + tmp + " -> " + sc.toString() + " " + rules.toString());
		}

		return rules;
	}

	public static String getUserToken(boolean check)
	{
		if (Settings.DEBUG_NOTOKEN)
			return null;

		String id;
		if (Settings.LIC_DISABLE)
			id = "id______________________________";
		else
			id = Preferences.get_s(Settings.PREF_USER_TOKEN);

		// 32 - GP token, 36 - our free token
		if (check && id != null && !(id.length() == 32 || id.length() == 36))
		{
			L.w(Settings.TAG_POLICY, "invalid token");
			return null;
		}

		return id;
	}

	// check if token is free
	public static boolean isFreeToken(String token)
	{
		if (Settings.DEBUG_NOFREESUBS)
			return false;

		if (token == null || token.length() != 36 || !token.startsWith("free"))
			return false;

		return true;
	}

	// return true if user use old free version of WebGuard
	public static boolean isFreeUser()
	{
		if (!Settings.LIC_FREEUSER)
			return false;

		int recovery = Preferences.get_i(Settings.PREF_RECOVERY_STATUS);
		if (recovery == 1)
			return true;

		return false;
	}

	public static boolean isDebugBuild()
	{
		return (Preferences.getPublisher().equals("debug"));
	}

	public static void statsReset()
	{
		blockedAdsIpCount.set(0);
		blockedAdsUrlCount.set(0);
		blockedApkCount.set(0);
		blockedMalwareSiteCount.set(0);
		blockedPaidSiteCount.set(0);
		proxyCompressionSave.set(0);
	}

	public static long[] statsGetInfo()
	{
		long[] res = new long[6];

		res[0] = blockedAdsIpCount.get();
		res[1] = blockedAdsUrlCount.get();
		res[2] = blockedApkCount.get();
		res[3] = blockedMalwareSiteCount.get();
		res[4] = blockedPaidSiteCount.get();
		res[5] = proxyCompressionSave.get();

		return res;
	}

	// update chargeable detects only when show notification
	public static void statsUpdateChargeable()
	{
		blockedPaidSiteCount.incrementAndGet();
	}

/*
	// ivi

	public static int iviCheckUrl(RequestHeader request)
	{
		if (!request.isHttp())
			return 0;

		String url = request.getUrl();
		if (url == null)
			return 0;

		if (LibNative.asciiStartsWith(url, "http://cdn-tags.brainient.com"))
		{
			if (LibNative.asciiEndsWith("/config.json", url))
				return 1;
			else if (LibNative.asciiEndsWith("/superpuper.mp4", url))
				return 2;
		}

		return 0;
	}

	public static String iviGetConfig()
	{
		return iviConfig;
	}
*/
}
