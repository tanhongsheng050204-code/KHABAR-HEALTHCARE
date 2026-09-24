"use client";
import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import {
  Activity,
  CalendarDays,
  HeartPulse,
  Home,
  LogOut,
  MessageCircle,
  ShieldCheck,
  Stethoscope,
  UserRoundPlus,
  UserRound,
  UsersRound,
} from "lucide-react";
import type { Me } from "@/lib/types";
import {
  ExperienceMotion,
  MotionButton,
  useExperienceMotion,
} from "@/components/experience-motion";
import {
  useWorkspaceNavigation,
  type WorkspaceSection,
} from "./workspace-navigation";
import styles from "@/app/home/home.module.css";
const roleIcon = {
  DOCTOR: Stethoscope,
  NURSE: HeartPulse,
  CLINIC_ADMIN: UserRoundPlus,
  PATIENT: UserRound,
  CAREGIVER: ShieldCheck,
};
export function AppShell({
  me,
  onSignOut,
  children,
}: {
  me: Me;
  onSignOut: () => void;
  children: ReactNode;
}) {
  const RoleIcon = roleIcon[me.role];
  const pathname = usePathname();
  const { section, navigate } = useWorkspaceNavigation();
  const clinicDoctor = me.clinicRoles?.includes("DOCTOR");
  const adminOnlyHome = me.clinicRoles?.includes("CLINIC_ADMIN") && !me.clinicRoles?.includes("NURSE") && !me.clinicRoles?.includes("DOCTOR");
  const links: { id: WorkspaceSection; label: string; icon: typeof Home }[] = [
    { id: "overview", label: adminOnlyHome ? "Staff & access" : me.clinicRoles?.includes("NURSE") && me.role !== "DOCTOR" ? "Call list" : "Overview", icon: adminOnlyHome ? UsersRound : me.clinicRoles?.includes("NURSE") ? HeartPulse : Home },
    ...(clinicDoctor
      ? [
          { id: "people" as const, label: "Patients", icon: UsersRound },
          { id: "schedule" as const, label: "Schedule", icon: CalendarDays },
        ]
      : []),
    ...(me.clinicRoles?.includes("NURSE") && me.role !== "DOCTOR"
      ? [
          { id: "people" as const, label: "Patient roster", icon: UsersRound },
        ]
      : []),
    ...(me.clinicRoles?.some((role) => role === "CLINIC_ADMIN" || role === "DOCTOR") && !adminOnlyHome
      ? [{ id: "staff" as const, label: "Staff & access", icon: UsersRound }]
      : []),
    ...(me.role === "PATIENT"
      ? [
          { id: "plan" as const, label: "My plan", icon: HeartPulse },
          { id: "checkin" as const, label: "Check in", icon: MessageCircle },
          { id: "readings" as const, label: "Readings", icon: Activity },
          { id: "people" as const, label: "My people", icon: UsersRound },
        ]
      : []),
  ];
  function navItems(mobile: boolean) {
    return links.map(({ id, label, icon: Icon }) => (
      <Link
        key={id}
        href={`/home#${id}`}
        className={
          pathname === "/home" && section === id ? styles.activeNav : undefined
        }
        aria-current={
          pathname === "/home" && section === id ? "location" : undefined
        }
        onClick={(event) => {
          if (
            pathname === "/home" &&
            event.button === 0 &&
            !event.metaKey &&
            !event.ctrlKey &&
            !event.shiftKey &&
            !event.altKey
          ) {
            event.preventDefault();
            navigate(id);
          }
        }}
      >
        {pathname === "/home" && section === id && (
          <NavigationIndicator mobile={mobile} />
        )}
        <Icon size={19} />
        <span>{label}</span>
      </Link>
    ));
  }
  return (
    <ExperienceMotion>
      <div className={styles.shell}>
        <a className={styles.skipLink} href="#overview">
          Skip to workspace
        </a>
        <aside className={styles.rail} aria-label="Your care space">
          <Link
            href="/"
            className={styles.railBrand}
            aria-label="Khabar landing page"
          >
            <span>
              <HeartPulse size={19} />
            </span>
            Khabar
          </Link>
          <div className={styles.workspaceLabel}>YOUR CARE SPACE</div>
          <nav aria-label="Workspace navigation">{navItems(false)}</nav>
          <div className={styles.railStory}>
            <HeartPulse size={25} />
            <p>
              Small check-ins.
              <br />
              <em>Meaningful continuity.</em>
            </p>
            <span>Care that carries on.</span>
          </div>
          <div className={styles.railFoot}>
            <div className={styles.profileMark}>
              <RoleIcon size={17} />
            </div>
            <div>
              <strong>{me.displayName}</strong>
              <span>{me.role.toLowerCase()}</span>
            </div>
            <button onClick={onSignOut} aria-label="Sign out">
              <LogOut size={17} />
            </button>
          </div>
        </aside>
        <div className={styles.main}>
          <header className={styles.contextBar}>
            <div>
              <Link
                href="/"
                className={styles.mobileMark}
                aria-label="Khabar landing page"
              >
                <HeartPulse size={17} />
              </Link>
              <p>{me.clinicName || "Khabar care"}</p>
            </div>
            <div>
              <span className={styles.demoPill}>Fictional data</span>
              <MotionButton />
              <button
                className={styles.mobileSignOut}
                aria-label="Sign out"
                onClick={onSignOut}
              >
                <LogOut size={17} />
              </button>
            </div>
          </header>
          {children}
        </div>
        <nav
          className={styles.mobileNav}
          style={{ gridTemplateColumns: `repeat(${links.length}, 1fr)` }}
          aria-label="Mobile navigation"
        >
          {navItems(true)}
        </nav>
      </div>
    </ExperienceMotion>
  );
}

function NavigationIndicator({ mobile }: { mobile: boolean }) {
  const { enabled } = useExperienceMotion();
  if (!enabled) return <i className={styles.navIndicator} />;
  return (
    <motion.i
      className={styles.navIndicator}
      layoutId={mobile ? "mobile-navigation" : "desktop-navigation"}
      transition={{ type: "spring", stiffness: 350, damping: 32 }}
    />
  );
}
