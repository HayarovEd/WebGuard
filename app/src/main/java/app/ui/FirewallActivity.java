package app.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import app.netfilter.FilterVpnService;
import app.App;
import app.Res;
import app.internal.Preferences;
import app.internal.Settings;
import app.security.Browsers;
import app.security.Firewall;
import app.security.Policy;
import app.ui.ExpandableListViewAdapter.*;
import app.ui.ExpandableListViewGroup.*;
import java.util.HashSet;
import java.util.List;

//public class FirewallActivity extends Activity
public class FirewallActivity extends PreferenceActivity
{
	ExpandableListView appsList;
	EditText inputSearch;
	ExpandableListViewAdapter listViewAdapter = null;
	//CheckBox checkBlockAppsData;
	Preference prefBlockAppsData;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		// android bug http://stackoverflow.com/questions/11751498/how-to-change-preferenceactivity-theme
		// TODO XXX replace for each activity to onApplyThemeResource
		try
		{
			if (Preferences.getBoolean(Settings.PREF_USE_LIGHT_THEME, false)) {
				setTheme(android.R.style.Theme_DeviceDefault_Light);
			}
		}
		catch (Exception e) { }

		super.onCreate(savedInstanceState);

		//if (Preferences.get(Settings.PREF_USE_LIGHT_THEME))
		//	setTheme(android.R.style.Theme_DeviceDefault_Light);

		// http://baroqueworksdev.blogspot.ro/2012/03/create-preferenceactivity-with-header.html
		// http://stackoverflow.com/questions/2697233/how-to-add-a-button-to-preferencescreen
		// http://stackoverflow.com/questions/14725553/android-listview-fixed-height-independent-of-screen-size
		// http://stackoverflow.com/questions/2025282/difference-between-px-dp-dip-and-sp-on-android
		setContentView(Res.layout.firewall_activity);
		addPreferencesFromResource(Res.layout.firewall_preferences);

		getActionBar().setDisplayHomeAsUpEnabled(true);
		overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

		appsList = (ExpandableListView) findViewById(Res.id.appsList);

		inputSearch = (EditText) findViewById(Res.id.appSearch);
		inputSearch.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { }
			@Override
			public void afterTextChanged(Editable arg0) { }

