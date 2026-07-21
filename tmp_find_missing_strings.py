from pathlib import Path
import re
root = Path('TMessagesProj/src/main/res')
base = root / 'values' / 'strings.xml'
if not base.exists():
    raise SystemExit('base strings.xml missing')
pattern = re.compile(r'<string name="([^"]+)"')
base_names = {m.group(1) for m in pattern.finditer(base.read_text(encoding='utf-8'))}
for d in sorted(root.iterdir()):
    if not d.is_dir() or d.name == 'values':
        continue
    file = d / 'strings.xml'
    if not file.exists():
        continue
    names = [m.group(1) for m in pattern.finditer(file.read_text(encoding='utf-8'))]
    missing = sorted(set(names) - base_names)
    if missing:
        print(d.name)
        for n in missing:
            print('  ' + n)
