package com.codex.statusdisplay;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private ParticleView particles;private StatusOverlay overlay;
    private final BroadcastReceiver refresh=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){reload();}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=.42f;getWindow().setAttributes(p);immersive();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(3,5,7));
        particles=new ParticleView(this);overlay=new StatusOverlay(this,particles);root.addView(particles,new FrameLayout.LayoutParams(-1,-1));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    }
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private void reload(){StatusModel m=StatusModel.load(this).effective();particles.setState(m.connected?m.state:"idle");overlay.setModel(m);}
    @Override protected void onResume(){super.onResume();registerReceiver(refresh,new IntentFilter(StatusModel.ACTION_REFRESH));immersive();reload();}
    @Override protected void onPause(){try{unregisterReceiver(refresh);}catch(IllegalArgumentException ignored){}scheduleRestore();super.onPause();}
    private void scheduleRestore(){
        Intent i=new Intent(this,BootReceiver.class).setAction(BootReceiver.ACTION_RESTORE);
        PendingIntent pi=PendingIntent.getBroadcast(this,7,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        ((AlarmManager)getSystemService(ALARM_SERVICE)).setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+15_000L,pi);
    }

    static final class StatusOverlay extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Handler clock=new Handler();private final ParticleView particles;private StatusModel model;
        private int page=0,taskIndex=0,previousTaskIndex=0;private long lastTaskFlip=SystemClock.elapsedRealtime(),flipStarted=0;private float touchX;
        private final Runnable tick=new Runnable(){@Override public void run(){StatusModel e=model.effective();particles.setState(e.connected?e.state:"idle");model=e;String[] titles=e.titles();long now=SystemClock.elapsedRealtime();if(page==0&&titles.length>1&&now-lastTaskFlip>=6000){previousTaskIndex=taskIndex%titles.length;taskIndex=(taskIndex+1)%titles.length;lastTaskFlip=now;flipStarted=now;}invalidate();clock.postDelayed(this,1000L);}};
        StatusOverlay(Context c,ParticleView particles){super(c);this.particles=particles;setClickable(true);setLayerType(View.LAYER_TYPE_SOFTWARE,null);model=StatusModel.load(c).effective();clock.post(tick);}
        void setModel(StatusModel m){model=m;invalidate();}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN){touchX=event.getX();return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){float dx=event.getX()-touchX;if(Math.abs(dx)>70){page=dx<0?1:0;invalidate();performClick();}return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDetachedFromWindow(){clock.removeCallbacks(tick);super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);StatusModel m=model.effective();int w=getWidth(),h=getHeight();
            float dx=((SystemClock.elapsedRealtime()/600_000L)%3-1)*3f,dy=((SystemClock.elapsedRealtime()/600_000L+1)%3-1)*2f;
            c.save();c.translate(dx,dy);long now=m.hostNow();
            if(!m.connected){
                text(c,new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(now)),w/2f,h/2f+8,112,Color.rgb(248,250,252),false,Paint.Align.CENTER);
                text(c,chineseDate(now),w/2f,h/2f+62,30,Color.rgb(148,163,184),false,Paint.Align.CENTER);
                connection(c,w-38,h-34,false);c.restore();return;
            }
            if(page==1||"idle".equals(m.state)){homePage(c,m,w,h,now);connection(c,w-38,h-34,true);pageDots(c,w,h,page==1?1:0);c.restore();return;}
            text(c,"C O D E X",34,68,28,colorFor(m.state),true,Paint.Align.LEFT);
            text(c,new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(now)),w-48,66,52,Color.WHITE,false,Paint.Align.RIGHT);
            text(c,chineseDate(now),w-48,100,20,Color.rgb(100,116,139),false,Paint.Align.RIGHT);
            String[] titles=m.titles();taskIndex%=titles.length;previousTaskIndex%=titles.length;String shown=titles[taskIndex];
            String[] lines=wrap(shown,15);float size=lines.length>1?48:(shown.length()>16?50:62),firstY=lines.length>1?202:236;
            float progress=flipStarted==0?1f:Math.min(1f,(SystemClock.elapsedRealtime()-flipStarted)/500f);
            if(progress<1f){drawTitle(c,titles[previousTaskIndex],48-progress*w,firstY);drawTitle(c,shown,48+(1-progress)*w,firstY);}else for(int i=0;i<lines.length;i++)text(c,lines[i],48,firstY+i*60,size,Color.rgb(248,250,252),false,Paint.Align.LEFT);
            text(c,stateLabel(m.state),50,firstY+lines.length*62,34,colorFor(m.state),false,Paint.Align.LEFT);
            text(c,elapsed(m.startedAtMs,now),48,h-52,38,Color.WHITE,false,Paint.Align.LEFT);
            text(c,Math.max(1,m.activeCount)+" 个任务",264,h-52,26,Color.rgb(148,163,184),false,Paint.Align.LEFT);
            weeklyUsage(c,w-300,h-82,m.quota7d);
            connection(c,w-42,126,m.connected);pageDots(c,w,h,0);c.restore();
        }
        private void drawTitle(Canvas c,String title,float x,float y){String[] lines=wrap(title,15);float size=lines.length>1?48:(title.length()>16?50:62);for(int i=0;i<lines.length;i++)text(c,lines[i],x,y+i*60,size,Color.rgb(248,250,252),false,Paint.Align.LEFT);}
        private void homePage(Canvas c,StatusModel m,int w,int h,long now){
            text(c,new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(now)),w/2f,174,92,Color.WHITE,false,Paint.Align.CENTER);
            text(c,chineseDate(now),w/2f,218,27,Color.rgb(148,163,184),false,Paint.Align.CENTER);commute(c,m,w,h,now);
        }
        private void pageDots(Canvas c,int w,int h,int selected){for(int i=0;i<2;i++){paint.setColor(i==selected?Color.rgb(94,234,212):Color.rgb(51,65,85));c.drawCircle(w/2f+(i==0?-10:10),h-20,i==selected?4:3,paint);}}
        private void commute(Canvas c,StatusModel m,int w,int h,long now){
            long age=m.commuteUpdatedAtMs>0?Math.max(0,now-m.commuteUpdatedAtMs):Long.MAX_VALUE;
            boolean usable=m.commuteAvailable&&m.commuteDurationMin>=0&&m.commuteDurationMin<=1440&&
                m.commuteArrivalAtMs>0&&age<=30*60_000L;
            if(!usable){
                text(c,"正在获取回家时间",w/2f,h/2f+128,30,Color.rgb(148,163,184),false,Paint.Align.CENTER);
            }else{
                String mode="walking".equals(m.commuteMode)?"步行":("driving".equals(m.commuteMode)?"自驾":"公共交通");
                text(c,mode,w/2f,h/2f+58,19,Color.rgb(100,116,139),false,Paint.Align.CENTER);
                text(c,"回家约 "+m.commuteDurationMin+" 分钟",w/2f,h/2f+125,38,Color.rgb(94,234,212),false,Paint.Align.CENTER);
                text(c,"预计 "+new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(m.commuteArrivalAtMs))+" 到家",w/2f,h/2f+172,25,Color.rgb(148,163,184),false,Paint.Align.CENTER);
            }
            text(c,"空闲",48,h-48,20,Color.rgb(71,85,105),false,Paint.Align.LEFT);
        }
        private void weeklyUsage(Canvas c,float x,float y,int value){
            if(value<0)return;
            int remaining=Math.max(0,100-value);
            text(c,"剩下 "+remaining+"%",x+260,y,18,Color.rgb(203,213,225),false,Paint.Align.RIGHT);
            float barY=y+25;paint.setStrokeWidth(8);paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(45,55,72));c.drawLine(x,barY,x+260,barY,paint);
            paint.setColor(Color.rgb(167,139,250));c.drawLine(x+260*(100-remaining)/100f,barY,x+260,barY,paint);
        }
        private void connection(Canvas c,float x,float y,boolean connected){
            paint.setColor(connected?Color.rgb(94,234,212):Color.rgb(71,85,105));paint.setShadowLayer(connected?10:0,0,0,paint.getColor());c.drawCircle(x,y,6,paint);paint.clearShadowLayer();
        }
        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align){
            paint.setTextSize(size);paint.setColor(color);paint.setTextAlign(align);paint.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,y,paint);
        }
        private static String[] wrap(String value,int width){String s=value==null||value.trim().isEmpty()?"Codex":value.trim();if(s.length()<=width)return new String[]{s};String a=s.substring(0,width),b=s.substring(width,Math.min(s.length(),width*2-1));if(s.length()>width*2-1)b+="…";return new String[]{a,b};}
        private static int colorFor(String s){if("waiting".equals(s))return Color.rgb(251,191,36);if("completed".equals(s))return Color.rgb(74,222,128);if("failed".equals(s))return Color.rgb(248,113,113);if("idle".equals(s))return Color.rgb(100,116,139);return Color.rgb(94,234,212);}
        private static String stateLabel(String s){if("waiting".equals(s))return "等待确认";if("completed".equals(s))return "已完成";if("failed".equals(s))return "发生错误";return "正在构建";}
        private static String elapsed(long start,long now){long sec=Math.max(0,(now-start)/1000L);return String.format(Locale.CHINA,"%02d 分 %02d 秒",sec/60,sec%60);}
        private static String chineseDate(long ms){Date d=new Date(ms);String date=new SimpleDateFormat("M月d日",Locale.CHINA).format(d);String[] week={"星期日","星期一","星期二","星期三","星期四","星期五","星期六"};java.util.Calendar cal=java.util.Calendar.getInstance(Locale.CHINA);cal.setTime(d);return date+"  "+week[cal.get(java.util.Calendar.DAY_OF_WEEK)-1];}
    }
}
