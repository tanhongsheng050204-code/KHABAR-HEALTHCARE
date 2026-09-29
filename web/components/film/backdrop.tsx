import { BACKDROPS, type ActId } from "./backdrops";
import styles from "./film.module.css";

/** An act's painted background: art-directed AI art when present, otherwise its placeholder gradient. */
export function Backdrop({
  act,
  priority = false,
}: {
  act: ActId;
  priority?: boolean;
}) {
  const art = BACKDROPS[act];
  return (
    <div
      className={styles.backdrop}
      data-layer="backdrop"
      data-act={act}
      aria-hidden="true"
    >
      {art && priority ? (
        // The first screen's art is found before the stylesheet asks for it; one per screen shape, so a
        // phone never fetches the desktop image. (React hoists these links into <head>.)
        <>
          <link
            rel="preload"
            as="image"
            type="image/avif"
            href={`/film/${act}-mobile.avif`}
            media="(max-width: 760px)"
            fetchPriority="high"
          />
          <link
            rel="preload"
            as="image"
            type="image/avif"
            href={`/film/${act}-desktop.avif`}
            media="(min-width: 761px)"
            fetchPriority="high"
          />
        </>
      ) : null}
      {art ? (
        <picture>
          <source
            media="(max-width: 760px)"
            type="image/avif"
            srcSet={`/film/${act}-mobile.avif`}
          />
          <source
            media="(max-width: 760px)"
            type="image/webp"
            srcSet={`/film/${act}-mobile.webp`}
          />
          <source type="image/avif" srcSet={`/film/${act}-desktop.avif`} />
          <img
            src={`/film/${act}-desktop.webp`}
            alt=""
            width={art.width}
            height={art.height}
            loading={priority ? "eager" : "lazy"}
            fetchPriority={priority ? "high" : "auto"}
            decoding="async"
          />
        </picture>
      ) : null}
    </div>
  );
}
