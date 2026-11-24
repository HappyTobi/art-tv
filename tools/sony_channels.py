#!/usr/bin/env python3
"""Export a Sony BRAVIA's complete broadcast channel list, without tuning or using adb.

    python3 tools/sony_channels.py 192.168.1.50
    python3 tools/sony_channels.py 192.168.1.50 --output channels.local.json

Enter the TV's PIN privately when asked (never in chat). If this client is already
paired and the TV shows no PIN, press Enter. Only channel metadata is saved;
PINs and authentication cookies stay in memory.
"""
import argparse
import base64
import datetime
import getpass
import http.cookiejar
import ipaddress
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request

CLIENT = {'clientid': 'art-tv:channel-lookup', 'nickname': 'Art TV channel lookup'}
MAX_PAGES = 1000


class SonyError(Exception):
    def __init__(self, message, code=None):
        super().__init__(message)
        self.code = code


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise SonyError('The TV redirected the request; credentials were not forwarded.')


def server_url(value):
    parsed = urllib.parse.urlsplit(value if '://' in value else 'http://' + value)
    if (parsed.scheme not in ('http', 'https') or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.path not in ('', '/') or parsed.query or parsed.fragment):
        raise ValueError('Give only the TV address, such as 192.168.1.50.')
    host = parsed.hostname.lower()
    parsed.port  # Validate the optional port before making a request.
    if parsed.scheme == 'http':
        try:
            address = ipaddress.ip_address(host)
            local = any(address in ipaddress.ip_network(net) for net in
                        ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
        except ValueError:
            local = bool(re.fullmatch(r'[a-z0-9-]+|[a-z0-9.-]+\.(local|lan|home\.arpa)', host))
        if not local:
            raise ValueError('Plain HTTP is allowed only for a TV on your own local network.')
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, '', '', ''))


class SonyClient:
    def __init__(self, server):
        self.server = server
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
            NoRedirect())

    def call(self, endpoint, method, params, authorization=None):
        headers = {'Content-Type': 'application/json'}
        if authorization:
            headers['Authorization'] = authorization
        body = json.dumps({'method': method, 'params': params, 'id': 1, 'version': '1.0'}).encode()
        request = urllib.request.Request(self.server + '/sony/' + endpoint, body, headers)
        try:
            response = self.opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            code = error.code
            error.close()
            raise SonyError('%s refused (HTTP %s).' % (method, code), code) from None
        with response:
            raw = response.read(8 * 1024 * 1024 + 1)
        if len(raw) > 8 * 1024 * 1024:
            raise SonyError('%s returned an unexpectedly large response.' % method)
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise SonyError('%s returned an invalid response.' % method)
        if 'error' in data:
            code = data['error'][0]
            raise SonyError('%s refused (Sony error %s).' % (method, code), code)
        if not isinstance(data.get('result'), list):
            raise SonyError('%s returned no result.' % method)
        return data['result']

    def authenticate(self):
        paired = False
        try:
            self.call('accessControl', 'actRegister', [CLIENT, []])
            paired = True
        except SonyError as error:
            if error.code != 401:
                raise
        print('Already paired: press Enter if no PIN appears.' if paired else
              'Enter the PIN displayed by the TV. It stays hidden and is never saved.')
        pin = getpass.getpass('TV pairing PIN: ')
        if pin:
            authorization = 'Basic ' + base64.b64encode((':' + pin).encode()).decode('ascii')
            self.call('accessControl', 'actRegister', [CLIENT, []], authorization)
        elif not paired:
            raise SonyError('A PIN is required; run the script again with the PIN shown on the TV.')


def rows(result, method):
    if len(result) != 1 or not isinstance(result[0], list):
        raise SonyError('%s returned an invalid list.' % method)
    return result[0]


def export_channels(client, page_size=200):
    sources = rows(client.call('avContent', 'getSourceList', [{'scheme': 'tv'}]), 'getSourceList')
    if not sources:
        raise SonyError('The TV returned no broadcast sources.')
    channels, summaries, seen_sources = [], [], set()
    for item in sources:
        source = item.get('source') if isinstance(item, dict) else None
        if not isinstance(source, str) or not source.startswith('tv:'):
            raise SonyError('The TV returned an invalid broadcast source.')
        if source in seen_sources:
            continue
        seen_sources.add(source)
        total = client.call('avContent', 'getContentCount', [{'source': source}])
        expected = total[0].get('count') if len(total) == 1 and isinstance(total[0], dict) else None
        if type(expected) is not int or expected < 0:
            raise SonyError('Sony returned an invalid channel count for %s.' % source)
        count, seen_pages = 0, set()
        for _ in range(MAX_PAGES):
            batch = rows(client.call('avContent', 'getContentList', [{
                'source': source, 'stIdx': count, 'cnt': page_size
            }]), 'getContentList')
            if not batch:
                break
            fingerprint = json.dumps(batch, sort_keys=True)
            if fingerprint in seen_pages:
                raise SonyError('Sony repeated a page for %s; no incomplete export will be written.' % source)
            seen_pages.add(fingerprint)
            for channel in batch:
                if (not isinstance(channel, dict) or not isinstance(channel.get('uri'), str)
                        or not channel['uri'].startswith(source + '?')):
                    raise SonyError('Sony returned an invalid channel URI for %s.' % source)
                channels.append(dict(channel, source=source))
            count += len(batch)
            if count >= expected:
                break
        else:
            raise SonyError('Sony exceeded the pagination limit; no incomplete export will be written.')
        if count != expected:
            raise SonyError('Sony reported %d channels for %s but returned %d; no incomplete export will be written.'
                            % (expected, source, count))
        summaries.append({'source': source, 'channel_count': count})
    if not channels:
        raise SonyError('The TV returned no channels; the output file was not changed.')
    return {
        'schema_version': 1,
        'tv': client.server,
        'exported_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'channel_count': len(channels),
        'sources': summaries,
        'channels': channels,
    }


def write_export(path, data, force=False):
    """Publish only a complete export; leave an existing file alone unless --force was explicit."""
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as output:
            json.dump(data, output, ensure_ascii=False, indent=2)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        if force:
            os.replace(temporary, path)
        else:
            os.link(temporary, path)  # Atomic no-overwrite, including when another export finishes first.
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('tv', help='Sony TV address')
    parser.add_argument('--output', type=Path, default=Path('sony-channels.local.json'))
    parser.add_argument('--force', action='store_true', help='replace an existing export only after a complete lookup')
    args = parser.parse_args(argv)
    try:
        server = server_url(args.tv)
        if args.output.exists() and not args.force:
            raise SonyError('Output already exists; choose another --output or use --force.')
        if not sys.stdin.isatty():
            raise SonyError('Run in an interactive terminal so the PIN can be entered without echoing.')
        client = SonyClient(server)
        client.authenticate()
        data = export_channels(client)
        write_export(args.output, data, args.force)
        print('Exported %d channels from %d source(s) to %s.' %
              (data['channel_count'], len(data['sources']), args.output))
        print('No channel was changed. PINs and cookies were not saved.')
        return 0
    except (SonyError, OSError, ValueError, urllib.error.URLError) as error:
        print('Channel export stopped: %s' % error, file=sys.stderr)
        return 1
    except (EOFError, KeyboardInterrupt):
        print('\nChannel export cancelled; no output was changed.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
