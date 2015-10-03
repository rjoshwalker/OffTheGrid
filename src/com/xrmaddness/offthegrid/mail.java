package com.xrmaddness.offthegrid;

import javax.activation.*;
import javax.mail.*;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.internet.*;
import javax.mail.util.*;

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.protocol.IMAPProtocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.preference.*;
import android.content.*;
import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

public class mail {
	IMAPFolder mail_folder = null;
	Store mail_store = null;

	boolean imap_connected = false;
	
	String get_mqueue_dir(Context c)
	{
		File dir = c.getExternalFilesDir(null);
		if (dir == null) {
			dir = c.getFilesDir();
		}
		if (dir == null) {
			return null;
		}
		
		return dir.toString() + "/mailqueue/";
	}
	
	public Session get_smtp_session(Context context, boolean use_tls)
	{
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);

		String host = SP.getString("smtp_server", "example.com");
		String user = SP.getString("smtp_user", "");
         
        String pass = SP.getString("smtp_pass", "");

        /* These settings might appear insecure, but we don't trust email providers
         * anyway. That is why we use encryption end to end.
         * We might leak our meta-data a bit easier this way.
         */
        Properties props = System.getProperties();
        if (use_tls) {
        	props.put("mail.smtp.starttls.enable", "true");
        } else {
        	props.put("mail.smtp.starttle.enable", "false");
        }
        props.put("mail.smtp.starttls.required", "false");
        	
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.user", user);
        props.put("mail.smtp.password", pass);
        props.put("mail.smtp.port", SP.getString("smtp_port", "587"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "false");
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtps.ssl.checkserveridentity", "false");
        props.put("mail.smtps.ssl.trust", "*");
        
        Session session = Session.getDefaultInstance(props, null);
        //session.setDebug(true);
		return session;
	}
	
	public void send(Context c, String to) {
		send(c, to, null);
	}
	public void send(Context c, String to, List<attachment> attachments) {
		send(c, to, attachments, "mixed");
	}
	public void send(Context context, String to, List<attachment> attachments, String subtype) {
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);

        String from = SP.getString("email_address", "nobody@example.com");
        
        Multipart multiPart;
        String finalString="";
         
        Session session = get_smtp_session(context, true);
        
        DataHandler handler=new DataHandler(new ByteArrayDataSource(finalString.getBytes(),"text/plain" ));
        MimeMessage message = new MimeMessage(session);
        
        try {
        	message.setFrom(new InternetAddress(from));
        	message.setDataHandler(handler);

        	message.addHeader("X-QuickMSG", "v1.0");
         
        	multiPart=new MimeMultipart(subtype);
 
        	InternetAddress toAddress;
        	toAddress = new InternetAddress(to);
        	message.addRecipient(Message.RecipientType.TO, toAddress);

        	message.setSubject("[quickmsg]");
        	message.setText("This is a QuickMSG.");

        	Log.d("smtp_send", "Message headers ready");
        	
        	if (attachments != null) {
        		Log.d("send", "attachments: " + attachments.size());
        		for (int i = 0; i < attachments.size(); i++) {
        			BodyPart messageBodyPart = new MimeBodyPart();
        			
        			messageBodyPart.setDataHandler(attachments.get(i).datahandler);
        		    messageBodyPart.setFileName(attachments.get(i).name);
        		    messageBodyPart.setDisposition(attachments.get(i).disposition);
        		    multiPart.addBodyPart(messageBodyPart);
        		}
        	}
        	message.setContent(multiPart);
        }
        catch (Exception e) {
    		Log.e("send_msg", "could no create message: " + e.getMessage());
    		e.printStackTrace();
    		return;
        }
        
