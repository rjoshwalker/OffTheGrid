package com.xrmaddness.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class local_message {
	static void send(Context ctx, String msg) {
		  Log.d("sender", "Broadcasting message");
		  Intent intent = new Intent("local_message");

		  intent.putExtra("message", msg);
		  LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
	}

	static void send_connection(Context ctx, boolean stat) {
		  Log.d("sender", "Broadcasting message");
		  Intent intent = new Intent("local_connection");

		  intent.putExtra("message", stat ? "T" : "F");
		  LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
	}
}
