package app.internal;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import com.android.vending.billing.*;
import app.common.NetUtil;
import app.info.Statistics;
import app.security.Policy;
import app.ui.BuyDialogActivity;
import app.ui.Notifications;
import app.ui.OptionsActivity;
import app.common.debug.L;
import app.common.Rnd;
import app.common.Utils;
import app.common.UtilsHttpResult;
import app.App;
import app.Res;
import app.UpdaterService;
import app.ui.PromoDialogActivity;
import app.ui.Toasts;
import app.ui.TryFreeDialogActivity;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONArray;
import org.squareup.okhttp.OkHttpClient;
import org.squareup.okhttp.OkUrlFactory;


// http://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html

public class InAppBilling
{
	public static final String PROMO_CODE_PARAM = "promoCode";
	public static final String FREE_TOKEN_PARAM = "freeToken";
	public static final String FULL_TOKEN_PARAM = "full";

	private volatile String  firstPrice = null;
	private volatile String  firstTitle = null;
	private volatile String  firstName = null;
	private volatile boolean firstInapp = false;
	private volatile String  secondPrice = null;
	private volatile String  secondTitle = null;
	private volatile String  secondName = null;
	private volatile boolean secondInapp = false;
	private volatile String  thirdPrice = null;
	private volatile String  thirdTitle = null;
	private volatile String  thirdName = null;
	private volatile boolean thirdInapp = false;
	private volatile String  freeName = null;
	private volatile boolean lastTryPromo = false; // if last attempt to buy with promo code

	private Context context;
	private IInAppBillingService mGPService;
	private final ServiceConnection mGPServiceConn;
	private boolean hasGooglePlay = false;

	private final ReentrantLock licenseLock = new ReentrantLock();
	private final ReentrantLock itemsLock = new ReentrantLock();
	//private Thread itemsThread = null;
	private Thread licenseThread = null;
	private boolean stopped = false;

	public InAppBilling(Context context)
	{
		this.context = context;

		if (Settings.LIC_USE_GP)
		{
			mGPServiceConn = new ServiceConnection()
			{
				@Override
				public void onServiceDisconnected(ComponentName name)
				{
					mGPService = null;
					L.w(Settings.TAG_INAPPBILLING, "onServiceDisconnected");
				}

				@Override
				public void onServiceConnected(ComponentName name, IBinder service)
				{
					mGPService = IInAppBillingService.Stub.asInterface(service);
					L.w(Settings.TAG_INAPPBILLING, "onServiceConnected");
//					  if (proMonthPrice == null && proYearPrice == null)
//						  loadItemsAsync();
				}
			};
		}
		else
		{
			mGPServiceConn = null;
		}
	}

	public void serviceBind()
	{
		if (Settings.LIC_USE_GP)
		{
			if (mGPService != null)
				return;

			Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
			serviceIntent.setPackage("com.android.vending");
			hasGooglePlay = context.bindService(serviceIntent, mGPServiceConn, Context.BIND_AUTO_CREATE);
		}
	}

	public boolean serviceBindAndWait(boolean startBinding)
	{
		// Пока мы не прибиндились и флаг наличия Google Play не обнулился, ждем
		// он может обнулиться только если бинд к сервису не прошел
		// TODO XXX sleep, wait 15 sec
		long t = System.currentTimeMillis();
		if (startBinding)
			serviceBind();

		while (mGPService == null && hasGooglePlay && (System.currentTimeMillis() - t < 15000))
			Utils.sleep(1000);

		return serviceIsBound();
	}

	public boolean serviceIsBound()
	{
		if (Settings.LIC_USE_GP)
			return (mGPService != null);
		else
			return true; // TODO XXX hack if GP not used
	}

	public void serviceUnbind()
	{
		if (mGPService == null)
			return;
		if (!itemsLock.tryLock())
			return;

		try
		{
			mGPService = null;
			context.unbindService(mGPServiceConn);
		}

		finally
		{
			itemsLock.unlock();
		}

		System.gc();
	}

	// TODO XXX return different result on RemoteException and NullPointerException
	private Bundle serviceGetSkuDetails(int apiVersion, String packageName, String type, Bundle skusBundle)
	{
		Bundle skuDetails = null;

		try { skuDetails = mGPService.getSkuDetails(apiVersion, packageName, type, skusBundle); }
		catch (RemoteException e) { e.printStackTrace(); }
		catch (NullPointerException e) { e.printStackTrace(); } // fix if mGPService unloaded (== null)

		return skuDetails;
	}

	// use this method to show buy dialog
	// param - see BuyDialogActivity.showAbove and promoCheck
	public void itemsLoadAndShowBuyDialog(final Context context)
	{
		itemsLoadAndShowBuyDialog(context, null);
	}

	public void itemsLoadAndShowBuyDialog(final Context context, final String param)
	{
		if (!itemsLock.tryLock())
			return;

		final ProgressDialog dialog;
		final String buyParam;
		final String buyCode;

		try
		{
			L.w(Settings.TAG_INAPPBILLING, "loadItemsAndShowBuyDialog");

			//if (itemsThread != null || mGPService == null)
			if (mGPService == null)
			{
				Toasts.showGooglePlayConnectError(); // maybe use dialog?
				return;
			}

			L.w(Settings.TAG_INAPPBILLING, "mService is ok");

			if (lastTryPromo)
			{
				resetItems(); // reset subs list if use promo code on last try
				lastTryPromo = false;
			}

			// have additional param? (promo)
			buyParam = (param != null) ? PROMO_CODE_PARAM : null;
			buyCode = (param != null) ? param : null;
			if (param != null)
				lastTryPromo = true;

			if (!hasNoItems())
			{
				// ok have some items, show buy dialog
				BuyDialogActivity.showAbove(context, buyParam, buyCode);
				return;
			}

			// TODO XXX create new thread to check license and show dialog
			// if dialog canceled did't stop thread
			// also loadItemsAsync can be already running

			L.w(Settings.TAG_INAPPBILLING, "loading items");

			// TODO XXX dialog duplicate (see BuyDialog, TryFreeDialog)
			dialog = new ProgressDialog(context);
			dialog.setIndeterminate(true);
			dialog.setCancelable(true);
			//dialog.setTitle(Res.string.app_name);
			dialog.setMessage(context.getText(Res.string.please_wait));
			dialog.setCanceledOnTouchOutside(false);
			dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
			{
				@Override
				public void onCancel(DialogInterface dialog) { } // TODO XXX actions?
			});
			Utils.dialogShow(null, dialog);
		}

