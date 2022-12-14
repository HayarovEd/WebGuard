package app.ui;

import app.internal.Preferences;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.*;
import android.view.Gravity;
import android.widget.Switch;

import app.internal.PublisherSettings;
import app.netfilter.FilterVpnService;
import app.netfilter.IFilterVpnApplication;
import app.netfilter.IFilterVpnPolicy;
import app.netfilter.proxy.BlockIPCache;
import app.common.NetUtil;
import app.common.Utils;
import app.common.debug.DebugUtils;
import app.*;
import app.internal.ProxyBase;
import app.security.Policy;
import app.common.debug.L;
import app.internal.InAppBilling;
import app.internal.Settings;


public class OptionsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

    public static final String BUY = "buy";

    private RootPrefSwitch rootSwitch = null;
    private IFilterVpnPolicy policy = null;
    private boolean preferencesResourceLoaded = false;
    private boolean startActionsCalled = false;
    private Activity currentActivity = null;

    private static boolean onForced = false;

    private OnSharedPreferenceChangeListener listener =
            new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    onSharedPreferenceChangedInternal(prefs, null, key);
                }
            };

    @SuppressLint("ResourceType")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load the preferences from an XML resource
        // Roman Popov 17.02.2017
        //noinspection ResourceType
        addPreferencesFromResource(Res.layout.preferences);
        preferencesResourceLoaded = true;

        Activity activity = getActivity();
        ActionBar actionbar = activity.getActionBar();
        Switch actionBarSwitch = new Switch(activity);

		/*
		actionBarSwitch.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				if ((event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) > 0)
				{
					Toasts.showToast("Some overlay program will prevent you to activate VPN functionality\n" +
										"Before switching VPN you should disable that app.");
					return true;
				}
				return false;
			}
		});
		*/

        float scale = getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (10 /*dp*/ * scale + 0.5f);
        //actionBarSwitch.setPadding(0, 0, dpAsPixels, 0);

        ActionBar.LayoutParams lp =
                new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT,
