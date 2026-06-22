/** QuickJS entry for NodeJhora — bundles de440s.bsp via base64 loader.
 *
 *  esbuild --loader:.bsp=base64 will encode the bsp file as a base64 string
 *  module. We decode it with a pure-JS function (no atob/Buffer needed).
 */
import bspBase64 from './de440s.bsp';

// Pure-JS base64 → Uint8Array (works in QuickJS, no atob/Buffer/Uint8Array.fromBase64)
function base64ToUint8Array(b64) {
    var chars = {};
    var alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    for (var i = 0; i < 64; i++) chars[alpha.charAt(i)] = i;
    chars['='] = 0;

    var pad = (b64.charAt(b64.length - 1) === '=' ? (b64.charAt(b64.length - 2) === '=' ? 2 : 1) : 0);
    var outLen = (b64.length >>> 2) * 3 - pad;
    var result = new Uint8Array(outLen);
    var pos = 0;

    for (var i = 0; i < b64.length; i += 4) {
        var a = chars[b64.charAt(i)] || 0;
        var b = chars[b64.charAt(i + 1)] || 0;
        var c = chars[b64.charAt(i + 2)] || 0;
        var d = chars[b64.charAt(i + 3)] || 0;
        result[pos++] = (a << 2) | (b >>> 4);
        if (i + 2 < b64.length && b64.charAt(i + 2) !== '=') result[pos++] = ((b & 15) << 4) | (c >>> 2);
        if (i + 3 < b64.length && b64.charAt(i + 3) !== '=') result[pos++] = ((c & 3) << 6) | d;
    }
    return result;
}

var bspBuffer = base64ToUint8Array(bspBase64);

// Polyfill Buffer#toString for SPK header detection
bspBuffer.toString = function (encoding, start, end) {
    if (encoding === 'ascii') {
        var result = '';
        for (var i = start; i < end; i++) result += String.fromCharCode(this[i]);
        return result.trim().replace(/\0/g, '');
    }
    return '';
};

// Wire up the ephemeris engine
import { EphemerisEngine } from '@node-jhora/core';
EphemerisEngine.getInstance().loadBspBuffer(bspBuffer);

// Export luxon DateTime for AI to construct dates (QuickJS no Intl, use fromISO with numeric offset)
export { DateTime } from 'luxon';

// Re-export the full public API
export * from '@node-jhora/core';
export * from '@node-jhora/analytics';
export * from '@node-jhora/prediction';
