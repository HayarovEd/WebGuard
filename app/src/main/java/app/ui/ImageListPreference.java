package app.ui;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import app.Res;

public class ImageListPreference extends DialogPreference implements OnItemSelectedListener
{
	private CharSequence[] mEntries;
	private CharSequence[] mEntryValues;
	private int[] imageResources;
	private String mValue;
	private int mClickedDialogEntryIndex;

	public ImageListPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public ImageListPreference(Context context)
	{
		this(context, null);
	}

	public void setEntries(CharSequence[] entries)
	{
		mEntries = entries;
	}

	public void setEntries(int entriesResId)
	{
		setEntries(getContext().getResources().getTextArray(entriesResId));
	}

	public CharSequence[] getEntries()
	{
		return mEntries;
	}

	public void setEntryValues(CharSequence[] entryValues)
	{
		mEntryValues = entryValues;
	}

	public void setEntryValues(int entryValuesResId)
	{
		setEntryValues(getContext().getResources().getTextArray(entryValuesResId));
	}

	public CharSequence[] getEntryValues()
	{
		return mEntryValues;
	}

	public void setImageResources(int[] imageResources)
	{
		this.imageResources = imageResources;
	}

	public int[] getImageResources()
	{
		return imageResources;
	}

	public void setValue(String value)
	{
		mValue = value;

		persistString(value);
	}

	public void setValueIndex(int index)
	{
		if (mEntryValues != null)
		{
			setValue(mEntryValues[index].toString());
		}
	}

	public String getValue()
	{
		return mValue;
	}

	public CharSequence getEntry()
	{
		int index = getValueIndex();
		return index >= 0 && mEntries != null ? mEntries[index] : null;
	}

	public int findIndexOfValue(String value)
	{
		if (value != null && mEntryValues != null)
		{
			for (int i = mEntryValues.length - 1; i >= 0; i--)
			{
				if (mEntryValues[i].equals(value))
				{
					return i;
				}
			}
		}
		return -1;
	}

	private int getValueIndex()
	{
		return findIndexOfValue(mValue);
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder)
	{
		super.onPrepareDialogBuilder(builder);

		if (mEntries == null || mEntryValues == null)
		{
			throw new IllegalStateException("ListPreference requires an entries array and an entryValues array.");
		}

		mClickedDialogEntryIndex = getValueIndex();
		builder.setSingleChoiceItems(mEntries, mClickedDialogEntryIndex,
				new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						mClickedDialogEntryIndex = which;

						/*
						 * Clicking on an item simulates the positive button
						 * click, and dismisses the dialog.
						 */
						ImageListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
						dialog.dismiss();
					}
				});

		/*
		 * The typical interaction for list-based dialogs is to have
		 * click-on-an-item dismiss the dialog instead of the user having to
		 * press 'Ok'.
		 */
		//builder.setPositiveButton(null, null);

		ImageListPreferenceArrayAdapter listAdapter = new ImageListPreferenceArrayAdapter(
				getContext(), Res.layout.imagelist_preference,
				this.getEntries(), this.getEntryValues(),
				this.getImageResources(), mClickedDialogEntryIndex, this);

		builder.setAdapter(listAdapter, this);
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);

		if (positiveResult && mClickedDialogEntryIndex >= 0 && mEntryValues != null)
		{
			String value = mEntryValues[mClickedDialogEntryIndex].toString();
			if (callChangeListener(value))
			{
				setValue(value);
			}
		}
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index)
	{
		return a.getString(index);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
	{
		setValue(restoreValue ? getPersistedString(mValue) : (String) defaultValue);
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent())
		{
			// No need to save instance state since it's persistent
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.value = getValue();
		return myState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if (state == null || !state.getClass().equals(SavedState.class))
		{
			// Didn't save state for us in onSaveInstanceState
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setValue(myState.value);
	}

	private static class SavedState extends BaseSavedState
	{
		String value;

		public SavedState(Parcel source)
		{
			super(source);
			value = source.readString();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			super.writeToParcel(dest, flags);
			dest.writeString(value);
		}

		public SavedState(Parcelable superState)
		{
			super(superState);
		}

		@SuppressWarnings("unused")
		public static final Creator<SavedState> CREATOR = new Creator<SavedState>()
		{
			public SavedState createFromParcel(Parcel in)
			{
				return new SavedState(in);
			}

			public SavedState[] newArray(int size)
			{
				return new SavedState[size];
			}
		};
	}

	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
	{
		mClickedDialogEntryIndex = arg2;
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0)
	{
	}
}
