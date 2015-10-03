package com.xrmaddness.offthegrid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class netwatcher extends BroadcastReceiver {
	
    @Override
    public void onReceive(Context context, Intent intent) {
        //here, check that the network connection is available. If yes, start your service. If not, stop your service.
       ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
       NetworkInfo info = cm.getActiveNetworkInfo();
	   Intent bg;

       if (info != null) {
           if (info.isConnected()) {
               Log.d("netwatcher", "connection, start service");
        	   bg = new Intent(context, background.class);
        	   bg.putExtra("state", 1);
       	       context.startService(bg);
       	       return;
           }
       }

       Log.d("netwatcher", "no connection, stop service");
       bg = new Intent(context, background.class);
       bg.putExtra("state", 0);
       context.stopService(bg);
    }
}