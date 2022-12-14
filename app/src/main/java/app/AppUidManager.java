package app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by Roman Popov on 27.08.17.
 */

public class AppUidManager {

    private static AppUidManager mInstance;
    private Context mContext;
    private final ConcurrentHashMap<Integer, String[]> mNames = new ConcurrentHashMap<>();

    public static AppUidManager getInstance(Context context) {

        if (mInstance == null) {
            mInstance = new AppUidManager(context);
        }

        return mInstance;

    }

    private AppUidManager(Context context) {

        mContext = context.getApplicationContext();

    }

    public synchronized void hashAllUid() {

        ConcurrentHashMap<Integer, ArrayList<String>> names = new ConcurrentHashMap<>();

        PackageManager packageManager = mContext.getPackageManager();

        final List<PackageInfo> list = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);

        for (PackageInfo info : list) {
            try {

                int uid = packageManager.getApplicationInfo(info.packageName, 0).uid;
                if (!names.containsKey(uid)) {
                    names.put(uid, new ArrayList<String>());
                }
                names.get(uid).add(info.packageName);

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        synchronized (mNames) {
            mNames.clear();

            Iterator<ConcurrentMap.Entry<Integer, ArrayList<String>>> iterator = names.entrySet().iterator();

            while (iterator.hasNext()) {

                ConcurrentMap.Entry<Integer, ArrayList<String>> _names = iterator.next();
                String[] values = _names.getValue().toArray(new String[_names.getValue().size()]);
                mNames.put(_names.getKey(), values);

            }
        }

    }

    public String[] getNamesFromUid(int uid) {

        synchronized (mNames) {

            return mNames.get(uid);

        }

    }

}
