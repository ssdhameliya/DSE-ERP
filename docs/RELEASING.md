# Releasing DSE ERP

## 1. Update the version

Set the Maven project version in `pom.xml`, for example:

```xml
<version>2.1.1</version>
```

The release tag must exactly match that value with a leading `v`.

## 2. Verify locally

```bash
mvn clean verify
```

For Windows installer testing:

```powershell
.\scripts\package-windows.ps1 -Version 2.1.1
```

For macOS installer testing:

```bash
./scripts/package-macos.sh 2.1.1
```

## 3. Commit before tagging

```bash
git add .
git commit -m "Release DSE ERP 2.1.1"
git push origin main
git tag v2.1.1
git push origin v2.1.1
```

Do not create the tag before the matching `pom.xml` change has been committed and pushed.

## 4. GitHub Actions output

The release workflow publishes:

- `DSE-ERP-<version>-Windows-x64.exe`
- `DSE-ERP-<version>-macOS-arm64.dmg`
- `DSE-ERP-<version>-macOS-x86_64.dmg`
- `checksums.txt`

## 5. Signing

Unsigned installers are suitable for internal testing but can trigger Windows SmartScreen or macOS Gatekeeper warnings. Public production releases should eventually use:

- A Windows code-signing certificate
- An Apple Developer ID certificate and Apple notarization

Store credentials only as encrypted GitHub Actions secrets. Never commit certificates, private keys, passwords, or app-specific passwords.
