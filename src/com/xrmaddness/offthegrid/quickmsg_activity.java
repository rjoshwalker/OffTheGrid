package com.xrmaddness.offthegrid;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;


public class quickmsg_activity extends Activity {
	Context activity_this = this;
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			on_update_ui();
		}
	};
	
	public void on_update_ui()
	{
		
	}
	
	@Override
	protected void onResume() {
	  super.onResume();
	  IntentFilter filter = new IntentFilter();
	  filter.addAction("net.vreeken.quickmsg.update_ui");
	  this.registerReceiver(receiver, filter);
	  background.activity_resume();
	  
	  on_update_ui();

	  LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
			      new IntentFilter("local_message"));
		
	}

	@Override
	protected void onPause() {
	  super.onPause();
	  background.activity_pause();
	  this.unregisterReceiver(this.receiver);
	  LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		  @Override
		  public void onReceive(Context context, Intent intent) {
		    // Get extra data included in the Intent
		    String message = intent.getStringExtra("message");
		    Log.d("receiver", "Got message: " + message);

//		    AlertDialog.Builder alert = new AlertDialog.Builder(activity_this);

//	    	alert.setTitle(R.string.action_message);
//	    	alert.setMessage(message);
//	    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
//	    		public void onClick(DialogInterface dialog, int whichButton) {
//	       		}
//	    	});
//	        alert.setCancelable(true);
//	        alert.show();
		    int duration = Toast.LENGTH_SHORT;

		    Toast toast = Toast.makeText(context, message, duration);
		    toast.show();
		  }
	};
}
