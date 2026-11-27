#!/usr/bin/env python3
"""Summarize a JSON export of the minimal allocation JFR. Never exports allocated contents."""
import argparse
from datetime import datetime
from collections import Counter
import json
from pathlib import Path

ALLOWED={'jdk.ObjectAllocationSample','jdk.ExecutionSample','jdk.GarbageCollection',
         'jdk.GCHeapSummary','jdk.ThreadAllocationStatistics','jdk.Metadata','jdk.CheckPoint'}

def summarize(data, window=None):
    counts=Counter();components=Counter();classes=Counter();application=Counter()
    samples=0
    for event in data['recording']['events']:
        kind=event['type']
        assert kind in ALLOWED, 'Unexpected event type; do not publish profile'
        if window:
            stamp=datetime.fromisoformat(event['values']['startTime'])
            if not (datetime.fromisoformat(window['start']) <= stamp <= datetime.fromisoformat(window['end'])):continue
        counts[kind]+=1
        if kind!='jdk.ObjectAllocationSample':continue
        value=event['values'];weight=value['weight'];samples+=1
        frames=(value.get('stackTrace') or {}).get('frames') or []
        names=[f['method']['type']['name'].replace('/','.')+'.'+f['method']['name'] for f in frames]
        # First matching frame attributes sampled allocation; nested categories are not added twice.
        component='other'
        for name in names:
            if name.startswith('org.postgresql.'):component='postgresql_driver';break
            if name.startswith('org.hibernate.'):component='hibernate';break
            if name.startswith('org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator.'):
                component='ledger_calculator';break
        components[component]+=weight
        classes[value['objectClass']['name'].replace('/','.')]+=weight
        first=next((name for name in names if name.startswith('org.grit.daynomy.')),'no_application_frame')
        application[first]+=weight
    return {'eventCounts':dict(counts),'allocationSamples':samples,
            'sampledWeightBytes':sum(components.values()),'components':dict(components),
            'topClasses':classes.most_common(15),'topApplicationFrames':application.most_common(15),
            'limitation':'JFR sampled weights are estimates, not exact allocation totals or peak memory'}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input');parser.add_argument('output');parser.add_argument('--window');args=parser.parse_args()
    result=summarize(json.loads(Path(args.input).read_text()),json.loads(Path(args.window).read_text()) if args.window else None)
    Path(args.output).write_text(json.dumps(result,indent=2))
    print(json.dumps({'samples':result['allocationSamples'],'components':result['components']}))