			@Override
			public void onTextChanged(CharSequence cs, int arg1, int arg2, int arg3)
			{
				if (listViewAdapter != null)
					listViewAdapter.getFilter().filter(cs.toString());
			}
		});

		// if use default checkbox (not preferences)
		/*
		checkBlockAppsData = (CheckBox) findViewById(Res.id.appsBlockData);
		boolean enabled = Preferences.get(Settings.PREF_BLOCK_APPS_DATA);
		checkBlockAppsData.setChecked(enabled);
		*/

		// if use preferences
		// http://stackoverflow.com/questions/5045000/checkboxpreference-onclick
		prefBlockAppsData = (Preference) findPreference(Settings.PREF_BLOCK_APPS_DATA);
		prefBlockAppsData.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			public boolean onPreferenceClick(Preference preference)
			{
				onBlockAppsDataCheckbox(null, preference); // preference already changed
				return true;
			}
		});

		//boolean showed = Preferences.get(Settings.PREF_SEE_FIREWALL_OPTS);
		//if (!showed)
		//	  Preferences.putBoolean(Settings.PREF_SEE_FIREWALL_OPTS, true);

		// not need now because we try to enable protection on first start
		//if (App.isFirstRun() && !statsForced)
		//{
		//	  Statistics.addLog(Settings.LOG_BUY_SHOW);
		//	  statsForced = true;
		//	  UpdaterService.startUpdate(UpdaterService.START_FORCE); // for 'buy show' statistic
		//}

		Firewall.init(); // inited in App.onStart normally

		updateList(true);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Firewall.save(); // save rules
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		updateList(false);
		updateGui();
	}

	private void updateGui()
	{
		//boolean checked = checkBlockAppsData.isChecked();
		boolean checked = Preferences.getBoolean(Settings.PREF_BLOCK_APPS_DATA, false);

		inputSearch.setEnabled(!checked);
		appsList.setEnabled(!checked);

		if (checked)
		{
			for (int i = 0; i < 3; i++)
				appsList.collapseGroup(i);
		}
	}

	private void updateList(boolean create)
	{
		Context context = App.getContext();
		SparseArray<ExpandableListViewGroup> groups = new SparseArray<ExpandableListViewGroup>();
		HashSet<Integer> uids = new HashSet<Integer>();

		if (create)
		{
			// empty list onCreate
			//ExpandableListViewGroup group = new ExpandableListViewGroup("Apps", null);
			listViewAdapter =
				ExpandableListViewAdapter.createListViewAdapter(this, Res.id.appsList, groups, Res.layout.firewall_listrow, null);

			return;
		}

		ExpandableListViewGroup groupBrowsers =
			new ExpandableListViewGroup("group_browsers", context.getString(Res.string.firewall_group_browsers));
		ExpandableListViewGroup groupUser =
			new ExpandableListViewGroup("group_user", context.getString(Res.string.firewall_group_user));
		ExpandableListViewGroup groupFirmware =
			new ExpandableListViewGroup("group_firmware", context.getString(Res.string.firewall_group_firmware));

		// fill groups with app data

		try
		{
			PackageManager pm = context.getPackageManager();
			List<ApplicationInfo> packages =
									pm.getInstalledApplications(0); // get list of installed apps

			ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

			for (ApplicationInfo packageInfo : packages)
			{
				// skip packages without '.' in name or excepted
				final String pkgName = packageInfo.packageName;
				if (pkgName == null || (pkgName.indexOf('.') < 0 || pkgName.equals(Settings.APP_PACKAGE)))
					continue;

				// TODO XXX what do with packages with same uid???
				int pkgUid = packageInfo.uid;
				if (pkgUid == 0 || uids.contains(Integer.valueOf(pkgUid)))
					continue; // skip root applications or with same uid
				uids.add(Integer.valueOf(pkgUid));

				String appName = null;
				try { appName = pm.getApplicationLabel(packageInfo).toString(); }
				catch (NullPointerException e) {}
				if (appName != null && (appName.isEmpty() || appName.equals(pkgName)))
					appName = null;

				Drawable icon = null;
				try { icon = pm.getApplicationIcon(packageInfo); }
				catch (NullPointerException e) {}

				AppItem item = new AppItem(pkgName, appName, icon);
				item.setMobileState(Firewall.mobileAppIsAllowed(pkgName)).
						setWiFiState(Firewall.wifiAppIsAllowed(pkgName));

				if (Browsers.isBrowser(pkgName))
					groupBrowsers.addChild(item);
				else if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1)
					groupFirmware.addChild(item);
				else
					groupUser.addChild(item);

				//L.a(TAG, "app '" + pkgName + "' " + pkgUid);
			}
		}
		catch (Exception e) { }

		// add groups to list

		groups.append(0, groupBrowsers);
		groups.append(1, groupUser);
		groups.append(2, groupFirmware);

		ExpandableListViewAdapter.OnChildListener ocvc = (new OnChildListener() {

				@Override
				public void onChildView(ExpandableListViewGroupItem child,
										int groupPosition, int childPosition, boolean isLastChild,
										View childView, ViewGroup parent) {
					onListChildView(child, groupPosition, childPosition, isLastChild, childView, parent);
				}

				@Override
				public boolean onChildFilter(ExpandableListViewGroupItem child,
												int groupPosition, int childPosition,
												String filterText, String filterTextLower)
				{
					return onListChildFilter(child, groupPosition, childPosition, filterText, filterTextLower);
				}

				@Override
				public int onChildCompare(ExpandableListViewGroupItem child1, ExpandableListViewGroupItem child2)
				{
					return onListChildCompare(child1, child2);
				}
			});

		listViewAdapter =
			ExpandableListViewAdapter.createListViewAdapter(this, Res.id.appsList, groups, Res.layout.firewall_listrow, ocvc);
		listViewAdapter.showChildrenCount(true).sortChildren();
		listViewAdapter.notifyDataSetChanged();
	}

	private void onListChildView(ExpandableListViewGroupItem child,
									int groupPosition, int childPosition, boolean isLastChild,
									View childView, ViewGroup parent)
	{
		final Activity activity = this;
		final AppItem item = (AppItem) child;

		final String text = item.getText();
		final Drawable icon = item.getIcon();

		TextView appText = (TextView) childView.findViewById(Res.id.appText);
		ImageView appIcon = null;
		CheckBox mobileAllow = (CheckBox) childView.findViewById(Res.id.appAllowMobile);
		CheckBox wifiAllow = (CheckBox) childView.findViewById(Res.id.appAllowWifi);

		// details on icon and name click

		final String tooltip =
			(item.getRealText() == null) ? item.getName() : item.getRealText() + " (" + item.getName() + ")";

		//childView.setOnClickListener(new OnClickListener() {
		appText.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v)
			{
				Toast.makeText(activity, tooltip, Toast.LENGTH_SHORT).show();
			}
		});

		if (appIcon != null)
		{
			appIcon.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v)
				{
					Toast.makeText(activity, tooltip, Toast.LENGTH_SHORT).show();
				}
			});
		}

		// checkbox'es changes

		mobileAllow.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				item.setMobileState(isChecked);
				//listViewAdapter.updateChild(item);

				Firewall.mobileAppState(item.getName(), isChecked);
			}
		});

		wifiAllow.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				item.setWiFiState(isChecked);
				//listViewAdapter.updateChild(item);

				Firewall.wifiAppState(item.getName(), isChecked);
			}
		});

		// update icon, text and checkboxes

		appText.setText(text);

		if (icon != null)
		{
			appIcon = (ImageView) childView.findViewById(Res.id.appIcon);
			appIcon.setImageDrawable(icon);
		}

		mobileAllow.setChecked(item.getMobileState());
		wifiAllow.setChecked(item.getWiFiState());
	}

	private boolean onListChildFilter(ExpandableListViewGroupItem child,
										int groupPosition, int childPosition,
										String filterText, String filterTextLower)
	{
		AppItem item = (AppItem) child;
		String text = item.getRealText();

		if (item.getName().toLowerCase().indexOf(filterTextLower) >= 0)
			return false;
		else if (text != null && text.toLowerCase().indexOf(filterTextLower) >= 0)
			return false;

		return true; // filtered
	}

	private int onListChildCompare(ExpandableListViewGroupItem child1, ExpandableListViewGroupItem child2)
	{
		String text1 = ((AppItem) child1).getRealText();
		String text2 = ((AppItem) child2).getRealText();

		// items with without labels will be in the end of list

		if (text1 == null && text2 == null)
			return 0;
		if (text1 == null && text2 != null)
			return 1;
		else if (text1 != null && text2 == null)
			return -1;
		else
			return child1.getText().compareToIgnoreCase(child2.getText());
	}

	public void onBlockAppsDataCheckbox(View view, Preference preference) {

		// запоминаю по-нормальному выбранное состояние следующих чекбоксов
		Preferences.putBoolean(Settings.PREF_BLOCK_APPS_DATA, preference.getSharedPreferences().getBoolean(Settings.PREF_BLOCK_APPS_DATA, false));


		updateGui();


		//boolean checked = ((CheckBox) view).isChecked(); // is the view now checked?
		//boolean checked = checkBlockAppsData.isChecked();
		boolean enabled = Preferences.getBoolean(Settings.PREF_BLOCK_APPS_DATA, false);
		boolean showed = Preferences.getBoolean(Settings.PREF_SEE_BLOCK_APPS_DATA, false);



		// if use default checkbox (not preferences)
		/*
		if (checked == enabled)
			return;
		enabled = checked;

		if (enabled)
			Preferences.putBoolean(Settings.PREF_BLOCK_APPS_DATA, true);
		else
			Preferences.putBoolean(Settings.PREF_BLOCK_APPS_DATA, false);
		*/

		if (enabled) {
			if (!showed) {
				Dialogs.showBlockAppsDataWarning(this);
				Preferences.putBoolean(Settings.PREF_SEE_BLOCK_APPS_DATA, true);
			}
		}

		Policy.reloadPrefs(); // TODO XXX may be move internetOnlyForBrowsers flag from Policy to Firewall?
		if (enabled)
			FilterVpnService.notifyDropConnections(this); // block apps data except browsers
	}

	//

	private static class AppItem implements ExpandableListViewGroupItem
	{
		private final String name;
		private final String text;
		private final Drawable icon;

		private boolean mobileAllow = true;
		private boolean wifiAllow = true;

		public AppItem(String name, String text, Drawable icon)
		{
			this.name = name;
			this.text = text;
			this.icon = icon;
		}

		public String getName()
		{
			return name;
		}

		public long getId()
		{
			return name.hashCode();
		}

		public String getText()
		{
			return (text == null) ? name : text;
		}

		public String getRealText()
		{
			return text;
		}

		public Drawable getIcon()
		{
			return icon;
		}

		public AppItem setMobileState(boolean allow)
		{
			mobileAllow = allow;
			return this;
		}

		public boolean getMobileState()
		{
			return mobileAllow;
		}

		public AppItem setWiFiState(boolean allow)
		{
			wifiAllow = allow;
			return this;
		}

		public boolean getWiFiState()
		{
			return wifiAllow;
		}
	}

	//

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();

		overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				onBackPressed();
				break;

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}
}
