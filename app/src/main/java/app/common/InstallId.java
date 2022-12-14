package app.common;

import android.content.Context;
//import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

// !!! don't use here any code from native libs

public class InstallId
{

	protected static final String mmcRemovableStr = "/sys/block/mmcblk0/removable";
	protected static final String mmcSerialStr	  = "/sys/block/mmcblk0/device/serial";
	protected static final String mmcNameStr	  = "/sys/block/mmcblk0/device/name";
	protected static final String mmcHwRevStr	  = "/sys/block/mmcblk0/device/hwrev";

	protected static final String cpuInfoStr	  = "/proc/cpuinfo";
	protected static final String btMacStr		  = "/sys/class/bluetooth/hci0/address";

	protected static final String emptyMac	  = "00:00:00:00:00:00";
	protected static final String emptySerial = "0000000000000000";

	protected static final int hashSeed = 0x12345678;

	// if all ids failed
	protected static final int emptyIN = 0x33720199;
	protected static final int badIN   = 0x33720399; // and if Build.SERIAL contains bad value

	protected int IN = 0;

	public InstallId(Context context)
	{
		StringBuilder str = new StringBuilder();
		int flag = 0;
		boolean badSerial = false;

		// boot mmc serial

		String mmc = "";

		File mmcRemovable = new File(InstallId.mmcRemovableStr);
		File mmcSerial = new File(InstallId.mmcSerialStr);
		File mmcName = new File(InstallId.mmcNameStr);
		File mmcHwRev = new File(InstallId.mmcHwRevStr);

		// no: htc wildfire AOSP

		if (mmcRemovable.canRead() &&
			mmcSerial.canRead() && mmcName.canRead() && mmcHwRev.canRead())
		{
			do
			{
				str.setLength(0);
				if (!readTextFile(mmcRemovable, str))
					break;

				String type = str.toString().trim();
				if (type.equals("0")) // TODO XXX may be don't check
				{
					str.setLength(0);
					if (!readTextFile(mmcName, str))
						break;

					mmc += str.toString().trim(); // + name

					str.setLength(0);
					if (!readTextFile(mmcSerial, str))
						break;

					mmc += str.toString().trim(); // + 0xVALUE

					str.setLength(0);
					if (!readTextFile(mmcHwRev, str))
						break;

					mmc += str.toString().trim(); // + 0xVALUE
				}

				if (!mmc.isEmpty()) flag |= 1;
			}
			while (false);
		}

		// imei

//		  TelephonyManager TelMgr = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
//		  String imei = TelMgr.getDeviceId(); // requires READ_PHONE_STATE
//		  if (imei == null) imei = "";
//		  else imei = imei.trim();
//
//		  if (!imei.isEmpty()) flag |= 2;
		String imei = "";

		// hw serial number

		String serial = android.os.Build.SERIAL; // can be ANYTHING
		if (serial != null)
			serial = serial.trim();

		if (serial == null ||
			serial.equals(emptySerial) || serial.toLowerCase().equals("unknown"))
		{
			if (serial != null)
				badSerial = true; // http://www.androiddevice.info/submission/17627/show
			serial = "";
		}

		if (!serial.isEmpty()) flag |= 4;

		// cpu serial number and bt mac (if all failed and have bad serial)

		String cpu = "";
		String btmac = "";
		String wifimac = "";

		if (flag == 0 && badSerial)
//		  if (flag == 0)
		{
			// cpu

			// Chip name	: EXYNOS4412
			// Chip revision	: 0011
			// Hardware	: SMDK4x12
			// Revision	: 000c
			// Serial		: 32b35f2d4df1493b

			// Hardware	: Goldfish
			// Revision	: 0000
			// Serial		: 0000000000000000

			// x86 ? no serial?
			// TODO XXX detect Intel Core and other desktop procs

			// have serial: samsung
			// no serial: htc, lg, sony

			File cpuInfo = new File(InstallId.cpuInfoStr);

			if (cpuInfo.canRead())
			{
				str.setLength(0);
				if (readTextFile(cpuInfo, str))
					cpu = str.toString().trim();
			}

			int sPos, dPos, ePos;
			if (!cpu.isEmpty() && (sPos = cpu.indexOf("Serial")) >= 0)
			{
				dPos = cpu.indexOf(':', sPos) + 1;
				ePos = cpu.indexOf('\n', sPos);
				if (dPos > sPos && dPos < cpu.length())
				{
					cpu = (ePos < dPos) ? cpu.substring(dPos).trim() :
											cpu.substring(dPos, ePos).trim();
					if (cpu.equals(emptySerial))
						cpu = "";
				}
			}

			if (!cpu.isEmpty()) flag |= 8;

			// bt mac

			// htc wildfire AOSP
			// /sys/module/board_marvel/parameters/bdaddress
			//
			// htc evo? CyanogenMod
			// /sys/module/htc_bdaddress/parameters/bdaddress
			//
			// htc one, lg
			// /sys/class/bluetooth/hci0/address
			// /sys/devices/virtual/bluetooth/hci0/address

			// adb shell ls -laR /sys | grep -C 3 hci

			// http://www.futureofprivacy.org/2014/11/04/android-5-0-lollipop-major-new-privacy-features/

			/*
			if (flag == 0)
			{
				File btMac = new File(InstallId.btMacStr);
				if (btMac.canRead())
				{
					str.setLength(0);
					if (readTextFile(btMac, str))
					{
						btmac = str.toString().trim();
						if (btmac.equals(emptyMac))
							btmac = "";
					}
				}

				if (!btmac.isEmpty()) flag |= 2;

				// require "android.permission.BLUETOOTH" permission
				//BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
				//bluetoothAdapter.getAddress();
			}
			*/

			// wifi mac (not work if wifi disabled)

			/*
			if (flag == 0)
			{
				try
				{
					List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
					for (NetworkInterface intf : interfaces)
					{
						if (!intf.getName().equalsIgnoreCase("wlan0"))
							continue;

						byte[] mac = intf.getHardwareAddress();
						str.setLength(0);
						for (int i = 0; i < mac.length; i++)
							str.append(String.format("%02X:", mac[i]));
						if (str.length() > 0)
							str.deleteCharAt(str.length() - 1);

						if (mac.length == 6)
						{
							wifimac = str.toString().trim();
							if (wifimac.equals(emptyMac))
								wifimac = "";
						}

						break;
					}
				}
				catch (Exception ex) { wifimac = ""; }

				if (!wifimac.isEmpty()) flag |= 2;

				// /sys/class/net/[something]/address
				//
				// adb shell getprop ril.wifi_macaddr
				//
				// require "android.permission.ACCESS_WIFI_STATE" permission
				//WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				//wm.getConnectionInfo().getMacAddress();
			}
			*/
		}

		// gen IN

		String hw;
		if (flag == 0)
		{
			this.IN = (badSerial) ? InstallId.badIN : InstallId.emptyIN;
			return;
		}
		else if (flag == 8 || flag == 2)
		//else if ((flag & 8) != 0 || (flag & 2) != 0)
		{
			// TODO XXX use separate because we already have old users and can't change algo
			hw = (flag == 8) ? cpu : btmac;
			//hw = (flag == 8) ? cpu : wifimac;
			//hw = cpu + "_" + btmac;
		}
		else
		{
			// imei == ""
			hw = mmc + "_" + imei + "_" + serial;
		}

		this.IN = murmurhash3x8632(hw.getBytes(), 0, hw.length(), InstallId.hashSeed);
		if (this.IN != 0)
			this.IN = (this.IN & ~0xF) | ((byte) flag);
	}

