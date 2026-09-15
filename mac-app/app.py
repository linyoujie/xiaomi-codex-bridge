#!/usr/bin/env python3
import atexit,html,json,os,signal,subprocess,threading,webbrowser
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs

APP=Path(__file__).resolve().parent
BRAND=APP/"brand-logo.png"
DATA=Path.home()/"Library"/"Application Support"/"XiaomiBridge"
CONFIG=DATA/"config.json"
DEFAULT={"serial":"","origin":"",
    "destination":"","refresh":"300",
    "mode":"transit","adb":"/opt/homebrew/bin/adb","chrome":"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome","login":True}
lock=threading.Lock();bridge=None;message="准备启动"

def load():
    try:return {**DEFAULT,**json.loads(CONFIG.read_text())}
    except Exception:return dict(DEFAULT)

def save(data):DATA.mkdir(parents=True,exist_ok=True);CONFIG.write_text(json.dumps(data,ensure_ascii=False,indent=2))

def restart():
    global bridge,message
    with lock:
        if bridge and bridge.poll() is None:bridge.terminate()
        c=load();env=os.environ.copy();env.update({"LX04_SERIAL":c["serial"],"ADB_PATH":c["adb"],"CHROME_PATH":c["chrome"],
            "LX04_COMMUTE_ORIGIN":c["origin"],"LX04_COMMUTE_DESTINATION":c["destination"],"LX04_COMMUTE_MODE":c["mode"],"LX04_COMMUTE_REFRESH_SECONDS":c["refresh"]})
        DATA.mkdir(parents=True,exist_ok=True);log=open(DATA/"bridge.log","ab",buffering=0)
        bridge=subprocess.Popen(["/usr/bin/python3",str(APP/"codex_lx04_bridge.py")],env=env,stdout=log,stderr=log)
        message=f"运行中 · PID {bridge.pid}"

def shutdown():
    global bridge
    if bridge and bridge.poll() is None:bridge.terminate()

atexit.register(shutdown)
signal.signal(signal.SIGTERM,lambda *_:(shutdown(),raise_system_exit()))
signal.signal(signal.SIGINT,lambda *_:(shutdown(),raise_system_exit()))

def raise_system_exit():
    raise SystemExit(0)

def login_item(enabled):
    target=Path.home()/"Library"/"LaunchAgents"/"com.codex.xiaomi-bridge-app.plist";target.parent.mkdir(parents=True,exist_ok=True)
    if not enabled:
        target.unlink(missing_ok=True);return
    app_bundle=APP.parent.parent
    plist=f'''<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>Label</key><string>com.codex.xiaomi-bridge-app</string><key>ProgramArguments</key><array><string>/usr/bin/open</string><string>{html.escape(str(app_bundle))}</string></array><key>RunAtLoad</key><true/></dict></plist>'''
    target.write_text(plist)

def adb_action(kind):
    c=load();args=[c["adb"],"-s",c["serial"]]
    args += ["get-state"] if kind=="test" else ["install","-r",str(APP/"codex-status.apk")]
    try:return subprocess.run(args,capture_output=True,text=True,timeout=45).stdout.strip() or "成功"
    except Exception as e:return f"失败：{e}"

