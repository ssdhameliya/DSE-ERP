# Releasing DSE ERP

## 1. Update the version

Set the Maven project version in `pom.xml`, for example:

```xml
<version>2.0.1</version>
```

`app-version.properties` and the default update configuration are filtered from the Maven version during the build.

## 2. Verify locally

```bash
mvn clean verify
```

For Windows installer testing:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -Version 2.0.1
```

For macOS installer testing:

```bash
./scripts/package-macos.sh 2.0.1
```

## 3. Commit and tag

```bash
git add .
git commit -m "Release DSE ERP 2.0.1"
git push origin main
git tag v2.0.1
git push origin v2.0.1
```

The tag must match the version in `pom.xml`.

## 4. GitHub Actions output

The release workflow publishes:

- `DSE-ERP-<version>-Windows-x64.exe`
- `DSE-ERP-<version>-macOS-arm64.dmg`
- `DSE-ERP-<version>-macOS-x86_64.dmg`
- `checksums.txt`

## 5. Signing later

Unsigned installers are suitable for internal testing but can trigger Windows SmartScreen or macOS Gatekeeper warnings. Production public releases should eventually use:

- A Windows code-signing certificate
- An Apple Developer ID certificate and Apple notarization

Store signing credentials only as encrypted GitHub Actions secrets. Never commit certificates, private keys, or passwords.
