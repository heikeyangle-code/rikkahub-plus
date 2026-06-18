#!/usr/bin/env python3
"""
Cross-compile Python native packages for Android ARM64 using official
Android Python builds from python.org.

Usage:
  python3 ci/cross_compile_wheels.py
"""
import os, sys, subprocess, glob, shutil, hashlib, io, tarfile
from pathlib import Path

PY_VER = "3.14"
PY_TAG = "cp314"
ABI_TAG = "cp314"
PLAT = "android_21_arm64_v8a"
ANDROID_PYTHON_URL = (
    f"https://www.python.org/ftp/python/{PY_VER}.3/"
    f"python-{PY_VER}.3-aarch64-linux-android.tar.gz"
)

WORKSPACE = os.environ.get("GITHUB_WORKSPACE", os.getcwd())
OFFLINE_PKGS = os.path.join(WORKSPACE, "app", "offline_pkgs")
NDK_VERSION = "27.0.12077973"


def log(msg):
    print(f"[cross-compile] {msg}", flush=True)


def run(cmd, **kwargs):
    log(f"Running: {cmd[:200] if isinstance(cmd, str) else ' '.join(cmd)[:200]}")
    return subprocess.run(cmd, shell=isinstance(cmd, str), **kwargs)


def download_android_python():
    """Download the official Android Python build from python.org."""
    dest = "/tmp/android-python"
    if os.path.exists(os.path.join(dest, "prefix", "lib", f"libpython{PY_VER}.so")):
        log(f"Android Python already at {dest}")
        return dest

    log(f"Downloading Android Python {PY_VER} from python.org...")
    import urllib.request
    req = urllib.request.Request(ANDROID_PYTHON_URL, headers={"Accept": "application/octet-stream"})
    resp = urllib.request.urlopen(req, timeout=120)
    data = resp.read()
    log(f"Downloaded {len(data)} bytes")

    tf = tarfile.open(fileobj=io.BytesIO(data))
    tf.extractall(dest)
    log(f"Extracted to {dest}")
    return dest


def find_or_install_ndk():
    """Find existing NDK or install it."""
    ndk_dirs = [
        os.environ.get("NDK_DIR", ""),
        os.path.join(os.environ.get("ANDROID_HOME", ""), "ndk", NDK_VERSION),
        os.path.join(os.environ.get("ANDROID_HOME", ""), "ndk-bundle"),
        os.path.join(Path.home(), "Android", "Sdk", "ndk", NDK_VERSION),
    ]
    for d in ndk_dirs:
        if d and os.path.exists(os.path.join(d, "toolchains", "llvm", "prebuilt", "linux-x86_64")):
            log(f"Found NDK at: {d}")
            return d

    log("Installing Android NDK...")
    os.makedirs("/tmp/android-sdk", exist_ok=True)
    url = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    run(f"curl -sL {url} -o /tmp/android-sdk/cmdline-tools.zip", check=True)
    run(f"unzip -q /tmp/android-sdk/cmdline-tools.zip -d /tmp/android-sdk/cmdline-tools-tmp", check=True)
    src = "/tmp/android-sdk/cmdline-tools-tmp/cmdline-tools"
    if os.path.exists(src):
        os.makedirs("/tmp/android-sdk/cmdline-tools/latest", exist_ok=True)
        for item in os.listdir(src):
            shutil.move(os.path.join(src, item), f"/tmp/android-sdk/cmdline-tools/latest/{item}")
    sdkmanager = "/tmp/android-sdk/cmdline-tools/latest/bin/sdkmanager"
    os.environ["ANDROID_HOME"] = "/tmp/android-sdk"
    run(f"yes | {sdkmanager} --install 'ndk;{NDK_VERSION}' --sdk_root=/tmp/android-sdk",
        check=True, timeout=300)
    ndk_path = f"/tmp/android-sdk/ndk/{NDK_VERSION}"
    if os.path.exists(ndk_path):
        log(f"Installed NDK at: {ndk_path}")
        return ndk_path
    raise RuntimeError("Failed to install NDK")


