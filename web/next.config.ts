import type { NextConfig } from "next";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const nextConfig: NextConfig = {
  turbopack: { root: dirname(fileURLToPath(import.meta.url)) },
  // The film was previewed here before it became the landing page; old links land on it.
  async redirects() {
    return [{ source: "/preview/film", destination: "/", permanent: true }];
  },
};

export default nextConfig;
