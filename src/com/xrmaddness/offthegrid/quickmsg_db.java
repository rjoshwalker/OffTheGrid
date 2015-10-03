package com.xrmaddness.offthegrid;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class quickmsg_db extends SQLiteOpenHelper {
 
    // Database Version
    private static final int DATABASE_VERSION = 2;
    // Database Name
    private static final String DATABASE_NAME = "QuickMSG_DB";
 
    public quickmsg_db(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION); 
    }
 
    private static final String TABLE_CONTACT = "contacts";
    private static final String KEY_CONTACT_ID = "id";
    private static final String KEY_CONTACT_ADDRESS = "address";
    private static final String KEY_CONTACT_NAME = "name";
    private static final String KEY_CONTACT_MEMBERS = "members";
    private static final String KEY_CONTACT_TYPE = "type";
    private static final String KEY_CONTACT_KEYSTAT = "keystat";
    private static final String KEY_CONTACT_LASTACT = "lastact";
    private static final String KEY_CONTACT_UNREAD = "unread";
    private static final String KEY_CONTACT_GROUP = "group_id";
    
    private static final String[] COLUMNS_CONTACT = { 
    	KEY_CONTACT_ID, 
    	KEY_CONTACT_ADDRESS, 
    	KEY_CONTACT_NAME, 
    	KEY_CONTACT_MEMBERS,
    	KEY_CONTACT_TYPE,
    	KEY_CONTACT_KEYSTAT,
    	KEY_CONTACT_LASTACT,
    	KEY_CONTACT_UNREAD,
    	KEY_CONTACT_GROUP
    };
    
    private static final String TABLE_MESSAGES = "messages";
    private static final String KEY_MESSAGES_ID = "id";
    private static final String KEY_MESSAGES_FROM = "from_id";
    private static final String KEY_MESSAGES_TIME = "time";
    private static final String KEY_MESSAGES_TEXT = "text";
    private static final String KEY_MESSAGES_URI = "uri";
    
    private static final String[] COLUMNS_MESSAGES = { 
    	KEY_MESSAGES_ID, 
    	KEY_MESSAGES_FROM, 
    	KEY_MESSAGES_TIME, 
    	KEY_MESSAGES_TEXT,
    	KEY_MESSAGES_URI
    };
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // SQL statement to create book table
        String CREATE_CONTACT_TABLE = "CREATE TABLE " + TABLE_CONTACT + " ( " +
        		KEY_CONTACT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                KEY_CONTACT_ADDRESS + " TEXT, "+
                KEY_CONTACT_NAME + " TEXT, " +
                KEY_CONTACT_MEMBERS + " TEXT, " +
                KEY_CONTACT_LASTACT + " INTEGER, " +
                KEY_CONTACT_TYPE + " INTEGER, " +
                KEY_CONTACT_KEYSTAT + " INTEGER, " +
                KEY_CONTACT_UNREAD + " INTEGER, " +
                KEY_CONTACT_GROUP + " INTEGER "+ " )";
 
        // create books table
        db.execSQL(CREATE_CONTACT_TABLE);

        String CREATE_MESSAGES_TABLE = "CREATE TABLE "+TABLE_MESSAGES+" ( " +
        		KEY_MESSAGES_ID + " INTEGER, " +
        		KEY_MESSAGES_FROM + " INTEGER, "+
                KEY_MESSAGES_TIME + " INTEGER, "+
                KEY_MESSAGES_TEXT + " TEXT, " + 
                KEY_MESSAGES_URI + " TEXT " + " )";
 
        // create books table
        db.execSQL(CREATE_MESSAGES_TABLE);
}
      
 
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older contacts table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
 
        // create fresh books table
        this.onCreate(db);
    }
 
    public void contact_add(contact contact){
        //for logging
    	//Log.d("contact_add", contact.toString());
    	
    	if (contact.type_get() == contact.TYPE_PERSON) {
    		contact check_contact = contact_get_person_by_address(contact.address_get());
    		if (check_contact != null) {
    			Log.d("contact_add", "contact exists with address " + contact.address_get());
    			return;
    		}
    	} else {
    		contact check_contact = contact_get_group_by_address_and_group(contact.address_get(), contact.group_get());
    		if (check_contact != null) {
    			Log.d("contact_add", "contact exists with address " + contact.address_get());
    			return;
    		}
    	}
    	
    	// 1. get reference to writable DB
    	SQLiteDatabase db = this.getWritableDatabase();

    	// 2. create ContentValues to add key "column"/value
    	ContentValues values = new ContentValues();
    	values.put(KEY_CONTACT_ADDRESS, contact.address_get());
    	values.put(KEY_CONTACT_NAME, contact.name_get());
    	values.put(KEY_CONTACT_TYPE, contact.type_get());
    	values.put(KEY_CONTACT_KEYSTAT, contact.keystat_get());
    	if (contact.type_get() == contact.TYPE_GROUP)
    		values.put(KEY_CONTACT_MEMBERS, contact.members_get_string());
    	values.put(KEY_CONTACT_LASTACT, contact.time_lastact_get());
    	values.put(KEY_CONTACT_UNREAD, contact.unread_get());
    	values.put(KEY_CONTACT_GROUP, contact.group_get());

    	// 3. insert
    	db.insert(TABLE_CONTACT, // table
    			null, //nullColumnHack
    			values); // key/value -> keys = column names/ values = column values

    	// 4. close
    	db.close();
    }
    
    public void contact_update(contact contact){
    	//Log.d("contact_update", contact.toString());
    	
    	// 1. get reference to writable DB
    	SQLiteDatabase db = this.getWritableDatabase();

    	// 2. create ContentValues to add key "column"/value
    	ContentValues values = new ContentValues();
    	values.put(KEY_CONTACT_ADDRESS, contact.address_get());
    	values.put(KEY_CONTACT_NAME, contact.name_get());
    	values.put(KEY_CONTACT_TYPE, contact.type_get());
    	values.put(KEY_CONTACT_KEYSTAT, contact.keystat_get());
    	if (contact.type_get() == contact.TYPE_GROUP)
    		values.put(KEY_CONTACT_MEMBERS, contact.members_get_string());
    	values.put(KEY_CONTACT_LASTACT, contact.time_lastact_get());
    	values.put(KEY_CONTACT_UNREAD, contact.unread_get());
    	values.put(KEY_CONTACT_GROUP, contact.group_get());

    	Log.d("contact_update", "update contact with id " + contact.id_get());
    	// 3. update
    	db.update(TABLE_CONTACT, // table
    			values, // key/value -> keys = column names/ values = column values
    			"id = " + contact.id_get(), null);
    	
    	// 4. close
    	db.close();
    }

    public void contact_remove(contact contact){
    	Log.d("contact_update", contact.toString());
    	if (contact.id_get() == 1) {
    		Log.d("contact remove", "Can't remove myself");
    		return;
    	}
    	
    	// 1. get reference to writable DB
    	SQLiteDatabase db = this.getWritableDatabase();

    	// 3. delete
    	db.delete(TABLE_CONTACT, "id = " + contact.id_get(), null);
    	
    	// 4. close
    	db.close();
    }
    
    public contact contact_get_person_by_address(String address) {
    	address = DatabaseUtils.sqlEscapeString(address);
    	List<contact> contacts = contact_get(
    		KEY_CONTACT_TYPE + " = " + contact.TYPE_PERSON + " AND " +
    	    KEY_CONTACT_ADDRESS + " = " + address, null);
    	
    	if (contacts.size() == 0)
    		return null;
    	return contacts.get(0);
    }

    public List<contact> contact_get_by_address(String address) {
    	address = DatabaseUtils.sqlEscapeString(address);
    	List<contact> contacts = contact_get(KEY_CONTACT_ADDRESS + " = " + address, null);
    	
    	return contacts;
    }

    public List<contact> contact_get_by_type(int type) {
    	List<contact> contacts = contact_get(KEY_CONTACT_TYPE + " = " + type, KEY_CONTACT_ID);
    	
    	return contacts;
    }


    public contact contact_get_group_by_address_and_group(String address, int group) {
    	address = DatabaseUtils.sqlEscapeString(address);
    	List<contact> contacts = contact_get(
    			KEY_CONTACT_TYPE + " = " + contact.TYPE_GROUP + " AND " +
    			KEY_CONTACT_ADDRESS + " = " + address + " AND " +
    			KEY_CONTACT_GROUP + " = " + group, null);
    	
    	if (contacts.size() == 0)
    		return null;
    	return contacts.get(0);
    }

    public contact contact_get_by_id(int id) {
    	List<contact> contacts = contact_get(KEY_CONTACT_ID + " = " + id, null);
    	
    	if (contacts.size() == 0)
    		return null;
    	return contacts.get(0);
    }

    public int message_get_count_by_id(int id, long since) {
    	List<message> messages = message_get(
    			KEY_MESSAGES_ID + " = " + id + " AND " +
    			KEY_MESSAGES_TIME + " > " + since, 
    			KEY_MESSAGES_TIME);
    	
    	return messages.size();
    }
    
    public List<message> message_get_by_id(int id) {
    	List<message> messages = message_get(
    			KEY_MESSAGES_ID + " = " + id, 
    			KEY_MESSAGES_TIME);
    	
    	return messages;
    }

    public List<message> message_get_by_id(int id, int max) {
    	List<message> messages = message_get(
    			KEY_MESSAGES_ID + " = " + id, 
    			KEY_MESSAGES_TIME + " DESC",
    			"" + max);
    	
    	Collections.reverse(messages);
    	
    	return messages;
    }
    
    public List<contact> contact_get_all() {
    	/* by default order on last activity */
    	return contact_get(null, KEY_CONTACT_LASTACT + " DESC");
    }
    
    public List<contact> contact_get_sendable() {
    	List<contact> persons = contact_get(
    			KEY_CONTACT_TYPE + " = " + contact.TYPE_PERSON + " AND " +
    			KEY_CONTACT_KEYSTAT + " = " + contact.KEYSTAT_VERIFIED,
    			KEY_CONTACT_NAME);
    	List<contact> groups = contact_get_by_type(contact.TYPE_GROUP);
    	groups.addAll(persons);
    	return groups;
    }
    
    public List<contact> contact_get(String where, String order) {
        List<contact> contacts = new LinkedList<contact>();
  
        // 1. build the query
        String query = "SELECT  * FROM " + TABLE_CONTACT +
        		((where == null) ? "" : (" WHERE " + where)) +
        		((order == null) ? "" : (" ORDER BY " + order));
        //Log.d("contact_get_all_ordered", "query: " + query);
        
        // 2. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
  
        // 3. go over each row, build book and add it to list
        contact contact = null;
        if (cursor.moveToFirst()) {
            do {
                contact = new contact();
                contact.id_set(Integer.parseInt(cursor.getString(0)));
                contact.address_set(cursor.getString(1));
                contact.name_set(cursor.getString(2));
                String members = cursor.getString(3);
                if (members != null)
                	contact.members_set(members);
                contact.time_lastact_set(Long.parseLong(cursor.getString(4)));
                contact.type_set(Integer.parseInt(cursor.getString(5)));
                contact.keystat_set(Integer.parseInt(cursor.getString(6)));
                contact.unread_set(Long.parseLong(cursor.getString(7)));
                contact.group_set(Integer.parseInt(cursor.getString(8)));
                
                contacts.add(contact);
                //Log.d("contact_get_all_ordered", contact.toString());
            } while (cursor.moveToNext());
        }
        db.close();
  
        return contacts;
    }

    public void message_add(message message){
        //for logging
    	//Log.d("message_add", message.toString());
    	
    	//todo: add check for existing.

    	// 1. get reference to writable DB
    	SQLiteDatabase db = this.getWritableDatabase();

    	// 2. create ContentValues to add key "column"/value
    	ContentValues values = new ContentValues();
    	values.put(KEY_MESSAGES_ID, message.id_get());
    	values.put(KEY_MESSAGES_FROM, message.from_get());
    	values.put(KEY_MESSAGES_TIME, message.time_get());
    	values.put(KEY_MESSAGES_TEXT, message.text_get());
    	values.put(KEY_MESSAGES_URI, message.uri_get_string());

    	// 3. insert
    	db.insert(TABLE_MESSAGES, // table
    			null, //nullColumnHack
    			values); // key/value -> keys = column names/ values = column values

    	// 4. close
    	db.close();
    }

    
    public List<message> message_get(String where, String order) {
    	return message_get(where, order, null);
    }
    public List<message> message_get(String where, String order, String limit) {
        List<message> messages = new LinkedList<message>();
  
        // 1. build the query
        String query = "SELECT  * FROM " + TABLE_MESSAGES +
        		((where == null) ? "" : (" WHERE " + where)) +
        		((order == null) ? "" : (" ORDER BY " + order)) +
        		((limit == null) ? "" : (" LIMIT " + limit));
        //Log.d("message_get", "query: " + query);
        
        // 2. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
  
        // 3. go over each row, build book and add it to list
        message message = null;
        if (cursor.moveToFirst()) {
            do {
                message = new message();
                message.id_set(Integer.parseInt(cursor.getString(0)));
                message.from_set(Integer.parseInt(cursor.getString(1)));
                message.time_set(Long.parseLong(cursor.getString(2)));
                message.text_set(cursor.getString(3));
                String uris = cursor.getString(4);
                if (uris != null)
                	message.uri_set(Uri.parse(uris));
  
                messages.add(message);
            } while (cursor.moveToNext());
        }
        db.close();
  
        return messages;
    }
}
