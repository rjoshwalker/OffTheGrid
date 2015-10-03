package com.xrmaddness.offthegrid;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;
import android.util.Log;

public class contact {
	public static final int TYPE_PERSON = 0;
	public static final int TYPE_GROUP = 1;
	
	public static final int KEYSTAT_NONE = 0;
	public static final int KEYSTAT_RECEIVED = 1;
	public static final int KEYSTAT_VERIFIED = 2;
	
	int _id;
	int _type;
	int _keystat;
	String _name;
	long _time_lastact;
	String _address; // for group this is the owner
	List<String> _members; // only for group
	long _unread;
	int _group;
	
	public contact()
	{}
	
	public contact(int type, int keystat, String address, String name, long time_lastact)
	{
		type_set(type);
		keystat_set(keystat);
		address_set(address);
		if (name != null)
			name_set(name);
		time_lastact_set(time_lastact);
	}
	public contact(int type, int keystat, String owner, String name, List<String> members, long time_lastact, int group)
	{
		type_set(type);
		keystat_set(keystat);
		address_set(owner);
		members_set(members);
		group_set(group);
		if (name != null)
			name_set(name);
		time_lastact_set(time_lastact);
	}
	public contact(int type, int keystat, String owner, String name, String members, long time_lastact)
	{
		type_set(type);
		keystat_set(keystat);
		address_set(owner);
		members_set(members);
		if (name != null)
			name_set(name);
		time_lastact_set(time_lastact);
	}
	
	public void type_set(int type)
	{
		_type = type;
	}
	public int type_get()
	{
		return _type;
	}
	public void keystat_set(int keystat)
	{
		_keystat = keystat;
	}
	public int keystat_get()
	{
		return _keystat;
	}
	public void address_set(String address)
	{
		_address = address;
		if (_name == null)
		{
			_name = address;
		}
	}
	public String address_get()
	{
		return _address;
	}
	public void name_set(String name)
	{
		_name = name;
	}
	public String name_get()
	{
		if (_name == null) {
			Log.e("contact", "name == null");
			return "";
		}
		return _name;
	}
	public void time_lastact_set(long time_lastact)
	{
		_time_lastact = time_lastact;
	}
	public long time_lastact_get()
	{
		return _time_lastact;
	}
	public void members_set(List<String> members)
	{
		_members = members;
	}
	public void members_set(String members)
	{
		if (members == null) {
			Log.e("contact", "members_set(null)");
			return;
		}
			
		_members = new ArrayList<String>(Arrays.asList(members.split(", ")));
	}
	public List<String> members_get()
	{
		return _members;
	}
	public String members_get_string()
	{
		if (_members == null)
			return "";
		return TextUtils.join(", ", _members);
	}

	public int id_get()
	{
		return _id;
	}
	public void id_set(int id)
	{
		_id = id;
	}
	
	public long unread_get()
	{
		return _unread;
	}
	public void unread_set(long unread)
	{
		_unread = unread;
	}
	
	public int group_get()
	{
		return _group;
	}
	public void group_set(int group)
	{
		_group = group;
	}
	
	public String toString()
	{
		if (_address == null)
			return "address==null";
		
		return "id =" + _id +", " +
				"address=" + _address + ", " +
				"type=" + _type + ", " +
				"keystat=" + _keystat + ", " +
				"name=" + ((_name == null) ? "null" : _name) + ", " +
				"members=" + ((_members == null) ? "null" : members_get_string()) + ", " +
				"goup=" + _group + ", " +
				"unread=" + _unread + ", " +
				"time_lastact=" + _time_lastact;
	}
}
