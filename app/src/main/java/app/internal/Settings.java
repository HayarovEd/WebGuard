package app.internal;

/*
 * main logs enable: DEBUG
 * full logs enable: DEBUG_STATE, DEBUG_NET, DEBUG_TCP, DEBUG_HTTP, DEBUG_DNS, DEBUG_POLICY, DEBUG_WGPROXY
 * get logs:
 *	   adb logcat dump | grep WG_
 *	   adb pull /data/data/app.webguard/files/webguard.log (if DEBUG_WRITE)
 *
 * also from debug options:
 *	   webguard.dump, webguard.hprof, webguardvpn.hprof, webguardvpn_gc.hprof, webguard.trace
 *
 * log and profiling data write in tmp, run:
 *	   adb shell chmod 0777 /data/local/tmp/
 *
 * get copy of WG from device:
 *	   adb backup -f backup.ab -noapk app.webguard
 *	   ( printf "\x1f\x8b\x08\x00\x00\x00\x00\x00" ; tail -c +25 backup.ab ) | tar xfvz -
 *
 * NOTE! when change flags value here remove bin directory
 *	   because rebuild with ant sometimes not change values (caches?)
 */

public class Settings
{
	// see L
	public static final boolean DEBUG				= false;

	// see Policy
	public static final boolean DEBUG_POLICY		= false; // dump policy info
	// see RequestHeader, TCPStateMachine
	public static final boolean DEBUG_HTTP			= false; // debug http parsing
	// see DNSRequest, DNSResponse
	public static final boolean DEBUG_DNS			= false; // debug dns parsing
	// see TCPStateMachine, TCPClient, TunReadThread
	public static final boolean DEBUG_NET			= false; // debug of our network (connections) processing
	public static final boolean DEBUG_WGPROXY		= false; // debug of our proxy working
	public static final boolean DEBUG_TCP			= false; // debug TCPClient states
	// see FilterVpnService
	public static final boolean DEBUG_STATE			= false; // debug network (uplink) states

	//

	public static final boolean DEBUG_WRITE			= false; // dump logs to file
	public static final boolean DEBUG_BT			= false; // append to log messages backtraces
	public static final boolean DEBUG_PKT_DUMP		= false; // dump all tun traffic

	//

	public static final boolean DEBUG_ALLOW_APP		= false; // allow internet only for single app
	public static final String	DEBUG_ALLOW_APP_NAME = "com.android.browser";

	public static final boolean DEBUG_WAIT_ON_START = false; // waitForDebugger on BootReceiver and StartActivity
	public static final boolean DEBUG_CC_ON_UPDATE	= false; // always clean caches on app update
	public static final boolean DEBUG_APP_LICENSE_CHECK = false; // show license on each start
	public static final boolean DEBUG_NO_LIBS		= false; // don't load native libs
	public static final boolean DEBUG_NO_UPDATE		= false; // disable data sending in UpdateService (enable with PROFILING)

	public static final boolean DEBUG_PROFILE_START = false; // trace from App start to Vpn stop
	public static final boolean DEBUG_PROFILE_MEM	= false; // dump memory info before and after Vpn stop
	// see ProxyWorker, see ProxyEvent ev + ClientEvent ev
	public static final boolean DEBUG_PROFILE_NET	= false; // profile network objects

	public static final boolean DEBUG_LOCAL_WGPROXY = false; // use proxy on 192.168.1.9

	public static final boolean DEBUG_NO_SCAN		= false; // disable databases
	public static final boolean DEBUG_DB_REPLACE	= false; // always replace same db version on app start (DEBUG !!! true)

	public static final boolean DEBUG_YOUTUBE		= false; // show requests to youtube
	public static final boolean DEBUG_SCANNER_URLS	= false; // show url variants in Scanner

	public static final boolean DEBUG_DROP_WG		= false; // drop connects from WG through WG

	// license

	public static final boolean DEBUG_BUYGUI		= false; // always show buy gui (on option select)

	public static final boolean DEBUG_NOSUBS		= false; // licenseCheckOnServer don't restore token
	public static final boolean DEBUG_NOFREESUBS	= false; // recoveryCheck don't restore anything (isFreeToken return false also)
	public static final boolean DEBUG_FREECHECK		= false; // always check recovery and free token scripts
	public static final boolean DEBUG_FAKETOKEN		= false; // use fake token (licenseCheck restore fake token)

