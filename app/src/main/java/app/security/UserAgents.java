package app.security;

import android.content.Context;
import app.Res;

import java.util.Random;


public class UserAgents
{
	private static Random random = new Random(System.currentTimeMillis());

	private static String[] list_mobile = null; // mobile useragents
	private static String[] list_desktop = null; // desktop useragents

	private static int randomNum = 0;

	public static void load(Context context)
	{
		list_mobile = context.getResources().getStringArray(Res.array.user_agents_mobile);
		list_desktop = context.getResources().getStringArray(Res.array.user_agents_desktop);
	}

	public static String getAgent(boolean isDesktop)
	{
		if (isDesktop)
		{
			if (list_desktop.length < 1)
				return null;

			randomNum = random.nextInt(list_desktop.length);
			return list_desktop[randomNum];
		}
		else
		{
			if (list_mobile.length < 1)
				return null;

			randomNum = random.nextInt(list_mobile.length);
			return list_mobile[randomNum];
		}
	}

	public static String getAgentDesktop()
	{
		if (list_desktop.length < 1)
			return null;

		return list_desktop[0];
	}
}
