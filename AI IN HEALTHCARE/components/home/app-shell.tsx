"use client"

import type { ReactNode } from "react"
import Link from "next/link"
import { Bell, CalendarDays, HeartPulse, Home, LogOut, MessageCircle, ShieldCheck, Stethoscope, UserRound, UsersRound } from "lucide-react"
import type { Me } from "@/lib/types"
import styles from "@/app/home/home.module.css"

const roleIcon={DOCTOR:Stethoscope,PATIENT:UserRound,CAREGIVER:ShieldCheck}

export function AppShell({me,onSignOut,children}:{me:Me;onSignOut:()=>void;children:ReactNode}){
  const RoleIcon=roleIcon[me.role]
  return <div className={styles.shell}><aside className={styles.rail}><Link href="/" className={styles.railBrand} aria-label="Khabar landing page"><span><HeartPulse size={19}/></span>Khabar</Link><nav aria-label="Workspace navigation"><a className={styles.activeNav} href="#overview"><Home size={18}/>Overview</a>{me.role==="DOCTOR"&&<><a href="#people"><UsersRound size={18}/>Patients</a><a href="#schedule"><CalendarDays size={18}/>Schedule</a></>}{me.role==="PATIENT"&&<><a href="#plan"><HeartPulse size={18}/>My plan</a><a href="#check-in"><MessageCircle size={18}/>Check in</a></>}<a href="#privacy"><ShieldCheck size={18}/>Privacy</a></nav><div className={styles.railFoot}><div className={styles.profileMark}><RoleIcon size={17}/></div><div><strong>{me.displayName}</strong><span>{me.role.toLowerCase()}</span></div><button onClick={onSignOut} aria-label="Sign out"><LogOut size={17}/></button></div></aside><div className={styles.main}><header className={styles.contextBar}><div><span className={styles.mobileMark}><HeartPulse size={17}/></span><p>{me.clinicName||"Khabar care"}</p></div><div><span className={styles.demoPill}>Fictional data</span><button aria-label="Notifications"><Bell size={17}/></button></div></header>{children}</div><nav className={styles.mobileNav} aria-label="Mobile navigation"><a href="#overview"><Home size={19}/><span>Home</span></a><a href={me.role==="PATIENT"?"#plan":"#people"}><UsersRound size={19}/><span>{me.role==="PATIENT"?"Plan":"People"}</span></a><a href={me.role==="PATIENT"?"#check-in":"#schedule"}><MessageCircle size={19}/><span>{me.role==="PATIENT"?"Check in":"Today"}</span></a><button onClick={onSignOut}><LogOut size={19}/><span>Sign out</span></button></nav></div>
}
