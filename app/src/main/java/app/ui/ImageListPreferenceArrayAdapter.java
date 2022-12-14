package app.ui;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;
import app.Res;

public class ImageListPreferenceArrayAdapter extends ArrayAdapter<CharSequence>
{
	private int index;
	private CharSequence[] entries;
	private int[] imageResources;
	private OnItemSelectedListener onItemSelectedListener;

	public ImageListPreferenceArrayAdapter(Context context, int textViewResourceId,
											CharSequence[] entries, CharSequence[] objects,
											int[] imageResources, int selected,
											OnItemSelectedListener onItemSelectedListener)
	{
		super(context, textViewResourceId, objects);

		index = selected;
		this.entries = entries;
		this.imageResources = imageResources;
		this.onItemSelectedListener = onItemSelectedListener;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		View row;
		View clickContainer;

		if (convertView != null)
		{
			row = convertView;
			clickContainer = row.findViewById(Res.id.il_llClickContainer);
		}
		else
		{
			//inflate layout
			LayoutInflater inflater = ((Activity)getContext()).getLayoutInflater();
			row = inflater.inflate(Res.layout.imagelist_preference, parent, false);

			clickContainer = row.findViewById(Res.id.il_llClickContainer);
			clickContainer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if(onItemSelectedListener != null)
						onItemSelectedListener.onItemSelected(null, v, (Integer)v.getTag(), (Integer)v.getTag());
					index = (Integer)v.getTag();
					notifyDataSetChanged();
				}
			});
		}
		row.setTag(position);
		clickContainer.setTag(position);

		//set name
		TextView tv = (TextView) row.findViewById(Res.id.il_txt);
		tv.setText(entries[position]);

		//set checkbox
		RadioButton tb = (RadioButton) row.findViewById(Res.id.ckbox);
		tb.setChecked(position == index);
		tb.setClickable(false);

		ImageView img = (ImageView)row.findViewById(Res.id.il_img);
		if (imageResources.length > position && imageResources[position] > 0)
		{
			img.setVisibility(View.VISIBLE);
			//img.setImageDrawable(getContext().getResources().getDrawable(imageResources[position]));
			img.setImageResource(imageResources[position]);
		}
		else
		{
			img.setVisibility(View.INVISIBLE);
		}

		return row;
	}
}
