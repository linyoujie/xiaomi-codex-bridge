import importlib.util
import pathlib
import unittest

MODULE_PATH=pathlib.Path(__file__).parents[1]/"mac"/"codex_lx04_bridge.py"
SPEC=importlib.util.spec_from_file_location("codex_lx04_bridge",MODULE_PATH)
bridge=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bridge)

class FakeClock:
    def __init__(self,value=1_000_000):self.value=value
    def __call__(self):return self.value
    def advance(self,seconds):self.value+=seconds

class CommuteParserTests(unittest.TestCase):
    def test_minutes(self):
        self.assertEqual(bridge.parse_commute_response('Transit 58 min 12:00 PM—12:58 PM'),58)

    def test_hours_and_minutes(self):
        self.assertEqual(bridge.parse_commute_response('"Transit","1 hr 7 min","12:00 PM—1:07 PM"'),67)
        self.assertEqual(bridge.parse_commute_response('Transit 1 hr 12:00 PM—1:00 PM'),60)

    def test_chinese(self):
        self.assertEqual(bridge.parse_commute_response('公共交通：1小时 5分钟'),65)

    def test_ignores_unrelated_maps_controls(self):
        self.assertIsNone(bridge.parse_commute_response('[[900,"15 min"],[1800,"30 min"]] travelmode=transit'))

    def test_no_route(self):
        self.assertIsNone(bridge.parse_commute_response('No transit routes available'))

class CommuteMonitorTests(unittest.TestCase):
    def test_idle_refresh_schedule_and_running_pause(self):
        clock=FakeClock();calls=[]
        monitor=bridge.CommuteMonitor(lambda:calls.append(clock()) or 61,clock)
        self.assertFalse(monitor.snapshot(False)["commute_available"])
        first=monitor.snapshot(True)
        self.assertTrue(first["commute_available"]);self.assertEqual(len(calls),1)
        clock.advance(299);monitor.snapshot(True);self.assertEqual(len(calls),1)
        clock.advance(1);monitor.snapshot(True);self.assertEqual(len(calls),2)
        clock.advance(600);monitor.snapshot(False);self.assertEqual(len(calls),2)
        monitor.snapshot(True);self.assertEqual(len(calls),3)

    def test_failure_keeps_cache_then_expires(self):
        clock=FakeClock();answers=iter([61,RuntimeError("offline"),RuntimeError("offline")])
        def fetch():
            value=next(answers)
            if isinstance(value,Exception):raise value
            return value
        monitor=bridge.CommuteMonitor(fetch,clock)
        self.assertTrue(monitor.snapshot(True)["commute_available"])
        clock.advance(bridge.COMMUTE_STALE_SECONDS+1)
        cached=monitor.snapshot(True)
        self.assertTrue(cached["commute_available"]);self.assertEqual(cached["commute_duration_min"],61)
        clock.advance(bridge.COMMUTE_EXPIRE_SECONDS-bridge.COMMUTE_STALE_SECONDS)
        self.assertFalse(monitor.snapshot(True)["commute_available"])

if __name__=="__main__":unittest.main()