def create_compiler_wrapper(cc_path, android_python_root):
    """Create a compiler/linker wrapper that strips host Python paths.
    
    The host Python's sysconfig data causes setuptools to add:
      - -I/opt/hostedtoolcache/Python/.../x64/include/python3.14  (host headers)
      - -L/opt/hostedtoolcache/Python/.../x64/lib                (host libs)
      - -Wl,--rpath=/opt/hostedtoolcache/Python/.../x64/lib      (host rpath)
    
    These pollute the cross-compiled .so with x86_64 references and
    broken RPATHs that don't exist on Android. This wrapper filters
    them out.
    """
    wrapper_path = "/tmp/compiler-wrapper.sh"
    with open(wrapper_path, "w") as f:
        f.write(f"""#!/bin/bash
# Cross-compiler wrapper: strips host Python paths from build
ARGS=()
for arg in "$@"; do
    case "$arg" in
        *hostedtoolcache*) continue ;;
        *--rpath=*)        continue ;;
        *Python*ROOT*)     continue ;;
    esac
    ARGS+=("$arg")
done
exec {cc_path} "${{ARGS[@]}}"
""")
    os.chmod(wrapper_path, 0o755)
    log(f"Created compiler wrapper: {wrapper_path}")
    # Also create a CXX wrapper
    cxx_path = cc_path.replace("aarch64-linux-android21-clang", "aarch64-linux-android21-clang++")
    wrapper_cxx = "/tmp/compiler-wrapper++.sh"
    with open(wrapper_cxx, "w") as f:
        f.write(f"""#!/bin/bash
ARGS=()
for arg in "$@"; do
    case "$arg" in
        *hostedtoolcache*) continue ;;
        *--rpath=*)        continue ;;
        *Python*ROOT*)     continue ;;
    esac
    ARGS+=("$arg")
done
exec {cxx_path} "${{ARGS[@]}}"
""")
    os.chmod(wrapper_cxx, 0o755)
    return wrapper_path, wrapper_cxx


def setup_env(ndk_path, android_python_root):
    """Set up cross-compilation environment using official Android Python."""
    toolchain = os.path.join(ndk_path, "toolchains", "llvm", "prebuilt", "linux-x86_64")
    cc = os.path.join(toolchain, "bin", "aarch64-linux-android21-clang")
    cxx = os.path.join(toolchain, "bin", "aarch64-linux-android21-clang++")
    py_prefix = os.path.join(android_python_root, "prefix")
    py_include = os.path.join(py_prefix, "include", f"python{PY_VER}")
    py_lib = os.path.join(py_prefix, "lib")
    ar = os.path.join(toolchain, "bin", "llvm-ar")

    # Create compiler wrappers that strip host Python paths
    cc_wrapper, cxx_wrapper = create_compiler_wrapper(cc, android_python_root)

    env = os.environ.copy()
    
    # CRITICAL: Unset host Python root dirs that cause setuptools to add host paths
    for var in list(env.keys()):
        if "PYTHON" in var.upper() and ("ROOT" in var.upper() or "DIR" in var.upper()):
            if var not in ("PYTHON_HOME",):  # keep PYTHON_HOME if set
                env.pop(var, None)
    
    # Clear LD_LIBRARY_PATH — host Python libs would pollute the link
    env.pop("LD_LIBRARY_PATH", None)
    env.pop("LD_RUN_PATH", None)
    env.pop("PKG_CONFIG_PATH", None)

    env.update({
        "CC": cc_wrapper,
        "CXX": cxx_wrapper,
        "AR": ar,
        "CFLAGS": f"--target=aarch64-linux-android21 -O2 -fPIC -I{py_include}",
        "CXXFLAGS": f"--target=aarch64-linux-android21 -O2 -fPIC -I{py_include}",
        "LDFLAGS": f"--target=aarch64-linux-android21 -L{py_lib}",
        "LDSHARED": f"{cxx_wrapper} --target=aarch64-linux-android21 -shared -L{py_lib}",
        "LIBS": f"-lpython{PY_VER}",
        "_PYTHON_HOST_PLATFORM": "aarch64-linux-android",
        "ANDROID_NDK_HOME": ndk_path,
        # PyO3/maturin cross-compilation
        "PYO3_CROSS_LIB_DIR": py_lib,
        "PYO3_CROSS_PYTHON_VERSION": PY_VER,
        "PYO3_CROSS_INCLUDE_DIR": py_include,
    })
    log(f"Android Python include: {py_include}")
    log(f"Android Python lib: {py_lib}")
    return env


