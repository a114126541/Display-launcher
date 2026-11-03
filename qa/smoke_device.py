#!/usr/bin/env python3
"""Exercise the real launcher UI and assert the resulting display assignments."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
P = 'com.dylan.displaylauncher'
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True, help='ADB serial of the test device')
parser.add_argument('--output', type=Path, default=ROOT / 'qa/evidence')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
results = []


def adb(*command, binary=False):
    return subprocess.check_output(['adb', '-s', args.serial, *command], text=not binary, timeout=30)


def shell(*command):
    return adb('shell', *command)


def dump(name):
    for attempt in range(4):
        raw = adb('exec-out', 'uiautomator', 'dump', '/dev/tty')
        begin, end = raw.find('<?xml'), raw.rfind('</hierarchy>')
        if begin >= 0 and end >= 0:
            root = ET.fromstring(raw[begin:end + len('</hierarchy>')])
            (args.output / (name + '.xml')).write_text(raw)
            return root
        time.sleep(0.5)
    raise AssertionError('UI tree unavailable: ' + name)


def node(root, key, value):
    found = [n for n in root.iter('node') if n.get(key) == value and n.get('bounds') != '[0,0][0,0]']
    assert found, f'UI target missing: {key}={value}'
    return found[0]


def tap(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    shell('input', '-d', '0', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def screenshot(name, display=0):
    (args.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', '-d', str(display), binary=True))


def open_tool(name):
    shell('am', 'force-stop', P)
    shell('am', 'start', '--display', '0', '-n', P + '/.MainActivity')
    root = dump(name)
    node(root, 'text', 'Display Launcher')
    return root


def is_chinese(root):
    return any(n.get('content-desc') == '切换到英文' for n in root.iter('node'))


def set_language(root, chinese, name):
    if is_chinese(root) != chinese:
        tap(node(root, 'content-desc', '切换到英文' if is_chinese(root) else 'Switch to Chinese'))
        root = dump(name)
    assert is_chinese(root) == chinese
    return root


def search(root, query, name):
    description = '搜索应用' if is_chinese(root) else 'Search apps'
    tap(node(root, 'content-desc', description))
    # Wait for the IME/focus transition before injecting text on this dual-display firmware.
    root = dump(name + '-focused')
    assert node(root, 'content-desc', description).get('focused') == 'true'
    shell('input', '-d', '0', 'text', query)
    root = dump(name + '-typed')
    assert node(root, 'content-desc', description).get('text') == query, 'Search text was not entered'
    shell('input', '-d', '0', 'keyevent', 'KEYCODE_BACK')
    return dump(name)


def launch(label, query, name):
    root = open_tool(name + '-list')
    root = search(root, query, name + '-search')
    row = ('在客屏打开 ' if is_chinese(root) else 'Open ') + label + ('' if is_chinese(root) else ' on the second display')
    tap(node(root, 'content-desc', row))


def assert_displays(secondary_package, name, primary_package=P):
    last = ''
    for attempt in range(16):
        last = shell('dumpsys', 'activity', 'activities')
        parts = re.split(r'Display #(\d+) \(activities from top to bottom\):', last)
        resumed = {}
        for i in range(1, len(parts), 2):
            matched = re.search(r'mResumedActivity:\s*(.*)', parts[i + 1])
            resumed[int(parts[i])] = matched.group(1) if matched else ''
        if primary_package + '/' in resumed.get(0, '') and secondary_package + '/' in resumed.get(1, ''):
            (args.output / (name + '-activities.txt')).write_text(last)
            results.append({'case': name, 'passed': True, 'resumed': resumed})
            print('PASS', name, flush=True)
            return
        time.sleep(0.6)
    (args.output / (name + '-failed-activities.txt')).write_text(last)
    raise AssertionError(name + ': primary / secondary app assignment failed')


try:
    root = set_language(open_tool('initial'), True, 'chinese')
    screenshot('interface-zh')
    root = set_language(root, False, 'english')
    screenshot('interface-en')
    root = open_tool('language-persistence')
    assert not is_chinese(root), 'Language selection did not persist'
    results.append({'case': 'English and Chinese switching, persistence across process restart', 'passed': True})
    root = search(root, 'zxq937', 'no-results')
    node(root, 'text', 'No matching apps')
    results.append({'case': 'Unmatched search shows an empty state', 'passed': True})
    shell('am', 'force-stop', 'com.loyverse.cds')
    launch('Loyverse CDS', 'CDS', 'cds-cold-start')
    assert_displays('com.loyverse.cds', 'cds-cold-start')
    shell('input', '-d', '0', 'keyevent', 'KEYCODE_TAB')
    root = dump('cds-launch-history')
    node(root, 'text', 'Last opened: Loyverse CDS')
    results.append({'case': 'Launch history is shown while the tool remains on the primary display', 'passed': True})
    screenshot('main-stays-after-launch')
    screenshot('cds-after-cold-start', 1)
    tap(node(root, 'content-desc', 'Go to desktop'))
    assert_displays('com.loyverse.cds', 'desktop-button', 'com.android.launcher3')
    launch('Settings', 'Settings', 'settings-launch')
    assert_displays('com.android.settings', 'settings-launch')
    screenshot('settings-on-secondary', 1)
    shell('am', 'force-stop', 'com.loyverse.cds')
    shell('am', 'start', '--display', '0', '-n', 'com.loyverse.cds/.activities.PairingActivity')
    for attempt in range(16):
        activity_dump = shell('dumpsys', 'activity', 'activities')
        if re.search(r'mResumedActivity:.*com.loyverse.cds/\.activities.ComposeActivity', activity_dump):
            break
        time.sleep(0.5)
    else:
        raise AssertionError('CDS did not finish its main-screen startup fixture')
    launch('Loyverse CDS', 'CDS', 'move-cds-from-main')
    assert_displays('com.loyverse.cds', 'move-cds-from-main')
    launch('Loyverse CDS', 'CDS', 'cds-warm-start')
    assert_displays('com.loyverse.cds', 'cds-warm-start')
    root = set_language(open_tool('final-list'), True, 'final-chinese')
    node(root, 'text', '上次打开：Loyverse CDS')
    screenshot('interface-zh-final')
    assert_displays('com.loyverse.cds', 'final-state')
finally:
    (args.output / 'report.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
