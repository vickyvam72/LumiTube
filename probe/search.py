import json, urllib.request
def post(q):
    body={"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20260804.16.00","hl":"id","gl":"ID"}},"query":q}
    req=urllib.request.Request("https://music.youtube.com/youtubei/v1/search?prettyPrint=false",data=json.dumps(body).encode(),headers={"Content-Type":"application/json","User-Agent":"Mozilla/5.0","Origin":"https://music.youtube.com"})
    return json.load(urllib.request.urlopen(req))
r=post("drive")
open("probe_out.json","w").write(json.dumps(r))
# path summary of renderer keys
lines=[]
def walk(o,path,depth):
    if depth>14: return
    if isinstance(o,dict):
        for k,v in o.items():
            if k.endswith("Renderer") or k in("contents","tabs","items"):
                p=path+"/"+k
                if k.endswith("Renderer"): lines.append(p)
                walk(v,p if k.endswith("Renderer") else path,depth+1)
            elif isinstance(v,(dict,list)): walk(v,path,depth+1)
    elif isinstance(o,list):
        for i,x in enumerate(o[:3]): walk(x,path,depth+1)
walk(r,"",0)
seen=[]
for l in lines:
    s="/".join(x.replace("Renderer","R") for x in l.split("/")[-4:])
    if s not in seen: seen.append(s)
msg=" | ".join(seen)
for i in range(0,min(len(msg),40000),3500):
    print("::warning::"+msg[i:i+3500])
