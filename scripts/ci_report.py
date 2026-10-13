import json
from pathlib import Path
import xml.etree.ElementTree as ET
import os
suites = [ET.parse(p).getroot() for p in Path('backend/target/surefire-reports').glob('TEST-*.xml')]
tests = sum(int(s.get('tests',0)) for s in suites)
failures = sum(int(s.get('failures',0))+int(s.get('errors',0)) for s in suites)
skipped = sum(int(s.get('skipped',0)) for s in suites)
xml = Path('backend/target/site/jacoco/jacoco.xml')
coverage = None
if xml.exists():
    counter = next(c for c in ET.parse(xml).getroot().findall('counter') if c.get('type')=='LINE')
    hit, miss = int(counter.get('covered')), int(counter.get('missed'))
    coverage = round(100*hit/(hit+miss),2) if hit+miss else 0
result = {'tests':tests,'failures_and_errors':failures,'skipped':skipped,'line_coverage_percent':coverage,
          'commit':os.environ.get('GITHUB_SHA'), 'run_url':f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}" if os.environ.get('GITHUB_RUN_ID') else None}
Path('reports').mkdir(exist_ok=True)
Path('reports/ci-results.json').write_text(json.dumps(result,indent=2))
coverage_label = f"{coverage}%" if coverage is not None else "Not generated because verification stopped"
summary=f"## FA2 verification\n\n| Metric | Actual result |\n|---|---:|\n| Tests | {tests} |\n| Failures/errors | {failures} |\n| Skipped | {skipped} |\n| Whole backend line coverage | {coverage_label} |\n\nExpiry gate tests exercise yesterday, today, 7 and 8 days, impossible dates and leap day. Coverage is reported for the whole backend, including OCR and email code.\n\nDemo deployment runs temporarily on this runner. It is not a public production deployment.\n"
Path('reports/summary.md').write_text(summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'],'a') as f: f.write(summary)
print(summary)
