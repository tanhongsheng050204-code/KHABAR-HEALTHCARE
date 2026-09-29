import styles from "../film.module.css";

/** Nurul, Aminah's fictional daughter in Kuala Lumpur, drawn in the same flat style as Aminah. */
export function Nurul() {
  return (
    <svg
      className={styles.nurul}
      viewBox="180 250 240 300"
      aria-hidden="true"
      focusable="false"
    >
      <g data-part="body">
        <path d="M205 548 C 210 440, 390 440, 395 548 Z" fill="#e8a33d" />
        <path
          d="M244 336 C 244 262, 356 262, 356 336 L 366 438 C 330 462, 270 462, 234 438 Z"
          fill="#6b4f7a"
        />
        <ellipse cx="300" cy="336" rx="42" ry="48" fill="#8a5a3c" />
        <path
          d="M260 318 C 266 272, 334 272, 340 318 C 326 298, 274 298, 260 318z"
          fill="#6b4f7a"
        />
        <ellipse
          data-part="eye"
          cx="285"
          cy="340"
          rx="4"
          ry="5"
          fill="#2b1d16"
        />
        <ellipse
          data-part="eye"
          cx="315"
          cy="340"
          rx="4"
          ry="5"
          fill="#2b1d16"
        />
        <path
          d="M290 360 q10 7 20 0"
          fill="none"
          stroke="#2b1d16"
          strokeWidth="3"
          strokeLinecap="round"
        />
        <path
          d="M250 470 C 232 480, 240 506, 262 500"
          fill="none"
          stroke="#c98a2e"
          strokeWidth="20"
          strokeLinecap="round"
        />
        <rect x="256" y="440" width="34" height="56" rx="7" fill="#3b2a20" />
        <rect x="260" y="446" width="26" height="40" rx="3" fill="#bfe3cf" />
      </g>
    </svg>
  );
}
