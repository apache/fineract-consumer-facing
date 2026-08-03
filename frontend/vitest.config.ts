import { defineConfig } from 'vitest/config';

// Force Ionic/Stencil packages through Vite's resolver
export default defineConfig({
  test: {
    server: {
      deps: {
        inline: [/@ionic/, /ionicons/, /@stencil/],
      },
    },
  },
});
