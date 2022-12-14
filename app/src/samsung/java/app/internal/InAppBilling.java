package app.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.samsung.android.sdk.iap.lib.helper.HelperDefine;
import com.samsung.android.sdk.iap.lib.helper.IapHelper;
import com.samsung.android.sdk.iap.lib.listener.OnGetOwnedListListener;
import com.samsung.android.sdk.iap.lib.listener.OnGetProductsDetailsListener;
import com.samsung.android.sdk.iap.lib.listener.OnPaymentListener;
import com.samsung.android.sdk.iap.lib.vo.ErrorVo;
import com.samsung.android.sdk.iap.lib.vo.OwnedProductVo;
import com.samsung.android.sdk.iap.lib.vo.ProductVo;
import com.samsung.android.sdk.iap.lib.vo.PurchaseVo;

import java.util.ArrayList;

import app.App;
import app.TimerService;
import app.UpdaterService;
import app.common.Utils;
import app.common.debug.L;
import app.info.Statistics;
import app.security.Policy;
import app.ui.BuyDialogActivity;
import app.ui.OptionsActivity;

public class InAppBilling extends IInAppBilling implements OnPaymentListener {

    public interface OnItemsLoaded {
        void onItemsLoaded();
    }

    public interface OnLicenseChecked {
        void onLicenseChecked();
    }

    private IapHelper mIapHelper;

