/** QuickJS entry for NodeJhora — bundles de440s.bsp via esbuild binary loader.
 *
 *  esbuild --loader:.bsp=binary embeds the file via __toBinaryNode(),
 *  which uses the native Uint8Array.fromBase64() in QuickJS-ng (fast).
 */
import bspBuffer from './de440s.bsp';

// Polyfill Buffer#toString for SPK header detection (line 66 in spk.js)
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
