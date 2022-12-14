package app.common;

import app.common.debug.L;
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import android.util.Base64;
import android.os.Process;
import android.view.WindowManager.BadTokenException;
import app.App;
import app.ui.OptionsActivity;
import app.Res;
import app.internal.Settings;
import org.squareup.okhttp.OkHttpClient;
import org.squareup.okhttp.OkUrlFactory;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class Utils
{
	// TODO XXX split to netfilter.utils and webguard.utils (or common.utils)

	public static final String DIALOG_TEXT	   = "dialogText";
	public static final String DIALOG_TEXT_ADD = "dialogTextAdd";
	public static final String DIALOG_TITLE    = "dialogTitle";
	public static final String DIALOG_TYPE	   = "dialogType";

	public static void startHome(Context context)
	{
		Intent intent = getStartHomeIntent();
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		context.startActivity(intent);
	}

	public static Intent getStartHomeIntent()
	{
		Intent intent = new Intent();
		intent.setAction("android.intent.action.MAIN");
		intent.addCategory("android.intent.category.HOME");

		return intent;
	}

	public static String getTopActivityPkgName(Context context)
	{
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		try
		{
			String n = am.getRunningTasks(1).get(0).baseActivity.getPackageName();
			return n;
		}
		catch (SecurityException e) { }
		catch (IndexOutOfBoundsException e) { }

		return "";
	}

	/*
	 * start standart android notification
	 *
	 * dlgType can be null
	 * use important flag to if you want the sound, led and vibration play each time the notification is sent
	 *
	 * TODO search all NotificationCompat.Builder, find proxy error notification
	 */
	public static void startNotification(Context context, int titleId, int textId,
											int dlgTitleId, int dlgTextId, String dlgTextAppend,
											String dlgType, int notificationId, boolean important, String channel_id)
	{
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(Res.drawable.icon)
				.setContentTitle(context.getText(titleId))
				.setContentText(context.getText(textId))
				.setAutoCancel(true);

		if (important)
			builder.setDefaults(Notification.DEFAULT_ALL).setOnlyAlertOnce(true);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = OptionsActivity.getIntent(context, false); // TODO XXX OptionsActivity.class
		if (dlgTitleId > 0 && dlgTextId > 0)
		{
			resultIntent.putExtra(Utils.DIALOG_TITLE, dlgTitleId);
			resultIntent.putExtra(Utils.DIALOG_TEXT, dlgTextId);
			if (dlgTextAppend != null)
				resultIntent.putExtra(Utils.DIALOG_TEXT_ADD, dlgTextAppend);
			if (dlgType != null)
				resultIntent.putExtra(Utils.DIALOG_TYPE, dlgType);
		}

		// The stack builder object will contain an artificial back stack for the started Activity.
		// This ensures that navigating backward from the Activity leads out of your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(OptionsActivity.class);

		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(notificationId, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		////

		if (notificationManager != null) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

//				String channel_id = CHANNEL_ID + "_01"; // The id of the channel.
				CharSequence name = context.getString(Res.string.app_name); // The user-visible name of the channel.
				int importance = NotificationManager.IMPORTANCE_HIGH;
				NotificationChannel channel = new NotificationChannel(channel_id, name, importance);
				builder.setChannelId(channel_id);

				notificationManager.createNotificationChannel(channel);

			}

			// mId allows you to update the notification later on.
			notificationManager.notify(notificationId, builder.build());

		} else {

			// ничего не делаем

		}

	}

	public static String getDefaultLauncherPkgName(Context context)
	{
		final PackageManager pm = context.getPackageManager();

		ResolveInfo info = pm.resolveActivity(getStartHomeIntent(), PackageManager.MATCH_DEFAULT_ONLY);

		if (info != null)
			return info.activityInfo.packageName;

		return null;
	}

	public static int getFileStart(byte[] data, int offset, int size)
	{
		int r = findByte(data, offset, (byte) '\r');
		if (r >= 0)
		{
			while (true)
			{
				if (r > data.length - 4)
					break;

				if (data[r + 1] == '\n' && data[r + 2] == '\r' && data[r + 3] == '\n')
					return (r + 4);

				r = findByte(data, r + 1, (byte) '\r');
				if (r < 0 || r > offset + size)
					return -1;
			}
		}

		return -1;
	}

	private static int findByte(byte[] data, int offset, byte value)
	{
		for (int i = offset; i < data.length; i++)
		{
			if (data[i] == value)
				return i;
		}

		return -1;
	}

	private static int findByte(byte[] data, byte value)
	{
		return findByte(data, 0, value);
	}

	public static boolean isEndOfChuncked(byte[] data, int offset, int size)
	{
		return (data[offset + size - 5] == '0' &&
				data[offset + size - 4] == '\r' && data[offset + size - 3] == '\n' &&
				data[offset + size - 2] == '\r' && data[offset + size - 1] == '\n');
	}

	public static long fileSize(String path)
	{
		File f = new File(path);
		return f.length();
	}

	public static String formatDateTime(long time, boolean getDate, boolean getTime, boolean getSeconds)
	{
		Calendar cal = GregorianCalendar.getInstance();
		cal.setTimeInMillis(time);
		StringBuilder sb = new StringBuilder(30);

		if (getDate)
		{
			int day = cal.get(Calendar.DAY_OF_MONTH);
			int month = cal.get(Calendar.MONTH) + 1;
			int year = cal.get(Calendar.YEAR);

			if (day < 10)
				sb.append('0');
			sb.append(day).append('.');
			if (month < 10)
				sb.append('0');
			sb.append(month).append('.');
			if (year < 10)
				sb.append('0');
			sb.append(year);

			if (getTime)
				sb.append(' ');
		}

		if (getTime)
		{
			int hour = cal.get(Calendar.HOUR_OF_DAY);
			int minute = cal.get(Calendar.MINUTE);
			int second = cal.get(Calendar.SECOND);

			if (hour < 10)
				sb.append('0');
			sb.append(hour).append(':');
			if (minute < 10)
				sb.append('0');
			sb.append(minute);

			if (getSeconds)
			{
				sb.append(':');
				if (second < 10)
					sb.append('0');
				sb.append(second);
			}
		}

		return sb.toString();
	}