//										  ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT, // no title!
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        lp.setMargins(dpAsPixels, dpAsPixels, dpAsPixels, dpAsPixels);

        actionbar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);
        actionbar.setCustomView(actionBarSwitch, lp);

        actionbar.setTitle(Res.string.app_name);

        rootSwitch = new RootPrefSwitch((OptionsActivity) getActivity(), actionBarSwitch, new RootPrefSwitch.OnChangedPowerListener() {
            @Override
            public void onChangedPower() {
                onSharedPreferenceChangedInternal(null, null, Settings.PREF_ACTIVE);
            }
        });

        // TODO XXX move to onCreateActions
        final String curUpdate = Preferences.get_s(Settings.PREF_NEED_UPDATE);
        if (curUpdate != null && (curUpdate.equals("block") || curUpdate.equals("finalblock")))
            actionBarSwitch.setEnabled(false);

        onCreateActions();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        final Bundle bundle = getArguments();
        if (bundle != null) {
            if (bundle.containsKey(BUY)) {
                Preference prefBuy = findPreference(Settings.PREF_BUY_SUBSCRIPTION);
                onSubscriptionBuy(prefBuy);
            }
        }

    }

    private void onCreateActions() {
        if (startActionsCalled) // TODO XXX add lock
            return;
        startActionsCalled = true;

        policy = ((IFilterVpnApplication) getCurrentActivity().getApplication()).getPolicy();

        if (PublisherSettings.FIRSTRUN_AUTOSTART) { // if (Settings.FIRSTRUN_AUTOSTART) {
            if (App.isFirstRun() && !onForced) {
                onForced = true;
                Preferences.putBoolean(Settings.PREF_ACTIVE, true);
            }
        }

        updateSettings(true); // and onResume
    }

    // call this method if want to use options without OptionsActivity
    public void setCurrentActivity(Activity activity) {
        currentActivity = activity;
        onCreateActions();
    }

    public Activity getCurrentActivity() {
        if (currentActivity != null)
            return currentActivity;
        else
            return getActivity();
    }

    @Override
    public void onStart() {
        super.onStart();

        //PreferenceManager.getDefaultSharedPreferences(App.getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        //PreferenceManager.getDefaultSharedPreferences(App.getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // TODO XXX why not onStart?
        //PreferenceManager.getDefaultSharedPreferences(App.getContext()).registerOnSharedPreferenceChangeListener(this);
        PreferenceManager.getDefaultSharedPreferences(App.getContext()).registerOnSharedPreferenceChangeListener(listener);
        //getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        //getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);

        if (rootSwitch != null)
            rootSwitch.resume();
        updateSettings(false);
    }

    @Override
    public void onPause() {
        super.onPause();

        // TODO XXX why not onStop?
        //PreferenceManager.getDefaultSharedPreferences(App.getContext()).unregisterOnSharedPreferenceChangeListener(this);
        PreferenceManager.getDefaultSharedPreferences(App.getContext()).unregisterOnSharedPreferenceChangeListener(listener);
        //getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        //getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);

        if (rootSwitch != null)
            rootSwitch.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void updateSettings() {
        updateSettings(false);
    }

    private void updateSettings(boolean onCreate) {
        //if (rootSwitch == null)
        //	return;

        L.d(Settings.TAG_OPTIONSFRAGMENT, "updateSettings");

        PreferenceScreen screen = (!preferencesResourceLoaded) ? null : getPreferenceScreen();
        updateSettingsClicks(screen); // register options clicks

        Preference pref;
        PreferenceCategory category;

        boolean activated = Preferences.isActive();
        boolean debugBuild = Policy.isDebugBuild();
        String token = Policy.getUserToken(true);
        boolean haveToken = (token != null);
        boolean freeToken = Policy.isFreeToken(token);
        boolean freeUser = Policy.isFreeUser();

        //

        Preferences.editStart();

        boolean prefCollectDebugInfo = Preferences.get(Settings.PREF_COLLECT_DEBUGINFO);
        boolean prefUseCompression = Preferences.get(Settings.PREF_USE_COMPRESSION);
        boolean prefAnonymize = Preferences.get(Settings.PREF_ANONYMIZE);
        boolean prefAnonymizeOnlyBrowsers = Preferences.get(Settings.PREF_ANONYMIZE_ONLY_BRW);
        boolean prefBlockMalicious = Preferences.get(Settings.PREF_BLOCK_MALICIOUS);
        boolean prefBlockApks = Preferences.get(Settings.PREF_BLOCK_APKS);
        boolean prefUseLightTheme = Preferences.get(Settings.PREF_USE_LIGHT_THEME);
        boolean prefChangeUseragent = Preferences.get(Settings.PREF_CHANGE_USERAGENT);
        boolean prefDesktopUseragent = Preferences.get(Settings.PREF_DESKTOP_USERAGENT);
        boolean prefBlockTpContent = Preferences.get(Settings.PREF_BLOCK_TP_CONTENT);


//		System.out.println("TEST prefCollectDebugInfo : " +prefCollectDebugInfo);
//		System.out.println("TEST prefUseCompression : " +prefUseCompression);
//		System.out.println("TEST prefAnonymize : " +prefAnonymize);
//		System.out.println("TEST prefAnonymizeOnlyBrowsers : " +prefAnonymizeOnlyBrowsers);
//		System.out.println("TEST prefBlockMalicious : " +prefBlockMalicious);
//		System.out.println("TEST prefBlockApks : " +prefBlockApks);
//		System.out.println("TEST prefUseLightTheme : " +prefUseLightTheme);
//		System.out.println("TEST prefChangeUseragent : " +prefChangeUseragent);
//		System.out.println("TEST prefDesktopUseragent : " +prefDesktopUseragent);
//		System.out.println("TEST prefBlockTpContent : " +prefBlockTpContent);


        //boolean prefBlockAppsData = Preferences.get(Settings.PREF_BLOCK_APPS_DATA);

//		boolean prefUseRu = Preferences.get(Settings.PREF_USE_RU_LANG);
//		boolean prefUseEn = Preferences.get(Settings.PREF_USE_EN_LANG);


        Preferences.editEnd();

        // enable/disable options

        int count = (screen == null) ? -1 : screen.getPreferenceCount();
        for (int i = 0; i <= count; i++) {
            pref = (i < count) ? screen.getPreference(i) :
                    screen.findPreference(Settings.PREF_ADVANCED_OPTS); // TODO XXX advanced ui hook

            int j = 0, c = 1;
            PreferenceGroup pgroup = (pref instanceof PreferenceGroup) ? (PreferenceGroup) pref : null;
            PreferenceScreen pscreen = (pref instanceof PreferenceScreen) ? (PreferenceScreen) pref : null;

            String key = pref.getKey();
            //if (Settings.PREF_ADVANCED_OPTS.equals(key))
            //	  key = (key == null) ? null : key;

            if ((pgroup != null || pscreen != null) &&
                    (key == null || key.equals(Settings.PREF_ADVANCED_OPTS))) {
                // have PreferenceCategory without key, iterate through child keys
                // TODO XXX if category need key (e.g. "other" or PREF_ADVANCED_OPTS)?

                c = (pgroup != null) ? pgroup.getPreferenceCount() : pscreen.getPreferenceCount();
                if (c == 0)
                    continue;

                pref = (pgroup != null) ? pgroup.getPreference(j) : pscreen.getPreference(j);
            }

            while (true) {
                key = pref.getKey();
                if (key == null)
                    break;

                //L.a(Settings.TAG_OPTIONSFRAGMENT, pref.getKey());

                boolean enable = activated;
                if (enable && !haveToken && !freeUser)
                    enable = false;
                //if (key.equals("pref_advanced_screen"))
                //	  enable = activated;

                //
                if (key.equals(Settings.PREF_FEEDBACK_SCREEN) ||
                        key.equals(Settings.PREF_STATS_SCREEN) ||
                        key.equals(Settings.PREF_BUY_SUBSCRIPTION) ||
                        key.equals(Settings.PREF_CHECK_SUBSCRIPTION) ||
                        key.equals(Settings.PREF_HELP_SCREEN) ||
                        key.equals(Settings.PREF_PRIVACY_SCREEN) ||
                        key.equals(Settings.PREF_ADVANCED_OPTS) ||
                        key.equals(Settings.PREF_ADVANCED_UI) ||
                        key.equals(Settings.PREF_ADVANCED_OTHER)) {
                    // enable always active options
                    enable = true;
                } else if (key.equals(Settings.PREF_DEBUG_SCREEN)) {
                    // show debug options
                    if (activated && !debugBuild)
                        enable = false;
                } else if (key.equals(Settings.PREF_FIREWALL_SCREEN) ||
                        key.equals(Settings.PREF_PROXY_COUNTRY) ||
                        key.equals(Settings.PREF_USE_COMPRESSION) ||
                        key.equals(Settings.PREF_ANONYMIZE) ||
                        key.equals(Settings.PREF_BLOCK_APPS_DATA)) {
                    // options only with token
                    if (activated && !haveToken)
                        enable = false;
                } else if (key.equals(Settings.PREF_ANONYMIZE_ONLY_BRW)) {
                    // if anonymize or compression enabled
                    boolean e = (prefUseCompression || prefAnonymize);
                    if (activated && (!e || !haveToken))
                        enable = false;
                }

                pref.setEnabled(enable);

                if (++j >= c) // c > 1, iterate through PreferenceCategory keys
                    break;

                pref = (pgroup != null) ? pgroup.getPreference(j) : pscreen.getPreference(j);
            }
        } // for

        if (screen != null) {
            if (!debugBuild) {
                pref = screen.findPreference(Settings.PREF_DEBUG_SCREEN);
                if (pref != null)
                    screen.removePreference(pref);
            }
            if (haveToken && !Settings.DEBUG_BUYGUI) {
                // disable buy option if have normal (not free) token
                pref = screen.findPreference(Settings.PREF_BUY_SUBSCRIPTION);
                if (haveToken && !freeToken)
                    pref.setEnabled(false);
                //if (pref != null)
                //	  category.removePreference(pref); // + find other category
            }
        }

        // need subscrition for all functionality, check token and other

        if (activated && !haveToken && !freeUser) {
            if (!onCreate) {
                Preferences.putBoolean(Settings.PREF_ACTIVE, false);
                onSubscriptionCheck(null);
            }
        } else {
            if (rootSwitch != null)
                rootSwitch.update();
        }

        // send message, stats, firewall, help

        if (screen != null) {
            pref = screen.findPreference(Settings.PREF_FEEDBACK_SCREEN);
            pref.setIntent(new Intent(App.getContext(), SendMessageActivity.class));

            // Roman Popov
            pref = screen.findPreference(Settings.PREF_STATS_SCREEN);
            pref.setIntent(new Intent(App.getContext(), StatisticsActivity.class));

            // Roman Popov
            pref = screen.findPreference(Settings.PREF_FIREWALL_SCREEN);
            pref.setIntent(new Intent(App.getContext(), FirewallActivity.class));

            pref = screen.findPreference(Settings.PREF_HELP_SCREEN);
            pref.setIntent(new Intent(App.getContext(), HelpActivity.class));

            // Privacy Policy (Roman Popov 01.05.2017)
            pref = screen.findPreference(Settings.PREF_PRIVACY_SCREEN);
            Uri uri = Uri.parse(Preferences.getPrivacyUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            pref.setIntent(intent);

            // update Debug options (could be changed outside GUI)

            pref = screen.findPreference(Settings.PREF_COLLECT_DEBUGINFO);
            ((CheckBoxPreference) pref).setChecked(prefCollectDebugInfo);

            // update Pro options (could be changed outside GUI)

            pref = screen.findPreference(Settings.PREF_USE_COMPRESSION);
            ((CheckBoxPreference) pref).setChecked(prefUseCompression);

            pref = screen.findPreference(Settings.PREF_ANONYMIZE);
            ((CheckBoxPreference) pref).setChecked(prefAnonymize);

            //Roman Popov
            pref = screen.findPreference(Settings.PREF_ANONYMIZE_ONLY_BRW);
            ((CheckBoxPreference) pref).setChecked(prefAnonymizeOnlyBrowsers);

            pref = screen.findPreference(Settings.PREF_BLOCK_MALICIOUS);
            ((CheckBoxPreference) pref).setChecked(prefBlockMalicious);

            pref = screen.findPreference(Settings.PREF_BLOCK_APKS);
            ((CheckBoxPreference) pref).setChecked(prefBlockApks);

            pref = screen.findPreference(Settings.PREF_USE_LIGHT_THEME);
            ((CheckBoxPreference) pref).setChecked(prefUseLightTheme);

            pref = screen.findPreference(Settings.PREF_CHANGE_USERAGENT);
            ((CheckBoxPreference) pref).setChecked(prefChangeUseragent);

            pref = screen.findPreference(Settings.PREF_DESKTOP_USERAGENT);
            ((CheckBoxPreference) pref).setChecked(prefDesktopUseragent);

            pref = screen.findPreference(Settings.PREF_BLOCK_TP_CONTENT);
            ((CheckBoxPreference) pref).setChecked(prefBlockTpContent);

            //////


            pref = screen.findPreference(Settings.PREF_SOCIAL_OTHER);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_OTHER));
            pref = screen.findPreference(Settings.PREF_SOCIAL_GPLUS);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_GPLUS));
            pref = screen.findPreference(Settings.PREF_SOCIAL_VK);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_VK));
            pref = screen.findPreference(Settings.PREF_SOCIAL_FB);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_FB));
            pref = screen.findPreference(Settings.PREF_SOCIAL_TWITTER);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_TWITTER));
            pref = screen.findPreference(Settings.PREF_SOCIAL_OK);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_OK));
            pref = screen.findPreference(Settings.PREF_SOCIAL_MAILRU);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_MAILRU));
            pref = screen.findPreference(Settings.PREF_SOCIAL_LINKEDIN);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_LINKEDIN));
            pref = screen.findPreference(Settings.PREF_SOCIAL_MOIKRUG);
            ((CheckBoxPreference) pref).setChecked(Preferences.get(Settings.PREF_SOCIAL_MOIKRUG));


            //pref = screen.findPreference(Settings.PREF_BLOCK_APPS_DATA);
            //((CheckBoxPreference) pref).setChecked(prefBlockAppsData);
        }

        // proxy country options

        Context c = App.getContext();
        Resources res = c.getResources();
        CharSequence[] list = ProxyBase.getAvailableServers();
        CharSequence[] names = new CharSequence[list.length];
        int[] drawables = new int[list.length];
        int i = 0;
        //String curServ = Preferences.getString(Settings.PREF_PROXY_COUNTRY, "auto");
        String curServ = Preferences.get_s(Settings.PREF_PROXY_COUNTRY);
        int curServNameId = 0;
        int curServIconId = 0;
        for (CharSequence serv : list) {
            int id = res.getIdentifier("proxy_country_" + serv, "string", App.packageName());
            if (serv.equals(curServ))
                curServNameId = id;
            names[i] = c.getString(id);
            id = res.getIdentifier("country_" + serv, "drawable", App.packageName());
            drawables[i] = id;
            if (serv.equals(curServ))
                curServIconId = id;
            i++;
        }

        if (screen != null) {
            ImageListPreference iprefs = (ImageListPreference) screen.findPreference(Settings.PREF_PROXY_COUNTRY);
            iprefs.setEntryValues(list);
            iprefs.setEntries(names);
            iprefs.setImageResources(drawables);
            if (curServNameId != 0) {
                iprefs.setSummary(curServNameId);
                iprefs.setIcon(curServIconId);
            }
        }

        // advanced, lang options (hide/show)

