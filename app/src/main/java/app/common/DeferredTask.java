package app.common;

//import app.netfilter.proxy.IClient;
import java.util.Comparator;

public class DeferredTask
{
	public static final Comparator comparator;
	long scheduleAbsTime;
	//IClient client;

	static
	{
		comparator = new TaskComparator();
	}

	public DeferredTask()
	{
		super();
	}

	static final class TaskComparator implements Comparator
	{
		TaskComparator()
		{
			super();
		}

		public int compare(DeferredTask task1, DeferredTask task2)
		{
			long delta;
			byte res;
			delta = task1.scheduleAbsTime - task2.scheduleAbsTime;

			if (delta <= 0)
			{
				res = (delta >= 0) ? (byte) 0 : (byte) -1;
			}
			else
			{
				res = (byte) 1;
			}

			return res;
		}

		public int compare(Object task1, Object task2)
		{
			return this.compare((DeferredTask) task1, (DeferredTask) task2);
		}
	}
}
