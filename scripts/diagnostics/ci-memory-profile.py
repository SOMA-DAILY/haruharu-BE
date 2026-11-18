#!/usr/bin/env python3
"""Run the complete Java build in an isolated Linux CI VM and preserve every outcome."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import re
import runpy
import shutil
import signal
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
MIB = 1024 * 1024
EXPECTED_TESTS = {"admin": 208, "app": 1473, "core": 195}


def fields(text):
    return {key: int(value) for key, value in re.findall(r"^(\w+):\s+(\d+)", text, re.M)}


def host_memory():
    values = fields(Path('/proc/meminfo').read_text())
    return {"host_used_mib": (values['MemTotal'] - values['MemAvailable']) / 1024,
            "host_available_mib": values['MemAvailable'] / 1024,
            "host_total_mib": values['MemTotal'] / 1024,
            "host_swap_used_mib": (values['SwapTotal'] - values['SwapFree']) / 1024}


def oom_kills():
    return int(re.search(r'^oom_kill (\d+)$', Path('/proc/vmstat').read_text(), re.M)[1])


def cgroup_memory():
    unified = re.search(r'^0::(.*)$', Path('/proc/self/cgroup').read_text(), re.M)
    if unified is None:
        return {'available': False, 'reason': 'No cgroup v2 membership'}
    directory = Path('/sys/fs/cgroup') / unified[1].lstrip('/')
    try:
        return {'available': True, 'path': unified[1],
                'max': (directory / 'memory.max').read_text().strip(),
                'events': {key: int(value) for key, value in
                           (line.split() for line in (directory / 'memory.events').read_text().splitlines())}}
    except (FileNotFoundError, PermissionError) as error:
        return {'available': False, 'reason': type(error).__name__}


def java_processes():
    result = []
    for directory in Path('/proc').glob('[0-9]*'):
        try:
            if directory.joinpath('comm').read_text().strip() == 'java':
                command = directory.joinpath('cmdline').read_bytes().replace(b'\0', b' ').decode()
                result.append((int(directory.name), command))
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            pass
    return result


def memory_sample(profile):
    result = host_memory()
    result.update(java_pss_mib=0.0, app_rss_mib=0.0, app_pss_mib=0.0,
                  app_workers=0, pss_read_errors=0)
    for pid, command in java_processes():
        try:
            memory = fields(Path(f'/proc/{pid}/smaps_rollup').read_text())
        except (FileNotFoundError, ProcessLookupError):
            continue  # The process exited between discovery and sampling.
        except PermissionError:
            result['pss_read_errors'] += 1
            continue
        result['java_pss_mib'] += memory['Pss'] / 1024
        if f'-Dtest.context.metrics={profile}/contexts.tsv' in command:
            result['app_rss_mib'] += memory['Rss'] / 1024
            result['app_pss_mib'] += memory['Pss'] / 1024
            result['app_workers'] += 1
    return result


def running_containers():
    return subprocess.check_output(['docker', 'ps', '-q'], text=True).split()


def wait_for_idle(seconds=30):
    deadline = time.monotonic() + seconds
    while True:
        java = java_processes()
        containers = running_containers()
        if not java and not containers:
            return
        if time.monotonic() >= deadline:
            raise RuntimeError(f'Runner is not idle: Java PIDs={[pid for pid, _ in java]}, '
                               f'container count={len(containers)}; refusing contaminated measurement')
        time.sleep(1)


def parse_gc(text):
    pauses = re.findall(
        r'GC\((\d+)\) Pause (Young[^\n]*?|Full[^\n]*?) (\d+)M->(\d+)M\(\d+M\) ([\d.]+)ms', text)
    # Only completed pauses are counted; a start line and an end line share an event ID.
    evacuation = {event for event, kind, *_ in pauses if 'Evacuation Failure' in kind}
    exhausted = {event for event, line in re.findall(r'GC\((\d+)\)([^\n]*)', text)
                 if 'to-space exhausted' in line.lower()}
    post = sorted(int(row[3]) for row in pauses)
    old = [int(value) for value in re.findall(r'Old regions: \d+->(\d+)', text)]
    region = re.search(r'Heap Region Size: (\d+)M', text)
    return {
        'gc_history_present': bool(pauses),
        'gc_history_starts_at_zero': bool(re.search(r'GC\(0\)', text)),
        'full_gc_count': sum(kind.startswith('Full') for _, kind, *_ in pauses),
        'evacuation_failure_count': len(evacuation),
        'to_space_exhausted_count': len(exhausted),
        'gc_pause_count': len(pauses),
        'gc_pause_ms': round(sum(float(row[4]) for row in pauses), 3),
        'post_gc_max_mib': max(post) if post else None,
        'post_gc_median_mib': statistics.median(post) if post else None,
        'post_gc_p95_mib': post[math.ceil(len(post) * .95) - 1] if post else None,
        'old_regions_max_mib': max(old) * int(region[1]) if old and region else None,
    }


def test_results(checkout, output):
    results = {}
    manifest = []
    errors = []
    for module in EXPECTED_TESTS:
        destination = output / 'raw' / module
        destination.mkdir(parents=True)
        totals = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
        for source in (checkout / module / 'build/test-results/test').glob('TEST-*.xml'):
            try:
                shutil.copy2(source, destination / source.name)
            except OSError as error:
                errors.append(f'{module}/{source.name}: copy: {error}')
            try:
                root = ET.parse(source).getroot()
            except (ET.ParseError, OSError) as error:
                errors.append(f'{module}/{source.name}: parse: {error}')
                continue
            for key in totals:
                totals[key] += int(root.get(key, 0))
            manifest.extend((module, case.get('classname'), case.get('name'))
                            for case in root.iter('testcase'))
        results[module] = totals
    (output / 'all-test-manifest.json').write_text(json.dumps(sorted(manifest), indent=2) + '\n')
    return results, errors


def read_task_events(path):
    events, errors = [], []
    if not path.exists():
        return events, ['Missing task events file']
    for number, line in enumerate(path.read_text(errors='replace').splitlines(), 1):
        try:
            event = json.loads(line)
            if not all(key in event for key in ('event', 'task', 'epochMillis')):
                raise ValueError('Missing task event fields')
            events.append(event)
        except (ValueError, TypeError) as error:
            errors.append(f'Task event line {number}: {error}')
    return events, errors


def memory_summary(samples, events):
    starts = [event['epochMillis'] / 1000 for event in events
              if event['task'] == ':app:test' and event['event'] == 'start']
    ends = [event['epochMillis'] / 1000 for event in events
            if event['task'] == ':app:test' and event['event'] == 'finish']
    app = [sample for sample in samples if starts and sample['epoch_seconds'] >= starts[0]
           and (not ends or sample['epoch_seconds'] <= ends[-1])]
    result = {}
    for label, rows in [('build', samples), ('app_phase', app)]:
        result[label] = {key: max(row[key] for row in rows) if rows else None
                         for key in ('host_used_mib', 'host_swap_used_mib', 'java_pss_mib',
                                     'app_rss_mib', 'app_pss_mib')}
        result[label]['host_min_available_mib'] = min(row['host_available_mib'] for row in rows) if rows else None
    result['max_sample_gap_seconds'] = max((b['epoch_seconds'] - a['epoch_seconds']
                                          for a, b in zip(samples, samples[1:])), default=0)
    result['pss_read_errors'] = sum(row['pss_read_errors'] for row in samples)
    result['max_app_workers'] = max((row['app_workers'] for row in samples), default=0)
    return result


def run(checkout, output, timeout_seconds):
    if output.exists():
        raise ValueError('Each run needs a new output directory')
    output.mkdir(parents=True)
    wait_for_idle()
    for module in EXPECTED_TESTS:
        shutil.rmtree(checkout / module / 'build/test-results/test', ignore_errors=True)
    shutil.rmtree(checkout / 'app/build/reports/test-jvm', ignore_errors=True)
    profile = output / 'profile'
    events_file = output / 'tasks.jsonl'
    command = [str(checkout / 'gradlew'), '--no-daemon', '--console=plain',
               '-I', str(HERE / 'test-context-profile.init.gradle'),
               f'-DtestContextProfileDir={profile}',
               f'-DtestContextProfilerSource={HERE / "TestContextMetrics.java"}',
               f'-DtestContextTaskEvents={events_file}', 'build']
    (output / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
    idle = host_memory()
    before_oom = oom_kills()
    before_cgroup = cgroup_memory()
    started = time.monotonic()
    samples = []
    timed_out = False
    sampler_error = None
    with (output / 'build.log').open('w') as log, (output / 'memory.csv').open('w') as csv_file:
        process = subprocess.Popen(command, cwd=checkout, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        writer = None
        try:
            while process.poll() is None:
                sample = {'epoch_seconds': time.time(), **memory_sample(profile)}
                samples.append(sample)
                if writer is None:
                    writer = csv.DictWriter(csv_file, fieldnames=sample)
                    writer.writeheader()
                writer.writerow(sample)
                csv_file.flush()
                if time.monotonic() - started > timeout_seconds:
                    timed_out = True
                    break
                time.sleep(.25)
        except Exception as error:
            sampler_error = repr(error)
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
            return_code = process.wait()

    # These run regardless of build success, unlike Gradle test.doLast.
    elapsed = time.monotonic() - started
    gc_dir = output / 'raw/gc'
    gc_dir.mkdir(parents=True)
    for source in (checkout / 'app/build/reports/test-jvm').glob('gc-*.log*'):
        shutil.copy2(source, gc_dir / source.name)
    gc_logs = list(gc_dir.glob('gc-*.log*'))
    gc = '\n'.join(path.read_text() for path in gc_logs)
    log = (output / 'build.log').read_text()
    events, event_errors = read_task_events(events_file)
    metrics_file = profile / 'contexts.tsv'
    metrics = metrics_file.read_text() if metrics_file.exists() else ''
    heaps = [int(value) for value in re.findall(r'\tmaxHeapBytes=(\d+)', metrics)]
    results, result_errors = test_results(checkout, output)
    finished = {event['task']: event for event in events if event['event'] == 'finish'}
    all_executed = all(f':{module}:test' in finished
                       and not any(finished[f':{module}:test'][key] for key in ('skipped', 'upToDate', 'failure'))
                       for module in EXPECTED_TESTS)
    coverage = all(results[module]['tests'] == count and
                   not any(results[module][key] for key in ('failures', 'errors', 'skipped'))
                   for module, count in EXPECTED_TESTS.items())
    result = {
        'return_code': return_code, 'timed_out': timed_out, 'sampler_error': sampler_error,
        'artifact_parse_errors': event_errors + result_errors,
        'build_seconds': elapsed, 'tests': results, 'all_test_tasks_executed': all_executed,
        'expected_test_scope_passed': coverage, 'actual_app_max_heap_bytes': heaps,
        'oom_message_present': 'OutOfMemoryError' in log or 'OutOfMemoryError' in gc,
        'host_oom_kills_delta': oom_kills() - before_oom,
        'cgroup_before': before_cgroup, 'cgroup_after': cgroup_memory(),
        'idle_host_memory': idle, 'memory': memory_summary(samples, events), 'gc': parse_gc(gc),
    }
    try:
        summarizer = runpy.run_path(str(HERE / 'summarize-test-contexts.py'))['summarize']
        result['contexts'] = summarizer(profile, gc_logs)
    except Exception as error:
        result['context_summary_error'] = repr(error)
    try:
        wait_for_idle()
        result['idle_after_run'] = True
    except Exception as error:
        result['idle_after_run'] = False
        result['cleanup_error'] = str(error)
    result['valid_completed_run'] = (return_code == 0 and not timed_out and sampler_error is None
                                     and not result['artifact_parse_errors']
                                     and coverage and all_executed and heaps == [1024 * MIB]
                                     and result['idle_after_run'] and 'contexts' in result
                                     and result['memory']['pss_read_errors'] == 0
                                     and result['memory']['max_app_workers'] == 1)
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: result[key] for key in ('valid_completed_run', 'return_code', 'gc', 'memory')}), flush=True)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkout', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--timeout-seconds', type=int, default=1200)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true' or not Path('/proc/meminfo').exists():
        parser.error('Use a dedicated GitHub-hosted Linux job; host metrics include every process')
    outcome = run(args.checkout.resolve(), args.output.resolve(), args.timeout_seconds)
    raise SystemExit(0 if outcome['valid_completed_run'] else 1)
