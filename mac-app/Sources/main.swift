import AppKit

final class BridgeManager {
    static let shared = BridgeManager()
    private var process: Process?
    var onStatus: ((String) -> Void)?

    func start() {
        stop()
        guard let script = Bundle.main.path(forResource: "codex_lx04_bridge", ofType: "py") else {
            onStatus?("找不到内置桥接器")
            return
        }
        let defaults = UserDefaults.standard
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/usr/bin/python3")
        task.arguments = [script]
        var env = ProcessInfo.processInfo.environment
        env["LX04_SERIAL"] = defaults.string(forKey: "serial") ?? ""
        env["ADB_PATH"] = defaults.string(forKey: "adbPath") ?? "/opt/homebrew/bin/adb"
        env["CHROME_PATH"] = defaults.string(forKey: "chromePath") ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        env["LX04_COMMUTE_ORIGIN"] = defaults.string(forKey: "origin") ?? ""
        env["LX04_COMMUTE_DESTINATION"] = defaults.string(forKey: "destination") ?? ""
        env["LX04_COMMUTE_REFRESH_SECONDS"] = String(max(60, defaults.integer(forKey: "refreshSeconds")))
        task.environment = env
        let logDirectory = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Logs/XiaomiBridge", isDirectory: true)
        try? FileManager.default.createDirectory(at: logDirectory, withIntermediateDirectories: true)
        let logURL = logDirectory.appendingPathComponent("bridge.log")
        if !FileManager.default.fileExists(atPath: logURL.path) { FileManager.default.createFile(atPath: logURL.path, contents: nil) }
        if let handle = try? FileHandle(forWritingTo: logURL) {
            try? handle.seekToEnd(); task.standardOutput = handle; task.standardError = handle
        }
        task.terminationHandler = { [weak self] _ in DispatchQueue.main.async { self?.onStatus?("桥接器已停止") } }
        do { try task.run(); process = task; onStatus?("运行中 · PID \(task.processIdentifier)") }
        catch { onStatus?("启动失败：\(error.localizedDescription)") }
    }

    func stop() { if let task = process, task.isRunning { task.terminate() }; process = nil }

