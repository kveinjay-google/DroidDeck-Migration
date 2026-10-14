#!/usr/bin/env python3
"""Convert a legacy TAR into <=2 GB volumes, without modifying its bytes."""
import argparse,json,hashlib,pathlib
p=argparse.ArgumentParser();p.add_argument('archive',type=pathlib.Path);p.add_argument('output',type=pathlib.Path);p.add_argument('--settings',type=pathlib.Path);args=p.parse_args()
args.output.mkdir(parents=True,exist_ok=False)
limit=2_000_000_000;whole=hashlib.sha256();parts=[];total=0
with args.archive.open('rb') as source:
 while True:
  first=source.read(min(8*1024*1024,limit))
  if not first:break
  name='part-%04d.bin'%(len(parts)+1);h=hashlib.sha256();size=0
  with (args.output/name).open('xb') as target:
   data=first
   while data:
    target.write(data);h.update(data);whole.update(data);size+=len(data);total+=len(data)
    if size==limit:break
    data=source.read(min(8*1024*1024,limit-size))
  parts.append(dict(name=name,bytes=size,sha256=h.hexdigest()));print(name,size,flush=True)
index=dict(format='droiddeck-migration',version=1,bytes=total,sha256=whole.hexdigest(),parts=parts,sourcePackage='com.droiddeck.launcher')
if args.settings:index['settings']=json.loads(args.settings.read_text())
(args.output/'backup.ddbackup.json').write_text(json.dumps(index,ensure_ascii=False,indent=2))
print('Complete:',total,'bytes,',len(parts),'parts; SHA256',whole.hexdigest())
