import type { Metadata } from "next";
import Link from "next/link";
import { Brand } from "@/components/brand";
import { FilmProvider } from "@/components/film/film-provider";
import { fontVariables } from "@/components/film/fonts";
import { Act0Hero } from "@/components/film/acts/act0-hero";
import styles from "@/components/film/film.module.css";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

export default function FilmPreviewPage() {
  return (
    <FilmProvider className={fontVariables}>
      <a className={styles.skipLink} href="#act3">Skip to the thirty days at home</a>
      <header className={styles.header}>
        <Brand />
        <Link className="button-secondary" href="/login">Sign in</Link>
      </header>
      <main id="main-content">
        <Act0Hero />
      </main>
    </FilmProvider>
  );
}