	public static final boolean DEBUG_NOTOKEN		= false; // getUserToken return null
	public static final boolean DEBUG_EMPTYREF		= false; // getReferrer return null

	public static final boolean DEBUG_GP_CHECK_INFO = false; // log more data in licenseCheckOnGP

	//

	// source tags
	public static final String TAG_HASHER              = "WG_Hasher";
	public static final String TAG_LIBPATCH            = "WG_LibPatch";
	public static final String TAG_NETINFO             = "WG_NetInfo";
	public static final String TAG_NETUTIL             = "WG_NetUtil";
	public static final String TAG_UTILS               = "WG_Utils";
	public static final String TAG_WIFI                = "WG_WiFi";
	public static final String TAG_DEBUGUTILS          = "WG_DebugUtils";
	public static final String TAG_BYTEBUFFERPOOL      = "WG_ByteBufferPool";
	public static final String TAG_MEMORYBUFFER        = "WG_MemoryBuffer";
	public static final String TAG_FILTERVPNSERVICE    = "WG_FilterVpnService";
	public static final String TAG_MAINVPNSERVICE      = "WG_MainVpnService";
	public static final String TAG_CLIENTEVENT         = "WG_ClientEvent";
	public static final String TAG_DNSREQUEST          = "WG_DNSRequest";
	public static final String TAG_DNSUTILS            = "WG_DNSUtils";
	public static final String TAG_CHUNKEDREADER       = "WG_ChunkedReader";
	public static final String TAG_REQUESTHEADER       = "WG_RequestHeader";
	public static final String TAG_RESPONSEHEADER      = "WG_ResponseHeader";
	public static final String TAG_BLOCKIPCACHE        = "WG_BlockIPCache";
	public static final String TAG_CHANNELPOOL         = "WG_ChannelPool";
	public static final String TAG_PACKET              = "WG_Packet";
	public static final String TAG_PACKETDEQUEPOOL     = "WG_PacketDequePool";
	public static final String TAG_PACKETPOOL          = "WG_PacketPool";
	public static final String TAG_TCPSTATEMACHINE     = "WG_TCPStateMachine";
	public static final String TAG_UDPCLIENT           = "WG_UDPClient";
	public static final String TAG_SCANNER             = "WG_Scanner";
	public static final String TAG_APP                 = "WG_App";
	public static final String TAG_APPRECEIVER         = "WG_AppReceiver";
	public static final String TAG_BOOTCOMPLETEDRECEIVER = "WG_BootCompletedReceiver";
	public static final String TAG_EXCEPTIONLOGGER     = "WG_ExceptionLogger";
	public static final String TAG_LOGSERVICE          = "WG_LogService";
	public static final String TAG_MYUPDATERECEIVER    = "WG_MyUpdateReceiver";
	public static final String TAG_NETWORKSTATECHANGERECEIVER = "WG_NetworkStateChangeReceiver";
	public static final String TAG_PROMPTACTIVITYSTARTSERVICE = "WG_PromptActivityStartService";
	public static final String TAG_SCREENSTATERECEIVER = "WG_ScreenStateReceiver";
	public static final String TAG_TIMERSERVICE        = "WG_TimerService";
	public static final String TAG_VPNLISTENER         = "WG_VPNListener";
	public static final String TAG_INAPPBILLING        = "WG_InAppBilling";
	public static final String TAG_PREFERENCES         = "WG_Preferences";
	public static final String TAG_PROXYBASE           = "WG_ProxyBase";
	public static final String TAG_BROWSERS            = "WG_Browsers";
	public static final String TAG_DATABASE            = "WG_Database";
	public static final String TAG_PROCESSES           = "WG_Processes";
	public static final String TAG_LICENSEACTIVITY     = "WG_LicenseActivity";
	public static final String TAG_NOTIFICATIONS       = "WG_Notifications";
	public static final String TAG_OPTIONSACTIVITY     = "WG_OptionsActivity";
	public static final String TAG_OPTIONSFRAGMENT     = "WG_OptionsFragment";
	public static final String TAG_ROOTPREFSWITCH      = "WG_RootPrefSwitch";
	public static final String TAG_STARTACTIVITY       = "WG_StartActivity";
	public static final String TAG_NETLINE             = "WG_NetLine";
	public static final String TAG_USB                 = "WG_Usb";
	public static final String TAG_APPMANAGER          = "WG_AppManager";
	public static final String TAG_STATISTICS          = "WG_Statistics";
	public static final String TAG_PROXYEVENT          = "WG_ProxyEvent";
	public static final String TAG_DNSBUFFER           = "WG_DNSBuffer";
	public static final String TAG_DNSRESPONSE         = "WG_DNSResponse";
	public static final String TAG_PROXYWORKER         = "WG_ProxyWorker";
	public static final String TAG_TCPCLIENT           = "WG_TCPClient";
	public static final String TAG_TUNREADTHREAD       = "WG_TunReadThread";
	public static final String TAG_TUNWRITETHREAD      = "WG_TunWriteThread";
	public static final String TAG_POLICY              = "WG_Policy";
	public static final String TAG_PROMPTACTIVITY      = "WG_PromptActivity";
	public static final String TAG_UPDATESERVICE       = "WG_UpdateService";
	public static final String TAG_VPNSOCKET           = "WG_VpnSocket";

