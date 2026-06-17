#!/usr/bin/env python3
"""
Cross-compile Python native packages for Android ARM64 (aarch64-linux-android).

Usage:
  python3 ci/cross_compile_wheels.py

Requires: Android NDK (installed by this script if not found), Rust toolchain
"""

import os, sys, subprocess, tarfile, glob, shutil, hashlib, json, tempfile
from pathlib import Path

WORKSPACE = os.environ.get("GITHUB_WORKSPACE", os.getcwd())
OFFLINE_PKGS = os.path.join(WORKSPACE, "app", "offline_pkgs")
ANDROID_HOME = os.environ.get("ANDROID_HOME", "")
NDK_VERSION = "27.0.12077973"  # Compatible with AGP used by this project

# Packages to cross-compile
PACKAGES = [
    {
        "name": "sxtwl",
        "version": "2.0.6",
        "py_pkg": "sxtwl",
        "type": "c",
        "patches": [
            # Remove distutils deprecation (Python 3.12 compatibility)
            ("sed -i 's/from distutils import ccompiler//' setup.py", False),
            ("sed -i 's/if ccompiler.get_default_compiler() == \"msvc\":/"
             "if platform.system() == \"Windows\":/' setup.py", False),
        ],
    },
    {
        "name": "pyswisseph",
        "version": "2.10.3.2",
        "py_pkg": "pyswisseph",
        "type": "c",
        "patches": [
            ("sed -i 's/swe_detection = True/swe_detection = False/' setup.py", True),
            ("sed -i 's/sqlite3_detection = True/sqlite3_detection = False/' setup.py", True),
        ],
    },
    {
        "name": "pydantic-core",
        "version": "2.46.4",
        "py_pkg": "pydantic_core",
        "type": "rust",
    },
]


def log(msg):
    print(f"[cross-compile] {msg}", flush=True)


def run(cmd, **kwargs):
    log(f"Running: {cmd if isinstance(cmd, str) else ' '.join(cmd)}")
    return subprocess.run(cmd, shell=isinstance(cmd, str), **kwargs)


def find_or_install_ndk():
    """Find existing NDK or install it."""
    # Check common NDK locations
    ndk_dirs = [
        os.environ.get("NDK_DIR", ""),
        os.path.join(ANDROID_HOME, "ndk", NDK_VERSION),
        os.path.join(ANDROID_HOME, "ndk-bundle"),
        os.path.join(Path.home(), "Android", "Sdk", "ndk", NDK_VERSION),
    ]
    # Check if sdkmanager already installed
    sdkmanager_paths = [
        os.path.join(ANDROID_HOME, "cmdline-tools", "latest", "bin", "sdkmanager"),
        os.path.join(ANDROID_HOME, "cmdline-tools", "bin", "sdkmanager"),
    ]
    
    for d in ndk_dirs:
        if d and os.path.exists(os.path.join(d, "toolchains", "llvm", "prebuilt", "linux-x86_64")):
            log(f"Found NDK at: {d}")
            return d
    
    # Install NDK
    log("Installing Android NDK...")
    os.makedirs("/tmp/android-sdk", exist_ok=True)
    
    # Download command-line tools
    url = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    zip_path = "/tmp/android-sdk/cmdline-tools.zip"
    run(f"curl -sL {url} -o {zip_path}", check=True)
    run(f"unzip -q {zip_path} -d /tmp/android-sdk/cmdline-tools-tmp", check=True)
    
    # Set up sdkmanager
    # The zip contains a "cmdline-tools/" prefix directory
    # Move contents into latest/ stripping the prefix
    extracted = "/tmp/android-sdk/cmdline-tools-tmp"
    src_dir = os.path.join(extracted, "cmdline-tools")
    if os.path.exists(src_dir):
        os.makedirs("/tmp/android-sdk/cmdline-tools/latest", exist_ok=True)
        for item in os.listdir(src_dir):
            shutil.move(os.path.join(src_dir, item),
                        f"/tmp/android-sdk/cmdline-tools/latest/{item}")
    
    sdkmanager = "/tmp/android-sdk/cmdline-tools/latest/bin/sdkmanager"
    if not os.path.exists(sdkmanager):
        # Fallback: search for it
        for root, dirs, files in os.walk("/tmp/android-sdk"):
            if "sdkmanager" in files:
                sdkmanager = os.path.join(root, "sdkmanager")
                break
    os.environ["ANDROID_HOME"] = "/tmp/android-sdk"
    
    # Accept licenses and install NDK
    run(f"yes | {sdkmanager} --install 'ndk;{NDK_VERSION}' --sdk_root=/tmp/android-sdk",
        check=True, timeout=300)
    
    ndk_path = f"/tmp/android-sdk/ndk/{NDK_VERSION}"
    if os.path.exists(ndk_path):
        log(f"Installed NDK at: {ndk_path}")
        return ndk_path
    
    raise RuntimeError("Failed to install NDK")


