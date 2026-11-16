import zipfile, struct, sys, pickle, os
def parse(data):
    # returns (this, super, interfaces, members set, refs list, utf8 list)
    p=8; n=struct.unpack('>H',data[p:p+2])[0]; p+=2
    cp=[None]*n; i=1
    while i<n:
        t=data[p]; p+=1
        if t==1:
            l=struct.unpack('>H',data[p:p+2])[0]; p+=2; cp[i]=('u',data[p:p+l].decode('utf8','replace')); p+=l
        elif t in (3,4): cp[i]=('i',); p+=4
        elif t in (5,6): cp[i]=('l',); p+=8; i+=1
        elif t==7: cp[i]=('c',struct.unpack('>H',data[p:p+2])[0]); p+=2
        elif t==8: cp[i]=('s',struct.unpack('>H',data[p:p+2])[0]); p+=2
        elif t in (9,10,11): a,b=struct.unpack('>HH',data[p:p+4]); cp[i]=({9:'F',10:'M',11:'I'}[t],a,b); p+=4
        elif t==12: a,b=struct.unpack('>HH',data[p:p+4]); cp[i]=('nt',a,b); p+=4
        elif t==15: cp[i]=('mh',); p+=3
        elif t==16: cp[i]=('mt',); p+=2
        elif t in (17,18): cp[i]=('dy',); p+=4
        elif t in (19,20): cp[i]=('mod',); p+=2
        else: raise Exception('tag %d'%t)
        i+=1
    U=lambda k: cp[k][1]
    C=lambda k: U(cp[k][1])
    acc,this,sup,ic=struct.unpack('>HHHH',data[p:p+8]); p+=8
    ifs=[C(struct.unpack('>H',data[p+2*j:p+2*j+2])[0]) for j in range(ic)]; p+=2*ic
    mem=set()
    for kind in ('F','M'):
        c=struct.unpack('>H',data[p:p+2])[0]; p+=2
        for _ in range(c):
            a,nm,ds,ac=struct.unpack('>HHHH',data[p:p+8]); p+=8
            mem.add((kind,U(nm),U(ds)))
            for _ in range(ac):
                l=struct.unpack('>I',data[p+2:p+6])[0]; p+=6+l
    refs=[]
    for e in cp:
        if e and e[0] in ('F','M','I'):
            nt=cp[e[2]]; refs.append((e[0],C(e[1]),U(nt[1]),U(nt[2])))
    classes=[C(k) for k,e in enumerate(cp) if e and e[0]=='c']
    utf=[e[1] for e in cp if e and e[0]=='u']
    return C(this),(C(sup) if sup else None),ifs,mem,refs,classes,utf
def index(jars, cache):
    if os.path.exists(cache): return pickle.load(open(cache,'rb'))
    idx={}
    for j in jars:
        z=zipfile.ZipFile(j)
        for n in z.namelist():
            if n.endswith('.class') and not n.startswith('META-INF'):
                try:
                    t,s,ifs,mem,_,_,_=parse(z.read(n)); idx.setdefault(t,(s,ifs,mem))
                except Exception as ex: pass
    pickle.dump(idx,open(cache,'wb')); return idx
