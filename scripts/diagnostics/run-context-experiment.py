#!/usr/bin/env python3
"""Execute a balanced sequence of frozen before/after builds on one CI VM."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tarfile

HERE = Path(__file__).resolve().parent
BEFORE = 'c4b8ff4ab55f929f39a7a0dccfc6d9bd1b60d285'
AFTER = '52ad9c17639e7f2446a033fd63011ddac6390fa9'
IMAGES = ['postgres:16-alpine', 'valkey/valkey:8-alpine', 'testcontainers/ryuk:0.14.0']


def capture(command, cwd=None):
    return subprocess.check_output(command, cwd=cwd, stderr=subprocess.STDOUT, text=True).strip()


def export_revision(repository, revision, destination):
    destination.mkdir(parents=True)
    with subprocess.Popen(['git', 'archive', revision], cwd=repository, stdout=subprocess.PIPE) as process:
        with tarfile.open(fileobj=process.stdout, mode='r|') as archive:
            archive.extractall(destination, filter='data')
        if process.wait() != 0:
            raise RuntimeError(f'Cannot export {revision}')


def run_sequence(sequence, output):
    repository = Path(os.environ['GITHUB_WORKSPACE'])
    output.mkdir(parents=True, exist_ok=False)
    workspace = Path(os.environ['RUNNER_TEMP']) / 'context-experiment-sources'
    workspace.mkdir(exist_ok=False)
    profile = runpy.run_path(str(HERE / 'ci-memory-profile.py'))
    metadata = {
        'sequence': sequence, 'revisions': {'A': BEFORE, 'B': AFTER},
        'harness_revision': capture(['git', 'rev-parse', 'HEAD'], repository),
        'runner_image': {key: os.environ.get(key) for key in ('ImageOS', 'ImageVersion', 'RUNNER_ARCH')},
        'os': Path('/etc/os-release').read_text(), 'kernel': capture(['uname', '-r']),
        'cpu_models': sorted(set(re.findall(r'^model name\s*:\s*(.*)$', Path('/proc/cpuinfo').read_text(), re.M))),
        'cpu_count': os.cpu_count(), 'memory': profile['host_memory'](),
        'java': capture(['java', '-version']), 'docker': capture(['docker', '--version']),
        'changed_paths': capture(['git', 'diff', '--name-only', BEFORE, AFTER], repository).splitlines(),
        'measurement_sha256': {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                               for path in HERE.iterdir() if path.suffix in ('.py', '.java', '.gradle')},
    }
    (output / 'environment.json').write_text(json.dumps(metadata, indent=2) + '\n')
    image_records = {}
    for image in IMAGES:
        with (output / ('pull-' + image.replace('/', '-').replace(':', '-') + '.log')).open('w') as log:
            subprocess.run(['docker', 'pull', image], stdout=log, stderr=subprocess.STDOUT, check=True)
        image_records[image] = json.loads(capture(['docker', 'image', 'inspect', image]))[0]['RepoDigests']
    metadata['image_digests'] = image_records
    (output / 'environment.json').write_text(json.dumps(metadata, indent=2) + '\n')
    for variant, revision in [('A', BEFORE), ('B', AFTER)]:
        checkout = workspace / variant
        export_revision(repository, revision, checkout)
        with (output / f'prepare-{variant}.log').open('w') as log:
            subprocess.run([str(checkout / 'gradlew'), '--no-daemon', '--console=plain',
                            '-I', str(HERE / 'test-context-profile.init.gradle'),
                            f'-DtestContextProfilerSource={HERE / "TestContextMetrics.java"}',
                            'testClasses', ':app:compileContextProfiler'], cwd=checkout,
                           stdout=log, stderr=subprocess.STDOUT, check=True, timeout=1200)
        profile['wait_for_idle']()
    outcomes = []
    for index, variant in enumerate(sequence, 1):
        print(f'Starting run {index}/{len(sequence)}: {variant}', flush=True)
        destination = output / f'{index}-{variant}'
        try:
            # A fresh sampler process prevents XML parsing/allocator retention from carrying
            # over into the next run's whole-VM memory measurement.
            subprocess.run([sys.executable, '-u', '-B', str(HERE / 'ci-memory-profile.py'),
                            '--checkout', str(workspace / variant), '--output', str(destination),
                            '--timeout-seconds', '1200'], check=False)
            result = json.loads((destination / 'result.json').read_text())
        except Exception as error:
            (output / f'{index}-{variant}-harness-error.txt').write_text(repr(error))
            raise  # Preserve artifacts and refuse to measure after unknown cleanup state.
        outcomes.append({'position': index, 'variant': variant, 'valid': result['valid_completed_run']})
        (output / 'runs.json').write_text(json.dumps(outcomes, indent=2) + '\n')
        if not result['idle_after_run']:
            raise RuntimeError('Previous build did not release its processes; stopping this sequence')
    manifests = [json.loads((output / f'{index}-{variant}' / 'all-test-manifest.json').read_text())
                 for index, variant in enumerate(sequence, 1)]
    identical = all(manifest == manifests[0] for manifest in manifests)
    final = {'runs': outcomes, 'identical_test_manifests': identical,
             'all_runs_valid': identical and all(outcome['valid'] for outcome in outcomes)}
    (output / 'sequence-result.json').write_text(json.dumps(final, indent=2) + '\n')
    return final['all_runs_valid']


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sequence', choices=['ABBA', 'BAAB'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        parser.error('This experiment requires a dedicated GitHub-hosted Linux job')
    raise SystemExit(0 if run_sequence(args.sequence, args.output.resolve()) else 1)