	public static final String TAG_MARK                = "WG_MARK";
	public static final String TAG_LOG                 = "WG_LOG";
	public static final String TAG_TRACE               = "WG_TRACE";
	public static final String TAG_DUMP                = "WG_DUMP";
	public static final String TAG_DEBUGINFO           = "WG_DebugInfo";
	public static final String TAG_PKTDUMP             = "WG_PKTDUMP";
	public static final String TAG_HPROF               = "WG_HPROF";

	//

	public static final String APP_NAME         = "WebGuard";
	public static final String APP_PACKAGE      = "app.webguard";
	public static final String APP_SITE         = "webguard.app";
	public static final String APP_FILES_PREFIX = "webguard";
	public static final String APP_NEWPACKAGE   = "";

	public static final String TUN_DEFAULT_IP = "10.10.10.1";
	public static final String TUN_IP		  = "10.20.30.1"; // "10.250.69.1";

	public static final String	DNS_FAKE_IP		   = "7.1.1.1";				 // "DoD Network Information Center" ip =)
	public static final byte[]	DNS_FAKE_IP_AR	   = new byte[]{7, 1, 1, 1}; // note about signed bytes
	public static final String	DNS1_IP			   = "8.8.8.8";				 // google dns
	public static final String	DNS2_IP			   = "77.88.8.8";			 // yandex dns
	public static final boolean DNS_SANITIZE_IPV6  = true; // remove ipv6 addresses from dns answers
	public static final boolean DNS_USE_CACHE	   = true; // use dns cache
	public static final boolean DNS_NO_ADS_IP	   = true; // don't return IP for blocked domains (ADS, MALWARE) in DNS responses
	public static final boolean DNS_IP_BLOCK_CLEAN = false; // clean or update list of blocked domains by time

	public static final boolean LOOPBACK_DROP_CONNECTS = false;
	public static final byte[]	LOOPBACK_IP_AR		   = new byte[]{127, 0, 0, 1};

	public static final String	TEST_LOCAL_PROXY_IP    = "7.1.1.10"; // ip to test app->proxy->vpn bug
	public static final byte[]	TEST_LOCAL_PROXY_IP_AR = new byte[]{7, 1, 1, 10};
	public static final String	TEST_TUN_WORK_IP	   = "7.1.1.11"; // ip to test tun work
	public static final byte[]	TEST_TUN_WORK_IP_AR    = new byte[]{7, 1, 1, 11};

	public static final String	WGPROXY_MAIN_DOMAIN = "webguard.app";
	public static final int		WGPROXY_PORT		= 4666;
	public static final boolean WGPROXY_CRYPT_USE	= true;

	public static final boolean LIC_DISABLE		   = false; // disable all check for subs
	public static final boolean LIC_CHECK_FORCE    = false; // force to check license on events (network change, boot, etc)
	public static final long	LIC_CHECK_INTERVAL = 24 * 60 * 60 * 1000; // 1 day, interval to check license
	public static final boolean LIC_FREE_AUTO	   = true; // free license auto registration (no captcha and dialogs)
	public static final boolean LIC_ASK_PROMO	   = false;
	public static final boolean LIC_FREEUSER	   = false; // isFreeUser (old user with free funcs) return false
	public static final boolean LIC_USE_GP		   = true; // use Google Play

//	public static final boolean VPN_SERVICE_FOREGROUND	= true; // set isForeground flag for VpnService to prevent killing
	public static final boolean VPN_SERVICE_STOP_ON_OFF = false; // stop vpn service on VPN off if protection enabled

