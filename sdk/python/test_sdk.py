import json, sys, threading, unittest, urllib.request, urllib.error, tempfile
from pathlib import Path
from auralis_sdk import validate, provider
from reference_server import Server
class SDKTest(unittest.TestCase):
    def test_examples_validate(self):
        for p in (Path(__file__).parent.parent/'examples').glob('*.json'): validate(json.loads(p.read_text()))
    def test_scripts_and_unsafe_origins_fail(self):
        m=provider('org.test.server','Test','https://music.example')
        m['script']='code.js'
        with self.assertRaises(ValueError): validate(m)
        m.pop('script'); m['provider']['catalogUrl']='https://music.example.evil/catalog'
        with self.assertRaises(ValueError): validate(m)
    def test_skin_limits_and_contrast(self):
        m={'apiVersion':1,'id':'org.test.skin','name':'Skin','version':'1.0.0','capabilities':['skin.apply'],'origins':[],'skin':{'textScale':4}}
        with self.assertRaises(ValueError): validate(m)
        m['skin']={'text':'#09090C'}
        with self.assertRaises(ValueError): validate(m)

class ServerTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); root=Path(self.temp.name); (root/'track.mp3').write_bytes(b'0123456789')
        self.server=Server(('127.0.0.1',0),root,'https://music.example','test-secret-at-least-24-characters')
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True); self.thread.start(); self.base=f'http://127.0.0.1:{self.server.server_port}'
    def tearDown(self): self.server.shutdown(); self.server.server_close(); self.temp.cleanup()
    def request(self,path,body=None,headers=None,auth=True):
        h={'Authorization':'Bearer '+self.server.token} if auth else {}; h.update(headers or {})
        req=urllib.request.Request(self.base+path,json.dumps(body).encode() if body is not None else None,headers=h)
        return urllib.request.urlopen(req,timeout=3)
    def test_auth_required(self):
        with self.assertRaises(urllib.error.HTTPError) as c: self.request('/v1/catalog',auth=False)
        self.assertEqual(401,c.exception.code)
    def test_catalog_and_ranges(self):
        with self.request('/v1/catalog') as r: track=json.load(r)['tracks'][0]
        self.assertEqual('track',track['title']); self.assertTrue(track['streamUrl'].startswith('https://music.example/'))
        with self.request('/v1/audio/'+track['id'],headers={'Range':'bytes=2-5'}) as r: self.assertEqual(206,r.status); self.assertEqual(b'2345',r.read())
        with self.assertRaises(urllib.error.HTTPError): self.request('/v1/audio/../secret')
    def test_shared_queue_contract(self):
        with self.request('/v1/catalog') as r: ident=json.load(r)['tracks'][0]['id']
        with self.request('/v1/party',{'action':'create'}) as r: sid=json.load(r)['session']
        with self.request('/v1/party',{'action':'join','session':sid}) as r: self.assertEqual([],json.load(r)['queue'])
        with self.request('/v1/party',{'action':'enqueue','session':sid,'trackId':ident}) as r: self.assertEqual([ident],json.load(r)['queue'])
        with self.request('/v1/party',{'action':'state','session':sid}) as r: self.assertEqual([ident],json.load(r)['queue'])
        with self.request('/v1/party',{'action':'leave','session':sid}) as r: self.assertTrue(json.load(r)['left'])
    def test_party_rejects_unknown_tracks_and_actions(self):
        with self.request('/v1/party',{'action':'create'}) as r: sid=json.load(r)['session']
        for body in [{'action':'enqueue','session':sid,'trackId':'bad'},{'action':'shell','session':sid}]:
            with self.assertRaises(urllib.error.HTTPError) as c: self.request('/v1/party',body)
            self.assertEqual(400,c.exception.code)
if __name__=='__main__': unittest.main()