//		String lang = App.getSysLang();
//		String prefName;

//		if (!prefUseEn && (prefUseRu || lang == null || !lang.equals("ru")))
//			prefName = Settings.PREF_USE_EN_LANG; // remove "to english" switch
//		else
//			prefName = Settings.PREF_USE_RU_LANG; // remove "to russian" switch

//		if (screen != null)
//		{
//			category = (PreferenceCategory) screen.findPreference(Settings.PREF_ADVANCED_UI);
//			pref = category.findPreference(prefName);
//			if (pref != null)
//				category.removePreference(pref);
//		}

        L.d(Settings.TAG_OPTIONSFRAGMENT, "updateSettings finish");
    }

    private void updateSettingsClicks(PreferenceScreen screen) {
        registerClick(screen, Settings.PREF_CHECK_SUBSCRIPTION); // subscription check
        registerClick(screen, Settings.PREF_BUY_SUBSCRIPTION);     // subscription buy

        registerClick(screen, Settings.PREF_ADVANCED_OPTS);         // advanced options warning

        registerClick(screen, Settings.PREF_CLEAN_CACHES);         // advanced options clean caches

        registerClick(screen, Settings.PREF_DEBUG_LOG_MARK);     // developer debug options
        registerClick(screen, Settings.PREF_DEBUG_UPDATE_DDB);
        registerClick(screen, Settings.PREF_DEBUG_HPROF);
        registerClick(screen, Settings.PREF_DEBUG_TEST);
    }

    private void registerClick(PreferenceScreen screen, final String key) {
        if (screen == null)
            return;

        Preference pref = screen.findPreference(key);
        if (pref != null && pref.getOnPreferenceClickListener() == null) {
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    onSharedPreferenceChangedInternal(null, preference, key);
                    return true;
                }
            });
        }
    }

    /*
     * process options changing (checkboxes, lists, etc)
     * also process options clicks (must be registred, see updateSettingsClicks)
     *
     * onSharedPreferenceChangedInternal:
     *	   onSharedPreferenceChanged -> prefs, null, key
     *	   onPreferenceClick		 -> null, pref, key
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        onSharedPreferenceChangedInternal(prefs, null, key);
    }

    private void onSharedPreferenceChangedInternal(SharedPreferences prefs, final Preference pref, String key) {
        L.w(Settings.TAG_OPTIONSFRAGMENT, "onSharedPreferenceChanged for ", key);

        // запоминаю по-нормальному выбранное состояние следующих чекбоксов
        if (key.equals(Settings.PREF_USE_COMPRESSION)) {
            Preferences.putBoolean(Settings.PREF_USE_COMPRESSION, prefs.getBoolean(Settings.PREF_USE_COMPRESSION, false));
        } else if (key.equals(Settings.PREF_ANONYMIZE)) {
            Preferences.putBoolean(Settings.PREF_ANONYMIZE, prefs.getBoolean(Settings.PREF_ANONYMIZE, false));
        } else if (key.equals(Settings.PREF_ANONYMIZE_ONLY_BRW)) {
            Preferences.putBoolean(Settings.PREF_ANONYMIZE_ONLY_BRW, prefs.getBoolean(Settings.PREF_ANONYMIZE_ONLY_BRW, true));
        } else if (key.equals(Settings.PREF_BLOCK_MALICIOUS)) {
            Preferences.putBoolean(Settings.PREF_BLOCK_MALICIOUS, prefs.getBoolean(Settings.PREF_BLOCK_MALICIOUS, true));
        } else if (key.equals(Settings.PREF_BLOCK_APKS)) {
            Preferences.putBoolean(Settings.PREF_BLOCK_APKS, prefs.getBoolean(Settings.PREF_BLOCK_APKS, true));
        } else if (key.equals(Settings.PREF_USE_LIGHT_THEME)) {
            Preferences.putBoolean(Settings.PREF_USE_LIGHT_THEME, prefs.getBoolean(Settings.PREF_USE_LIGHT_THEME, false));
        } else if (key.equals(Settings.PREF_CHANGE_USERAGENT)) {
            Preferences.putBoolean(Settings.PREF_CHANGE_USERAGENT, prefs.getBoolean(Settings.PREF_CHANGE_USERAGENT, false));
        } else if (key.equals(Settings.PREF_DESKTOP_USERAGENT)) {
            Preferences.putBoolean(Settings.PREF_DESKTOP_USERAGENT, prefs.getBoolean(Settings.PREF_DESKTOP_USERAGENT, false));
        } else if (key.equals(Settings.PREF_BLOCK_TP_CONTENT)) {
            Preferences.putBoolean(Settings.PREF_BLOCK_TP_CONTENT, prefs.getBoolean(Settings.PREF_BLOCK_TP_CONTENT, false));
        } else if (key.equals(Settings.PREF_COLLECT_DEBUGINFO)) {
            Preferences.putBoolean(Settings.PREF_COLLECT_DEBUGINFO, prefs.getBoolean(Settings.PREF_COLLECT_DEBUGINFO, false));
        } else if (key.equals(Settings.PREF_PROXY_COUNTRY)) {
            Preferences.putString(Settings.PREF_PROXY_COUNTRY, prefs.getString(Settings.PREF_PROXY_COUNTRY, "auto"));
        } else if (key.equals(Settings.PREF_SOCIAL_OTHER)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_OTHER, prefs.getBoolean(Settings.PREF_SOCIAL_OTHER, true));
        } else if (key.equals(Settings.PREF_SOCIAL_GPLUS)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_GPLUS, prefs.getBoolean(Settings.PREF_SOCIAL_GPLUS, true));
        } else if (key.equals(Settings.PREF_SOCIAL_VK)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_VK, prefs.getBoolean(Settings.PREF_SOCIAL_VK, true));
        } else if (key.equals(Settings.PREF_SOCIAL_FB)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_FB, prefs.getBoolean(Settings.PREF_SOCIAL_FB, true));
        } else if (key.equals(Settings.PREF_SOCIAL_TWITTER)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_TWITTER, prefs.getBoolean(Settings.PREF_SOCIAL_TWITTER, true));
        } else if (key.equals(Settings.PREF_SOCIAL_OK)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_OK, prefs.getBoolean(Settings.PREF_SOCIAL_OK, true));
        } else if (key.equals(Settings.PREF_SOCIAL_MAILRU)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_MAILRU, prefs.getBoolean(Settings.PREF_SOCIAL_MAILRU, true));
        } else if (key.equals(Settings.PREF_SOCIAL_LINKEDIN)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_LINKEDIN, prefs.getBoolean(Settings.PREF_SOCIAL_LINKEDIN, true));
        } else if (key.equals(Settings.PREF_SOCIAL_MOIKRUG)) {
            Preferences.putBoolean(Settings.PREF_SOCIAL_MOIKRUG, prefs.getBoolean(Settings.PREF_SOCIAL_MOIKRUG, true));
        }


        // main switch

        if (key.equals(Settings.PREF_ACTIVE)) {
            onMainSwitch();
        }

        //

        else if (key.equals(Settings.PREF_BLOCK_MALICIOUS))
        //key.equals(Settings.PREF_ALLOW_SOME_ADS))
        {
            if (Settings.DNS_IP_BLOCK_CLEAN)
                BlockIPCache.clearOnUpdate();
            else
                BlockIPCache.clear();
        }

        // block apps data enable (+ warning)
/*
		else if (key.equals(Settings.PREF_BLOCK_APPS_DATA))
		{
			boolean enabled = Preferences.get(Settings.PREF_BLOCK_APPS_DATA);
			boolean showed = Preferences.get(Settings.PREF_SEE_BLOCK_APPS_DATA);
			if (enabled)
			{
				if (!showed)
				{
					Dialogs.showBlockAppsDataWarning(getCurrentActivity());
					Preferences.putBoolean(Settings.PREF_SEE_BLOCK_APPS_DATA, true);
				}

				FilterVpnService.notifyDropConnections(getCurrentActivity()); // block apps data except browsers
			}
		}
*/
        // change compression or anonymizing settings

        else if (key.equals(Settings.PREF_PROXY_COUNTRY) ||
                key.equals(Settings.PREF_USE_COMPRESSION) ||
                key.equals(Settings.PREF_ANONYMIZE)) {
            ProxyBase.notifyServersUp();

            // TODO XXX drop connections here, but update country in Policy.reloadPrefs
            // TODO XXX drop all connections even if use proxy only in browsers

            if (key.equals(Settings.PREF_PROXY_COUNTRY))
                FilterVpnService.notifyProxyChanged(getCurrentActivity()); // change proxy
            else
                FilterVpnService.notifyDropConnections(getCurrentActivity()); // enable proxy
        }

        // subscription buy click

        else if (key.equals(Settings.PREF_BUY_SUBSCRIPTION)) {
            onSubscriptionBuy(pref);
        }

        // subscription check

        else if (key.equals(Settings.PREF_CHECK_SUBSCRIPTION)) {
            onSubscriptionCheck(pref);
        }

        // advanced, enter warning

        else if (key.equals(Settings.PREF_ADVANCED_OPTS)) {
            boolean showed = Preferences.getBoolean(Settings.PREF_SEE_ADVANCED_OPTS, false);
            if (!showed) {
                Dialogs.showAdvancedUsersOnlyWarning(getCurrentActivity());
                Preferences.putBoolean(Settings.PREF_SEE_ADVANCED_OPTS, true);
            }
        }

        // advanced, light theme

        else if (key.equals(Settings.PREF_USE_LIGHT_THEME)) {
            Utils.activityReload(getCurrentActivity());
        }

        // advanced, lang options (hide/show)