	public static final boolean UPDATER_STOP_ON_OFF  = false; // stop updater service on vpn off
	public static final boolean UPDATER_STOP_ON_IDLE = false; // stop updater service on idle

	public static final boolean TCPIP_CON_USE_NETLINK = true; // search TCP connects info with netlink
	public static final boolean UPDATE_APPS_REHASH    = true; // rehash (and other info) all apps after WG update (DEBUG !!! false)
	public static final boolean EULA_ASK              = false; // ask user to accept app license
//	public static final boolean FIRSTRUN_AUTOSTART    = true; // autostart protection on app first run
	public static final boolean APPS_EXCLUDE_BROWSERS = true; // separate ad blocking for browsers and other apps (see PREF_APP_ADBLOCK)
	public static final boolean USER_FEEDBACK         = true; // show different feedback (stats, help us) notifications to user
	public static final boolean SHOW_BOOT_SCREEN      = false; // Roman Popov 07.05.21 // show separate activity instead main on boot
	public static final boolean EVENTS_LOG            = true; // disable LOG_* messages logging
	public static final boolean CLEAR_CACHES          = true; // clear apps caches and kill apps
	public static final boolean NOTIFY_BUY_BUTTON     = true; // show buy button at license end notifications

	public static final long	ACTIVATE_REQUEST_INTERVAL = 5 * 60 * 1000; // 5 min to up PromptActivity if not activated on boot
	//public static final long	  ACTIVATE_REQUEST_INTERVAL = 1 * 60 * 1000;
	public static final long	SETTINGS_RELOAD_INTERVAL = 5 * 60 * 60 * 1000; // 05:00 every day reload settings
	public static final long	CLEAR_CACHES_INTERVAL = 15 * 60 * 1000; // clear apps caches only if WG disabled > 15 min (not used)
	//public static final long	  CLEAR_CACHES_INTERVAL = 1 * 60 * 1000;

	//

	// издатели
	public enum PUBLISHERS {
		GOOGLE,
		SAMSUNG,
		HUAWEI,
		AMAZON
	}

	//

	// tun/network data processing errors
	public static final String LOG_SHARING_WIFI_ERR			= "sharing WiFi err";
	public static final String LOG_BYTEBUFPOOL_OUTOFMEM_ERR = "BBP OOM err ";			 // + date
	public static final String LOG_VPNESTABLISH_NULL_ERR	= "establish nl err";
	public static final String LOG_VPNESTABLISH_UNK_ERR		= "establish un err";
	public static final String LOG_VPNESTABLISH_TUN_ERR		= "establish tu err";
	public static final String LOG_VPNPREPARE_NULL_ERR		= "prepare nl err";
	public static final String LOG_DNSRESP_NULL_ERR			= "dns resp parse nl err";
	public static final String LOG_CHUNKPARSE_NULL_ERR		= "chunk parse nl err ";	 // + chunk data
	public static final String LOG_PACKETPOOL_OUTOFMEM_ERR	= "PP OOM err ";			 // + date
	public static final String LOG_TCPCLIENT_CHANREG_ERR	= "chan register err";
	public static final String LOG_UDPCLIENT_DNSPARSE_ERR	= "udp dns err ";			 // + dns request
	public static final String LOG_NOTIFIER_DOMAIN_ERR		= "notifier domain err";

	// local proxy detect/deleting
	public static final String LOG_LOCAL_PROXY_DELETE		= "lp del";
	public static final String LOG_LOCAL_PROXY_DELETE_ERR	= "lp del err";
	public static final String LOG_LOCAL_PROXY_APP			= "lp app ";				 // + pkg name

	// database
	public static final String LOG_DB_COPY_ERR				= "db copy err";
	public static final String LOG_DB_LOAD_ERR				= "db load err";
	public static final String LOG_DB_UPDATE_ERR			= "db update err";
	public static final String LOG_DB_PATCH_ERR				= "db patch err";
	public static final String LOG_DB_FAST_ERR				= "db fast err";

