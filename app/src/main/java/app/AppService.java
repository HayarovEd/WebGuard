package app;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import app.info.AppManager;


public class AppService extends JobIntentService {

    private static final int JOB_ID = 1340;

    private static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, AppService.class, JOB_ID, work);
    }

    public static void hashAllApps(Context context) {
        enqueueWork(context, new Intent(context, AppService.class));
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        if (!App.isLibsLoaded()) {
            return;
        }

        // service to hash all packages
        AppManager.updateAppList(null);
    }

}
