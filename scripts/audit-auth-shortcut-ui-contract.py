#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

failures=[]
def require(cond,msg):
    if not cond: failures.append(msg)

def text(path): return Path(path).read_text(encoding='utf-8')

auth=text('desktop/src/main/java/org/example/api/auth/AuthApiClient.java')
login=text('desktop/src/main/java/org/example/controller/LoginController.java')
quote=text('desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java')
recon_api=text('desktop/src/main/java/org/example/api/recon/PurchaseReconApiClient.java')
settings=text('desktop/src/main/java/org/example/controller/SettingsController.java')
api_session=text('desktop/src/main/java/org/example/api/ApiSession.java')
config=text('desktop/src/main/java/org/example/config/ConfigManager.java')
runtime_contract=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
runtime_controller=text('server/src/main/java/org/example/server/runtime/RuntimeController.java')
runtime_controller_test=text('server/src/test/java/org/example/server/runtime/RuntimeControllerTest.java')
runtime_bootstrap=text('desktop/src/main/java/org/example/api/runtime/RuntimeBootstrapper.java')
shortcuts=text('desktop/src/main/resources/fxml/pages/settings/ShortcutsSettingsPanel.fxml')
prc=text('desktop/src/main/resources/fxml/pages/PurchaseRecon.fxml')
rs=text('desktop/src/main/resources/fxml/pages/ReconSupplier.fxml')
security_config=text('server/src/main/java/org/example/server/security/SecurityConfig.java')
quotation_service=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
purchase_recon_service=text('server/src/main/java/org/example/server/recon/PurchaseReconService.java')
purchase_recon_batch=text('server/src/main/java/org/example/server/persistence/entity/PurchaseReconImportBatchEntity.java')
insights_service=text('server/src/main/java/org/example/server/insights/InsightsService.java')

require('spring-security-bearer-v5' in runtime_contract, 'R12 must use the signed bearer-v5 API contract')
require('BUILD_REVISION = "9.0.14"' in runtime_contract and 'APP_VERSION = "9.0.14"' in runtime_contract, '9.0.14 must publish one exact desktop/server version/build contract so stale backends are rejected')
require('buildRevision' in runtime_controller, 'Runtime health must expose the backend build revision')
require(runtime_controller_test.count('new RuntimeController(service, RuntimeContract.APP_VERSION, RuntimeContract.API_REVISION, RuntimeContract.BUILD_REVISION)') == 2,
        'RuntimeController tests must instantiate the four-argument runtime contract constructor')
require('jsonPath("$.buildRevision").value(RuntimeContract.BUILD_REVISION)' in runtime_controller_test,
        'RuntimeController tests must assert the current build revision exposed by health')
require('RuntimeContract.BUILD_REVISION.equals(status.buildRevision())' in runtime_bootstrap,
        'Runtime bootstrap must reject a stale same-version backend build')
require('private static volatile String apiBaseUrl' in api_session and 'boundApiBaseUrl()' in api_session,
        'Bearer session must remember the exact Spring server that issued it')
require('ApiSession.establish(response.accessToken(), response.expiresAt(), issuingBaseUrl)' in auth,
        'Login must bind the bearer token to the issuing Spring endpoint')
require('String loginBase = preLoginBaseUrl();' in auth and 'requireCompatibleRuntime(loginBase);' in auth,
        'Login must verify the exact runtime contract before credentials/session are accepted')
require('postAt(loginBase, "/api/auth/login"' in auth,
        'Login must be sent to the same verified Spring endpoint')
require('establishSession(response, loginBase);' in auth,
        'Bearer session must bind to the exact verified server that issued it')
require('RuntimeContract.API_REVISION.equals(status.apiRevision())' in auth and 'RuntimeContract.BUILD_REVISION.equals(status.buildRevision())' in auth,
        'Auth client must independently reject stale same-version backends')
require('ApiSession.boundApiBaseUrl()' in config,
        'Authenticated business clients must stay on the login-bound Spring endpoint')
require('getDataApiBaseUrlUnbound()' in text('desktop/src/main/java/org/example/api/runtime/RuntimeApiClient.java'),
        'Runtime health must probe the configured endpoint independently of the current bearer binding')
require('rebindAuthenticatedSessionIfNeeded' in runtime_bootstrap and 'ApiSession.rebindApiBaseUrl' in runtime_bootstrap,
        'Runtime endpoint changes must verify the existing bearer token before rebinding business APIs')
require('org.example.api.ApiRuntime.HTTP' in auth, 'Auth API must use the shared business HTTP runtime')
require('verifyBusinessSession();' in auth, 'Login must verify a secured business endpoint before accepting the session')
require('PermissionService.refreshStrict();' in login, 'Login must load effective permissions strictly before dashboard')
token_service=text('server/src/main/java/org/example/server/security/TokenService.java')
token_codec=text('server/src/main/java/org/example/server/security/SignedTokenCodec.java')
bearer_filter=text('server/src/main/java/org/example/server/security/BearerTokenAuthenticationFilter.java')
security_config=text('server/src/main/java/org/example/server/security/SecurityConfig.java')
require('SignedTokenCodec.encode' in token_service and 'auth_version' in token_service,
        'R12 authentication must use signed tokens with per-user auth versioning')
