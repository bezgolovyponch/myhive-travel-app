import json,os,re,sys,time,urllib.request,statistics as st
KEY=os.environ['K']; B='http://localhost:8080'
get=lambda p: json.load(urllib.request.urlopen(B+p,timeout=30))
dest=[d for d in get('/destinations') if d['slug']=='prague'][0]
acts=sorted(get(f"/activities?destinationId={dest['id']}"),key=lambda a:(-a.get('featuredWeight',0),a['name']))[:80]
pk=[p for p in get(f"/packages?destinationId={dest['id']}") if p.get('tier')]
code={a['id']:f"A{i+1}" for i,a in enumerate(acts)}
def cut(t,n):
    t=re.sub(r'\s+',' ',t or '').strip(); return t if len(t)<=n else t[:n-1]+'…'
SYS=open('/Users/m/myhive-travel-app/myhive-backend/src/main/resources/prompts/ai/planner-system.st').read()
PRESET="""
READY-MADE PACKAGES are listed after the catalog, each made for one tier. They are the starting point:
- For every tier pick the ready-made package of that tier that fits the brief best and use its activities. Do not mix two ready-made packages into one tier.
- Change it only where the brief or the conversation asks for it.
- A tier has exactly as many activities as its ready-made package unless the organizer asked for a specific extra one. Never add one to fill a free slot or an empty day.
- A tier's price per person stays within 15 percent of its ready-made package's price unless the organizer asked for more or less.
- Place its own activities across the days so every day stays inside that tier's per-day limits below.
- A tier with no ready-made package is composed from the catalog as usual."""
def system(shape=None):
    s=SYS
    for k,v in {'destinationName':'Prague','locale':'en','days':'3','groupSize':'10','arrival':'AFTERNOON','departure':'MORNING',
                'dayWindows':'day 1: AFTERNOON, EVENING, NIGHT; day 2: MORNING, AFTERNOON, EVENING, NIGHT; day 3: MORNING','maxTierSpread':'4','presetRules':PRESET}.items():
        s=s.replace('{'+k+'}',v)
    s=s.replace('\\{','{').replace('\\}','}')
    if shape:
        s=s[:s.index('Answer ONLY')]+shape
    return s
def user(full=True):
    brief={"days":3,"groupSize":10,"categorySlugs":["nightlife"],"vibe":"nightlife first","dislikes":None,"budget":None,"arrival":None,"departure":None,"notes":None,"wishes":[]}
    o="BRIEF: "+json.dumps(brief)+"\n\nRECENT CONVERSATION:\nUSER: <user>Plan 2 nights for 10 of us — nightlife first</user>\n\nCATALOG (activityId | name | duration | price per person | group minimum | categories"+(" | about | includes" if full else "")+"):\n"
    for a in acts:
        cats=','.join(sorted(c['slug'] for c in a['categories']))
        o+=f"{code[a['id']]} | {a['name']} | {a['duration'] or 120} min | {a['price']} EUR pp | min {a.get('minPrice') or '-'} | {cats}"
        if full: o+=f" | {cut(a['description'],160)} | {cut(a.get('includes'),200) or '-'}"
        o+="\n"
    o+="\nREADY-MADE PACKAGES (tier | name | price per person | number of activities | activities, most important first):\n"
    for p in pk:
        ids=[code[x['activityId']] for x in p['activities'] if x['activityId'] in code]
        o+=f"{p['tier']} | {p['name']} | {sum(x['price'] for x in p['activities'])} EUR pp | {len(ids)} | {', '.join(ids)}\n"
    return o
