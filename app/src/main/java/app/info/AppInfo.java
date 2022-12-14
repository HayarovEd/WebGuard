package app.info;

import android.util.Base64;
import app.common.Hasher;
import app.common.Utils;
import org.json.JSONException;
import org.json.JSONObject;

class AppInfo
{
	public String apkPath = null;
	String packageName = null;
	String name = null;
	String versionName = null;
	String apkHashSha1 = null;
	String apkHashSha256 = null;
	String apkHashMd5 = null;
	String dexHash = null;
	int hashesVersion = 0;
	int hashesVersionCode = 0;
	int versionCode = 0;
	// какой файл качал
	String url = null;
	long installTime = 0;
	long updateTime = 0;
	long size = 0;
	int fileType = -1;

	public AppInfo(String pack, String name, String verName, int verCode, long installTime, long updateTime, String url)
	{
		packageName = pack;
		this.name = name;
		versionName = verName;
		versionCode = verCode;
		this.installTime = installTime;
		this.updateTime = updateTime;
		this.url = url;
	}

	public AppInfo(String pack, String name, String verName, int verCode, long installTime, long updateTime)
	{
		packageName = pack;
		this.name = name;
		versionName = verName;
		versionCode = verCode;
		this.installTime = installTime;
		this.updateTime = updateTime;
	}

	public AppInfo(String json)
	{
		try
		{
			JSONObject obj = new JSONObject(json);
			parseFromJSON(obj);
		}
		catch (JSONException e) { e.printStackTrace(); }
	}

	public AppInfo(JSONObject obj)
	{
		parseFromJSON(obj);
	}

	private void parseFromJSON(JSONObject obj)
	{
		packageName = obj.optString("packageName");
		name = obj.optString("name");
		versionName = obj.optString("versionName");
		versionCode = obj.optInt("versionCode");
		installTime = obj.optLong("installTime");
		updateTime = obj.optLong("updateTime");
		url = obj.optString("downloaded");
		size = obj.optLong("size");

		apkHashSha1 = obj.optString("apkHashSha1");
		apkHashSha256 = obj.optString("apkHashSha256");
		apkHashMd5 = obj.optString("apkHashMd5");
		dexHash = obj.optString("dexHash");
		hashesVersionCode = obj.optInt("hashesVersionCode", 0);

		fileType = obj.optInt("fileType", -1);
	}

	public JSONObject getJSON()
	{
		JSONObject obj = new JSONObject();
		try
		{
			obj.put("packageName", packageName);
			obj.put("name", name);
			obj.put("versionCode", versionCode);
			obj.put("versionName", versionName);
			obj.put("installTime", installTime);
			obj.put("updateTime", updateTime);
			obj.put("downloaded", url);
			obj.put("size", size);

			obj.put("apkHashSha1", apkHashSha1);
			obj.put("apkHashSha256", apkHashSha256);
			obj.put("apkHashMd5", apkHashMd5);
			obj.put("dexHash", dexHash);
			obj.put("hashesVersionCode", hashesVersionCode);

			if (fileType > 0)
				obj.put("fileType", fileType);

			return obj;
		}
		catch (JSONException e) { e.printStackTrace(); }

		return null;
	}

	public void updateHashes()
	{
		if (apkPath == null)
			return;

		if (apkHashSha1 != null && !apkHashSha1.isEmpty() &&
			apkHashSha256 != null && !apkHashSha256.isEmpty() &&
			apkHashMd5 != null && !apkHashMd5.isEmpty() &&
			dexHash != null && !dexHash.isEmpty() &&
			hashesVersionCode == versionCode)
		{
			return;
		}

		//byte[] buf = Hasher.sha1File(apkPath);
		//if (buf != null)
		//	  apkHash = Base64.encodeToString(buf, Base64.NO_WRAP);

		Hasher hasher = new Hasher(null, null);
		hasher.hashFile(apkPath);

		byte[] buf = hasher.getSha1();
		if (buf != null)
			apkHashSha1 = Base64.encodeToString(buf, Base64.NO_WRAP);

		buf = hasher.getSha256();
		if (buf != null)
			apkHashSha256 = Base64.encodeToString(buf, Base64.NO_WRAP);

		buf = hasher.getMd5();
		if (buf != null)
			apkHashMd5 = Base64.encodeToString(buf, Base64.NO_WRAP);

		dexHash = Hasher.sha1DexFromApkAsBase64(apkPath);

		hashesVersionCode = versionCode;
		size = Utils.fileSize(apkPath);
	}

	@Override
	public String toString()
	{
		return getJSON().toString();
	}
}