require('auth_token_revocation' in token_service and 'UPDATE users SET auth_version=COALESCE(auth_version,0)+1' in token_service,
        'R12 logout/role/password invalidation must remain PostgreSQL-authoritative')
require('HmacSHA256' in token_codec and 'MessageDigest.isEqual' in token_codec,
        'Signed bearer codec must use HMAC-SHA256 and constant-time signature comparison')
require('AUTH_FAILURE_ATTRIBUTE' in bearer_filter and 'result.status().code()' in bearer_filter,
        'Bearer filter must retain a precise authentication failure reason')
require('AUTH_TOKEN_MISSING' not in security_config or 'AUTH_FAILURE_ATTRIBUTE' in security_config,
        '401 responses must expose the server authentication failure code')
require('private final String base' not in quote, 'Quotation API must not freeze the server base URL at construction')
require('URI.create(base()+p)' in quote, 'Quotation API must resolve the current canonical API base per request')
require('ApiSession.clear();throw new org.example.api.ApiSession.AuthenticationRequiredException("Purchase Recon' not in recon_api,
        'A single Purchase Recon 401 must not erase the desktop token before central session handling')
require('if (response.statusCode() == 401) { ApiSession.clear();' not in auth,
        'Auth helper calls must not destroy the global bearer token on one endpoint 401')

require('requestMatchers("/error"' in security_config,
        'R12 must permit Spring /error so authenticated backend failures are not rewritten as fake 401s')
require('if (result.authenticated()) request.removeAttribute(AUTH_FAILURE_ATTRIBUTE)' in bearer_filter,
        'R12 must not label successfully authenticated requests as authentication failures')
require("DATE_TRUNC('month',CAST(NULLIF(TRIM(quotation_date),'') AS DATE))" in quotation_service
        or "DATE_TRUNC('month',dse_safe_date(quotation_date))" in quotation_service,
        'Quotation metrics must safely parse legacy TEXT dates before PostgreSQL DATE_TRUNC')
require('nextConfiguredReference("REF_RECON_SUPPLIER"' in purchase_recon_service
        and 'nextConfiguredReference("REF_PURCHASE_RECON"' in purchase_recon_service,
        'Purchase Recon must use the central atomic Reference Master allocator')

require('private Integer importedRows=0' in purchase_recon_batch
        and 'private Integer duplicateRows=0' in purchase_recon_batch
        and 'private Integer warningRows=0' in purchase_recon_batch
        and 'private Integer ignoredRows=0' in purchase_recon_batch,
        'Purchase Recon import-batch NOT NULL counters must have Java-side zero defaults')
require('batch.setImportedRows(0)' in purchase_recon_service
        and 'batch.setDuplicateRows(0)' in purchase_recon_service
        and 'batch.setWarningRows(0)' in purchase_recon_service
        and 'batch.setIgnoredRows(0)' in purchase_recon_service,
        'Purchase Recon commit must initialize all batch counters before the first database insert')
require('FROM reminder_register' in insights_service
        and 'reminder KPIs' in insights_service,
        'Dashboard reminder KPI query must select from reminder_register')

require('validateShortcutManager' in settings and 'ShortcutRegistry.validateActions' in settings,
        'Shortcut Manager must validate only its owned visible catalog')
require('ShortcutRegistry.validate(' not in settings,
        'Shortcut Manager must not validate hidden PDF/Excel/Master default conflicts')
require('#disableSelectedShortcut' in shortcuts and '#deleteSelectedShortcut' in shortcuts,
        'Shortcut drawer must expose Disable and Delete Assignment controls')
require('disableShortcut(action)' in settings and 'deleteShortcutAssignment(action)' in settings,
        'Shortcut rows must expose per-action disable/delete behavior')

for name,xml in [('PurchaseRecon',prc),('ReconSupplier',rs)]:
    require('recon-metric-card' in xml, f'{name} KPI cards must use horizontal register-card anatomy')
    require('recon-register-page' in xml, f'{name} must opt into recon register KPI sizing')

for path in ('desktop/src/main/resources/fxml/pages/PurchaseRecon.fxml',
             'desktop/src/main/resources/fxml/pages/ReconSupplier.fxml',
             'desktop/src/main/resources/fxml/pages/settings/ShortcutsSettingsPanel.fxml'):
    try: ET.parse(path)
    except Exception as exc: failures.append(f'{path} XML parse failed: {exc}')

if failures:
    print('AUTH / SHORTCUT / RECON UI CONTRACT: FAIL')
    for f in failures: print(' -',f)
    sys.exit(2)
print('AUTH / SHORTCUT / RECON UI CONTRACT: PASS')