// отключил возможность переключения с русского на английский тк добавили другие языке
//		else if (key.equals(Settings.PREF_USE_RU_LANG) ||
//					key.equals(Settings.PREF_USE_EN_LANG))
//		{
//			App.changeLanguage(true);
//			Utils.activityReload(getCurrentActivity());
//		}

        // advanced, block thirdparty site requests (+ warning)

        else if (key.equals(Settings.PREF_BLOCK_TP_CONTENT)) {
            boolean enabled = Preferences.getBoolean(Settings.PREF_BLOCK_TP_CONTENT, false);
            boolean showed = Preferences.getBoolean(Settings.PREF_SEE_BLOCK_TP_CONTENT, false);
            if (enabled && !showed) {
                Dialogs.showThirdPartyOptionWarning(getCurrentActivity());
                Preferences.putBoolean(Settings.PREF_SEE_BLOCK_TP_CONTENT, true);
            }
        }

        // advanced, debug

        else if (key.equals(Settings.PREF_COLLECT_DEBUGINFO)) {
            final boolean enabled = Preferences.getBoolean(Settings.PREF_COLLECT_DEBUGINFO, false);
            DebugUtils.switchDebugInfoCollect(enabled, false);
        } else if (key.equals(Settings.PREF_CLEAN_CACHES)) {
            App.cleanCaches(true, false, false, false); // on demand
            //Toasts.showNoNetwork();
        }

        // developer debug options

        else if (key.equals(Settings.PREF_DEBUG_LOG_MARK)) {
            L.a(Settings.TAG_MARK, "MARK");
        } else if (key.equals(Settings.PREF_DEBUG_UPDATE_DDB)) {
            DebugUtils.updateDebugDB(policy, true);
        } else if (key.equals(Settings.PREF_DEBUG_TRACING)) {
            final boolean enabled = Preferences.getBoolean(Settings.PREF_DEBUG_TRACING, false);
            DebugUtils.switchTracing(enabled, false, true);
        } else if (key.equals(Settings.PREF_DEBUG_HPROF)) {
            DebugUtils.dumpHprof("", true);
        } else if (key.equals(Settings.PREF_DEBUG_TCPDUMP_WL)) {
            final boolean enabled = Preferences.getBoolean(Settings.PREF_DEBUG_TCPDUMP_WL, false);
            DebugUtils.dumpTraffic(enabled, true, true);
        } else if (key.equals(Settings.PREF_DEBUG_TCPDUMP_TUN)) {
            final boolean enabled = Preferences.getBoolean(Settings.PREF_DEBUG_TCPDUMP_TUN, false);
            DebugUtils.dumpTraffic(enabled, false, true);
        } else if (key.equals(Settings.PREF_DEBUG_DUMP_TUN)) {
            final boolean enabled = Preferences.getBoolean(Settings.PREF_DEBUG_DUMP_TUN, false);
            DebugUtils.dumpTunTraffic(enabled, true);
        } else if (key.equals(Settings.PREF_DEBUG_TEST)) {
            Notifications.showSubsExpired();

            // http://stackoverflow.com/questions/19022773/which-intent-for-settings-data-usage
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$DataUsageSummaryActivity"));
                startActivity(i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //

        Policy.reloadPrefs();

        // TODO XXX recursion? locks? may be remove thread?
        new Thread(new Runnable() {
            @Override
            public void run() {
                //Utils.sleep(150); // wtf?
                final Activity activity = getCurrentActivity();
                Utils.runOnUiThread(activity, new Runnable() {
                    @Override
                    public void run() {
                        updateSettings(false);
                    }
                });
            }
        }).start();

        L.w(Settings.TAG_OPTIONSFRAGMENT, "onSharedPreferenceChanged for ", key, " finished");
    }

    private void onMainSwitch() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("onSharedPreferenceChanged active");
                //Utils.sleep(100); // wtf?

                if (!Preferences.isActive()) {
                    //((OptionsActivity) getCurrentActivity()).stopVPN();
                    App.disable();

                    Preferences.setNotifyDisabledAlarm(false); // for disabled notification
                    Notifications.hideProxySelectError(getCurrentActivity());
                    //Notifications.hideLicenseCheckError(getCurrentActivity());
                    TimerService.updateSettingsStartTimer(true);
                } else {
//                    ((OptionsActivity) getCurrentActivity()).startVPN(); // fixed by Roman Popov
                    OptionsActivity.startVPN(getCurrentActivity()); // added by Roman Popov

                    Preferences.setNotifyDisabledAlarm(true); // for disabled notification
                    TimerService.updateSettingsStartTimer(false);

                    UpdaterService.startUpdate(UpdaterService.START_FORCE_DELAYED); // switch on
                }
            }
        }).start();
    }

    public void onSubscriptionBuy(final Preference preference) {
        if (NetUtil.getStatus() == -1) {
            Toasts.showNoNetwork();
            return;
        }

        //App.getBilling().serviceUnbind(); // for tests

        final ProgressDialog dialog = progressDialogShow(preference, false, false);
        if (!App.getBilling().serviceIsBound())
            App.getBilling().serviceBind();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                Utils.runOnUiThread(activity, new Runnable() {
                    @Override
                    public void run() {
                        App.getBilling().serviceBindAndWait(false);
                        progressDialogCancel(activity, dialog, preference, false);

                        App.getBilling().itemsLoadAndShowBuyDialog(activity);


                        //BuyDialogActivity.showAbove(getCurrentActivity());
                        //clearProxySettings();
                    }
                });
            }
        }).start();
    }

    // check subscription on gui option click (or on switch on if preference == null)
    public void onSubscriptionCheck(final Preference preference) {
        Notifications.hideLicenseCheckError(getCurrentActivity());

        if (NetUtil.getStatus() == -1) {
            if (preference == null)
                Dialogs.showNoNetwork(getCurrentActivity());
            else
                Toasts.showNoNetwork();

            return;
        }

        // TODO XXX create new thread to check license and show dialog
        // if dialog canceled did't stop thread (but stop billing!)

        //Preferences.putString(Preferences.PREF_USER_TOKEN, null);
        //Toasts.showSubscriptionChecking();

        final ProgressDialog dialog = progressDialogShow(preference, (preference == null), true);

        if (!App.getBilling().serviceIsBound())
            App.getBilling().serviceBind();

        new Thread(new Runnable() {
            @Override
            public void run() {
                //Utils.sleep(1500); // wtf?
                final Activity activity = getCurrentActivity();

                if (PublisherSettings.PUBLISHER.equals(Settings.PUBLISHERS.SAMSUNG)) {

                    Utils.runOnUiThread(activity, new Runnable() {
                        @Override
                        public void run() {
                            progressDialogCancel(activity, dialog, preference, true);
                        }
                    });

                    final InAppBilling billing = App.getBilling();
// Roman Popov
//                    billing.licenseCheck((preference != null), new InAppBilling.OnLicenseChecked() {
//                        @Override
//                        public void onLicenseChecked() {
//
//                            // process check result
//
//                            Utils.runOnUiThread(activity, new Runnable() {
//                                @Override
//                                public void run() {
//
//                                    String token = Policy.getUserToken(true);
//                                    boolean isFree = Policy.isFreeUser();
//
//                                    if (token != null) {
//                                        if (preference != null)
//                                            Toasts.showSubscriptionOk();
//                                    } else {
//                                        Preferences.clearProFunctions(false);
//
//                                        if (Preferences.getBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, false)) {
//                                            Toasts.showSubscriptionExpired();
//                                        } else {
//                                            // don't show missing subs msg if check on switch on
//                                            if (preference != null)
//                                                Toasts.showSubscriptionNotFound();
//                                        }
//                                    }
//
//                                    // show or not buy dialog (if call on enable switch)
//                                    if (preference == null) {
//                                        if (token == null && !isFree) {
//                                            // show buy/tryfree dialog if no token and not free user
//                                            SubsNeedDialogActivity.showAbove(getCurrentActivity());
//                                        } else {
//                                            // TODO XXX copy/paste from SubsNeedDialogActivity.onActivityResult
//                                            Activity a = getCurrentActivity();
//                                            if (a instanceof OptionsActivity) {
//                                                Preferences.putBoolean(Settings.PREF_ACTIVE, true);
//                                                ((OptionsActivity) a).startVPN();
//                                            }
//                                        }
//                                    }
//
//                                    updateSettings(false);
//                                }
//                            });
//
//
//                        }
//                    });

                } else {

                    if (!App.getBilling().serviceBindAndWait(false)) {
                        // show google play connect error

                        Utils.runOnUiThread(activity, new Runnable() {
                            @Override
                            public void run() {
                                progressDialogCancel(activity, dialog, preference, false);

                                //if (preference == null)
                                //	  Dialogs.showGooglePlayConnectError(activity);
                                //else
                                Toasts.showGooglePlayConnectError();
                            }
                        });

                        return;
                    }

                    // check license

                    final InAppBilling billing = App.getBilling();


                    billing.licenseCheck((preference != null));
                    while (billing.isCheckLicense()) // wait other threads license check
                        Utils.sleep(1000);

                    // process check result

                    Utils.runOnUiThread(activity, new Runnable() {
                        @Override
                        public void run() {
                            if (!progressDialogCancel(activity, dialog, preference, true))
                                return; // dialog already canceled, exit

                            String token = Policy.getUserToken(true);
                            boolean isFree = Policy.isFreeUser();

                            if (token != null) {
                                if (preference != null)
                                    Toasts.showSubscriptionOk();
                            } else {
                                Preferences.clearProFunctions(false);

                                if (Preferences.getBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, false)) {
                                    Toasts.showSubscriptionExpired();
                                } else {
                                    // don't show missing subs msg if check on switch on
                                    if (preference != null)
                                        Toasts.showSubscriptionNotFound();
                                }
                            }

                            // show or not buy dialog (if call on enable switch)
                            if (preference == null) {
                                if (token == null && !isFree) {
                                    // show buy/tryfree dialog if no token and not free user
                                    SubsNeedDialogActivity.showAbove(getCurrentActivity());
                                } else {
                                    // TODO XXX copy/paste from SubsNeedDialogActivity.onActivityResult
                                    //////// fixed by Roman Popov
//                                    Activity a = getCurrentActivity();
//                                    if (a instanceof OptionsActivity) {
//                                        Preferences.putBoolean(Settings.PREF_ACTIVE, true);
//                                        ((OptionsActivity) a).startVPN();
//                                    }
                                    OptionsActivity.startVPN(getCurrentActivity());
                                    ////////
                                }
                            }

                            updateSettings(false);
                        }
                    });

                }


            }
        }).start();
    }

    // show progress dialog and disable preference item (if have)
    private ProgressDialog progressDialogShow(final Preference preference, final boolean isCheckSubscription,
                                              final boolean stopBillingOnCancel) {
        if (preference != null)
            preference.setEnabled(false);

        final ProgressDialog dialog = new ProgressDialog(getCurrentActivity());
        dialog.setIndeterminate(true);
        dialog.setCancelable(true);
        //dialog.setTitle(Res.string.app_name);
        if (isCheckSubscription)
            dialog.setMessage(App.getContext().getResources().getText(Res.string.checking_subscription));
        else
            dialog.setMessage(App.getContext().getResources().getText(Res.string.please_wait));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (stopBillingOnCancel)
                    App.getBilling().stop();
                if (preference != null)
                    preference.setEnabled(true);
            }
        });

        Utils.dialogShow(getCurrentActivity(), dialog);

        return dialog;
    }

    /*
     * close progress dialog and enable preference item (if have)
     * also can skip item enable if dialog not canceled (may be already closed in other thread)
     */
    boolean progressDialogCancel(Activity activity, Dialog dialog, Preference preference,
                                 boolean stopOnCancelFail) {
        boolean canceled = Utils.dialogClose(activity, dialog, true);
        if (!canceled && stopOnCancelFail)
            return false;

        if (preference != null)
            preference.setEnabled(true);

        return canceled;
    }

/*
	// wtf? why separate thread?
	private void clearProxySettings()
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("clearProxySettings()");
				Preferences.clearProFunctions(true);
			}
		}).start();
	}
*/
}