		finally
		{
			itemsLock.unlock();
		}

		//itemsThread = new Thread(new Runnable()
		Thread itemsThreadLocal = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				// TODO XXX we check mGPService upper, but sometimes GP not work after reboot
				itemsLoad(param);

				if (!Utils.dialogClose(null, dialog, false))
					return; // GUI or dialog was closed

				BuyDialogActivity.showAbove(context, buyParam, buyCode);
				//itemsThread = null;
			}
		});
		//itemsThread.setName("itemsLoadAndShowBuyDialog()");
		//itemsThread.start();
		itemsThreadLocal.setName("loadItemsAndShowBuyDialog()");
		itemsThreadLocal.start();
	}

	private void resetItems()
	{
		if (!itemsLock.tryLock())
			return;

		firstPrice = null;
		firstTitle = null;
		firstName  = null;
		firstInapp = false;

		secondPrice = null;
		secondTitle = null;
		secondName	= null;
		secondInapp = false;

		thirdPrice = null;
		thirdTitle = null;
		thirdName  = null;
		thirdInapp = false;

		freeName = null;

		itemsLock.unlock();
	}

//	public boolean isLoadItems()
//	{
//		if (itemsLock.isLocked())
//			return true;
//		return false;
//	}

/*
	public void loadItemsAsync(final String param)
	{
		if (itemsThread != null)
			return;

		itemsThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
//				  if (mGPService == null)
//					  serviceBindAndWait(true);
				if (mGPService == null)
					return;

				itemsLoad(param);
				itemsThread = null;
			}
		});
		itemsThread.setName("loadItemsAsync()");
		itemsThread.start();
	}
*/

	/*
	 * load items (subscriptions variants) from GooglePlay (and WG server, see itemsLoadFromServer)
	 * return true if no errors
	 */
	private boolean itemsLoad(final String param)
	{
		if (!itemsLock.tryLock())
			return false;

		try
		{
			boolean result = false;
			resetItems();

			do
			{
				// get info from our server

				if (!itemsLoadFromServer(param))
					break;
				if (firstName == null && secondName == null && thirdName == null)
					return true; // no active subscriptions variants

				// get information from GooglePlay

				ArrayList<String> skuList = new ArrayList<String>();
				if (firstName != null)
					skuList.add(firstName);
				if (secondName != null)
					skuList.add(secondName);
				if (thirdName != null)
					skuList.add(thirdName);

				Bundle querySkus = new Bundle();
				querySkus.putStringArrayList("ITEM_ID_LIST", skuList);

				try
				{
					if (mGPService == null)
						break;

					String pkgName = App.packageName();
					Bundle skuDetails = null;
					int response;

					// request available subscriptions

					skuDetails = serviceGetSkuDetails(3, pkgName, "subs", querySkus);
					response = (skuDetails == null) ? -2 : skuDetails.getInt("RESPONSE_CODE");

					if (response != 0)
					{
						if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BILLING_SUBSGP_ERR + response);
						break; // sometimes return 6 (Fatal error during the API action)
					}
					ArrayList<String> responseList = skuDetails.getStringArrayList("DETAILS_LIST");
					if (responseList == null)
					{
						Statistics.addLog(Settings.LOG_BILLING_SUBSGP_NULL);
						break;
					}

					// request available in-app purchases

					skuDetails = serviceGetSkuDetails(3, pkgName, "inapp", querySkus);
					response = (skuDetails == null) ? -2 : skuDetails.getInt("RESPONSE_CODE");

					if (response != 0)
					{
						if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BILLING_INAPPGP_ERR + response);
						break; // see upper
					}
					ArrayList<String> responseList2 = skuDetails.getStringArrayList("DETAILS_LIST");
					if (responseList2 == null)
					{
						Statistics.addLog(Settings.LOG_BILLING_INAPPGP_NULL);
						break;
					}

					// ok, parse answers

					responseList.addAll(responseList2);
					responseList2 = null;

					for (String thisResponse : responseList)
					{
						JSONObject object = new JSONObject(thisResponse);
						String productId = object.getString("productId");
						String price = object.getString("price");
						String title = object.getString("title");
						String type = object.getString("type");

						L.e(Settings.TAG_INAPPBILLING, "Item: ", productId, " ", title, " ", price);

						if (firstName != null && productId.equals(firstName))
						{
							firstPrice = price;
							firstTitle = title;
							firstInapp = "inapp".equals(type);
						}
						else if (secondName != null && productId.equals(secondName))
						{
							secondPrice = price;
							secondTitle = title;
							secondInapp = "inapp".equals(type);
						}
						else if (thirdName != null && productId.equals(thirdName))
						{
							thirdPrice = price;
							thirdTitle = title;
							thirdInapp = "inapp".equals(type);
						}
					}

					result = true;
				}
				catch (JSONException e) { e.printStackTrace(); }
			}
			while (false);

			if (!result)
				resetItems();

			return result;
		}

		finally
		{
			itemsLock.unlock();
		}
	}

	/*
	 * load active items (subscriptions variants) from WG billing server
	 * return true if no errors
	 */
	private boolean itemsLoadFromServer(final String param)
	{
		HttpURLConnection con = null;
		BufferedInputStream in = null;
		byte[] data = null;
		String signHash = null;
		String key = null;

		try
		{
			String addr = Preferences.getSubsUrl();
			if (addr == null)
				return true;

			// generate random value that will be returned in server signed response
			// needed to protect from mitm attack
			try
			{
				final byte[] keyBytes = (new Rnd()).generateBytes(32);
				key = Utils.toHex(keyBytes);
			}
			catch (SecurityException e)
			{
				e.printStackTrace();
				return false;
			}

			// send request to server

			addr += "?dev=" + App.getInstallId() +
						"&publisher=" + Preferences.getPublisher() +
						"&key=" + key;

			final String referrer = Preferences.getReferrer(true);
			if (referrer != null)
				addr += "&referrer=" + referrer;

			final String token = Policy.getUserToken(false);
			if (token != null)
				addr += "&token=" + token;

			if (param != null)
				addr += "&promo=" + param; // see promoCheck

			try
			{
				JSONObject devInfo = Preferences.getDeviceInfo();
				addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
						"&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
			}
			catch (UnsupportedEncodingException e) { e.printStackTrace(); }
			catch (JSONException e) { e.printStackTrace(); }

			//addr = Preferences.getSubsUrl();
			//addr += "?dev=aa78fc45&publisher=google&key=964ef8afe7da8f2c58b143453a8419542122480507e15e7a3b49711b989709bf" +
			//		  "&referrer=utm_source%3D1%26utm_medium%3Dbanner%26utm_campaign%3Daff";
			//key = "964ef8afe7da8f2c58b143453a8419542122480507e15e7a3b49711b989709bf";

			//

			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			try { con.connect(); }
			catch (SecurityException ex) { return false; }

			int code = con.getResponseCode();
			if (code != 200)
				return false; // bad response

			// good response, parse

			signHash = con.getHeaderField("X-Subs-Sign");

			if (signHash != null)
			{
				int contentLength = con.getContentLength();
				//String transferEncoding = con.getHeaderField("Transfer-Encoding");
				//boolean canRead = (contentLength > 0 || (transferEncoding != null && transferEncoding.trim().equals("chunked")));
				//if (Settings.DEBUG) L.e(Settings.TAG_INAPPBILLING, "postData ", "Content-Length: ", Integer.toString(contentLength));

				try { in = new BufferedInputStream(con.getInputStream()); } catch (IOException e) { }

				if (in != null)
				{
					ByteArrayOutputStream baos = new ByteArrayOutputStream((contentLength > 0) ? contentLength : 128);
					byte[] buf = new byte[4096];
					int read;
					while ((read = in.read(buf)) != -1)
						baos.write(buf, 0, read);

					data = baos.toByteArray();

					in.close();
					in = null;
				}
			}
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e)
		{
			e.printStackTrace();
			data = null;
		}

		finally
		{
			if (in != null)
				try { in.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		if (data == null)
		{
			Statistics.addLog(Settings.LOG_BILLING_SUBSDATA_ERR);
			return false; // bad, no data from server
		}

		// have response, check sign and parse subs names

		if (!UpdaterService.isSignedDataValid(data, signHash, true))
		{
			Statistics.addLog(Settings.LOG_BILLING_SUBSSIGN_ERR);
			return false;
		}

		try
		{
			JSONObject response = new JSONObject(new String(data, 0));
			String serverKey = response.getString("key");
			JSONArray subs = response.getJSONArray("subs");

			if (serverKey == null || !serverKey.equals(key) || subs == null)
				return false;

//			  firstName = "pro_month";
//			  secondName = "pro_year";

			int l = subs.length();
			for (int i = 0; i < l; i++)
			{
				String subname = subs.getString(i);
				if (subname.startsWith("free")) // free subscription (TryFreeDialogActivity)
					{ /*if (freeName == null) freeName = subname;*/ }
				else if (firstName == null)
					firstName = subname;
				else if (secondName == null)
					secondName = subname;
				else if (thirdName == null)
					thirdName = subname;
			}

			return true;
		}
		catch (JSONException ex)
		{
			Statistics.addLog(Settings.LOG_BILLING_SUBSPARSE_ERR);
		}

		return false;
	}

	//
	public void stop()
	{
		stopped = true;
	}

	//
	public boolean licenseIsCheckTime()
	{
		final long t = Preferences.get_l(Settings.PREF_USER_TOKEN_TIME);
		final long expireTime = t + Settings.LIC_CHECK_INTERVAL;
		final long time = System.currentTimeMillis();

		if (Settings.LIC_DISABLE)
			return false;

		//L.d(Settings.TAG_INAPPBILLING, "t = " + t+ " exp = " + expireTime + " time = " + time);
		if (Settings.LIC_CHECK_FORCE)
			return true;
		else if (t <= 0) // t == 0 if no token, see Policy.clearToken
			return (Policy.getUserToken(true) != null);
		else
			return (t > time || expireTime < time);
	}

	// onDemand - on user demand
	public void licenseCheckAsync(final boolean onDemand)
	{
		if (Settings.LIC_DISABLE)
			return;

		if (licenseThread != null)
			return;

		licenseThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				// TODO XXX move GooglePlay service bind/unbind into licenseCheck

				if (mGPService == null)
				{
					if (!serviceBindAndWait(true))
					{
						L.d(Settings.TAG_INAPPBILLING, "checkLicenseAsync() null");
						//return;
					}
				}

				licenseCheck(/*false, */onDemand);

				if (!App.isUIActive()) // TODO XXX fix GooglePlay service unbind
					serviceUnbind();

				licenseThread = null;
			}
		});

		licenseThread.setName("checkLicenseAsync()");
		licenseThread.start();
	}

	/*
	 * Checks license
	 * TODO XXX didn't check active license on server if failed to get data from Google Play
	 *
	 * @param isMainThread - if that is true than sending to our server will be in another thread (not main)
	 * @return true if it has checked the license on Google Play and our server, false if fail
	 */
	public boolean licenseCheck(/*boolean isMainThread, */final boolean onDemand)
	{
		if (!licenseLock.tryLock())
			return false;

		L.d(Settings.TAG_INAPPBILLING, "licenseCheck");

		try
		{
			if (Settings.LIC_DISABLE)
				return true;

			// set fake token :)
			if (Settings.DEBUG_FAKETOKEN)
			{
				Preferences.enableProFunctions("098f6bcd4621d373cade4e832627b4f6", true);
				Policy.reloadPrefs();
				return true;
			}

			// check for old user
			int recovery = Preferences.get_i(Settings.PREF_RECOVERY_STATUS);
			if (Settings.DEBUG_FREECHECK)
				recovery = 0;

			if (recovery == 0 || onDemand)
			{
				// no recovery check yet or manual run
				recovery = recoveryCheck(onDemand);
				if (Settings.EVENTS_LOG)
					if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVREC_STATE + recovery);

				if (recovery == 3 && !Settings.DEBUG_FREECHECK) // have free token
					return true;
			}

			// free token?
			String token = Policy.getUserToken(false);
			if (Policy.isFreeToken(token) || (Settings.DEBUG_FREECHECK && token != null))
			{
				int result = freeCheck(token, onDemand);
				if (Settings.EVENTS_LOG)
					if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVFREESUBS_STATE + result);

				if (result == 0) // have free subscription
					return true;
				else if (result == -1) // free check error
					return false;
			}

			// paid token?
			boolean expired = Preferences.get(Settings.PREF_SUBSCRIPTION_EXPIRED); // ??? why before checks?
			StringBuilder data = new StringBuilder();
			StringBuilder sigs = new StringBuilder();

			int itemsNum = licenseCheckOnGP(data, sigs, onDemand);
			if (Settings.EVENTS_LOG)
				if (onDemand) Statistics.addLog(Settings.LOG_BILLING_GPSUBS_STATE + itemsNum);

			if (itemsNum < 0) // no GP or GP error
				return false;

			if (itemsNum > 0) // TODO XXX hmmm, may be if itemsNum >= 0 ?
			{
				//if (isMainThread)
				//	  sendLicenseToServerAsync(data.toString(), sigs.toString(), onDemand);
				//else
				boolean result = licenseCheckOnServer(data.toString(), sigs.toString(), onDemand);
				if (Settings.EVENTS_LOG)
					if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVSUBS_STATE + ((result) ? "1" : "0"));

				if (!result)
					return false;
			}

			if (itemsNum == 0 || expired)
				// notify about futures if no items, subs expire and didn't show subs expire notify
				OptionsActivity.notifyAboutSubscriptionFeatures();

			// unbind in licenseCheckAsync and on OptionsActivity destroy
			//unbindService();
		}

		finally
		{
			licenseLock.unlock();
		}

		return true;
	}

	public boolean isCheckLicense()
	{
		if (licenseLock.isLocked())
			return true;
		return false;
	}

	//
