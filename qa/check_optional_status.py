#!/usr/bin/env python3
"""Temporary opt-in test. Uses the real Android consent UI, then restores access to OFF."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

P = 'com.dylan.displaylauncher'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='ADB serial of the test device')
parser.add_argument('--allow-temporary-access', action='store_true', help='Explicitly allow this test to enable and then revoke Accessibility access through the system UI')
args = parser.parse_args()
if not args.allow_temporary_access:
    parser.error('This test changes Accessibility access temporarily; pass --allow-temporary-access only on an authorized test device.')
SERIAL = args.serial
OUT = Path(__file__).resolve().parent / 'evidence/status'
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, binary=False):
    return subprocess.check_output(['adb', '-s', SERIAL, *args], text=not binary, timeout=30)


def shell(*args):
    return adb('shell', *args)


def enabled_setting():
    return shell('settings', 'get', 'secure', 'enabled_accessibility_services').strip()


def tree(step):
    raw = adb('exec-out', 'uiautomator', 'dump', '/dev/tty')
    (OUT / (step + '.xml')).write_text(raw)
    return ET.fromstring(raw[raw.find('<?xml'):raw.rfind('</hierarchy>') + len('</hierarchy>')])


def click(step, *labels):
    root = tree(step)
    nodes = [n for n in root.iter('node') if n.get('text') in labels and n.get('bounds') != '[0,0][0,0]']
    assert nodes, 'Missing UI target: ' + str(labels)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
    shell('input', '-d', '0', 'tap', str((x1 + x2)//2), str((y1 + y2)//2))


def capture(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', '-d', '0', binary=True))


def current_log():
    pid = shell('pidof', '-s', P).strip()
    return adb('logcat', '--pid', pid, '-d', '-s', 'DisplayLauncher:I', '*:S')


def disable():
    shell('am', 'start', '--display', '0', '-a', 'android.settings.ACCESSIBILITY_SETTINGS')
    click('revoke-list', 'Display Launcher status')
    click('revoke-detail', 'Use Display Launcher status')
    click('revoke-confirm', 'STOP')


baseline = enabled_setting()
assert P not in baseline, 'This test expects live-status access to start OFF.'
report = {'temporary_permission_test': True, 'baseline_accessibility_services': baseline}
try:
    shell('am', 'start', '--display', '0', '-n', P + '/.MainActivity')
    click('entry', '开启实时状态', 'Enable live status')
    click('disclosure', '前往设置', 'Open settings')
    click('settings-list', 'Display Launcher status')
    click('settings-detail', 'Use Display Launcher status')
    click('system-consent', 'ALLOW')
    assert P in enabled_setting()
    shell('am', 'start', '--display', '0', '-n', P + '/.MainActivity')
    time.sleep(2)
    logs = current_log()
    assert re.search(r'Status display=1 live=true package=com.loyverse.cds', logs), logs
    capture('live-cds')
    report['live_cds'] = True
    # Change the second display outside this launcher, so history cannot explain the new status.
    shell('am', 'start', '--display', '1', '-a', 'android.settings.SETTINGS')
    time.sleep(2)
    logs = current_log()
    assert re.search(r'Status display=1 live=true package=com.android.settings', logs), logs
    (OUT / 'live-status-log.txt').write_text(logs)
    capture('live-settings')
    report['external_app_change_detected'] = True
    print('PASS live status tracks an external app change', flush=True)
finally:
    if P in enabled_setting():
        disable()
    restored = enabled_setting()
    report['access_restored_to_off'] = P not in restored
    report['other_services_preserved'] = restored == baseline
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    assert restored == baseline, 'Accessibility service state was not restored.'

shell('am', 'start', '--display', '1', '-n', 'com.loyverse.cds/.activities.PairingActivity')
shell('am', 'start', '--display', '0', '-n', P + '/.MainActivity')
time.sleep(2)
root = tree('history-fallback')
assert any(n.get('text') in ['上次打开：Loyverse CDS', 'Last opened: Loyverse CDS'] for n in root.iter('node'))
capture('permission-off-history')
report['history_fallback'] = True
(OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
print('PASS permission revoked, history fallback restored, other services preserved', flush=True)
