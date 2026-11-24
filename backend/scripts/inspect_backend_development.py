#!/usr/bin/env python3
"""Read development identity, release and API status; never print environment contents."""

import hashlib
import json
import re
import subprocess
import urllib.request
from pathlib import Path


def main():
    instance = Path('/sys/devices/virtual/dmi/id/board_asset_tag').read_text().strip()
    if instance != 'i-0f2e92d9c7bd8fe06':
        raise RuntimeError('Refusing to inspect a non-development instance')
    print(json.dumps({'instance': instance, 'environment': 'development'}), flush=True)
    service = subprocess.check_output([
        'systemctl', 'show', 'daynomy-dev', '-p', 'ActiveState', '-p', 'MainPID',
        '-p', 'FragmentPath', '-p', 'EnvironmentFiles', '-p', 'DropInPaths'], text=True)
    print(service, flush=True)
    print(json.dumps({'memory': {line.split(':')[0]: line.split(':')[1].strip()
                               for line in Path('/proc/meminfo').read_text().splitlines()
                               if line.split(':')[0] in ('MemTotal', 'MemAvailable', 'SwapTotal', 'SwapFree')}}), flush=True)
    app = Path('/home/ec2-user/backend')
    owner = app / 'deployment-dev/active-slot'
    slot = owner.read_text().strip() if owner.exists() else None
    if slot not in (None, 'blue', 'green'):
        raise RuntimeError('Unexpected development slot')
    active_unit='daynomy-dev-green' if slot=='green' else 'daynomy-dev'
    properties=dict(line.split('=',1) for line in subprocess.check_output([
        'systemctl','show',active_unit,'-p','ActiveState','-p','MainPID','-p','NRestarts'],text=True).splitlines())
    print(json.dumps({'active_service':active_unit,'properties':properties}),flush=True)
    if properties['ActiveState']!='active' or properties['MainPID']=='0' or properties['NRestarts']!='0':
        raise RuntimeError('Active development JVM is stopped or restarted')
    jar = (app / ('deployment-dev/' + slot + '.jar') if slot else app / 'current.jar').resolve(strict=True)
    print(json.dumps({'active_slot': slot, 'release': jar.name,
                      'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}), flush=True)
    nginx = subprocess.run(['sudo', '-n', 'nginx', '-T'], capture_output=True, text=True)
    selected = [line.strip() for line in nginx.stdout.splitlines()
                if re.fullmatch(r'# configuration file /etc/nginx/[^\r\n]+:', line)
                or re.fullmatch(r'\s*server_name\s+[a-zA-Z0-9_. -]+;\s*', line)
                or re.fullmatch(r'\s*proxy_pass http://(?:127\.0\.0\.1:\d+|daynomy_[a-z_]+);\s*', line)]
    print(json.dumps({'nginx_exit': nginx.returncode, 'routing': selected}), flush=True)
    with urllib.request.urlopen('http://127.0.0.1:8081/actuator/health', timeout=5) as response:
        if json.load(response)['status'] != 'UP':
            raise RuntimeError('Development backend is unhealthy')
    for base in ('http://127.0.0.1:8080', 'https://dev.daynomy.com'):
        # When green serves, 8080 may intentionally be stopped. Inspect active slot directly.
        if base.endswith(':8080') and slot == 'green':
            base = 'http://127.0.0.1:8082'
        with urllib.request.urlopen(base + '/api/news?page=1&size=1', timeout=10) as response:
            body = json.load(response)
            if not isinstance(body.get('items'), list):
                raise RuntimeError('Development news response is invalid')
            actual = response.headers.get('X-Daynomy-Slot')
            if slot:
                if actual != slot:
                    raise RuntimeError('Development API did not reach the active slot')
            print(json.dumps({'api': base, 'status': response.status, 'slot': actual}), flush=True)


if __name__ == '__main__':
    main()