	// different events
	public static final String LOG_LICENSE_ACCEPTED			= "license agree";
	public static final String LOG_TRY_FREE					= "try free";
	public static final String LOG_STATS_SHOW				= "stats show";
	public static final String LOG_HELP_SHOW				= "help show";
	public static final String LOG_TETHERING				= "tethering";
	public static final String LOG_TIME_CHANGE				= "time change";
	public static final String LOG_BOOT						= "boot";
	public static final String LOG_UPDATED					= "updated";
	public static final String LOG_STARTED					= "started ";				 // + time
	public static final String LOG_RESTARTED				= "restarted ";				 // + time
	public static final String LOG_CACHES_CLEAR				= "cc run";
	public static final String LOG_CACHES_CLEAR_ERR			= "cc err";
	public static final String LOG_INIT_TIMEOUT				= "init timeout ";			 // + num

	// parsing/loading user subscriptions/inapp info
	//public static final String LOG_BILLING_SUBSSIZE		   = "GP subs size ";		   // + size
	//public static final String LOG_BILLING_GPNOSUBS		   = "GP no subs";
	public static final String LOG_BILLING_GPSUBS_ERR		 = "GP subs err";
	public static final String LOG_BILLING_GPSUBS_STATE		 = "GP subs state ";		 // + code
	public static final String LOG_BILLING_GPINAPP_ERR		 = "GP subs erri";
	public static final String LOG_BILLING_GPSIGS_ERR		 = "GP subs errs";
	public static final String LOG_BILLING_GPPURCHASE_ERR	 = "GP purchase err ";		 // + code
	public static final String LOG_BILLING_SRVSUBS_ERR		 = "server subs err";
	public static final String LOG_BILLING_SRVSUBS_STATE	 = "server subs state ";	 // + code
	public static final String LOG_BILLING_SRVSUBS_CODE		 = "server subs code ";		 // + code
	public static final String LOG_BILLING_SRVPARSE_ERR		 = "server parse err";
	public static final String LOG_BILLING_SRVFREESUBS_ERR	 = "server freesubs err";
	public static final String LOG_BILLING_SRVFREESUBS_STATE = "server freesubs state "; // + code
	public static final String LOG_BILLING_SRVFREESUBS_CODE  = "server freesubs code ";  // + code
	public static final String LOG_BILLING_SRVREC_STATE		 = "server rec state ";		 // + code
	public static final String LOG_BILLING_SRVREC_CODE		 = "server rec code ";		 // + code

	// parsing/loading subscriptions/inapp list info
	public static final String LOG_BILLING_SUBSDATA_ERR		= "subs data err";
	public static final String LOG_BILLING_SUBSSIGN_ERR		= "subs sign err";
	public static final String LOG_BILLING_SUBSPARSE_ERR	= "subs parse err";
	public static final String LOG_BILLING_SUBSGP_ERR		= "subs GP err ";			 // + code
	public static final String LOG_BILLING_SUBSGP_NULL		= "subs GP nl";
	public static final String LOG_BILLING_INAPPGP_ERR		= "subs GP err i";			 // + code
	public static final String LOG_BILLING_INAPPGP_NULL		= "subs GP nli";
	public static final String LOG_BILLING_SUBSGP_EMPTY		= "subs GP empty";

	// error state
	public static final String LOG_ERROR_AUTH_ON_PROXY		= "error auth on proxy";	// так и не смогли авторизоваться на нашем прокси
	public static final String LOG_ERROR_AUTH_ON_PROXY2		= "error auth on proxy2";	// так и не смогли авторизоваться на нашем прокси

	public static final String LOG_EXCEPTION				 = "exception ";

	// buy/getfree processing
	public static final String LOG_BUY_SHOW					= "buy show";
	public static final String LOG_BUY_NEED					= "buy need";
	public static final String LOG_BUY_CLICKED				= "buy clicked ";			 // + name
	public static final String LOG_BUY_ACCEPTED				= "buy accepted";
	public static final String LOG_BUY_COMPLETED			= "buy completed";
	public static final String LOG_BUY_ERR					= "buy err";
	public static final String LOG_BUY_DECLINED				= "buy declined";
	public static final String LOG_BUY_SELECTED				= "buy selected ";			 // + name

