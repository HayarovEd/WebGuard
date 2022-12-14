package app.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.preference.PreferenceManager;

import com.ironz.binaryprefs.BinaryPreferencesBuilder;

import app.common.Hasher;
import app.common.LibNative;
import app.common.Utils;
import app.common.debug.L;
import app.App;
import app.Res;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;


public class Preferences
{
//	private static final SharedPreferences prefs;

//	private static final SharedPreferences prefsShared;
//	private static final com.ironz.binaryprefs.Preferences prefs;
	private static com.ironz.binaryprefs.Preferences prefs;

//	private static SharedPreferences.Editor editorShared = null;
	private static SharedPreferences.Editor editor = null;
	private static ReentrantLock editLock = new ReentrantLock();
	private static int lockCounter = 0;

	private static boolean policy_updated = false;
	private static boolean terms_updated = false;
	private static int newPolicyVersion = -1;
	private static int newTermsVersion = -1;
	private static boolean hasSavedPolicy = false;
	private static String publisher = null;
	private static int appVersion;
	private static int updateRetryTime;
	private static String termsUrl = null;
	private static String privacyUrl = null;
	private static String billingUrl = null;
	private static String subsUrl = null;
	private static String freeUrl = null;
	private static String promoUrl = null;
	private static String recoveryUrl = null;
	private static String debugUrl = null;
	private static String installId = null;

	private static String appCertHash = null;

	private static boolean rooted = false;
	private static boolean netlinkWork = false;

	// TODO XXX remove locks on read, and check edit lock on read

//	static
//	{
//		prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
//		prefsShared = PreferenceManager.getDefaultSharedPreferences(App.getContext());
//		prefs = new BinaryPreferencesBuilder(App.getContext()).build();
//	}

	public static void init(Context context) {
		prefs = new BinaryPreferencesBuilder(context).build();
	}

	public static void load(Context context)
	{
		String country = get_s(Settings.PREF_PROXY_COUNTRY); // need here? copy in Policy.reloadPrefs
		ProxyBase.setCurrentCountry(country);
		//prefs = activity.getSharedPreferences("app.webguard_preferences", Context.MODE_PRIVATE);

		ApplicationInfo info = null;
		PackageManager pm = context.getPackageManager();
		try
		{
			info = pm.getApplicationInfo(App.packageName(), PackageManager.GET_META_DATA);
			PackageInfo pinfo = pm.getPackageInfo(App.packageName(), PackageManager.GET_META_DATA);
			appVersion = pinfo.versionCode;
		}
		catch (PackageManager.NameNotFoundException e) { e.printStackTrace(); }

		if (info == null)
			return;

		Preferences.editStart();
		try
		{
			publisher = info.metaData.getString(Settings.MANIFEST_PUBLISHER);

			updateRetryTime = info.metaData.getInt(Settings.PREF_UPDATE_RETRY_TIME, 3 * 60 * 60); // 10800, 3h in seconds

			if (get_s(Settings.PREF_UPDATE_URL) == null)
				putString(Settings.PREF_UPDATE_URL, info.metaData.getString(Settings.MANIFEST_UPDATE_URL));

			billingUrl = info.metaData.getString(Settings.MANIFEST_BILLING_URL, null);
			subsUrl =  info.metaData.getString(Settings.MANIFEST_SUBS_URL, null);
			freeUrl =  info.metaData.getString(Settings.MANIFEST_FREE_URL, null);
			promoUrl =	info.metaData.getString(Settings.MANIFEST_PROMO_URL, null);
			recoveryUrl =  info.metaData.getString(Settings.MANIFEST_RECOVERY_URL, null);
			debugUrl = info.metaData.getString(Settings.MANIFEST_DEBUG_URL, null);
		}
		finally { Preferences.editEnd(); }

		checkPolicyState(context, info);
		getCertHash(context, pm);

		installId = App.getInstallId();
		rooted = hasRoot();
		if (App.isLibsLoaded())
			netlinkWork = LibNative.netlinkIsWork();
	}

