import type { MetadataRoute } from "next"

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Khabar care companion",
    short_name: "Khabar",
    description: "A clinician-led follow-up care concept demo.",
    start_url: "/",
    display: "standalone",
    background_color: "#edf5f4",
    theme_color: "#0f4b4d",
    icons: [{ src: "/favicon.ico", sizes: "any", type: "image/x-icon" }],
  }
}
