import type { Metadata } from "next";
import { FilmProvider } from "@/components/film/film-provider";
import { fontVariables } from "@/components/film/fonts";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

export default function FilmPreviewPage() {
  return (
    <FilmProvider className={fontVariables}>
      <main id="main-content">
        <h1>
          The visit ends.
          <br />
          <em>Care should not.</em>
        </h1>
      </main>
    </FilmProvider>
  );
}
