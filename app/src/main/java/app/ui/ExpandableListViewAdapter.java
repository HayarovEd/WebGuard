package app.ui;

import android.app.Activity;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckedTextView;
import android.widget.ExpandableListView;
import android.widget.Filter;
import android.widget.Filterable;
import app.Res;
import app.ui.ExpandableListViewGroup.*;
import java.util.Comparator;

public class ExpandableListViewAdapter extends BaseExpandableListAdapter implements Filterable
{
	private SparseArray<ExpandableListViewGroup> groups;
	private final SparseArray<ExpandableListViewGroup> groupsOrig;
	private final int rowDetailsResourceId;
	private final OnChildListener ocvc;

	private boolean isShowItemsCount = false;

	private final ItemFilter filter = new ItemFilter();
	private final LayoutInflater inflater;
	//private final Activity activity;
	//private final int dpAsPixels;

	// create new ExpandableListViewAdapter and add it to list with listViewResourceId
	public static ExpandableListViewAdapter createListViewAdapter(Activity activity, final int listViewResourceId,
																	SparseArray<ExpandableListViewGroup> groups,
																	final int rowDetailsResourceId, final OnChildListener ocvc)
	{
		ExpandableListView listView = (ExpandableListView) activity.findViewById(listViewResourceId);
		ExpandableListViewAdapter adapter =
			new ExpandableListViewAdapter(activity, groups, rowDetailsResourceId, ocvc);
		listView.setAdapter(adapter);

		return adapter;
	}

	// create new ExpandableListViewAdapter with internal row by rowDetailsResourceId
	public ExpandableListViewAdapter(Activity activity, SparseArray<ExpandableListViewGroup> groups,
										final int rowDetailsResourceId, final OnChildListener ocvc)
	{
		this.groups = groups;
		this.groupsOrig = groups;
		this.rowDetailsResourceId = rowDetailsResourceId;
		this.ocvc = ocvc;

		//this.activity = activity;
		inflater = activity.getLayoutInflater();

		//float scale = activity.getResources().getDisplayMetrics().density;
		//dpAsPixels = (int) (20 /*dp*/ * scale + 0.5f);
	}

	// may be need to call notifyDataSetChanged after this
	public ExpandableListViewAdapter showChildrenCount(boolean enable)
	{
		isShowItemsCount = enable;
		return this;
	}

	public ExpandableListViewAdapter sortChildren()
	{
		if (ocvc == null)
			return this;

		Comparator <ExpandableListViewGroupItem> comparator = (new Comparator <ExpandableListViewGroupItem>() {

				@Override
				public int compare(ExpandableListViewGroupItem child1, ExpandableListViewGroupItem child2)
				{
					return ocvc.onChildCompare(child1, child2);
				}
			});

		int gs = groupsOrig.size();
		for (int i = 0; i < gs; i++)
			groupsOrig.valueAt(i).sortChildren(comparator);

		groups = groupsOrig;

		return this;
	}

	@Override
	public Object getChild(int groupPosition, int childPosition)
	{
		return groups.get(groupPosition).getChild(childPosition);
	}

	@Override
	public long getChildId(int groupPosition, int childPosition)
	{
		//return 0; // see hasStableIds
		return groups.get(groupPosition).getChild(childPosition).getId();
	}

	@Override
	public View getChildView(int groupPosition, final int childPosition, boolean isLastChild,
							  View convertView, ViewGroup parent)
	{
		// create view
		if (convertView == null)
			convertView = inflater.inflate(rowDetailsResourceId, null);

		//((ExpandableListView) parent).setDividerHeight(0);

		final ExpandableListViewGroupItem child =
			(ExpandableListViewGroupItem) getChild(groupPosition, childPosition);

		// fill view (in external handler)
		if (ocvc != null)
			ocvc.onChildView(child, groupPosition, childPosition, isLastChild, convertView, parent);

		return convertView;
	}

	@Override
	public int getChildrenCount(int groupPosition)
	{
		return groups.get(groupPosition).getChildrenCount();
	}

	@Override
	public Object getGroup(int groupPosition)
	{
		return groups.get(groupPosition);
	}

	@Override
	public int getGroupCount()
	{
		return groups.size();
	}

	@Override
	public void onGroupCollapsed(int groupPosition)
	{
		super.onGroupCollapsed(groupPosition);
	}

	@Override
	public void onGroupExpanded(int groupPosition)
	{
		super.onGroupExpanded(groupPosition);
	}

	@Override
	public long getGroupId(int groupPosition)
	{
		//return 0; // see hasStableIds
		return groups.get(groupPosition).getId();
	}

