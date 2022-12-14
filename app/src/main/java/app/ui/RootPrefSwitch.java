package app.ui;

import android.widget.CompoundButton;
import android.widget.Switch;
import app.netfilter.FilterVpnService;
import app.internal.Preferences;
import app.common.debug.L;
import app.common.Utils;
import app.internal.Settings;


public class RootPrefSwitch implements CompoundButton.OnCheckedChangeListener
{

	public interface OnChangedPowerListener {
		void onChangedPower();
	}

	protected final OptionsActivity activity;
	private Switch prefSwitch;
	private OnChangedPowerListener mOnChangedPowerListener;

	public RootPrefSwitch(OptionsActivity activity, Switch swtch, OnChangedPowerListener listener)
	{
		this.activity = activity;
		this.mOnChangedPowerListener = listener;
		setSwitch(swtch);
	}

	public void setSwitch(Switch swtch)
	{
		if (prefSwitch == swtch)
			return;

		if (prefSwitch != null)
			prefSwitch.setOnCheckedChangeListener(null);
		prefSwitch = swtch;
		prefSwitch.setOnCheckedChangeListener(this);

		//prefSwitch.setChecked(isSwitchOn());
		prefSwitch.setChecked(Preferences.isActive());
	}

	public void onCheckedChanged(CompoundButton view, boolean isChecked)
	{
		boolean isServiceActive = Preferences.isActive();

		if (isChecked != isServiceActive)
		{
			tempDisable(); // ???
			L.d(Settings.TAG_ROOTPREFSWITCH, "isChecked");
			Preferences.putBoolean(Settings.PREF_ACTIVE, isChecked);

			//if (isChecked)
			//{
			//	  activity.startVPN(App.getContext());
			//	  UpdaterService.startUpdate(UpdaterService.START_FORCE_DELAYED);
			//}
			//else
			//	  activity.stopVPN();
		}

		if (mOnChangedPowerListener != null) {
			mOnChangedPowerListener.onChangedPower();
		}
	}
/*
	public boolean isSwitchOn()
	{
		return Preferences.isActive();
	}
*/
	public void update()
	{
		prefSwitch.setChecked(Preferences.isActive());
	}

	public void resume()
	{
		prefSwitch.setOnCheckedChangeListener(this);
		prefSwitch.setChecked(Preferences.isActive());
	}

	public void pause()
	{
		prefSwitch.setOnCheckedChangeListener(null);
	}

	// ???
	private void tempDisable()
	{
		prefSwitch.setEnabled(false);

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				// state changed, but our vpn service starting/stopping now, so wait
				// TODO XXX what do if reach counter max?
				int counter = 0;
				while (counter < 5 && FilterVpnService.isInTransition())
				{
					L.e(Settings.TAG_ROOTPREFSWITCH, "waiting for service");
					Utils.sleep(1000);
					counter++;
				}

				activity.runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						prefSwitch.setEnabled(true);
					}
				});
			}
		}).start();
	}
}
