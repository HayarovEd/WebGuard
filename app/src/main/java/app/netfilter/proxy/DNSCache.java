package app.netfilter.proxy;

import app.netfilter.dns.DNSAnswer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DNSCache
{
	private static final ArrayList<DNSAnswer> answers = new ArrayList<DNSAnswer>(50);
	private static final Set<byte[]> ips = new HashSet<byte[]>(50);

	public static DNSAnswer getForIp(byte[] ip)
	{
		synchronized (answers)
		{
			for (DNSAnswer answer : answers)
			{
				if (Arrays.equals(answer.ip, ip))
					return answer;
			}
		}

		return null;
	}

	public static DNSAnswer getForDomain(String domain)
	{
		synchronized (answers)
		{
			for (DNSAnswer answer : answers)
			{
				if (answer.domain != null && answer.domain.equals(domain))
					return answer;
			}
		}

		return null;
	}

	public static void addAnswer(DNSAnswer answer)
	{
		synchronized (answers)
		{
			/*
			for (DNSAnswer a: answers)
			{
				if (a.ip != null && Arrays.equals(a.ip, answer.ip))
					return; // this ip already is here
			}
			*/

			if (!ips.contains(answer.ip))
			{
				answers.add(answer);
				ips.add(answer.ip);
			}
		}
	}

	public static void clear()
	{
		synchronized (answers)
		{
			answers.clear();
		}
	}
}