        if (!smtp_send(context, message)) {
        	save(context, message);
   			local_message.send(context, 
   				"Sending message failed, " +
   				"the message has been saved for a later attempt.");
        }
        
    }

	public void save(Context c, Message m)
	{
		String dir = get_mqueue_dir(c); 
		if (dir == null)
			return;

		File df = new File(dir);
		String mfile = UUID.randomUUID().toString();
		File mf = new File(dir, mfile);
		df.mkdirs();
		Log.d("mail save", "save message to dir: " + dir + " file: " + mfile);

		OutputStream of;
		try {
			of = new FileOutputStream(mf);
		} catch (FileNotFoundException e) {
				String msg = e.getMessage();
				Log.d("save", "Could not create file: " + (msg != null ? msg : "null"));
				return;
		}
		
		try {
			m.writeTo(of);
		} catch (IOException e) {
			String msg = e.getMessage();
			Log.d("save", "IOException: " + (msg != null ? msg : "null"));
			e.printStackTrace();
		} catch (MessagingException e) {
			String msg = e.getMessage();
			Log.d("save", "MessagingException: " + (msg != null ? msg : "null"));
			e.printStackTrace();
		}
		
		try {
			of.close();
		} catch (Exception e) {
			String err = e.getMessage();
			Log.e("mail save", "err:" +(err != null ? err : "null"));
		}
		
	}
	
	public void flush(Context c)
	{
		String dir = get_mqueue_dir(c); 
		if (dir == null)
			return;
		
		File[] files = new File(dir).listFiles();
		if (files == null)
			return;
		for ( File f : files ) {
			if (f.isFile()) {
				Log.d("mail flush", "Message in queue, size: " + f.length());
				if (f.length() == 0) {
					f.delete();
					Log.d("mail flush", "Delete empty message, propably from a previous buggy version");
					continue;
				}
				InputStream is;
				try {
					is = new FileInputStream(f);
				} catch (FileNotFoundException e1) {
					continue;
				}
				
		        Session session = get_smtp_session(c, true);
		        try {
					Message message = new MimeMessage(session, is);

					if (smtp_send(c, message)) {
						f.delete();
			        }
					
					Log.d("mail flush", "Message send");
				} catch (MessagingException e) {
					String msg = e.getMessage();
					Log.e("flush", "resend message failed: " + (msg != null ? msg : ""));
		   			local_message.send(c, 
		   	   				"Resending saved message failed, " +
		   	   				"the message will be resend later.");
					continue;
				}
		        
			}
		}
	}

	public boolean smtp_send(Context context, Message message)
	{
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);

		String host = SP.getString("smtp_server", "example.com");
		String user = SP.getString("smtp_user", "");
        String pass = SP.getString("smtp_pass", "");
        String port = SP.getString("smtp_port", "587");
        String transport1;
        String transport2;
        
        if (port.equals("465")) {
        	transport1 = "smtps";
        	transport2 = "smtp";
        } else {
        	transport1 = "smtp";
        	transport2 = "smtps";
        }
        
        try {

            Session session = get_smtp_session(context, true);
        	Transport transport = session.getTransport(transport1);

        	transport.connect(host, user, pass);

        	transport.sendMessage(message, message.getAllRecipients());
        	transport.close();
        }
        catch (Exception e) {
    		Log.e("send_msg", "could not send message with " + transport1);
    		e.printStackTrace();
            try {

                Session session = get_smtp_session(context, true);
            	Transport transport = session.getTransport(transport2);

            	transport.connect(host, user, pass);

            	transport.sendMessage(message, message.getAllRecipients());
            	transport.close();
            }
            catch (Exception e2) {
        		Log.e("send_msg", "could not send message with " + transport2);
				e.printStackTrace();
        		return false;
            }
        }
        Log.d("send_msg", "message has been sent.");

		return true;
	}
	
	public void recv_quickmsg_cb(String from, List<attachment> attachments, String subtype)
	{
		
	}

	private Store open_imap(Context context)
	{
		if (mail_store != null) {
			if (mail_store.isConnected())
				return mail_store;
		}
		mail_store = null;
		mail_folder = null;
		
		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);

		String host = SP.getString("imap_server", "example.com");
		String port = SP.getString("imap_port", "993");
		String user = SP.getString("imap_user", "nobody");
		String pass = SP.getString("imap_pass", "");

		Properties props = System.getProperties();
		props.setProperty("mail.imap.host", host);
		props.setProperty("mail.imap.port", port);
		props.setProperty("mail.imap.connectiontimeout", "5000");
		props.setProperty("mail.imap.ssl.trust",  "*");
		props.setProperty("mail.imap.starttls.enable", "true");
		//props.setProperty("mail.imap.timeout", "5000");

		Session session = Session.getDefaultInstance(props);
		URLName urlName = new URLName("imaps", host, Integer.parseInt(port), "", user, pass);
		Store store;
		
