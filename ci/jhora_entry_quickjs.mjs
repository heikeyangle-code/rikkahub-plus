/** Intl polyfill — QuickJS lacks the ECMA-402 Intl API that luxon needs.
 *
 *  luxon's systemLocale() calls new Intl.DateTimeFormat().resolvedOptions().locale
 *  during DateTime construction. Without this, any luxon DateTime instantiation
 *  throws "'Intl' is not defined".
 *
 *  This lightweight polyfill covers only what luxon actually uses. It does NOT
 *  load ICU data — locale-aware formatting (toLocaleString, etc.) returns
 *  defaults ('en-US'). For astrometric calculations this is irrelevant; all
 *  planetary computations use pure number math, never locale-dependent formatting.
 */
if (typeof Intl === 'undefined') {
  var Intl$ = {
    DateTimeFormat: function (locale, opts) {
      return {
        resolvedOptions: function () { return { locale: locale || 'en-US', timeZone: 'UTC' }; },
        format: function (d) { return d ? d.toISOString ? d.toISOString() : String(d) : ''; },
        formatToParts: function (d) { return [{ type: 'year', value: String(d.getFullYear?.() || 0) }]; }
      };
    },
    NumberFormat: function (locale, opts) {
      return {
        resolvedOptions: function () { return { locale: locale || 'en-US' }; },
        format: function (n) { return String(n); }
      };
    },
    Locale: function (l) { return { locale: l, language: l ? l.split('-')[0] : 'en' }; },
    supportedLocalesOf: function () { return []; },
    Collator: function () { return { compare: function (a, b) { return a < b ? -1 : a > b ? 1 : 0; } }; }
  };
  // Assign to globalThis so bundled libraries (luxon) can find it via global scope
  globalThis.Intl = Intl$;
}

/** QuickJS entry for NodeJhora — reads bsp buffer from __nodejhora_bsp global.
 *
 *  Kotlin side injects the 32MB de440s.bsp as a byte[] into
 *  this global before loading this file. Zero encoding overhead.
 */
// bspBuffer is pre-injected by Kotlin — handle both ArrayBuffer and Uint8Array
var bspBuffer = __nodejhora_bsp;
if (bspBuffer instanceof ArrayBuffer) {
    bspBuffer = new Uint8Array(bspBuffer);
}
__nodejhora_bsp = null;  // release global ref

// Polyfill Buffer#toString for SPK header detection (spk.js line 66)
bspBuffer.toString = function (encoding, start, end) {
    if (encoding === 'ascii') {
        var result = '';
        for (var i = start; i < end; i++) result += String.fromCharCode(this[i]);
        return result.trim().replace(/\0/g, '');
    }
    return '';
};

// Wire up ephemeris engine
import { EphemerisEngine } from '@node-jhora/core';
EphemerisEngine.getInstance().loadBspBuffer(bspBuffer);

// Export luxon DateTime for AI date construction
export { DateTime } from 'luxon';

// Re-export full public API
export * from '@node-jhora/core';
export * from '@node-jhora/analytics';
export * from '@node-jhora/prediction';
