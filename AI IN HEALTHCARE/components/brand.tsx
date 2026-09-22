import Link from "next/link"
import { HeartPulse } from "lucide-react"

export function Brand({ compact = false }: { compact?: boolean }) {
  return <Link className="brand" href="/" aria-label="Khabar home"><span className="brand-symbol" aria-hidden="true"><HeartPulse size={compact ? 17 : 20} strokeWidth={2.2} /></span><span>Khabar</span></Link>
}
