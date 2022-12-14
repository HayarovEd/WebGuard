package app;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

/**
 * Created by Roman Popov on 27.08.17.
 * <p>
 * служба формирует информацию об uid всех приложений (для определения packageName по uid для Android 7 и выше, тк доступ к /proc гугл запретил)
 */

public class AppUidService extends JobIntentService {

    private static final int JOB_ID = 1338;

    private static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, AppUidService.class, JOB_ID, work);
    }

    public static void hashAllUid(Context context) {
        enqueueWork(context, new Intent(context, AppUidService.class));
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        AppUidManager.getInstance(this).hashAllUid();
    }

}
