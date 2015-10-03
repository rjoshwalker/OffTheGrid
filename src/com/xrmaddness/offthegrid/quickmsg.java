package com.xrmaddness.offthegrid;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.util.ByteArrayDataSource;

import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPSecretKey;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class quickmsg {
	
	Boolean is_post = false;
	Boolean is_grouppost = false;
	Boolean is_group = false;
	message _msg = new message();
	contact _contact = new contact();
	int group_id = 0;
	String group_owner = "";
	
	public quickmsg()
	{
		_contact.type_set(_contact.TYPE_PERSON);
	}
	
	public attachment send_message(Context context, contact from, contact to, message message)
	{
		Log.d("message", "Send message");
		String msg = "";
		
		msg += "Action: post\n";
		msg += "Name: " + from.name_get() + "\n";
		if (to.type_get() == to.TYPE_GROUP) {
			msg += "Group: " + to.group_get() + "\n";
			msg += "Owner: " + to.address_get() + "\n";
			Log.d("send message", "Send to group " + to.group_get());
		}
		msg += "Time: " + message.time_get() + "\n";
		msg += "\n";
		msg += message.text_get();
		
		DataSource ds;
		try {
			ds = new ByteArrayDataSource(msg, "application/quickmsg");
		} catch (IOException e) {
			Log.e("message", "new ds: " + e.getMessage());
			return null;
		}
		attachment a = new attachment();
		a.datahandler = new DataHandler(ds);
		a.name = "QuickMSG";
		List<attachment> as = new LinkedList<attachment>();
		
		as.add(a);
		
		Uri uri = message.uri_get();
		if (uri != null) {
	    	Log.d("quickmsg", "send attachment: " + uri.toString());

			ContentResolver cR = context.getContentResolver();
			InputStream is;
			try {
				is = cR.openInputStream(uri);
			} catch (FileNotFoundException e) {
				Log.d("send_message", e.getMessage());
				return null;
			}
			String type = cR.getType(uri);

	    	a = new attachment();
	    	try {
				a.datahandler = new DataHandler(new ByteArrayDataSource(is, type));
			} catch (IOException e) {
				Log.d("send_message", e.getMessage());
				return null;
			}
	    	List<String> names = uri.getPathSegments();
	    	a.name = names.get(names.size()-1);
	    	
	    	as.add(a);
		}
		
		mail m = new mail();
		
		return m.multipart_create(as);
	}

	public attachment send_group(contact group, pgp pgp, quickmsg_db db)
	{
		String msg = "";
		if (group.type_get() != group.TYPE_GROUP)
			return null;
		
		msg += "Action: group\n";
		msg += "Name: " + group.name_get() + "\n";
		msg += "Group: " + group.group_get() + "\n";
		msg += "Owner: " + group.address_get() + "\n";
		msg += "Members: " + group.members_get_string() + "\n"; 
		msg += "\n";
		
		DataSource ds;
		try {
			ds = new ByteArrayDataSource(msg, "application/quickmsg");
		} catch (IOException e) {
			Log.e("message", "new ds: " + e.getMessage());
			return null;
		}
		attachment a = new attachment();
		a.datahandler = new DataHandler(ds);
		a.name = "QuickMSG";
		List<attachment> as = new LinkedList<attachment>();
		
		as.add(a);
		
		List<String>members = group.members_get();
		
		for (int i = 0; i < members.size(); i++) {
			String member = members.get(i);
			attachment ak = pgp.key_attachment(member);
			if (ak != null)
				as.add(ak);
		}
		
		mail m = new mail();
		
		return m.multipart_create(as);
	}
	
	public void parse_attachment(attachment a)
	{
		InputStream is;
		try {
			is = a.datahandler.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			
			String line;
			while ((line = br.readLine()) != null) {
				if (line.length() == 0) {
					break;
				}
				Log.d("parse_attachment", line);
				
				String[] splitted = line.split(": ");
				String field = splitted[0];
				String value = splitted[1];
				
				if (field.equals("Action")) {
					if (value.equals("post")) {
						is_post = true;
					}
					if (value.equals("group")) {
						is_group = true;
						Date now = new Date();
						long time_now = now.getTime() / 1000;

						_contact.time_lastact_set(time_now);
					}
				}
				if (field.equals("Members")) {
					_contact.members_set(value);
				}
				if (field.equals("Group")) {
					group_id = Integer.parseInt(value);
					
					is_grouppost = is_post;
					
					_contact.type_set(_contact.TYPE_GROUP);
					_contact.group_set(group_id);
				}
				if (field.equals("Owner")) {
					group_owner = value;
				}
				if (field.equals("Name")) {
					_contact.name_set(value);
				}
				if (field.equals("Time")) {
					_msg.time_set(Long.parseLong(value));
				}
			}
			if (is_post) {
				while ((line = br.readLine()) != null) {
					_msg.text_set(_msg.text_get() + "\n" + line);
				}
				
				return;
			}
		}
		catch (Exception e) {
			Log.e("parse attachment", e.getMessage());
			return;
		}
		
		return;
	}
	
	public contact get_contact()
	{
		return _contact;
	}
	
	public message get_message()
	{
		if (is_post)
			return _msg;
		else
			return null;
	}
	
	public int get_group()
	{
		return group_id;
	}
}
