import base64
import contextlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import sony_channels as sony


def channel(source, index, name):
    return {'uri': source + '?trip=1.2.' + str(index), 'title': name,
            'index': index, 'dispNum': str(index), 'programMediaType': 'tv'}


class SonyChannelsTest(unittest.TestCase):
    def test_pin_is_requested_before_listing_and_never_exported(self):
        client = sony.SonyClient('http://192.168.1.50')
        client.call = Mock(side_effect=[sony.SonyError('PIN required', 401), []])
        with patch.object(sony.getpass, 'getpass', return_value='1234') as prompt, contextlib.redirect_stdout(io.StringIO()):
            client.authenticate()
        prompt.assert_called_once()
        self.assertEqual('Basic ' + base64.b64encode(b':1234').decode(),
                         client.call.call_args.args[3])
        self.assertEqual(['actRegister', 'actRegister'],
                         [call.args[1] for call in client.call.call_args_list])

    def test_already_paired_tv_accepts_enter_but_unpaired_tv_requires_pin(self):
        for paired in (True, False):
            client = sony.SonyClient('http://192.168.1.50')
            client.call = Mock(return_value=[])
            if not paired:
                client.call.side_effect = sony.SonyError('PIN required', 401)
            with patch.object(sony.getpass, 'getpass', return_value=''), contextlib.redirect_stdout(io.StringIO()):
                if paired:
                    client.authenticate()
                else:
                    with self.assertRaisesRegex(sony.SonyError, 'PIN is required'):
                        client.authenticate()

    def test_all_sources_pages_and_non_shortcut_channels_are_preserved(self):
        satellite = [channel('tv:dvbs', i, name) for i, name in enumerate(
            ['Das Erste HD', 'Radio Kultur', 'ZDFinfo HD', 'ZDF', 'KiKA HD'], 1)]
        satellite[1]['programMediaType'] = 'radio'
        cable = [channel('tv:dvbc', 1, 'Regional TV')]
        client = Mock(server='http://192.168.1.50')

        def call(endpoint, method, params):
            if method == 'getSourceList':
                return [[{'source': 'tv:dvbs'}, {'source': 'tv:dvbc'}]]
            query = params[0]
            entries = satellite if query['source'] == 'tv:dvbs' else cable
            if method == 'getContentCount':
                return [{'count': len(entries)}]
            self.assertEqual('getContentList', method)
            # Some TVs cap each page below the requested size.
            return [entries[query['stIdx']:query['stIdx'] + min(query['cnt'], 2)]]

        client.call.side_effect = call
        data = sony.export_channels(client)
        self.assertEqual(6, data['channel_count'])
        self.assertEqual([5, 1], [source['channel_count'] for source in data['sources']])
        self.assertEqual(satellite + cable, [
            {key: value for key, value in row.items() if key != 'source'} for row in data['channels']])
        self.assertNotIn('1234', json.dumps(data))

    def test_repeated_page_or_later_failure_does_not_replace_existing_export(self):
        batch = [channel('tv:dvbs', 1, 'Das Erste HD')]
        for last in ([batch], sony.SonyError('getContentList refused', 403)):
            client = Mock(server='http://192.168.1.50')
            client.call.side_effect = [[[{'source': 'tv:dvbs'}]], [{'count': 2}], [batch], last]
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'channels.json'
                path.write_text('previous export')
                with self.assertRaises(sony.SonyError):
                    data = sony.export_channels(client, page_size=1)
                    sony.write_export(path, data, force=True)
                self.assertEqual('previous export', path.read_text())

    def test_premature_end_of_list_is_not_reported_as_a_complete_export(self):
        client = Mock(server='http://192.168.1.50')
        client.call.side_effect = [
            [[{'source': 'tv:dvbs'}]], [{'count': 3}], [[channel('tv:dvbs', 1, 'Das Erste HD')]], [[]]]
        with self.assertRaisesRegex(sony.SonyError, 'reported 3.*returned 1'):
            sony.export_channels(client)

    def test_atomic_output_is_private_and_never_overwrites_without_force(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'channels.json'
            sony.write_export(path, {'channels': ['ZDF']})
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            with self.assertRaises(FileExistsError):
                sony.write_export(path, {'channels': ['KiKA']})
            self.assertEqual({'channels': ['ZDF']}, json.loads(path.read_text()))
            sony.write_export(path, {'channels': ['KiKA']}, force=True)
            self.assertEqual({'channels': ['KiKA']}, json.loads(path.read_text()))
            self.assertEqual(['channels.json'], os.listdir(directory))

    def test_public_http_and_embedded_credentials_are_refused(self):
        for address in ('http://8.8.8.8', 'http://user:password@192.168.1.50', 'http://192.168.1.50/other'):
            with self.subTest(address=address), self.assertRaises(ValueError):
                sony.server_url(address)


if __name__ == '__main__':
    unittest.main()
