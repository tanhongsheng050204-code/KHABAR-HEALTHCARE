"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { HeartPulse } from "lucide-react";
import { apiRequest, ApiError } from "@/lib/api";
import { clearToken, getToken } from "@/lib/session";
import type { Me } from "@/lib/types";
import { Toast } from "@/components/ui";
import { AppShell } from "./app-shell";
import { DoctorHome } from "./doctor-home";
import { PatientHome } from "./patient-home";
import { CaregiverHome } from "./caregiver-home";
import styles from "@/app/home/home.module.css";

export function HomeClient() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [notice, setNotice] = useState<{
    tone: "error" | "success" | "info";
    text: string;
  } | null>(null);
  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.replace("/login");
      return;
    }
    apiRequest<Me>("/api/me", {}, token)
      .then(setMe)
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login");
          return;
        }
        setNotice({
          tone: "error",
          text:
            error instanceof Error
              ? error.message
              : "Your workspace could not be opened.",
        });
      })
      .finally(() => setLoading(false));
  }, [router, attempt]);
  function signOut() {
    clearToken();
    router.replace("/login");
  }
  if (loading)
    return (
      <main className={styles.loading}>
        <span>
          <HeartPulse size={24} />
        </span>
        <p>Bringing your care into focus…</p>
      </main>
    );
  if (!me)
    return (
      <main className={styles.loading}>
        <p>We could not open your workspace.</p>
        <p role="alert">{notice?.text}</p>
        <button
          className="button-primary"
          onClick={() => {
            setLoading(true);
            setNotice(null);
            setAttempt(attempt + 1);
          }}
        >
          Try again
        </button>
        <button
          className="button-primary"
          onClick={() => router.replace("/login")}
        >
          Return to sign in
        </button>
      </main>
    );
  return (
    <AppShell me={me} onSignOut={signOut}>
      {notice && (
        <Toast tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Toast>
      )}
      {me.role === "DOCTOR" && <DoctorHome me={me} notify={setNotice} />}{" "}
      {me.role === "PATIENT" && <PatientHome me={me} notify={setNotice} />}{" "}
      {me.role === "CAREGIVER" && <CaregiverHome me={me} notify={setNotice} />}
    </AppShell>
  );
}