    public InAppBilling(Context context) {
        super(context);

        if (Settings.LIC_USE_GP) {
            mIapHelper = IapHelper.getInstance(App.getContext());
            mIapHelper.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_PRODUCTION);
//            mIapHelper.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_TEST);
        } else {
            mIapHelper = null;
        }
    }

    @Override
    public void serviceBind() {
    }

    @Override
    public boolean serviceBindAndWait(boolean startBinding) {
        if (startBinding) {
            serviceBind();
        }
        Utils.sleep(1000);
        return serviceIsBound();
    }

    @Override
    public boolean serviceIsBound() {
        return true;
    }

    @Override
    public void serviceUnbind() {
    }

    @Override
    public void itemsLoadAndShowBuyDialog(final Context context) {
        itemsLoadAndShowBuyDialog(context, null);
    }

    @Override
    public void itemsLoadAndShowBuyDialog(final Context context, final String param) {
        Thread itemsThreadLocal = new Thread(new Runnable() {
            @Override
            public void run() {
                itemsLoad(param, new OnItemsLoaded() {
                    @Override
                    public void onItemsLoaded() {
                        BuyDialogActivity.showAbove(context, null, null);
                    }
                });
            }
        });
        itemsThreadLocal.setName("loadItemsAndShowBuyDialog()");
        itemsThreadLocal.start();
    }

    public void licenseCheck(final boolean onDemand, final OnLicenseChecked onLicenseChecked) {

        L.d(Settings.TAG_INAPPBILLING, "licenseCheck");

        if (Settings.LIC_DISABLE) {
            if (onLicenseChecked != null) {
                onLicenseChecked.onLicenseChecked();
            }
            return;
        }

        // set fake token :)
        if (Settings.DEBUG_FAKETOKEN) {
            Preferences.enableProFunctions("098f6bcd4621d373cade4e832627b4f6", true);
            Policy.reloadPrefs();
            if (onLicenseChecked != null) {
                onLicenseChecked.onLicenseChecked();
            }
            return;
        }

        // check for old user
        int recovery = Preferences.get_i(Settings.PREF_RECOVERY_STATUS);
        if (Settings.DEBUG_FREECHECK) {
            recovery = 0;
        }

        if (recovery == 0 || onDemand) {
            // no recovery check yet or manual run
            recovery = recoveryCheck(onDemand);
            if (Settings.EVENTS_LOG) {
                if (onDemand) {
                    Statistics.addLog(Settings.LOG_BILLING_SRVREC_STATE + recovery);
                }
            }

            if (recovery == 3 && !Settings.DEBUG_FREECHECK) { // have free token
                if (onLicenseChecked != null) {
                    onLicenseChecked.onLicenseChecked();
                }
                return;
            }
        }

        // free token?
        String token = Policy.getUserToken(false);
        if (Policy.isFreeToken(token) || (Settings.DEBUG_FREECHECK && token != null)) {
            int result = freeCheck(token, onDemand);
            if (Settings.EVENTS_LOG) {
                if (onDemand) {
                    Statistics.addLog(Settings.LOG_BILLING_SRVFREESUBS_STATE + result);
                }
            }

            if (result == 0) { // have free subscription
                if (onLicenseChecked != null) {
                    onLicenseChecked.onLicenseChecked();
                }
                return;
            } else if (result == -1) { // free check error
                if (onLicenseChecked != null) {
                    onLicenseChecked.onLicenseChecked();
                }
                return;
            }
        }


        // paid token?
        mIapHelper.getOwnedList(HelperDefine.PRODUCT_TYPE_ALL, new OnGetOwnedListListener() {
            @Override
            public void onGetOwnedProducts(final ErrorVo _errorVO, final ArrayList<OwnedProductVo> _ownedList) {

                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        boolean hasLicense = false;

                        if (_errorVO != null && _errorVO.getErrorCode() == HelperDefine.IAP_ERROR_NONE) {

                            if (_ownedList != null && _ownedList.size() > 0) {

                                if (Settings.EVENTS_LOG) {
                                    if (onDemand) {
                                        Statistics.addLog(Settings.LOG_BILLING_GPSUBS_STATE + _ownedList.size());
                                    }
                                }

                                StringBuilder data = new StringBuilder();
                                StringBuilder sigs = new StringBuilder();

                                data.append('[');
                                sigs.append('[');
                                boolean find = false;
                                for (OwnedProductVo ownedProductVo : _ownedList) {
                                    data.append(',');
                                    data.append(ownedProductVo.getJsonString());
                                    find = true;
                                }
                                if (find) {
                                    data.deleteCharAt(1);
                                }
                                data.append(']');
                                sigs.append(']');

                                boolean result = licenseCheckOnServer(data.toString(), sigs.toString(), onDemand);
                                if (result) {
                                    hasLicense = true;
                                }

                                if (Settings.EVENTS_LOG) {
                                    if (onDemand) {
                                        Statistics.addLog(Settings.LOG_BILLING_SRVSUBS_STATE + ((result) ? "1" : "0"));
                                    }
                                }

                            } else {

                                if (_ownedList == null) {
                                    if (Settings.EVENTS_LOG) {
                                        if (onDemand) {
                                            Statistics.addLog(Settings.LOG_BILLING_SUBSGP_NULL);
                                        }
                                    }
                                } else {
                                    if (Settings.EVENTS_LOG) {
                                        if (onDemand) {
                                            Statistics.addLog(Settings.LOG_BILLING_GPSUBS_STATE + _ownedList.size());
                                        }
                                    }
                                }

                            }

                        } else {

                            Statistics.addLog(Settings.LOG_BILLING_SUBSGP_ERR + (_errorVO != null ? _errorVO.getErrorString() : ""));

                        }

                        if (!hasLicense && Preferences.get(Settings.PREF_SUBSCRIPTION_EXPIRED)) {
                            subscriptionDisable(!onDemand);

                            // notify about futures if no items, subs expire and didn't show subs expire notify
                            OptionsActivity.notifyAboutSubscriptionFeatures();
                        }

                        if (onLicenseChecked != null) {
                            onLicenseChecked.onLicenseChecked();
                        }


                    }
                }).start();

            }
        });

    }

    @Override
    public void licenseCheck(final boolean onDemand) {
        licenseCheck(onDemand, null);
    }

    private void itemsLoad(final String param, final OnItemsLoaded onItemsLoaded) {

        synchronized (sku) {

            // get info from our server
            if (!itemsLoadFromServer(param)) {
                return;
            }

            final String firstName = getFirstName();
            final String secondName = getSecondName();
            final String thirdName = getThirdName();

            if (firstName == null && secondName == null && thirdName == null) {
                return; // no active subscriptions variants
            }


            // request available subscriptions and get information from Samsung Apps

            ArrayList<String> skuList = new ArrayList<>();
            if (firstName != null) {
                skuList.add(firstName);
            }
            if (secondName != null) {
                skuList.add(secondName);
            }
            if (thirdName != null) {
                skuList.add(thirdName);
            }


            mIapHelper.getProductsDetails(TextUtils.join(",", skuList), new OnGetProductsDetailsListener() {
                @Override
                public void onGetProducts(ErrorVo _errorVO, ArrayList<ProductVo> _productList) {

                    if (_errorVO != null && _errorVO.getErrorCode() == HelperDefine.IAP_ERROR_NONE) {

                        if (_productList == null) {

                            if (Settings.EVENTS_LOG) {
                                Statistics.addLog(Settings.LOG_BILLING_SUBSGP_NULL);
                            }

                        } else if (_productList.size() == 0) {

                            if (Settings.EVENTS_LOG) {
                                Statistics.addLog(Settings.LOG_BILLING_SUBSGP_EMPTY);
                            }

                        } else {

                            synchronized (sku) {

                                for (ProductVo productVo : _productList) {

                                    String productId = productVo.getItemId();
                                    String price = productVo.getItemPriceString();
                                    String title = productVo.getItemName();
                                    String type = productVo.getType();

                                    L.e(Settings.TAG_INAPPBILLING, "Item: ", productId, " ", title, " ", price);

                                    if (firstName != null && productId.equals(firstName)) {
                                        sku.putString("firstName", firstName);
                                        sku.putString("firstPrice", price);
                                        sku.putString("firstTitle", title);
                                        sku.putBoolean("firstInapp", "Subscription".equals(type));
                                    } else if (secondName != null && productId.equals(secondName)) {
                                        sku.putString("secondName", secondName);
                                        sku.putString("secondPrice", price);
                                        sku.putString("secondTitle", title);
                                        sku.putBoolean("secondInapp", "Subscription".equals(type));
                                    } else if (thirdName != null && productId.equals(thirdName)) {
                                        sku.putString("thirdName", thirdName);
                                        sku.putString("thirdPrice", price);
                                        sku.putString("thirdTitle", title);
                                        sku.putBoolean("thirdInapp", "Subscription".equals(type));
                                    }

                                }

                                if (onItemsLoaded != null) {
                                    onItemsLoaded.onItemsLoaded();
                                }

                            }


                        }

                    } else {

                        if (Settings.EVENTS_LOG) {
                            Statistics.addLog(Settings.LOG_BILLING_SUBSGP_ERR + (_errorVO != null ? _errorVO.getErrorString() : ""));
                        }

                    }

                }
            });

        }

    }

    @Override
    public boolean purchase(Activity activity, String id, boolean isInapp, int requestCode) {
        if (hasService()) {
            mIapHelper.startPayment(id, App.getInstallId(), true, this);
            return true;
        }
        return false;
    }

    @Override
    public boolean purchaseComplete(Intent data) {
        return false;
    }

    @Override
    public boolean hasGooglePlay() {
        return true;
    }

    @Override
    protected boolean hasService() {
        return (mIapHelper != null);
    }

    @Override
    public void onPayment(ErrorVo _errorVO, final PurchaseVo _purchaseVO) {

        if (_errorVO != null && _errorVO.getErrorCode() == HelperDefine.IAP_ERROR_NONE) {

            Statistics.addLog(Settings.LOG_BUY_ACCEPTED);

            if (_purchaseVO != null) {

                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        if (licenseCheckOnServer(_purchaseVO.getJsonString(), "", false)) {

                            L.d(Settings.TAG_INAPPBILLING, "You have bought the ", _purchaseVO.getItemId(), ". Excellent choice, adventurer!");

                            TimerService.evaluateNotifyInitTimer(true);
                            TimerService.firstResultNotifyInitTimer();

                            Statistics.addLog(Settings.LOG_BUY_COMPLETED);

                        } else {

                            Statistics.addLog(Settings.LOG_BUY_ERR); // activate error

                        }

                        if (Settings.EVENTS_LOG) {
                            UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'buy completed' statistic
                        }

                    }
                }).start();

            } else {

                Statistics.addLog(Settings.LOG_BUY_ERR + " _purchaseVO is null");

            }

        } else {

            Statistics.addLog(Settings.LOG_BUY_DECLINED);
            Statistics.addLog(Settings.LOG_BUY_ERR + " " + (_errorVO != null ? _errorVO.getErrorString() : ""));

        }

    }


}
