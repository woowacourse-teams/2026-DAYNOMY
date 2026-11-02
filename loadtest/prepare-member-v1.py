#!/usr/bin/env python3
"""Offline member fixture preparation; never connects to a DB or starts a test.

1) python3 loadtest/prepare-member-v1.py seed --api get --namespace lt746-get-01 --out /private/seed.sql
   Execute the generated SQL in DBeaver on daynomy_dev. Export the final result as UTF-8 CSV with headers.
2) python3 loadtest/prepare-member-v1.py tokens --api get --namespace lt746-get-01 \
     --csv /private/members.csv --env-file /private/dev.env --out /private/accounts-member-v1.json
   The env file supplies the deployed dev server's JWT_SECRET (Base64) and optional JWT_ISSUER.
   Do not use synthetic unsigned tokens for member APIs. Local development keys are not dev server keys.
3) Copy the private JSON onto the runner, or run k6 locally. Existing Actions passes
   MEMBER_ACCOUNTS_FILE from params; Secret registration alone does not create that file.
4) Execute the generated post-run verification SQL in DBeaver and compare with result JSON.

Use a fresh namespace per API/RPS/repetition. No reset, member reactivation, or deletion
of existing data is performed. This script refuses to overwrite outputs.
"""

import argparse
import base64
import csv
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import sys
import time
import uuid


def count_for(api, rps, duration):
    match = re.fullmatch(r'([1-9]\d*)(s|m)', duration)
    seconds = int(match[1]) * (60 if match[2] == 'm' else 1) if match else 0
    if not 1 <= rps <= 500 or not 10 <= seconds <= 1800:
        raise ValueError('Use RPS 1–500 and DURATION 10s–30m.')
    return (rps * seconds if api == 'delete' else 1000), seconds


def private_write(path, content):
    path = Path(path)
    # Exclusive creation prevents overwriting an earlier baseline or credentials.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write(content)


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def seed_sql(namespace, count):
    prefix = 's' + hashlib.sha256(namespace.encode()).hexdigest()[:5]
    ns = quote(namespace)
    return f"""-- DBeaver: execute the whole script. Fresh namespace only; no real members are changed.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='120s';
DO $guard$
BEGIN
 IF current_database() <> 'daynomy_dev' THEN RAISE EXCEPTION 'Use daynomy_dev'; END IF;
 IF EXISTS (SELECT 1 FROM members WHERE left(provider_id,length({ns})+1)={ns}||'-')
 THEN RAISE EXCEPTION 'Namespace already exists; use a fresh namespace'; END IF;
END $guard$;
INSERT INTO members(provider,provider_id,email,google_name,nickname,role,status,created_at,updated_at)
SELECT 'GOOGLE',{ns}||'-'||lpad(n::text,7,'0'),
 {ns}||'-'||lpad(n::text,7,'0')||'@example.invalid',NULL,
 '{prefix}_'||n,'USER','ACTIVE',now(),now()
FROM generate_series(0,{count}) s(n);
-- One stored refresh-token row/member exercises withdrawal's token deletion.
-- It is fixture data, not a usable refresh credential.
INSERT INTO refresh_tokens(member_id,token_hash,expires_at,created_at)
SELECT id,encode(sha256(convert_to(provider_id||'-fixture-refresh','UTF8')),'hex'),
 now()+interval '1 day',now() FROM members
WHERE left(provider_id,length({ns})+1)={ns}||'-';
COMMIT;
-- Export THIS result only as CSV with headers, not the preceding statement results.
SELECT substring(provider_id FROM length({ns})+2)::int AS test_number,
 id AS member_id,provider_id,email,google_name,nickname,role,status
FROM members WHERE left(provider_id,length({ns})+1)={ns}||'-'
ORDER BY test_number;
"""


def verification_sql(namespace):
    ns = quote(namespace)
    return f"""-- Read-only post-run verification. Compare withdrawal count (minus warmup) with successful target requests.
SELECT COUNT(*) AS members,
 COUNT(*) FILTER(WHERE status='ACTIVE') AS active_members,
 COUNT(*) FILTER(WHERE status='WITHDRAWN') AS withdrawn_members,
 COUNT(*) FILTER(WHERE withdrawn_at IS NOT NULL) AS withdrawal_timestamps,
 (SELECT COUNT(*) FROM refresh_tokens t JOIN members owner ON owner.id=t.member_id
  WHERE left(owner.provider_id,length({ns})+1)={ns}||'-') AS remaining_refresh_tokens
FROM members WHERE left(provider_id,length({ns})+1)={ns}||'-';
-- GET: all remain ACTIVE, all refresh rows remain. PATCH: names change, states/tokens remain.
-- DELETE: successful requests + one warmup become WITHDRAWN and lose their refresh rows.
SELECT COUNT(*) AS changed_nicknames FROM members
WHERE left(provider_id,length({ns})+1)={ns}||'-'
AND nickname <> 's{hashlib.sha256(namespace.encode()).hexdigest()[:5]}_' ||
 substring(provider_id FROM length({ns})+2)::int;
"""