//		Log.d("recv_msg", urlName.toString());
		try { 
		  store = session.getStore(urlName);
		  if (!store.isConnected()) {
		    store.connect();
		  }
		} catch (Exception e) {
    		Log.e("recv_msg", "connect imaps: " + e.getMessage());
		
    		urlName = new URLName("imap", host, Integer.parseInt(port), "", user, pass);
//    		Log.d("recv_msg", urlName.toString());
    		
    		try {
    			store = session.getStore(urlName);
    			if (!store.isConnected()) {
    				store.connect();
    			}
    		} catch (Exception e2) {
    			Log.e("recv_msg", "connect imap: " + e2.getMessage());
       			local_message.send(context, 
       	   				"Could not connect to the IMAP server");
        		return null;
    		}
		}
		
		Log.d("recv_msg", "store.isConnected():" + store.isConnected());
				
		mail_store = store;

		imap_connected = true;
		local_message.send_connection(context, imap_connected);

		return store;
	}
	
	private IMAPFolder open_folder(Context context, Store store) 
	{
		// Open the Folder
		String mbox = "INBOX";
		IMAPFolder folder;
		if ( mail_folder != null) {
			if (mail_folder.isOpen()) {
				return mail_folder;
			}
		}
			
		try {
			folder = (IMAPFolder) store.getDefaultFolder();
			if (folder == null) {
				Log.e("recv_msg", "No default folder");
				return null;
			}

		   folder = (IMAPFolder) folder.getFolder(mbox);
		   if (!folder.exists()) {
			   Log.e("recv_msg", mbox + "  does not exist");
			   return null;
		   }
		
		   folder.open(Folder.READ_WRITE);

		} catch (MessagingException e) {
			Log.e("open_folder", e.getMessage());
			return null;
		} catch (IllegalStateException ei) {
			String msg = ei.getMessage();
			Log.e("open_folder", "illegal state: " +(msg != null ? msg : "null"));
			mail_folder = null;
			try {
				mail_store.close();
			} catch (MessagingException e) {
				e.printStackTrace();
			}
			mail_store = null;
			return null;
		}

	
		try {
			folder.setSubscribed(true);
		} catch (MessagingException e) {
			Log.e("open folder", "setSubscribed " + e.getMessage());
		}
		
		mail_folder = folder;
		return folder;
	}

	public void noop(IMAPFolder folder) {
		try {
			folder.doCommand(new IMAPFolder.ProtocolCommand() {
				public Object doCommand(IMAPProtocol p)
						throws ProtocolException {
					p.simpleCommand("NOOP", null);
					return null;
				}
			});
		} catch (MessagingException e) {
			Log.e("noop", "noop failed" + e.getMessage());
		}
	}

	
	public void idle(Context context, final long timeout) 
	{
		Store store = open_imap(context);
		if (store == null) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}
			return;
		}
		final IMAPFolder folder = open_folder(context, store);

		if (folder == null) {
			return;
		}
		
		Thread t = new Thread(new Runnable() {
            public void run() {
				try {
					Thread.sleep(timeout);
				} catch (InterruptedException e) {
				}
				Log.d("mail idle", "timeout");
				noop(folder);
            }
        });
		
		t.start();

		folder.addMessageCountListener(new MessageCountListener() {
            @Override
            public void messagesAdded(MessageCountEvent arg0) {
            	Log.d("mail idle", "message added");
            	noop(folder);
            }

			@Override
			public void messagesRemoved(MessageCountEvent arg0) {
			}
       });
		
		try {
			folder.idle();
			t.interrupt();
		} catch (MessagingException e) {
			Log.e("idle", e.getMessage());
			if (!store.isConnected()) {
				Log.d("idle", "connection closed");
				
				imap_connected = false;
				local_message.send_connection(context, imap_connected);

				mail_folder = null;
				mail_store = null;
			}
		}
		Log.d("mail idle", "end of idle");
	}
	
	public void recv(Context context) {
		Store store = open_imap(context);
		if (store == null)
			return;

		Folder folder = open_folder(context, store);
		if (folder == null)
			return;
		
		Boolean exp = false;
		
		try {

		   if (!(folder instanceof UIDFolder)) {
			   Log.e("recv_msg", "This Provider or this folder does not support UIDs");
			   return;
		   }

		   UIDFolder ufolder = (UIDFolder)folder;

		   int totalMessages = folder.getMessageCount();

		   if (totalMessages == 0) {
			   Log.d("recv_msg", "Empty folder");
			   return;
		   }

	 	   // Attributes & Flags for ALL messages ..
		   Message[] msgs = ufolder.getMessagesByUID(1, UIDFolder.LASTUID);
		   // Use a suitable FetchProfile
		   FetchProfile fp = new FetchProfile();
		   fp.add(FetchProfile.Item.ENVELOPE);
		   fp.add(FetchProfile.Item.FLAGS);
		   fp.add("X-QuickMSG");
		   fp.add("From");
		   folder.fetch(msgs, fp);

		   for (int i = 0; i < msgs.length; i++) {
			   // ufolder.getUID(msgs[i]) + ":");
			   InternetAddress add = (InternetAddress)msgs[i].getFrom()[0];
			   String add_str = add.getAddress();
			   Pattern pattern = Pattern.compile("<(.*?)>");
			   Matcher matcher = pattern.matcher(add_str);
			   if (matcher.find())
			   {
				   add_str = matcher.group(1);
			   }

			   String h[] = msgs[i].getHeader("X-QuickMSG");
			   if (h != null) {
				   if (msgs[i].isSet(Flags.Flag.DELETED))
					   continue;
				   
				   Object o = msgs[i].getContent();
				   if (!(o instanceof MimeMultipart))
					   continue;

				   List<attachment> attachments = new LinkedList<attachment>();
				   
				   Multipart mp = (Multipart)o;
				   ContentType ct = new ContentType(mp.getContentType());
				   String subtype = ct.getSubType();

				   for (int j = 0; j < mp.getCount(); j++) {
					   BodyPart bp = mp.getBodyPart(j);
					   attachment attachment = new attachment();
					   attachment.name = bp.getFileName();
					   attachment.datahandler = bp.getDataHandler();
					   attachments.add(attachment);
				   }
				   recv_quickmsg_cb(add_str, attachments, subtype);

				   msgs[i].setFlag(Flags.Flag.SEEN, true);
				   msgs[i].setFlag(Flags.Flag.DELETED, true);
				   exp = true;
			   }
		   }

		   if (exp) {
			   folder.expunge();
			   folder.close(false);
			   store.close();
			   mail_folder = null;
			   mail_store = null;
			   
			   imap_connected = false;
			   local_message.send_connection(context, imap_connected);
		   }
	   } catch (Exception e) {
		   Log.e("recv_msg", "recv: " + e.getMessage());
	   }
	}
	
	public attachment multipart_create(List<attachment> attachments)
	{
        MimeMultipart multiPart;
    	multiPart=new MimeMultipart("mixed");

		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			Properties props = System.getProperties();
	        props.put("mail.host", "smtp.dummydomain.com");
	        props.put("mail.transport.protocol", "smtp");

	        Session mailSession = Session.getDefaultInstance(props, null);
	        MimeMessage message = new MimeMessage(mailSession);
	        
	        for (int i = 0; i < attachments.size(); i++ ) {
	        	attachment attachment = attachments.get(i);
	        	BodyPart messageBodyPart = new MimeBodyPart();

	        	messageBodyPart.setDataHandler(attachment.datahandler);
			    messageBodyPart.setFileName(attachment.name);
			    messageBodyPart.setDisposition(attachment.disposition);
			    multiPart.addBodyPart(messageBodyPart);
	        }
	        
		    
//		    Log.d("mp", multiPart.getPreamble());
//		    multiPart.setPreamble("T");
		    
		    message.setContent(multiPart);
		    
		    DataHandler dh = message.getDataHandler();
		    Log.d("message", "contenttype: " + dh.getContentType());
		  
		    Log.d("message", message.toString());
		    message.writeTo(bos);
		}
		catch (Exception e) {
			Log.e("create mime", e.getMessage());
		}
		DataSource ads = new ByteArrayDataSource(bos.toByteArray(), "multipart/mixed");
		attachment aout = new attachment();
		aout.datahandler = new DataHandler(ads);
		aout.name = "encrypted";
	
		return aout;
	}
	
	List<attachment> multipart_get_attachments(attachment mpa)
	{
		Properties props = System.getProperties();
        props.put("mail.host", "smtp.dummydomain.com");
        props.put("mail.transport.protocol", "smtp");
        Session mailSession = Session.getDefaultInstance(props, null);
        MimeMessage msg = null;
        
        try {
			msg = new MimeMessage(mailSession, mpa.datahandler.getInputStream());
		} catch (Exception e) {
			Log.e("multipart_get_attachments", "Could not create mime message");
			e.printStackTrace();
			return null;
		}
        if (msg == null)
        	return null;
        
        Object o;
		try {
			o = msg.getContent();
		} catch (Exception e) {
			Log.e("multipart_get_attachments", "Could not get content");
			e.printStackTrace();
			return null;
		}
		if (!(o instanceof MimeMultipart))
			   	return null;

		List<attachment> attachments = new LinkedList<attachment>();
		   
		Multipart mp = (Multipart)o;
		try {
			ContentType ct = new ContentType(mp.getContentType());
			String subtype = ct.getSubType();

			for (int j = 0; j < mp.getCount(); j++) {
			   BodyPart bp = mp.getBodyPart(j);
			   attachment attachment = new attachment();
			   attachment.name = bp.getFileName();
			   attachment.datahandler = bp.getDataHandler();
			   attachments.add(attachment);
			   Log.d("multipart_get_attachments", "Got attachment with name:" + attachment.name);
			}
		}
		catch (Exception e) {
			Log.e("multipart_get_attachments", "Could not get attachments");
			e.printStackTrace();
			return null;
		}
		
		return attachments;
	}
}
