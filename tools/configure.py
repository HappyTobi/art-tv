#!/usr/bin/env python3
"""Generate the Sony address and the single allowed cleartext host."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from sony_channels import server_url

ROOT = Path(__file__).resolve().parents[1]


def main():
    settings = {}
    for line in (ROOT / 'config.properties').read_text(encoding='utf-8-sig').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, separator, value = line.partition('=')
            if not separator or key.strip() != 'sony_tv_url':
                raise ValueError('Only sony_tv_url is supported in config.properties.')
            settings[key.strip()] = value.strip()
    value = settings.get('sony_tv_url', '')
    url = server_url(value) if value else ''
    source = ROOT / 'out/gen/io/happytobi/arttv/Config.java'
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text('package io.happytobi.arttv;\nfinal class Config {\n'
                      '    static final String SONY_TV_URL = %s;\n}\n' % json.dumps(url), encoding='utf-8')
    root = ET.Element('network-security-config')
    ET.SubElement(root, 'base-config', cleartextTrafficPermitted='false')
    if url.startswith('http://'):
        from urllib.parse import urlsplit
        domain = ET.SubElement(root, 'domain-config', cleartextTrafficPermitted='true')
        ET.SubElement(domain, 'domain', includeSubdomains='false').text = urlsplit(url).hostname
    target = ROOT / 'out/res/xml/network_security_config.xml'
    target.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(target, encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError) as error:
        sys.exit('Configuration error: ' + str(error))
