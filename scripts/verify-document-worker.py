#!/usr/bin/env python3
"""Smoke-test the packaged Boot worker and vision wire format with a local mock. No credentials needed."""
import base64
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import zlib

root = Path(__file__).resolve().parent.parent
jar = root / 'backend/target/backend-0.0.1-SNAPSHOT.jar'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
requests = []
evidence_sequence = []
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pdf', type=Path, help='Also process a local PDF against the mock provider; no file is sent externally.')
args = parser.parse_args()


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_POST(self):
        if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
            chunks = []
            while True:
                size = int(self.rfile.readline().strip().split(b';')[0], 16)
                if not size:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            data = b''.join(chunks)
        else:
            data = self.rfile.read(int(self.headers['Content-Length']))
        body = json.loads(data)
        requests.append(body)
        evidence = evidence_sequence.pop(0) if evidence_sequence else valid_evidence
        result = json.dumps({'status': 'completed', 'output': [{'type': 'message', 'role': 'assistant',
                            'content': [{'type': 'output_text', 'text': json.dumps(evidence)}]}]}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(result)))
        self.end_headers()
        self.wfile.write(result)


def png_chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def run_worker(name, content, endpoint):
    config = json.dumps({'filename': name, 'endpoint': endpoint, 'apiKey': 'mock-server-only-key',
                         'model': 'deepseek-flash', 'visionModel': 'deepseek-flash', 'effort': 'none', 'timeout': 'PT5S'}).encode()
    # JSON ASCII is compatible with DataInputStream.readUTF (modified UTF-8).
    payload = struct.pack('>H', len(config)) + config + struct.pack('>I', len(content)) + content
    with tempfile.TemporaryDirectory(prefix='erro-worker-smoke-') as directory:
        process = subprocess.run([java, '-Xmx256m', '-Djava.awt.headless=true', f'-Duser.home={directory}',
                                  '-Dloader.main=ink.erro.backend.document.DocumentWorker', '-cp', str(jar),
                                  'org.springframework.boot.loader.launch.PropertiesLauncher'],
                                 input=payload, cwd=directory, capture_output=True, timeout=30)
        assert process.returncode == 0, 'Packaged document worker did not start or complete'
        return json.loads((Path(directory) / 'result.json').read_text())


valid_evidence = {'coordinateSpace': 'NORMALIZED_1000', 'items': [{'reference': 'Q1', 'description': 'C16 breaker',
                  'quantity': 1, 'unit': 'tk', 'basis': 'SYMBOL_COUNT', 'x': 500, 'y': 500}], 'notes': 'Mock vision fixture'}
invalid_evidence = json.loads(json.dumps(valid_evidence))
invalid_evidence['items'][0]['x'] = 1500
server = ThreadingHTTPServer(('127.0.0.1', 0), Provider)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
try:
    endpoint = f'http://127.0.0.1:{server.server_port}/responses'
    text = run_worker('materials.csv', b'Description,Quantity,Unit\nC16 breaker,3,pcs', endpoint)
    assert text['extraction']['evidence'][-1]['items'][0]['quantity'] == 3
    assert requests == [], 'Structured text must not call vision'
    png = b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('>2I5B', 20, 20, 8, 2, 0, 0, 0))
    png += png_chunk(b'IDAT', zlib.compress((b'\x00' + b'\xff' * 60) * 20)) + png_chunk(b'IEND', b'')
    evidence_sequence.extend([invalid_evidence, valid_evidence])
    vision = run_worker('drawing.png', png, endpoint)
    assert vision['extraction']['evidence'][0]['items'][0]['basis'] == 'VISION_SYMBOL_COUNT'
    assert len(requests) == 2
    req = requests[0]
    assert req['model'] == 'deepseek-flash' and req['store'] is False
    part = req['input'][0]['content'][1]
    assert part['type'] == 'input_image' and part['detail'] == 'original'
    assert base64.b64decode(part['image_url'].split(',')[1]).startswith(b'\x89PNG')
    assert 'tools' not in req and 'previous_response_id' not in req
    assert requests[1]['input'][0]['content'][1] == part, 'Correction must use the same image'
    assert 'previous extraction could not be validated' in requests[1]['input'][0]['content'][0]['text']
    evidence_sequence.extend([invalid_evidence, invalid_evidence])
    failed = run_worker('invalid-locations.png', png, endpoint)
    assert 'after one automatic correction' in failed['error'] and 'extraction' not in failed
    assert len(requests) == 4, 'Unusable evidence must stop after one correction'
    if args.pdf:
        requests.clear()
        evidence_sequence.extend([invalid_evidence, valid_evidence])
        pdf = run_worker(args.pdf.name, args.pdf.read_bytes(), endpoint)
        assert 'extraction' in pdf, pdf.get('error', 'PDF extraction missing')
        if requests:
            assert len(requests) >= 2
            assert 'previous extraction could not be validated' in requests[1]['input'][0]['content'][0]['text']
        print(f'PASS: local PDF through packaged worker ({len(requests)} mock vision calls)')
    print('PASS: packaged Boot worker, CSV without vision, inline DeepSeek images, bounded coordinate correction and safe failure')
finally:
    server.shutdown()
    server.server_close()
