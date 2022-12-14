package app.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.squareup.okhttp.OkHttpClient;
import org.squareup.okhttp.OkUrlFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.locks.ReentrantLock;

import app.App;
import app.UpdaterService;
import app.common.NetUtil;
import app.common.Rnd;
import app.common.Utils;
import app.common.UtilsHttpResult;
import app.common.debug.L;
import app.info.Statistics;
import app.security.Policy;
import app.ui.Notifications;
import app.ui.PromoDialogActivity;
import app.ui.TryFreeDialogActivity;

public abstract class IInAppBilling {

    public static final String PROMO_CODE_PARAM = "promoCode";
    public static final String FREE_TOKEN_PARAM = "freeToken";
    public static final String FULL_TOKEN_PARAM = "full";

    static final Bundle sku = new Bundle();

    private Thread licenseThread = null;
    private boolean stopped = false;

    ///

    public abstract void serviceBind();

    public abstract boolean serviceBindAndWait(boolean startBinding);

    public abstract boolean serviceIsBound();

    public abstract void serviceUnbind();

    public abstract void itemsLoadAndShowBuyDialog(final Context context);

    public abstract void itemsLoadAndShowBuyDialog(final Context context, final String param);

    public abstract void licenseCheck(final boolean onDemand);

    public abstract boolean purchase(Activity activity, String id, boolean isInapp, int requestCode);

    public abstract boolean purchaseComplete(Intent data);

    public abstract boolean hasGooglePlay();

    ///

    protected abstract boolean hasService();

    ///

    public IInAppBilling(Context context) {
    }

