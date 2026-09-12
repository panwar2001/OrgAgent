import { reactRouter } from "@react-router/dev/vite";
import { cloudflare } from "@cloudflare/vite-plugin";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [
    // Runs the server code in the Workers runtime locally, so dev matches production.
    cloudflare({ viteEnvironment: { name: "ssr" } }),
    tailwindcss(),
    reactRouter(),
  ],

  resolve: {
    tsconfigPaths: true,
  },

  ssr: {
    noExternal: ["@clerk/react-router"],
  },
});