/*
	private void sendLicenseToServerAsync(final String json, final String signature,
											final boolean onDemand)
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("sendLicenseToServerAsync()");
				licenseCheckOnServer(json, signature, onDemand);
			}
		}).start();
	}
*/

	/*
	 * return num of licenses info that was loaded from GooglePlay (and license may be bad, or expired)
	 * also fills data and signatures (and disable subscription if GP return nothing)
	 * return -1 on error, -2 if GP not available or not loaded, < -10 GP error
	 */
	private int licenseCheckOnGP(StringBuilder data, StringBuilder signatures,
											final boolean onDemand)
	{
		String continuationToken = null;
		int responseCode;
		boolean error = false;
		int itemsNum = 0;

		if (!hasGooglePlay)
			return -2;

		L.d(Settings.TAG_INAPPBILLING, "licenseCheckOnGP");

		data.append('[');
		signatures.append('[');

		int subsType = 0;
		try
		{
			do
			{
				if (mGPService == null)
					return -2;

				Bundle ownedItems = null;

				// TODO XXX mGPService can be null, no locks
				String subsTypeS = (subsType == 0) ? "subs" : "inapp";
				try
				{
					ownedItems = mGPService.getPurchases(3, App.packageName(), subsTypeS, continuationToken);
					responseCode = ownedItems.getInt("RESPONSE_CODE");
				}
				catch (NullPointerException e)
				{
					e.printStackTrace(); // fix if mGPService unloaded (== null)
					responseCode = -2;
				}

				if (Settings.DEBUG_GP_CHECK_INFO && Settings.EVENTS_LOG)
					Statistics.addLog("GP r1 " + App.packageName() + " " + ownedItems.getInt("RESPONSE_CODE"));

				if (responseCode != 0)
					break;

				//ArrayList<String> ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
				ArrayList<String> purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
				ArrayList<String> signatureList = ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
				continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN");

				if (Settings.DEBUG_GP_CHECK_INFO && Settings.EVENTS_LOG)
				{
					Statistics.addLog("GP r2 " + ((purchaseDataList == null) ? "bad" : "ok") + " " +
										((signatureList == null) ? "bad" : "ok") + " " + continuationToken);
					ArrayList<String> ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
					Statistics.addLog("GP r21 " + ((ownedSkus == null) ? "bad" : "ok " + ownedSkus.size()));
				}

				if (purchaseDataList == null || signatureList == null)
				{
					if (onDemand && purchaseDataList != null && signatureList == null)
						Statistics.addLog(Settings.LOG_BILLING_GPSIGS_ERR);
					continue;
				}

				if (Settings.DEBUG_GP_CHECK_INFO && Settings.EVENTS_LOG)
					Statistics.addLog("GP r3 " + purchaseDataList.size() + " " + signatureList.size() + " " + continuationToken);

				int list_size = purchaseDataList.size();
				if (list_size == 0)
				{
					//Statistics.addLog(Settings.LOG_BILLING_GPNOSUBS);
					continue;
				}

				// send purchases data to our server
				//Statistics.addLog(Settings.LOG_BILLING_SUBSSIZE + list_size);
				//long mostRecentTime = 0;

				for (int i = 0; i < list_size; i++, itemsNum++)
				{
					if (itemsNum > 0)
					{
						data.append(',');
						signatures.append(',');
					}

					String purchaseData = purchaseDataList.get(i);
					//L.w(Settings.TAG_INAPPBILLING, "json: ", purchaseData);
					data.append(purchaseData);
					String signature = signatureList.get(i);
					//L.w(Settings.TAG_INAPPBILLING, "sig: ", signature);
					signatures.append('"');
					signatures.append(signature);
					signatures.append('"');
					//long t = getPurchaseTimeFromJSON(purchaseData);
				}
			}
			while (continuationToken != null || ++subsType < 2);
		}
		catch (RemoteException e)
		{
			e.printStackTrace();

			error = true;
			responseCode = -1;
		}

		data.append(']');
		signatures.append(']');

		if (Settings.DEBUG_GP_CHECK_INFO && Settings.EVENTS_LOG)
			Statistics.addLog("GP r4 " + data.toString() + " " + signatures.toString() + " " + itemsNum);

		if (itemsNum == 0 || responseCode != 0) // TODO XXX hmmm, and if itemsNum > 0 with bad code?
		{
			if (responseCode == 0)
			{
				// google play didn't return any subscriptions info (and no errors)
				// disable Pro functions if google play return nothing and we have active subscription

				subscriptionDisable(!onDemand);
			}
			else
			{
				// TODO XXX also will be here if allow inet for browsers only

				itemsNum = (error) ? responseCode : responseCode + (-100);

				if (subsType == 0)
					Statistics.addLog(Settings.LOG_BILLING_GPSUBS_ERR);
				else
					Statistics.addLog(Settings.LOG_BILLING_GPINAPP_ERR);
			}
		}

		return itemsNum;
	}

	/*
	 * return true of license was checked on server (and license may be bad, or expired)
	 * or false on error
	 */
	private boolean licenseCheckOnServer(final String data, final String signatures,
											final boolean onDemand)
	{
		stopped = false;
		boolean checked = false;
		boolean parseErr = false;
		String request = null;

		String billing_url = Preferences.getBillingUrl();
		if (billing_url == null)
			return false;

		L.d(Settings.TAG_INAPPBILLING, "licenseCheckOnServer");

		//

		try
		{
			request = "dev=" + App.getInstallId() +
					"&publisher=" + Preferences.getPublisher() +
					"&bug=1";

			if (data != null)
				request += "&json=" + URLEncoder.encode(data, "UTF-8");

			if (signatures != null)
				request += "&sig=" + URLEncoder.encode(signatures, "UTF-8");

			final String ref = Preferences.getReferrer(true);
			if (ref != null)
				request += "&ref=" + ref;

			JSONObject devInfo = Preferences.getDeviceInfo();
			request += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
						"&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
		}
		catch (UnsupportedEncodingException e) { e.printStackTrace(); }
		catch (JSONException e) { e.printStackTrace(); }

		if (request == null)
			return false;

		// try to check license on our server (3 times with 5 sec interval)

		for (int i = 0; i < 3; i++)
		{
			if (NetUtil.getStatus() == -1 || stopped)
			{
				stopped = true;
				break;
			}

			L.d(Settings.TAG_INAPPBILLING, "Checking license from our server...");

			UtilsHttpResult response = Utils.postData(billing_url, request.getBytes());
			if (response.isFailed())
			{
				Utils.sleep(5000); // wait
				continue;
			}

			// check server answer

			L.d(Settings.TAG_INAPPBILLING, "Got answer...");

			int code = response.getResultCode();
			byte[] responseData = response.getData();
			if (Settings.EVENTS_LOG)
				if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVSUBS_CODE + code);

			if ((code != 200 && code != 404 && code != 410) ||
				responseData == null || responseData.length == 0)
			{
				// bad answer
				L.e(Settings.TAG_INAPPBILLING, "Bad response from server!");
				if (NetUtil.getStatus() != -1)
					Notifications.showLicenseCheckError(context);

				break;
			}

			// parse server answer

			String token = null;
			String full = null;
			String ok = null;

			try
			{
				JSONObject result = new JSONObject(new String(responseData, 0));
				token = result.optString("token", null); // must be in 200
				full = result.optString("full", null);	 // must be in 200
				ok = result.optString("ok", null);		 // must be in 404, 410
			}
			catch (JSONException ex)
			{
				L.d(Settings.TAG_INAPPBILLING, "Failed to parse server license data.");
			}

			if ((code == 200 && (token == null || full == null)) ||
				(code != 200 && ok == null))
			{
				// 200 or 404, 410 without required params
				parseErr = true;
			}
			else if (code == 200 && !Settings.DEBUG_NOSUBS)
			{
				// 200 - ok, have subscription, parse answer

				L.d(Settings.TAG_INAPPBILLING, "Got account: ", token);
				Preferences.enableProFunctions(token, "1".equals(full));
			}
			else
			{
				// 410 - subscription expired (gone)
				// 404 - subscription not found

				//Statistics.addLog("subs code " + code);

				// see enableProFunctions, clearProFunctions
				if (code == 410)
					Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_WAS, true);
				else
					Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_WAS, false);
				Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, false);

				subscriptionDisable(!onDemand);
			}

			if (!parseErr)
				checked = true;
			break;
		} // for

		if (parseErr)
			Statistics.addLog(Settings.LOG_BILLING_SRVPARSE_ERR);
		else if (!checked && !stopped)
			Statistics.addLog(Settings.LOG_BILLING_SRVSUBS_ERR);
		else if (checked)
			Policy.reloadPrefs();

		return checked;
	}

	// return true if show notification to user
	private boolean subscriptionDisable(boolean showNotify)
	{
		boolean show = false;

		Preferences.editStart();

		String token = Policy.getUserToken(false);
		boolean isFree = Policy.isFreeToken(token);

		if (Preferences.get(Settings.PREF_SUBSCRIPTION_WAS) &&
			!Preferences.get(Settings.PREF_SUBSCRIPTION_EXPIRED))
		{
			// have expired subscription, see notifyAboutSubscriptionFeatures
			final int count = Preferences.get_i(Settings.PREF_NO_SUBS_AD_COUNT);
			if (count == 0)
				Preferences.putInt(Settings.PREF_NO_SUBS_AD_COUNT, 1);

			if (showNotify)
				show = true;
		}

		Preferences.clearProFunctions(false);

		Preferences.editEnd();

		if (show)
		{
			if (isFree)
				Notifications.showSubsExpiredFree();
			else
				Notifications.showSubsExpired();
		}

		App.disable(); // remove this if have return free functional

		return show;
	}

	private long getPurchaseTimeFromJSON(String json)
	{
		try
		{
			JSONObject obj = new JSONObject(json);
			long t = obj.getLong("purchaseTime");
			return t;
		}
		catch (JSONException e) { e.printStackTrace(); }

		return 0;
	}

	public boolean purchase(Activity activity, String id, boolean isInapp, int requestCode)
	{
		try
		{
			if (mGPService == null)
				return false;

			Bundle buyIntentBundle = null;
			int response;

			String subsTypeS = (!isInapp) ? "subs" : "inapp";
			try
			{
				buyIntentBundle = mGPService.getBuyIntent(3, App.packageName(), id, subsTypeS, App.getInstallId());
				response = buyIntentBundle.getInt("RESPONSE_CODE");
			}
			catch (NullPointerException e)
			{
				e.printStackTrace(); // fix if mGPService unloaded (== null)
				response = -2;
			}

			if (response == 0)
			{
				PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
				activity.startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, new Intent(), 0, 0, 0);

				return true;
			}
		}

		catch (RemoteException e) { e.printStackTrace(); }
		catch (IntentSender.SendIntentException e) { e.printStackTrace(); }

		return false;
	}

	public boolean purchaseComplete(Intent data)
	{
		int responseCode = data.getIntExtra("RESPONSE_CODE", 0); // TODO XXX default 0???
		if (responseCode == 0)
		{
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
			String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
			L.e(Settings.TAG_INAPPBILLING, "json: ", purchaseData);
			L.e(Settings.TAG_INAPPBILLING, "sig: ", dataSignature);

			if (purchaseData != null && dataSignature != null)
			{
				try
				{
					JSONObject jo = new JSONObject(purchaseData);
					String productId = jo.getString("productId");

					//long time = jo.getLong("purchaseTime");
					//Preferences.putLong(Settings.PREF_USER_TOKEN_TIME, time);

					//sendLicenseToServerAsync(purchaseData, dataSignature, false);
					if (licenseCheckOnServer(purchaseData, dataSignature, false))
					{
						L.d(Settings.TAG_INAPPBILLING, "You have bought the ", productId, ". Excellent choice, adventurer!");
						return true; // all OK
					}
				}
				catch (JSONException e)
				{
					L.d(Settings.TAG_INAPPBILLING, "Failed to parse purchase data.");
					e.printStackTrace();
				}
			}
		}

		if (Settings.EVENTS_LOG) Statistics.addLog(Settings.LOG_BILLING_GPPURCHASE_ERR + responseCode);
		return false;
	}

	// freeTry -> TryFreeDialogActivity -> freeTry -> freeConfirm ->
	//	  activity.setResult(RESULT_OK, intent) -> freeComplete
	public boolean freeGet(Activity activity, String id, int requestCode)
	{
		return freeGet(activity, id, requestCode, null);
	}

	public boolean freeGet(Activity activity, String id, int requestCode, String param)
	{
		final String freeParam = (param != null) ? PROMO_CODE_PARAM : null;
		final String freeCode = (param != null) ? param : null;

		Intent intent = new Intent(activity, TryFreeDialogActivity.class);
		if (freeParam != null)
			intent.putExtra(freeParam, freeCode);
		activity.startActivityForResult(intent, requestCode);

		return false;
	}

	public boolean freeComplete(Intent data)
	{
		// see checkCaptchaOnServer
		String token = data.getStringExtra(FREE_TOKEN_PARAM);
		if (token == null || token.isEmpty())
			return false;

		boolean isFull = data.hasExtra(FULL_TOKEN_PARAM); // see freeConfirm
		Preferences.enableProFunctions(token, isFull);

		return true;
	}

	// return -1 on error, 0 - all ok, 1 - if license not found or expired
	public int freeCheck(final String token, final boolean onDemand)
	{
		HttpURLConnection con = null;
		String status = null;

		if (token == null || token.isEmpty())
			return -1;

		String addr = Preferences.getFreeUrl();
		if (addr == null)
			return -1;

		L.d(Settings.TAG_INAPPBILLING, "freeCheck");

		//

		addr += "?action=check_free_token" +
					"&dev=" + App.getInstallId() +
					"&publisher=" + Preferences.getPublisher() +
					"&token=" + token;

		final String ref = Preferences.getReferrer(true);
		if (ref != null)
			addr += "&ref=" + ref;

		try
		{
			JSONObject devInfo = Preferences.getDeviceInfo();
			addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
					"&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
		}
		catch (UnsupportedEncodingException e) { }
		catch (JSONException ex) { }

		// TODO XXX add several attempts, see licenseCheckOnServer

		try
		{
			// send request to server

			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			String host = url.getHost();
			String ip = null;
			if (host != null)
				ip = NetUtil.lookupIp(host, 3);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url, ip); // use patched OkHttp and OkIo to set server ip address

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			try { con.connect(); }
			catch (SecurityException ex) { return -1; }

			int code = con.getResponseCode();
			if (Settings.EVENTS_LOG)
				if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVFREESUBS_CODE + code);

			if (code != 200)
				return -1; // bad response

			// good response, parse

			status = con.getHeaderField("X-Free-Status");
			//status = "ok";
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (con != null)
				con.disconnect();
		}

		// check for errors
		int retval = 0;
		if ("error".equals(status))
			retval = 1;
		else if (!"ok".equals(status))
			retval = -1;

		if (Settings.DEBUG_NOFREESUBS)
			retval = 1;

		//
		if (retval == 0)
		{
			// update token time (see licenseIsCheckTime)
			long time = System.currentTimeMillis();
			Preferences.putLong(Settings.PREF_USER_TOKEN_TIME, time);
		}
		else if (retval == 1)
		{
			subscriptionDisable(!onDemand);
		}
		else if (retval == -1)
		{
			Statistics.addLog(Settings.LOG_BILLING_SRVFREESUBS_ERR);
		}

		return retval;
	}

	/*
	 * load free subs data from WG billing server, return captcha Bitmap or null
	 *
	 * result[0] return status: 0 - ok, -1 - error, 1 - no more free subscription
	 * if result[1] == 1 than enable apps ads block
	 * result[2] return number of free days
	 *
	 * result[3], tokenBytes[36]
	 */
	public Bitmap freeTry(byte[] result, byte[] tokenBytes, String param, boolean useCaptcha)
	{
		HttpURLConnection con = null;
		BufferedInputStream in = null;
		String status = null;
		String token = null;
		String appAdsBlock = null;
		String days = null;
		byte[] data = null;

		result[0] = -1;
		result[1] = 0;
		result[2] = 1;

		String addr = Preferences.getFreeUrl();
		if (addr == null)
			return null;

		// send request to server
		if (useCaptcha)
			addr += "?action=get_free_token";
		else
			addr += "?action=get_free_token2";

		addr += "&dev=" + App.getInstallId() + "&publisher=" + Preferences.getPublisher();

		final String ref = Preferences.getReferrer(true);
		if (ref != null)
			addr += "&ref=" + ref;

		if (param != null)
			addr += "&promo=" + param; // see promoCheck

		try
		{
			JSONObject devInfo = Preferences.getDeviceInfo();
			addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
					"&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
		}
		catch (UnsupportedEncodingException e) { }
		catch (JSONException ex) { }

		// TODO XXX add several attempts, see licenseCheckOnServer

		// load data
		try
		{
			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);
			//URL url = new URL("http://www.picturesnew.com/media/images/336914de2a.png");

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			try { con.connect(); }
			catch (SecurityException ex) { return null; }

			if (con.getResponseCode() != 200)
				return null; // bad response

			// good response, parse

			status = con.getHeaderField("X-Free-Status");
			//status = "ok";
			token = con.getHeaderField("X-Free-Token");
			appAdsBlock = con.getHeaderField("X-Full");
			days = con.getHeaderField("X-Free-Days");

			if (token != null && useCaptcha)
			{
				int contentLength = con.getContentLength();
				//String transferEncoding = con.getHeaderField("Transfer-Encoding");
				//boolean canRead = (contentLength > 0 || (transferEncoding != null && transferEncoding.trim().equals("chunked")));
				//if (Settings.DEBUG) L.e(Settings.TAG_INAPPBILLING, "postData ", "Content-Length: ", Integer.toString(contentLength));

				try { in = new BufferedInputStream(con.getInputStream()); } catch (IOException e) { }

				if (in != null)
				{
					ByteArrayOutputStream baos = new ByteArrayOutputStream((contentLength > 0) ? contentLength : 4096);
					byte[] buf = new byte[4096];
					int read;
					while ((read = in.read(buf)) != -1)
						baos.write(buf, 0, read);

					data = baos.toByteArray();

					in.close();
					in = null;
				}
			}
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e)
		{
			e.printStackTrace();
			data = null;
		}
		finally
		{
			if (in != null)
				try { in.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		// check for errors
		if ("error".equals(status))
		{
			result[0] = 1;
			return null;
		}
		else if (!"ok".equals(status) || (data == null && useCaptcha) ||
					token == null || token.getBytes().length != tokenBytes.length)
		{
			return null;
		}

		// parse days param
		if (days != null)
		{
			if (days.isEmpty())
				return null;

			int d;
			try { d = Integer.parseInt(days); }
			catch (NumberFormatException ex) { return null; }
			if (d > 0)
				result[2] = (byte) d;
		}

		// have valid response, create captcha
		Bitmap image = null;
		if (useCaptcha)
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
			image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
			if (image == null)
				return null;
		}

		result[0] = 0;
		if (appAdsBlock != null && Boolean.valueOf(appAdsBlock))
			result[1] = 1;
		System.arraycopy(token.getBytes(), 0, tokenBytes, 0, tokenBytes.length);

		return image;
	}

	/*
	 * check token and captcha on server (if use captcha)
	 * return 0 on success, -1 on error, 1 on invalid text
	 * also set activity result, see freeTry
	 */
	public int freeConfirm(Activity activity, String captchaText,
							String token, boolean appAdsBlock, boolean useCaptcha)
	{
		HttpURLConnection con = null;
		String status = null;

		if ((useCaptcha && captchaText.isEmpty()) || token.isEmpty())
			return -1;

		Intent intent = new Intent();
		intent.putExtra(FREE_TOKEN_PARAM, token);
		if (appAdsBlock)
			intent.putExtra(FULL_TOKEN_PARAM, true);

		if (!useCaptcha)
		{
			activity.setResult(Activity.RESULT_OK, intent);
			return 0;
		}

		String addr = Preferences.getFreeUrl();
		if (addr == null)
			return -1;

		// send request to server

		addr += "?action=check_captcha" +
					"&dev=" + App.getInstallId() +
					"&publisher=" + Preferences.getPublisher() +
					"&token=" + token +
					"&captcha=" + captchaText;

		final String ref = Preferences.getReferrer(true);
		if (ref != null)
			addr += "&ref=" + ref;

		try
		{
			JSONObject devInfo = Preferences.getDeviceInfo();
			addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
					"&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
		}
		catch (UnsupportedEncodingException e) { }
		catch (JSONException e) { }

		// TODO XXX add several attempts, see licenseCheckOnServer

		// load data
		try
		{
			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			try { con.connect(); }
			catch (SecurityException ex) { return -1; }

			if (con.getResponseCode() != 200)
				return -1; // bad response

			// good response, parse

			status = con.getHeaderField("X-Free-Status");
			//status = "ok";
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (con != null)
				con.disconnect();
		}

		// check for errors
		if ("error".equals(status))
			return 1;
		else if (!"ok".equals(status))
			return -1;

		// X-FreeStatus == ok
		activity.setResult(Activity.RESULT_OK, intent);
		return 0;
	}

	// promoAsk -> PromoDialogActivity -> promoCheck -> activity.setResult(RESULT_OK, intent)
	public boolean promoAsk(Activity activity, int requestCode)
	{
		activity.startActivityForResult(new Intent(activity, PromoDialogActivity.class), requestCode);

		return false;
	}

	/*
	 * return -1 on error, 0 - all ok, 1 - if promocode not found, 2 - or expired
	 * also set activity result, see promoAsk
	 */
	public int promoCheck(final Activity activity, final String promoCode)
	{
		HttpURLConnection con = null;
		String status = null;

		if (promoCode == null)
			return -1;

		if (promoCode.isEmpty())
		{
			Intent intent = new Intent();
			activity.setResult(Activity.RESULT_OK, intent);
			return 0;
		}

		String addr = Preferences.getPromoUrl();
		if (addr == null)
			return -1;

		//

		addr += "?code=" + promoCode +
					"&dev=" + App.getInstallId() +
					"&publisher=" + Preferences.getPublisher();

		final String ref = Preferences.getReferrer(true);
		if (ref != null)
			addr += "&ref=" + ref;

		// TODO XXX add several attempts, see licenseCheckOnServer

		try
		{
			// send request to server

			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			try { con.connect(); }
			catch (SecurityException ex) { return -1; }

			if (con.getResponseCode() != 200)
				return -1; // bad response

			// good response, parse

			status = con.getHeaderField("X-Promo-Status");
			//status = "ok";
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (con != null)
				con.disconnect();
		}

		// check for errors
		if ("0".equals(status) || "-1".equals(status))
			return 1; // not found
		else if ("2".equals(status))
			return 2; // expired
		else if (!"1".equals(status))
			return -1;

		// ok, X-Promo-Status == 1
		Intent intent = new Intent();
		intent.putExtra(PROMO_CODE_PARAM, promoCode);
		activity.setResult(Activity.RESULT_OK, intent);

		return 0;
	}

	/*
	 * check for old (free) user and restore referrer if need
	 * also check and restore free subscription
	 * return -1 on error, 0 - unknown, 1 - old user, 2 - new user, 3 - restore free token
	 */
	public int recoveryCheck(final boolean onDemand)
	{
		HttpURLConnection con = null;
		String status = null;
		String referrer = null;
		String token = null;
		String full = null;

		String addr = Preferences.getRecoveryUrl();
		if (addr == null)
			return -1;

		L.d(Settings.TAG_INAPPBILLING, "recoveryCheck");

		try
		{
			addr += "?dev=" + App.getInstallId() +
						"&publisher=" + Preferences.getPublisher();

			final String ref = Preferences.getReferrer(true);
			if (ref != null)
				addr += "&ref=" + ref;

			JSONObject devInfo = Preferences.getDeviceInfo();
			addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
					"&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
		}
		catch (UnsupportedEncodingException e) { return -1; }
		catch (JSONException ex) { return -1; }

		// TODO XXX add several attempts, see licenseCheckOnServer

		try
		{
			// send request to server

			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			String host = url.getHost();
			String ip = null;
			if (host != null)
				ip = NetUtil.lookupIp(host, 3);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url, ip); // use patched OkHttp and OkIo to set server ip address

			con.setRequestMethod("GET");
			//con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			try { con.connect(); }
			catch (SecurityException ex) { return -1; }

			int code = con.getResponseCode();
			if (Settings.EVENTS_LOG)
				if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVREC_CODE + code);

			if (code != 200)
				return -1; // bad response

			// good response, parse

			status = con.getHeaderField("X-Recovery-Status");
			referrer = con.getHeaderField("X-Recovery-Partner");
			token = con.getHeaderField("X-Recovery-Free-Token");
			full = con.getHeaderField("X-Recovery-Full");
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (con != null)
				con.disconnect();
		}

		// check return value

		int retval = 0;
		if ("1".equals(status))
			retval = 1;
		else if ("2".equals(status))
			retval = 2;

		if (Settings.DEBUG_NOFREESUBS)
		{
			retval = 2;
			referrer = null;
			token = null;
			full = null;
		}

		if (retval == 1 || retval == 2)
			Preferences.putInt(Settings.PREF_RECOVERY_STATUS, retval);

		// restore referrer if missed
		Preferences.editStart(); // synchronization with ReferalReceiver

		if (referrer != null && Preferences.getReferrer(false) == null)
		{
			try { referrer = URLDecoder.decode(referrer, "UTF-8"); } catch (Exception ex) { }
			if (!referrer.isEmpty())
			{
				referrer = "utm_source=" + referrer + "&utm_medium=banner&utm_campaign=aff";
				Preferences.putString(Settings.PREF_REFERRER, referrer);
				Preferences.putBoolean(Settings.PREF_REFERRER_RECOVERY, true); // see referrerUpdate
			}
		}

		Preferences.editEnd();

		// restore free subscription if have
		if (token != null)
		{
			Preferences.enableProFunctions(token, "true".equals(full));
			retval = 3;
		}

		return retval;
	}

	// update referrer if have promocode and no referrer (return true if updated)
	public boolean referrerUpdate(String promoCode)
	{
		if (promoCode == null || promoCode.isEmpty())
			return false;

		Preferences.editStart(); // synchronization with ReferalReceiver

		boolean result = false;
		String r = Preferences.getReferrer(false);
		boolean recovery = Preferences.get(Settings.PREF_REFERRER_RECOVERY); // see recoveryCheck

		if (r == null || r.isEmpty() || recovery)
		{
			// need update

			r = "utm_source=" + promoCode + "&utm_medium=promo&utm_campaign=aff";
			Preferences.putString(Settings.PREF_REFERRER, r);
			if (recovery)
				Preferences.putBoolean(Settings.PREF_REFERRER_RECOVERY, false);

			result = true;
		}

		Preferences.editEnd();

		return result;
	}

