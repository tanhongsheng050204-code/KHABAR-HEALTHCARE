import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

// Replaced act by act in the following tasks; the heading is the hero's.
export default function FilmPreviewPage() {
  return (
    <main id="main-content">
      <h1>
        The visit ends.
        <br />
        <em>Care should not.</em>
      </h1>
    </main>
  );
}
