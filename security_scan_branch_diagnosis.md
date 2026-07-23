# Security Scan Branch Mismatch Diagnosis & Remediation Report

## Executive Summary

This report documents the root cause analysis, branch alignment, workflow enhancement, and verification for the GitHub Actions security scan reporting outdated Netty vulnerabilities (`io.netty:netty-transport:4.2.15.Final`).

The investigation confirmed that the Netty security patch commit (**`068e1d4`**) existed on the **`maintenance`** branch, whereas **`master`** was pointing to an older commit (**`01a4855`**). Because the GitHub Actions workflow (`.github/workflows/security-scan.yml`) was configured to trigger on `master` and `main`, CI runs were scanning `master` prior to the Netty upgrade.

To resolve the mismatch:
1. `maintenance` was merged into `master` (fast-forwarding `master` to include `068e1d4`).
2. `.github/workflows/security-scan.yml` was upgraded to add `maintenance` to trigger branches, introduce commit/branch diagnostic logging, generate and upload a detailed `dependency-tree.txt` artifact, and execute `./mvnw -U clean verify org.owasp:dependency-check-maven:12.2.2:check` with strict security gate enforcement (`failBuildOnCVSS=7`).

---

## 1. Branch & Commit Status Analysis

Prior to remediation, the commit state across local and remote branches was as follows:

| Branch | Latest Commit SHA | Commit Message | Netty Version |
| :--- | :--- | :--- | :--- |
| **`maintenance`** | `068e1d4` | `fix(security): resolved netty OWASP vulnerabilities` | **`4.2.16.Final`** |
| **`origin/maintenance`** | `068e1d4` | `fix(security): resolved netty OWASP vulnerabilities` | **`4.2.16.Final`** |
| **`master`** *(Before Merge)* | `01a4855` | `fix(security): upgrade PostgreSQL JDBC and resolve OWASP dependency scan` | `4.2.15.Final` |
| **`origin/master`** | `01a4855` | `fix(security): upgrade PostgreSQL JDBC and resolve OWASP dependency scan` | `4.2.15.Final` |

### Diagnosis
Commit `068e1d4` did **not** exist on `master`. Since the CI pipeline only triggered on pushes to `main` and `master`, GitHub Actions ran against `01a4855` on `master`, which contained Netty `4.2.15.Final` and produced CVE warnings (`CVE-2026-44891`, `CVE-2026-55833`, `CVE-2026-55831`).

---

## 2. Actions Taken

### 2.1 Branch Alignment
`maintenance` was merged into `master` via fast-forward merge:
```bash
git checkout master
git merge maintenance
```
Post-merge HEAD on `master`: `068e1d4` (and subsequent workflow updates).

### 2.2 Workflow Enhancements (`.github/workflows/security-scan.yml`)

1. **Trigger Expansion**: Added `maintenance` to `on.push.branches` and `on.pull_request.branches`.
2. **Diagnostic Steps**:
   - Log current commit SHA: `git rev-parse HEAD`
   - Log current branch name: `git rev-parse --abbrev-ref HEAD`
   - Output dependency tree with Netty and PostgreSQL filtering:
     ```bash
     ./mvnw dependency:tree -Dverbose > dependency-tree.txt
     ```
   - Upload `dependency-tree.txt` as a workflow artifact using `actions/upload-artifact@v4`.
3. **OWASP Execution Command Updated**:
   Configured Maven execution to force update dependencies and clean verify prior to running OWASP check:
   ```bash
   ./mvnw -U clean verify \
     org.owasp:dependency-check-maven:12.2.2:check \
     -DfailBuildOnCVSS=7 \
     -Dformat=HTML \
     -DsuppressionFile=dependency-check-suppression.xml \
     -DnvdApiKey=${NVD_API_KEY}
   ```

---

## 3. Dependency Verification & Diagnostic Output

Local execution of `./mvnw dependency:tree -Dverbose > dependency-tree.txt` confirms all Netty dependencies resolve to **`4.2.16.Final`**:

```text
[INFO] +- org.springframework.boot:spring-boot-starter-data-redis:jar:4.1.0:compile
[INFO] |  +- org.springframework.boot:spring-boot-data-redis:jar:4.1.0:compile
[INFO] |  |  +- io.lettuce:lettuce-core:jar:7.5.2.RELEASE:compile
[INFO] |  |  |  +- io.netty:netty-common:jar:4.2.16.Final:compile
[INFO] |  |  |  +- io.netty:netty-handler:jar:4.2.16.Final:compile
[INFO] |  |  |  |  +- io.netty:netty-resolver:jar:4.2.16.Final:compile
[INFO] |  |  |  |  +- io.netty:netty-buffer:jar:4.2.16.Final:compile
[INFO] |  |  |  |  +- io.netty:netty-transport-native-unix-common:jar:4.2.16.Final:compile
[INFO] |  |  |  |  \- io.netty:netty-codec-base:jar:4.2.16.Final:compile
[INFO] |  |  |  +- io.netty:netty-transport:jar:4.2.16.Final:compile
[INFO] |  |  |  \- io.netty:netty-resolver-dns:jar:4.2.16.Final:compile
[INFO] |  |  |     \- io.netty:netty-codec-dns:jar:4.2.16.Final:compile
```

PostgreSQL driver remains resolved to **`42.7.12`**:
```text
[INFO] +- org.postgresql:postgresql:jar:42.7.12:runtime
```

---

## 4. OWASP Security Scan Result

Command executed:
```bash
./mvnw -U clean verify org.owasp:dependency-check-maven:12.2.2:check -DfailBuildOnCVSS=7 -Dformat=HTML -DsuppressionFile=dependency-check-suppression.xml
```

- **CVSS Gate**: `7.0` (High/Critical threshold preserved, NOT weakened)
- **Suppressions**: 0 suppressions added (`dependency-check-suppression.xml` unmodified)
- **Status**: **`BUILD SUCCESS`**
- **Result**: Zero vulnerabilities &ge; 7.0 detected across Netty and all other project dependencies.

---

## 5. Verification & Acceptance Criteria Checklist

- [x] **`master` contains Netty fix**: Commit `068e1d4` merged into `master`.
- [x] **CI builds expected commit**: Workflow trigger updated to include `master` and `maintenance`. Diagnostic step prints commit SHA.
- [x] **Dependency tree artifact uploaded**: `dependency-tree.txt` generated and uploaded as artifact.
- [x] **OWASP scans resolved Netty version**: `io.netty:netty-transport` scanned at version `4.2.16.Final`.
- [x] **Security gate intact**: `failBuildOnCVSS=7` maintained. Zero suppressions added.
