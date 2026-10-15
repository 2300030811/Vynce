#!/usr/bin/env python3
"""
Release asset validation gate for Vynce multiplatform releases.
Ensures Android APK, Windows NSIS installer, Windows portable executable,
Linux AppImage/deb, macOS dmg/zip, differential blockmaps, and auto-updater
manifests (latest*.yml) are complete, authentic, and bit-identical before publication.
Hardened against path traversal, symlink escapes, and platform package omissions.
"""

import argparse
import base64
import hashlib
import os
import re
import shutil
import sys
import yaml


def parse_args():
    parser = argparse.ArgumentParser(description="Validate Vynce release assets")
    parser.add_argument(
        "--version",
        required=True,
        help="Target release version (e.g. 3.0.1)",
    )
    parser.add_argument(
        "--assets-dir",
        required=True,
        help="Path to directory containing release assets",
    )
    parser.add_argument(
        "--platform",
        choices=["windows", "linux", "macos", "all"],
        default="windows",
        help="Target platform validation contract (default: windows)",
    )
    parser.add_argument(
        "--require-apk",
        action="store_true",
        default=False,
        help="Require an Android APK (*.apk) to be present",
    )
    parser.add_argument(
        "--require-all-apks",
        action="store_true",
        default=False,
        help="Require all 5 official Android core APK variants (universal, arm64-v8a, armeabi-v7a, x86_64, x86) for target version",
    )
    parser.add_argument(
        "--require-installer",
        action="store_true",
        default=True,
        help="Require Windows NSIS installer (*Setup*.exe)",
    )
    parser.add_argument(
        "--require-portable",
        action="store_true",
        default=True,
        help="Require Windows portable executable ('Vynce <version>.exe')",
    )
    return parser.parse_args()


def validate_safe_manifest_filename(ref: str, label: str, assets_dir: str) -> str:
    """
    Validate that a manifest file reference (path or files[].url) is a safe,
    pure basename strictly confined within the intended staging directory.
    Rejects POSIX absolute paths, Windows drive/UNC paths, path traversal,
    nested directories, URL schemes, colons, NUL bytes, and symlink targets
    escaping the staging directory.
    """
    if not ref or not isinstance(ref, str) or not ref.strip():
        print(f"FATAL: {label} reference is missing or empty in manifest", file=sys.stderr)
        sys.exit(1)

    ref = ref.strip()

    # Reject NUL bytes
    if "\0" in ref:
        print(f"FATAL: {label} reference contains forbidden NUL byte: {ref!r}", file=sys.stderr)
        sys.exit(1)

    # Reject colons (covers Windows drive letters, URL schemes, NTFS alternate data streams)
    if ":" in ref:
        print(f"FATAL: {label} reference contains forbidden colon character or scheme: '{ref}'", file=sys.stderr)
        sys.exit(1)

    # Reject UNC paths (\\server\share or //server/share)
    if ref.startswith(("\\\\", "//")):
        print(f"FATAL: {label} reference is a UNC network path: '{ref}'", file=sys.stderr)
        sys.exit(1)

    # Reject POSIX absolute paths (/...) and Windows backslash paths (\...)
    if ref.startswith(("/", "\\")):
        print(f"FATAL: {label} reference is an absolute path: '{ref}'", file=sys.stderr)
        sys.exit(1)

    # Reject any directory separators (forward-slash or backslash) -> path traversal or nested path
    if "/" in ref or "\\" in ref:
        print(f"FATAL: {label} reference contains directory separators or path traversal: '{ref}'", file=sys.stderr)
        sys.exit(1)

    # Reject reserved relative names '.' and '..'
    if ref in (".", ".."):
        print(f"FATAL: {label} reference is a reserved relative name: '{ref}'", file=sys.stderr)
        sys.exit(1)

    # Reject if basename differs from ref
    if os.path.basename(ref) != ref:
        print(f"FATAL: {label} reference is not a pure basename: '{ref}'", file=sys.stderr)
        sys.exit(1)

    # Confirm resolved path remains strictly within assets_dir using commonpath
    abs_assets_dir = os.path.abspath(assets_dir)
    resolved_path = os.path.abspath(os.path.join(abs_assets_dir, ref))
    try:
        common = os.path.commonpath([abs_assets_dir, resolved_path])
        if common != abs_assets_dir or os.path.dirname(resolved_path) != abs_assets_dir:
            print(
                f"FATAL: Resolved path for {label} reference '{ref}' escapes staging directory '{abs_assets_dir}'",
                file=sys.stderr,
            )
            sys.exit(1)
    except Exception as e:
        print(f"FATAL: Cannot verify commonpath for {label} reference '{ref}': {e}", file=sys.stderr)
        sys.exit(1)

    # Symlink & realpath confinement check:
    # If the file exists on disk or is a symlink, verify its real target stays strictly inside real_assets_dir
    real_assets_dir = os.path.realpath(abs_assets_dir)
    if os.path.exists(resolved_path) or os.path.islink(resolved_path):
        real_target = os.path.realpath(resolved_path)
        try:
            common_real = os.path.commonpath([real_assets_dir, real_target])
            if common_real != real_assets_dir or os.path.dirname(real_target) != real_assets_dir:
                print(
                    f"FATAL: Symlink or real target for {label} reference '{ref}' escapes staging directory: '{real_target}' outside '{real_assets_dir}'",
                    file=sys.stderr,
                )
                sys.exit(1)
        except Exception as e:
            print(f"FATAL: Cannot verify realpath confinement for {label} reference '{ref}': {e}", file=sys.stderr)
            sys.exit(1)

    return ref


