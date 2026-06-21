#!/usr/bin/env python3
"""Fix horoscope-engine.js: QuickJS may not support destructured default params.
Appends a wrapper that ensures language has a fallback value."""

import sys

path = sys.argv[1]

fix = """

// Horoscope language fallback for QuickJS compatibility
(function(){
    var H = HoroscopeJS.Horoscope;
    HoroscopeJS.Horoscope = function(opts) {
        opts = opts || {};
        if (!opts.language) opts.language = 'en';
        return new H(opts);
    };
})();
"""

with open(path, 'a') as f:
    f.write(fix)

print(f"Horoscope language fallback appended to {path}")
