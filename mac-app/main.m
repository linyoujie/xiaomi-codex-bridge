#import <Cocoa/Cocoa.h>
#import <WebKit/WebKit.h>

@interface AppDelegate : NSObject <NSApplicationDelegate,NSWindowDelegate>
@property NSWindow *window;
@property WKWebView *webView;
@property NSTask *server;
@end

@implementation AppDelegate
- (void)showWindow:(id)sender {[self.window makeKeyAndOrderFront:nil];[NSApp activateIgnoringOtherApps:YES];}
- (void)quitApplication:(id)sender {[NSApp terminate:nil];}
- (void)buildMenu {
    NSMenu *bar=[NSMenu new];NSMenuItem *root=[NSMenuItem new];[bar addItem:root];
    NSMenu *menu=[NSMenu new];
    NSMenuItem *show=[[NSMenuItem alloc] initWithTitle:@"显示 Xiaomi桥接器" action:@selector(showWindow:) keyEquivalent:@""];show.target=self;[menu addItem:show];
    [menu addItem:NSMenuItem.separatorItem];
    NSMenuItem *quit=[[NSMenuItem alloc] initWithTitle:@"退出 Xiaomi桥接器" action:@selector(quitApplication:) keyEquivalent:@"q"];quit.target=self;[menu addItem:quit];
    root.submenu=menu;NSApp.mainMenu=bar;
}
- (void)loadSettingsWithRetries:(NSInteger)remaining {
    NSURL *url=[NSURL URLWithString:@"http://127.0.0.1:17860"];
    NSMutableURLRequest *probe=[NSMutableURLRequest requestWithURL:url];probe.timeoutInterval=.4;
    [[[NSURLSession sharedSession] dataTaskWithRequest:probe completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
        dispatch_async(dispatch_get_main_queue(),^{
            if(data.length>0){[self.webView loadRequest:[NSURLRequest requestWithURL:url]];}
            else if(remaining>0){dispatch_after(dispatch_time(DISPATCH_TIME_NOW,(int64_t)(.35*NSEC_PER_SEC)),dispatch_get_main_queue(),^{[self loadSettingsWithRetries:remaining-1];});}
            else{[self.webView loadHTMLString:@"<style>body{background:#05090d;color:#edfaff;font:16px -apple-system;padding:36px}h2{color:#62f4df}</style><h2>Xiaomi桥接器启动失败</h2><p>请关闭窗口后重新打开。</p>" baseURL:nil];}
        });
    }] resume];
}
- (void)applicationDidFinishLaunching:(NSNotification *)note {
    NSRect frame=NSMakeRect(0,0,860,720);
    self.window=[[NSWindow alloc] initWithContentRect:frame
        styleMask:NSWindowStyleMaskTitled|NSWindowStyleMaskClosable|NSWindowStyleMaskMiniaturizable|NSWindowStyleMaskResizable
        backing:NSBackingStoreBuffered defer:NO];
    self.window.title=@"Xiaomi桥接器";
    self.window.delegate=self;
    self.window.minSize=NSMakeSize(620,560);
    self.webView=[[WKWebView alloc] initWithFrame:frame];
    self.webView.autoresizingMask=NSViewWidthSizable|NSViewHeightSizable;
    self.window.contentView=self.webView;

    NSString *resources=NSBundle.mainBundle.resourcePath;
    self.server=[NSTask new];
    self.server.executableURL=[NSURL fileURLWithPath:@"/usr/bin/python3"];
    self.server.arguments=@[[resources stringByAppendingPathComponent:@"app.py"]];
    NSMutableDictionary *environment=[NSProcessInfo.processInfo.environment mutableCopy];
    environment[@"XIAOMI_NO_BROWSER"]=@"1";
    self.server.environment=environment;
    NSError *error=nil;
    [self.server launchAndReturnError:&error];

    [self buildMenu];[self.window center];[self.window makeKeyAndOrderFront:nil];[NSApp activateIgnoringOtherApps:YES];
    if(error){
        NSString *html=[NSString stringWithFormat:@"<h2>启动失败</h2><p>%@</p>",error.localizedDescription];
        [self.webView loadHTMLString:html baseURL:nil];
    }else{
        [self loadSettingsWithRetries:30];
    }
}
- (BOOL)windowShouldClose:(NSWindow *)sender {[sender orderOut:nil];return NO;}
- (BOOL)applicationShouldHandleReopen:(NSApplication *)sender hasVisibleWindows:(BOOL)visible {if(!visible)[self showWindow:nil];return YES;}
- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender{return NO;}
- (void)applicationWillTerminate:(NSNotification *)note {
    if(self.server.running){[self.server terminate];[self.server waitUntilExit];}
}
@end

int main(int argc,const char *argv[]){
    @autoreleasepool{NSApplication *app=NSApplication.sharedApplication;AppDelegate *delegate=[AppDelegate new];app.delegate=delegate;[app run];}
    return 0;
}
