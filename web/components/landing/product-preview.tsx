"use client"

import { useState } from "react"
import { Activity, ArrowUpRight, BellRing, CalendarDays, Check, HeartPulse, MessageCircle } from "lucide-react"
import styles from "@/app/landing.module.css"

export function ProductPreview() {
  const [view, setView] = useState<"clinic" | "patient">("clinic")
  return <div className={styles.productFrame}><div className={styles.frameTop}><div className={styles.windowDots}><i /><i /><i /></div><span>Live care workspace</span><div className={styles.framePulse}><i /> Connected</div></div><div className={styles.previewBody}>
    <aside className={styles.previewRail}><span className={styles.miniMark}><HeartPulse size={16} /></span><button className={view === "clinic" ? styles.activeIcon : ""} onClick={() => setView("clinic")} aria-label="Show clinic view"><Activity size={17} /></button><button className={view === "patient" ? styles.activeIcon : ""} onClick={() => setView("patient")} aria-label="Show patient view"><MessageCircle size={17} /></button></aside>
    <div className={styles.previewContent}><div className={styles.previewHeading}><div><small>{view === "clinic" ? "Tuesday · Clinic view" : "Day 4 · Patient view"}</small><strong>{view === "clinic" ? "Good morning, Dr. Lee." : "Good morning, Aminah."}</strong></div><button aria-label="Notifications"><BellRing size={16} /><i /></button></div>
      {view === "clinic" ? <><div className={styles.previewMetrics}><div><span className={styles.redDot} />Call now<strong>2</strong></div><div><span className={styles.amberDot} />Check today<strong>5</strong></div><div><CalendarDays size={14} />Visits<strong>8</strong></div></div><div className={styles.previewList}><div className={styles.listTitle}><span>Who needs you first</span><small>Prioritised now</small></div><PreviewPerson initials="AN" name="Aminah N." note="Dizziness after morning dose" tone="red" /><PreviewPerson initials="RK" name="Ravi K." note="Blood pressure above range" tone="amber" /><PreviewPerson initials="LT" name="Lim T." note="No reply for two days" tone="blue" /></div></> : <><div className={styles.careSummary}><span><Check size={15} /> Your plan is up to date</span><h3>Keep taking your medicine after food.</h3><p>If the dizziness returns, send the clinic a message today.</p></div><div className={styles.nextStep}><CalendarDays size={18} /><div><small>Next appointment</small><strong>Thursday, 10:30 AM</strong></div><ArrowUpRight size={17} /></div><div className={styles.patientActions}><button>Record a reading</button><button>Ask the clinic</button></div></>}
    </div></div></div>
}

function PreviewPerson({ initials, name, note, tone }: { initials: string; name: string; note: string; tone: string }) { return <div className={styles.previewPerson}><span className={styles.avatar}>{initials}</span><div><strong>{name}</strong><small>{note}</small></div><i className={styles[tone]} /><ArrowUpRight size={15} /></div> }
