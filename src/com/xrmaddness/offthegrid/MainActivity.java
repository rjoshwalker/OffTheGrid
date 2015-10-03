package com.xrmaddness.offthegrid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.util.*;



public class MainActivity extends quickmsg_activity {

	quickmsg_db db = new quickmsg_db(this);
    contact contact;
    TextView msg_viewer;
	
    final static int MAXVIEW_INIT = 10;
    final static int MAXVIEW_ADD = 5;
    int maxview = MAXVIEW_INIT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = getIntent();
        int id;
        String action = intent.getAction();
    	EditText new_msg = (EditText)findViewById(R.id.new_msg);
    	
    	new_msg.setText("");
    	maxview = MAXVIEW_INIT;
    	msg_viewer = (TextView)findViewById(R.id.msg_viewer);
    	
        if (Intent.ACTION_SEND.equals(action)) {
        	Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
            	select_contact(this, uri);
            }
        } else {
            id = intent.getIntExtra("id", -1);        
            Log.d("main actvitity", "id: " + id);
            
            contact = db.contact_get_by_id(id);
        }
        if (contact == null)
        	return;
        
        setTitle(getString(R.string.title_activity_main) + " - " + contact.name_get());
        
        display_contact();
    }
    
    public void select_contact(final Context context, final Uri uri)
    {
		final List<contact> contactlist = db.contact_get_sendable();
		List<CharSequence> names = new ArrayList<CharSequence>();

	    for (int i = 0; i < contactlist.size(); i++) {
			contact p = contactlist.get(i);
			String add = p.address_get();	
			String name = p.name_get() + " (" + add + ")";
			
			names.add(name);
		}

	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    // Set the dialog title
	    builder.setTitle(R.string.action_select_contact);
	    // Specify the list array, the items to be selected by default (null for none),
	    // and the listener through which to receive callbacks when items are selected
	    builder.setSingleChoiceItems(
	    		names.toArray(new CharSequence[names.size()]),
	    		-1,
	            new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				contact = contactlist.get(which);
				Log.d("select contact", "which: " + which);
			}
	    });
	    // Set the action buttons
	    builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
            	Log.d("select contact", "OK " + (contact == null ? "null" : "contact"));
            	if (contact != null) {
            		Log.d("onresume", "going to send message");
            		send_msg_attachment(context, uri);
            	}
            	finish();
            	return;
	        }
	    });
	    
	    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	    	@Override
	        public void onClick(DialogInterface dialog, int id) {
	    		Log.d("select contact", "cancel");
	    		contact = null;
	    		finish();
	    		return;
	        }
	    });
	    builder.show();
    }
    
    public void on_update_ui()
    {
    	if (contact == null)
    		return;
    	display_contact();
    }

/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

        case R.id.action_settings:

            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);

            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }    
