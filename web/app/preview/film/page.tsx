import type { Metadata } from "next";
import Link from "next/link";
import { Brand } from "@/components/brand";
import { FilmProvider } from "@/components/film/film-provider";
import { fontVariables } from "@/components/film/fonts";
import { Act0Hero } from "@/components/film/acts/act0-hero";
import { Act1Paper } from "@/components/film/acts/act1-paper";
import { Act2Visit } from "@/components/film/acts/act2-visit";
import { Act3ThirtyDays } from "@/components/film/acts/act3-thirty-days";
import { Act4Daughter } from "@/components/film/acts/act4-daughter";
import { Act5Town } from "@/components/film/acts/act5-town";
import { Act6Trust } from "@/components/film/acts/act6-trust";
import styles from "@/components/film/film.module.css";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

export default function FilmPreviewPage() {
  return (
    <FilmProvider className={fontVariables}>
      <a className={styles.skipLink} href="#act3">
        Skip to the thirty days at home
      </a>
      <header className={styles.header}>
        <Brand />
        <Link className="button-secondary" href="/login">
          Sign in
        </Link>
      </header>
      <main id="main-content">
        <Act0Hero />
        <Act1Paper />
        <Act2Visit />
        <Act3ThirtyDays />
        <Act4Daughter />
        <Act5Town />
        <Act6Trust />
      </main>
      <footer className={styles.footer}>
        <Brand compact />
        <p>Concept prototype using fictional patient data.</p>
      </footer>
    </FilmProvider>
  );
}
