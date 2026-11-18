"""Behavior checks for failure counting and the measurement boundaries."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('ci_memory_profile', Path(__file__).with_name('ci-memory-profile.py'))
PROFILE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PROFILE)


class GcEvidenceTest(unittest.TestCase):
    def test_counts_completed_events_once_and_not_normal_allocation_causes(self):
        result = PROFILE.parse_gc('''
[info][gc,init] Heap Region Size: 1M
[info][gc,start] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[info][gc,heap] GC(0) Old regions: 20->21
[info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 300M->200M(1024M) 1.5ms
[info][gc,start] GC(1) Pause Young (Mixed) (G1 Evacuation Pause)
[info][gc] GC(1) To-space exhausted
[info][gc] GC(1) Pause Young (Mixed) (G1 Evacuation Pause) (Evacuation Failure) 971M->795M(1024M) 5.8ms
[info][gc,start] GC(2) Pause Full (Allocation Failure)
[info][gc] GC(2) Pause Full (Allocation Failure) 990M->800M(1024M) 10.0ms
''')
        self.assertEqual(3, result['gc_pause_count'])
        self.assertEqual(1, result['evacuation_failure_count'])
        self.assertEqual(1, result['to_space_exhausted_count'])
        self.assertEqual(1, result['full_gc_count'])
        self.assertEqual(800, result['post_gc_max_mib'])
        self.assertEqual(795, result['post_gc_median_mib'])
        self.assertEqual(800, result['post_gc_p95_mib'])
        self.assertEqual(21, result['old_regions_max_mib'])
        self.assertAlmostEqual(17.3, result['gc_pause_ms'])
        self.assertTrue(result['gc_history_starts_at_zero'])

    def test_missing_gc_is_unknown_not_zero_heap(self):
        result = PROFILE.parse_gc('java.lang.OutOfMemoryError: Java heap space')
        self.assertFalse(result['gc_history_present'])
        self.assertIsNone(result['post_gc_max_mib'])
        self.assertIsNone(result['old_regions_max_mib'])

    def test_normal_allocation_failure_reason_is_not_evacuation_failure(self):
        result = PROFILE.parse_gc('GC(0) Pause Young (Allocation Failure) 300M->90M(1024M) 1.0ms')
        self.assertEqual(0, result['evacuation_failure_count'])
        self.assertEqual(0, result['to_space_exhausted_count'])
        self.assertEqual(0, result['full_gc_count'])


class MemoryBoundaryTest(unittest.TestCase):
    def test_reads_rollup_pss_without_including_pss_anon_twice(self):
        values = PROFILE.fields('Rss: 4000 kB\nPss: 3000 kB\nPss_Anon: 2500 kB\n')
        self.assertEqual(3000, values['Pss'])
        self.assertEqual(4000, values['Rss'])

    def test_app_phase_excludes_compilation_peak_and_does_not_sum_peaks(self):
        rows = []
        for epoch, host, java, app in [(1, 4000, 2000, 0), (3, 3000, 1700, 1200), (5, 3500, 1600, 1300)]:
            rows.append({'epoch_seconds': epoch, 'host_used_mib': host,
                         'host_available_mib': 7000 - host, 'host_swap_used_mib': 0,
                         'java_pss_mib': java, 'app_rss_mib': app + 10, 'app_pss_mib': app,
                         'pss_read_errors': 0, 'app_workers': 1 if app else 0})
        events = [{'event': 'start', 'task': ':app:test', 'epochMillis': 2000},
                  {'event': 'finish', 'task': ':app:test', 'epochMillis': 4000}]
        result = PROFILE.memory_summary(rows, events)
        self.assertEqual(4000, result['build']['host_used_mib'])
        self.assertEqual(3000, result['app_phase']['host_used_mib'])
        self.assertEqual(1700, result['app_phase']['java_pss_mib'])
        self.assertEqual(1200, result['app_phase']['app_pss_mib'])

    def test_failed_app_phase_without_finish_preserves_samples(self):
        sample = {'epoch_seconds': 3, 'host_used_mib': 6500, 'host_available_mib': 500,
                  'host_swap_used_mib': 10, 'java_pss_mib': 2000, 'app_rss_mib': 1500,
                  'app_pss_mib': 1400, 'pss_read_errors': 0, 'app_workers': 1}
        result = PROFILE.memory_summary([sample], [{'event': 'start', 'task': ':app:test', 'epochMillis': 2000}])
        self.assertEqual(1400, result['app_phase']['app_pss_mib'])
        self.assertEqual(500, result['app_phase']['host_min_available_mib'])

    def test_reused_directory_is_rejected_before_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'new output directory'):
                PROFILE.run(Path(directory), Path(directory), 10)


class InterruptedArtifactsTest(unittest.TestCase):
    def test_preserves_valid_and_truncated_xml_and_reports_incomplete_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'checkout/app/build/test-results/test'
            source.mkdir(parents=True)
            (source / 'TEST-broken.xml').write_text('<testsuite tests="1"><testcase')
            (source / 'TEST-complete.xml').write_text(
                '<testsuite tests="1"><testcase classname="Example" name="works"/></testsuite>')
            result, errors = PROFILE.test_results(root / 'checkout', root / 'output')
            self.assertEqual(1, result['app']['tests'])
            self.assertEqual(1, len(errors))
            self.assertEqual(2, len(list((root / 'output/raw/app').glob('*.xml'))))

    def test_preserves_complete_task_events_before_truncated_last_line(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'tasks.jsonl'
            source.write_text('{"event":"start","task":":app:test","epochMillis":1000}\n{"event":')
            events, errors = PROFILE.read_task_events(source)
            self.assertEqual(1, len(events))
            self.assertEqual(':app:test', events[0]['task'])
            self.assertEqual(1, len(errors))


if __name__ == '__main__':
    unittest.main()
