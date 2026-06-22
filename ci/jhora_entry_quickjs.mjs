/** QuickJS entry for NodeJhora — bundles de440s.bsp via esbuild base64 loader.
 *
 *  Decodes with native Uint8Array.fromBase64() when available (QuickJS-ng),
 *  falling back to pure-JS decoder for older QuickJS without native base64.
 */
import bspBase64 from './de440s.bsp';

// Fast native decode when available, else pure JS
var bspBuffer;
if (typeof Uint8Array !== 'undefined' && typeof Uint8Array.fromBase64 === 'function') {
    bspBuffer = Uint8Array.fromBase64(bspBase64);
} else {
    // Pure-JS base64 → Uint8Array (works everywhere, no atob/Buffer needed)
    var chars = {};
    var alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    for (var i = 0; i < 64; i++) chars[alpha.charAt(i)] = i;
    chars['='] = 0;
    var pad = (bspBase64.charAt(bspBase64.length - 1) === '=' ? (bspBase64.charAt(bspBase64.length - 2) === '=' ? 2 : 1) : 0);
    var outLen = (bspBase64.length >>> 2) * 3 - pad;
    bspBuffer = new Uint8Array(outLen);
    var pos = 0;
    for (var i = 0; i < bspBase64.length; i += 4) {
        var a = chars[bspBase64.charAt(i)] || 0;
        var b = chars[bspBase64.charAt(i + 1)] || 0;
        var c = chars[bspBase64.charAt(i + 2)] || 0;
        var d = chars[bspBase64.charAt(i + 3)] || 0;
        bspBuffer[pos++] = (a << 2) | (b >>> 4);
        if (i + 2 < bspBase64.length && bspBase64.charAt(i + 2) !== '=') bspBuffer[pos++] = ((b & 15) << 4) | (c >>> 2);
        if (i + 3 < bspBase64.length && bspBase64.charAt(i + 3) !== '=') bspBuffer[pos++] = ((c & 3) << 6) | d;
    }
}

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