	public int getInstallID()
	{
		return ((this.IN == 0) ? InstallId.emptyIN : this.IN);
	}

	public String getInstallIDStr()
	{
		int value = this.getInstallID();
		//String hex = Integer.toHexString(value);
		String hex = String.format("%08x", value);

		return hex;
	}

	protected static boolean readTextFile(File file, StringBuilder text)
	{
		BufferedReader reader = null;
		char[] buf = new char[256];
		int size;

		try
		{
			reader = new BufferedReader(new FileReader(file));
			while ((size = reader.read(buf)) != - 1)
				if (size > 0) text.append(buf, 0, size);
			reader.close();
			reader = null;

			return true;
		}
		catch (FileNotFoundException ex) { }
		catch (IOException ex) { }

		finally
		{
			if (reader != null)
				try { reader.close(); } catch (IOException e) { /*e.printStackTrace();*/ }
		}

		return false;
	}

	// returns the MurmurHash3_x86_32 hash
	protected static int murmurhash3x8632(byte[] data, int offset, int len, int seed)
	{
		int c1 = 0xcc9e2d51;
		int c2 = 0x1b873593;

		int h1 = seed;
		int roundedEnd = offset + (len & 0xfffffffc);  // round down to 4 byte block

		for (int i = offset; i < roundedEnd; i += 4)
		{
			// little endian load order
			int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
			k1 *= c1;
			k1 = (k1 << 15) | (k1 >>> 17);	// ROTL32(k1,15);
			k1 *= c2;

			h1 ^= k1;
			h1 = (h1 << 13) | (h1 >>> 19);	// ROTL32(h1,13);
			h1 = h1 * 5 + 0xe6546b64;
		}

		// tail
		int k1 = 0;

		switch (len & 0x03)
		{
			case 3:
				k1 = (data[roundedEnd + 2] & 0xff) << 16;
				// fallthrough
			case 2:
				k1 |= (data[roundedEnd + 1] & 0xff) << 8;
				// fallthrough
			case 1:
				k1 |= data[roundedEnd] & 0xff;
				k1 *= c1;
				k1 = (k1 << 15) | (k1 >>> 17);	// ROTL32(k1,15);
				k1 *= c2;
				h1 ^= k1;
			default:
		}

		// finalization
		h1 ^= len;

		// fmix(h1);
		h1 ^= h1 >>> 16;
		h1 *= 0x85ebca6b;
		h1 ^= h1 >>> 13;
		h1 *= 0xc2b2ae35;
		h1 ^= h1 >>> 16;

		return h1;
	}
}
