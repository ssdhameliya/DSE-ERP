#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
def text(path): return (ROOT/path).read_text(encoding='utf-8',errors='replace')
def req(ok,msg):
    if not ok:
        print('FAIL -',msg); failures.append(msg)
    else: print('PASS -',msg)

failures=[]
desktop_root=ROOT/'desktop/src/main'
desktop='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in desktop_root.rglob('*') if p.is_file())
server_client=text('desktop/src/main/java/org/example/update/ServerReleaseClient.java')
update=text('desktop/src/main/java/org/example/update/UpdateService.java')
rollback=text('desktop/src/main/java/org/example/rollback/RollbackService.java')
server=text('server/src/main/java/org/example/server/update/UpdateDistributionService.java')
controller=text('server/src/main/java/org/example/server/update/UpdateDistributionController.java')
security=text('server/src/main/java/org/example/server/security/SecurityConfig.java')
props=text('server/src/main/resources/application.properties')
release=text('.github/workflows/release.yml')
prod=text('.github/workflows/deploy-prod.yml')
token_setup=text('scripts/linux/configure-github-update-token.sh')

req('api.github.com' not in desktop,'desktop contains no direct GitHub API endpoint')
req('GitHubReleaseClient' not in desktop,'desktop contains no direct GitHub release client')
req('update.github.owner' not in desktop and 'update.github.repository' not in desktop,'desktop no longer owns repository credentials/configuration')
req('/api/updates' in server_client and 'DSE_UPDATE_SERVICE_URL' in server_client,'desktop uses configurable company update service')
req('new ServerReleaseClient()' in update,'normal updater uses server release client')
req('updateService.byVersion' in rollback and 'updateService.releases' in rollback,'safe rollback uses the same server release gateway')
req('DSE_GITHUB_UPDATE_TOKEN' in props and 'dse.update.github.token' in props,'GitHub credential is configured only on Spring server')
req('Authorization", "Bearer " + token' in server,'server authenticates private GitHub requests')
req('HttpClient.Redirect.NEVER' in server,'server controls redirects explicitly')
req('redirected.GET()' in server and 'githubRequest' not in server.split('redirected.GET()',1)[0].split('HttpRequest.Builder redirected',1)[1],
    'signed asset redirect request is built without GitHub authorization helper')
req('"/api/updates/**"' in security and '.permitAll()' in security,'pre-login update gateway is available before authentication')
req('@RequestMapping("/api/updates")' in controller and '/assets/{assetId}' in controller,'server exposes release metadata and streaming asset proxy')
req('ensurePublishedAsset(assetId)' in server and 'release.path("draft").asBoolean(false)' in server,
    'public asset proxy is restricted to published non-draft release assets')
req('node.path("html_url")' not in server and 'Open Release Page' not in desktop,
    'private GitHub release page is not exposed through desktop update UI')
req('downloadVerified(UpdateRelease release' in update and 'ChecksumVerifier.verify(file,checksum)' in update,'private-repo path preserves SHA-256 verified installer flow')
for env in ('scripts/linux/uat.env.example','scripts/linux/prod.env.example'):
    req('DSE_GITHUB_UPDATE_TOKEN=CHANGE_ME' in text(env),f'{env} documents protected server token')
req(release.count('DSE_GITHUB_UPDATE_TOKEN: ${{ secrets.DSE_GITHUB_UPDATE_TOKEN }}') >= 2
    and 'configure-github-update-token.sh uat' in release
    and 'configure-github-update-token.sh prod' in release,
    'UAT and PROD tokens are supplied from their protected GitHub Environments rather than source')
req('PROD_PRIVATE_UPDATE_GATEWAY_OK' in release and 'UAT_PRIVATE_UPDATE_GATEWAY_OK' in release,
    'single-run UAT and PROD jobs both verify private GitHub release lookup through the server gateway')
req('DSE_GITHUB_UPDATE_TOKEN: ${{ secrets.DSE_GITHUB_UPDATE_TOKEN }}' in prod
    and 'configure-github-update-token.sh prod' in prod
    and 'PROD_PRIVATE_UPDATE_GATEWAY_OK' in prod,
    'manual PROD fallback uses the same protected GitHub Environment token model')
req('read -r TOKEN' in token_setup and 'DSE_GITHUB_UPDATE_TOKEN=%s' in token_setup
    and 'GITHUB_UPDATE_TOKEN_CONFIGURED' in token_setup,
    'server token setup consumes the secret on stdin and persists it without logging the credential')

if failures:
    print('PRIVATE_UPDATE_DISTRIBUTION_CONTRACT_FAIL')
    sys.exit(1)
print('PRIVATE_UPDATE_DISTRIBUTION_CONTRACT_OK desktop=server-gateway github-token=server-only prelogin=yes sha256=yes')