	// notify events
	public static final String LOG_NOTIFY_CLICKED			= "notify clicked";
	public static final String LOG_NOTIFY_TRYSUBS			= "notify try subs";
	public static final String LOG_NOTIFY_SUBSEXP			= "notify subs expired";
	public static final String LOG_NOTIFY_FREEEXP			= "notify free expired";
	public static final String LOG_NOTIFY_BGERR				= "notify bg err";
	public static final String LOG_NOTIFY_NEEDUP			= "notify need update";
	public static final String LOG_NOTIFY_NEWS				= "notify news";
	public static final String LOG_NOTIFY_NEWS_ERROR		= "notify news error";
	public static final String LOG_NOTIFY_EVALUATE			= "notify evaluate";
	public static final String LOG_NOTIFY_EVALUATE_ERROR	= "notify evaluate error";
	public static final String LOG_NOTIFY_FEEDBACK			= "notify feedback";
	public static final String LOG_NOTIFY_FEEDBACK_ERROR	= "notify feedback error";
	public static final String LOG_NOTIFY_FEEDBACK2			= "notify feedback2";
	public static final String LOG_NOTIFY_FIRSTRESULT		= "notify first result";
	public static final String LOG_NOTIFY_FIRSTRESULT_ERROR	= "notify first result error";
	public static final String LOG_NOTIFY_LICERR			= "notify license err";
	public static final String LOG_NOTIFY_LICERR_ERROR		= "notify license err error";
	public static final String LOG_NOTIFY_PROXYERR			= "notify proxy err";
	public static final String LOG_NOTIFY_PROXYERR_ERROR	= "notify proxy err error";
	public static final String LOG_NOTIFY_APPDISABLED		= "notify app disabled";
	public static final String LOG_NOTIFY_APPDISABLED_ERROR	= "notify app disabled error";

	// dialog events
	public static final String LOG_DIALOG_NEEDUP			= "dialog need update";
	public static final String LOG_DIALOG_DISPROXY			= "dialog disable proxy";
	public static final String LOG_DIALOG_DELPROXY			= "dialog delete proxy";
	public static final String LOG_DIALOG_VPNCANCEL			= "dialog vpn cancel";
	public static final String LOG_DIALOG_VPNMISSED			= "dialog vpn missed";
	public static final String LOG_DIALOG_VPNBUG1900		= "dialog vpn bug1900";
	public static final String LOG_DIALOG_INSTALLERR		= "dialog install err";
	public static final String LOG_DIALOG_TPWARN			= "dialog tp warn";
	public static final String LOG_DIALOG_NEWS				= "dialog news";
	public static final String LOG_DIALOG_EVALUATE			= "dialog evaluate";
	public static final String LOG_DIALOG_FEEDBACK			= "dialog feedback";
	public static final String LOG_DIALOG_FEEDBACK2			= "dialog feedback2";
	public static final String LOG_DIALOG_FIRSTRESULT		= "dialog firstresult";
	public static final String LOG_DIALOG_PERMERR			= "dialog perm err";
	public static final String LOG_DIALOG_NONET				= "dialog no network";
	public static final String LOG_DIALOG_GPERR				= "dialog gp err";
	public static final String LOG_DIALOG_ADVWARN			= "dialog adv warn";
	public static final String LOG_DIALOG_BAWARN			= "dialog ba warn";

	//

	// gui settings
	public static final String PREF_ACTIVE			   = "pref_active";			  // stats
	public static final String PREF_BLOCK_MALICIOUS    = "pref_block_malicious";  // stats
	public static final String PREF_ALLOW_SOME_ADS	   = "pref_allow_some_ads";
	public static final String PREF_BLOCK_APKS		   = "pref_block_apks";		  // stats
	public static final String PREF_USE_COMPRESSION    = "pref_use_compression";  // stats
	public static final String PREF_PROXY_COUNTRY	   = "pref_proxy_country";	  // stats
	public static final String PREF_ANONYMIZE		   = "pref_anonymize";		  // stats
	public static final String PREF_ANONYMIZE_ONLY_BRW = "pref_anonymize_only_browsers"; // stats
	public static final String PREF_BLOCK_APPS_DATA    = "pref_block_apps_data";  // stats
	public static final String PREF_CHECK_SUBSCRIPTION = "pref_check_subscription";
	public static final String PREF_BUY_SUBSCRIPTION   = "pref_buy_subscription";

	public static final String PREF_FEEDBACK_SCREEN    = "pref_feedback_screen";
	public static final String PREF_STATS_SCREEN	   = "pref_stats_screen";
	public static final String PREF_FIREWALL_SCREEN    = "pref_firewall_screen";
	public static final String PREF_SOCIAL_SCREEN	   = "pref_social_screen";
	public static final String PREF_HELP_SCREEN		   = "pref_help_screen";
	public static final String PREF_PRIVACY_SCREEN     = "pref_privacy_screen";
	public static final String PREF_ADVANCED_OPTS	   = "pref_advanced_screen";
	public static final String PREF_ADVANCED_UI		   = "pref_advanced_ui";
	public static final String PREF_ADVANCED_SECURITY  = "pref_advanced_security";
	public static final String PREF_ADVANCED_OTHER	   = "pref_advanced_other";

