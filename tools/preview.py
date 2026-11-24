#!/usr/bin/env python3
"""Build a local design preview; --serve opens no connections to the TV."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def artworks():
    directory = ROOT / 'app/assets/art'
    entries = json.loads((directory / 'catalogue.json').read_text(encoding='utf-8'))
    if not isinstance(entries, list) or not entries:
        raise ValueError('The artwork catalogue must be a nonempty list.')
    seen = set()
    for entry in entries:
        if (not isinstance(entry, dict)
                or not isinstance(entry.get('id'), str)
                or not re.fullmatch(r'[a-z0-9_-]+', entry['id'])
                or entry['id'] in seen
                or not isinstance(entry.get('title'), str) or not entry['title'].strip()
                or not isinstance(entry.get('file'), str)
                or not re.fullmatch(r'[a-zA-Z0-9_-]+\.(jpg|jpeg|png|webp)', entry['file'])
                or not (directory / entry['file']).is_file()):
            raise ValueError('Invalid or missing artwork in catalogue.')
        seen.add(entry['id'])
    return entries


def build():
    entries = artworks()
    output = ROOT / 'out/preview'
    output.mkdir(parents=True, exist_ok=True)
    for file in ('index.html', 'preview.js', 'art.js', 'art.css'):
        shutil.copyfile(ROOT / 'preview' / file, output / file)
    (output / 'art').mkdir(exist_ok=True)
    for entry in entries:
        shutil.copyfile(ROOT / 'app/assets/art' / entry['file'], output / 'art' / entry['file'])
    (output / 'artworks.js').write_text('var ARTWORKS = ' + json.dumps(entries, ensure_ascii=False) + ';\n', encoding='utf-8')
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serve', action='store_true')
    parser.add_argument('--port', type=int, default=8000)
    args = parser.parse_args()
    output = build()
    if args.serve:
        server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(SimpleHTTPRequestHandler, directory=str(output)))
        print('Design preview: http://127.0.0.1:%d (Ctrl-C to stop)' % args.port, flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()
    else:
        print('Design preview: ' + (output / 'index.html').as_uri())


if __name__ == '__main__':
    main()
