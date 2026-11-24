#!/usr/bin/env python3
"""Explicit development-only bootstrap/switch of the already verified running JAR."""
import argparse
import fcntl
import hashlib
import json
import os
import re
from pathlib import Path

import bootstrap_backend
import deploy_backend


def current_artifact(app):
    state = app / 'deployment-dev'
    if state.exists():
        slot = (state / 'active-slot').read_text().strip()
        if slot not in ('blue', 'green'):
            raise RuntimeError('Unknown active development slot')
        return (state / (slot + '.jar')).resolve(strict=True)
    return (app / 'current.jar').resolve(strict=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--operation', choices=('bootstrap', 'switch'), required=True)
    parser.add_argument('--expected-current-sha256', required=True)
    parser.add_argument('--version')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise RuntimeError('Run as root')
    bootstrap_backend.configure_environment('development')
    deploy_backend.require_target_instance()
    if not re.fullmatch(r'[a-f0-9]{64}', args.expected_current_sha256):
        raise RuntimeError('An inspected current JAR SHA256 is required')
    artifact = current_artifact(deploy_backend.APP)
    checksum = hashlib.sha256(artifact.read_bytes()).hexdigest()
    if checksum != args.expected_current_sha256:
        raise RuntimeError('Active JAR changed; inspect latest development state')
    print(json.dumps({'operation': args.operation, 'artifact': artifact.name, 'sha256': checksum}), flush=True)
    if not args.apply:
        if args.operation == 'bootstrap':
            bootstrap_backend.preflight(artifact)
        return
    memory_state = Path('/var/lib/daynomy-dev-memory-943')
    memory_state.mkdir(mode=0o700, exist_ok=True)
    with (memory_state / 'operations.lock').open('a+') as operations:
        fcntl.flock(operations, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.operation == 'bootstrap':
            bootstrap_backend.apply(artifact, checksum)
        else:
            with (deploy_backend.STATE / 'deploy.lock').open('a+') as deployment:
                fcntl.flock(deployment, fcntl.LOCK_EX | fcntl.LOCK_NB)
                deploy_backend.deploy(artifact, args.version or '')


if __name__ == '__main__':
    main()