    /*
     * load active items (subscriptions variants) from WG billing server
     * return true if no errors
     */
    static synchronized boolean itemsLoadFromServer(final String param) {
        HttpURLConnection con = null;
        BufferedInputStream in = null;
        byte[] data = null;
        String signHash = null;
        String key = null;

        try {
            String addr = Preferences.getSubsUrl();
            if (addr == null) {
                return true;
            }

            // generate random value that will be returned in server signed response
            // needed to protect from mitm attack
            try {
                final byte[] keyBytes = (new Rnd()).generateBytes(32);
                key = Utils.toHex(keyBytes);
            } catch (SecurityException e) {
                e.printStackTrace();
                return false;
            }

            // send request to server

            addr += "?dev=" + App.getInstallId() +
                    "&publisher=" + Preferences.getPublisher() +
                    "&key=" + key;

            final String referrer = Preferences.getReferrer(true);
            if (referrer != null) {
                addr += "&referrer=" + referrer;
            }

            final String token = Policy.getUserToken(false);
            if (token != null) {
                addr += "&token=" + token;
            }

            if (param != null) {
                addr += "&promo=" + param; // see promoCheck
            }

            try {
                JSONObject devInfo = Preferences.getDeviceInfo();
                addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
                        "&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

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

            try {
                con.connect();
            } catch (SecurityException ex) {
                return false;
            }

            int code = con.getResponseCode();
            if (code != 200) {
                return false; // bad response
            }

            // good response, parse

            signHash = con.getHeaderField("X-Subs-Sign");

            if (signHash != null) {
                int contentLength = con.getContentLength();
                //String transferEncoding = con.getHeaderField("Transfer-Encoding");
                //boolean canRead = (contentLength > 0 || (transferEncoding != null && transferEncoding.trim().equals("chunked")));
                //if (Settings.DEBUG) L.e(Settings.TAG_INAPPBILLING, "postData ", "Content-Length: ", Integer.toString(contentLength));

                try {
                    in = new BufferedInputStream(con.getInputStream());
                } catch (IOException e) {
                }

                if (in != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream((contentLength > 0) ? contentLength : 128);
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        baos.write(buf, 0, read);
                    }

                    data = baos.toByteArray();

                    in.close();
                    in = null;
                }
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            data = null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (con != null) {
                con.disconnect();
            }
        }

        if (data == null) {
            Statistics.addLog(Settings.LOG_BILLING_SUBSDATA_ERR);
            return false; // bad, no data from server
        }

        // have response, check sign and parse subs names

        if (!UpdaterService.isSignedDataValid(data, signHash, true)) {
            Statistics.addLog(Settings.LOG_BILLING_SUBSSIGN_ERR);
            return false;
        }

        try {
            JSONObject response = new JSONObject(new String(data, 0));
            String serverKey = response.getString("key");
            JSONArray subs = response.getJSONArray("subs");

            if (serverKey == null || !serverKey.equals(key) || subs == null) {
                return false;
            }

            sku.putString("firstName", null);
            sku.putString("firstTitle", null);
            sku.putString("firstPrice", null);
            sku.putBoolean("firstInapp", false);

            sku.putString("secondName", null);
            sku.putString("secondTitle", null);
            sku.putString("secondPrice", null);
            sku.putBoolean("secondInapp", false);

            sku.putString("thirdName", null);
            sku.putString("thirdTitle", null);
            sku.putString("thirdPrice", null);
            sku.putBoolean("thirdInapp", false);

            sku.putString("freeName", null);


            for (int i = 0; i < subs.length(); i++) {
                String subname = subs.getString(i);
                if (subname.startsWith("free")) {
                    /*if (freeName == null) freeName = subname;*/
                } else if (sku.getString("firstName") == null) {
                    sku.putString("firstName", subname);
                } else if (sku.getString("secondName") == null) {
                    sku.putString("secondName", subname);
                } else if (sku.getString("thirdName") == null) {
                    sku.putString("thirdName", subname);
                }
            }

            return true;

        } catch (JSONException ex) {
            Statistics.addLog(Settings.LOG_BILLING_SUBSPARSE_ERR);
        }

        return false;
    }

    public void stop() {
        stopped = true;
    }

    ///////////////

    public boolean licenseIsCheckTime() {
        final long t = Preferences.get_l(Settings.PREF_USER_TOKEN_TIME);
        final long expireTime = t + Settings.LIC_CHECK_INTERVAL;
        final long time = System.currentTimeMillis();

        if (Settings.LIC_DISABLE) {
            return false;
        }

        //L.d(Settings.TAG_INAPPBILLING, "t = " + t+ " exp = " + expireTime + " time = " + time);
        if (Settings.LIC_CHECK_FORCE) {
            return true;
        } else if (t <= 0) { // t == 0 if no token, see Policy.clearToken
            return (Policy.getUserToken(true) != null);
        } else {
            return (t > time || expireTime < time);
        }
    }

    // onDemand - on user demand
    public void licenseCheckAsync(final boolean onDemand) {
        if (Settings.LIC_DISABLE) {
            return;
        }

        if (licenseThread != null) {
            return;
        }

        licenseThread = new Thread(new Runnable() {
            @Override
            public void run() {

                if (!hasService()) {
                    if (!serviceBindAndWait(true)) {
                        L.d(Settings.TAG_INAPPBILLING, "checkLicenseAsync() null");
                    }
                }

                licenseCheck(/*false, */onDemand);

                licenseThread = null;
            }
        });

        licenseThread.setName("checkLicenseAsync()");
        licenseThread.start();
    }

    public boolean isCheckLicense() {
        return false;
    }

    /*
     * return true of license was checked on server (and license may be bad, or expired)
     * or false on error
     */
    protected boolean licenseCheckOnServer(final String data, final String signatures, final boolean onDemand) {
        stopped = false;
        boolean checked = false;
        boolean parseErr = false;
        String request = null;

        String billing_url = Preferences.getBillingUrl();
        if (billing_url == null) {
            return false;
        }

        L.d(Settings.TAG_INAPPBILLING, "licenseCheckOnServer");

        //

        try {
            request = "dev=" + App.getInstallId() +
                    "&publisher=" + Preferences.getPublisher() +
                    "&bug=1";

            if (data != null) {
                request += "&json=" + URLEncoder.encode(data, "UTF-8");
            }

            if (signatures != null) {
                request += "&sig=" + URLEncoder.encode(signatures, "UTF-8");
            }

            final String ref = Preferences.getReferrer(true);
            if (ref != null) {
                request += "&ref=" + ref;
            }

            JSONObject devInfo = Preferences.getDeviceInfo();
            request += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
                    "&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");

        } catch (UnsupportedEncodingException e) {
            if (Settings.EVENTS_LOG) {
                if (onDemand) {
                    Statistics.addLog(Settings.LOG_EXCEPTION + " licenseCheckOnServer UnsupportedEncodingException " + e.getMessage() + " in request " + request);
                }
            }
            e.printStackTrace();
        } catch (JSONException e) {
            if (Settings.EVENTS_LOG) {
                if (onDemand) {
                    Statistics.addLog(Settings.LOG_EXCEPTION + " licenseCheckOnServer JSONException " + e.getMessage() + " in request " + request);
                }
            }
            e.printStackTrace();
        }

        if (request == null) {
            return false;
        }

        // try to check license on our server (3 times with 5 sec interval)

        for (int i = 0; i < 3; i++) {

            if (NetUtil.getStatus() == -1 || stopped) {
                stopped = true;
                break;
            }

            L.d(Settings.TAG_INAPPBILLING, "Checking license from our server...");

            UtilsHttpResult response = Utils.postData(billing_url, request.getBytes());
            if (response.isFailed()) {
                Utils.sleep(5000); // wait
                continue;
            }

            // check server answer

            L.d(Settings.TAG_INAPPBILLING, "Got answer...");

            int code = response.getResultCode();
            byte[] responseData = response.getData();
            if (Settings.EVENTS_LOG) {
                if (onDemand) {
                    Statistics.addLog(Settings.LOG_BILLING_SRVSUBS_CODE + code);
                }
            }

            if ((code != 200 && code != 404 && code != 410) || responseData == null || responseData.length == 0) {
                // bad answer
                L.e(Settings.TAG_INAPPBILLING, "Bad response from server!");
                if (NetUtil.getStatus() != -1) {
                    Notifications.showLicenseCheckError(App.getContext());
                }

                break;
            }

            // parse server answer

            String token = null;
            String full = null;
            String ok = null;

            try {
                JSONObject result = new JSONObject(new String(responseData, 0));
                token = result.optString("token", null); // must be in 200
                full = result.optString("full", null);     // must be in 200
                ok = result.optString("ok", null);         // must be in 404, 410
            } catch (JSONException ex) {
                L.d(Settings.TAG_INAPPBILLING, "Failed to parse server license data.");
            }

            if ((code == 200 && (token == null || full == null)) || (code != 200 && ok == null)) {
                // 200 or 404, 410 without required params
                parseErr = true;
            } else if (code == 200 && !Settings.DEBUG_NOSUBS) {
                // 200 - ok, have subscription, parse answer

                L.d(Settings.TAG_INAPPBILLING, "Got account: ", token);
                Preferences.enableProFunctions(token, "1".equals(full));
            } else {
                // 410 - subscription expired (gone)
                // 404 - subscription not found

                //Statistics.addLog("subs code " + code);

                // see enableProFunctions, clearProFunctions
                if (code == 410) {
                    Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_WAS, true);
                } else {
                    Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_WAS, false);
                }
                Preferences.putBoolean(Settings.PREF_SUBSCRIPTION_EXPIRED, false);

                subscriptionDisable(!onDemand);
            }