codes=list(code.values())
item={"type":"object","properties":{"slot":{"type":"string","enum":["MORNING","AFTERNOON","EVENING","NIGHT"]},"startHint":{"type":["string","null"]},"activityId":{"type":"string","enum":codes}},"required":["slot","startHint","activityId"],"additionalProperties":False}
SCHEMA={"type":"object","properties":{"packages":{"type":"array","items":{"type":"object","properties":{"key":{"type":"string","enum":["BASIC","MEDIUM","PREMIUM"]},"days":{"type":"array","items":{"type":"object","properties":{"dayNumber":{"type":"integer"},"items":{"type":"array","items":item}},"required":["dayNumber","items"],"additionalProperties":False}}},"required":["key","days"],"additionalProperties":False}}},"required":["packages"],"additionalProperties":False}
COMPACT_SHAPE='Answer ONLY with compact JSON, one line: {"BASIC":[[day 1 picks],[day 2 picks],[day 3 picks]],"MEDIUM":[...],"PREMIUM":[...]} where a pick is the string "<slot letter>:<code>", slot letters M=MORNING A=AFTERNOON E=EVENING N=NIGHT, e.g. "E:A12". An empty day is [].'
pick={"type":"string","pattern":"^[MAEN]:A[0-9]{1,2}$"}
days={"type":"array","items":{"type":"array","items":pick}}
COMPACT={"type":"object","properties":{k:days for k in ("BASIC","MEDIUM","PREMIUM")},"required":["BASIC","MEDIUM","PREMIUM"],"additionalProperties":False}
def call(model,sysm,usr,schema,extra=None):
    body={"model":model,"temperature":0.4,"stream":True,"stream_options":{"include_usage":True},
          "messages":[{"role":"system","content":sysm},{"role":"user","content":usr}],
          "response_format":{"type":"json_schema","json_schema":{"name":"plan","strict":True,"schema":schema}},
          "enable_thinking":False,"reasoning":{"enabled":False}}
    if extra: body.update(extra)
    req=urllib.request.Request('https://openrouter.ai/api/v1/chat/completions',data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+KEY,'Content-Type':'application/json'})
    t0=time.time(); ttft=None; text=''; usage={}; prov=None
    try:
        with urllib.request.urlopen(req,timeout=120) as r:
            for raw in r:
                line=raw.decode('utf-8','ignore').strip()
                if not line.startswith('data:'): continue
                d=line[5:].strip()
                if d=='[DONE]': break
                try: j=json.loads(d)
                except Exception: continue
                prov=j.get('provider',prov)
                if j.get('error'): return {'error':str(j['error'])[:160]}
                for ch in j.get('choices',[]):
                    c=(ch.get('delta') or {}).get('content')
                    if c:
                        if ttft is None: ttft=time.time()-t0
                        text+=c
                if j.get('usage'): usage=j['usage']
    except urllib.error.HTTPError as e:
        return {'error':f"HTTP {e.code} {e.read().decode()[:160]}"}
    except Exception as e:
        return {'error':str(e)[:160]}
    total=time.time()-t0
    ok=False; why=[]
    try:
        j=json.loads(text)
        plan={}
        if 'packages' in j:
            for p in j['packages']: plan[p['key']]=[[(i['slot'][0],i['activityId']) for i in d['items']] for d in sorted(p['days'],key=lambda d:d['dayNumber'])]
        else:
            for k in ('BASIC','MEDIUM','PREMIUM'): plan[k]=[[tuple(x.split(':')) for x in d] for d in j[k]]
        win=[set('AEN'),set('MAEN'),set('M')]; cap={'BASIC':(2,360),'MEDIUM':(3,480),'PREMIUM':(4,540)}
        dur={v:(a['duration'] or 120) for a in acts for k,v in [(a['id'],code[a['id']])]}
        for k in ('BASIC','MEDIUM','PREMIUM'):
            if k not in plan: why.append('MISSING_TIER'); continue
            if len(plan[k])!=3: why.append(f'WRONG_DAY_COUNT/{k}={len(plan[k])}'); continue
            seen=set()
            for n,d in enumerate(plan[k]):
                if any(c not in dur for _,c in d): why.append('UNKNOWN'); continue
                if any(sl not in win[n] for sl,_ in d): why.append(f'SLOT_WINDOW/{k}/d{n+1}')
                if len({sl for sl,_ in d})<len(d): why.append(f'SLOT_TAKEN/{k}/d{n+1}')
                if len(d)>cap[k][0]: why.append(f'OVER_ITEMS/{k}/d{n+1}')
                if sum(dur[c] for _,c in d)+30*max(0,len(d)-1)>cap[k][1]: why.append(f'OVER_MINUTES/{k}/d{n+1}')
                for _,c in d:
                    if c in seen: why.append(f'DUP/{k}')
                    seen.add(c)
            if not seen: why.append(f'EMPTY/{k}')
        ok=not why
    except Exception as e: why=['UNPARSEABLE '+str(e)[:40]]
    return {'text':text,'ttft':ttft,'total':total,'out':usage.get('completion_tokens'),'in':usage.get('prompt_tokens'),'cached':(usage.get('prompt_tokens_details') or {}).get('cached_tokens'),'ok':ok,'why':why,'provider':prov}
VARIANTS=[
 ('V1 max, full catalog, current shape','qwen/qwen3.7-max',system(),user(True),SCHEMA,None),
 ('V2 max + provider sort=throughput','qwen/qwen3.7-max',system(),user(True),SCHEMA,{'provider':{'sort':'throughput'}}),
 ('V3 max, catalog without about/includes','qwen/qwen3.7-max',system(),user(False),SCHEMA,None),
 ('V4 max, compact answer shape','qwen/qwen3.7-max',system(COMPACT_SHAPE),user(True),COMPACT,None),
 ('V5 plus, current shape','qwen/qwen3.7-plus',system(),user(True),SCHEMA,None),
 ('V6 plus, compact shape','qwen/qwen3.7-plus',system(COMPACT_SHAPE),user(True),COMPACT,None),
 ('V7 3.8-flash, compact shape','qwen/qwen3.8-flash',system(COMPACT_SHAPE),user(True),COMPACT,None),
]
N=int(sys.argv[1]) if len(sys.argv)>1 else 3
out=[]
for name,model,s,u,schema,extra in VARIANTS:
    runs=[call(model,s,u,schema,extra) for _ in range(N)]
    good=[r for r in runs if 'error' not in r]
    if not good:
        print(f"{name:42} ERROR {runs[0]['error']}"); out.append({'name':name,'error':runs[0]['error']}); continue
    med=lambda k: round(st.median([r[k] for r in good if r[k] is not None]),2) if any(r[k] is not None for r in good) else None
    row={'name':name,'sample':good[0]['text'][:400],'n':len(good),'ttft_s':med('ttft'),'total_s':med('total'),'min_s':round(min(r['total'] for r in good),2),'max_s':round(max(r['total'] for r in good),2),'out_tokens':med('out'),'in_tokens':med('in'),'cached':med('cached'),'valid_shape':sum(1 for r in good if r['ok']),'provider':sorted({str(r['provider']) for r in good}),'errors':len(runs)-len(good)}
    out.append(row)
    row['violations']=[r['why'] for r in good]
    print(f"{name:42} total {row['total_s']:5}s (min {row['min_s']}, max {row['max_s']})  first token {row['ttft_s']}s  out {row['out_tokens']} tok  in {row['in_tokens']} tok  cached {row['cached']}  shape ok {row['valid_shape']}/{row['n']}  {row['violations']}", flush=True)
json.dump(out,open('bench-compose.json','w'),indent=1)
