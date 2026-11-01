"""Assemble the standalone design review page from the conversation source."""
from pathlib import Path
import argparse

OUT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=OUT/'design-source.html')
SOURCE = parser.parse_args().source
fragment = SOURCE.read_text(encoding='utf-8')
assert len(fragment.encode('utf-8')) < 1_000_000
assert '\\"' not in fragment
prefix = '''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>生意账本完整设计</title>
<style>:root{color-scheme:light dark}body{margin:0;padding:28px 18px;background:light-dark(#f4f5f7,#191e24)}main{max-width:1000px;margin:auto}</style>
</head><body><main>
'''
(OUT/'index.html').write_text(prefix+fragment+'\n</main></body></html>\n',encoding='utf-8')
(OUT/'design-source.html').write_text(fragment,encoding='utf-8')
print('Review page built; source bytes:',len(fragment.encode('utf-8')))