	@Override
	public View getGroupView(int groupPosition, boolean isExpanded,
								View convertView, ViewGroup parent)
	{
		if (convertView == null)
			convertView = inflater.inflate(Res.layout.listrow_group, null);

		//LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) parent.getLayoutParams();
		//lp.setMargins(lp.leftMargin, dpAsPixels * 10, lp.rightMargin, dpAsPixels * 10);
		//parent.setLayoutParams(lp);
		//convertView.getPaddingLeft();
		//convertView.setPadding(convertView.getPaddingLeft(), dpAsPixels, convertView.getPaddingRight(), dpAsPixels);

		//((ExpandableListView) parent).setDividerHeight(dpAsPixels);

		ExpandableListViewGroup group = (ExpandableListViewGroup) getGroup(groupPosition);
		final String text = group.getText();

		((CheckedTextView) convertView).setText((isShowItemsCount) ? text + " (" + group.getChildrenCount() + ")" : text);
		((CheckedTextView) convertView).setChecked(isExpanded);

		return convertView;
	}

	@Override
	public boolean hasStableIds()
	{
		//return false;
		return true;
	}

	@Override
	public boolean isChildSelectable(int groupPosition, int childPosition)
	{
		return false;
	}

	/*
	 * call this function from main thread to correctly refresh ListView
	 *
	 * TODO XXX temporary func to update item. Think out a way to update item from onChildView event
	 */
/*
	public boolean updateChild(ExpandableListViewGroupItem child)
	{
		final String name = child.getName();

		int gs = groupsOrig.size();
		for (int i = 0; i < gs; i++)
		{
			ExpandableListViewGroup g = groupsOrig.valueAt(i);

			// search childs
			int is = g.getChildrenCount();
			for (int j = 0; j < is; j++)
			{
				final ExpandableListViewGroupItem c = g.getChild(j);
				if (name.equals(c.getName()))
				{
					g.setChild(j, child);
					notifyDataSetChanged();

					return true;
				}
			}
		}

		return false;
	}
*/

	//

	public Filter getFilter()
	{
		return filter;
	}

	private class ItemFilter extends Filter
	{
		/*
		 * this function caller handle exceptions and write it to log:
		 * W/Filter  ( 3767): An exception occured during performFiltering()!
		 * W/Filter  ( 3767): java.lang.NullPointerException
		 */
		@Override
		protected FilterResults performFiltering(CharSequence constraint)
		{
			FilterResults results = new FilterResults();
			String filterText = constraint.toString();
			String filterTextLower = constraint.toString().toLowerCase();

			if (ocvc == null)
			{
				results.values = groupsOrig;
				results.count = groupsOrig.size();
			}
			else
			{
				//groups.clear();
				SparseArray<ExpandableListViewGroup> ngs = new SparseArray<ExpandableListViewGroup>();

				int gs = groupsOrig.size();
				for (int i = 0; i < gs; i++)
				{
					int key = groupsOrig.keyAt(i);
					ExpandableListViewGroup g = groupsOrig.get(key);

					// copy group params
					ExpandableListViewGroup ng = new ExpandableListViewGroup(g.getName(), g.getRealText());

					// filter childs (in external handler)
					int is = g.getChildrenCount();
					for (int j = 0; j < is; j++)
					{
						final ExpandableListViewGroupItem child = g.getChild(j);
						if (!ocvc.onChildFilter(child, i, j, filterText, filterTextLower))
							ng.addChild(child);
					}

					//groups.append(key, ng);
					ngs.append(key, ng);
				}

				results.values = ngs;
				results.count = ngs.size();
			}

			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results)
		{
			if (results.values == null)
			{
				// ooups, something wrong!
				notifyDataSetInvalidated();
			}
			else
			{
				groups = (SparseArray<ExpandableListViewGroup>) results.values;
				notifyDataSetChanged();
			}
		}
	}

	//

	public interface OnChildListener
	{
		// first set Views listerners in external hanler! before change any values! (also see getChildView)
		void onChildView(ExpandableListViewGroupItem child, int groupPosition, int childPosition, boolean isLastChild, View childView, ViewGroup parent);

		// return true if item filtered (also see performFiltering)
		boolean onChildFilter(ExpandableListViewGroupItem child, int groupPosition, int childPosition, String filterText, String filterTextLower);

		// c1 < c2 ret < 0, c1 == c2 ret 0, c1 > c2 ret > 0 (also see sortChildren)
		int onChildCompare(ExpandableListViewGroupItem child1, ExpandableListViewGroupItem child2);
	}
}
