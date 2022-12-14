package app.info;

import app.security.Processes;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

public class AppUrlList
{
	private int uid = -1;
	private String[] packageNames = null;
	private boolean isBrowser = false;
	private HashSet<UrlRecord> records = new HashSet<UrlRecord>();

/*
	public AppUrlList(int uid)
	{
		this.uid = uid;
		packageNames = LibNative.getNamesFromUid(uid);
		isBrowser = Browsers.isBrowser(packageNames); // TODO when it has cache of uid=isBrowser change to uid
	}
*/

	public AppUrlList(int uid, boolean isBrowser)
	{
		this.uid = uid;
		this.isBrowser = isBrowser;
		packageNames = Processes.getNamesFromUid(uid);
	}

/*
	public AppUrlList(int uid, String[] packageNames)
	{
		this.uid = uid;
		this.packageNames = packageNames;
		isBrowser = Browsers.isBrowser(packageNames); // TODO when it has cache of uid=isBrowser change to uid
	}
*/

	public AppUrlList(JSONObject json)
	{
		uid = json.optInt("uid");
		isBrowser = json.optBoolean("browser");

		if (json.has("packages"))
		{
			try
			{
				JSONArray arr = json.getJSONArray("packages");
				packageNames = new String[arr.length()];
				for (int i = 0; i < packageNames.length; ++i)
					packageNames[i] = arr.getString(i);

				if (json.has("records"))
				{
					arr = json.getJSONArray("records");
					for (int i = 0; i < arr.length(); ++i)
						records.add(new UrlRecord(arr.getJSONObject(i)));
				}
			}
			catch (JSONException e) { e.printStackTrace(); }
		}
	}

	public void addUrl(String url, String referrer, int type, int[] records)
	{
		this.records.add(new UrlRecord(url, referrer, type, records));
	}

	public JSONObject getJSON()
	{
		if (packageNames == null)
			return null;

		JSONObject obj = new JSONObject();
		try
		{
			obj.put("uid", uid);

			JSONArray arr = new JSONArray();
			for (String packageName : packageNames)
				arr.put(packageName);

			obj.put("packages", arr);
			obj.put("browser", isBrowser);

			arr = new JSONArray();
			for (UrlRecord rec : records)
				arr.put(rec.getJSON());

			obj.put("records", arr);

			return obj;
		}
		catch (JSONException e) { e.printStackTrace(); }

		return null;
	}

	public int getUid()
	{
		return uid;
	}

	public String[] getPackageNames()
	{
		return packageNames;
	}

	class UrlRecord
	{
		public String url = null;
		public String referrer = null;
		public int type = -1;
		public int[] records = null; // records number from database (if have)

		public UrlRecord(String url, String referrer, int type, int[] records)
		{
			this.url = url;
			this.referrer = referrer;
			this.type = type;
			this.records = records;
		}

		public UrlRecord(JSONObject json)
		{
			url = json.optString("url");
			referrer = json.optString("referrer");
			type = json.optInt("type", -1);

			if (json.has("records"))
			{
				try
				{
					JSONArray arr = json.getJSONArray("records");
					records = new int[arr.length()];
					for (int i = 0; i < records.length; ++i)
						records[i] = arr.getInt(i);
				}
				catch (JSONException e) { e.printStackTrace(); }
			}
		}

		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof UrlRecord))
				return false;

			UrlRecord rec = (UrlRecord) obj;

			if (url != null && !url.equals(rec.url))
				return false;

			if (referrer != null && !referrer.equals(rec.referrer))
				return false;

			if (type != rec.type)
				return false;

			if ((records != null && rec.records == null) || (records == null && rec.records != null))
				return false;

			if (records != null)
			{
				if (records.length != rec.records.length)
					return false;

				for (int i = 0; i < records.length; ++i)
				{
					if (records[i] != rec.records[i])
						return false;
				}
			}

			return true;
		}

		@Override
		public int hashCode()
		{
			return getJSON().toString().hashCode();
		}

		public JSONObject getJSON()
		{
			JSONObject obj = new JSONObject();
			try
			{
				obj.put("url", url);
				obj.put("referrer", referrer);

				if (type >= 0)
					obj.put("type", type);

				if (records != null)
				{
					JSONArray arr = new JSONArray();
					for (int rec : records)
						arr.put(rec);
					obj.put("records", arr);
				}

				return obj;
			}
			catch (JSONException e) { e.printStackTrace(); }

			return null;
		}
	}
}
