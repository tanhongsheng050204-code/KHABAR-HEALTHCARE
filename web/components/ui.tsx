import type { ReactNode } from "react"
import { Inbox, X } from "lucide-react"

export function StatusBadge({ level, children }: { level: string; children: ReactNode }) { return <span className={`status-badge status-${level.toLowerCase()}`}>{children}</span> }
export function EmptyState({ title, copy, icon = <Inbox size={20} /> }: { title: string; copy: string; icon?: ReactNode }) { return <div className="empty-state"><span>{icon}</span><strong>{title}</strong><p>{copy}</p></div> }
export function Toast({ children, tone = "info", onClose }: { children: ReactNode; tone?: "info" | "error" | "success"; onClose?: () => void }) { return <div className={`toast toast-${tone}`} role={tone === "error" ? "alert" : "status"}><span>{children}</span>{onClose && <button onClick={onClose} aria-label="Dismiss message"><X size={16} /></button>}</div> }
export function SectionHeading({ eyebrow, title, action }: { eyebrow?: string; title: string; action?: ReactNode }) { return <div className="section-heading"><div>{eyebrow && <p>{eyebrow}</p>}<h2>{title}</h2></div>{action}</div> }
