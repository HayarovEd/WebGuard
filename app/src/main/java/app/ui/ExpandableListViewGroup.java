package app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ExpandableListViewGroup
{
	private final String name;
	private String text;
	private final List<ExpandableListViewGroupItem> children = new ArrayList<ExpandableListViewGroupItem>();

	/*
	 * create items group with name and text
	 * group name MUST BE unique through all groups!
	 */
	public ExpandableListViewGroup(String name, String text)
	{
		this.name = name;
		this.text = text;
	}

	public String getName()
	{
		return name;
	}

	public long getId()
	{
		return name.hashCode();
	}

//	  public ExpandableListViewGroup setText(String text)
//	  {
//		  this.text = text;
//		  return this;
//	  }

	public String getText()
	{
		return (text == null) ? name : text;
	}

	public String getRealText()
	{
		return text;
	}

	/*
	 * add item to group
	 * item name and id MUST BE unique through all groups items!
	 */
	public ExpandableListViewGroup addChild(ExpandableListViewGroupItem item)
	{
		children.add(item);
		return this;
	}

	public ExpandableListViewGroupItem getChild(int childPosition)
	{
		return children.get(childPosition);
	}

	public ExpandableListViewGroup setChild(int childPosition, ExpandableListViewGroupItem child)
	{
		children.set(childPosition, child);
		return this;
	}

	public int getChildrenCount()
	{
		return children.size();
	}

//	  public List<ExpandableListViewGroupItem> getChildren()
//	  {
//		  return children;
//	  }

	public ExpandableListViewGroup sortChildren(Comparator<ExpandableListViewGroupItem> comparator)
	{
		Collections.sort(children, comparator);
		return this;
	}

	//

	public interface ExpandableListViewGroupItem
	{
		// see addChild
		String getName();
		long   getId();
		String getText();
	}
}
