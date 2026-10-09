"""Auralis SDK v1: build file-based, data-only extensions. Python 3.10+, no dependencies."""
from __future__ import annotations
import json, re, argparse
from pathlib import Path
from urllib.parse import urlsplit
CAPABILITIES = {'catalog.read', 'stream.play', 'web.embed', 'skin.apply', 'party.session'}
def origin(url: str) -> str:
    p = urlsplit(url)
    if p.scheme != 'https' or not p.hostname or p.username or p.password or '\\' in url:
        raise ValueError('Use an HTTPS URL without credentials')
    host = f'[{p.hostname}]' if ':' in p.hostname else p.hostname
    return f'https://{host}' + (f':{p.port}' if p.port and p.port != 443 else '')
def validate(m: dict) -> dict:
    encoded = json.dumps(m, ensure_ascii=False).encode()
    if len(encoded) > 65536: raise ValueError('Maximum manifest size is 64 KiB')
    if set(m) - {'apiVersion','id','name','version','capabilities','origins','provider','web','skin','party'}: raise ValueError('Unknown or executable manifest field')
    if m.get('apiVersion') != 1: raise ValueError('Unsupported API version')
    if not re.fullmatch(r'[a-z][a-z0-9]*(?:[.-][a-z0-9]+){1,7}', m.get('id','')) or len(m['id']) > 80: raise ValueError('Invalid reverse-domain ID')
    if not isinstance(m.get('name'),str) or not 1 <= len(m['name'].strip()) <= 80 or not re.fullmatch(r'\d+\.\d+\.\d+',m.get('version','')): raise ValueError('Invalid name/version')
    caps = m.get('capabilities',[])
    if not caps or len(caps) > 12 or not set(caps) <= CAPABILITIES: raise ValueError('Unknown capability')
    origins = m.get('origins',[])
    if len(origins) > 12 or any(origin(x) != x or urlsplit(x).path or urlsplit(x).query or urlsplit(x).fragment for x in origins): raise ValueError('Origins must be exact HTTPS origins')
    for section,cap,key in [('provider','catalog.read','catalogUrl'),('web','web.embed','url'),('party','party.session','url')]:
        if section in m:
            if cap not in caps or set(m[section]) != {key}: raise ValueError(f'Invalid {section} contribution')
            value = m[section][key]
            if len(value) > 2048 or origin(value) not in origins or urlsplit(value).fragment: raise ValueError('Contribution outside declared origins')
    if 'stream.play' in caps and 'provider' not in m: raise ValueError('Streaming needs a provider')
    if 'skin' in m:
        s = m['skin']
        if 'skin.apply' not in caps or set(s) - {'primary','background','surface','text','muted','textScale','rowHeight','cornerRadius','artworkSize','layout','font','atmosphere'}: raise ValueError('Invalid skin contribution')
        colors = {'primary':'#69E0BE','background':'#09090C','surface':'#19191F','text':'#F8F6F7','muted':'#ADABB6'}
        for key,default in colors.copy().items():
            colors[key] = s.get(key,default)
            if not re.fullmatch(r'#[\da-fA-F]{6}',colors[key]): raise ValueError('Colors must be #RRGGBB')
        for key,lo,hi,default in [('textScale',.85,1.3,1),('rowHeight',64,112,72),('cornerRadius',0,32,18),('artworkSize',40,80,56)]:
            if not lo <= s.get(key,default) <= hi: raise ValueError(f'{key} outside supported bounds')
        if s.get('layout','standard') not in {'standard','compact','covers'} or s.get('font','sans') not in {'sans','serif','mono'}: raise ValueError('Unsupported layout/font')
        def lum(c):
            rgb = [int(c[i:i+2],16)/255 for i in (1,3,5)]
            return sum(w*(x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4) for w,x in zip((.2126,.7152,.0722),rgb))
        def contrast(a,b):
            x,y = sorted((lum(colors[a]),lum(colors[b])))
            return (y+.05)/(x+.05)
        if any(contrast(a,b)<minimum for a,b,minimum in [('text','background',4.5),('text','surface',4.5),('muted','background',3),('muted','surface',3),('primary','background',3)]): raise ValueError('Skin colors need readable contrast')
    if not set(m) & {'provider','web','skin','party'}: raise ValueError('No contribution')
    return m

def provider(extension_id: str, name: str, base_url: str, party: bool = True) -> dict:
    base = base_url.rstrip('/')
    m = {'apiVersion':1,'id':extension_id,'name':name,'version':'1.0.0','capabilities':['catalog.read','stream.play'],'origins':[origin(base)],'provider':{'catalogUrl':base+'/v1/catalog'}}
    if party: m['capabilities'].append('party.session'); m['party']={'url':base+'/v1/party'}
    return validate(m)
def write(manifest: dict, path: str | Path):
    Path(path).write_text(json.dumps(validate(manifest),indent=2)+'\n',encoding='utf-8')

def main():
    p=argparse.ArgumentParser(); sub=p.add_subparsers(dest='command',required=True)
    v=sub.add_parser('validate'); v.add_argument('file')
    b=sub.add_parser('provider'); b.add_argument('--id',required=True); b.add_argument('--name',required=True); b.add_argument('--base-url',required=True); b.add_argument('--output',required=True)
    a=p.parse_args()
    if a.command=='validate': validate(json.loads(Path(a.file).read_text())); print('Valid SDK v1 manifest')
    else: write(provider(a.id,a.name,a.base_url),a.output); print(f'Created {a.output}')
if __name__=='__main__': main()