def create_wheel(pkg_name, version, py_pkg, so_files, py_files=None, init_content=None):
    """Create a .whl with android_21_arm64_v8a platform tag.
    
    Args:
        py_files: list of (src_path, dest_filename) tuples for .py files to include in the package
        init_content: if set, use this as __init__.py content instead of default stub
    """
    wheel_name = f"{pkg_name.replace('-', '_')}-{version}-{PY_TAG}-{ABI_TAG}-{PLAT}.whl"
    wheel_dir = f"/tmp/wheels/{wheel_name.replace('.whl', '')}"
    if os.path.exists(wheel_dir):
        shutil.rmtree(wheel_dir)

    pkg_dir = os.path.join(wheel_dir, py_pkg)
    os.makedirs(pkg_dir, exist_ok=True)
    dist_info = f"{pkg_name}-{version}.dist-info"
    os.makedirs(os.path.join(wheel_dir, dist_info), exist_ok=True)

    for src, dest_name in so_files:
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(pkg_dir, dest_name))
            log(f"  Copied {dest_name}")

    # Copy extra .py files (e.g. SWIG wrapper) into the package dir
    if py_files:
        for src, dest_name in py_files:
            if os.path.exists(src):
                shutil.copy2(src, os.path.join(pkg_dir, dest_name))
                log(f"  Copied py {dest_name}")

    init_py = os.path.join(pkg_dir, "__init__.py")
    if init_content:
        with open(init_py, "w") as f:
            f.write(init_content)
    elif not os.path.exists(init_py):
        with open(init_py, "w") as f:
            f.write(f"# {py_pkg} package\n")

    with open(os.path.join(wheel_dir, dist_info, "WHEEL"), "w") as f:
        f.write(f"Wheel-Version: 1.0\nGenerator: cross-compile\n"
                f"Root-Is-Purelib: false\nTag: {PY_TAG}-{ABI_TAG}-{PLAT}\n")

    with open(os.path.join(wheel_dir, dist_info, "METADATA"), "w") as f:
        f.write(f"Metadata-Version: 2.1\nName: {pkg_name}\n"
                f"Version: {version}\nSummary: Cross-compiled for Android ARM64\n")

    records = []
    for root, dirs, files in os.walk(wheel_dir):
        for fn in files:
            fp = os.path.join(root, fn)
            rel = os.path.relpath(fp, wheel_dir)
            if fn == "RECORD":
                records.append(f"{rel},")
                continue
            h = hashlib.sha256()
            with open(fp, "rb") as f:
                h.update(f.read())
            records.append(f"{rel},sha256={h.hexdigest()},{os.path.getsize(fp)}")

    with open(os.path.join(wheel_dir, dist_info, "RECORD"), "w") as f:
        f.write("\n".join(records) + "\n")

    whl_output = os.path.join("/tmp/wheels", wheel_name)
    orig = os.getcwd()
    os.chdir(wheel_dir)
    run(f"zip -qr {whl_output} .", check=True)
    os.chdir(orig)
    shutil.rmtree(wheel_dir)
    log(f"Created wheel: {whl_output} ({os.path.getsize(whl_output)} bytes)")
    return whl_output