	public static final String PREF_SOCIAL_VK		   = "pref_social_vk";		  // stats
	public static final String PREF_SOCIAL_FB		   = "pref_social_fb";		  // stats
	public static final String PREF_SOCIAL_TWITTER	   = "pref_social_twitter";   // stats
	public static final String PREF_SOCIAL_OK		   = "pref_social_ok";		  // stats
	public static final String PREF_SOCIAL_MAILRU	   = "pref_social_mailru";	  // stats
	//public static final String PREF_SOCIAL_LJ			 = "pref_social_lj";
	public static final String PREF_SOCIAL_GPLUS	   = "pref_social_gplus";	  // stats
	public static final String PREF_SOCIAL_LINKEDIN    = "pref_social_linkedin";  // stats
	public static final String PREF_SOCIAL_MOIKRUG	   = "pref_social_moikrug";   // stats
	public static final String PREF_SOCIAL_OTHER	   = "pref_social_other";	  // stats

	public static final String PREF_USE_LIGHT_THEME    = "pref_use_light_theme";  // stats
	public static final String PREF_CHANGE_USERAGENT   = "pref_change_useragent"; // stats
	public static final String PREF_DESKTOP_USERAGENT  = "pref_set_desktop_useragent"; // stats
	public static final String PREF_BLOCK_TP_CONTENT   = "pref_block_thirdparty_content"; // stats
	public static final String PREF_COLLECT_DEBUGINFO  = "pref_collect_debug_info";
	public static final String PREF_CLEAN_CACHES	   = "pref_clean_caches";

	// убрали выбор языков тк добавили другие языки
//	public static final String PREF_USE_EN_LANG		   = "pref_use_en_lang";	  // stats
//	public static final String PREF_USE_RU_LANG		   = "pref_use_ru_lang";	  // stats

	public static final String PREF_DEBUG_SCREEN	   = "pref_debug_screen";
	public static final String PREF_DEBUG_LOG_MARK	   = "pref_log_mark";
	public static final String PREF_DEBUG_UPDATE_DDB   = "pref_update_debug_db";
	public static final String PREF_DEBUG_TRACING	   = "pref_tracing";
	public static final String PREF_DEBUG_HPROF		   = "pref_hprof";
	public static final String PREF_DEBUG_TCPDUMP_WL   = "pref_tcpdump_wlan";
	public static final String PREF_DEBUG_TCPDUMP_TUN  = "pref_tcpdump_tun";
	public static final String PREF_DEBUG_DUMP_TUN	   = "pref_dump_tun";
	public static final String PREF_DEBUG_TEST		   = "pref_test";

	// internal values
	public static final String PREF_DISABLE_TIME	   = "pref_disable_time";	  // stats
	public static final String PREF_USER_TOKEN		   = "pref_user_token";		  // stats
	public static final String PREF_USER_TOKEN_TIME    = "pref_user_token_time";  // stats
	public static final String PREF_RECOVERY_STATUS    = "recovery_status";		  // stats
	public static final String PREF_REFERRER		   = "referrer";
	public static final String PREF_REFERRER_RECOVERY  = "referrer_recovery";	  // stats
	public static final String PREF_APP_ADBLOCK		   = "invalid_option";		  // old "app_adblock", stats
	public static final String PREF_SUBSCRIPTION_WAS   = "subs_was";			  // stats
	public static final String PREF_SUBSCRIPTION_EXPIRED = "subs_expired";		  // stats

	public static final String PREF_UPDATE_URL		   = "update_url";
	public static final String PREF_NEED_UPDATE		   = "need_update";			  // stats
	public static final String PREF_UPDATE_NOTE_TIME   = "update_note_time";
	public static final String PREF_UPDATE_RETRY_TIME  = "update_retry_time";
	public static final String PREF_UPDATE_TIME		   = "update_time";				   // stats
	public static final String PREF_LAST_UPDATE_TIME   = "last_update_time";		   // stats
	public static final String PREF_UPDATE_ERROR_COUNT = "update_error_count";		   // stats
	public static final String PREF_LAST_FASTUPDATE_TIME = "last_fastupdate_time";	   // stats
	public static final String PREF_FASTUPDATE_ERROR_COUNT = "fastupdate_error_count"; // stats