            if (!parseErr) {
                checked = true;
            }
            break;
        } // for

        if (parseErr) {
            Statistics.addLog(Settings.LOG_BILLING_SRVPARSE_ERR);
        } else if (!checked && !stopped) {
            Statistics.addLog(Settings.LOG_BILLING_SRVSUBS_ERR);
        } else if (checked) {
            Policy.reloadPrefs();
        }

        return checked;
    }

    // return true if show notification to user
    public boolean subscriptionDisable(boolean showNotify) {
        boolean show = false;

        Preferences.editStart();

        String token = Policy.getUserToken(false);
        boolean isFree = Policy.isFreeToken(token);

        if (Preferences.get(Settings.PREF_SUBSCRIPTION_WAS) && !Preferences.get(Settings.PREF_SUBSCRIPTION_EXPIRED)) {
            // have expired subscription, see notifyAboutSubscriptionFeatures
            final int count = Preferences.get_i(Settings.PREF_NO_SUBS_AD_COUNT);
            if (count == 0) {
                Preferences.putInt(Settings.PREF_NO_SUBS_AD_COUNT, 1);
            }

            if (showNotify) {
                show = true;
            }
        }

        Preferences.clearProFunctions(false);

        Preferences.editEnd();

        if (show) {
            if (isFree) {
                Notifications.showSubsExpiredFree();
            } else {
                Notifications.showSubsExpired();
            }
        }

        App.disable(); // remove this if have return free functional

        return show;
    }

    // freeTry -> TryFreeDialogActivity -> freeTry -> freeConfirm ->
    //	  activity.setResult(RESULT_OK, intent) -> freeComplete
    public boolean freeGet(Activity activity, String id, int requestCode) {
        return freeGet(activity, id, requestCode, null);
    }

    public boolean freeGet(Activity activity, String id, int requestCode, String param) {
        final String freeParam = (param != null) ? PROMO_CODE_PARAM : null;
        final String freeCode = (param != null) ? param : null;

        Intent intent = new Intent(activity, TryFreeDialogActivity.class);
        if (freeParam != null) {
            intent.putExtra(freeParam, freeCode);
        }
        activity.startActivityForResult(intent, requestCode);

        return false;
    }

    public boolean freeComplete(Intent data) {
        // see checkCaptchaOnServer
        String token = data.getStringExtra(FREE_TOKEN_PARAM);
        if (token == null || token.isEmpty()) {
            return false;
        }

        boolean isFull = data.hasExtra(FULL_TOKEN_PARAM); // see freeConfirm
        Preferences.enableProFunctions(token, isFull);

        return true;
    }

    // return -1 on error, 0 - all ok, 1 - if license not found or expired
    public int freeCheck(final String token, final boolean onDemand) {
        HttpURLConnection con = null;
        String status = null;

        if (token == null || token.isEmpty()) {
            return -1;
        }

        String addr = Preferences.getFreeUrl();
        if (addr == null) {
            return -1;
        }

        L.d(Settings.TAG_INAPPBILLING, "freeCheck");

        //

        addr += "?action=check_free_token" +
                "&dev=" + App.getInstallId() +
                "&publisher=" + Preferences.getPublisher() +
                "&token=" + token;

        final String ref = Preferences.getReferrer(true);
        if (ref != null) {
            addr += "&ref=" + ref;
        }

        try {
            JSONObject devInfo = Preferences.getDeviceInfo();
            addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
                    "&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
        } catch (JSONException ex) {
        }

        // TODO XXX add several attempts, see licenseCheckOnServer

        try {
            // send request to server

            // see Utils.postData (TODO XXX merge all code)
            URL url = new URL(addr);

            String host = url.getHost();
            String ip = null;
            if (host != null) {
                ip = NetUtil.lookupIp(host, 3);
            }

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

            try {
                con.connect();
            } catch (SecurityException ex) {
                return -1;
            }

            int code = con.getResponseCode();
            if (Settings.EVENTS_LOG) {
                if (onDemand) Statistics.addLog(Settings.LOG_BILLING_SRVFREESUBS_CODE + code);
            }

            if (code != 200) {
                return -1; // bad response
            }

            // good response, parse

            status = con.getHeaderField("X-Free-Status");
            //status = "ok";

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        // check for errors
        int retval = 0;
        if ("error".equals(status)) {
            retval = 1;
        } else if (!"ok".equals(status)) {
            retval = -1;
        }

        if (Settings.DEBUG_NOFREESUBS) {
            retval = 1;
        }

        //
        if (retval == 0) {
            // update token time (see licenseIsCheckTime)
            long time = System.currentTimeMillis();
            Preferences.putLong(Settings.PREF_USER_TOKEN_TIME, time);
        } else if (retval == 1) {
            subscriptionDisable(!onDemand);
        } else if (retval == -1) {
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
    public Bitmap freeTry(byte[] result, byte[] tokenBytes, String param, boolean useCaptcha) {
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
        if (addr == null) {
            return null;
        }

        // send request to server
        if (useCaptcha) {
            addr += "?action=get_free_token";
        } else {
            addr += "?action=get_free_token2";
        }

        addr += "&dev=" + App.getInstallId() + "&publisher=" + Preferences.getPublisher();

        final String ref = Preferences.getReferrer(true);
        if (ref != null) {
            addr += "&ref=" + ref;
        }

        if (param != null) {
            addr += "&promo=" + param; // see promoCheck
        }

        try {
            JSONObject devInfo = Preferences.getDeviceInfo();
            addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
                    "&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
        } catch (JSONException ex) {
        }

        // TODO XXX add several attempts, see licenseCheckOnServer

        // load data
        try {
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

            try {
                con.connect();
            } catch (SecurityException ex) {
                return null;
            }

            if (con.getResponseCode() != 200) {
                return null; // bad response
            }

            // good response, parse

            status = con.getHeaderField("X-Free-Status");
            //status = "ok";
            token = con.getHeaderField("X-Free-Token");
            appAdsBlock = con.getHeaderField("X-Full");
            days = con.getHeaderField("X-Free-Days");

            if (token != null && useCaptcha) {

                int contentLength = con.getContentLength();
                //String transferEncoding = con.getHeaderField("Transfer-Encoding");
                //boolean canRead = (contentLength > 0 || (transferEncoding != null && transferEncoding.trim().equals("chunked")));
                //if (Settings.DEBUG) L.e(Settings.TAG_INAPPBILLING, "postData ", "Content-Length: ", Integer.toString(contentLength));

                try {
                    in = new BufferedInputStream(con.getInputStream());
                } catch (IOException e) {
                }

                if (in != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream((contentLength > 0) ? contentLength : 4096);
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        baos.write(buf, 0, read);
                    }

                    data = baos.toByteArray();

                    in.close();
                    in = null;
                }
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            data = null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (con != null) {
                con.disconnect();
            }
        }

        // check for errors
        if ("error".equals(status)) {
            result[0] = 1;
            return null;
        } else if (!"ok".equals(status) || (data == null && useCaptcha) || token == null || token.getBytes().length != tokenBytes.length) {
            return null;
        }

        // parse days param
        if (days != null) {
            if (days.isEmpty()) {
                return null;
            }

            int d;
            try {
                d = Integer.parseInt(days);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (d > 0) {
                result[2] = (byte) d;
            }
        }

        // have valid response, create captcha
        Bitmap image = null;
        if (useCaptcha) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (image == null) {
                return null;
            }
        }

        result[0] = 0;
        if (appAdsBlock != null && Boolean.valueOf(appAdsBlock)) {
            result[1] = 1;
        }

        System.arraycopy(token.getBytes(), 0, tokenBytes, 0, tokenBytes.length);

        return image;
    }

    /*
     * check token and captcha on server (if use captcha)
     * return 0 on success, -1 on error, 1 on invalid text
     * also set activity result, see freeTry
     */
    public int freeConfirm(Activity activity, String captchaText, String token, boolean appAdsBlock, boolean useCaptcha) {
        HttpURLConnection con = null;
        String status = null;

        if ((useCaptcha && captchaText.isEmpty()) || token.isEmpty()) {
            return -1;
        }

        Intent intent = new Intent();
        intent.putExtra(FREE_TOKEN_PARAM, token);
        if (appAdsBlock) {
            intent.putExtra(FULL_TOKEN_PARAM, true);
        }

        if (!useCaptcha) {
            activity.setResult(Activity.RESULT_OK, intent);
            return 0;
        }

        String addr = Preferences.getFreeUrl();
        if (addr == null) {
            return -1;
        }

        // send request to server

        addr += "?action=check_captcha" +
                "&dev=" + App.getInstallId() +
                "&publisher=" + Preferences.getPublisher() +
                "&token=" + token +
                "&captcha=" + captchaText;

        final String ref = Preferences.getReferrer(true);
        if (ref != null) {
            addr += "&ref=" + ref;
        }

        try {
            JSONObject devInfo = Preferences.getDeviceInfo();
            addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
                    "&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
        } catch (JSONException e) {
        }

        // TODO XXX add several attempts, see licenseCheckOnServer

        // load data
        try {
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

            try {
                con.connect();
            } catch (SecurityException ex) {
                return -1;
            }

            if (con.getResponseCode() != 200) {
                return -1; // bad response
            }

            // good response, parse

            status = con.getHeaderField("X-Free-Status");
            //status = "ok";

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        // check for errors
        if ("error".equals(status)) {
            return 1;
        } else if (!"ok".equals(status)) {
            return -1;
        }

        // X-FreeStatus == ok
        activity.setResult(Activity.RESULT_OK, intent);
        return 0;
    }

    // promoAsk -> PromoDialogActivity -> promoCheck -> activity.setResult(RESULT_OK, intent)
    public boolean promoAsk(Activity activity, int requestCode) {
        activity.startActivityForResult(new Intent(activity, PromoDialogActivity.class), requestCode);
        return false;
    }

    /*
     * return -1 on error, 0 - all ok, 1 - if promocode not found, 2 - or expired
     * also set activity result, see promoAsk
     */
    public int promoCheck(final Activity activity, final String promoCode) {
        HttpURLConnection con = null;
        String status = null;

        if (promoCode == null) {
            return -1;
        }

        if (promoCode.isEmpty()) {
            Intent intent = new Intent();
            activity.setResult(Activity.RESULT_OK, intent);
            return 0;
        }

        String addr = Preferences.getPromoUrl();
        if (addr == null) {
            return -1;
        }

        //

        addr += "?code=" + promoCode +
                "&dev=" + App.getInstallId() +
                "&publisher=" + Preferences.getPublisher();

        final String ref = Preferences.getReferrer(true);
        if (ref != null) {
            addr += "&ref=" + ref;
        }

        // TODO XXX add several attempts, see licenseCheckOnServer

        try {
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

            try {
                con.connect();
            } catch (SecurityException ex) {
                return -1;
            }

            if (con.getResponseCode() != 200) {
                return -1; // bad response
            }

            // good response, parse

            status = con.getHeaderField("X-Promo-Status");
            //status = "ok";

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        // check for errors
        if ("0".equals(status) || "-1".equals(status)) {
            return 1; // not found
        } else if ("2".equals(status)) {
            return 2; // expired
        } else if (!"1".equals(status)) {
            return -1;
        }

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
    public int recoveryCheck(final boolean onDemand) {
        HttpURLConnection con = null;
        String status = null;
        String referrer = null;
        String token = null;
        String full = null;

        String addr = Preferences.getRecoveryUrl();
        if (addr == null) {
            return -1;
        }

        L.d(Settings.TAG_INAPPBILLING, "recoveryCheck");

        try {

            addr += "?dev=" + App.getInstallId() +
                    "&publisher=" + Preferences.getPublisher();

            final String ref = Preferences.getReferrer(true);
            if (ref != null) {
                addr += "&ref=" + ref;
            }

            JSONObject devInfo = Preferences.getDeviceInfo();
            addr += "&vendor=" + URLEncoder.encode(devInfo.getString("manufacturer"), "UTF-8") +
                    "&model=" + URLEncoder.encode(devInfo.getString("model"), "UTF-8");

        } catch (UnsupportedEncodingException e) {
            return -1;
        } catch (JSONException ex) {
            return -1;
        }

        // TODO XXX add several attempts, see licenseCheckOnServer

        try {
            // send request to server

            // see Utils.postData (TODO XXX merge all code)
            URL url = new URL(addr);

            String host = url.getHost();
            String ip = null;
            if (host != null) {
                ip = NetUtil.lookupIp(host, 3);
            }

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

            try {
                con.connect();
            } catch (SecurityException ex) {
                return -1;
            }

            int code = con.getResponseCode();
            if (Settings.EVENTS_LOG) {
                if (onDemand) {
                    Statistics.addLog(Settings.LOG_BILLING_SRVREC_CODE + code);
                }
            }

            if (code != 200) {
                return -1; // bad response
            }

            // good response, parse

            status = con.getHeaderField("X-Recovery-Status");
            referrer = con.getHeaderField("X-Recovery-Partner");
            token = con.getHeaderField("X-Recovery-Free-Token");
            full = con.getHeaderField("X-Recovery-Full");

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        // check return value

        int retval = 0;
        if ("1".equals(status)) {
            retval = 1;
        } else if ("2".equals(status)) {
            retval = 2;
        }

        if (Settings.DEBUG_NOFREESUBS) {
            retval = 2;
            referrer = null;
            token = null;
            full = null;
        }

        if (retval == 1 || retval == 2) {
            Preferences.putInt(Settings.PREF_RECOVERY_STATUS, retval);
        }

        // restore referrer if missed
        Preferences.editStart(); // synchronization with ReferalReceiver

        if (referrer != null && Preferences.getReferrer(false) == null) {
            try {
                referrer = URLDecoder.decode(referrer, "UTF-8");
            } catch (Exception ex) {
            }
            if (!referrer.isEmpty()) {
                referrer = "utm_source=" + referrer + "&utm_medium=banner&utm_campaign=aff";
                Preferences.putString(Settings.PREF_REFERRER, referrer);
                Preferences.putBoolean(Settings.PREF_REFERRER_RECOVERY, true); // see referrerUpdate
            }
        }

        Preferences.editEnd();

        // restore free subscription if have
        if (token != null) {
            Preferences.enableProFunctions(token, "true".equals(full));
            retval = 3;
        }

        return retval;
    }

    // update referrer if have promocode and no referrer (return true if updated)
    public boolean referrerUpdate(String promoCode) {
        if (promoCode == null || promoCode.isEmpty()) {
            return false;
        }

        Preferences.editStart(); // synchronization with ReferalReceiver

        boolean result = false;
        String r = Preferences.getReferrer(false);
        boolean recovery = Preferences.get(Settings.PREF_REFERRER_RECOVERY); // see recoveryCheck

        if (r == null || r.isEmpty() || recovery) {
            // need update

            r = "utm_source=" + promoCode + "&utm_medium=promo&utm_campaign=aff";
            Preferences.putString(Settings.PREF_REFERRER, r);
            if (recovery) {
                Preferences.putBoolean(Settings.PREF_REFERRER_RECOVERY, false);
            }

            result = true;
        }

        Preferences.editEnd();

        return result;
    }

    public boolean hasNoItems() {
        return (sku.getString("firstPrice") == null && sku.getString("secondPrice") == null && sku.getString("thirdPrice") == null && sku.getString("freeName") == null);
    }

    public boolean hasFirst() {
        return (sku.getString("firstPrice") != null);
    }

    public boolean hasSecond() {
        return (sku.getString("secondPrice") != null);
    }

    public boolean hasThird() {
        return (sku.getString("thirdPrice") != null);
    }

    public boolean hasFree() {
        return (sku.getString("freeName") != null);
    }

    public String getFirstName() {
        return sku.getString("firstName");
    }

    public String getSecondName() {
        return sku.getString("secondName");
    }

    public String getThirdName() {
        return sku.getString("thirdName");
    }

    public String getFreeName() {
        return sku.getString("freeName");
    }

    public String getFirstText() {
        return getText(sku.getString("firstName"), sku.getString("firstTitle"), sku.getString("firstPrice"));
    }

    public String getSecondText() {
        return getText(sku.getString("secondName"), sku.getString("secondTitle"), sku.getString("secondPrice"));
    }

    public String getThirdText() {
        return getText(sku.getString("thirdName"), sku.getString("thirdTitle"), sku.getString("thirdPrice"));
    }

    private String getText(String name, String title, String price) {
        if (name != null && title != null && price != null) {
            final String appName = " (" + Settings.APP_NAME + ")";
            int pos = title.indexOf(appName);
            if (pos >= 0) {
                title = title.substring(0, pos);
            }

            if (name.indexOf("promo") >= 0) {
                return title;
            }

            return (title + " - " + price);
        }

        return null;
    }

    public boolean isFirstInapp() {
        return sku.getBoolean("firstInapp", false);
    }

    public boolean isSecondInapp() {
        return sku.getBoolean("secondInapp", false);
    }

    public boolean isThirdInapp() {
        return sku.getBoolean("thirdInapp", false);
    }

}
