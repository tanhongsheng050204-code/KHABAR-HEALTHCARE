import type { ReactNode } from "react";
import { htmlLang, type Lang } from "../messages";
import type { ThreadState } from "../thread";
import styles from "../film.module.css";

/** A phone showing a Khabar conversation; the words are real text, so screen readers read them. */
export function Phone({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className={styles.phone} role="group" aria-label={label}>
      <div className={styles.phoneTop} aria-hidden="true">
        <span className={styles.phoneAvatar}>K</span> Khabar
      </div>
      <div className={styles.phoneScreen}>{children}</div>
    </div>
  );
}

export function Bubble({
  from,
  lang,
  tone,
  isNew = false,
  className,
  children,
}: {
  from: "khabar" | "aminah";
  lang: Lang;
  tone?: ThreadState;
  isNew?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <p
      className={`${styles.bubble} ${className ?? ""}`}
      data-from={from}
      data-tone={tone}
      data-new={isNew || undefined}
      lang={htmlLang(lang)}
    >
      <span className={styles.srOnly} lang="en">
        {from === "khabar" ? "Khabar says: " : "Aminah replies: "}
      </span>
      {children}
    </p>
  );
}
