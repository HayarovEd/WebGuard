package app.ui;

import android.app.Activity;
import android.content.Context;
import app.App;
import app.Res;
import app.info.Statistics;
import app.internal.Preferences;
import app.internal.Settings;


public class Dialogs
{
	/*
	 * show dialog with notification about update (OK + Update), but it can be forcible update (with Update button only)
	 * updateblock - 1, updateupdate - 2, updatefinalblock - 3, updatefinalupdate - 4
	 */
	public static void showNeedUpdate(Activity context, int type)
	{
		if (type < 1 || type > 4)
			return;

		Context c = (context != null) ? context : App.getContext();

		switch (type)
		{
		case 1:
			MessageDialogActivity.show(context, c.getString(Res.string.need_update_block),
										c.getString(Res.string.need_update_block_text),
										c.getString(Res.string.do_update),
										MessageDialogActivity.APP_MARKET_ACTION, true);
			break;

		case 2:
			MessageDialogActivity.show(context, c.getString(Res.string.need_update),
										c.getString(Res.string.need_update_text),
										c.getString(Res.string.do_update),
										MessageDialogActivity.APP_MARKET_ACTION, false);
			break;

		case 3:
			MessageDialogActivity.show(context, c.getString(Res.string.need_finalupdate_block),
										c.getString(Res.string.need_finalupdate_block_text),
										c.getString(Res.string.do_update),
										MessageDialogActivity.APPNEW_MARKET_ACTION, true);
			break;

		case 4:
			MessageDialogActivity.show(context, c.getString(Res.string.need_finalupdate),
										c.getString(Res.string.need_finalupdate_text),
										c.getString(Res.string.do_update),
										MessageDialogActivity.APPNEW_MARKET_ACTION, false);
			break;
		}

		Statistics.addLog(Settings.LOG_DIALOG_NEEDUP);
	}

	//

	// dialog with proposal to try disable proxy, because WebGuard can't work (Proxy->VPN->Inet bug)
	public static void showTryDisableProxy(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.proxy_needs_to_be_disabled),
									c.getString(Res.string.try_to_disable_proxy),
									MessageDialogActivity.DISABLE_PROXY_ACTION, false);

		Statistics.addLog(Settings.LOG_DIALOG_DISPROXY);
	}

	/*
	 * dialog with info about enabled app with proxy and message to disable this app
	 * proxyPackage: proxy package name, or "" if app name missing, or null if package name missing
	 */
	public static void showProxyDeleteError(Activity context, String proxyPackage)
	{
		Context c = (context != null) ? context : App.getContext();
		String message = c.getString(Res.string.proxy_needs_to_be_disabled);

		if (proxyPackage == null)
			message = c.getString(Res.string.proxy_needs_to_be_disabled2);
		else if (proxyPackage.equals(""))
			message = message.replace("###", c.getString(Res.string.app_not_found));
		else
			message = message.replace("###", proxyPackage);

		MessageDialogActivity.show(context, c.getString(Res.string.error), message);

		Statistics.addLog(Settings.LOG_DIALOG_DELPROXY);
	}

	//

	// if user didn't get us vpn rights via VpnDialogs (android feature (bug) with another app transparent activity on screen)
	public static void showVpnRightsCancel(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.got_problems),
									c.getString(Res.string.got_problems_vpn_dialog));

		Statistics.addLog(Settings.LOG_DIALOG_VPNCANCEL);
	}

	// some phones firmware miss VpnDialogs.apk
	public static void showFirmwareVpnUnavailable(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.vpn_unavailable));

		Statistics.addLog(Settings.LOG_DIALOG_VPNMISSED);
	}

	// bug with VPN rights on android 5.0
	public static void showGetVpnBug1900(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.vpn_bug_1900),
									c.getString(Res.string.c0ntinue),
									MessageDialogActivity.MANAGE_VPN_ACTION, true);

		Statistics.addLog(Settings.LOG_DIALOG_VPNBUG1900);
	}

	//

	// error if missing native libs (android bug when lib not installed or miss after update)
	public static void showInstallError(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.installer_error));

		Statistics.addLog(Settings.LOG_DIALOG_INSTALLERR);
	}

	public static void showThirdPartyOptionWarning(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.warning),
									c.getString(Res.string.third_party_warning));

		Statistics.addLog(Settings.LOG_DIALOG_TPWARN);
	}

	public static void showWhatsNew(Activity context, String newsText)
	{
		Context c = (context != null) ? context : App.getContext();

		//MessageDialogActivity.show(context, c.getString(Res.string.what_new), newsText,
		//							  c.getText(Res.string.evaluate).toString(),
		//							  MessageDialogActivity.APP_MARKET_ACTION, false);
		MessageDialogActivity.show(context, c.getString(Res.string.what_new), newsText);

		Statistics.addLog(Settings.LOG_DIALOG_NEWS);
	}

	public static void showEvaluate(Activity context, String evaluateText)
	{
		Preferences.putInt(Settings.PREF_EVALUATE_STATUS, 2); // ok, user click notification (dialog started)

		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.evaluate_app), evaluateText,
									c.getText(Res.string.evaluate).toString(),
									MessageDialogActivity.EVALUATE_ACTION, false);

		Statistics.addLog(Settings.LOG_DIALOG_EVALUATE);
	}

	public static void showFeedback(Activity context, String feedbackText)
	{
		Preferences.putInt(Settings.PREF_FEEDBACK_STATUS, 7); // dialog started

		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.feedback_app), feedbackText,
									c.getText(Res.string.message_to_developer).toString(),
									MessageDialogActivity.FEEDBACK_ACTION, false);

		Statistics.addLog(Settings.LOG_DIALOG_FEEDBACK);
	}

	public static void showFeedback2(Activity context, String feedback2Text)
	{
		Preferences.putInt(Settings.PREF_FEEDBACK2_STATUS, 7); // dialog started

		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.feedback2_app), feedback2Text,
									c.getText(Res.string.message_to_developer).toString(),
									MessageDialogActivity.FEEDBACK2_ACTION, false);

		Statistics.addLog(Settings.LOG_DIALOG_FEEDBACK2);
	}

	public static void showFirstResult(Activity context, String firstResultText)
	{
		Preferences.putInt(Settings.PREF_FIRSTRESULT_STATUS, 7); // dialog started

		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.first_result), firstResultText);

		Statistics.addLog(Settings.LOG_DIALOG_FIRSTRESULT);
	}

	// some users try to rewoke some rights from our app and we crash, so show error
	public static void showPermissionsError(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.no_needed_permissions));

		Statistics.addLog(Settings.LOG_DIALOG_PERMERR);
	}

	// same as Toasts.showNoNetwork
	public static void showNoNetwork(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.you_are_not_connected_to_network));

		Statistics.addLog(Settings.LOG_DIALOG_NONET);
	}

	// same as Toasts.showGooglePlayConnectError
	public static void showGooglePlayConnectError(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.error),
									c.getString(Res.string.cant_connect_to_google_play));

		Statistics.addLog(Settings.LOG_DIALOG_GPERR);
	}

	public static void showAdvancedUsersOnlyWarning(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.warning),
									c.getString(Res.string.advanced_users_only));

		Statistics.addLog(Settings.LOG_DIALOG_ADVWARN);
	}

	public static void showBlockAppsDataWarning(Activity context)
	{
		Context c = (context != null) ? context : App.getContext();

		MessageDialogActivity.show(context, c.getString(Res.string.warning),
									c.getString(Res.string.block_apps_data_warn));

		Statistics.addLog(Settings.LOG_DIALOG_BAWARN);
	}
}
