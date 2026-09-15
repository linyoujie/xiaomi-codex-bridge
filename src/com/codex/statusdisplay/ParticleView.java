package com.codex.statusdisplay;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class ParticleView extends GLSurfaceView {
    private final ParticleRenderer renderer;
    private final Handler frames=new Handler();
    private final Runnable draw=new Runnable(){@Override public void run(){requestRender();frames.postDelayed(this,33L);}};
    ParticleView(Context context){
        super(context);setEGLContextClientVersion(2);setPreserveEGLContextOnPause(true);
        renderer=new ParticleRenderer();setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
    void setState(String state){renderer.setState(state);}
    void replayCompletion(){renderer.replayCompletion();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();frames.removeCallbacks(draw);frames.post(draw);}
    @Override protected void onDetachedFromWindow(){frames.removeCallbacks(draw);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){
        super.onWindowVisibilityChanged(visibility);frames.removeCallbacks(draw);
        if(visibility==VISIBLE)frames.post(draw);
    }

    static final class ParticleRenderer implements Renderer {
        private static final int MAX=760;
        private final FloatBuffer seeds;
        private int program,pos,time,color,pointScale,layer,gradient,fireworks,effectAge,vertexBuffer,budget=MAX,slowFrames;
        private final long started=SystemClock.elapsedRealtime();
        private volatile long completedStarted=-1L;
        volatile String state="idle";
        ParticleRenderer(){
            float[] data=new float[MAX*3];Random r=new Random(0xC0DE);
            for(int i=0;i<MAX;i++){data[i*3]=r.nextFloat();data[i*3+1]=r.nextFloat();data[i*3+2]=r.nextFloat();}
            seeds=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            seeds.put(data).position(0);
        }
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(.012f,.02f,.028f,1f);
            String v="precision mediump float;attribute vec3 a;uniform float t;uniform float ps;uniform float layer;uniform float fw;uniform float age;"+
                "varying float z;varying float gx;varying float ly;varying float life;varying float gather;void main(){"+
                "float x=mod(a.x+t*(.012+.018*a.z),1.3)-.15;"+
                "float wave=.16*sin(x*7.4+a.z*1.8+t*.42);"+
                "float sy=a.y*2.-1.;float shaped=sy*abs(sy);"+
                "float width=mix(.045,.20,step(.78,a.z));float spread=shaped*width;"+
                "vec2 normalPos=vec2(x*2.-1.,(wave+spread-.015)*1.9);"+
                "if(fw<.5){gl_Position=vec4(normalPos,0.,1.);gl_PointSize=ps*(.72+1.48*a.z);z=a.z;gx=clamp(x+.15,0.,1.);ly=layer;life=1.;gather=0.;}"+
                "else{"+
                "float burst=floor(a.x*3.);float theta=6.28318*fract(a.y+a.z*.37);vec2 direction=vec2(cos(theta),sin(theta));"+
                "vec2 gatherOrigin=burst<.5?vec2(-.58,.10):(burst<1.5?vec2(.02,.28):vec2(.61,.03));"+
                "float gatherAmount=smoothstep(1.,2.35,age);vec2 scatterPos=mix(normalPos,gatherOrigin,gatherAmount);"+
                "float roundAge=max(age-2.4,0.);float localAge=mod(roundAge,1.55)-burst*.16;float roundId=floor(roundAge/1.55);"+
                "float speed=.18+.40*a.z;float shift=mod(roundId,2.)*.18-.09;vec2 origin=burst<.5?vec2(-.58+shift,.10):(burst<1.5?vec2(.02-shift,.28):vec2(.61+shift,.03));"+
                "vec2 firePos=origin+direction*speed*max(localAge,0.);firePos.y-=.10*max(localAge,0.)*max(localAge,0.);"+
                "if(localAge<0.||localAge>1.45)firePos=vec2(3.,3.);float firePhase=step(2.4,age);"+
                "vec2 effectPos=scatterPos;effectPos=mix(effectPos,firePos,firePhase);"+
                "gather=firePhase;life=mix(1.,clamp(1.-max(localAge,0.)/1.45,0.,1.),firePhase);"+
                "gl_Position=vec4(mix(normalPos,effectPos,fw),0.,1.);"+
                "gl_PointSize=ps*mix(mix(.60+1.65*a.z,.72+1.48*a.z,layer),1.15+2.5*a.z,fw);"+
                "z=a.z;gx=mix(clamp(x+.15,0.,1.),burst*.5,1.-gather);ly=layer;}}";
            String f="precision mediump float;uniform vec3 c;uniform float grad;uniform float fw;varying float z;varying float gx;varying float ly;varying float life;varying float gather;void main(){"+
                "vec2 p=gl_PointCoord-vec2(.5);float d=dot(p,p);if(d>.25)discard;"+
                "vec3 cyan=vec3(.08,.94,1.);vec3 violet=vec3(.58,.30,1.);"+
                "vec3 spectral=mix(c,mix(cyan,violet,smoothstep(.28,.90,gx)),grad);"+
                "float edge=1.-smoothstep(.035,.25,d);float alpha=edge*mix(.10+.25*z,.40+.58*z,ly);"+
                "vec3 fireColor=mix(mix(vec3(.10,.92,1.),vec3(.60,.32,1.),smoothstep(.1,.65,gx)),vec3(1.,.58,.16),step(.85,gx));"+
                "vec3 effectColor=mix(spectral,fireColor,gather);float effectAlpha=edge*life*(.55+.45*z);"+
                "gl_FragColor=vec4(mix(spectral,effectColor,fw),mix(alpha,effectAlpha,fw));}";
            program=link(v,f);pos=GLES20.glGetAttribLocation(program,"a");time=GLES20.glGetUniformLocation(program,"t");
            color=GLES20.glGetUniformLocation(program,"c");pointScale=GLES20.glGetUniformLocation(program,"ps");
            layer=GLES20.glGetUniformLocation(program,"layer");gradient=GLES20.glGetUniformLocation(program,"grad");
            fireworks=GLES20.glGetUniformLocation(program,"fw");effectAge=GLES20.glGetUniformLocation(program,"age");
            int[] buffers=new int[1];GLES20.glGenBuffers(1,buffers,0);vertexBuffer=buffers[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vertexBuffer);seeds.position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,seeds.capacity()*4,seeds,GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);}
        @Override public void onDrawFrame(GL10 gl){
            long before=System.nanoTime();GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program);
            GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vertexBuffer);
            GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,12,0);GLES20.glEnableVertexAttribArray(pos);
            GLES20.glUniform1f(time,(SystemClock.elapsedRealtime()-started)/1000f);
            setStateColor(color,state);
            boolean idle="idle".equals(state);float age=completedStarted<0?99f:(SystemClock.elapsedRealtime()-completedStarted)/1000f;
            boolean fire="completed".equals(state)&&age<8f;GLES20.glUniform1f(fireworks,fire?1f:0f);GLES20.glUniform1f(effectAge,age);
            GLES20.glUniform1f(gradient,"working".equals(state)?1f:0f);
            GLES20.glUniform1f(layer,1f);GLES20.glUniform1f(pointScale,idle?1.45f:(fire?2.5f:3.0f));
            GLES20.glDrawArrays(GLES20.GL_POINTS,0,idle?180:(fire?Math.min(650,budget):budget));
            long elapsed=System.nanoTime()-before;if(elapsed>33_000_000L)slowFrames++;else slowFrames=Math.max(0,slowFrames-1);
            if(slowFrames>24&&budget>520){budget-=80;slowFrames=0;}
        }
        void setState(String next){
            if(next==null)next="idle";
            if("completed".equals(next)&&!"completed".equals(state))completedStarted=SystemClock.elapsedRealtime();
            else if(!"completed".equals(next))completedStarted=-1L;
            state=next;
        }
        void replayCompletion(){if("completed".equals(state))completedStarted=SystemClock.elapsedRealtime();}
        private static void setStateColor(int uniform,String s){
            if("waiting".equals(s))GLES20.glUniform3f(uniform,.98f,.67f,.12f);
            else if("completed".equals(s))GLES20.glUniform3f(uniform,.29f,.87f,.50f);
            else if("failed".equals(s))GLES20.glUniform3f(uniform,.97f,.32f,.38f);
            else if("idle".equals(s))GLES20.glUniform3f(uniform,.20f,.45f,.50f);
            else GLES20.glUniform3f(uniform,.25f,.78f,.96f);
        }
        private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)Log.e("CodexParticles",GLES20.glGetShaderInfoLog(s));return s;}
        private static int link(String v,String f){int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,shader(GLES20.GL_VERTEX_SHADER,v));GLES20.glAttachShader(p,shader(GLES20.GL_FRAGMENT_SHADER,f));GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)Log.e("CodexParticles",GLES20.glGetProgramInfoLog(p));return p;}
    }
}