	public static final String PREF_STATS			   = "pref_stats";
	public static final String PREF_APPS_HASHED		   = "hashing_on_install";

	public static final String PREF_CLEARCACHES_TIME   = "pref_cc_time";		  // stats
	public static final String PREF_CLEARCACHES_NEED   = "pref_cc_need";

	public static final String PREF_NOTIFY_DISABLED    = "notify_disabled";
	public static final String PREF_DNS_SERVERS		   = "dns_servers";			  // ??? what this?
	public static final String PREF_PROXY_DEL_TRY	   = "proxy_delete_try";
	public static final String PREF_FIRST_START_TIME   = "first_start_time";	  // stats
	public static final String PREF_NO_SUBS_AD_COUNT   = "subs_ad_count";
	public static final String PREF_BASES_VERSION	   = "bases_version";
	public static final String PREF_NEWS_SHOWN		   = "news_shown";
	public static final String PREF_POLICY_VERSION	   = "policy_version";
	public static final String PREF_TERMS_VERSION	   = "terms_version";
	public static final String PREF_EVALUATE_TIME	   = "evaluate_time";
	public static final String PREF_EVALUATE_STATUS    = "evaluate_status";		  // stats
	public static final String PREF_FEEDBACK_TIME	   = "feedback_time";
	public static final String PREF_FEEDBACK_STATUS    = "feedback_status";		  // stats
	public static final String PREF_FEEDBACK2_TIME	   = "feedback2_time";
	public static final String PREF_FEEDBACK2_STATUS   = "feedback2_status";	  // stats
	public static final String PREF_FIRSTRESULT_TIME   = "firstresult_time";
	public static final String PREF_FIRSTRESULT_STATUS = "firstresult_status";	  // stats

	//public static final String PREF_SEE_FIREWALL_OPTS  = "see_firewall_screen";	// stats
	public static final String PREF_SEE_ADVANCED_OPTS  = "see_advanced_screen";   // stats
	public static final String PREF_SEE_BLOCK_APPS_DATA = "see_block_apps_data";  // stats
	public static final String PREF_SEE_BLOCK_TP_CONTENT = "see_block_tp_content"; // stats

	// additional data in Preferences.getAllPreferences (stats)
	public static final String STATS_APP_CERTHASH	   = "app_check";
	// TODO add here names from Preferences.getDeviceInfo()

	// additional data in Statistics.updateStats (stats)
	public static final String STATS_NET_TCP_MAX		  = "tcp_clients_max";
	public static final String STATS_NET_UDP_MAX		  = "udp_clients_max";
	public static final String STATS_NET_NL_ERR_MAX		  = "nl_err_max";
	public static final String STATS_NET_NL_NF_MAX		  = "nl_nf_max";
	public static final String STATS_NET_PROC_RETRY_MAX   = "proc_retry_max";
	public static final String STATS_NET_PROC_NF_MAX	  = "proc_nf_max";
	public static final String STATS_POLICY_BLOCK_ADS_IP  = "block_ads_ip";
	public static final String STATS_POLICY_BLOCK_ADS_URL = "block_ads_url";
	public static final String STATS_POLICY_BLOCK_APK	  = "block_apk";
	public static final String STATS_POLICY_BLOCK_MALWARE = "block_malware";
	public static final String STATS_POLICY_BLOCK_PAID	  = "block_paid";
	public static final String STATS_COMPRESSION_SAVE	  = "proxy_save";

	// manifest values
	public static final String MANIFEST_PUBLISHER	   = "publisher";
	public static final String MANIFEST_UPDATE_URL	   = "update_url";
	public static final String MANIFEST_BILLING_URL    = "billing_url";
	public static final String MANIFEST_SUBS_URL	   = "subs_url";
	public static final String MANIFEST_FREE_URL	   = "free_url";
	public static final String MANIFEST_PROMO_URL	   = "promo_url";
	public static final String MANIFEST_RECOVERY_URL   = "recovery_url";
	public static final String MANIFEST_DEBUG_URL	   = "debug_url";
	public static final String MANIFEST_POLICY_VERSION = "policy_version";
	public static final String MANIFEST_TERMS_VERSION  = "terms_version";
	public static final String MANIFEST_POLICY_URL	   = "privacy_url";
	public static final String MANIFEST_TERMS_URL	   = "terms_url";
}
