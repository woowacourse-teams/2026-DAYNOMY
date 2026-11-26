"""Keep the previous frontend until the same commit's backend deployment succeeds."""
import json
import os
import re
import subprocess
import time
import urllib.parse
import urllib.request


def backend_changed(before, after):
    if before == '0' * 40:
        return True
    if not all(re.fullmatch('[a-f0-9]{40}', value or '') for value in (before, after)):
        raise RuntimeError('Invalid release commit identity')
    result = subprocess.run(['git', 'diff', '--quiet', before, after, '--',
                             'backend/', '.github/workflows/backend-cd.yml'], check=False)
    if result.returncode not in (0, 1):
        raise RuntimeError('Cannot determine backend changes')
    return result.returncode == 1


def deployment_result(runs, sha):
    matching = [run for run in runs if run.get('head_sha') == sha
                and run.get('head_branch') == 'main' and run.get('event') == 'push']
    if not matching:
        return None
    latest = max(matching, key=lambda run: run['id'])
    if latest.get('status') != 'completed':
        return None
    if latest.get('conclusion') != 'success':
        raise RuntimeError('Same-commit production backend deployment did not succeed; frontend remains unchanged')
    return latest['id']


def wait_for_success(fetch, sha, timeout=1800):
    deadline = time.monotonic() + timeout
    while True:
        result = deployment_result(fetch(), sha)
        if result is not None:
            return result
        if time.monotonic() >= deadline:
            raise RuntimeError('Timed out waiting for same-commit backend deployment; frontend remains unchanged')
        time.sleep(10)


def main():
    if os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise RuntimeError('Production release gate requires main')
    sha = os.environ['GITHUB_SHA']
    if not backend_changed(os.environ['RELEASE_BEFORE'], sha):
        print('Frontend-only push: no backend deployment is required.')
        return
    repository = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise RuntimeError('Invalid repository identity')
    token = os.environ['GITHUB_TOKEN']
    url = ('https://api.github.com/repos/' + repository
           + '/actions/workflows/backend-cd.yml/runs?'
           + urllib.parse.urlencode({'head_sha': sha, 'event': 'push', 'per_page': 10}))

    def fetch():
        request = urllib.request.Request(url, headers={
            'Authorization': 'Bearer ' + token,
            'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
        })
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)['workflow_runs']

    run = wait_for_success(fetch, sha)
    print(json.dumps({'production_backend_run': run, 'commit': sha, 'backend_ready': True}))


if __name__ == '__main__':
    main()
