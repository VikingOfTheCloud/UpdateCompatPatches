import sys, zipfile, re; sys.path.insert(0,'.')
from refcheck import parse, index
idx={}
for c,j in (('pz.idx','pz.jar'),('vp.idx','vp.jar'),('zb.idx','zb.jar')):
    for k,v in index([j],c).items(): idx.setdefault(k,v)
JDK=('java/','javax/','jdk/','sun/','com/sun/')
def resolve(cls,kind,name,desc,seen=None):
    seen=seen or set()
    if cls in seen: return None
    seen.add(cls)
    if cls.startswith(JDK): return 'jdk' if name in ('<init>','toString','equals','hashCode','getClass','clone','size','get','add','iterator','isEmpty','contains','remove','put','length','charAt','valueOf','name','ordinal','values','close','run','accept','apply','test','compareTo','getName') else None
    if cls not in idx: return None
    s,ifs,mem=idx[cls]
    if (kind,name,desc) in mem: return 'ok'
    for p in ([s] if s else [])+ifs:
        r=resolve(p,kind,name,desc,seen)
        if r: return r
    return None
for jar in sys.argv[1:]:
    z=zipfile.ZipFile(jar); own={}; parsed=[]
    for n in z.namelist():
        if n.endswith('.class'):
            r=parse(z.read(n)); own[r[0]]=(r[1],r[2],r[3]); parsed.append(r)
    for k,v in own.items(): idx[k]=v
    missC=set(); missM=set(); ext=set(); targets=set()
    for t,s,ifs,mem,refs,classes,utf in parsed:
        for c in classes:
            c=c.lstrip('[').strip('L;')
            if c.startswith(JDK) or c in own or len(c)<3: continue
            ext.add(c.split('/')[0]+'/'+(c.split('/')[1] if '/' in c else ''))
            if c not in idx and c.startswith(('zombie/','viewpoint/')): missC.add(c)
        for kind,c,name,desc in refs:
            if c.startswith('[') or c.startswith(JDK) or c in own and (kind if kind!='I' else 'M',name,desc) in own[c][2]: continue
            k='F' if kind=='F' else 'M'
            if c.startswith(('zombie/','viewpoint/')) and c not in missC and resolve(c,k,name,desc) is None: missM.add(f'{c}.{name}{desc}')
        for u in utf:
            if re.fullmatch(r'(zombie|viewpoint)(\.[A-Za-z0-9_$]+)+',u): targets.add(u)
    badT=[t for t in targets if t.replace('.','/') not in idx and t.replace('.','/') not in own]
    if missC or missM or badT:
        print(f'== {jar}'); [print('   class  ',x) for x in sorted(missC)]; [print('   member ',x) for x in sorted(missM)]; [print('   target ',x) for x in sorted(badT)]
