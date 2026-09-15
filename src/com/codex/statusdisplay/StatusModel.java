package com.codex.statusdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

final class StatusModel {
    static final String ACTION_REFRESH = "com.codex.status.REFRESH";
    static final long COMPLETED_HOLD_MS = 8_000L;
    final String state, title, phase, taskTitles, commuteOptions, commuteMode;
    final long startedAtMs, hostTimeMs, hostElapsedMs, completedAtMs;
    final long commuteArrivalAtMs, commuteUpdatedAtMs;
    final int activeCount, quota5h, quota7d;
    final int commuteDurationMin;
    final boolean connected, commuteAvailable;

    StatusModel(String state, String title, String phase, String taskTitles, long startedAtMs, int activeCount,
                int quota5h, int quota7d, long hostTimeMs, long hostElapsedMs,
                boolean connected, long completedAtMs, boolean commuteAvailable,
                int commuteDurationMin, String commuteOptions, String commuteMode, long commuteArrivalAtMs, long commuteUpdatedAtMs) {
        this.state=state; this.title=title; this.phase=phase; this.taskTitles=taskTitles;this.startedAtMs=startedAtMs;
        this.activeCount=activeCount; this.quota5h=quota5h; this.quota7d=quota7d;
        this.hostTimeMs=hostTimeMs; this.hostElapsedMs=hostElapsedMs;
        this.connected=connected; this.completedAtMs=completedAtMs;
        this.commuteAvailable=commuteAvailable;this.commuteDurationMin=commuteDurationMin;this.commuteOptions=commuteOptions;this.commuteMode=commuteMode;
        this.commuteArrivalAtMs=commuteArrivalAtMs;this.commuteUpdatedAtMs=commuteUpdatedAtMs;
    }

    static StatusModel load(Context c) {
        SharedPreferences p=c.getSharedPreferences("status",Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        return new StatusModel(p.getString("state","idle"),p.getString("title","Codex"),
            p.getString("phase",""),p.getString("task_titles",""),p.getLong("started_at_ms",now),p.getInt("active_count",0),
            p.getInt("quota_5h_percent",-1),p.getInt("quota_7d_percent",-1),
            p.getLong("host_time_ms",now),p.getLong("host_elapsed_ms",SystemClock.elapsedRealtime()),
            p.getBoolean("connected",false),p.getLong("completed_at_ms",0),
            p.getBoolean("commute_available",false),p.getInt("commute_duration_min",-1),p.getString("commute_options",""),p.getString("commute_mode","transit"),
            p.getLong("commute_arrival_at_ms",0),p.getLong("commute_updated_at_ms",0));
    }
    long hostNow(){return hostTimeMs+Math.max(0,SystemClock.elapsedRealtime()-hostElapsedMs);}
    StatusModel effective(){
        boolean fresh=connected&&SystemClock.elapsedRealtime()-hostElapsedMs<45_000L;
        if("completed".equals(state)&&completedAtMs>0&&hostNow()-completedAtMs>=COMPLETED_HOLD_MS)
            return new StatusModel("idle","Codex","","",hostNow(),0,quota5h,quota7d,
                hostTimeMs,hostElapsedMs,fresh,completedAtMs,commuteAvailable,commuteDurationMin,commuteOptions,commuteMode,
                commuteArrivalAtMs,commuteUpdatedAtMs);
        if(fresh!=connected)return new StatusModel(state,title,phase,taskTitles,startedAtMs,activeCount,quota5h,quota7d,
            hostTimeMs,hostElapsedMs,fresh,completedAtMs,commuteAvailable,commuteDurationMin,commuteOptions,commuteMode,
            commuteArrivalAtMs,commuteUpdatedAtMs);
        return this;
    }
    String[] titles(){return taskTitles==null||taskTitles.isEmpty()?new String[]{title}:taskTitles.split("\\|\\|\\|");}
    int[] commuteChoices(){
        if(commuteOptions==null||commuteOptions.isEmpty())return commuteDurationMin>=0?new int[]{commuteDurationMin}:new int[0];
        String[] parts=commuteOptions.split(",");int[] values=new int[Math.min(3,parts.length)];int count=0;
        for(String part:parts)try{values[count++]=Integer.parseInt(part);}catch(NumberFormatException ignored){}
        if(count==values.length)return values;int[] trimmed=new int[count];System.arraycopy(values,0,trimmed,0,count);return trimmed;
    }
}
