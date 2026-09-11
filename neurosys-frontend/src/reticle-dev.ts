// Dev-only. Imported automatically by @reticlehq/vite-plugin, so you do not need to import it.
// Self-guards on import.meta.env.DEV, so it is a no-op in a production build.
import { registerCapabilities, connect } from '@reticlehq/react';

if (import.meta.env.DEV) {
  try {
    connect();
  } catch (e) {
    console.warn('[Reticle] Connect error:', e);
  }

  registerCapabilities({
    testids: [], // none found; add data-testid to your key elements
    signals: [], // names you pass to reticle.signal()
    stores: [], // the keys you registered above
  });
}
