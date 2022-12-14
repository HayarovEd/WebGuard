package app.common.memdata;

import app.info.Statistics;
import app.common.debug.L;
import app.common.Utils;
import app.internal.Settings;

import java.util.ArrayDeque;
import java.nio.ByteBuffer;

public class ByteBufferPool
{
	private static final int CAPACITY = 64;

	public static final int BUFFER_SIZE_SMALL = 16384;
	public static final int BUFFER_SIZE_BIG = 49152;

	private static final ArrayDeque<ByteBuffer> pool = new ArrayDeque<ByteBuffer>(CAPACITY);
	private static final ArrayDeque<ByteBuffer> pool2 = new ArrayDeque<ByteBuffer>(CAPACITY);

	ByteBufferPool()
	{
		super();
	}

	/*
	public static ByteBuffer alloc()
	{
		synchronized (pool)
		{
			ByteBuffer buf = pool.pollFirst();
			if (buf == null)
				return ByteBuffer.allocate(32768);
			return buf;
		}
	}
	*/

	public static ByteBuffer alloc(int capacity)
	{
		try
		{
			ByteBuffer buf;
			if (capacity < BUFFER_SIZE_BIG)
			{
				synchronized (pool2)
				{
					buf = pool2.pollFirst();
				}
			}
			else
			{
				synchronized (pool)
				{
					buf = pool.pollFirst();
				}
			}

			if (buf == null)
				buf = ByteBuffer.allocate(capacity);

			return buf;
		}
		catch (OutOfMemoryError e)
		{
			e.printStackTrace();
			L.e(Settings.TAG_BYTEBUFFERPOOL, ": OutOfMemoryError in ByteBufPool");

			if (Settings.EVENTS_LOG)
				Statistics.addLog(Settings.LOG_BYTEBUFPOOL_OUTOFMEM_ERR +
									Utils.formatDateTime(System.currentTimeMillis(), true, true, true));
			System.gc();
			Thread.yield();

			return null;
		}
	}

	public static void release(ByteBuffer buf)
	{
		buf.clear();

		if (buf.capacity() < BUFFER_SIZE_BIG)
		{
			synchronized (pool2)
			{
				int size = pool2.size();
				if (size < CAPACITY)
					pool2.addLast(buf);
			}
		}
		else
		{
			synchronized (pool)
			{
				int size = pool.size();
				if (size < CAPACITY)
					pool.addLast(buf);
			}
		}
	}

	public static void clear()
	{
		synchronized (pool)
		{
			pool.clear();
		}

		synchronized (pool2)
		{
			pool2.clear();
		}
	}
}
