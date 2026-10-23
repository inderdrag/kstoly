"""Run only in GitHub Actions after a successful Android build.
Deletes retired desktop installers, never APKs or release tags.
"""
import json, os, re, urllib.request
repo=os.environ['GITHUB_REPOSITORY']
token=os.environ['GH_TOKEN']
def api(path,method='GET',data=None):
    request=urllib.request.Request('https://api.github.com/repos/'+repo+path,method=method,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/json'},data=None if data is None else json.dumps(data).encode())
    with urllib.request.urlopen(request,timeout=30) as response:
        raw=response.read()
        return json.loads(raw) if raw else None
removed=0
page=1
while True:
    releases=api('/releases?per_page=100&page='+str(page))
    if not releases:break
    for release in releases:
        desktop=[a for a in release.get('assets',[]) if a['name'].lower().endswith('.exe')]
        if not desktop:continue
        for asset in desktop:
            api('/releases/assets/'+str(asset['id']),'DELETE');removed+=1
        # Remove only obsolete platform descriptions, preserving Android notes.
        body='\n'.join(line for line in (release.get('body') or '').splitlines() if not re.search(r'Windows|\.exe|EXE|Electron/NSIS',line,re.I))
        name=re.sub(r'\s*/\s*Windows.*$','',release.get('name') or '',flags=re.I)
        api('/releases/'+str(release['id']),'PATCH',{'body':body,'name':name})
    page+=1
print('Retired installer assets removed:',removed)
# Remove only artifacts explicitly named for the retired platform.
page=1
while True:
    artifacts=api('/actions/artifacts?per_page=100&page='+str(page)).get('artifacts',[])
    if not artifacts:break
    for artifact in artifacts:
        if artifact['name'] in ('Windows-EXE','Windows-Installer'):
            api('/actions/artifacts/'+str(artifact['id']),'DELETE')
    page+=1