*/
    String viewer_html = "";
    
    /** Called when the user clicks the Send button */
    public void send_msg(View view)
    {
    	send_msg_attachment(view.getContext(), null);
		
   		display_contact();
    }
    
    public void send_msg_attachment(final Context context, Uri uri) {
    	EditText new_msg = (EditText)findViewById(R.id.new_msg);
    	final attachment unenc;
    	
    	Date now = new Date();
		long time_now = now.getTime() / 1000;

		message m = new message();
		m.id_set(contact.id_get());
		m.from_set(1);
		m.time_set(time_now);
		m.text_set(new_msg.getText().toString());
		if (uri != null) {
			m.uri_set(uri);
		}
		
		db.message_add(m);
		
   		//new_mail.send(this);
   		Log.d("send_msg", "send to " + contact.name_get() + "id: " + contact.id_get());
   	
   		quickmsg qmsg = new quickmsg();
   		unenc = qmsg.send_message(this, db.contact_get_by_id(1), contact, m);
   		
   		Log.d("send_msg", "got message unencrypted");
   		
   		
   		final List<String> to_adds;
   		if (contact.type_get() == contact.TYPE_GROUP)
   			to_adds = contact.members_get();
   		else {
   			to_adds = new LinkedList<String>();
   			to_adds.add(contact.address_get());
   		}

   		//final Context context = c;
   		
        new Thread(new Runnable() {
            public void run() {
            	pgp pgp_enc = new pgp(context);
           		
           		attachment id = pgp_enc.pgpmime_id();

           		for (int i = 0; i < to_adds.size(); i++ ) {
           			String to = to_adds.get(i);
           			if (to.equals(pgp.my_user_id))
           				continue;

           			attachment enc = pgp_enc.encrypt_sign(unenc, to);
           	   		if (enc == null)
           	   			continue;
           	   		enc.disposition = "inline";
           	   		
           	   		Log.d("send_msg", "got message encrypted");
           			mail mail = new mail();
           			List<attachment> attachments = new LinkedList<attachment>();
           			attachments.add(id);
           			attachments.add(enc);
        		
           			mail.send(context, to, attachments, "encrypted");
           		}
        		Log.d("send_msg", "mail.send done");            	
            }
        }).start();
        
        new_msg.setText("");
    }
    
    public void display_contact() {
    	List<message> messages = db.message_get_by_id(contact.id_get(), maxview + 1);
    	Boolean unread = false;
    	Boolean more;
    	
    	if (messages.size() > maxview) {
    		messages.remove(0);
    		more = true;
    	} else {
    		more = false;
    	}
    	
    	viewer_html = "";
    	
    	for (int i = 0; i < messages.size(); i++) {
    		message m = messages.get(i);
    		contact from = db.contact_get_by_id(m.from_get());
    		if (from == null) {
    			Log.d("display contact", "from == null" + m.from_get());
    			from = new contact();
    		}
    		
    		if (m.time_get() > contact.unread_get()) {
    			if (!unread)
    				viewer_html += "<p><b><i>New:</i></b></p>";
    			unread = true;
    			contact.unread_set(m.time_get());
    		}
    		
    		long time_ms = m.time_get() * 1000;
			Date time_d = new java.util.Date(time_ms);
			SimpleDateFormat dateformat = new SimpleDateFormat();
				
    		viewer_html += "<p>" +
    				"<font color='#808080'><b>" + from.name_get()+ "</b>" +
    				"<i> " + dateformat.format(time_d) + "</i>" +
    				"</font>" +
    				"<br>" +
    				m.text_get();
    		
    		Uri uri = m.uri_get();
    		if (uri != null) {
    			ContentResolver cR = getContentResolver();
    			String fulltype;
    			boolean uri_fault = false;
    			try {
    				fulltype = cR.getType(uri);
    			} 
    			catch (IllegalStateException e) {
    				Log.e("display contact", "could not get full type");
    				fulltype = "unknown type";
    				uri_fault = true;
    			}
    			boolean handled = false;
    			String handled_string = "";

    			if (fulltype != null) {
        			String type = fulltype.split("/")[0].toLowerCase();
 //   				Log.d("display contact", "fulltype: " + fulltype + ", type: " + type);
        			
        			if (type.equals("image")) {
        				handled_string = "<img src='image#" + uri.toString() + "'>";
        				handled = true;
        			}
        			if (type.equals("video")) {
        				handled_string = "<img src='video#" + uri.toString() + "'>";
        				handled = true;
        			}
        			if (type.equals("audio")) {
        				handled_string = "<img src='audio#" + uri.toString() + "'>";
        				handled = true;
        			}
    			}

    			if (uri_fault)
    				handled = false;
    		
       			if (!handled) {
       				viewer_html += "<br>" + fulltype + "<br>";
       			} else {
       				Intent intent = new Intent(Intent.ACTION_VIEW);
       				intent.setData(uri);
       				List<ResolveInfo> ia = this.getPackageManager().queryIntentActivities(intent, 0);
       		        if (ia.size() > 0) {
            			viewer_html += "<a href='" + uri.toString() + 
            					"'>" +
            					(handled ? handled_string : uri.toString()) +
            					"</a>";
       		        } else {
       		        	handled = false;
       		        }
       			}
       			if (!handled) {
    		       	viewer_html += uri.toString();
    		    }
    		}
    		
    		viewer_html += "<p>";
    	}
    	if (contact.time_lastact_get() > contact.unread_get()) {
    		contact.unread_set(contact.time_lastact_get());
    	}
    	db.contact_update(contact);
    	
    	final Context context = this;
    	DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    	final int maxw = metrics.widthPixels / 2 + 1;
    	final int maxh = metrics.heightPixels / 2 + 1;
    	
    	Spanned span = Html.fromHtml(viewer_html, new Html.ImageGetter() {
    		  @Override
    		  public Drawable getDrawable(final String img_source) {
    			  String source_type = img_source.split("#")[0];
    			  String source = img_source.split("#")[1];
//    			  Log.d("getdrawable", "img_source: " +img_source+ "source_type: " + source_type + " source: " + source);
    			  Bitmap bm;
    			  
    			  if (source_type.equals("image")) {
    				  BitmapFactory.Options options = new BitmapFactory.Options();
    			  
    				  InputStream is;
    				  try {
    					  is = getContentResolver().openInputStream(Uri.parse(source));
    				  } catch (FileNotFoundException e) {
    					  Log.d("getDrawable", e.getMessage());
    					  return null;
    				  }
    			  
    				  options.inJustDecodeBounds = true;
    				  BitmapFactory.decodeStream(is, null, options);
    				  int h = options.outHeight;
    				  int w = options.outWidth;
    			  
    				  int scaleh = (maxh + h) / maxh;
    				  int scalew = (maxw + w) / maxw;
    			  
    				  options.inSampleSize = Math.max(scaleh, scalew);
    				  options.inJustDecodeBounds = false;

    				  try {
    					  is = getContentResolver().openInputStream(Uri.parse(source));
    				  } catch (FileNotFoundException e) {
    					  Log.d("getDrawable", e.getMessage());
    					  return null;
    				  }

    				  bm = BitmapFactory.decodeStream(is, null, options);
    			  } else if (source_type.equals("video")) {
    				  String[] projection = { MediaStore.Video.Media._ID };
    				  String result = null;
    				  Cursor cursor = getContentResolver().query(Uri.parse(source), projection, null, null, null);

    				  int column_index = cursor
    				            .getColumnIndexOrThrow(MediaStore.Video.Media._ID);

    				  cursor.moveToFirst();
    				  long video_id = cursor.getLong(column_index);
    				  cursor.close();
    				  
    				  ContentResolver crThumb = getContentResolver();

    				  bm = MediaStore.Video.Thumbnails.getThumbnail(crThumb,video_id, MediaStore.Video.Thumbnails.MICRO_KIND, null);
    			  } else if (source_type.equals("audio")) {
    				  Log.d("get drawable", "audio uri");
    				  Drawable dp = getResources().getDrawable(R.drawable.play);
    				  if (dp == null) {
    					  return null;
    				  }
        			  dp.setBounds(0, 0, dp.getIntrinsicWidth(), dp.getIntrinsicHeight());
    				  return dp;
    			  } else {
    				  Log.d("get drawable", "unknown source type: " + source_type);
    				  return null;
    			  }

				  Drawable d = new BitmapDrawable(getResources(), bm);
    			  d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
    			  
    			  return d;
    		  }
    	}, null);
    	
    	if (more) {
        	Spannable span_more = new SpannableString("view more messages\n\n");
        	span_more.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                	maxview += MAXVIEW_ADD;
                	Log.d("view contacts", "new maxview: " + maxview);
                	display_contact();
                }
            }, 0, span_more.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        	        	
        	span = (Spanned) TextUtils.concat(span_more, span);
    	}
    	
        msg_viewer.setText(span);
        msg_viewer.setMovementMethod(LinkMovementMethod.getInstance());
 
       final ScrollView scrollview = ((ScrollView) findViewById(R.id.scrollView1));
        scrollview.post(new Runnable() {
            @Override
            public void run() {
            	if (maxview > MAXVIEW_INIT)
            		scrollview.fullScroll(ScrollView.FOCUS_UP);
            	else
            		scrollview.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }
}