def setup_ndk_env(ndk_path):
    """Set environment variables for NDK cross-compilation."""
    toolchain = os.path.join(ndk_path, "toolchains", "llvm", "prebuilt", "linux-x86_64")
    cc = os.path.join(toolchain, "bin", "aarch64-linux-android21-clang")
    cxx = os.path.join(toolchain, "bin", "aarch64-linux-android21-clang++")
    ar = os.path.join(toolchain, "bin", "llvm-ar")
    ld = os.path.join(toolchain, "bin", "ld.lld")
    
    env = os.environ.copy()
    env.update({
        "CC": cc,
        "CXX": cxx,
        "AR": ar,
        "LD": ld,
        "CFLAGS": "--target=aarch64-linux-android21 -O2 -fPIC",
        "CXXFLAGS": "--target=aarch64-linux-android21 -O2 -fPIC",
        "LDFLAGS": "--target=aarch64-linux-android21",
        "LDSHARED": f"{cc} --target=aarch64-linux-android21 -shared",
        "_PYTHON_HOST_PLATFORM": "aarch64-linux-android",
        "ANDROID_NDK_HOME": ndk_path,
    })
    return env


def create_wheel(pkg_name, version, py_pkg, so_files, python_tag="cp312", abi_tag="cp312"):
    """Create a pip-installable .whl file from cross-compiled .so files."""
    plat = "aarch64-linux-android"
    wheel_name = f"{pkg_name.replace('-', '_')}-{version}-{python_tag}-{abi_tag}-{plat}.whl"
    wheel_dir = f"/tmp/wheels/{wheel_name.replace('.whl', '')}"
    
    # Clean
    if os.path.exists(wheel_dir):
        shutil.rmtree(wheel_dir)
    
    # Package files
    pkg_dir = os.path.join(wheel_dir, py_pkg)
    os.makedirs(pkg_dir, exist_ok=True)
    os.makedirs(os.path.join(wheel_dir, f"{py_pkg}-{version}.dist-info"), exist_ok=True)
    
    # Copy .so files
    for src, dest_name in so_files:
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(pkg_dir, dest_name))
            log(f"  Copied {src} -> {pkg_dir}/{dest_name}")
    
    # Create __init__.py if doesn't exist
    init_py = os.path.join(pkg_dir, "__init__.py")
    if not os.path.exists(init_py):
        with open(init_py, "w") as f:
            f.write(f"# {py_pkg} package\n")
    
    # WHEEL metadata
    with open(os.path.join(wheel_dir, f"{py_pkg}-{version}.dist-info", "WHEEL"), "w") as f:
        f.write(f"""Wheel-Version: 1.0
Generator: cross-compile (manual)
Root-Is-Purelib: false
Tag: {python_tag}-{abi_tag}-{plat}
""")
    
    # METADATA
    with open(os.path.join(wheel_dir, f"{py_pkg}-{version}.dist-info", "METADATA"), "w") as f:
        f.write(f"""Metadata-Version: 2.1
Name: {pkg_name}
Version: {version}
Summary: Cross-compiled for Android ARM64
""")
    
    # RECORD
    record_path = os.path.join(wheel_dir, f"{py_pkg}-{version}.dist-info", "RECORD")
    records = []
    for root, dirs, files in os.walk(wheel_dir):
        for fn in files:
            fp = os.path.join(root, fn)
            rel = os.path.relpath(fp, wheel_dir)
            if fn == "RECORD":
                records.append(f"{rel},,")
                continue
            h = hashlib.sha256()
            with open(fp, "rb") as f:
                h.update(f.read())
            size = os.path.getsize(fp)
            records.append(f"{rel},sha256={h.hexdigest()},{size}")
    
    with open(record_path, "w") as f:
        f.write("\n".join(records) + "\n")
    
    # Create .whl (zip with .whl extension)
    whl_output = os.path.join("/tmp/wheels", wheel_name)
    if os.path.exists(whl_output):
        os.remove(whl_output)
    
    # Zip it up
    orig_dir = os.getcwd()
    os.chdir(wheel_dir)
    run(f"zip -qr {whl_output} .", check=True)
    os.chdir(orig_dir)
    
    shutil.rmtree(wheel_dir)
    log(f"Created wheel: {whl_output} ({os.path.getsize(whl_output)} bytes)")
    return whl_output