def compile_c_package(pkg, env):
    """Cross-compile a C extension package and output .whl."""
    pkg_name = pkg["name"]
    version = pkg["version"]
    py_pkg = pkg["py_pkg"]

    sdist_file = f"/tmp/{pkg_name}-{version}.tar.gz"
    if not os.path.exists(sdist_file):
        iso_flag = "" if pkg.get("build_isolation") else "--no-build-isolation"
        to = 600 if pkg.get("build_isolation") else 120
        run(f"pip download --no-deps {iso_flag} --no-binary :all: "
            f"'{pkg_name}=={version}' -d /tmp/ 2>&1 | tail -1", check=True, timeout=to)

    matches = (glob.glob(f"/tmp/{pkg_name.replace('-', '_')}-{version}.tar.gz")
               + glob.glob(f"/tmp/{pkg_name}-{version}.tar.gz"))
    if not matches:
        raise FileNotFoundError(f"Cannot find sdist for {pkg_name}")
    sdist_path = matches[0]
    log(f"Source: {sdist_path}")

    extract_dir = f"/tmp/build_{pkg_name}_{version}"
    if os.path.exists(extract_dir):
        shutil.rmtree(extract_dir)
    os.makedirs(extract_dir)
    run(f"tar xfz '{sdist_path}' -C {extract_dir}", check=True)
    src_dir = os.path.join(extract_dir, os.listdir(extract_dir)[0])

    # Apply patches
    for patch_cmd, required in pkg.get("patches", []):
        result = run(f"cd '{src_dir}' && {patch_cmd}", check=False)
        if result.returncode != 0 and required:
            raise RuntimeError(f"Required patch failed: {patch_cmd}")

    # Remove prebuilt .so files
    run(f"find '{src_dir}' -name '*.so' -delete", check=False)

    # Cross-compile with NDK + official Android Python headers/lib
    # Use LIBS for -lpython3.14 instead of LDFLAGS to avoid conflicts
    build_cmd = (
        f"cd '{src_dir}' && "
        f"CC='{env['CC']}' CXX='{env['CXX']}' "
        f"CFLAGS='{env['CFLAGS']}' CXXFLAGS='{env['CXXFLAGS']}' "
        f"LDFLAGS='{env['LDFLAGS']}' LDSHARED='{env['LDSHARED']}' "
        f"LIBS='{env['LIBS']}' "
        f"_PYTHON_HOST_PLATFORM=aarch64-linux-android "
        f"python setup.py build_ext --inplace 2>&1"
    )
    result = run(build_cmd, check=False, timeout=300)
    if result.returncode != 0:
        log("--inplace failed, trying build + copy...")
        result = run(
            f"cd '{src_dir}' && "
            f"CC='{env['CC']}' CXX='{env['CXX']}' "
            f"CFLAGS='{env['CFLAGS']}' CXXFLAGS='{env['CXXFLAGS']}' "
            f"LDFLAGS='{env['LDFLAGS']}' LDSHARED='{env['LDSHARED']}' "
            f"LIBS='{env['LIBS']}' "
            f"_PYTHON_HOST_PLATFORM=aarch64-linux-android "
            f"python setup.py build 2>&1",
            check=False, timeout=300)
        if result.returncode != 0:
            log(f"Build FAILED for {pkg_name}")
            return False

    # Find .so files and package as .whl
    so_files = []
    for root, dirs, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".so") and "libc++" not in f and "chaquopy" not in f:
                src = os.path.join(root, f)
                name = f.replace("x86_64-linux-gnu", "aarch64-linux-android")
                so_files.append((src, name))

    if not so_files:
        log(f"No .so found for {pkg_name}")
        return False

    log(f"Found .so: {[s for s, _ in so_files]}")

    # Verify .so is ARM64 architecture
    for src, _ in so_files:
        result = run(f"file '{src}'", check=False, capture_output=True)
        output = result.stdout.decode() if hasattr(result, 'stdout') and result.stdout else ""
        if "aarch64" in output or "ARM" in output or "ARM64" in output:
            log(f"  ✅ {os.path.basename(src)}: ARM64")
        else:
            log(f"  ⚠️  {os.path.basename(src)}: {output[:100]}")

    # Resolve extra .py files relative to source dir
    py_file_list = None
    if pkg.get("extra_py_files"):
        py_file_list = [(os.path.join(src_dir, f), f) for f in pkg["extra_py_files"]]

    wheel_path = create_wheel(pkg_name, version, py_pkg, so_files,
                              py_files=py_file_list,
                              init_content=pkg.get("init_content"))
    dest = os.path.join(OFFLINE_PKGS, os.path.basename(wheel_path))
    shutil.copy2(wheel_path, dest)
    log(f"ARM64 .whl: {dest}")
    return True


