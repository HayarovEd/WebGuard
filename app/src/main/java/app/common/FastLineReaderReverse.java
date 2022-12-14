package app.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;


public class FastLineReaderReverse
{
	private static byte[] commonBuf1 = null;
	private static byte[] commonBuf2 = null;
	private byte[] buf = null;
	private int bytes = 0;
	private int readPos = 0;

	private static final Object lock = new Object();

	public FastLineReaderReverse(java.io.InputStream stream) throws IOException
	{
		buf = null;
		bytes = 0;
		readPos = 0;

		init(stream);
		readPos = (bytes - 1);
	}

	public FastLineReaderReverse(String fileName) throws IOException
	{
		buf = null;
		bytes = 0;
		readPos = 0;
		FileInputStream v2 = null;

		try
		{
			v2 = new FileInputStream(fileName);
			init(v2);
			readPos = (bytes - 1);
		}
		finally
		{
			if (v2 != null)
				try { v2.close(); } catch (IOException e) { e.printStackTrace(); }
		}
	}

	public static void setCommonBufSize(int size)
	{
		commonBuf1 = new byte[size];
		commonBuf2 = new byte[size];
	}

	public void close()
	{
		synchronized (lock)
		{
			if (commonBuf1 != null)
			{
				if (commonBuf2 == null)
					commonBuf2 = buf;
			}
			else
			{
				commonBuf1 = buf;
			}
		}
	}

	public void init(java.io.InputStream stream) throws IOException
	{
		synchronized (lock)
		{
			if (commonBuf1 == null)
			{
				if (commonBuf2 == null)
				{
					buf = new byte[262144];
				}
				else
				{
					buf = commonBuf2;
					commonBuf2 = null;
				}
			}
			else
			{
				buf = commonBuf1;
				commonBuf1 = null;
			}

			while (true)
			{
				int i = stream.read(buf, bytes, buf.length - bytes);
				if (i == -1)
					break;

				bytes = (i + bytes);
				if (bytes < buf.length)
					continue;

				buf = Arrays.copyOf(buf, 2 * buf.length);
			}
		}
	}

	public String readLine()
	{
		String res = null;

		if (readPos >= 0)
		{
			int length = 0;
			while (readPos >= 0)
			{
				byte b = buf[readPos--];
				if (b != 10)
				{
					if (b != 13)
						length += 1;
				}
				else
				{
					if ((readPos >= 0) && (buf[readPos] == 13))
						readPos = (readPos - 1);
				}
			}

			res = new String(this.buf, 0, ((this.readPos - length) + 1), length);
		}

		return res;
	}
}
