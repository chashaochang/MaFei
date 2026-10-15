# OHOS credential storage

`OhosAssetCredentialStore(privateDirectory, namespace)` requires the persistent app-private
files directory supplied by the host. The path is not inferred and has no temporary/cache
fallback. All instances for a namespace must use the same directory. The module owns only
the `mafei-secure-<namespace>` child (0700), never the whole supplied directory.

## Storage and compatibility

- Existing short MFS1 values (encoded <= 1024 bytes) remain direct AssetStore secrets.
- Longer values, up to the shared MFS1 codec bound (1 MiB encoded), use CryptoArchitectureKit
  AES-256-GCM. Fresh OS CSPRNG bytes provide a per-write 256-bit key, 96-bit nonce and 128-bit
  opaque file ID. Namespace and alias framing are authenticated as AAD. The 128-bit tag is
  verified before decoding any plaintext. No algorithm, randomness, or verification fallback.
- AssetStore contains a 64-byte MFL1 reference with ID/key/nonce; files contain only ciphertext
  and tag. Keys keep device-first-unlock / no auth prompt / never sync Asset policy. They are
  protected by Asset, not claimed to be non-exportable hardware keys.
- Each write exclusively creates and fsyncs its immutable encrypted file before adding/updating
  Asset. The Asset operation is the commit point and overwrite=false remains atomic. Only
  after commit is the previously referenced file deleted. Short/long transitions retain the
  same public alias; enumeration never exposes crypto implementation keys.
- Per-directory Linux OFD locks cover cooperating threads, instances and processes, using
  existing CPF POSIX bindings. Missing lock support fails explicitly; it does not downgrade
  to an ineffective per-process record lock. An admitted blocking lock or native operation
  does not promise prompt cancellation; cancellation is checked again before Asset commit.
- File access uses directory descriptors, O_NOFOLLOW, O_EXCL, owner/type/mode/link checks,
  complete bounded reads/writes and fsync. Missing or corrupt referenced ciphertext is an error,
  not a logged-out/empty result. Known-alias deletion can remove corrupt metadata.

## Crash/failure boundary

This is not an atomic transaction between Asset service and filesystem. A crash before Asset
commit can leave ciphertext without its key; a crash after commit but before old-file cleanup
can leave obsolete ciphertext. Such files are not enumerated or restored. No automatic orphan
scanner deletes unknown files. A cleanup/fsync error after the commit is reported, although
the new credential may already be readable. No old key is regenerated or plaintext recovered
from settings. Deletion of the live Asset removes access to the key; orphan ciphertext may remain.

## Existing native APIs / sources

- CPF `CryptoSymKeyApi`, `CryptoSymCipherApi`, `CryptoCommonApi` platform KLIBs: API 12+
  `libohcrypto.so`; already provided by the toolchain, no Maven dependency added.
- Random bytes use `/dev/urandom` with complete-read/error handling. `CryptoRand` C APIs start
  at API 20 and are deliberately NOT referenced by this API-17-compatible module.
- GCM parameter/tag flow follows the official
  [OpenHarmony AES-GCM NDK guide](https://github.com/openharmony/docs/blob/master/en/application-dev/security/CryptoArchitectureKit/crypto-aes-sym-encrypt-decrypt-gcm-ndk.md),
  checked against local SDK headers and CPF KLIB metadata. Android's existing encrypted-file
  design supplied the encryption-before-file, AAD and explicit-corruption principles; no JVM
  implementation is linked on OHOS.

## Validation boundary

Mainline verification on 2026-09-08:

- `:platform:secure-storage-ohos:hostTest`: **28/28 executed**, including 15 existing
  short-value tests and 13 long-value tests. The host JVM target only runs injected
  common tests; its platform factories fail Unsupported and are not an Asset implementation.
- `compileKotlinOhosArm64`, `compileTestKotlinOhosArm64` and `linkDebugTestOhosArm64`
  passed. The resulting ELF links `libohcrypto.so` and `libasset_ndk.z.so`. This includes
  28 common plus 5 real-platform tests; **zero OHOS tests executed**.
- Logs: `/private/tmp/mafei-ohos-long-tests.log`. Native test linking initially caught
  one inferred non-Unit test return; corrected explicitly without changing assertions.
- `hdc list targets` returned `[Empty]`; no install, real Asset CRUD, system GCM
  execution or process-restart durability is claimed.
- Android host compile gate passed (app compile UP-TO-DATE). Fresh
  `:composeApp:publishDebugBinariesToHarmonyApp` succeeded, followed by unsigned ARM64
  HAP packaging and `node scripts/verify-harmony-artifacts.mjs` PASS. Logs:
  `/private/tmp/mafei-ohos-long-publish.log`, `/private/tmp/mafei-ohos-long-hap.log`.
  Existing fusion-renderer partial-linkage warnings remain; packaging is not first-frame proof.

Common tests cover short compatibility, long roundtrips, transitions, overwrite=false, codec
bounds, failed encryption/write/Asset commit, random-ID collision, cancellation-before-commit,
corrupt/missing/swapped records, key/nonce/AAD changes, cleanup failure and deletion.
The fake vault checks transaction behavior only, NOT cryptography.

OHOS tests include an AES-256-GCM known-answer vector, tamper/AAD rejection, long credentials
and competing writes through the real Asset service. They require an application-identity
test host with `MAFEI_TEST_PRIVATE_FILES_DIR` set to its persistent private directory. A shell
runner without Asset access is not sufficient. Real crypto, filesystem durability/locks,
Asset access and restart/HAP/device validation remain **Pending** until actually executed.