def validate_apk(assets_dir: str):
    apk_files = [f for f in os.listdir(assets_dir) if f.endswith(".apk") and os.path.isfile(os.path.join(assets_dir, f))]
    if not apk_files:
        print("FATAL: Android APK is missing from release assets! Aborting publication.", file=sys.stderr)
        sys.exit(1)
    print(f"Verified Android APK: {apk_files[0]}")


REQUIRED_APK_ARCHITECTURES = [
    "universal",
    "arm64-v8a",
    "armeabi-v7a",
    "x86_64",
    "x86",
]


def validate_all_apks(assets_dir: str, target_ver: str):
    """
    Validate that all five official Android core release APK variants
    (universal, arm64-v8a, armeabi-v7a, x86_64, x86) for the target version
    are present in the release assets directory. Rejects missing variants,
    duplicates, version mismatches, and unrecognized architecture files.
    """
    apk_files = [
        f for f in os.listdir(assets_dir)
        if f.lower().endswith(".apk") and os.path.isfile(os.path.join(assets_dir, f))
    ]
    if not apk_files:
        print("FATAL: No Android APK files found in release assets! Aborting publication.", file=sys.stderr)
        sys.exit(1)

    # 1. Path confinement & safety validation for every APK file
    for apk in apk_files:
        validate_safe_manifest_filename(apk, f"Android APK '{apk}'", assets_dir)

    # 2. Strict filename inspection for all staged APKs
    # Expected pattern: Vynce-<version>-core-<arch>-release-<buildCode>.apk
    general_pattern = re.compile(
        r"^Vynce-(?P<version>[0-9]+\.[0-9]+\.[0-9]+)-core-(?P<arch>[a-zA-Z0-9_\-]+)-release-(?P<code>[0-9]+)\.apk$"
    )

    detected_variants = {}
    for apk in apk_files:
        m = general_pattern.match(apk)
        if not m:
            print(
                f"FATAL: Staged Android APK '{apk}' has an invalid or unexpected filename format. Expected 'Vynce-{target_ver}-core-<arch>-release-<buildCode>.apk'",
                file=sys.stderr,
            )
            sys.exit(1)

        apk_ver = m.group("version")
        if apk_ver != target_ver:
            print(
                f"FATAL: Staged Android APK '{apk}' version ({apk_ver}) does not match target release version ({target_ver})!",
                file=sys.stderr,
            )
            sys.exit(1)

        apk_arch = m.group("arch")
        if apk_arch not in REQUIRED_APK_ARCHITECTURES:
            print(
                f"FATAL: Staged Android APK '{apk}' has unknown or unexpected architecture '{apk_arch}'. Expected one of: {REQUIRED_APK_ARCHITECTURES}",
                file=sys.stderr,
            )
            sys.exit(1)

        if apk_arch in detected_variants:
            print(
                f"FATAL: Duplicate Android APK variant staged for architecture '{apk_arch}': '{detected_variants[apk_arch]}' and '{apk}'!",
                file=sys.stderr,
            )
            sys.exit(1)

        detected_variants[apk_arch] = apk

    # 3. Completeness check: verify every required architecture is present
    missing_archs = [arch for arch in REQUIRED_APK_ARCHITECTURES if arch not in detected_variants]
    if missing_archs:
        print(
            f"FATAL: Missing required Android APK variant(s) for version {target_ver}: {missing_archs}! All 5 variants ({REQUIRED_APK_ARCHITECTURES}) are required.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"Verified all 5 required Android APK variants for v{target_ver}:")
    for arch in REQUIRED_APK_ARCHITECTURES:
        print(f"  - {arch}: {detected_variants[arch]}")


def find_installer(assets_dir: str, target_ver: str):
    candidates = [
        f"Vynce Setup {target_ver}.exe",
        f"Vynce-Setup-{target_ver}.exe",
    ]
    for c in candidates:
        full_cand = os.path.join(assets_dir, c)
        if os.path.isfile(full_cand):
            validate_safe_manifest_filename(c, f"Installer candidate '{c}'", assets_dir)
            return c

    for f in os.listdir(assets_dir):
        if "setup" in f.lower() and target_ver in f and f.endswith(".exe"):
            full_path = os.path.join(assets_dir, f)
            if os.path.isfile(full_path):
                validate_safe_manifest_filename(f, f"Installer candidate '{f}'", assets_dir)
                return f
    return None


def validate_portable(assets_dir: str, target_ver: str):
    candidates = [
        f"Vynce {target_ver}.exe",
        f"Vynce-{target_ver}.exe",
    ]
    for c in candidates:
        full_cand = os.path.join(assets_dir, c)
        if os.path.isfile(full_cand):
            validate_safe_manifest_filename(c, f"Portable candidate '{c}'", assets_dir)
            print(f"Verified Windows portable executable: {c}")
            return c

    for f in os.listdir(assets_dir):
        f_lower = f.lower()
        if f.startswith("Vynce") and target_ver in f and f.endswith(".exe") and "setup" not in f_lower:
            full_path = os.path.join(assets_dir, f)
            if os.path.isfile(full_path):
                validate_safe_manifest_filename(f, f"Portable candidate '{f}'", assets_dir)
                print(f"Verified Windows portable executable: {f}")
                return f

    print(
        f"FATAL: Windows portable executable ('Vynce {target_ver}.exe') is missing from release assets! Aborting publication.",
        file=sys.stderr,
    )
    sys.exit(1)


def validate_linux_artifacts(assets_dir: str, target_ver: str):
    # Enforces complete Linux platform package contract: requires both AppImage and deb
    appimages = [
        f for f in os.listdir(assets_dir)
        if f.lower().endswith(".appimage") and target_ver in f and os.path.isfile(os.path.join(assets_dir, f))
    ]
    if not appimages:
        print(f"FATAL: Linux AppImage (*.AppImage) for version {target_ver} is missing from release assets!", file=sys.stderr)
        sys.exit(1)
    for a in appimages:
        validate_safe_manifest_filename(a, f"Linux AppImage '{a}'", assets_dir)
    print(f"Verified Linux AppImage: {appimages[0]}")

    debs = [
        f for f in os.listdir(assets_dir)
        if f.lower().endswith(".deb") and target_ver in f and os.path.isfile(os.path.join(assets_dir, f))
    ]
    if not debs:
        print(f"FATAL: Linux Debian package (*.deb) for version {target_ver} is missing from release assets!", file=sys.stderr)
        sys.exit(1)
    for d in debs:
        validate_safe_manifest_filename(d, f"Linux Debian package '{d}'", assets_dir)
    print(f"Verified Linux deb package: {debs[0]}")

    linux_manifest = os.path.join(assets_dir, "latest-linux.yml")
    if os.path.isfile(linux_manifest):
        validate_manifest_and_staged_assets(assets_dir, target_ver, manifest_name="latest-linux.yml")


def validate_macos_artifacts(assets_dir: str, target_ver: str):
    # Enforces complete macOS platform package contract: requires both DMG and ZIP
    dmgs = [
        f for f in os.listdir(assets_dir)
        if f.lower().endswith(".dmg") and target_ver in f and os.path.isfile(os.path.join(assets_dir, f))
    ]
    if not dmgs:
        print(f"FATAL: macOS disk image (*.dmg) for version {target_ver} is missing from release assets!", file=sys.stderr)
        sys.exit(1)
    for dmg in dmgs:
        validate_safe_manifest_filename(dmg, f"macOS DMG '{dmg}'", assets_dir)
    print(f"Verified macOS DMG: {dmgs[0]}")

    zips = [
        f for f in os.listdir(assets_dir)
        if f.lower().endswith(".zip") and target_ver in f and os.path.isfile(os.path.join(assets_dir, f))
    ]
    if not zips:
        print(f"FATAL: macOS ZIP archive (*.zip) for version {target_ver} is missing from release assets!", file=sys.stderr)
        sys.exit(1)
    for z in zips:
        validate_safe_manifest_filename(z, f"macOS ZIP '{z}'", assets_dir)
    print(f"Verified macOS ZIP: {zips[0]}")

    mac_manifest = os.path.join(assets_dir, "latest-mac.yml")
    if os.path.isfile(mac_manifest):
        validate_manifest_and_staged_assets(assets_dir, target_ver, manifest_name="latest-mac.yml")


def validate_checksum_format(sha_str: str, label: str) -> bytes:
    if not sha_str or not isinstance(sha_str, str) or not sha_str.strip():
        print(f"FATAL: {label} checksum is missing or empty in manifest", file=sys.stderr)
        sys.exit(1)
    sha_str = sha_str.strip()
    try:
        decoded = base64.b64decode(sha_str)
    except Exception as e:
        print(f"FATAL: {label} checksum is not valid Base64: {e}", file=sys.stderr)
        sys.exit(1)

    if len(decoded) != 64:
        print(
            f"FATAL: {label} checksum decoded length is {len(decoded)} bytes; expected 64 bytes for SHA-512",
            file=sys.stderr,
        )
        sys.exit(1)
    return decoded


def calculate_sha512_b64(file_path: str, assets_dir: str = None) -> str:
    if assets_dir:
        real_assets_dir = os.path.realpath(assets_dir)
        real_file = os.path.realpath(file_path)
        if os.path.commonpath([real_assets_dir, real_file]) != real_assets_dir:
            print(f"FATAL: Target file '{file_path}' resolves via symlink to '{real_file}' outside staging directory", file=sys.stderr)
            sys.exit(1)

    hasher = hashlib.sha512()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return base64.b64encode(hasher.digest()).decode("ascii")


def validate_manifest_and_staged_assets(assets_dir: str, target_ver: str, manifest_name: str = "latest.yml"):
    manifest_path = os.path.join(assets_dir, manifest_name)
    if not os.path.isfile(manifest_path):
        print(f"FATAL: {manifest_name} auto-update manifest is missing at {manifest_path}! Aborting publication.", file=sys.stderr)
        sys.exit(1)

    validate_safe_manifest_filename(manifest_name, f"Manifest '{manifest_name}'", assets_dir)

    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest = yaml.safe_load(f)
    except Exception as e:
        print(f"FATAL: {manifest_name} could not be parsed as valid YAML: {e}", file=sys.stderr)
        sys.exit(1)

    if not isinstance(manifest, dict):
        print(f"FATAL: {manifest_name} content is not a valid YAML mapping", file=sys.stderr)
        sys.exit(1)

    # 1. Version check
    m_ver = str(manifest.get("version", "")).strip()
    if not m_ver:
        print(f"FATAL: {manifest_name} version field is missing or empty", file=sys.stderr)
        sys.exit(1)
    if m_ver != target_ver:
        print(
            f"FATAL: {manifest_name} version ({m_ver}) does not match target release version ({target_ver})",
            file=sys.stderr,
        )
        sys.exit(1)
    print(f"Verified {manifest_name} version matches release: {m_ver}")

    # 2. Installer path reference check (hardened against path traversal and symlinks)
    raw_path = manifest.get("path")
    m_path = validate_safe_manifest_filename(raw_path, f'{manifest_name} "path"', assets_dir)

    # 3. Files array check
    files_list = manifest.get("files")
    if not isinstance(files_list, list) or len(files_list) == 0:
        print(f"FATAL: {manifest_name} \"files\" array is missing or empty", file=sys.stderr)
        sys.exit(1)

    # 4. Mandatory Root sha512 check
    root_sha = manifest.get("sha512")
    if not root_sha or not isinstance(root_sha, str) or not root_sha.strip():
        print(f"FATAL: {manifest_name} root sha512 checksum is missing or empty", file=sys.stderr)
        sys.exit(1)
    root_sha = root_sha.strip()
    validate_checksum_format(root_sha, f"{manifest_name} root sha512")

    # 5. Mandatory Per-file sha512 checks in files[] (hardened against path traversal and symlinks)
    expected_hashes = {}
    for idx, item in enumerate(files_list):
        if not isinstance(item, dict):
            print(f"FATAL: Entry {idx} in {manifest_name} files array is not a mapping", file=sys.stderr)
            sys.exit(1)
        raw_url = item.get("url")
        url = validate_safe_manifest_filename(raw_url, f'{manifest_name} files[{idx}].url', assets_dir)

        file_sha = item.get("sha512")
        if not file_sha or not isinstance(file_sha, str) or not file_sha.strip():
            print(f"FATAL: Per-file sha512 checksum for \"{url}\" is missing or empty in {manifest_name}", file=sys.stderr)
            sys.exit(1)
        file_sha = file_sha.strip()
        validate_checksum_format(file_sha, f"{manifest_name} files entry \"{url}\"")

        # Inconsistency detection
        if url == m_path and file_sha != root_sha:
            print(
                f"FATAL: Inconsistent sha512 checksums in {manifest_name} for \"{m_path}\": root sha512 does not match files[] entry sha512",
                file=sys.stderr,
            )
            sys.exit(1)

        expected_hashes[url] = file_sha

    if m_path in expected_hashes:
        if expected_hashes[m_path] != root_sha:
            print(
                f"FATAL: Inconsistent sha512 checksums in {manifest_name} for \"{m_path}\": root sha512 does not match files[] entry sha512",
                file=sys.stderr,
            )
            sys.exit(1)
    else:
        expected_hashes[m_path] = root_sha

    if not expected_hashes:
        print(f"FATAL: No verifiable installer hashes found in {manifest_name}", file=sys.stderr)
        sys.exit(1)

    # 6. Staged asset resolution & source artifact verification for compatibility aliases
    real_assets_dir = os.path.realpath(assets_dir)
    for ref in list(expected_hashes.keys()):
        # ref is guaranteed safe by validate_safe_manifest_filename
        ref_path = os.path.join(assets_dir, ref)
        if not os.path.isfile(ref_path):
            candidates = [ref.replace("-", " "), ref.replace(" ", "-")]
            valid_sources = [c for c in candidates if c != ref]
            source_found = None
            for cand in valid_sources:
                validate_safe_manifest_filename(cand, f"Alias candidate '{cand}'", assets_dir)
                cand_path = os.path.join(assets_dir, cand)
                if os.path.isfile(cand_path):
                    source_found = cand
                    break

            if not source_found:
                print(
                    f"FATAL: Manifest installer reference \"{ref}\" cannot be resolved to any staged asset in {assets_dir} (source installer is missing)",
                    file=sys.stderr,
                )
                sys.exit(1)

            source_path = os.path.join(assets_dir, source_found)
            source_blockmap = source_path + ".blockmap"
            target_blockmap = ref_path + ".blockmap"

            # Check that source blockmap exists before creating alias
            if not os.path.isfile(source_blockmap):
                print(
                    f"FATAL: Missing source blockmap \"{os.path.basename(source_blockmap)}\" when compatibility alias is required for \"{ref}\"",
                    file=sys.stderr,
                )
                sys.exit(1)

            # Validate that neither source nor target blockmap escapes assets_dir
            validate_safe_manifest_filename(os.path.basename(source_blockmap), "Source blockmap", assets_dir)
            validate_safe_manifest_filename(os.path.basename(target_blockmap), "Target blockmap alias", assets_dir)

            # Realpath verification before copying
            real_source = os.path.realpath(source_path)
            real_source_blockmap = os.path.realpath(source_blockmap)
            if os.path.commonpath([real_assets_dir, real_source]) != real_assets_dir:
                print(f"FATAL: Source file '{source_path}' resolves via symlink to '{real_source}' outside staging directory", file=sys.stderr)
                sys.exit(1)
            if os.path.commonpath([real_assets_dir, real_source_blockmap]) != real_assets_dir:
                print(f"FATAL: Source blockmap '{source_blockmap}' resolves via symlink outside staging directory", file=sys.stderr)
                sys.exit(1)

            print(f"Creating installer compatibility alias: {ref} <- {source_found}")
            shutil.copy2(source_path, ref_path)

            if not os.path.isfile(target_blockmap):
                print(f"Creating blockmap compatibility alias: {os.path.basename(target_blockmap)} <- {os.path.basename(source_blockmap)}")
                shutil.copy2(source_blockmap, target_blockmap)

    # 7. Checksum recalculation & byte-for-byte comparison against staged files
    for ref, expected_sha in expected_hashes.items():
        ref_file = os.path.join(assets_dir, ref)
        if not os.path.isfile(ref_file):
            print(f"FATAL: Staged installer asset \"{ref}\" does not exist at {ref_file}", file=sys.stderr)
            sys.exit(1)

        computed_sha = calculate_sha512_b64(ref_file, assets_dir=assets_dir)
        if computed_sha != expected_sha:
            print(
                f"FATAL: SHA-512 mismatch for installer asset \"{ref}\"! Expected: {expected_sha}, Computed: {computed_sha}",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"Verified SHA-512 digest for {ref} matches manifest exactly.")


def main():
    args = parse_args()
    assets_dir = os.path.abspath(args.assets_dir)
    target_ver = args.version.strip()

    if not os.path.isdir(assets_dir):
        print(f"FATAL: Staging directory '{assets_dir}' does not exist or is not a directory.", file=sys.stderr)
        sys.exit(1)

    print(f"Validating release assets for version {target_ver} (platform: {args.platform}) in: {assets_dir}")

    # Check APK if required
    if args.require_all_apks:
        validate_all_apks(assets_dir, target_ver)
    elif args.require_apk:
        validate_apk(assets_dir)

    # Platform: Windows
    if args.platform in ("windows", "all"):
        if args.require_installer:
            installer = find_installer(assets_dir, target_ver)
            if not installer:
                print("FATAL: Windows NSIS installer is missing! Aborting publication.", file=sys.stderr)
                sys.exit(1)
            print(f"Verified Windows installer: {installer}")

        if args.require_portable:
            validate_portable(assets_dir, target_ver)

        validate_manifest_and_staged_assets(assets_dir, target_ver, manifest_name="latest.yml")

    # Platform: Linux
    if args.platform in ("linux", "all"):
        validate_linux_artifacts(assets_dir, target_ver)

    # Platform: macOS
    if args.platform in ("macos", "all"):
        validate_macos_artifacts(assets_dir, target_ver)

    print("ALL REQUIRED MULTIPLATFORM ASSETS AND UPDATER MANIFESTS VERIFIED SUCCESSFULLY.")
    sys.exit(0)


if __name__ == "__main__":
    main()