	private static void checkPolicyState(Context context, final ApplicationInfo info)
	{
		int saved_policy_version = Preferences.getInt(Settings.PREF_POLICY_VERSION, -1);
		int saved_terms_version = Preferences.getInt(Settings.PREF_TERMS_VERSION, -1);

		hasSavedPolicy = (saved_policy_version >= 0 && saved_terms_version >= 0);

		int apk_policy_version = info.metaData.getInt(Settings.MANIFEST_POLICY_VERSION, -1);
		int apk_terms_version = info.metaData.getInt(Settings.MANIFEST_TERMS_VERSION, -1);

		termsUrl = info.metaData.getString(Settings.MANIFEST_TERMS_URL, null);
		privacyUrl = info.metaData.getString(Settings.MANIFEST_POLICY_URL, null);

		policy_updated = (saved_policy_version < apk_policy_version);
		terms_updated = (saved_terms_version < apk_terms_version);

		newPolicyVersion = apk_policy_version;
		newTermsVersion = apk_terms_version;
	}

	private static void getCertHash(Context context, PackageManager pm)
	{
		try
		{
			Signature sig =
				pm.getPackageInfo(App.packageName(), PackageManager.GET_SIGNATURES).signatures[0];
			byte[] hash = Hasher.md5(sig.toByteArray());
			appCertHash = Utils.toHex(hash);
		}
		catch (Exception e) { }
	}

	public static String getLicenseActivityText()
	{
		String s = App.getContext().getString(Res.string.license_dialog_text);
		return s.replace("#s1", termsUrl).replace("#s2", privacyUrl);
	}

	public static void clearProFunctions(boolean proxyOnly)
	{
		Preferences.editStart();
		try
		{
			if (!proxyOnly &&
				Preferences.getBoolean(Settings.PREF_SUBSCRIPTION_WAS, false) && !Preferences.getBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, false))
			{
				Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, true);

				// save old settings
				Preferences.putBoolean(Settings.PREF_ANONYMIZE + "_bak", Preferences.get(Settings.PREF_ANONYMIZE));
				Preferences.putBoolean(Settings.PREF_USE_COMPRESSION + "_bak", Preferences.get(Settings.PREF_USE_COMPRESSION));
				Preferences.putBoolean(Settings.PREF_BLOCK_APPS_DATA + "_bak", Preferences.get(Settings.PREF_BLOCK_APPS_DATA));
			}

			// clear proxy settings
			if (Preferences.get(Settings.PREF_USE_COMPRESSION))
				Preferences.putBoolean(Settings.PREF_USE_COMPRESSION, false);

			if (Preferences.get(Settings.PREF_ANONYMIZE))
				Preferences.putBoolean(Settings.PREF_ANONYMIZE, false);

			if (proxyOnly)
				return;

			if (Preferences.get(Settings.PREF_BLOCK_APPS_DATA))
				Preferences.putBoolean(Settings.PREF_BLOCK_APPS_DATA, false);