/*
	public static String getDomain(String url)
	{
		if (url == null)
			return null;

		int schemePos = LibNative.asciiIndexOf("://", url);

		String domain = "";

		if (schemePos > 0)
		{
			int slashPos = url.indexOf('/', schemePos + 3);
			if (slashPos > 0)
				domain = url.substring(schemePos + 3, slashPos);
			else if (url.length() > schemePos + 3)
				domain = url.substring(schemePos + 3);
		}

		if (domain.indexOf(':') > 0)
			domain = domain.substring(0, domain.indexOf(':'));

		return domain;
	}
*/

	public static String getDomain(String url)
	{
		if (url == null)
			return null;

		final int len = url.length();

		int schemePos = LibNative.asciiIndexOf("://", url);
		if (schemePos > 0 && len > schemePos + 3)
			url = url.substring(schemePos + 3);

		int s = url.indexOf('/');
		int p = url.indexOf(':');
		int q = url.indexOf('?');
		if ((p >= 0 && s >= 0 && p < s) || s < 0) s = p;
		if ((q >= 0 && s >= 0 && q < s) || s < 0) s = q;

		if (s >= 0) return url.substring(0, s); // TODO XXX may be check lenght also?
		else return url;
	}

	public static String ipToString(byte[] ip, int port)
	{
		/*
		if (ip == null || ip.length != 4)
			return "null";

		Object[] buf = new Object[4];
		buf[0] = ip[0] & 0xFF;
		buf[1] = ip[1] & 0xFF;
		buf[2] = ip[2] & 0xFF;
		buf[3] = ip[3] & 0xFF;

		if (port != 0)
			return String.format("%d.%d.%d.%d", buf) + ":" + (port & (-1L >>> 32));
		else
			return String.format("%d.%d.%d.%d", buf);
		*/
		return LibNative.ipToString(ip, port);
	}

	public static int ipToInt(byte[] ip)
	{
		if (ip == null || ip.length != 4)
			return -1;

		return ((ip[0] & 0xFF) | ((ip[1] & 0xFF) << 8) |
				((ip[2] & 0xFF) << 16) | ((ip[3] & 0xFF) << 24));
	}

	public static byte[] intToIp(int i)
	{
		byte[] buf = new byte[4];

		buf[0] = (byte) (i & 0xFF);
		buf[1] = (byte) ((i >> 8) & 0xFF);
		buf[2] = (byte) ((i >> 16) & 0xFF);
		buf[3] = (byte) ((i >> 24) & 0xFF);

		return buf;
	}

	public static boolean ip4Cmp(byte[] ip1, byte[] ip2)
	{
		if (ip1 == null || ip1.length != 4 || ip2 == null || ip2.length != 4)
			return false;

		if (ip1[0] == ip2[0] && ip1[1] == ip2[1] && ip1[2] == ip2[2] && ip1[3] == ip2[3])
			return true;

		return false;
	}

	public static String concatStrings(String[] strings)
	{
		String res = null;

		if (strings != null)
		{
			StringBuilder sb = new StringBuilder();

			if (strings.length > 0 && strings[0] != null)
				sb.append(strings[0].trim());

			for (int i = 1; i < strings.length; i++)
			{
				if (strings[i] != null)
					sb.append(',').append(strings[i].trim());
			}

			res = sb.toString();
		}

		return res;
	}

	public static boolean isNumber(String s)
	{
		if (s == null)
			return false;

		char ch;

		for (int i = 0; i < s.length(); ++i)
		{
			ch = s.charAt(i);
			if (ch < '0' || ch > '9')
				return false;
		}

		return true;
	}

	// TODO XXX move in native
	public static String getMainDomain(String domain)
	{
		if (LibNative.asciiStartsWith("www.", domain) && domain.length() > 4)
			domain = domain.substring(4);

		if (!isIp(domain))
		{
			String[] parts = domain.split("\\.");
			if (parts.length >= 2)
				return parts[parts.length - 2] + "." + parts[parts.length - 1];
		}

		return domain;
	}

	public static String getThirdLevelDomain(String domain)
	{
		if (LibNative.asciiStartsWith("www.", domain) && domain.length() > 4)
			domain = domain.substring(4);

		if (!isIp(domain))
		{
			String[] parts = domain.split("\\.");
			if (parts.length >= 2)
			{
				if (parts.length == 2)
					return parts[parts.length - 2] + "." + parts[parts.length - 1];
				else if (parts.length >= 3)
					return parts[parts.length - 3] + "." + parts[parts.length - 2] + "." + parts[parts.length - 1];
			}
		}

		return domain;
	}

	public static boolean isIp(String domain)
	{
		if (domain == null) return false;

		for (int i = 0; i < domain.length(); i++)
		{
			char c = domain.charAt(i);
			if (c != '.' && (c < '0' || c > '9'))
				return false;
		}

		return true;
	}

	public static boolean isHttpRequestLine(String s)
	{
		return (s != null && (LibNative.asciiStartsWith("GET ", s) || LibNative.asciiStartsWith("POST ", s) ||
				LibNative.asciiStartsWith("HEAD ", s) || LibNative.asciiStartsWith("PUT ", s) ||
				LibNative.asciiStartsWith("DELETE ", s) || LibNative.asciiStartsWith("OPTIONS ", s) ||
				LibNative.asciiStartsWith("TRACE ", s)));
	}

	public static byte[] concatArrays(byte[] a1, byte[] a2)
	{
		if (a1 == null || a2 == null)
			throw new NullPointerException();

		byte[] res = new byte[a1.length + a2.length];

		System.arraycopy(a1, 0, res, 0, a1.length);
		System.arraycopy(a2, 0, res, a1.length, a2.length);

		return res;
	}

	public static int indexOf(byte[] array, byte[] target, int start, int maxpos)
	{
		if (target.length == 0 || array.length < target.length || array.length < start) // TODO XXX
			return -1;

		maxpos = Math.min(maxpos, array.length - target.length + 1);

	indexOf_outer:
		for (int i = start; i < maxpos; i++)
		{
			for (int j = 0; j < target.length; j++)
			{
				if (array[i + j] != target[j])
					continue indexOf_outer;
			}

			return i;
		}

		return -1;
	}

	public static String[] getPackagesForUid(int uid)
	{
		Context context = App.getContext();

		PackageManager pm = context.getPackageManager();
		return pm.getPackagesForUid(uid);
	}

	public static byte[] deflate(byte[] data)
	{
		if (data == null)
			return null;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_COMPRESSION, true));

		try
		{
			dos.write(data);
			dos.finish();
			dos.flush();
			baos.flush();

			return baos.toByteArray();
		}
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			try { dos.close(); } catch (IOException e) { e.printStackTrace(); }
		}

		return null;
	}

	// TODO not used
