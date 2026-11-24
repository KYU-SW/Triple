"""JUnit 결과와 JaCoCo 보고서를 읽어 실행 요약(마크다운)과 README 배지용 JSON을 만든다.

사용: python3 test_summary.py <app/build 경로> --summary <파일> --badges <폴더>
배지 JSON은 shields.io endpoint 형식이다 (https://shields.io/badges/endpoint-badge).
"""
import argparse
import glob
import json
import os
import xml.etree.ElementTree as ET

ENGINE = 'com/triple/fcare/engine'


def junit(build):
    total = fail = skip = 0
    rows = []
    for f in sorted(glob.glob(os.path.join(build, 'test-results', 'testDebugUnitTest', '*.xml'))):
        r = ET.parse(f).getroot()
        t, fa, er, sk = (int(r.get(k, 0)) for k in ('tests', 'failures', 'errors', 'skipped'))
        total += t; fail += fa + er; skip += sk
        rows.append((r.get('name', '').split('.')[-1], t, fa + er, sk))
    return total, fail, skip, rows


def jacoco(build):
    files = glob.glob(os.path.join(build, 'reports', 'coverage', '**', 'report.xml'), recursive=True)
    if not files:
        return None
    root = ET.parse(files[0]).getroot()

    def pct(el, kind):
        for c in el.findall('counter'):
            if c.get('type') == kind:
                cov, mis = int(c.get('covered')), int(c.get('missed'))
                return 100.0 * cov / (cov + mis) if cov + mis else 0.0, cov, cov + mis
        return None

    out = {}
    for pkg in root.findall('package'):
        if pkg.get('name') == ENGINE:
            out['engine_line'] = pct(pkg, 'LINE')
            out['engine_branch'] = pct(pkg, 'BRANCH')
    out['all_line'] = pct(root, 'LINE')
    return out


def color(p):
    return 'brightgreen' if p >= 90 else 'green' if p >= 80 else 'yellow' if p >= 60 else 'red'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('build')
    ap.add_argument('--summary')
    ap.add_argument('--badges')
    a = ap.parse_args()

    total, fail, skip, rows = junit(a.build)
    cov = jacoco(a.build)
    passed = total - fail - skip

    md = ['## FCare 단위시험 결과', '',
          f'- 시험: **{total}개** 중 통과 {passed}, 실패 {fail}, 건너뜀 {skip}', '']
    if cov and cov.get('engine_line'):
        p, c, n = cov['engine_line']
        md.append(f'- 계산 엔진(`engine` 패키지) 줄 커버리지: **{p:.1f}%** ({c}/{n})')
        if cov.get('engine_branch'):
            bp, bc, bn = cov['engine_branch']
            md.append(f'- 계산 엔진 분기 커버리지: {bp:.1f}% ({bc}/{bn})')
        if cov.get('all_line'):
            ap_, ac, an = cov['all_line']
            md.append(f'- 앱 전체 줄 커버리지(화면 코드 포함, 참고): {ap_:.1f}% ({ac}/{an})')
    else:
        md.append('- 커버리지 보고서를 찾지 못함')
    md += ['', '| 시험 클래스 | 사례 수 | 실패 | 건너뜀 |', '| --- | --- | --- | --- |']
    md += [f'| {n} | {t} | {f} | {s} |' for n, t, f, s in rows]
    text = '\n'.join(md) + '\n'
    print(text)
    if a.summary:
        with open(a.summary, 'a', encoding='utf-8') as f:
            f.write(text)

    if a.badges:
        os.makedirs(a.badges, exist_ok=True)
        tests = {'schemaVersion': 1, 'label': 'unit tests',
                 'message': f'{passed} passed' + (f', {fail} failed' if fail else ''),
                 'color': 'brightgreen' if total and not fail else 'red'}
        with open(os.path.join(a.badges, 'tests.json'), 'w', encoding='utf-8') as f:
            json.dump(tests, f)
        if cov and cov.get('engine_line'):
            p = cov['engine_line'][0]
            badge = {'schemaVersion': 1, 'label': 'engine line coverage', 'message': f'{p:.1f}%', 'color': color(p)}
            with open(os.path.join(a.badges, 'coverage.json'), 'w', encoding='utf-8') as f:
                json.dump(badge, f)

    if total == 0:
        raise SystemExit('시험 결과 파일이 없습니다')


if __name__ == '__main__':
    main()