def main():
    os.makedirs("/tmp/wheels", exist_ok=True)
    os.makedirs(OFFLINE_PKGS, exist_ok=True)
    
    ndk_path = find_or_install_ndk()
    env = setup_ndk_env(ndk_path)
    
    # Also ensure maturin is installed for Rust packages
    result = run("pip install maturin 2>&1 | tail -3", check=False)
    if result.returncode != 0:
        log("maturin not available via pip, trying alternative...")
        run("pip install maturin --no-binary maturin 2>&1 | tail -3", check=False)
    
    for pkg in PACKAGES:
        pkg_name = pkg["name"]
        version = pkg["version"]
        py_pkg = pkg["py_pkg"]
        pkg_type = pkg["type"]
        
        log(f"\n=== Cross-compiling {pkg_name}=={version} ({pkg_type}) ===")
        
        # Download source tarball — --no-build-isolation 防止 pip 单独装 maturin
        sdist_file = f"/tmp/{pkg_name}-{version}.tar.gz"
        if not os.path.exists(sdist_file):
            run(f"pip download --no-deps --no-build-isolation --no-binary :all: '{pkg_name}=={version}' -d /tmp/ --no-index 2>/dev/null || "
                f"pip download --no-deps --no-build-isolation --no-binary :all: '{pkg_name}=={version}' -d /tmp/ 2>&1 | tail -1",
                check=True, timeout=120)
        
        # Find the downloaded file
        matches = list(glob.glob(f"/tmp/{pkg_name.replace('-', '_')}-{version}.tar.gz"))
        matches += list(glob.glob(f"/tmp/{pkg_name}-{version}.tar.gz"))
        matches += list(glob.glob(f"/tmp/{pkg_name.replace('-', '_')}*-{version}.tar.gz"))
        
        if not matches:
            # Check what was actually downloaded
            log(f"No sdist found for {pkg_name}, checking /tmp/ ...")
            all_files = os.listdir("/tmp/")
            log(f"Files in /tmp/: {[f for f in all_files if pkg_name.replace('-', '_') in f or pkg_name in f]}")
            raise FileNotFoundError(f"Cannot find sdist for {pkg_name}")
        
        sdist_path = matches[0]
        log(f"Source: {sdist_path}")
        
        # Extract
        extract_dir = f"/tmp/build_{pkg_name}_{version}"
        if os.path.exists(extract_dir):
            shutil.rmtree(extract_dir)
        os.makedirs(extract_dir)
        
        run(f"tar xfz '{sdist_path}' -C {extract_dir}", check=True)
        
        # Find the extracted directory
        extracted = os.listdir(extract_dir)
        src_dir = os.path.join(extract_dir, extracted[0])
        log(f"Extracted to: {src_dir}")
        
        if pkg_type == "c":
            # Apply patches
            if "patches" in pkg:
                for patch_cmd, _ in pkg["patches"]:
                    result = run(f"cd '{src_dir}' && {patch_cmd}", check=False)
                    if result.returncode != 0 and _:  # required patch failed
                        raise RuntimeError(f"Required patch failed: {patch_cmd}")
            
            # Clean any prebuilt .so files
            run(f"find '{src_dir}' -name '*.so' -delete", check=False)
            
            # Build with NDK cross-compiler
            result = run(
                f"cd '{src_dir}' && "
                f"CC='{env['CC']}' CXX='{env['CXX']}' "
                f"CFLAGS='{env['CFLAGS']}' CXXFLAGS='{env['CXXFLAGS']}' "
                f"LDFLAGS='{env['LDFLAGS']}' LDSHARED='{env['LDSHARED']}' "
                f"_PYTHON_HOST_PLATFORM=aarch64-linux-android "
                f"python setup.py build_ext --inplace 2>&1",
                check=False, timeout=300)
            
            if result.returncode != 0:
                # Try with build directory instead of --inplace
                log("--inplace failed, trying with build_ext + copy...")
                result = run(
                    f"cd '{src_dir}' && "
                    f"CC='{env['CC']}' CXX='{env['CXX']}' "
                    f"CFLAGS='{env['CFLAGS']}' CXXFLAGS='{env['CXXFLAGS']}' "
                    f"LDFLAGS='{env['LDFLAGS']}' LDSHARED='{env['LDSHARED']}' "
                    f"_PYTHON_HOST_PLATFORM=aarch64-linux-android "
                    f"python setup.py build 2>&1",
                    check=False, timeout=300)
                if result.returncode != 0:
                    log(f"Build FAILED for {pkg_name}")
                    log(result.stdout[-2000:] if hasattr(result, 'stdout') else "")
                    continue
            
            # Find compiled .so files — accept any .so, rename x86_64→aarch64
            so_files = []
            for root, dirs, files in os.walk(src_dir):
                for f in files:
                    if f.endswith(".so") and "libc++" not in f:
                        src = os.path.join(root, f)
                        # Rename to proper platform tag if needed
                        name = f.replace("x86_64-linux-gnu", "aarch64-linux-android")
                        name = name.replace("linux_x86_64", "aarch64-linux-android")
                        so_files.append((src, name))
            
            if not so_files:
                log(f"No .so files found for {pkg_name}, checking...")
                run(f"find '{src_dir}' -name '*.so' 2>/dev/null", check=False)
                continue
            
            log(f"Found .so files: {[s for s,_ in so_files]}")
            
            # Create wheel
            wheel_path = create_wheel(pkg_name, version, py_pkg, so_files)
            
            # Copy to offline_pkgs
            dest = os.path.join(OFFLINE_PKGS, os.path.basename(wheel_path))
            shutil.copy2(wheel_path, dest)
            log(f"Copied to offline_pkgs: {dest}")
            
        elif pkg_type == "rust":
            # Rust package (pydantic-core)
            # Find Python 3.11 exact path (maturin needs the version in the name)
            py_path = subprocess.run(["which", "python3"], capture_output=True, text=True).stdout.strip()
            py_dir = os.path.dirname(py_path) if py_path else ""
            
            # Python 3.11 may be at a versioned path in hostedtoolcache
            for candidate in [
                os.path.join(py_dir, "python3.11"),
                "/opt/hostedtoolcache/Python/3.11.15/x64/bin/python3.11",
                "/opt/hostedtoolcache/Python/3.11.15/x64/bin/python",
            ]:
                if os.path.exists(candidate):
                    # Create versioned symlink if needed
                    target_dir = os.path.dirname(candidate)
                    versioned = os.path.join(target_dir, "python3.11")
                    if not os.path.exists(versioned) and "python3.11" not in candidate:
                        try:
                            os.symlink(candidate, versioned)
                            log(f"Created symlink: {versioned} -> {candidate}")
                        except PermissionError:
                            # Can't write to /opt/, try /tmp/
                            tmp_python = "/tmp/python3.11"
                            if not os.path.exists(tmp_python):
                                os.symlink(candidate, tmp_python)
                                versioned = tmp_python
                                log(f"Created symlink: {versioned}")
                    else:
                        versioned = candidate
                    break
            else:
                versioned = "python3.11"  # Fallback, might work if in PATH
            
            rust_env = env.copy()
            rust_env["CARGO_BUILD_TARGET"] = "aarch64-linux-android"
            rust_env["CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"] = env["CC"]
            
            result = run(
                f"cd '{src_dir}' && "
                f"CARGO_BUILD_TARGET=aarch64-linux-android "
                f"CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER='{env['CC']}' "
                f"maturin build --target aarch64-linux-android "
                f"--interpreter '{versioned}' --release -o /tmp/wheels/ 2>&1",
                check=False, timeout=600)
            
            if result.returncode != 0:
                log(f"maturin build FAILED for {pkg_name}")
                log(result.output if hasattr(result, 'output') else "")
                # Try pip wheel as fallback
                result = run(
                    f"cd '{src_dir}' && "
                    f"CARGO_BUILD_TARGET=aarch64-linux-android "
                    f"CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER='{env['CC']}' "
                    f"pip wheel --no-deps . -w /tmp/wheels/ 2>&1",
                    check=False, timeout=600)
                if result.returncode != 0:
                    log(f"pip wheel also FAILED for {pkg_name}")
                    continue
            
            # Find the generated wheel
            wheels_in_tmp = glob.glob(f"/tmp/wheels/{pkg_name.replace('-', '_')}*.whl")
            if wheels_in_tmp:
                wheel_path = wheels_in_tmp[0]
                # Rename to proper platform
                new_name = wheel_path.replace("linux_x86_64", "aarch64-linux-android")
                if new_name != wheel_path:
                    os.rename(wheel_path, new_name)
                    wheel_path = new_name
                
                dest = os.path.join(OFFLINE_PKGS, os.path.basename(wheel_path))
                shutil.copy2(wheel_path, dest)
                log(f"Copied to offline_pkgs: {dest}")
            else:
                log(f"No wheel generated for {pkg_name}")
    
    # Clean up
    shutil.rmtree("/tmp/wheels", ignore_errors=True)
    
    # List final offline_pkgs
    log("\n=== Final offline_pkgs ===")
    for f in sorted(os.listdir(OFFLINE_PKGS)):
        if any(p["name"] in f for p in PACKAGES):
            log(f"  {f} ({os.path.getsize(os.path.join(OFFLINE_PKGS, f))} bytes)")
    
    log("\nDone!")


if __name__ == "__main__":
    main()
