package app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import app.App;
import app.internal.Preferences;
import app.Res;
import app.common.Utils;
import app.common.WiFi;
import app.internal.Settings;

public class MessageDialogActivity extends Activity
{
	public static final String APP_MARKET_ACTION	= "market";
	public static final String APPNEW_MARKET_ACTION = "market_new";
	public static final String DISABLE_PROXY_ACTION = "disable_proxy";
	public static final String MANAGE_VPN_ACTION	= "vpn_manage";
	public static final String EVALUATE_ACTION		= "evaluate";
	public static final String FEEDBACK_ACTION		= "feedback";
	public static final String FEEDBACK2_ACTION		= "feedback2";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setFinishOnTouchOutside(false);

		if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
			setTheme(Res.style.DialogWindowTitleText_Light_Multiline);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(Res.layout.message_dialog_activity);
		setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, Res.drawable.icon);

		Intent intent = getIntent();

		//
		if (intent.hasExtra("title"))
			setTitle(intent.getStringExtra("title"));

		if (intent.hasExtra("text"))
		{
			TextView tv = (TextView) findViewById(Res.id.dialog_text);
			String t = intent.getStringExtra("text");
			if (t.indexOf("<a href=") < 0 && t.indexOf("<br>") < 0)
			{
				tv.setText(t);
			}
			else
			{
				// format dialog text as html
				tv.setText(Html.fromHtml(t));
				tv.setMovementMethod(LinkMovementMethod.getInstance());
			}
		}

		//
		boolean showSkip = false;
		boolean showClose = false;

		if (intent.hasExtra("secondText") && intent.hasExtra("secondAction"))
		{
			Button b = (Button) findViewById(Res.id.second_button);
			b.setText(intent.getStringExtra("secondText"));
			b.setVisibility(View.VISIBLE);

			String secondAction = intent.getStringExtra("secondAction");
			b.setTag(secondAction);

			if (EVALUATE_ACTION.equals(secondAction)) // show Skip instead Ok
				showSkip = true;
			else if (FEEDBACK_ACTION.equals(secondAction) || FEEDBACK2_ACTION.equals(secondAction)) // show Close instead Ok
				showClose = true;
		}

		if (intent.hasExtra("disableOk"))
			findViewById(Res.id.ok_button).setVisibility(View.GONE);
		else if (showSkip)
			((Button) findViewById(Res.id.ok_button)).setText(Res.string.skip);
		else if (showClose)
			((Button) findViewById(Res.id.ok_button)).setText(Res.string.close);
	}

	/*
	 * show advanced dialog with second button (maybe without OK) and extra data (context can be null)
	 * for second button handlers (secondButtonAction) see onSecondClick
	 *
	 * if dialog text have "<a href=" or "<br>" it will be formatted as html (\n not work!!!)
	 */
	public static void show(Activity context, String title, String text, String secondButtonText, String secondButtonAction,
								boolean disableOk)
	{
		Context c = (context != null) ? context : App.getContext();
		Intent intent = new Intent(c, MessageDialogActivity.class);
		int flags;

		if (context != null)
			flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
		else
			flags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
		intent.setFlags(flags);

		intent.putExtra("title", title);
		intent.putExtra("text", text);

		if (secondButtonText != null && secondButtonAction != null)
		{
			intent.putExtra("secondText", secondButtonText);
			intent.putExtra("secondAction", secondButtonAction);
		}

		if (disableOk)
			intent.putExtra("disableOk", disableOk);

		c.startActivity(intent);
	}

	// show simple dialog with text and button OK (context can be null)
	public static void show(Activity context, String title, String text)
	{
		show(context, title, text, null, null, false);
	}

	public void onOkayClick(View view)
	{
		finish();
	}

	public void onSecondClick(View view)
	{
		if (view.getTag() == null)
			return;

		final String action = view.getTag().toString();

		//
		if (action.equals(EVALUATE_ACTION))
			Preferences.putInt(Settings.PREF_EVALUATE_STATUS, 3); // button clicked

		//
		if (action.equals(APP_MARKET_ACTION) || action.equals(EVALUATE_ACTION))
			Utils.startMarket(this, Settings.APP_PACKAGE);

		else if (action.equals(APPNEW_MARKET_ACTION))
			Utils.startMarket(this, Settings.APP_NEWPACKAGE);

		else if (action.equals(DISABLE_PROXY_ACTION))
			WiFi.unsetWifiProxySettings(this);

		else if (action.equals(MANAGE_VPN_ACTION))
			Utils.showVpnDialogsManage(this);

		else if (action.equals(FEEDBACK_ACTION) || action.equals(FEEDBACK2_ACTION))
			SendMessageActivity.sendMessage(null, false);

		else if (action.equals(Notifications.BUY_ACTION)) // TODO XXX remove this
		{
//			Intent i = OptionsActivity.getIntent(this, false);
			// поправил тут баг с кнопкой Купить подписку
			Intent i = OptionsActivity.getIntent(this, true);
			i.putExtra(Notifications.BUY_ACTION, 1);
			startActivity(i);
		}

		finish();
	}
}