			clearToken();
		}
		finally { Preferences.editEnd(); }
	}

	public static void enableProFunctions(String token, boolean isFull)
	{
		// TODO XXX check token for valid, copy/paste, see Policy.getUserToken
		if (token != null && !(token.length() == 32 || token.length() == 36))
		{
			L.w(Settings.TAG_PREFERENCES, "invalid token");
			return;
		}

		//

		Preferences.editStart();
		try
		{
			Preferences.putBoolean(Settings.PREF_APP_ADBLOCK, isFull);
			Preferences.putString(Settings.PREF_USER_TOKEN, token);

			//if (Preferences.get_l(Settings.PREF_USER_TOKEN_TIME) == 0)
			{
				long time = System.currentTimeMillis();
				Preferences.putLong(Settings.PREF_USER_TOKEN_TIME, time);
			}

			Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_WAS, true);
			if (Preferences.get(Settings.PREF_SUBSCRIPTION_EXPIRED))
			{
				// restore old settings
				Preferences.putBoolean(Settings.PREF_ANONYMIZE, Preferences.get(Settings.PREF_ANONYMIZE + "_bak"));
				Preferences.putBoolean(Settings.PREF_USE_COMPRESSION, Preferences.get(Settings.PREF_USE_COMPRESSION + "_bak"));
				Preferences.putBoolean(Settings.PREF_BLOCK_APPS_DATA, Preferences.get(Settings.PREF_BLOCK_APPS_DATA + "_bak"));

				Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, false);
			}
		}
		finally { Preferences.editEnd(); }
	}

	public static void clearToken()
	{
		Preferences.editStart();
		try
		{
			Preferences.putString(Settings.PREF_USER_TOKEN, null);
			Preferences.putLong(Settings.PREF_USER_TOKEN_TIME, 0);
		}
		finally { Preferences.editEnd(); }
	}

	// return null if referrer == ""
	public static String getReferrer(boolean encode)
	{
		if (Settings.DEBUG_EMPTYREF)
			return null;

		String referrer = get_s(Settings.PREF_REFERRER);
		if (referrer != null)
		{
			try { referrer = (encode) ? URLEncoder.encode(referrer, "UTF-8") : referrer; }
			catch (UnsupportedEncodingException ex) { }

			if (referrer.isEmpty())
				referrer = null;
		}

		return referrer;
	}

	public static String getPublisher()
	{
		return publisher;
	}

	public static boolean policyUpdated()
	{
		return policy_updated;
	}

	public static boolean termsUpdated()
	{
		return terms_updated;
	}

	public static boolean hasSavedPolicy()
	{
		return hasSavedPolicy;
	}

	public static boolean isActive()
	{
		return getBoolean(Settings.PREF_ACTIVE, false);
	}

	// return update interval in seconds
	public static int getUpdateRetryTime()
	{
		int t = getInt(Settings.PREF_UPDATE_RETRY_TIME, 0);
		if (t <= 0)
			t = updateRetryTime;

		return t;
	}

	public static void setNotifyDisabledAlarm(boolean disable)
	{
		long time = 0;
		if (!disable)
			time = System.currentTimeMillis() + 15 * 86400 * 1000;

		putLong(Settings.PREF_NOTIFY_DISABLED, time);
	}

	public static int getNewPolicyVersion()
	{
		return newPolicyVersion;
	}

	public static int getNewTermsVersion()
	{
		return newTermsVersion;
	}


	public static void savePolicyAndTerms() //SharedPreferences... sharedPreferences)
	{
		editLock.lock();
		try
		{
			//SharedPreferences.Editor e = prefs.edit();
            SharedPreferences.Editor e;
//            if (sharedPreferences == null) {
                e = prefs.edit();
//            } else {
//                e = prefsShared.edit();
//            }

			e.putInt(Settings.PREF_POLICY_VERSION, newPolicyVersion);
			e.putInt(Settings.PREF_TERMS_VERSION, newTermsVersion);

			commit(e);
			policy_updated = terms_updated = false;

		} catch (Exception e) {
			e.printStackTrace();
		}
		finally { editLock.unlock(); }

//        if (sharedPreferences == null) {
//            savePolicyAndTerms(prefsShared);
//        }

	}

	public static int getAppVersion()
	{
		return appVersion;
	}

	public static String getAppCertHash()
	{
		return appCertHash;
	}

	public static ArrayList<String> getDNSServers()
	{
		editLock.lock();
		try
		{
			ArrayList<String> res = new ArrayList<String>();
			final Set<String> set = prefs.getStringSet(Settings.PREF_DNS_SERVERS, null);
			if (set != null)
			{
				for (String server : set)
					res.add(server);
			}

			return res;
		}
		finally { editLock.unlock(); }
	}

	public static void putDNSServers(ArrayList<String> servers) //, SharedPreferences... sharedPreferences)
	{
		editLock.lock();
		try
		{
			Set<String> set = new HashSet<String>();
			for (String s : servers) {
				set.add(s);
			}

//			final SharedPreferences.Editor e = prefs.edit();
			SharedPreferences.Editor e;
//			if (sharedPreferences == null) {
				e = prefs.edit();
//			} else {
//				e = prefsShared.edit();
//			}

			e.putStringSet(Settings.PREF_DNS_SERVERS, set);
			commit(e);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			editLock.unlock();
		}

//		if (sharedPreferences == null) {
//			putDNSServers(servers, prefsShared);
//		}

	}

	public static String getPrivacyUrl()
	{
		return privacyUrl;
	}

	public static String getBillingUrl()
	{
		return billingUrl;
	}

	public static String getSubsUrl()
	{
		return subsUrl;
	}

	public static String getFreeUrl()
	{
		return freeUrl;
	}

	public static String getPromoUrl()
	{
		return promoUrl;
	}

	public static String getRecoveryUrl()
	{
		return recoveryUrl;
	}

	public static String getDebugUrl()
	{
		return debugUrl;
	}

	// getString("manufacturer"), getString("model")
	public static JSONObject getDeviceInfo()
	{
		JSONObject obj = new JSONObject();

		try
		{
			obj.put("manufacturer", Build.MANUFACTURER);
			obj.put("fingerprint", Build.FINGERPRINT);
			obj.put("model", Build.MODEL);
			obj.put("android", Build.VERSION.RELEASE);
			obj.put("firmware", Build.ID);

			obj.put("install_id", installId);
			obj.put("rooted", rooted);
			obj.put("netlink", netlinkWork);
		}
		catch (JSONException e) { e.printStackTrace(); }

		return obj;
	}

	public static boolean hasRoot()
	{
		// checkRootMethod1
//		  String buildTags = android.os.Build.TAGS;
//		  boolean have = (buildTags != null && buildTags.contains("test-keys"));
//		  if (have)
//			  return true;

		// checkRootMethod2
		if (new File("/system/app/Superuser.apk").exists())
			return true;

		// checkRootMethod3
		String[] paths = { "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
							"/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su",
							"/data/local/su" };
		for (String path : paths)
			if (new File(path).exists()) return true;

		// checkRootMethod4
//		  Process process = null;
//		  try
//		  {
//			  process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
//			  BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
//			  if (in.readLine() != null)
//				  return true;
//		  }
//		  catch (Throwable t) { }
//		  finally { if (process != null) process.destroy(); }

		return false;
	}

	public static JSONObject getAllPreferences()
	{
		JSONObject obj = new JSONObject();

		Preferences.editStart();
		try
		{
			try
			{
				obj.put(Settings.PREF_ACTIVE, Preferences.get(Settings.PREF_ACTIVE));

				obj.put(Settings.PREF_BLOCK_MALICIOUS, Preferences.get(Settings.PREF_BLOCK_MALICIOUS));
				obj.put(Settings.PREF_BLOCK_APKS, Preferences.get(Settings.PREF_BLOCK_APKS));
				//obj.put(Settings.PREF_ALLOW_SOME_ADS, Preferences.get(Settings.PREF_ALLOW_SOME_ADS));
				obj.put(Settings.PREF_ANONYMIZE, Preferences.get(Settings.PREF_ANONYMIZE));
				obj.put(Settings.PREF_ANONYMIZE_ONLY_BRW, Preferences.get(Settings.PREF_ANONYMIZE_ONLY_BRW));
				obj.put(Settings.PREF_PROXY_COUNTRY, Preferences.get_s(Settings.PREF_PROXY_COUNTRY));
				obj.put(Settings.PREF_USE_COMPRESSION, Preferences.get(Settings.PREF_USE_COMPRESSION));
				obj.put(Settings.PREF_BLOCK_APPS_DATA, Preferences.get(Settings.PREF_BLOCK_APPS_DATA));

				obj.put(Settings.PREF_USE_LIGHT_THEME, Preferences.get(Settings.PREF_USE_LIGHT_THEME));
				obj.put(Settings.PREF_CHANGE_USERAGENT, Preferences.get(Settings.PREF_CHANGE_USERAGENT));
				obj.put(Settings.PREF_DESKTOP_USERAGENT, Preferences.get(Settings.PREF_DESKTOP_USERAGENT));
//				obj.put(Settings.PREF_USE_EN_LANG, Preferences.get(Settings.PREF_USE_EN_LANG));
//				obj.put(Settings.PREF_USE_RU_LANG, Preferences.get(Settings.PREF_USE_RU_LANG));
				obj.put(Settings.PREF_BLOCK_TP_CONTENT, Preferences.get(Settings.PREF_BLOCK_TP_CONTENT));

				obj.put(Settings.PREF_SOCIAL_VK, Preferences.get(Settings.PREF_SOCIAL_VK));
				obj.put(Settings.PREF_SOCIAL_FB, Preferences.get(Settings.PREF_SOCIAL_FB));
				obj.put(Settings.PREF_SOCIAL_TWITTER, Preferences.get(Settings.PREF_SOCIAL_TWITTER));
				obj.put(Settings.PREF_SOCIAL_OK, Preferences.get(Settings.PREF_SOCIAL_OK));
				obj.put(Settings.PREF_SOCIAL_MAILRU, Preferences.get(Settings.PREF_SOCIAL_MAILRU));
				//obj.put(Settings.PREF_SOCIAL_LJ, Preferences.get(Settings.PREF_SOCIAL_LJ));
				obj.put(Settings.PREF_SOCIAL_GPLUS, Preferences.get(Settings.PREF_SOCIAL_GPLUS));
				obj.put(Settings.PREF_SOCIAL_LINKEDIN, Preferences.get(Settings.PREF_SOCIAL_LINKEDIN));
				obj.put(Settings.PREF_SOCIAL_MOIKRUG, Preferences.get(Settings.PREF_SOCIAL_MOIKRUG));
				obj.put(Settings.PREF_SOCIAL_OTHER, Preferences.get(Settings.PREF_SOCIAL_OTHER));

				//obj.put(Settings.PREF_SEE_FIREWALL_OPTS, Preferences.get(Settings.PREF_SEE_FIREWALL_OPTS));
				obj.put(Settings.PREF_SEE_ADVANCED_OPTS, Preferences.get(Settings.PREF_SEE_ADVANCED_OPTS));
				obj.put(Settings.PREF_SEE_BLOCK_APPS_DATA, Preferences.get(Settings.PREF_SEE_BLOCK_APPS_DATA));
				obj.put(Settings.PREF_SEE_BLOCK_TP_CONTENT, Preferences.get(Settings.PREF_SEE_BLOCK_TP_CONTENT));

				obj.put(Settings.PREF_USER_TOKEN, Preferences.get_s(Settings.PREF_USER_TOKEN));
				obj.put(Settings.PREF_USER_TOKEN_TIME, Preferences.get_l(Settings.PREF_USER_TOKEN_TIME));
				obj.put(Settings.PREF_RECOVERY_STATUS, Preferences.get_i(Settings.PREF_RECOVERY_STATUS));
				obj.put(Settings.PREF_SUBSCRIPTION_WAS, Preferences.get(Settings.PREF_SUBSCRIPTION_WAS));
				obj.put(Settings.PREF_SUBSCRIPTION_EXPIRED, Preferences.get(Settings.PREF_SUBSCRIPTION_EXPIRED));
				obj.put(Settings.PREF_APP_ADBLOCK, Preferences.get(Settings.PREF_APP_ADBLOCK));
				//obj.put(Settings.PREF_REFERRER, Preferences.get_s(Settings.PREF_REFERRER));
				obj.put(Settings.PREF_REFERRER_RECOVERY, Preferences.get(Settings.PREF_REFERRER_RECOVERY));

				obj.put(Settings.PREF_FIRST_START_TIME, Preferences.get_l(Settings.PREF_FIRST_START_TIME));
				obj.put(Settings.PREF_LAST_UPDATE_TIME, Preferences.get_l(Settings.PREF_LAST_UPDATE_TIME));
				obj.put(Settings.PREF_UPDATE_ERROR_COUNT, Preferences.get_i(Settings.PREF_UPDATE_ERROR_COUNT));
				obj.put(Settings.PREF_LAST_FASTUPDATE_TIME, Preferences.get_l(Settings.PREF_LAST_FASTUPDATE_TIME));
				obj.put(Settings.PREF_FASTUPDATE_ERROR_COUNT, Preferences.get_i(Settings.PREF_FASTUPDATE_ERROR_COUNT));
				obj.put(Settings.PREF_UPDATE_TIME, Preferences.get_l(Settings.PREF_UPDATE_TIME));
				obj.put(Settings.PREF_NEED_UPDATE, Preferences.get_s(Settings.PREF_NEED_UPDATE));
				obj.put(Settings.PREF_CLEARCACHES_TIME, Preferences.get_l(Settings.PREF_CLEARCACHES_TIME));
				obj.put(Settings.PREF_DISABLE_TIME, Preferences.get_l(Settings.PREF_DISABLE_TIME));
				obj.put(Settings.PREF_EVALUATE_STATUS, Preferences.get_i(Settings.PREF_EVALUATE_STATUS));
				obj.put(Settings.PREF_FEEDBACK_STATUS, Preferences.get_i(Settings.PREF_FEEDBACK_STATUS));
				obj.put(Settings.PREF_FEEDBACK2_STATUS, Preferences.get_i(Settings.PREF_FEEDBACK2_STATUS));
				obj.put(Settings.PREF_FIRSTRESULT_STATUS, Preferences.get_i(Settings.PREF_FIRSTRESULT_STATUS));

				obj.put(Settings.STATS_APP_CERTHASH, appCertHash);

				return obj;
			}
			catch (JSONException e) { e.printStackTrace(); }
		}
		finally { Preferences.editEnd(); }

		return null;
	}

	//

	public static int getInt(String name, int def)
	{
		editLock.lock();
		try
		{
			return prefs.getInt(name, def);
		}
		finally { editLock.unlock(); }
	}

    public static int get_i(String name)
    {
        int def = 0;

        if (name.equals(Settings.PREF_UPDATE_ERROR_COUNT))
            // use such value to force updates if no network on install (will be increased by 2 or 3 if no network)
            def = -1111;

        editLock.lock();
        try
        {
            return prefs.getInt(name, def);
        }
        finally { editLock.unlock(); }
    }

	public static long getLong(String name, long def)
	{
		editLock.lock();
		try
		{
			return prefs.getLong(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static long get_l(String name)
	{
		long def = 0;

		editLock.lock();
		try
		{
			return prefs.getLong(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static boolean getBoolean(String name, boolean def)
	{
		editLock.lock();
		try
		{
			return prefs.getBoolean(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static boolean get(String name)
	{
		boolean def = false;

		if (name.equals(Settings.PREF_BLOCK_MALICIOUS) ||
			name.equals(Settings.PREF_BLOCK_APKS) ||

			name.equals(Settings.PREF_SOCIAL_OTHER) ||
			name.equals(Settings.PREF_SOCIAL_GPLUS) ||
			name.equals(Settings.PREF_SOCIAL_VK) ||
			name.equals(Settings.PREF_SOCIAL_FB) ||
			name.equals(Settings.PREF_SOCIAL_TWITTER) ||
			name.equals(Settings.PREF_SOCIAL_OK) ||
			name.equals(Settings.PREF_SOCIAL_MAILRU) ||
			name.equals(Settings.PREF_SOCIAL_LINKEDIN) ||
			name.equals(Settings.PREF_SOCIAL_MOIKRUG) ||

			name.equals(Settings.PREF_ANONYMIZE_ONLY_BRW) ||
			//name.equals(Settings.PREF_CHANGE_USERAGENT) ||
			//name.equals(Settings.PREF_ALLOW_SOME_ADS) ||
			name.equals(Settings.PREF_STATS))
		{
			def = true;
		}

		editLock.lock();
		try
		{
			return prefs.getBoolean(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static String getString(String name, String def)
	{
		editLock.lock();
		try
		{
			return prefs.getString(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static String get_s(String name)
	{
		String def = null;

		if (name.equals(Settings.PREF_BASES_VERSION)) def = "0";
		else if (name.equals(Settings.PREF_PROXY_COUNTRY)) def = "auto";

		editLock.lock();
		try
		{
			return prefs.getString(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static Set<String> getStrings(String name, Set<String> def)
	{
		editLock.lock();
		try
		{
			return prefs.getStringSet(name, def);
		}
		finally { editLock.unlock(); }
	}

	public static void editStart() {
		editLock.lock();

		if (lockCounter == 0) {
//			editorShared = prefsShared.edit();
			editor = prefs.edit();
		}
		lockCounter++;
	}

	public static void editEnd() {
		lockCounter--;
		if (lockCounter == 0) {
//			commit(editorShared);
			commit(editor); // TODO XXX ahaha, was java.lang.ArrayIndexOutOfBoundsException on texet
//			editorShared = null;
			editor = null;
		}

		editLock.unlock();
	}

	private static boolean commit(SharedPreferences.Editor editor)
	{
		boolean result = true;

		// TODO XXX apply is faster (commit could take up to 100ms for writing to the XML backed file)
		// but apply don't guarantee settings save if app killed by OOM or crashed

		if (App.isUIThread())
			editor.apply();
		else
			result = editor.commit();

		return result;
	}

	public static void putBoolean(String name, boolean value) //, SharedPreferences... sharedPreferences)
	{
		editLock.lock();
		try
		{
			if (Settings.DEBUG) L.d(Settings.TAG_PREFERENCES, "'" + name + "' -> " + value);

//			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit();

			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit(); // prefs.edit();
//			SharedPreferences.Editor eShared = (editorShared != null) ? editorShared : prefsShared.edit(); // prefsShared.edit();


			e.putBoolean(name, value);
//			eShared.putBoolean(name, value);

			if (editor == null) {
				commit(e);
			}
//			if (editorShared == null) {
//				commit(eShared);
//			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			editLock.unlock();
		}

	}

	public static void putInt(String name, int value) //, SharedPreferences... sharedPreferences)
	{
		editLock.lock();
		try
		{
			if (Settings.DEBUG) L.d(Settings.TAG_PREFERENCES, "'" + name + "' -> " + value);

//			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit();
			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit(); // prefs.edit();
//			SharedPreferences.Editor eShared = (editorShared != null) ? editorShared : prefsShared.edit(); // prefsShared.edit();

			e.putInt(name, value);
//			eShared.putInt(name, value);

			if (editor == null) {
				commit(e);
			}
//			if (editorShared == null) {
//				commit(eShared);
//			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			editLock.unlock();
		}

	}

	public static void putString(String name, String value)
	{
		editLock.lock();
		try
		{
			if (Settings.DEBUG) L.d(Settings.TAG_PREFERENCES, "'" + name + "' -> '" + value + "'");

			//			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit();
			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit(); // prefs.edit();
//			SharedPreferences.Editor eShared = (editorShared != null) ? editorShared : prefsShared.edit(); // prefsShared.edit();

			e.putString(name, value);
//			eShared.putString(name, value);

			if (editor == null) {
				commit(e);
			}
//			if (editorShared == null) {
//				commit(eShared);
//			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			editLock.unlock();
		}

	}

/*
	public static void putStrings(String name, HashSet<String> strings)
	{
		editLock.lock();
		try
		{
			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit();
			e.putStringSet(name, strings);
			if (editor == null)
				commit(e);
		}
		finally { editLock.unlock(); }
	}
*/

	public static void putLong(String name, long value)
	{
		editLock.lock();
		try
		{
			if (Settings.DEBUG) L.d(Settings.TAG_PREFERENCES, "'" + name + "' -> " + value);

//			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit();

			SharedPreferences.Editor e = (editor != null) ? editor : prefs.edit(); // prefs.edit();
//			SharedPreferences.Editor eShared = (editorShared != null) ? editorShared : prefsShared.edit(); // prefsShared.edit();

			e.putLong(name, value);
//			eShared.putLong(name, value);

			if (editor == null) {
				commit(e);
			}
//			if (editorShared == null) {
//				commit(eShared);
//			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			editLock.unlock();
		}

	}
}