def page():
    c=load();esc=lambda k:html.escape(str(c[k]),quote=True)
    return f'''<!doctype html><meta charset="utf-8"><title>Xiaomi桥接器</title><style>
    body{{margin:0;background:#05090d;color:#edfaff;font:15px -apple-system;padding:36px}}main{{max-width:760px;margin:auto}}
    .brand{{display:flex;align-items:center;gap:18px;margin-bottom:6px}}.brand img{{width:76px;height:76px;border-radius:21px;box-shadow:0 0 28px #735cff40}}
    h1{{font-size:34px;margin:0;color:#62f4df}}.brand p{{margin:7px 0 0}}p{{color:#8293a9}}label{{display:block;margin:17px 0 6px}}
    input{{box-sizing:border-box;width:100%;padding:11px 13px;border:1px solid #26384a;border-radius:9px;background:#0b131b;color:white;font-size:15px}}
    .modes{{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}}.modes input{{position:absolute;opacity:0;pointer-events:none}}
    .modes label{{margin:0;padding:13px;text-align:center;border:1px solid #26384a;border-radius:10px;background:#0b131b;color:#91a1b5;cursor:pointer;transition:.18s}}
    .modes input:checked+label{{border-color:#42e8d0;background:#0d3737;color:#69f7e2;box-shadow:0 0 18px #38d9c033;font-weight:700}}
    .buttons{{display:flex;gap:10px;margin-top:24px}}button{{padding:11px 16px;border:0;border-radius:9px;background:#38d9c0;color:#03100d;font-weight:700}}
    button.secondary{{background:#172532;color:#d8e7f2}}.status{{margin-top:22px;padding:14px;border:1px solid #1b665c;border-radius:9px;color:#62f4df}}
    .check{{display:flex;gap:9px;align-items:center}}.check input{{width:auto}}
    </style><main><div class="brand"><img src="/brand-logo.png" alt="Codex 粒子萤火虫"><div><h1>Xiaomi桥接器</h1><p>Codex 状态 · LX04 显示 · 隐藏式通勤查询</p></div></div><form method="post">
    <label>设备序列号</label><input name="serial" value="{esc('serial')}"><label>通勤起点</label><input name="origin" value="{esc('origin')}">
    <label>通勤终点</label><input name="destination" value="{esc('destination')}"><label>出行方式</label><div class="modes">
    <input id="transit" type="radio" name="mode" value="transit" {'checked' if c['mode']=='transit' else ''}><label for="transit">公共交通</label>
    <input id="driving" type="radio" name="mode" value="driving" {'checked' if c['mode']=='driving' else ''}><label for="driving">自驾</label>
    <input id="walking" type="radio" name="mode" value="walking" {'checked' if c['mode']=='walking' else ''}><label for="walking">步行</label></div><label>刷新秒数</label><input name="refresh" value="{esc('refresh')}">
    <label>ADB 路径</label><input name="adb" value="{esc('adb')}"><label>Chrome 路径</label><input name="chrome" value="{esc('chrome')}">
    <label class="check"><input type="checkbox" name="login" {'checked' if c['login'] else ''}>登录后自动启动</label>
    <div class="buttons"><button name="action" value="save">保存并重启</button><button class="secondary" name="action" value="test">测试连接</button><button class="secondary" name="action" value="install">安装显示 APK</button></div></form>
    <div class="status">{html.escape(message)}</div><p>Chrome 无头渲染由 Xiaomi桥接器在后台完成，不会打开地图窗口。音响只收到分钟数和预计到家时间。</p></main>'''

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path=="/brand-logo.png" and BRAND.exists():
            self.respond_bytes(BRAND.read_bytes(),"image/png")
        else:self.respond(page())
    def do_POST(self):
        global message
        form=parse_qs(self.rfile.read(int(self.headers.get("Content-Length","0"))).decode());action=form.get("action",["save"])[0]
        c=load()
        for key in ("serial","origin","destination","refresh","adb","chrome"):
            if key in form:c[key]=form[key][0].strip()
        c["mode"]=form.get("mode",["transit"])[0] if form.get("mode",["transit"])[0] in {"transit","driving","walking"} else "transit"
        c["login"]="login" in form;save(c);login_item(c["login"])
        if action=="install":message=adb_action("install")
        elif action=="test":message="ADB："+adb_action("test")
        else:restart()
        self.respond(page())
    def respond(self,data):
        self.respond_bytes(data.encode(),"text/html; charset=utf-8")
    def respond_bytes(self,raw,content_type):
        self.send_response(200);self.send_header("Content-Type",content_type);self.send_header("Cache-Control","no-cache");self.send_header("Content-Length",str(len(raw)));self.end_headers();self.wfile.write(raw)
    def log_message(self,*args):pass

if __name__=="__main__":
    config=load();save(config);login_item(config.get("login",True))
    subprocess.run(["launchctl","bootout",f"gui/{os.getuid()}/com.codex.lx04-status"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    restart();server=ThreadingHTTPServer(("127.0.0.1",17860),Handler)
    if os.environ.get("XIAOMI_NO_BROWSER")!="1":threading.Timer(.5,lambda:webbrowser.open("http://127.0.0.1:17860")).start()
    server.serve_forever()
