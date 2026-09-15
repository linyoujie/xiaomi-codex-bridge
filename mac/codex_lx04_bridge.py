#!/usr/bin/env python3
import glob,html,json,os,re,shlex,socket,sqlite3,struct,subprocess,threading,time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request,urlopen

SERIAL=os.environ.get("LX04_SERIAL","21065/C0VP67106")
ADB=os.environ.get("ADB_PATH","/opt/homebrew/bin/adb")
CHROME=os.environ.get("CHROME_PATH","/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
CODEX=Path.home()/".codex"
IPC=Path("/tmp/codex-ipc")/f"ipc-{os.getuid()}.sock"
COMMUTE_ORIGIN=os.environ.get("LX04_COMMUTE_ORIGIN","W 42nd St & Broadway, New York, NY 10036")
COMMUTE_DESTINATION=os.environ.get("LX04_COMMUTE_DESTINATION","142nd St & 60th Ave, Flushing, NY 11355")
COMMUTE_MODE=os.environ.get("LX04_COMMUTE_MODE","transit")
if COMMUTE_MODE not in {"transit","driving","walking"}:COMMUTE_MODE="transit"
COMMUTE_REFRESH_SECONDS=max(60,int(os.environ.get("LX04_COMMUTE_REFRESH_SECONDS","300")))
COMMUTE_STALE_SECONDS=15*60
COMMUTE_EXPIRE_SECONDS=30*60
COMPLETED_HOLD_SECONDS=8
COMMUTE_MAX_RESPONSE=2*1024*1024

def commute_url():
    return "https://www.google.com/maps/dir/?"+urlencode({"api":"1","origin":COMMUTE_ORIGIN,
        "destination":COMMUTE_DESTINATION,"travelmode":COMMUTE_MODE})

def parse_duration_minutes(value):
    text=value.lower().strip();hours=minutes=0
    hour=re.search(r"(\d+)\s*(?:h(?:r|our)?s?|小时)",text)
    minute=re.search(r"(\d+)\s*(?:m(?:in(?:ute)?)?s?|分钟)",text)
    if hour:hours=int(hour.group(1))
    if minute:minutes=int(minute.group(1))
    if not hour and not minute:return None
    total=hours*60+minutes
    return total if 5<=total<=6*60 else None

def parse_commute_options(raw):
    """Conservatively extract a transit duration from a rendered Maps response."""
    text=raw.decode("utf-8","replace") if isinstance(raw,bytes) else str(raw)
    text=html.unescape(text).replace("\\u0026","&").replace("\\u003d","=")
    text=text.replace("\\x22",'"').replace("\\\"",'"')
    # Query/config values say `travelmode=transit` near generic 15/30-minute
    # controls. They are not route cards and must not qualify a duration.
    text=re.sub(r"travelmode\s*=\s*(?:transit|driving|walking)","",text,flags=re.IGNORECASE)
    duration=r"(?:(?:\d+\s*(?:hr|hour|hrs|hours|小时))(?:\s*\d+\s*(?:min|mins|minute|minutes|分钟))?|(?:\d+\s*(?:min|mins|minute|minutes|分钟)))"
    mode_label=(r"(?:Walking|Walk|步行)" if COMMUTE_MODE=="walking" else
        (r"(?:Driving|Drive|驾车|自驾)" if COMMUTE_MODE=="driving" else r"(?:Transit|Public transit|公共交通)"))
    patterns=[
        rf"{mode_label}[^\n]{{0,280}}?({duration})",
        rf"({duration})[^\n]{{0,180}}?{mode_label}",
        rf"({duration})[^\n]{{0,100}}?\d{{1,2}}:\d{{2}}\s*(?:AM|PM)?\s*(?:—|-|–)\s*\d{{1,2}}:\d{{2}}",
    ]
    found=[]
    for pattern in patterns:
        for match in re.finditer(pattern,text,re.IGNORECASE):
            minutes=parse_duration_minutes(match.group(1))
            if minutes is not None and minutes not in found:found.append(minutes)
    return found[:3]

def parse_commute_response(raw):
    options=parse_commute_options(raw)
    return options[0] if options else None

def fetch_commute_options(timeout=12):
    request=Request(commute_url(),headers={"User-Agent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 Chrome/126 Safari/537.36","Accept-Language":"en-US,en;q=0.8",
        "Accept-Encoding":"identity"})
    with urlopen(request,timeout=timeout) as response:
        raw=response.read(COMMUTE_MAX_RESPONSE+1)
    if len(raw)>COMMUTE_MAX_RESPONSE:raise ValueError("Maps response exceeded size limit")
    options=parse_commute_options(raw)
    if not options:
        result=subprocess.run([CHROME,"--headless=new","--disable-gpu","--no-first-run",
            "--virtual-time-budget=9000","--dump-dom",commute_url()],capture_output=True,
            text=True,timeout=max(20,timeout+10))
        if result.returncode!=0:raise ValueError("headless Maps rendering failed")
        options=parse_commute_options(result.stdout)
    if not options:raise ValueError("no transit duration in rendered Maps response")
    return options

def fetch_commute(timeout=12):
    return fetch_commute_options(timeout)[0]

class CommuteMonitor:
    def __init__(self,fetcher=fetch_commute,clock=time.time):
        self.fetcher,self.clock=fetcher,clock
        self.last_attempt=0;self.last_success=0;self.duration=None;self.options=[];self.was_idle=False
    def snapshot(self,is_idle):
        now=self.clock();entering=is_idle and not self.was_idle;self.was_idle=is_idle
        if is_idle and (entering or now-self.last_attempt>=COMMUTE_REFRESH_SECONDS):
            self.last_attempt=now
            try:
                self.duration=int(self.fetcher());self.options=[self.duration];self.last_success=now
                if os.environ.get("LX04_DEBUG"):print("commute minutes=",self.duration,flush=True)
            except Exception as error:
                if os.environ.get("LX04_DEBUG"):print("commute fetch error:",str(error)[:160],flush=True)
        age=now-self.last_success if self.last_success else float("inf")
        available=self.duration is not None and age<=COMMUTE_EXPIRE_SECONDS
        return {"commute_available":available,"commute_duration_min":self.duration if available else -1,
            "commute_options":"",
            "commute_mode":COMMUTE_MODE,
            "commute_arrival_at_ms":int((self.last_success+self.duration*60)*1000) if available else 0,
            "commute_updated_at_ms":int(self.last_success*1000) if available else 0}

class HomeTimeMonitor:
    """Local-only countdown; never sends office or home locations anywhere."""
    def snapshot(self,is_idle):
        now=time.time();local=time.localtime(now)
        try:hour,minute=(int(part) for part in HOME_TIME.split(":",1))
        except (ValueError,TypeError):hour,minute=18,0
        hour=max(0,min(23,hour));minute=max(0,min(59,minute))
        target=time.mktime((local.tm_year,local.tm_mon,local.tm_mday,hour,minute,0,
            local.tm_wday,local.tm_yday,local.tm_isdst))
        remaining=max(0,int((target-now+59)//60))
        return {"commute_available":True,"commute_duration_min":remaining,
            "commute_arrival_at_ms":int(target*1000),"commute_updated_at_ms":int(now*1000)}

def clean_title(value):
    lines=str(value or "").splitlines()
    first=lines[0].strip() if lines else ""
    if not first or "TRANSCRIPT" in first or "tool call" in first.lower():return "Codex 任务"
    return first[:40]

def title_for(thread_id):
    if not re.fullmatch(r"[0-9a-fA-F-]{36}",thread_id or ""):return "Codex 任务"
    try:
        with sqlite3.connect(f"file:{CODEX/'state_5.sqlite'}?mode=ro",uri=True,timeout=1) as db:
            # `title` is normally derived from the first user message. `name` is
            # the actual task name shown in the Codex sidebar, so only expose it.
            row=db.execute("select coalesce(nullif(name,''),nullif(agent_nickname,'')) from threads where id=? limit 1",(thread_id,)).fetchone()
        return clean_title(row[0] if row else "")
    except (sqlite3.Error,OSError):return "Codex 任务"

def recursive_string(value,keys):
    if isinstance(value,dict):
        for key in keys:
            if isinstance(value.get(key),str) and value[key]:return value[key]
        for nested in value.values():
            hit=recursive_string(nested,keys)
            if hit:return hit
    elif isinstance(value,list):
        for nested in value:
            hit=recursive_string(nested,keys)
            if hit:return hit

def recursive_runtime(value):
    if isinstance(value,dict):
        if isinstance(value.get("threadRuntimeStatus"),dict):return value["threadRuntimeStatus"]
        kind=str(value.get("type","")).lower()
        if kind in {"active","idle","inactive","error","failed","notloaded","not_loaded"}:return value
        for nested in value.values():
            hit=recursive_runtime(nested)
            if hit:return hit
    elif isinstance(value,list):
        for nested in value:
            hit=recursive_runtime(nested)
            if hit:return hit

class IPCMonitor:
    def __init__(self):
        self.lock,self.entries=threading.Lock(),{}
        threading.Thread(target=self._loop,daemon=True).start()
    def _loop(self):
        while True:
            try:
                with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as client:
                    client.connect(str(IPC))
                    request={"type":"request","requestId":f"lx04-{time.time_ns()}","sourceClientId":"lx04-status","version":0,"method":"initialize","params":{"clientType":"lx04-status"},"timeoutMs":5000}
                    payload=json.dumps(request,separators=(",",":")).encode();client.sendall(struct.pack("<I",len(payload))+payload);self._read(client)
            except OSError:
                with self.lock:self.entries.clear()
                time.sleep(1.5)
    def _read(self,client):
        buffer=bytearray()
        while True:
            chunk=client.recv(65536)
            if not chunk:return
            buffer.extend(chunk)
            while len(buffer)>=4:
                size=struct.unpack_from("<I",buffer)[0]
                if size>256*1024*1024:return
                if len(buffer)<size+4:break
                raw=bytes(buffer[4:size+4]);del buffer[:size+4]
                try:self._ingest(json.loads(raw))
                except (json.JSONDecodeError,UnicodeDecodeError):pass
    def _ingest(self,message):
        thread_id=recursive_string(message,{"conversationId","threadId","conversation_id","thread_id"})
        if not thread_id:return
        runtime=recursive_runtime(message);method=str(message.get("method",""));state=None
        if runtime:
            kind=str(runtime.get("type","")).lower();flags={str(x).lower() for x in runtime.get("activeFlags",[])}
            if kind=="active":state="waiting" if {"waitingonapproval","waitingonuserinput"}&flags else "working"
            elif kind in {"error","failed"}:state="failed"
            elif kind in {"idle","inactive","notloaded","not_loaded"}:state="completed"
        elif "requestApproval" in method or "requestUserInput" in method:state="waiting"
        if not state:return
        now=time.time()
        with self.lock:
            old=self.entries.get(thread_id);self.entries[thread_id]={"state":state,"updated":now,"started":old["started"] if old else now}
    def snapshot(self):
        now=time.time()
        with self.lock:
            self.entries={k:v for k,v in self.entries.items() if now-v["updated"]<=21600};entries=dict(self.entries)
        if not entries:return None
        selected=None
        for state in ("waiting","working","failed","completed","idle"):
            matches=[(k,v) for k,v in entries.items() if v["state"]==state]
            if matches:selected=max(matches,key=lambda item:item[1]["updated"]);break
        thread_id,item=selected;active=sum(v["state"] in {"working","waiting"} for v in entries.values())
        visible=sorted(((k,v) for k,v in entries.items() if v["state"] in {"working","waiting"}),
            key=lambda pair:(0 if pair[1]["state"]=="waiting" else 1,-pair[1]["updated"]))
        titles=[]
        for task_id,_ in visible[:6]:
            name=title_for(task_id)
            if name not in titles:titles.append(name)
        selected_title=title_for(thread_id)
        if not titles:titles=[selected_title]
        return {"state":item["state"],"title":selected_title,"task_titles":titles,
            "phase":"等待确认" if item["state"]=="waiting" else "执行中","started_at_ms":int(item["started"]*1000),"active_count":active}

def newest_session():
    paths=glob.glob(str(CODEX/"sessions"/"**"/"*.jsonl"),recursive=True)
    return max(paths,key=os.path.getmtime) if paths else None

def fallback_snapshot():
    path=newest_session()
    if not path:return None
    try:
        with open(path,"rb") as handle:
            handle.seek(0,2);size=handle.tell();handle.seek(max(0,size-4*1024*1024));raw=handle.read()
    except OSError:return None
    state,pending,started="idle",set(),os.path.getmtime(path)
    for line in raw.splitlines()[1:]:
        try:record=json.loads(line)
        except (json.JSONDecodeError,UnicodeDecodeError):continue
        payload=record.get("payload",{})
        if record.get("type")!="event_msg" or not isinstance(payload,dict):continue
        kind=payload.get("type","");call_id=next((payload.get(k) for k in ("call_id","callId","request_id","requestId","id") if payload.get(k)),"unknown")
        if kind=="task_started":state,pending="working",set()
        elif kind in {"task_complete","turn_aborted","task_cancelled","task_canceled"}:state,pending="completed",set()
        elif kind in {"task_failed","task_error"}:state,pending="failed",set()
        elif kind in {"exec_approval_request","apply_patch_approval_request","request_user_input","elicitation_request","permissions_request"}:pending.add(call_id);state="waiting"
        elif kind.endswith("_response"):
            pending.discard(call_id)
            if not pending:state="working"
    if pending:state="waiting"
    match=re.search(r"[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}",os.path.basename(path))
    title=title_for(match.group(0) if match else "")
    return {"state":state,"title":title,"task_titles":[title],"phase":"等待确认" if state=="waiting" else "执行中","started_at_ms":int(started*1000),"active_count":1 if state in {"working","waiting"} else 0}

def send(snapshot):
    now=int(time.time()*1000)
    titles="|||".join(clean_title(title).replace("|||"," ") for title in snapshot.get("task_titles",[snapshot["title"]])[:6])
    remote=["am","broadcast","-n","com.codex.statusdisplay/.StatusReceiver","-a","com.codex.status.UPDATE","--es","state",snapshot["state"],"--es","title",clean_title(snapshot["title"]),"--es","task_titles",titles,"--es","phase",snapshot.get("phase",""),"--el","started_at_ms",str(snapshot["started_at_ms"]),"--ei","active_count",str(snapshot["active_count"]),"--ei","quota_5h_percent",str(snapshot.get("quota_5h_percent",-1)),"--ei","quota_7d_percent",str(snapshot.get("quota_7d_percent",-1)),"--ez","commute_available",str(bool(snapshot.get("commute_available",False))).lower(),"--ei","commute_duration_min",str(snapshot.get("commute_duration_min",-1)),"--es","commute_options",snapshot.get("commute_options",""),"--es","commute_mode",snapshot.get("commute_mode","transit"),"--el","commute_arrival_at_ms",str(snapshot.get("commute_arrival_at_ms",0)),"--el","commute_updated_at_ms",str(snapshot.get("commute_updated_at_ms",0)),"--el","host_time_ms",str(now),"--ez","connected","true"]
    args=[ADB,"-s",SERIAL,"shell"," ".join(shlex.quote(part) for part in remote)]
    try:
        result=subprocess.run(args,capture_output=True,text=True,timeout=12)
        if os.environ.get("LX04_DEBUG"):
            print("state=",snapshot["state"],"title=",clean_title(snapshot["title"]),"rc=",result.returncode,flush=True)
            if result.stdout:print(result.stdout.strip(),flush=True)
            if result.stderr:print(result.stderr.strip(),flush=True)
        return result.returncode==0
    except (OSError,subprocess.TimeoutExpired) as error:
        if os.environ.get("LX04_DEBUG"):print("send error:",error,flush=True)
        return False

def main():
    ipc,commute,last,last_sent=IPCMonitor(),CommuteMonitor(),None,0
    completed_since=0
    while True:
        live,fallback=ipc.snapshot(),fallback_snapshot()
        if os.environ.get("LX04_DEBUG"):print("live=",live,"fallback=",fallback,flush=True)
        if fallback and fallback["state"] in {"working","waiting"} and (not live or live["state"] not in {"working","waiting"}):snap=fallback
        else:snap=live or fallback or {"state":"idle","title":"Codex","phase":"","started_at_ms":int(time.time()*1000),"active_count":0}
        if snap["state"]=="completed":
            if not completed_since:completed_since=time.time()
        else:completed_since=0
        display_idle=snap["state"]=="idle" or bool(completed_since and time.time()-completed_since>=COMPLETED_HOLD_SECONDS)
        # Keep the commute page fresh even while Codex is working, because the
        # user can swipe to it at any time.
        snap.update(commute.snapshot(True))
        key=json.dumps(snap,sort_keys=True,ensure_ascii=False)
        if key!=last or time.time()-last_sent>=30:
            if send(snap):last,last_sent=key,time.time()
        if os.environ.get("LX04_ONCE"):return
        time.sleep(1.5)

if __name__=="__main__":main()
