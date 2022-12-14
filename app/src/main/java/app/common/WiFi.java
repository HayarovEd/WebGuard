package app.common;

import app.common.debug.L;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import app.internal.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class WiFi
{
	private static Object getField(Object obj, String name)
			throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
	{
		Field f = obj.getClass().getField(name);
		Object out = f.get(obj);
		return out;
	}

	private static Object getDeclaredField(Object obj, String name)
			throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
	{
		Field f = obj.getClass().getDeclaredField(name);
		f.setAccessible(true);
		Object out = f.get(obj);
		return out;
	}

	private static void setEnumField(Object obj, String value, String name)
			throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
	{
		Field f = obj.getClass().getField(name);
		f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
	}

	private static String getEnumField(Object obj, String name)
			throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
	{
		Field f = obj.getClass().getField(name);
		//f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
		return f.get(obj).toString();
	}

	private static void setProxySettings(String assign, WifiConfiguration wifiConf)
			throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException
	{
		setEnumField(wifiConf, assign, "proxySettings");
	}

	static WifiConfiguration GetCurrentWifiConfiguration(WifiManager manager)
	{
		if (!manager.isWifiEnabled())
			return null;

		List<WifiConfiguration> configList = manager.getConfiguredNetworks();
		if (configList == null)
		{
			// TODO XXX sleep? 3 sec? maybe check state or another?
			Utils.sleep(3000);

			configList = manager.getConfiguredNetworks();
			if (configList == null)
				return null;
		}

		int cur = manager.getConnectionInfo().getNetworkId();
		for (WifiConfiguration config : configList)
		{
			if (config.networkId == cur)
				return config;
		}

		return null;
	}

	public static boolean isProxySet(Context context)
	{
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiConfiguration config = GetCurrentWifiConfiguration(manager);
		if (null == config)
			return false;

		try
		{
			String res = getEnumField(config, "proxySettings");
			L.a(Settings.TAG_WIFI, "PROXY res = " + res);

			if (res != null && res.equals("STATIC"))
				return true;
		}
		catch (Exception e) { e.printStackTrace(); }

		return false;
	}

	public static void setWifiProxySettings(Context context)
	{
		// get the current wifi configuration
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiConfiguration config = GetCurrentWifiConfiguration(manager);
		if (null == config)
			return;

		try
		{
			// get the link properties from the wifi configuration
			Object linkProperties = getField(config, "linkProperties");
			if (null == linkProperties)
				return;

			// get the setHttpProxy method for LinkProperties
			Class proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
			Class[] setHttpProxyParams = new Class[1];
			setHttpProxyParams[0] = proxyPropertiesClass;
			Class lpClass = Class.forName("android.net.LinkProperties");
			Method setHttpProxy = lpClass.getDeclaredMethod("setHttpProxy", setHttpProxyParams);
			setHttpProxy.setAccessible(true);

			// get ProxyProperties constructor
			Class[] proxyPropertiesCtorParamTypes = new Class[3];
			proxyPropertiesCtorParamTypes[0] = String.class;
			proxyPropertiesCtorParamTypes[1] = int.class;
			proxyPropertiesCtorParamTypes[2] = String.class;

			Constructor proxyPropertiesCtor = proxyPropertiesClass.getConstructor(proxyPropertiesCtorParamTypes);

			// create the parameters for the constructor
			Object[] proxyPropertiesCtorParams = new Object[3];
			proxyPropertiesCtorParams[0] = "127.0.0.1";
			proxyPropertiesCtorParams[1] = 8080;
			proxyPropertiesCtorParams[2] = null;

			// create a new object using the params
			Object proxySettings = proxyPropertiesCtor.newInstance(proxyPropertiesCtorParams);

			// pass the new object to setHttpProxy
			Object[] params = new Object[1];
			params[0] = proxySettings;
			setHttpProxy.invoke(linkProperties, params);

			setProxySettings("STATIC", config);

			// save the settings
			manager.updateNetwork(config);
			manager.disconnect();
			manager.reconnect();
		}
		catch (Exception e) { e.printStackTrace(); }
	}

	public static void unsetWifiProxySettings(Context context)
	{
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiConfiguration config = GetCurrentWifiConfiguration(manager);
		if (null == config)
			return;

		try
		{
			//get the link properties from the wifi configuration
			Object linkProperties = getField(config, "linkProperties");
			if (null == linkProperties)
				return;

			//get the setHttpProxy method for LinkProperties
			Class proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
			Class[] setHttpProxyParams = new Class[1];
			setHttpProxyParams[0] = proxyPropertiesClass;
			Class lpClass = Class.forName("android.net.LinkProperties");
			Method setHttpProxy = lpClass.getDeclaredMethod("setHttpProxy", setHttpProxyParams);
			setHttpProxy.setAccessible(true);

			//pass null as the proxy
			Object[] params = new Object[1];
			params[0] = null;
			setHttpProxy.invoke(linkProperties, params);

			setProxySettings("NONE", config);

			//save the config
			manager.updateNetwork(config);
			manager.disconnect();
			manager.reconnect();
		}
		catch (Exception e) { e.printStackTrace(); }
	}

	public static void reconnect(Context context)
	{
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiConfiguration config = GetCurrentWifiConfiguration(manager);
		if (null == config)
			return;

		manager.disconnect();
		Utils.sleep(1000); // TODO XXX check state instead of sleep
		manager.reconnect();
	}

	// http://stackoverflow.com/questions/22841317/disable-wifi-tethering-on-android
	// http://stackoverflow.com/questions/3531801/enable-disable-usb-or-wifi-tethering-programmatically-on-android
	// http://stackoverflow.com/questions/7509924/detect-usb-tethering-on-android/7830747#7830747
	public static void setWifiTetheringEnabled(Context context, boolean enable)
	{
		WifiManager wifiManager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);

		Method[] methods = wifiManager.getClass().getDeclaredMethods();
		for (Method method : methods)
		{
			if (method.getName().equals("setWifiApEnabled"))
			{
				try { method.invoke(wifiManager, null, enable); } catch (Exception ex) {}
				break;
			}
		}
	}
}