def compile_rust_package(pkg, env):
    """Cross-compile a Rust+PyO3 package using the official Android Python."""
    pkg_name = pkg["name"]
    version = pkg["version"]
    py_pkg = pkg["py_pkg"]

    sdist_file = f"/tmp/{pkg_name}-{version}.tar.gz"
    if not os.path.exists(sdist_file):
        run(f"pip download --no-deps --no-build-isolation --no-binary :all: "
            f"'{pkg_name}=={version}' -d /tmp/ 2>&1 | tail -1", check=True, timeout=120)

    matches = (glob.glob(f"/tmp/{pkg_name.replace('-', '_')}-{version}.tar.gz")
               + glob.glob(f"/tmp/{pkg_name}-{version}.tar.gz"))
    if not matches:
        raise FileNotFoundError(f"Cannot find sdist for {pkg_name}")
    sdist_path = matches[0]

    extract_dir = f"/tmp/build_{pkg_name}_{version}"
    if os.path.exists(extract_dir):
        shutil.rmtree(extract_dir)
    os.makedirs(extract_dir)
    run(f"tar xfz '{sdist_path}' -C {extract_dir}", check=True)
    src_dir = os.path.join(extract_dir, os.listdir(extract_dir)[0])

    # Ensure maturin is installed
    run("pip install maturin 2>&1 | tail -3", check=False)

    # Try maturin first
    log("Trying maturin with PYO3_CROSS_LIB_DIR...")
    result = run(
        f"cd '{src_dir}' && "
        f"CARGO_BUILD_TARGET=aarch64-linux-android "
        f"CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER='{env['CC']}' "
        f"PYO3_CROSS_LIB_DIR='{env['PYO3_CROSS_LIB_DIR']}' "
        f"PYO3_CROSS_PYTHON_VERSION='{env['PYO3_CROSS_PYTHON_VERSION']}' "
        f"maturin build --target aarch64-linux-android "
        f"--release -o /tmp/wheels/ 2>&1",
        check=False, timeout=600)

    if result.returncode == 0:
        wheels = glob.glob(f"/tmp/wheels/{pkg_name.replace('-', '_')}*.whl")
        if wheels:
            wheel_path = wheels[0]
            new_name = wheel_path.replace("linux_x86_64", PLAT)
            if new_name != wheel_path:
                os.rename(wheel_path, new_name)
                wheel_path = new_name
            dest = os.path.join(OFFLINE_PKGS, os.path.basename(wheel_path))
            shutil.copy2(wheel_path, dest)
            log(f"Rust .whl: {dest}")
            return True

    # Fallback: cargo build directly with PYO3_CROSS_LIB_DIR
    log(f"maturin failed. Trying cargo build directly...")
    result = run(
        f"cd '{src_dir}' && "
        f"CARGO_BUILD_TARGET=aarch64-linux-android "
        f"CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER='{env['CC']}' "
        f"PYO3_CROSS_LIB_DIR='{env['PYO3_CROSS_LIB_DIR']}' "
        f"PYO3_CROSS_PYTHON_VERSION='{env['PYO3_CROSS_PYTHON_VERSION']}' "
        f"cargo build --release --target aarch64-linux-android 2>&1",
        check=False, timeout=600)

    if result.returncode != 0:
        log(f"cargo build also FAILED for {pkg_name}")
        return False

    so_candidates = glob.glob(f"{src_dir}/target/aarch64-linux-android/release/lib*.so")
    if not so_candidates:
        log(f"No .so after cargo build for {pkg_name}")
        return False

    so_files = []
    for so_path in so_candidates:
        pyname = os.path.basename(so_path).replace('lib_', '').replace('lib', '')
        pyname = pyname.replace('.so', f'.{PY_TAG}-aarch64-linux-android.so')
        so_files.append((so_path, pyname))

    log(f"Cargo produced: {[s for s, _ in so_files]}")
    wheel_path = create_wheel(pkg_name, version, py_pkg, so_files)
    dest = os.path.join(OFFLINE_PKGS, os.path.basename(wheel_path))
    shutil.copy2(wheel_path, dest)
    log(f"ARM64 .whl from cargo: {dest}")
    return True


def main():
    os.makedirs("/tmp/wheels", exist_ok=True)
    os.makedirs(OFFLINE_PKGS, exist_ok=True)

    ndk_path = find_or_install_ndk()
    android_python = download_android_python()
    env = setup_env(ndk_path, android_python)

    # Install Rust target & maturin for cross-compilation
    run("rustup target add aarch64-linux-android 2>&1 | tail -1", check=False)
    run("pip install maturin 2>&1 | tail -3", check=False)

    PACKAGES = [
        {"name": "sxtwl", "version": "2.0.6", "py_pkg": "sxtwl", "type": "c",
         "extra_py_files": ["sxtwl.py"],
         "init_content": "from .sxtwl import *\n",
         "patches": [
             ("sed -i 's/from distutils import ccompiler//' setup.py", False),
             ("sed -i 's/if ccompiler.get_default_compiler() == \"msvc\":/"
              "if platform.system() == \"Windows\":/' setup.py", False),
         ]},
        {"name": "pyswisseph", "version": "2.10.3.2", "py_pkg": "swisseph", "type": "c",
         "patches": [
             ("sed -i 's/swe_detection = True/swe_detection = False/' setup.py", True),
             ("sed -i 's/sqlite3_detection = True/sqlite3_detection = False/' setup.py", True),
         ]},
        {"name": "pydantic-core", "version": "2.46.4", "py_pkg": "pydantic_core", "type": "rust"},
        {"name": "ephem", "version": "4.2.1", "py_pkg": "ephem", "type": "c",
         "patches": []},
    ]

    for pkg in PACKAGES:
        log(f"\n=== Cross-compiling {pkg['name']}=={pkg['version']} ({pkg['type']}) ===")
        ok = compile_c_package(pkg, env) if pkg["type"] == "c" else compile_rust_package(pkg, env)
        log(f"  {'✅ done' if ok else '❌ failed'}")

    shutil.rmtree("/tmp/wheels", ignore_errors=True)
    log("\n=== offline_pkgs ===")
    for f in sorted(os.listdir(OFFLINE_PKGS)):
        for p in PACKAGES:
            if p["name"] in f:
                log(f"  {f} ({os.path.getsize(os.path.join(OFFLINE_PKGS, f))} bytes)")
    log("\nDone!")


if __name__ == "__main__":
    main()
