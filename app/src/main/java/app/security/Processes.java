
package app.security;

import android.util.SparseArray;

import app.App;
import app.AppUidManager;
import app.common.LibNative;
import java.util.HashSet;
import java.util.Set;

public class Processes
{
	private static final SparseArray<String[]> uidCache = new SparseArray<String[]>();

	public static void clearCaches()
	{
		synchronized (uidCache)
		{
			uidCache.clear();
		}
	}

	/*
	 * search processes by uid and return it name (pkgname) (see LibNative.getNamesFromUid)
	 *
	 * works only if app with uid is running!
	 * also remove from name some data (XXX.chrome:sandboxed_process0 -> XXX.chrome)
	 */
	public static String[] getNamesFromUid(int uid) {

		String[] res;

		synchronized (uidCache) { // TODO XXX may be optimize locking?
			res = uidCache.get(uid);
		}
		if (res != null)
			// TODO XXX if not all processes with uid was running when put in cache?! may be add timeout?
			return res;

		res = LibNative.getNamesFromUid(uid, 0);

		if (res != null && res.length > 0) {
			int pos;
			Set<String> names = new HashSet<String>();

			for (String pkgName : res) {
				if ((pos = pkgName.indexOf(':')) > 0)
					pkgName = pkgName.substring(0, pos);
				names.add(pkgName);
			}

			res = new String[names.size()];
			res = names.toArray(res);

			synchronized (uidCache) {
				uidCache.put(uid, res);
			}

		} else {

			// если не удалось определить название пакета приложения (для Android 7 и выше) - то определяем по базе установленных пакетов
			res = AppUidManager.getInstance(App.getContext()).getNamesFromUid(uid);

			if (res != null && res.length > 0) {

				synchronized (uidCache) {
					uidCache.put(uid, res);
				}

			}

		}

		return res;
	}

}
