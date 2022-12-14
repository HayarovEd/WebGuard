package app.ui;

import android.app.Activity;
import app.common.Utils;

public class ActivityKillerThread implements Runnable
{
	private volatile Activity activity = null;
	private long timeToKill = 0;
	private long timeToWait = 0;

	private Thread thread = null;
	private volatile boolean stopped = false;

	// start thread to kill activity after specified time (ms)
	public ActivityKillerThread(Activity activity, long ms)
	{
		this.activity = activity;
		long time = System.currentTimeMillis();
		timeToKill = time + ms;

		if (timeToKill > time)
		{
			timeToWait = (ms > 1000) ? ms : 1000;
			thread = new Thread(this);
			thread.start();
		}
	}

	@Override
	public void run()
	{
		boolean first_wait = true;

		while (!stopped)
		{
			Utils.sleep(timeToWait);

			if (first_wait)
			{
				first_wait = false;
				timeToWait = 1000;
			}

			if (System.currentTimeMillis() >= timeToKill)
			{
				activity.runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						//if (BuyDialogActivity.running != null)
						//	  BuyDialogActivity.running.finish(); // TODO XXX very bad code
						if (activity instanceof OptionsActivity)
							((OptionsActivity) activity).finishAll();
						else
							activity.finish();

						activity = null;
					}
				});
				stopped = true;
			}
		}
	}

	public void stop()
	{
		stopped = true;
		if (thread != null)
			thread.interrupt();
	}
}
