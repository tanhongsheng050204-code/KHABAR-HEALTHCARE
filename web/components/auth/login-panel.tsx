"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowRight,
  Check,
  Eye,
  EyeOff,
  ChevronLeft,
  KeyRound,
  LoaderCircle,
  Mail,
  Stethoscope,
  UserRound,
} from "lucide-react";
import { apiRequest } from "@/lib/api";
import { setToken } from "@/lib/session";
import type { Me } from "@/lib/types";
import styles from "@/app/login/login.module.css";
import authStyles from "./login-panel.module.css";

type SignInMethod = "password" | "otp";
type Stage = "credentials" | "code";

export function LoginPanel() {
  const router = useRouter();
  const [method, setMethod] = useState<SignInMethod>("otp");
  const [stage, setStage] = useState<Stage>("credentials");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [resendAfter, setResendAfter] = useState(0);
  const codeInput = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (stage === "code") codeInput.current?.focus();
  }, [stage]);
  useEffect(() => {
    if (resendAfter <= 0) return;
    const timer = window.setTimeout(
      () => setResendAfter(resendAfter - 1),
      1000,
    );
    return () => window.clearTimeout(timer);
  }, [resendAfter]);
  const [otp, setOtp] = useState("");
  const [manualToken, setManualToken] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{
    tone: "error" | "success";
    text: string;
  } | null>(null);
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL?.replace(/\/$/, "");
  const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

  async function complete(token: string) {
    if (inviteCode.trim()) {
      await apiRequest(
        `/api/invites/${encodeURIComponent(inviteCode.trim())}/accept`,
        {
          method: "POST",
          body: JSON.stringify({
            displayName: displayName.trim() || undefined,
          }),
        },
        token,
      );
    }
    await apiRequest<Me>("/api/me", {}, token);
    setToken(token);
    router.replace("/home");
  }

  async function demo(role: "doctor" | "patient") {
    setBusy(role);
    setMessage(null);
    try {
      const result = await apiRequest<{ token: string }>(
        `/dev/token?as=${role}`,
        { method: "POST" },
        null,
      );
      await complete(result.token);
    } catch (error) {
      setMessage({
        tone: "error",
        text:
          error instanceof Error ? error.message : "The demo could not start.",
      });
    } finally {
      setBusy(null);
    }
  }

  async function sendCode(event?: React.FormEvent) {
    event?.preventDefault();
    setMessage(null);
    if (!supabaseUrl || !supabaseKey) {
      setMessage({
        tone: "error",
        text: "Email sign-in is not configured in this deployment. Use a local demo account.",
      });
      return;
    }
    setBusy("email");
    try {
      const response = await fetch(`${supabaseUrl}/auth/v1/otp`, {
        method: "POST",
        headers: { apikey: supabaseKey, "Content-Type": "application/json" },
        body: JSON.stringify({ email, create_user: true }),
      });
      if (!response.ok)
        throw new Error(
          "We could not send a code to that address. Check it and try again.",
        );
      setStage("code");
      setOtp("");
      setResendAfter(60);
      setMessage({
        tone: "success",
        text: `A secure code is on its way to ${email}.`,
      });
    } catch (error) {
      setMessage({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The code could not be sent.",
      });
    } finally {
      setBusy(null);
    }
  }

  async function verifyCode(event: React.FormEvent) {
    event.preventDefault();
    if (!supabaseUrl || !supabaseKey) return;
    setBusy("code");
    setMessage(null);
    try {
      const response = await fetch(`${supabaseUrl}/auth/v1/verify`, {
        method: "POST",
        headers: { apikey: supabaseKey, "Content-Type": "application/json" },
        body: JSON.stringify({ email, token: otp, type: "email" }),
      });
      const body = (await response.json()) as { access_token?: string };
      if (!response.ok || !body.access_token)
        throw new Error(
          "That code was not accepted. Request a new one and try again.",
        );
      await complete(body.access_token);
    } catch (error) {
      setMessage({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The code could not be verified.",
      });
    } finally {
      setBusy(null);
    }
  }

  async function signInWithPassword(event: React.FormEvent) {
    event.preventDefault();
    setMessage(null);
    if (!supabaseUrl || !supabaseKey) {
      setMessage({
        tone: "error",
        text: "Clinic sign-in is not configured in this deployment. Use a local demo account.",
      });
      return;
    }
    setBusy("password");
    try {
      const response = await fetch(
        `${supabaseUrl}/auth/v1/token?grant_type=password`,
        {
          method: "POST",
          headers: { apikey: supabaseKey, "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        },
      );
      const body = (await response.json()) as { access_token?: string };
      if (!response.ok || !body.access_token)
        throw new Error(
          "Email or password was not accepted. Check them and try again.",
        );
      setPassword("");
      await complete(body.access_token);
    } catch (error) {
      setMessage({
        tone: "error",
        text: error instanceof Error ? error.message : "Clinic sign-in failed.",
      });
    } finally {
      setBusy(null);
    }
  }

  async function useToken(event: React.FormEvent) {
    event.preventDefault();
    setBusy("token");
    setMessage(null);
    try {
      await complete(manualToken.trim());
    } catch (error) {
      setMessage({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "That token was not accepted.",
      });
    } finally {
      setBusy(null);
    }
  }

  const heading =
    stage === "code"
      ? "Enter the secure code"
      : method === "password"
        ? "Sign in to your clinic"
        : "Continue your care";

  return (
    <div className={styles.panel}>
      <div className={styles.formHeading}>
        <p>{stage === "code" ? "Check your inbox" : "Welcome back"}</p>
        <h2>{heading}</h2>
        <span>
          {stage === "code"
            ? `We sent a one-time code to ${email}.`
            : method === "password"
              ? "Clinic staff: sign in with your registered clinic email and password."
              : "Patients and invited caregivers: use your email address to receive a one-time code."}
        </span>
      </div>

      <div
        className={authStyles.methodGroup}
        role="group"
        aria-label="Choose your sign-in method"
      >
        <button
          className={
            method === "password"
              ? authStyles.methodSelected
              : authStyles.method
          }
          type="button"
          aria-pressed={method === "password"}
          onClick={() => {
            setMethod("password");
            setStage("credentials");
            setMessage(null);
          }}
          disabled={!!busy}
        >
          <Stethoscope size={16} /> Clinic staff
        </button>
        <button
          className={
            method === "otp" ? authStyles.methodSelected : authStyles.method
          }
          type="button"
          aria-pressed={method === "otp"}
          onClick={() => {
            setMethod("otp");
            setStage("credentials");
            setMessage(null);
          }}
          disabled={!!busy}
        >
          <UserRound size={16} /> Patient or caregiver
        </button>
      </div>

      {message && (
        <div
          className={`${styles.message} ${styles[message.tone]}`}
          role={message.tone === "error" ? "alert" : "status"}
        >
          {message.tone === "success" && <Check size={16} />}
          <span>{message.text}</span>
        </div>
      )}

      {stage === "code" ? (
        <form onSubmit={verifyCode} className={styles.form}>
          <label>
            One-time code
            <div className={styles.inputWrap}>
              <KeyRound size={17} />
              <input
                ref={codeInput}
                value={otp}
                onChange={(event) => setOtp(event.target.value)}
                placeholder="Enter your email code"
                inputMode="numeric"
                autoComplete="one-time-code"
                required
              />
            </div>
          </label>
          <button className="button-primary" disabled={!!busy}>
            {busy === "code" ? (
              <LoaderCircle className={styles.spin} size={17} />
            ) : (
              <ArrowRight size={17} />
            )}
            Continue securely
          </button>
          <button
            className={styles.backButton}
            type="button"
            onClick={() => {
              setStage("credentials");
              setOtp("");
              setMessage(null);
            }}
          >
            <ChevronLeft size={15} /> Use another email
          </button>
          <button
            className={styles.backButton}
            type="button"
            disabled={!!busy || resendAfter > 0}
            onClick={() => void sendCode()}
          >
            {resendAfter > 0
              ? `Resend available in ${resendAfter}s`
              : "Resend code"}
          </button>
        </form>
      ) : (
        <form
          onSubmit={method === "password" ? signInWithPassword : sendCode}
          className={styles.form}
        >
          <label>
            Email address
            <div className={styles.inputWrap}>
              <Mail size={17} />
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder={
                  method === "password" ? "name@clinic.my" : "you@example.com"
                }
                autoComplete="email"
                required
              />
            </div>
          </label>
          {method === "password" && (
            <label>
              Password
              <div className={styles.inputWrap}>
                <KeyRound size={17} />
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className={styles.passwordToggle}
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  aria-pressed={showPassword}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </label>
          )}
          <button className="button-primary" disabled={!!busy}>
            {busy === "email" || busy === "password" ? (
              <LoaderCircle className={styles.spin} size={17} />
            ) : (
              <ArrowRight size={17} />
            )}
            {method === "password" ? "Sign in securely" : "Email me a code"}
          </button>
        </form>
      )}

      <div className={styles.divider}>
        <span>Take a look around with fictional data</span>
      </div>
      <div className={styles.demoGrid}>
        <button onClick={() => void demo("doctor")} disabled={!!busy}>
          <span>
            <Stethoscope size={18} />
          </span>
          <div>
            <strong>Doctor view</strong>
            <small>Priorities, patients and visits</small>
          </div>
          <ArrowRight size={16} />
        </button>
        <button onClick={() => void demo("patient")} disabled={!!busy}>
          <span>
            <UserRound size={18} />
          </span>
          <div>
            <strong>Patient view</strong>
            <small>Plan, readings and appointments</small>
          </div>
          <ArrowRight size={16} />
        </button>
      </div>

      <details className={styles.advanced}>
        <summary>Have an invitation code?</summary>
        <div className={styles.advancedBody}>
          <label>
            Invitation code
            <input
              value={inviteCode}
              onChange={(event) => setInviteCode(event.target.value)}
              placeholder="Optional one-time invite"
            />
          </label>
          <label>
            Your name
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Needed for a new invitation"
            />
          </label>
        </div>
      </details>
      {process.env.NODE_ENV === "development" && (
        <details className={styles.advanced}>
          <summary>Developer access</summary>
          <div className={styles.advancedBody}>
            <form onSubmit={useToken}>
              <label>
                Supabase access token
                <textarea
                  rows={3}
                  value={manualToken}
                  onChange={(event) => setManualToken(event.target.value)}
                  required
                />
              </label>
              <button className="button-secondary" disabled={!!busy}>
                Validate and continue
              </button>
            </form>
          </div>
        </details>
      )}
      <p className={styles.prototypeNote}>
        This prototype uses fictional patient data. Do not enter real health
        information.
      </p>
    </div>
  );
}
