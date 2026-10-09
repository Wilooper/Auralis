"""Small HTTPS catalog/stream/party adapter. Extensions execute on your server, never the phone.
Set AURALIS_SERVER_TOKEN, supply a trusted TLS cert/key, and a folder of audio files.
For YouTube Music, implement the same endpoints around your own authorized server API.
"""
from __future__ import annotations
import argparse, hashlib, hmac, json, mimetypes, os, re, secrets, ssl, threading, time
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
from collections import OrderedDict, deque
from auralis_sdk import origin

class Server(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    def __init__(self, address, root, base_url, token):
        self.root=Path(root).resolve(); self.base_url=base_url.rstrip('/'); origin(self.base_url)
        if urlsplit(self.base_url).path: raise ValueError('base-url must be an origin')
        if not token or len(token)<24: raise ValueError('Use a random token of at least 24 characters')
        self.token=token; self.lock=threading.Lock(); self.parties={}; self.rates=OrderedDict(); self.slots=threading.BoundedSemaphore(8)
        self.tracks={}
        for path in sorted(self.root.rglob('*')):
            if len(self.tracks)>=500: break
            if path.is_file() and not path.is_symlink() and path.suffix.lower() in {'.mp3','.flac','.ogg','.opus','.wav'} and path.resolve().is_relative_to(self.root):
                ident=hashlib.sha256(str(path.relative_to(self.root)).encode()).hexdigest()[:24]
                self.tracks[ident]=path
        super().__init__(address,Handler)
    def process_request(self, request, client_address):
        if not self.slots.acquire(False): request.close(); return
        try: super().process_request(request,client_address)
        except Exception: self.slots.release(); raise
    def process_request_thread(self, request, client_address):
        try: super().process_request_thread(request,client_address)
        finally: self.slots.release()

class Handler(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    server: Server
    def setup(self): super().setup(); self.connection.settimeout(10)
    def log_message(self,*args): pass # Never log credentials, session IDs, or media URLs.
    def reply(self,code,body):
        data=json.dumps(body,separators=(',',':')).encode(); self.send_response(code); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(data))); self.send_header('Connection','close'); self.end_headers()
        if self.command!='HEAD': self.wfile.write(data)
        self.close_connection=True
    def authorized(self):
        if len(self.path)>2048 or not hmac.compare_digest(self.headers.get('Authorization',''), 'Bearer '+self.server.token): self.reply(401,{'error':'unauthorized'}); return False
        now=time.monotonic(); ip=self.client_address[0]
        with self.server.lock:
            times=self.server.rates.setdefault(ip,deque()); self.server.rates.move_to_end(ip)
            while times and now-times[0]>60: times.popleft()
            limited=len(times)>=60
            if not limited: times.append(now)
            while len(self.server.rates)>256: self.server.rates.popitem(last=False)
        if limited: self.reply(429,{'error':'rate_limited'}); return False
        return True
    def do_HEAD(self): self.do_GET()
    def do_GET(self):
        if not self.authorized(): return
        p=urlsplit(self.path)
        if p.path=='/v1/catalog':
            q=parse_qs(p.query).get('q',[''])[0][:200].casefold()
            rows=[{'id':i,'title':path.stem,'artist':'Self-hosted library','streamUrl':self.server.base_url+'/v1/audio/'+i,'format':('ogg' if path.suffix.lower()=='.opus' else path.suffix.lower()[1:])} for i,path in self.server.tracks.items() if q in path.stem.casefold()][:100]
            self.reply(200,{'tracks':rows}); return
        if p.path.startswith('/v1/audio/'):
            path=self.server.tracks.get(p.path.removeprefix('/v1/audio/'))
            if path is None or not path.resolve().is_relative_to(self.server.root): self.reply(404,{'error':'not_found'}); return
            size=path.stat().st_size; start=0; end=size-1; code=200
            value=self.headers.get('Range')
            if value:
                m=re.fullmatch(r'bytes=(\d*)-(\d*)',value)
                if not m or not any(m.groups()): self.reply(416,{'error':'invalid_range'}); return
                a,b=m.groups()
                if a: start=int(a); end=min(int(b),end) if b else end
                else: start=max(0,size-int(b))
                if start>end or start>=size: self.reply(416,{'error':'invalid_range'}); return
                code=206
            with path.open('rb') as stream:
                self.send_response(code); self.send_header('Content-Type',mimetypes.guess_type(path.name)[0] or 'application/octet-stream'); self.send_header('Content-Length',str(end-start+1)); self.send_header('Accept-Ranges','bytes'); self.send_header('Connection','close')
                if code==206: self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
                self.end_headers()
                if self.command!='HEAD':
                    stream.seek(start); remaining=end-start+1
                    while remaining>0:
                        chunk=stream.read(min(32768,remaining))
                        if not chunk: break
                        self.wfile.write(chunk); remaining-=len(chunk)
            self.close_connection=True; return
        self.reply(404,{'error':'not_found'})
    def do_POST(self):
        if not self.authorized(): return
        if urlsplit(self.path).path!='/v1/party': self.reply(404,{'error':'not_found'}); return
        try:
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=8192: raise ValueError()
            body=json.loads(self.rfile.read(length)); action=body.get('action'); sid=body.get('session',''); track=body.get('trackId','')
            if not isinstance(sid,str) or len(sid)>128 or not isinstance(track,str) or len(track)>128: raise ValueError()
            with self.server.lock:
                now=time.monotonic(); self.server.parties={k:v for k,v in self.server.parties.items() if now-v['updated']<7200}
                if action=='create':
                    if len(self.server.parties)>=20: self.reply(429,{'error':'session_limit'}); return
                    sid=secrets.token_urlsafe(24); self.server.parties[sid]={'queue':[],'updated':now}
                party=self.server.parties.get(sid)
                if not party: self.reply(404,{'error':'session_not_found'}); return
                if action=='enqueue':
                    if track not in self.server.tracks or len(party['queue'])>=100: self.reply(400,{'error':'invalid_track_or_full_queue'}); return
                    party['queue'].append(track)
                elif action not in {'create','join','state','leave'}: raise ValueError()
                party['updated']=now
                self.reply(200,{'session':sid,'queue':party['queue'],'mode':'shared-queue','left':action=='leave'})
        except (ValueError,TypeError,RecursionError,json.JSONDecodeError): self.reply(400,{'error':'invalid_request'})

def main():
    p=argparse.ArgumentParser(); p.add_argument('--music',required=True); p.add_argument('--base-url',required=True); p.add_argument('--cert',required=True); p.add_argument('--key',required=True); p.add_argument('--bind',default='127.0.0.1'); p.add_argument('--port',type=int,default=8443); a=p.parse_args()
    server=Server((a.bind,a.port),a.music,a.base_url,os.environ.get('AURALIS_SERVER_TOKEN',''))
    tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); tls.minimum_version=ssl.TLSVersion.TLSv1_2; tls.load_cert_chain(a.cert,a.key); server.socket=tls.wrap_socket(server.socket,server_side=True)
    print(f'Auralis adapter ready: {len(server.tracks)} songs. Shared queues only; no synchronized audio.')
    try: server.serve_forever()
    finally: server.server_close()
if __name__=='__main__': main()
