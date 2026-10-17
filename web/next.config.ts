import type { NextConfig } from "next";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const nextConfig: NextConfig = {
  turbopack: { root: dirname(fileURLToPath(import.meta.url)) },
  // The film was previewed here, then was the landing page; it now lives at /story.
  async redirects() {
    return [{ source: "/preview/film", destination: "/story", permanent: true }];
  },
};

export default nextConfig;