/*
	public static boolean sendFile(String addr, byte[] data)
	{
		boolean res = false;
		HttpURLConnection con = null;
		OutputStream out = null;

		try
		{
			URL url = new URL(addr);
			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setDoOutput(true);
			con.setFixedLengthStreamingMode(data.length);
			con.connect();

			out = new BufferedOutputStream(con.getOutputStream());
			out.write(data);
			out.flush();
			out.close();
			out = null;

			if (con.getResponseCode() == 200)
				res = true;
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (out != null)
				try { out.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		return res;
	}
*/

	public static PublicKey getPublicKeyFromAsset(String fileName)
	{
		try
		{
			// Loading certificate file
			InputStream inStream = App.getContext().getAssets().open(fileName);
			byte[] key = new byte[1024]; // TODO XXX
			int size = 0;

			while (true)
			{
				int result = inStream.read(key, size, key.length - size);
				if (result == -1)
					break;
				size += result;
			}
			inStream.close();
			if (size == 0)
				return null;

			byte[] decodedKey = Base64.decode((new String(key, 0, 0, size)).replaceAll("\\s", ""), Base64.DEFAULT);
			X509EncodedKeySpec x509 = new X509EncodedKeySpec(decodedKey);

			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePublic(x509);

			// Read the public key from certificate file
			//pubKey = (RSAPublicKey) cert.getPublicKey();
			//L.d(Settings.TAG_UTILS, "Public Key Algorithm = " + cert.getPublicKey().getAlgorithm() + "\n");
		}

		catch (IOException e) { e.printStackTrace(); }
		catch (NoSuchAlgorithmException e) { e.printStackTrace(); }
		catch (InvalidKeySpecException e) { e.printStackTrace(); }

		return null;
	}

	public static String toHex(byte[] data)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < data.length; i++)
		{
			int b = data[i] & 0xff;
			String str = Integer.toHexString(b);
			if (str.length() < 2)
				sb.append('0');
			sb.append(str);
		}

		return sb.toString();
	}

	public static void saveFile(InputStream is, String path) throws IOException
	{
		byte[] buf = new byte[4096];
		FileOutputStream fos = null;

		try
		{
			fos = new FileOutputStream(path);
			int read;
			while ((read = is.read(buf)) > 0)
				fos.write(buf, 0, read);
		}
		finally
		{
			if (fos != null)
				try { fos.close(); } catch (IOException e) { e.printStackTrace(); }
		}
	}

	public static void saveFile(byte[] data, String path) throws IOException
	{
		FileOutputStream fos = null;

		try
		{
			fos = new FileOutputStream(path);
			fos.write(data, 0, data.length);
		}
		finally
		{
			if (fos != null)
				try { fos.close(); } catch (IOException e) { e.printStackTrace(); }
		}
	}

	public static boolean copyFile(String src, String dst)
	{
		try
		{
			FileInputStream fis = new FileInputStream(src);
			FileOutputStream fos = new FileOutputStream(dst);
			int read;
			byte[] buf = new byte[4096];

			while ((read = fis.read(buf)) > 0)
				fos.write(buf, 0, read);

			fis.close();
			fos.flush();
			fos.close();

			return true;
		}
		catch (IOException e) { e.printStackTrace(); }

		return false;
	}

	public static String getNameOnly(String fileNameWithExt)
	{
		if (fileNameWithExt == null)
			return null;

		final int pos = fileNameWithExt.indexOf('.');
		if (pos <= 0)
			return fileNameWithExt;

		return fileNameWithExt.substring(0, pos);
	}

	public static byte[] getFileContentsRaw(String fileName) throws IOException
	{
		FileInputStream fis = null;
		ByteArrayOutputStream baos = null;

		try
		{
			fis = new FileInputStream(fileName);
			baos = new ByteArrayOutputStream();
			byte[] buf = new byte[2048];

			int read;
			while ((read = fis.read(buf)) > 0)
				baos.write(buf, 0, read);
		}
		catch (FileNotFoundException e)
		{
			L.w(Settings.TAG_UTILS, "File not found: ", fileName);
			return null;
		}

		finally
		{
			if (fis != null)
				try { fis.close(); } catch (IOException e) { e.printStackTrace(); }
			if (baos != null)
				try { baos.close(); } catch (IOException e) { e.printStackTrace(); }
		}

		if (baos == null)
			return null;

		return baos.toByteArray();
	}

	public static String getFileContents(String fileName) throws IOException
	{
		final byte[] content = getFileContentsRaw(fileName);
		if (content == null)
			return null;

		return (new String(content)); // TODO XXX may be use String(content, 0, 0, len)?
	}

	public static void startMemoryDrain(final int size, final long pause)
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Random r = new Random(System.currentTimeMillis());
				Vector<byte[]> v = new Vector<byte[]>();

				while (true)
				{
					try
					{
						v.add(new byte[size]);
					}
					catch (OutOfMemoryError e)
					{
						L.d(Settings.TAG_UTILS, "Memory full!");
						break;
					}

					try { Thread.sleep(pause); } catch (InterruptedException e) { e.printStackTrace(); }

					if (r.nextInt(50) < 5)
						v.remove(0);
				}

				sleep(300000);
			}
		}).start();
	}

	public static String getAssetAsString(Context context, String fileName)
	{
		try
		{
			InputStream inStream = context.getAssets().open(fileName);
			ByteArrayOutputStream baos = new ByteArrayOutputStream(150);

			int read;
			byte[] buf = new byte[4096];
			while ((read = inStream.read(buf)) > 0)
				baos.write(buf, 0, read);

			inStream.close();

			return (new String(baos.toByteArray()));
		}
		catch (IOException e) { e.printStackTrace(); }

		return null;
	}

	public static String getLocalizedAssetAsString(Context context, String resName)
	{
		final String lang = Locale.getDefault().getLanguage();

		String res = getAssetAsString(context, resName + "/" + lang + ".txt");
		if (res == null)
			res = getAssetAsString(context, resName + "/en.txt");

		return res;
	}

	public static void startMarket(Context context, String packageName)
	{
		try
		{
			String url = "market://details?id=" + packageName;
			context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		}
		catch (ActivityNotFoundException anfe)
		{
			String url = "https://play.google.com/store/apps/details?id=" + packageName;
			context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		}
	}

	public static boolean canConnect(String host, int port)
	{
		try
		{
			Socket s = new Socket(host, port);
			s.close();

			return true;
		}
		catch (IOException e) { /*e.printStackTrace();*/ }

		return false;
	}

	public static boolean hasPermissions(Context c, String[] permissions, String packageName)
	{
		PackageManager pm = c.getPackageManager();

		for (int i = 0; i < permissions.length; i++)
		{
			if (pm.checkPermission(permissions[i], packageName) == PackageManager.PERMISSION_DENIED)
				return false;
		}

		return true;
	}

	/*
	 * return false if dialog.show don't called (activity finished)
	 * TODO we can use SYSTEM_ALERT_WINDOW dialog in some cases:
	 *	  http://stackoverflow.com/questions/2634991/android-1-6-android-view-windowmanagerbadtokenexception-unable-to-add-window
	 */
	public static boolean dialogShow(Activity activity, Dialog dialog)
	{
		if (activity != null && activity.isFinishing())
			return false; // dialog or activity canceled

		// TODO XXX workaround for
		// android.view.WindowManager$BadTokenException: Unable to add window - token android.os.BinderProxy@42e93628 is not valid; is your activity running?
		try { dialog.show(); }
		catch (BadTokenException e) { e.printStackTrace(); }

		return true;
	}

	// return false if dialog.dismiss(cancel) don't called (dialog not shown or activity finished)
	public static boolean dialogClose(Activity activity, Dialog dialog, boolean canceled)
	{
		if (!dialog.isShowing() || (activity != null && activity.isFinishing()))
			return false; // dialog or activity canceled

		// TODO XXX workaround for
		// java.lang.IllegalArgumentException: View=com.android.internal.policy.impl.PhoneWindow$DecorView{} not attached to window manager
		try { if (canceled) dialog.cancel(); else dialog.dismiss(); }
		catch (IllegalArgumentException e) { e.printStackTrace(); }

		return true;
	}

	public static boolean runOnUiThread(Activity activity, Runnable action)
	{
		if (activity == null)
			return false;

		activity.runOnUiThread(action);
		return true;
	}

	public static void sleep(long ms)
	{
		try { Thread.sleep(ms); }
		catch (InterruptedException e) { e.printStackTrace(); }
	}

	// TODO used only for debug.db
	public static byte[] getData(String addr)
	{
		byte[] res = null;
		HttpURLConnection con = null;
		BufferedInputStream in = null;

		try
		{
			// see Utils.postData (TODO XXX merge all code)
			URL url = new URL(addr);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url);

			con.setRequestMethod("GET");
			con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");

			try { con.connect(); }
			catch (SecurityException ex) { return res; }

			if (con.getResponseCode() == 200)
			{
				int contentLength = con.getContentLength();
				//String transferEncoding = con.getHeaderField("Transfer-Encoding");
				//boolean canRead = (contentLength > 0 || (transferEncoding != null && transferEncoding.trim().equals("chunked")));
				//if (Settings.DEBUG) L.e(Settings.TAG_UTILS, "postData ", "Content-Length: ", Integer.toString(contentLength));

				try { in = new BufferedInputStream(con.getInputStream()); } catch (IOException e) { }

				if (in != null)
				{
					ByteArrayOutputStream baos = new ByteArrayOutputStream((contentLength > 0) ? contentLength : 4096);
					byte[] buf = new byte[4096];
					int read;
					while ((read = in.read(buf)) != -1)
						baos.write(buf, 0, read);

					res = baos.toByteArray();
				}
			}
			else
			{
				res = new byte[0];
				if (Settings.DEBUG) L.e(Settings.TAG_UTILS, "getData ", "ResponseCode: ", Integer.toString(con.getResponseCode()));
			}
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (in != null)
				try { in.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		return res;
	}

	// TODO used only by inAppBilling
	public static UtilsHttpResult postData(String addr, byte[] data)
	{
		return postData(addr, data, 15000); // TODO XXX 15 sec, maybe low?
	}

	public static UtilsHttpResult postData(String addr, byte[] data, int timeout)
	{
		UtilsHttpResult res = new UtilsHttpResult();
		HttpURLConnection con = null;
		OutputStream out = null;
		BufferedInputStream in = null;

		try
		{
			URL url = new URL(addr);

			// resolve ip manually because DNS requests by HTTP class
			// can be unresolved during vpn start/restart
			String host = url.getHost();
			String ip = null;
			if (host != null)
				ip = NetUtil.lookupIp(host, 3);

			//con = (HttpURLConnection) url.openConnection();
			final OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
			con = factory.open(url, ip); // use patched OkHttp and OkIo to set server ip address

			con.setRequestMethod("POST");
			con.setDoInput(true);
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Accept-Encoding", "");
			//System.setProperty("http.keepAlive", "false");
			if (timeout > 0)
			{
				con.setConnectTimeout(timeout);
				con.setReadTimeout(timeout);
			}

			if (data != null)
			{
				con.setDoOutput(true);
				con.setRequestProperty("Content-Length", Integer.toString(data.length));
				//con.setFixedLengthStreamingMode(data.length);
				//con.setRequestProperty("Content-Type", "application/octet-stream");
				//con.setRequestProperty("Content-Type", "application/stat_data");
				//con.setRequestProperty("Content-Type", "text/plain");
				//con.setRequestProperty("Content-Type", "multipart/form-data");
				con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			}

			// see UpdaterService.sendData
			try { con.connect(); }
			catch (SecurityException ex) { return res; }

			if (data != null)
			{
				out = new BufferedOutputStream(con.getOutputStream());
				out.write(data);
				out.flush();
				out.close();
				out = null;
			}

			int code = con.getResponseCode();
			byte[] responseData = null;

			//if (code == 200)
			{
				int contentLength = con.getContentLength();
				//String transferEncoding = con.getHeaderField("Transfer-Encoding");
				//boolean canRead = (contentLength > 0 || (transferEncoding != null && transferEncoding.trim().equals("chunked")));
				//if (Settings.DEBUG) L.e(Settings.TAG_UTILS, "postData ", "Content-Length: ", Integer.toString(contentLength));

				try { in = new BufferedInputStream(con.getInputStream()); } catch (IOException e) { }
				if (in == null)
					in = new BufferedInputStream(con.getErrorStream()); // some HTTP codes treated as error

				// TODO XXX if no any data???

				ByteArrayOutputStream baos = new ByteArrayOutputStream((contentLength > 0) ? contentLength : 128);
				byte[] buf = new byte[4096];
				int read;
				while ((read = in.read(buf)) != -1)
					baos.write(buf, 0, read);

				responseData = baos.toByteArray();
			}
			//else
			//{
			//	  //rdata = new byte[0];
			//	  if (Settings.DEBUG) L.e(Settings.TAG_UTILS, "postData ", "ResponseCode: ", Integer.toString(con.getResponseCode()));
			//}

			res = new UtilsHttpResult(code, responseData);
		}
		catch (MalformedURLException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (out != null)
				try { out.close(); } catch (IOException e) { e.printStackTrace(); }
			if (in != null)
				try { in.close(); } catch (IOException e) { e.printStackTrace(); }

			if (con != null)
				con.disconnect();
		}

		return res;
	}

	// return path where we can create file: sdcard, tmp, app data dir
	public static String getWritablePath(String filename)
	{
		String filePath = null;

		File sdcard = Environment.getExternalStorageDirectory();
		File f = new File(sdcard.getPath() + "/" + filename);
		File tmp = new File("/data/local/tmp");
		File ft = new File("/data/local/tmp/" + filename); // adb shell chmod 0777 /data/local/tmp/

		if ((sdcard.isDirectory() && sdcard.canWrite()) || f.canWrite())
			// can write to sdcard
			filePath = f.getAbsolutePath();
		else if ((tmp.isDirectory() && tmp.canWrite()) || ft.canWrite())
			// can write to tmp
			// adb shell chmod 0777 /data/local/tmp/
			filePath = ft.getAbsolutePath();
		else
			// adb backup -f backup.ab -noapk app.webguard
			// ( printf "\x1f\x8b\x08\x00\x00\x00\x00\x00" ; tail -c +25 backup.ab ) | tar xfvz -
			filePath = App.getContext().getFilesDir().getAbsolutePath() + "/" + filename;

		return filePath;
	}

	public static int unsignedByte(byte b)
	{
		return (b & 0xFF);
	}

	// android docs tells that we can set priority < 0 manually, but we can =)
	public static void maximizeThreadPriority()
	{
		//final int priorityNew = Process.THREAD_PRIORITY_DEFAULT; // 0
		//final int priorityNew = Process.THREAD_PRIORITY_FOREGROUND; // -2
		final int priorityNew = Process.THREAD_PRIORITY_DISPLAY; // -4
		//final int priorityNew = Process.THREAD_PRIORITY_AUDIO; // -16, too brutal

		try
		{
			int tid = Process.myTid();
			int priorityOld = Process.getThreadPriority(tid);
			if (priorityOld > priorityNew) // lower is better
			{
				Process.setThreadPriority(priorityNew);
				//L.a(Settings.TAG_UTILS, "PRIORITY ", "new max " + Process.getThreadPriority(tid));
			}
		}
		catch (Exception e) {}
	}

	public static void minimizeThreadPriority()
	{
		final int priorityNew = Process.THREAD_PRIORITY_BACKGROUND; // 10
		//final int priorityNew = Process.THREAD_PRIORITY_LOWEST; // 19, too brutal

		try
		{
			int tid = Process.myTid();
			int priorityOld = Process.getThreadPriority(tid);
			if (priorityOld < priorityNew) // lower is better
			{
				Process.setThreadPriority(priorityNew);
				//L.a(Settings.TAG_UTILS, "PRIORITY ", "new min " + Process.getThreadPriority(tid));
			}
		}
		catch (Exception e) {}
	}

	/*
	 * send intent to start VpnDialogs.ManageDialog (for android 5.0)
	 * TODO XXX this didn't work if VPN was not active
	 */
	public static boolean showVpnDialogsManage(Activity activity)
	{
		Intent intent = new Intent();

		//intent.setClassName("com.android.vpndialogs", "com.android.vpndialogs.ConfirmDialog"); // android < 5
		//ComponentName componentName =
		//	  ComponentName.unflattenFromString("com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog");
		//intent.setClassName(componentName.getPackageName(), componentName.getClassName());
		//startActivityForResult(intent, VPN_REQUEST_CODE);

		ComponentName componentName =
			ComponentName.unflattenFromString("com.android.vpndialogs/com.android.vpndialogs.ManageDialog");
		intent.setClassName(componentName.getPackageName(), componentName.getClassName());
		try
		{
			activity.startActivity(intent);
		}
		catch (ActivityNotFoundException ex)
		{
			return false;
		}

		return true;
	}

	/*
	 * check if have android 5.0 (LOLLIPOP) bug with incorrect VPN UID and package name check
	 *
	 * https://code.google.com/p/android-developer-preview/issues/detail?id=1900
	 */
	public static boolean isHaveAndroidBug1900()
	{
		if (Build.VERSION.SDK_INT < 21)
			return false;

		Object iConnectivityService = null; // ConnectivityService and methods to check android 5.0 vpn bug
		Method prepareVpnMethod = null;
		Method establishVpnMethod = null;

		// get ConnectivityService methods
		if (iConnectivityService == null || prepareVpnMethod == null || establishVpnMethod == null)
		{
			try
			{
				Context context = App.getContext();

				final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				final Class conmanClass = Class.forName(conman.getClass().getName());
				final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
				iConnectivityManagerField.setAccessible(true);

				if (iConnectivityService == null)
					iConnectivityService = iConnectivityManagerField.get(conman);
				final Class iConnectivityServiceClass = Class.forName(iConnectivityService.getClass().getName());

				if (prepareVpnMethod == null)
				{
					prepareVpnMethod = iConnectivityServiceClass.getDeclaredMethod("prepareVpn", String.class, String.class);
					prepareVpnMethod.setAccessible(true);
				}

				if (establishVpnMethod == null)
				{
					final Class vpnconfigClass = Class.forName("com.android.internal.net.VpnConfig");
					establishVpnMethod = iConnectivityServiceClass.getDeclaredMethod("establishVpn", vpnconfigClass);
					establishVpnMethod.setAccessible(true);
				}
			}
			catch (Exception e)
			{
				return false;
			}
		}

		// check prepareVpn and establishVpn return values (all OK if methods generate exceptions)
		// see:
		// http://tools.oesf.biz/android-5.0.0_r2.0/xref/frameworks/base/packages/VpnDialogs/src/com/android/vpndialogs/ConfirmDialog.java#60
		// http://tools.oesf.biz/android-5.0.0_r2.0/xref/frameworks/base/services/core/java/com/android/server/ConnectivityService.java#2750
		try
		{
			String pkgName = App.packageName();
			Object prepareResult = prepareVpnMethod.invoke(iConnectivityService, pkgName, null);
			if (Boolean.FALSE.equals(prepareResult))
				return false; // ok, we have no rights

			Object establishResult = establishVpnMethod.invoke(iConnectivityService, new Object[]{null});
			if (establishResult == null)
				return true; // we have rights but establish return null
		}
		catch (Exception e) { e.printStackTrace(); }

		return false;
	}

	public static void activityReload(Activity activity)
	{
		Intent intent = activity.getIntent();

		activity.overridePendingTransition(0, 0);
		intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		activity.finish();
		activity.overridePendingTransition(0, 0);

		//Context context = activity.getApplicationContext();
		activity.startActivity(intent);
	}

//	public static void langChange(String lang)
//	{
//		if (lang == null || lang.equalsIgnoreCase(""))
//			return;
//
//		Locale locale = new Locale(lang);
//		Locale.setDefault(locale);
//		Configuration config = new Configuration();
//		config.locale = locale;
//
//		//getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
//		//getApplicationContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
//		//UserDetail.this.getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
//
//		Context context = App.getContext();
//		context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
//	}

//	public static String langGet()
//	{
//		String sysLang = null;
//
//		Context context = App.getContext();
//		try { sysLang = context.getResources().getConfiguration().locale.getLanguage(); } catch (Exception e) { }
//
//		return sysLang;
//	}

	/*
	 * clear all applications cache (return false on error)
	 *
	 * http://stackoverflow.com/questions/17313721/how-to-delete-other-applications-cache-from-our-android-app
	 * http://stackoverflow.com/questions/14507092/android-clear-cache-of-all-apps
	 * https://phonesecurity.googlecode.com/svn/trunk/FastAppMgr/src/com/herry/fastappmgr/view/CacheAppsListActivity.java
	 *
	 * may not clean all apps:
	 * W/PackageManager(  730): Couldn't clear application caches
	 */
	//public static boolean clearCaches(IPackageDataObserver clearCacheObserver)
	public static boolean clearCaches()
	{
		try
		{
			Context context = App.getContext();
			PackageManager	pm = context.getPackageManager();
			Method[] methods = pm.getClass().getDeclaredMethods();

			for (Method m : methods)
			{
				if (!m.getName().equals("freeStorage"))
					continue;

				try
				{
					long desiredFreeStorage = Long.MAX_VALUE; // request max of free space
					m.invoke(pm, desiredFreeStorage , null);

					return true;
				}
				catch (Exception e) { /* method invocation failed (permission?)*/ }

				break;
			}
		}
		catch (Exception e) { }

		return false;
	}

	/*
	 * kills all packages running in background (return false on error)
	 *
	 * http://stackoverflow.com/questions/7397668/how-to-kill-all-running-applications-in-android
	 *
	 * TODO XXX may be slow? test with 100 applications!
	 */
	public static boolean killBackgroundApps(final String pkgExcept)
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					Context context = App.getContext();
					PackageManager pm = context.getPackageManager();
					List<ApplicationInfo> packages = pm.getInstalledApplications(0); // get a minimal list of installed apps

					ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

					for (ApplicationInfo packageInfo : packages)
					{
						//if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1)
						//	  continue;

						// skip packages without '.' in name or excepted
						final String pkgName = packageInfo.packageName;
						if (pkgName != null && (pkgName.indexOf('.') < 0 || pkgName.equals(pkgExcept)))
							continue;

						mActivityManager.killBackgroundProcesses(packageInfo.packageName);
						//L.a(Settings.TAG_UTILS, "kill '" + packageInfo.packageName + "'");
					}
				}
				catch (Exception e) { }
			}
		});
		t.setName("killApps");
		t.start();

		return true;
	}

	// 100 b -> 1 KB, 102400 b -> 100 KB, 1048576 b -> 1 MB, 1049600 b -> 1.1 MB
	public static String byteCountToHuman(long bytes, Context context)
	{
		int unit = 1024;
		double count;
		String pre;

		if (bytes < 1000)
		{

			count = (bytes == 0) ? 0 : 1; //pre = (si) ? "kiB" : "KB";
			pre = "KB";
		}
		else
		{
			int exp = (int) (Math.log(bytes) / Math.log(unit));
			count = bytes / Math.pow(unit, exp);
			if ((int) count > 999) // temporary fix =)
				count = bytes / Math.pow(unit, ++exp);

			try { pre = "KMGTPE".charAt(exp - 1) + "B"; }
			catch (IndexOutOfBoundsException e) { pre = ""; }
		}

		if (context != null)
		{
			if ("KB".equals(pre))
				pre = context.getString(Res.string.kb); // TODO move getString from here
			else if ("MB".equals(pre))
				pre = context.getString(Res.string.mb);
			else if ("GB".equals(pre))
				pre = context.getString(Res.string.gb);
			else if ("TB".equals(pre))
				pre = context.getString(Res.string.tb);
		}

		String result;
		if (count % 1 < 0.1)
			result = String.format("%d %s", (long) count, pre);
		else
			result = String.format("%.1f %s", count, pre);

		return result;
	}
