#!/usr/bin/env python3
"""Fix build.yml: unified engine install fails because Chaquopy proxy
can't reach GitHub. Download source, pack locally, install from offline."""
import sys, re

path = sys.argv[1]
with open(path) as f:
    content = f.read()

old = """          # arcanite-unified: 统一塔罗引擎 (arcanite+Waite+TarotKit, seed条件分支, Lenormand, DrawnCard proxy 已内置)
          pip install git+https://github.com/heikeyangle-code/arcanite-unified.git 2>&1"""

new = """          # arcanite-unified: 统一塔罗引擎 (arcanite+Waite+TarotKit, seed条件分支, Lenormand, DrawnCard proxy 已内置)
          cd /tmp/pypi-offline
          curl -sL https://github.com/heikeyangle-code/arcanite-unified/archive/master.tar.gz -o arcanite-unified.tar.gz 2>&1
          mkdir -p /tmp/arcanite_unified_build
          cd /tmp/arcanite_unified_build
          tar xzf /tmp/pypi-offline/arcanite-unified.tar.gz --strip=1
          pip install . --no-deps 2>&1
          dir=/tmp/arcanite_unified_build"""

content = content.replace(old, new)

with open(path, 'w') as f:
    f.write(content)

print('build.yml fixed: download from GitHub + local install')
