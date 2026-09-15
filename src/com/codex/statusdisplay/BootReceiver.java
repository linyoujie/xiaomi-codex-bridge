package com.codex.statusdisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    static final String ACTION_RESTORE="com.codex.status.RESTORE";
    @Override public void onReceive(Context context,Intent intent){
        context.startActivity(new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
}
