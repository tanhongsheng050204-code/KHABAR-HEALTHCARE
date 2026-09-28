import styles from "../film.module.css";

/** Mak Cik Aminah, a fictional patient: flat shapes, one outline weight, parts named for animation. */
export function Aminah() {
  return (
    <svg className={styles.aminah} viewBox="180 240 260 310" aria-hidden="true" focusable="false">
      <g data-part="body">
        <path d="M200 540 C 205 420, 395 420, 400 540 Z" fill="#7aa892" />
        <path d="M232 470 q68 30 136 0 l6 70 h-148z" fill="#5e8f78" />
        <path d="M240 330 C 240 250, 360 250, 360 330 L 372 440 C 330 470, 270 470, 228 440 Z" fill="#2f6b4f" />
        <ellipse cx="300" cy="330" rx="44" ry="50" fill="#a86f4c" />
        <path d="M256 312 C 262 262, 338 262, 344 312 C 330 290, 270 290, 256 312z" fill="#2f6b4f" />
        <ellipse data-part="eye" cx="284" cy="334" rx="4" ry="5" fill="#2b1d16" />
        <ellipse data-part="eye" cx="316" cy="334" rx="4" ry="5" fill="#2b1d16" />
        <path d="M288 356 q12 9 24 0" fill="none" stroke="#2b1d16" strokeWidth="3" strokeLinecap="round" />
        <circle cx="274" cy="350" r="6" fill="#d98a6a" opacity=".6" />
        <circle cx="326" cy="350" r="6" fill="#d98a6a" opacity=".6" />
        <path d="M236 430 C 214 440, 224 470, 250 462" fill="none" stroke="#5e8f78" strokeWidth="22" strokeLinecap="round" />
        <g data-part="phone">
          <rect x="238" y="400" width="34" height="56" rx="7" fill="#3b2a20" />
          <rect x="242" y="406" width="26" height="40" rx="3" fill="#bfe3cf" />
        </g>
        <path d="M362 420 C 380 440, 372 470, 352 466" fill="none" stroke="#5e8f78" strokeWidth="22" strokeLinecap="round" />
      </g>
    </svg>
  );
}
