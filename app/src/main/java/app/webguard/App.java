package app.webguard;

import android.content.Intent;
import android.content.IntentFilter;

import app.NetworkStateChangeReceiver;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;

public class App extends app.App {

    @Override
    public void onCreate() {
        super.onCreate();

        initNetworkStateChangeReceiver(); // для Android N и выше нужно явно регистрировать броадкаст ресивер

        initAppReceiver();

        initReferalReceiver();

    }

//    https://developer.android.com/topic/performance/background-optimization
    private void initNetworkStateChangeReceiver() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(CONNECTIVITY_ACTION);
            registerReceiver(new NetworkStateChangeReceiver(), intentFilter);
  //      }
    }

    private void initAppReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_INSTALL);
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        intentFilter.addDataScheme("package");
        registerReceiver(new app.AppReceiver(), intentFilter);
    }

    private void initReferalReceiver() {
        registerReceiver(new app.ReferalReceiver(), new IntentFilter("com.android.vending.INSTALL_REFERRER"));
    }

}
