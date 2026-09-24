import { HeartPulse, RefreshCw } from "lucide-react";
import styles from "@/app/home/home.module.css";
export function WorkspaceLoading() {
  return (
    <main className={styles.workspace} id="overview" aria-busy="true">
      <div className={styles.loadingHeader} role="status">
        <HeartPulse size={22} />
        <span>Bringing your care into focus…</span>
      </div>
      <div className={styles.skeletonGrid} aria-hidden="true">
        <div />
        <div />
        <div />
        <div />
      </div>
      <div className={styles.skeletonPanel} aria-hidden="true">
        <i />
        <i />
        <i />
      </div>
    </main>
  );
}
export function WorkspaceError({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <main className={styles.workspace} id="overview">
      <section className={styles.loadError} role="alert">
        <HeartPulse size={28} />
        <h1>Your care space needs another moment.</h1>
        <p>{message}</p>
        <button className="button-primary" onClick={retry}>
          <RefreshCw size={16} />
          Try again
        </button>
      </section>
    </main>
  );
}
