package app.ui;

import android.widget.Toast;
import app.App;
import app.Res;


public class Toasts
{
	// TODO move to common.Utils (how to use App.*?)
	public static void showToast(final CharSequence message)
	{
		//getActivity().runOnUiThread(new Runnable()

		//Context context = App.getContext();
		//Toast toast = Toast.makeText(context, context.getString(Res.string.vpn_unavailable), Toast.LENGTH_LONG);

		if (App.isUIThread())
		{
			Toast.makeText(App.getContext(), message, Toast.LENGTH_LONG).show();
			return;
		}

		// not UI thread

		App.postToHandler(new Runnable()
		{
			@Override
			public void run()
			{
				Toast.makeText(App.getContext(), message, Toast.LENGTH_LONG).show();
			}
		});
	}

	public static void showToast(final int resId)
	{
		showToast(App.getContext().getResources().getText(resId));
	}

	public static void showToast(final String message)
	{
		showToast((CharSequence) message);
	}

	//

	public static void showFirmwareVpnUnavailable()
	{
		showToast(Res.string.vpn_unavailable);
	}

	public static void showVpnEstablishError()
	{
		showToast(Res.string.vpn_error);
	}

	public static void showVpnEstablishException()
	{
		showToast(Res.string.vpn_exception);
	}

	//

	public static void showNoNetwork()
	{
		showToast(Res.string.you_are_not_connected_to_network);
	}

	public static void showGooglePlayConnectError()
	{
		showToast(Res.string.cant_connect_to_google_play);
	}

	//

	public static void showSubscriptionChecking()
	{
		showToast(Res.string.checking_subscription);
	}

	public static void showSubscriptionOk()
	{
		showToast(Res.string.subscription_ok);
	}

	public static void showSubscriptionExpired()
	{
		showToast(Res.string.subscription_expired);
	}

	public static void showSubscriptionNotFound()
	{
		showToast(Res.string.subscription_not_ok);
	}
}