    func runADB(_ arguments: [String], completion: @escaping (String) -> Void) {
        let path = UserDefaults.standard.string(forKey: "adbPath") ?? "/opt/homebrew/bin/adb"
        DispatchQueue.global(qos: .userInitiated).async {
            let task = Process(); let pipe = Pipe(); task.executableURL = URL(fileURLWithPath: path)
            task.arguments = arguments; task.standardOutput = pipe; task.standardError = pipe
            do { try task.run(); task.waitUntilExit(); let data = pipe.fileHandleForReading.readDataToEndOfFile()
                let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                DispatchQueue.main.async { completion(output.isEmpty ? (task.terminationStatus == 0 ? "成功" : "失败") : output) }
            } catch { DispatchQueue.main.async { completion(error.localizedDescription) } }
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var window: NSWindow!
    private let serial = NSTextField(), origin = NSTextField(), destination = NSTextField()
    private let refresh = NSTextField(), adb = NSTextField(), chrome = NSTextField(), status = NSTextField(labelWithString: "准备启动")
    private let login = NSButton(checkboxWithTitle: "登录后自动启动", target: nil, action: nil)

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        let defaults = UserDefaults.standard
        defaults.register(defaults: ["serial":"", "origin":"",
            "destination":"", "refreshSeconds":300,
            "adbPath":"/opt/homebrew/bin/adb", "chromePath":"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"])
        window = NSWindow(contentRect: NSRect(x:0,y:0,width:620,height:510), styleMask:[.titled,.closable,.miniaturizable], backing:.buffered, defer:false)
        window.title = "Xiaomi桥接器"; window.center(); window.isReleasedWhenClosed = false
        let stack = NSStackView(); stack.orientation = .vertical; stack.spacing = 12; stack.alignment = .leading; stack.translatesAutoresizingMaskIntoConstraints = false
        window.contentView?.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: window.contentView!.leadingAnchor,constant:24),stack.trailingAnchor.constraint(equalTo:window.contentView!.trailingAnchor,constant:-24),stack.topAnchor.constraint(equalTo:window.contentView!.topAnchor,constant:22)])
        let title = NSTextField(labelWithString:"Xiaomi桥接器"); title.font = .systemFont(ofSize:26,weight:.bold); stack.addArrangedSubview(title)
        let subtitle = NSTextField(labelWithString:"Codex 状态、通勤时间与 LX04 显示控制"); subtitle.textColor = .secondaryLabelColor; stack.addArrangedSubview(subtitle)
        addRow(stack,"设备序列号",serial,defaults.string(forKey:"serial")!)
        addRow(stack,"通勤起点",origin,defaults.string(forKey:"origin")!)
        addRow(stack,"通勤终点",destination,defaults.string(forKey:"destination")!)
        addRow(stack,"刷新秒数",refresh,String(defaults.integer(forKey:"refreshSeconds")))
        addRow(stack,"ADB 路径",adb,defaults.string(forKey:"adbPath")!)
        addRow(stack,"Chrome 路径",chrome,defaults.string(forKey:"chromePath")!)
        login.state = FileManager.default.fileExists(atPath: launchAgentURL().path) ? .on : .off; login.target=self; login.action=#selector(toggleLogin); stack.addArrangedSubview(login)
        let buttons=NSStackView();buttons.orientation = .horizontal;buttons.spacing=10
        buttons.addArrangedSubview(button("保存并重启",#selector(save)))
        buttons.addArrangedSubview(button("测试连接",#selector(testConnection)))
        buttons.addArrangedSubview(button("安装显示 APK",#selector(installAPK)))
        buttons.addArrangedSubview(button("查看日志",#selector(openLogs)));stack.addArrangedSubview(buttons)
        status.textColor = .systemGreen; stack.addArrangedSubview(status)
        window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps:true)
        BridgeManager.shared.onStatus = { [weak self] value in self?.status.stringValue=value }
        BridgeManager.shared.start()
    }

    private func addRow(_ stack:NSStackView,_ label:String,_ field:NSTextField,_ value:String){
        let row=NSStackView();row.orientation = .horizontal;row.spacing=12
        let caption=NSTextField(labelWithString:label);caption.alignment = .right;caption.widthAnchor.constraint(equalToConstant:95).isActive=true
        field.stringValue=value;field.widthAnchor.constraint(equalToConstant:440).isActive=true;row.addArrangedSubview(caption);row.addArrangedSubview(field);stack.addArrangedSubview(row)
    }
    private func button(_ title:String,_ action:Selector)->NSButton{let b=NSButton(title:title,target:self,action:action);b.bezelStyle = .rounded;return b}
    @objc private func save(){let d=UserDefaults.standard;d.set(serial.stringValue,forKey:"serial");d.set(origin.stringValue,forKey:"origin");d.set(destination.stringValue,forKey:"destination");d.set(Int(refresh.stringValue) ?? 300,forKey:"refreshSeconds");d.set(adb.stringValue,forKey:"adbPath");d.set(chrome.stringValue,forKey:"chromePath");BridgeManager.shared.start()}
    @objc private func testConnection(){save();BridgeManager.shared.runADB(["-s",serial.stringValue,"get-state"]){[weak self] in self?.status.stringValue="ADB：\($0)"}}
    @objc private func installAPK(){guard let apk=Bundle.main.path(forResource:"codex-status",ofType:"apk") else {status.stringValue="找不到内置 APK";return};BridgeManager.shared.runADB(["-s",serial.stringValue,"install","-r",apk]){[weak self] in self?.status.stringValue=$0}}
    @objc private func openLogs(){NSWorkspace.shared.open(FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Logs/XiaomiBridge/bridge.log"))}
    private func launchAgentURL()->URL{FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/LaunchAgents/com.codex.xiaomi-bridge-app.plist")}
    @objc private func toggleLogin(){let url=launchAgentURL();if login.state == .on {let object:[String:Any]=["Label":"com.codex.xiaomi-bridge-app","ProgramArguments":["/usr/bin/open","-a",Bundle.main.bundlePath],"RunAtLoad":true];if let data=try? PropertyListSerialization.data(fromPropertyList:object,format:.xml,options:0){try? FileManager.default.createDirectory(at:url.deletingLastPathComponent(),withIntermediateDirectories:true);try? data.write(to:url)}}else{try? FileManager.default.removeItem(at:url)}}
    func applicationWillTerminate(_ notification:Notification){BridgeManager.shared.stop()}
}

let app=NSApplication.shared
let delegate=AppDelegate()
app.delegate=delegate
app.run()
