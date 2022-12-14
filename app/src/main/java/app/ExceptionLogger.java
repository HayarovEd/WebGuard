package app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import app.netfilter.proxy.Packet;
import app.netfilter.proxy.TCPClient;
import app.netfilter.proxy.UDPClient;
import app.common.LibNative;
import app.internal.Settings;

import java.io.FileInputStream;
import java.io.IOException;

// !!! don't use here any code from native libs

public final class ExceptionLogger implements Thread.UncaughtExceptionHandler
{
	private static Context mContext;
	private final Thread.UncaughtExceptionHandler mDefaultUeh;
	private static int width = 0, height = 0, density = 0;
	private static ExceptionLogger instance = null;
	private static byte[] buf = new byte[2048];

	public static ExceptionLogger get(Context context)
	{
		if (context != null && mContext == null)
			mContext = context;

		if (instance == null)
			instance = new ExceptionLogger(mContext);

		return instance;
	}

	private ExceptionLogger(final Context context)
	{
		mContext = context;
		mDefaultUeh = Thread.getDefaultUncaughtExceptionHandler();

//		  DisplayMetrics dm = new DisplayMetrics();
//		  ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
//		  width = dm.widthPixels;
//		  height = dm.heightPixels;
//		  density = dm.densityDpi;
	}

	public static void start()
	{
		start(App.getContext());
	}

	public static void start(Context context)
	{
		Thread.setDefaultUncaughtExceptionHandler(ExceptionLogger.get(context));
	}

	public static void disable()
	{
		if (instance != null)
			Thread.setDefaultUncaughtExceptionHandler(instance.mDefaultUeh);
	}

	/**
	 * Called when an uncaught exception occurs. This implementation simply sends an intent to our
	 * error reporting service and allows the application to force close. Hanging here by blocking
	 * on a network request or something similar is both annoying to the user and likely won't finish
	 * since when the user hits Force Close, the process is terminated regardless of whether or
	 * not this call finished its execution. By sending an intent to our reporting service, Android
	 * will restart our process so that our intent can be processed, allowing us to report the exception
	 * dependably and without being detrimental to the user's experience.
	 *
	 * @param thread The thread the uncaught exception was thrown on
	 * @param ex	 The uncaught exception
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex)
	{
		try
		{
			reportException(mContext, ex, Settings.TAG_EXCEPTIONLOGGER, true);
		}
		finally
		{
			if (ex instanceof OutOfMemoryError)
			{
				//FilterVpnService.startByAlarm(App.getContext(), 5000);

				//System.loadLibrary("native"); // libraries loaded on app create
				LibNative.exit(0); // with exit code 0 android will restart us =)))

				//System.exit(0);
			}
			else if (mDefaultUeh != null)
			{
				mDefaultUeh.uncaughtException(thread, ex);
			}
		}
	}

	/**
	 * Helper method that prepares the the intent to start the reporter service
	 *
	 * @param context Used to start the service
	 * @param ex	  The exception
	 * @param tag	  A tag to help you determine where the error occurred
	 * @param fatal   Whether or not the exception was fatal
	 */
	public static void reportException(final Context context, final Throwable ex, final String tag, final boolean fatal)
	{
		String stack = Log.getStackTraceString(ex);

		final Intent i = new Intent(LogService.INTENT);
		i.putExtra(LogService.EXTRA_STACKTRACE, stack);
		i.putExtra(LogService.EXTRA_FATAL, fatal);
		i.putExtra(LogService.EXTRA_TAG, tag);
//		  i.putExtra(LogService.EXTRA_WIDTH, width);
//		  i.putExtra(LogService.EXTRA_HEIGHT, height);
//		  i.putExtra(LogService.EXTRA_DENSITY, density);
		i.putExtra(LogService.EXTRA_MANUFACTURER, Build.MANUFACTURER);
		i.putExtra(LogService.EXTRA_MODEL, Build.MODEL);
		i.putExtra(LogService.EXTRA_BRAND, Build.BRAND);
		i.putExtra(LogService.EXTRA_RELEASE, Build.VERSION.RELEASE);
		i.putExtra(LogService.EXTRA_FINGERPRINT, Build.FINGERPRINT);
		i.putExtra(LogService.EXTRA_INSTALL_ID, App.getInstallId());
		if (ex instanceof OutOfMemoryError) {
			i.putExtra(LogService.EXTRA_MEMINFO, readProcMemInfo());
		}
		Runtime r = Runtime.getRuntime();
		i.putExtra(LogService.EXTRA_MEMTOTAL, r.totalMemory());
		i.putExtra(LogService.EXTRA_MEMFREE, r.freeMemory());
		i.putExtra(LogService.EXTRA_TCP_CLIENT_COUNT, TCPClient.getRefCount());
		i.putExtra(LogService.EXTRA_UDP_CLIENT_COUNT, UDPClient.getRefCount());
		i.putExtra(LogService.EXTRA_PACKETS_COUNT, Packet.getRefCount());

		LogService.startService(context, i); //context.startService(i);
	}

	private static String readProcMemInfo()
	{
		FileInputStream fis = null;
		try
		{
			fis = new FileInputStream("/proc/meminfo");
			int len = fis.read(buf);
			//fis.close();
			String res = new String(buf, 0, 0, len);

			return res;
		}
		catch (IOException e) { e.printStackTrace(); }

		finally
		{
			if (fis != null)
				try { fis.close(); } catch (IOException e) { e.printStackTrace(); }
		}

		return null;
	}
}