/*
	//
	public static boolean test()
	{
		Object iConnectivityService = null; // ConnectivityService and methods to check android 5.0 vpn bug
		Method method = null;
		//Method establishVpnMethod = null;

		// get ConnectivityService methods
		if (iConnectivityService == null || method == null)
		{
			try
			{
				Context context = App.getContext();

				final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				final Class conmanClass = Class.forName(conman.getClass().getName());
				final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
				iConnectivityManagerField.setAccessible(true);

				if (iConnectivityService == null)
					iConnectivityService = iConnectivityManagerField.get(conman);
				final Class iConnectivityServiceClass = Class.forName(iConnectivityService.getClass().getName());

				if (method == null)
				{
					//method = iConnectivityServiceClass.getDeclaredMethod("untether", String.class);
					method = iConnectivityServiceClass.getDeclaredMethod("getTetherableIfaces");
					method.setAccessible(true);
				}

				//if (establishVpnMethod == null)
				//{
				//	final Class vpnconfigClass = Class.forName("com.android.internal.net.VpnConfig");
				//	establishVpnMethod = iConnectivityServiceClass.getDeclaredMethod("establishVpn", vpnconfigClass);
				//	establishVpnMethod.setAccessible(true);
				//}
			}
			catch (Exception e)
			{
				return false;
			}
		}

		try
		{
			Object result = method.invoke(iConnectivityService);
			//int r = (Integer) result;
			String[] r = (String[]) result;
			if (r != null)
				return true;
			//if (Boolean.FALSE.equals(prepareResult))
			//	return false; // ok, we have no rights

			//Object establishResult = establishVpnMethod.invoke(iConnectivityService, new Object[]{null});
			//if (establishResult == null)
			//	return true; // we have rights but establish return null
		}
		catch (Exception e) { e.printStackTrace(); }

		return false;
	}
*/
}
