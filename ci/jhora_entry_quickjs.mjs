/** QuickJS entry for NodeJhora — reads bsp buffer from __nodejhora_bsp global.
 *
 *  Kotlin side injects the 32MB de440s.bsp as a byte[] into
 *  this global before loading this file. Zero encoding overhead.
 */
// bspBuffer is pre-injected by Kotlin as a Uint8Array on globalThis
var bspBuffer = __nodejhora_bsp;

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

// Clean up the global to free the reference
__nodejhora_bsp = null;

// Export luxon DateTime for AI date construction
export { DateTime } from 'luxon';

// Re-export full public API
export * from '@node-jhora/core';
export * from '@node-jhora/analytics';
export * from '@node-jhora/prediction';