def env_values(path):
    values = dict(os.environ)
    if path:
        for line in Path(path).read_text(encoding='utf-8').splitlines():
            match = re.fullmatch(r'\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*', line)
            if match:
                value = match[2]
                if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
                    value = value[1:-1]
                values[match[1]] = value
    return values


def b64(value):
    return base64.urlsafe_b64encode(value).decode().rstrip('=')


def mint(member_id, issuer, key, now, expires):
    header = b64(b'{"alg":"HS256","typ":"JWT"}')
    claims = dict(iss=issuer, sub=str(member_id), iat=now, exp=expires,
                  jti=str(uuid.uuid4()), token_type='access', role='USER')
    payload = b64(json.dumps(claims, separators=(',', ':')).encode())
    unsigned = header + '.' + payload
    return unsigned + '.' + b64(hmac.new(key, unsigned.encode(), hashlib.sha256).digest())


def token_fixture(args, count, seconds):
    env = env_values(args.env_file)
    try:
        key = base64.b64decode(env.get('JWT_SECRET', ''), validate=True)
    except Exception:
        raise ValueError('JWT_SECRET must be the deployed dev key in Base64.') from None
    if len(key) < 32:
        raise ValueError('JWT_SECRET must decode to at least 32 bytes.')
    issuer = args.issuer or env.get('JWT_ISSUER', 'daynomy')
    if not issuer or args.token_seconds < seconds + 180:
        raise ValueError('Set issuer and a token lifetime longer than setup + measurement.')
    with open(args.csv, encoding='utf-8-sig', newline='') as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != count + 1:
        raise ValueError('CSV row count differs from the selected API/RPS/duration.')
    accounts = []
    now = int(time.time())
    for index, row in enumerate(rows):
        member_id = int(row['member_id'])
        if (int(row['test_number']) != index or member_id < 1 or
                row['provider_id'] != f'{args.namespace}-{index:07d}' or
                row['email'] != f'{args.namespace}-{index:07d}@example.invalid' or
                row['role'] != 'USER' or row['status'] != 'ACTIVE' or not row['nickname']):
            raise ValueError('CSV must contain ordered ACTIVE test members from this namespace only.')
        account = dict(testNumber=index, memberId=member_id, email=row['email'],
                       name=row['google_name'] or None, nickname=row['nickname'],
                       role='USER', status='ACTIVE',
                       accessToken=mint(member_id, issuer, key, now, now + args.token_seconds))
        if index == 0:
            account['warmup'] = True
        accounts.append(account)
    if len({account['memberId'] for account in accounts}) != len(accounts):
        raise ValueError('Duplicate member IDs in CSV.')
    return dict(schemaVersion=1, api=args.api, namespace=args.namespace,
                nicknamePrefix='m' + hashlib.sha256(args.namespace.encode()).hexdigest()[:5],
                baseUrl=args.base_url, issuer=issuer, accounts=accounts)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('command', choices=['seed', 'tokens', 'verify'])
    parser.add_argument('--api', choices=['get', 'patch', 'delete'], required=True)
    parser.add_argument('--namespace', required=True)
    parser.add_argument('--rps', type=int, default=100)
    parser.add_argument('--duration', default='5m')
    parser.add_argument('--out', required=True)
    parser.add_argument('--csv')
    parser.add_argument('--env-file')
    parser.add_argument('--issuer')
    parser.add_argument('--base-url', default='https://dev.daynomy.com')
    parser.add_argument('--token-seconds', type=int, default=3600)
    args = parser.parse_args(argv)
    if not re.fullmatch(r'lt746-[a-z0-9-]{1,24}', args.namespace):
        parser.error('namespace must start with lt746- followed by 1–24 lowercase letters/digits/hyphens.')
    if not re.fullmatch(r'https://[^\s/@:?#]+(?::\d+)?', args.base_url):
        parser.error('base-url must be an HTTPS origin without credentials.')
    try:
        count, seconds = count_for(args.api, args.rps, args.duration)
        if args.command == 'seed':
            content = seed_sql(args.namespace, count)
        elif args.command == 'verify':
            content = verification_sql(args.namespace)
        else:
            if not args.csv:
                raise ValueError('tokens requires the CSV exported by the seed SQL.')
            content = json.dumps(token_fixture(args, count, seconds), separators=(',', ':'))
        private_write(args.out, content)
    except (ValueError, OSError, KeyError, TypeError):
        # Do not echo exception contents: CSV rows, credentials and paths may be private.
        parser.exit(1, 'Preparation failed. Check API/count, ACTIVE CSV rows, deployed JWT settings and a new writable output path. No values are logged.\n')
    print(f'Prepared {args.command}: {count} measurement members + 1 warmup. No DB connection or load test was started.')


if __name__ == '__main__':
    main()
