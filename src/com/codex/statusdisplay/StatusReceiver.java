package com.codex.statusdisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

public final class StatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        long now=intent.getLongExtra("host_time_ms",System.currentTimeMillis());
        String state=safeState(intent.getStringExtra("state"));
        SharedPreferences preferences=context.getSharedPreferences("status",Context.MODE_PRIVATE);
        String previousState=preferences.getString("state","idle");
        SharedPreferences.Editor e=preferences.edit()
            .putString("state",state).putString("title",clean(intent.getStringExtra("title")))
            .putString("task_titles",cleanTitles(intent.getStringExtra("task_titles")))
            .putString("phase",clean(intent.getStringExtra("phase")))
            .putLong("started_at_ms",intent.getLongExtra("started_at_ms",now))
            .putInt("active_count",Math.max(0,intent.getIntExtra("active_count",0)))
            .putInt("quota_5h_percent",clamp(intent.getIntExtra("quota_5h_percent",-1)))
            .putInt("quota_7d_percent",clamp(intent.getIntExtra("quota_7d_percent",-1)))
            .putBoolean("commute_available",intent.getBooleanExtra("commute_available",false))
            .putInt("commute_duration_min",commuteMinutes(intent.getIntExtra("commute_duration_min",-1)))
            .putString("commute_options",cleanCommuteOptions(intent.getStringExtra("commute_options")))
            .putString("commute_mode",commuteMode(intent.getStringExtra("commute_mode")))
            .putLong("commute_arrival_at_ms",positive(intent.getLongExtra("commute_arrival_at_ms",0)))
            .putLong("commute_updated_at_ms",positive(intent.getLongExtra("commute_updated_at_ms",0)))
            .putLong("host_time_ms",now).putLong("host_elapsed_ms",SystemClock.elapsedRealtime())
            .putBoolean("connected",intent.getBooleanExtra("connected",true));
        if("completed".equals(state)&&!"completed".equals(previousState))e.putLong("completed_at_ms",now);
        e.apply();
        context.sendBroadcast(new Intent(StatusModel.ACTION_REFRESH).setPackage(context.getPackageName()));
        context.startActivity(new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    private static String safeState(String s){
        return "working".equals(s)||"waiting".equals(s)||"completed".equals(s)||"failed".equals(s)?s:"idle";
    }
    private static String clean(String s){
        if(s==null||s.trim().isEmpty())return "Codex";
        s=s.replace('\n',' ').replace('\r',' ').trim();return s.length()>40?s.substring(0,39)+"…":s;
    }
    private static String cleanTitles(String value){
        if(value==null||value.trim().isEmpty())return "";
        String[] input=value.split("\\|\\|\\|");StringBuilder out=new StringBuilder();
        for(int i=0;i<input.length&&i<6;i++){
            String title=clean(input[i]);if("Codex".equals(title))continue;
            if(out.length()>0)out.append("|||");out.append(title.replace("|||"," "));
        }
        return out.toString();
    }
    private static int clamp(int n){return n<0?-1:Math.min(100,n);}
    private static int commuteMinutes(int n){return n>=0&&n<=1440?n:-1;}
    private static String commuteMode(String value){return "walking".equals(value)||"driving".equals(value)?value:"transit";}
    private static String cleanCommuteOptions(String value){
        if(value==null)return "";String[] parts=value.split(",");StringBuilder out=new StringBuilder();
        for(String part:parts){try{int n=Integer.parseInt(part.trim());if(n>=0&&n<=1440){if(out.length()>0)out.append(',');out.append(n);if(out.toString().split(",").length>=3)break;}}catch(NumberFormatException ignored){}}
        return out.toString();
    }
    private static long positive(long n){return Math.max(0,n);}
}
