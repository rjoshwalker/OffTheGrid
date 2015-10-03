package com.xrmaddness.offthegrid;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.internet.ContentType;
import javax.mail.util.ByteArrayDataSource;


public class ListActivity extends quickmsg_activity {
	static final int SETTINGS_DONE = 1;

	quickmsg_db db = new quickmsg_db(this);
	
	pgp pgp = null;
	mail mail = new mail();

	String version_get()
	{
		String app_ver;
		try
		{
		    app_ver = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException e)
		{
			return "unknown";
		}
		return app_ver;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);
		
		Log.d("onCreate", "Version: " + version_get());

	    view_contacts();
	    
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

	    String email_address = SP.getString("email_address", null);

	    create_pgp(email_address);
	    

	    check_background();

	    /* User entered the app, cancel all notifications (also if user did not get in
	     * via the notification) 
	     */
	    NotificationManager notification_mgr =
			    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notification_mgr.cancelAll();
	}

	
		
	public void check_background()
	{
	    //here, check that the network connection is available. If yes, start your service. If not, stop your service.
	    ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo info = cm.getActiveNetworkInfo();
	    Intent bg;

	    if (info != null) {
	    	if (info.isConnected()) {
	    		Log.d("list act netwatcher", "connection, start service");
	    		bg = new Intent(this, background.class);
	    		bg.putExtra("state", 1);
       	       	this.startService(bg);
       	       	return;
	    	}
	    }
	    Log.d("list act netwatcher", "no connection, stop service");
	    bg = new Intent(this, background.class);
	    bg.putExtra("state", 0);
       	this.startService(bg);
       	return;
	}
	
	public void on_update_ui()
	{
		view_contacts();
	}
	
	private void create_pgp(String email_address)
	{
		Log.d("create_pgp", "Create pgp if email_address set");
	    if (email_address != null) {
	    	pgp = new pgp(this.getApplication());
	    } else {
			warn_settings_dialog();
	    }
	}

	public void add_contact(String contact_address)
	{
		Date now = new Date();
		long time_now = now.getTime() / 1000;
		
		/* Don't add ourself */
		if (contact_address.equals(pgp.my_user_id))
			return;
		if (!android.util.Patterns.EMAIL_ADDRESS.matcher(contact_address).matches())
			return;
		
		contact contact = new contact();
		contact.address_set(contact_address);
		contact.type_set(contact.TYPE_PERSON);
		contact.time_lastact_set(time_now);
		
		db.contact_add(contact);
		
		send_key(contact);
		view_contacts();
	}

	public void add_group(String name)
	{
		String me = pgp.my_user_id;
		Date now = new Date();
		long time_now = now.getTime() / 1000;
		
		name = name.split("\n")[0];

		List<String> members = new LinkedList<String>();
		members.add(me);
		
		contact contact = new contact();
		contact.address_set(me);
		contact.name_set(name);
		contact.type_set(contact.TYPE_GROUP);
		contact.time_lastact_set(time_now);
		contact.members_set(members);
		
		int group;
		contact ce;
		Random r = new Random();
		do {
			group = r.nextInt(32768);
			ce = db.contact_get_group_by_address_and_group(me, group);
		} while(ce != null);
		Log.d("add_group", "Group: " + me + " " + group);
		contact.group_set(group);
		
		db.contact_add(contact);
		
		view_contacts();
	}
	
	
	public void send_key(contact contact)
	{
		final Context c = this;
		final contact sendcontact = contact;
		
        new Thread(new Runnable() {
            public void run() {
        		attachment attachment = pgp.key_attachment(pgp.my_user_id);
        		
        		List<attachment> attachments = new LinkedList<attachment>();
        		attachments.add(attachment);
        		
        		
        		mail.send(c, sendcontact.address_get(), attachments);
        		Log.d("send_key", "done");
            }
        }).start();
	}

	public void view_contacts()
	{
		ListView listview= (ListView)findViewById(R.id.contact_list);
		ArrayAdapter<Spanned> contacts_adapter;
		ArrayList<Spanned> contacts_list;
		contacts_list = new ArrayList<Spanned>();

		final List<contact> contacts = db.contact_get_all();
		
		contacts_list.clear();

		for (int i = 0; i < contacts.size(); i++) {
			long time_lastact;
			Date lastact;
			Spanned view_span;
			contact contact = contacts.get(i);
			String view_html;
			long unread = 0;
			
			time_lastact = contact.time_lastact_get() * 1000;
			lastact = new java.util.Date(time_lastact);
			SimpleDateFormat dateformat = new SimpleDateFormat();
			
			String name_start = "";
			String name_end = "";

			if (contact.time_lastact_get() > contact.unread_get()) {
				name_start = "<b>";
				name_end = "</b>";
				
				unread = db.message_get_count_by_id(
				    contact.id_get(), contact.unread_get());
			}
			
			
			view_html = "<font ";
			if (contact.type_get() == contact.TYPE_PERSON) {
				Log.d("list", "person");
				switch (contact.keystat_get()) {
					case com.xrmaddness.offthegrid.contact.KEYSTAT_NONE:
						Log.d("list", "keystat none");
						view_html += "color='#990000'";
						break;
					case com.xrmaddness.offthegrid.contact.KEYSTAT_RECEIVED:
						Log.d("list", "keystat received");
						view_html += "color='#cc6600'";
						break;
					case com.xrmaddness.offthegrid.contact.KEYSTAT_VERIFIED:
						Log.d("list", "keystat verified");
						view_html += "color='#004400'";
						break;
				}
			}
			view_html += ">";
			view_html += name_start + contact.name_get() + name_end + "<br><small>";
			if (contact.type_get() == contact.TYPE_GROUP) {
				String owner = contact.address_get();
				List<String> members = contact.members_get();
				
				for (int j = 0; j < members.size(); j++) {
					String member = members.get(j);
					if (member.equals(owner)) {
						view_html += "<i>" + member + "</i> ";
						
					} else {
						view_html += member + " ";
					}
				}
			} else {
				view_html += "<i>" + contact.address_get() + "</i> ";
			}
			view_html += "<br>";
			view_html += dateformat.format(lastact);
			view_html += "</small></font>";
			
			/* add number of unread messages */
			if (unread > 0) {
				view_html += "<small><font color='#00cc00'> (" + unread + ")</font></small>";
			}
			
			view_span = Html.fromHtml(view_html);
			
			contacts_list.add(view_span);
		}
		
		
	    contacts_adapter = new ArrayAdapter<Spanned>(this,
		        android.R.layout.simple_list_item_1, contacts_list);

	    listview.setAdapter(contacts_adapter);


	    contacts_adapter.notifyDataSetChanged();
	    listview.setAdapter(contacts_adapter);
	    
	    listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int position,
					long id) {
				final contact c = contacts.get(position);
				
				if (c.type_get() == contact.TYPE_GROUP) {
					contact_view(c);
				}
				if (c.type_get() == contact.TYPE_PERSON) {
					switch (c.keystat_get()) {
						case contact.KEYSTAT_NONE: {
							Log.d("contact", "ask action");
						    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
						    CharSequence[] items = { 
						    		getString(R.string.action_send_key),
						    		getString(R.string.action_remove)};
						    builder.setTitle(R.string.dialog_contact)
						           .setItems(items, new DialogInterface.OnClickListener() {
						               public void onClick(DialogInterface dialog, int which) {
						            	   Log.d("contact", "which: " + which);
						            	   if (which == 0) {
						            		   Log.d("contact", "send_key_dialg(c)");
						            		   send_key_dialog(c);
						            	   }
						            	   if (which == 1) {
						            		   Log.d("contact", "remove(c)");
						            		   remove(c);
						            	   }
						           }
						    });
						    builder.show();
						    break;
						}
						case contact.KEYSTAT_RECEIVED: {
							Log.d("contact", "ask action");
						    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
						    CharSequence[] items = { 
						    		getString(R.string.action_verify), 
						    		getString(R.string.action_send_key),
						    		getString(R.string.action_remove)};
						    builder.setTitle(R.string.dialog_contact)
						           .setItems(items, new DialogInterface.OnClickListener() {
						               public void onClick(DialogInterface dialog, int which) {
						            	   Log.d("contact", "which: " + which);
						            	   if (which == 0) {
						            		   Log.d("contact", "verify_dialog(c)");
						            		   verify_dialog(c);
						            	   }
						            	   if (which == 1) {
						            		   Log.d("contact", "send_key_dialg(c)");
						            		   send_key_dialog(c);
						            	   }
						            	   if (which == 2) {
						            		   Log.d("contact", "remove(c)");
						            		   remove(c);
						            	   }
						           }
						    });
						    builder.show();
						    break;
						}
						case contact.KEYSTAT_VERIFIED: {
							contact_view(c);

						}
					}
				}
			}
		});

	    listview.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View view,
					int position, long id) {
				final contact c = contacts.get(position);

				if (c.type_get() == contact.TYPE_GROUP) {
					Log.d("contact", "ask action");
					AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
					CharSequence[] items = { 
			    		getString(R.string.action_set_member),
			    		getString(R.string.action_set_name),
			    		getString(R.string.action_remove)
					    };
					builder.setTitle(R.string.dialog_contact)
						.setItems(items, new DialogInterface.OnClickListener() {
			               public void onClick(DialogInterface dialog, int which) {
			            	   Log.d("contact", "which: " + which);
			            	   if (which == 0) {
			            		   Log.d("group", "set member");
			            		   set_member_dialog(c);
			            	   }
			            	   if (which == 1) {
			            		   Log.d("group", "set name");
			            		   group_name_dialog(c);
			            	   }
			            	   if (which == 2) {
			            		   Log.d("group", "remove(c)");
			            		   remove(c);
			            	   }
			               }
			           });
					builder.show();
					return true;
				
				}
				if (c.type_get() == contact.TYPE_PERSON) {
					switch (c.keystat_get()) {
						case contact.KEYSTAT_VERIFIED: {
							Log.d("contact", "ask action");
							AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
							CharSequence[] items = { 
					    		getString(R.string.action_send_key),
					    		getString(R.string.action_fingerprint),
					    		getString(R.string.action_remove)
							    };
							builder.setTitle(R.string.dialog_contact)
								.setItems(items, new DialogInterface.OnClickListener() {
					               public void onClick(DialogInterface dialog, int which) {
					            	   Log.d("contact", "which: " + which);
					            	   if (which == 0) {
					            		   Log.d("contact", "send_key_dialg(c)");
					            		   send_key_dialog(c);
					            	   }
					            	   if (which == 1) {
					            		   view_fingerprint_dialog(c);
					            	   }
					            	   if (which == 2) {
					            		   Log.d("contact", "remove(c)");
					            		   remove(c);
					            	   }
					               }
					           });
							builder.show();
							return true;
						}
					}
				}
				return false;
			}
		});

	}

	void contact_view(contact c)
	{
		Intent intent = new Intent(this, MainActivity.class);
		intent.putExtra("id", c.id_get());
	    startActivity(intent);
	}
	
	public void group_update(contact c)
	{
		db.contact_update(c);
		
		quickmsg qm = new quickmsg();
		attachment qma = qm.send_group(c, pgp, db);

   		List<String> to_adds;
   		to_adds = c.members_get();

   		attachment id = pgp.pgpmime_id();

   		for (int i = 0; i < to_adds.size(); i++ ) {
   			String to = to_adds.get(i);
   			if (to.equals(pgp.my_user_id))
   				continue;

   			attachment enc = pgp.encrypt_sign(qma, to);
   	   		enc.disposition = "inline";
   	   		
   	   		Log.d("group_update", "message encrypted for: " + to);
   			mail mail = new mail();
   			List<attachment> attachments = new LinkedList<attachment>();
   			attachments.add(id);
   			attachments.add(enc);
		
   			mail.send(this, to, attachments, "encrypted");
   		}
		Log.d("group_update", "mail.send done");

		view_contacts();
	}
	
	void set_member_dialog(final contact c)
	{
		if (!c.address_get().equals(pgp.my_user_id)) {
			Log.d("set_member_dialog", "not my group");
			return;
		}
		
		List<String> old_members = c.members_get();
		
		final List<contact> persons = db.contact_get_by_type(contact.TYPE_PERSON);
		List<CharSequence> names = new ArrayList<CharSequence>();
		final boolean[] checked = new boolean[persons.size()];

	    for (int i = 0; i < persons.size(); i++) {
			contact p = persons.get(i);
	    	String add = p.address_get();
			String name = p.name_get() + " " + add;
			boolean check = false;
			
			for (int j = 0; j < old_members.size(); j++) {
				if (add.equals(old_members.get(j))) {
					check = true;
				}
			}
			
			names.add(name);
			checked[i] = check;
		}
		

	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    // Set the dialog title
	    builder.setTitle(R.string.action_set_member);
	    // Specify the list array, the items to be selected by default (null for none),
	    // and the listener through which to receive callbacks when items are selected
	    builder.setMultiChoiceItems(
	    		names.toArray(new CharSequence[names.size()]), 
	    		checked,
	            new DialogInterface.OnMultiChoiceClickListener() {
	    	@Override
	        public void onClick(DialogInterface dialog, int which,
	                       boolean isChecked) {
	    		checked[which] = isChecked;
	        }
	    });
	    // Set the action buttons
	    builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
            		List<String> members = new LinkedList<String>();
          		
            		for (int i = 0; i < persons.size(); i++) {
            			/* add all members (and ourself) */
            			if (i == 0 || checked[i]) {
            				if (persons.get(i).keystat_get() == contact.KEYSTAT_VERIFIED)
            					members.add(persons.get(i).address_get());
            			}
            		}
            		
            		c.members_set(members);
            		group_update(c);
	        }
	    });
	    
	    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	    	@Override
	        public void onClick(DialogInterface dialog, int id) {
	    		return;
	        }
	    });
	    builder.show();
		
	}

	void group_name_dialog(final contact c)
	{
		if (!c.address_get().equals(pgp.my_user_id)) {
			Log.d("set_name_dialog", "not my group");
			return;
		}

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_group_name);
    	alert.setMessage(R.string.dialog_group_name);

    	// Set an EditText view to get user input 
    	final EditText input = new EditText(this);
    	input.setInputType(InputType.TYPE_CLASS_TEXT);
    	alert.setView(input);

    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String value = input.getText().toString();

    			c.name_set(value);
    			
    			group_update(c);
       		}
    	});

    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    		}
    	});

    	alert.show(); 

	}

	void send_key_dialog(final contact c)
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_send_key);
    	alert.setMessage(R.string.dialog_send_key);
    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {

    			Log.d("resend key", "resend key");
    			send_key(c);
       		}
    	});
    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    		}
    	});
        alert.setCancelable(true);
        alert.show();
	}
	
	void warn_settings_dialog()
	{
    	LayoutInflater factory = LayoutInflater.from(this);

    	final View textEntryView = factory.inflate(R.layout.gmail_account, null);

    	final EditText input_name = (EditText) textEntryView.findViewById(R.id.gmail_name);
   	 	final EditText input_address = (EditText) textEntryView.findViewById(R.id.gmail_address);
    	final EditText input_pass = (EditText) textEntryView.findViewById(R.id.gmail_pass);

    	input_name.setText("", TextView.BufferType.EDITABLE);
    	input_address.setText("", TextView.BufferType.EDITABLE);
    	input_pass.setText("", TextView.BufferType.EDITABLE);

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_warn_settings);
    	alert.setMessage(getString(R.string.dialog_warn_settings));
    	alert.setView(textEntryView);
    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String name = input_name.getText().toString();
    			String address = input_address.getText().toString();
    			String pass = input_pass.getText().toString();
    			if (name.length() <= 0 ||
    				address.length() <= 0 ||
    				pass.length() <= 0) {
    					return;
    			}
    			
    			settings_set_gmail(name, address, pass);
       		}
    	});
    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    		}
    	});
        alert.setCancelable(true);
		alert.show();		
	}
	
	void settings_set_gmail(String name, String address, String pass)
	{
		/* we got a gmail account user, fill in the blanks with server info
		 * and commit it
		 */
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		Editor editor = SP.edit();
	    
		editor.putString("display_name", name);
		editor.putString("email_address", address);
		editor.putString("imap_user", address);
		editor.putString("smtp_user", address);
		editor.putString("imap_pass", pass);
		editor.putString("smtp_pass", pass);

		editor.putString("imap_server", "imap.gmail.com");
		editor.putString("imap_port", "993");
		editor.putString("smtp_server", "smtp.gmail.com");
		editor.putString("smtp_port", "587");
		editor.commit();
		
		settings_done();
	}
	
	void view_fingerprint_dialog(contact c)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_fingerprint);
    	alert.setMessage(getString(R.string.dialog_fingerprint) + " " + 
    			c.name_get() +" "+ c.address_get() + ": " +
    			pgp.fingerprint(c.address_get()));
    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
       		}
    	});
		alert.show();		
	}
	
	void verify_dialog(final contact c)
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_verify);
    	alert.setMessage(getString(R.string.dialog_verify) + ": " +
    			pgp.fingerprint(c.address_get()));
    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {

    			Log.d("verify_dialog", "verified contact");
    			c.keystat_set(c.KEYSTAT_VERIFIED);
    			db.contact_update(c);
    			view_contacts();
    			send_key(c);
       		}
    	});
    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    		}
    	});
        alert.setCancelable(true);
		alert.show();
	}

	void warn_remove_self()
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_remove_self);
    	alert.setMessage(getString(R.string.dialog_remove_self));
    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    		}
    	});
        alert.setCancelable(true);
		alert.show();		
	}
	
	void remove(final contact c)
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.action_remove);
    	alert.setMessage(getString(R.string.dialog_remove) + ": " +
    			c.name_get());
    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String address = c.address_get();
    			
    			if (c.id_get() == 1) {
    				warn_remove_self();
    				return;
    			}
    			Log.d("remove", "remove contact");
    			if (c.type_get() == contact.TYPE_PERSON) {
    				List<contact> contacts = db.contact_get_by_address(address);
    				for (int i = 0; i < contacts.size(); i++)
    					db.contact_remove(contacts.get(i));
    				if (!address.equals(pgp.my_user_id))
    					pgp.public_keyring_remove_by_address(address);
    			} else {
    				db.contact_remove(c);
    			}
    			
    			view_contacts();
       		}
    	});
    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    		}
    	});
        alert.setCancelable(true);
		alert.show();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.list, menu);
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

        case R.id.action_settings:

            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, SETTINGS_DONE);

            return true;

        case R.id.action_my_fingerprint: {
        	String fingerprint;
            if (pgp == null) {
            	fingerprint = "No keys available, fill in email address first in settings";
            } else {
            	fingerprint = pgp.fingerprint(pgp.my_user_id);
            	if (fingerprint == null) {
            		fingerprint = "No key found";
            	}
            }
            
            AlertDialog.Builder alert  = new AlertDialog.Builder(this);

            alert.setMessage(fingerprint);
            alert.setTitle(R.string.action_my_fingerprint);
            alert.setPositiveButton("OK", null);
            alert.setCancelable(true);
            alert.create().show();
            
        	return true;
        }

        case R.id.action_add_contact: {
        	if (pgp == null)
        		return true;
        	
        	AlertDialog.Builder alert = new AlertDialog.Builder(this);

        	alert.setTitle(R.string.action_add_contact);
        	alert.setMessage(R.string.dialog_add_contact);

        	// Set an EditText view to get user input 
        	final EditText input = new EditText(this);
        	input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        	alert.setView(input);

        	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
        			String value = input.getText().toString();
        			
        			Log.d("add_contact", value);
        			add_contact(value);
           		}
        	});

        	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
        		}
        	});

        	alert.show();
        	return true;
        }
        case R.id.action_add_group: {
        	if (pgp == null)
        		return true;
        	
        	AlertDialog.Builder alert = new AlertDialog.Builder(this);

        	alert.setTitle(R.string.action_add_group);
        	alert.setMessage(R.string.dialog_add_group);

        	// Set an EditText view to get user input 
        	final EditText input = new EditText(this);
        	input.setInputType(InputType.TYPE_CLASS_TEXT);
        	alert.setView(input);

        	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
        			String value = input.getText().toString();
        			
        			Log.d("add_group", value);
        			add_group(value);
           		}
        	});

        	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
        		}
        	});

        	alert.show();
        	return true;
        }
        	 
        case R.id.action_show_license: {
        	String license = version_get() + "\n";

        	AssetManager am = getAssets();
        	InputStream is;
			try {
				is = am.open("license.txt");
	        	license += filestring.is2str(is);
	        	is.close();

	        	is = am.open("spongycastle_license.txt");
	        	license += filestring.is2str(is);
	        	is.close();

	        	is = am.open("javamail_license.txt");
	        	license += filestring.is2str(is);
	        	is.close();
			} catch (IOException e) {
				Log.e("show license", e.getMessage());
			}
        	
    
            AlertDialog.Builder alert  = new AlertDialog.Builder(this);

            alert.setMessage(license);
            alert.setTitle(R.string.action_show_license);
            alert.setPositiveButton("OK", null);
            alert.setCancelable(true);
            alert.create().show();
            
        	return true;
        	
        }
        
        default:
            return super.onOptionsItemSelected(item);
        }
    }    

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    Log.d("listactivity", "onActivityResult");

	    if (requestCode == SETTINGS_DONE) {
	    	Log.d("listactivity", "requestCode == SETTINGS_DONE");
	    	settings_done();
	    }
	}
	
	void settings_done()
	{
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

	    String email_address = SP.getString("email_address", null);
	    if (email_address == null)
	    	return;
	    
	    contact me_org = db.contact_get_by_id(1);
	    contact me;
	    
	    if (me_org != null) {
	    	Log.d("settings done", "contact with id 1 exists: " + me_org.id_get());
	    	me = me_org;
	    } else {
	    	Log.d("settings done", "no contact with id 1 yet");
	    	me = new contact();
	    }
	    
	    me.id_set(1);
	    me.name_set(SP.getString("display_name", null));
	    me.address_set(email_address);

	    Date now = new Date();
		long time_now = now.getTime() / 1000;

		me.type_set(contact.TYPE_PERSON);
		me.time_lastact_set(time_now);
		me.keystat_set(contact.KEYSTAT_VERIFIED);

		if (me_org == null) {
	    	db.contact_add(me);
	    } else {
	    	db.contact_update(me);
	    }
	    
		create_pgp(email_address);
    	
    	view_contacts();
    	
    	check_background();
	}


	
	
	
	
	
	
	private BroadcastReceiver connection_receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			on_update_ui();
		}
	};

	@Override
	protected void onResume() {
	  super.onResume();
	  IntentFilter filter = new IntentFilter();
	  filter.addAction("com.xrmaddness.offthegrid.update_ui");
	  this.registerReceiver(connection_receiver, filter);
	  

	  LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
			      new IntentFilter("local_connection"));
		
	}

	@Override
	protected void onPause() {
	  super.onPause();

	  this.unregisterReceiver(this.connection_receiver);
	  LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		  @Override
		  public void onReceive(Context context, Intent intent) {
		    // Get extra data included in the Intent
		    String message = intent.getStringExtra("message");
		    boolean status;
		    
		    if (message.contains("T"))
		    	status = true;
		    else
		    	status = false;
		    
		    Log.d("receiver", "Got connection status: " + (status ? "True" : "False"));

		    TextView textview= (TextView)findViewById(R.id.connection_status);
		    textview.setText("Connection status: " + 
		        (status ? "Connected" : "Disconnected"));
		  }
	};

}
