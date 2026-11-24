#!/usr/bin/env python3
"""Read development identity, release and API status; never print environment contents."""

import hashlib
import json
import re
import subprocess
import urllib.request
from pathlib import Path


def startup_diagnostics(journal):
    """Extract code identifiers only; never publish raw application log messages."""
    patterns = {
        'exceptions': r'\b(?:[a-zA-Z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error)\b',
        'beans': r"(?:bean with name|parameter named|reference to bean|named) ['\"]([A-Za-z][\w.]{0,120})['\"]",
        'exception_names': r'\b([A-Z][A-Za-z0-9]*(?:Exception|Error))\b',
        'missing_types': r"(?:bean of type|No qualifying bean of type) ['\"]([\w.$]+)['\"]",
        'properties': r"Could not resolve placeholder ['\"]([\w.-]+)['\"]",
        'sql_states': r'SQL State\s*:\s*([A-Z0-9]{5})\b',
        'schema_errors': r'((?:relation|column) "[a-z][a-z0-9_]*" (?:already exists|does not exist))',
        'migrations': r'\b(V\d+__[A-Za-z0-9_]+\.sql)\b',
        'frames': r'\bat ((?:[A-Za-z_$][\w$]*\.)+[\w$<>]+\([A-Za-z_$][\w$]*\.java:\d+\))',
        'known_failures': r'(?:Unable to start web server|Failed to initialize JPA EntityManagerFactory|Validate failed|Migration checksum mismatch|Failed to obtain JDBC Connection|Connection refused|OutOfMemoryError|Not a managed type|Schema-validation: missing table|APPLICATION FAILED TO START|Failed to bind properties|NoSuchMethodError|ClassNotFoundException)',
        'initialization_causes': r'(?:circular reference|circular dependency|Circular depends-on relationship|No LoadTimeWeaver setup|Cannot apply class transformer|No bean named|No qualifying bean|Failed to determine a suitable driver class|Unable to create requested service|Error creating bean|Cannot resolve reference|entityManagerFactory|flywayInitializer|already closed|BeanFactory not initialized|Failed to introspect Class|Lookup method resolution failed)',
        'config_symbols': r'\b(?:class|method|field|constructor) ([A-Za-z_$][\w.$<>]+)\b',
    }
    findings = {key: set() for key in patterns}
    templates = set()
    safe_words = set('a an the is was not no to of for from in on with without cannot could failed failure error creating bean named name type expected actually found missing circular reference dependency depends relationship initialize initialization configuration method constructor parameter argument class transformer load time weaver specified available required defined setting property caused by application context web server start load resolve factory entity manager persistence unit jpa hibernate jdbc connection database schema migration checksum version validation illegal state already exists does not exist table column shutdown closed operation supported must be null invalid unable using annotated build validation'.split())
    def strings(value):
        if isinstance(value, str):
            return [value]
        if isinstance(value, dict):
            return [part for child in value.values() for part in strings(child)]
        if isinstance(value, list):
            return [part for child in value for part in strings(child)]
        return []

    for line in journal.splitlines():
        try:
            message = json.loads(line).get('MESSAGE', '')
        except (ValueError, AttributeError):
            continue
        if isinstance(message, list) and all(isinstance(byte, int) and 0 <= byte <= 255 for byte in message):
            message = bytes(message).decode('utf-8', errors='replace')
        if not isinstance(message, str):
            continue
        try:
            structured = json.loads(message)
            if isinstance(structured, dict):
                message = '\n'.join(strings(structured))
        except ValueError:
            pass
        for key, pattern in patterns.items():
            findings[key].update(re.findall(pattern, message))
        # Emit only a fixed vocabulary from exception reasons, never arbitrary log tokens.
        reasons = re.findall(r'(?:Exception|Error): ([^\n]{1,8000})', message)
        if re.search(r'Exception|Error|ERROR|FAILED', message):
            reasons.append(message[-12000:])
        for reason in reasons:
            templates.add(' '.join(word.lower() if word.lower() in safe_words else '_'
                                   for word in re.findall(r'[A-Za-z]+', reason)))
    result = {key: sorted(values)[:80] for key, values in findings.items() if values}
    result['reason_templates'] = sorted(templates)[:30]
    return result


def main():
    instance = Path('/sys/devices/virtual/dmi/id/board_asset_tag').read_text().strip()
    if instance != 'i-0f2e92d9c7bd8fe06':
        raise RuntimeError('Refusing to inspect a non-development instance')
    print(json.dumps({'instance': instance, 'environment': 'development'}), flush=True)
    service = subprocess.check_output([
        'systemctl', 'show', 'daynomy-dev', '-p', 'ActiveState', '-p', 'MainPID',
        '-p', 'FragmentPath', '-p', 'EnvironmentFiles', '-p', 'DropInPaths'], text=True)
    print(service, flush=True)
    limits = subprocess.check_output([
        'systemctl', 'show', 'daynomy-dev', '-p', 'MemoryCurrent', '-p', 'MemoryPeak',
        '-p', 'MemoryMax', '-p', 'MemorySwapMax', '-p', 'Result', '-p', 'NRestarts'], text=True)
    print(limits, flush=True)
    pid = subprocess.check_output(['systemctl', 'show', 'daynomy-dev', '-p', 'MainPID', '--value'], text=True).strip()
    if pid.isdigit() and pid != '0':
        options = Path('/proc/' + pid + '/cmdline').read_bytes().decode().split('\0')
        print(json.dumps({'jvm_memory_options': [option for option in options if re.fullmatch(
            r'-X(?:mx|ms|ss)\d+[kKmMgG]?|-XX:(?:MaxRAMPercentage|InitialRAMPercentage|MaxMetaspaceSize|ReservedCodeCacheSize)=[\d.]+[kKmMgG]?', option)]}), flush=True)
    journal = subprocess.run([
        'sudo', '-n', 'journalctl', '-u', 'daynomy-dev', '--since', '45 minutes ago',
        '--grep', 'Exception|ERROR|Caused|Error|APPLICATION|Description|Action|JPA|entityManager|Flyway|Migration|exited|Failed|oom|Killed',
        '-n', '2000', '-o', 'json', '--no-pager'], capture_output=True, text=True)
    print(json.dumps({'startup_diagnostics_exit': journal.returncode,
                      'startup_diagnostics': startup_diagnostics(journal.stdout)}), flush=True)
    statuses = sorted(set(re.findall(r'(?:status=|result [\'\"])([0-9A-Za-z/-]+)', journal.stdout)))
    print(json.dumps({'historical_exit_statuses': statuses}), flush=True)
    print(json.dumps({'memory': {line.split(':')[0]: line.split(':')[1].strip()
                               for line in Path('/proc/meminfo').read_text().splitlines()
                               if line.split(':')[0] in ('MemTotal', 'MemAvailable', 'SwapTotal', 'SwapFree')}}), flush=True)
    app = Path('/home/ec2-user/backend')
    owner = app / 'deployment-dev/active-slot'
    slot = owner.read_text().strip() if owner.exists() else None
    if slot not in (None, 'blue', 'green'):
        raise RuntimeError('Unexpected development slot')
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