/*
	// wtf?
	public void startCheckingThread(final BuyDialogActivity activity)
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("startCheckingThread()");
				Utils.sleep(5000);
				final String token = Policy.getUserToken(false);
				activity.runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						activity.showPurchaseResult(token != null);
					}
				});
			}
		}).start();
	}
*/

	public boolean hasGooglePlay()
	{
		return hasGooglePlay;
	}

	public boolean hasNoItems()
	{
		return (firstPrice == null && secondPrice == null && thirdPrice == null &&
				freeName == null);
	}

	public boolean hasFirst()
	{
		return (firstPrice != null);
	}

	public boolean hasSecond()
	{
		return (secondPrice != null);
	}

	public boolean hasThird()
	{
		return (thirdPrice != null);
	}

	public boolean hasFree()
	{
		return (freeName != null);
	}

	public String getFirstName()
	{
		return firstName;
	}

	public String getSecondName()
	{
		return secondName;
	}

	public String getThirdName()
	{
		return thirdName;
	}

	public String getFreeName()
	{
		return freeName;
	}

	public String getFirstText()
	{
		return getText(firstName, firstTitle, firstPrice);
	}

	public String getSecondText()
	{
		return getText(secondName, secondTitle, secondPrice);
	}

	public String getThirdText()
	{
		return getText(thirdName, thirdTitle, thirdPrice);
	}

	private String getText(String name, String title, String price)
	{
		if (title != null && price != null)
		{
			final String appName = " (" + Settings.APP_NAME + ")";
			int pos = title.indexOf(appName);
			if (pos >= 0)
				title = title.substring(0, pos);

			if (name.indexOf("promo") >= 0)
				return title;
			return (title + " - " + price);
		}

		return null;
	}

	public boolean isFirstInapp()
	{
		return firstInapp;
	}

	public boolean isSecondInapp()
	{
		return secondInapp;
	}

	public boolean isThirdInapp()
	{
		return thirdInapp;
	}
}
