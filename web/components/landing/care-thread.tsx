"use client"

import { useRef } from "react"
import { motion, useScroll, useTransform } from "motion/react"
import { CalendarCheck, ClipboardCheck, HeartHandshake } from "lucide-react"
import styles from "@/app/landing.module.css"

const moments = [
  { number:"01", icon:CalendarCheck, title:"Before the visit", heading:"Arrive with the story already started.", copy:"The patient books, answers a calm guided intake, and shares medicines from every source. The clinic sees the important context before the room door opens.", proof:"Identity removed before AI assistance", side:"Patient tells Khabar in their own words" },
  { number:"02", icon:ClipboardCheck, title:"During the visit", heading:"Turn clinical shorthand into something safe and clear.", copy:"The doctor speaks or types notes. Khabar structures the draft, checks medicines and allergies, and keeps critical concerns visible until they are resolved.", proof:"Clinician reviews and finalises every plan", side:"Safety check stays tied to the source notes" },
  { number:"03", icon:HeartHandshake, title:"After the visit", heading:"Know who needs a person, before they disappear.", copy:"Patients receive a plain-language summary and check-ins. Replies and home readings are prioritised so the clinic can call the right person first.", proof:"Escalates to people—never diagnoses", side:"Family access follows patient consent" },
]

export function CareThread() {
  const ref = useRef<HTMLElement>(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start 75%", "end 55%"] })
  const scaleY = useTransform(scrollYProgress, [0, 1], [0, 1])
  return <section className={styles.journey} id="journey" ref={ref}><div className={styles.journeyHeader}><p>Three moments. One continuous picture.</p><h2>Nothing important gets<br />left at the clinic door.</h2></div><div className={styles.moments}><div className={styles.threadTrack} aria-hidden="true"><motion.i style={{ scaleY, transformOrigin:"top" }} /></div>{moments.map((moment,index)=>{const Icon=moment.icon;return <motion.article key={moment.number} className={styles.moment} initial={{opacity:0,y:38}} whileInView={{opacity:1,y:0}} viewport={{once:true,amount:.35}} transition={{duration:.7,delay:.08}}><div className={styles.momentNumber}>{moment.number}</div><div className={styles.momentCopy}><p><Icon size={18}/> {moment.title}</p><h3>{moment.heading}</h3><span>{moment.copy}</span><small><i/> {moment.proof}</small></div><div className={`${styles.momentScene} ${styles[`scene${index+1}`]}`}><div className={styles.sceneIcon}><Icon size={23}/></div><p>{moment.side}</p><div className={styles.sceneLines}><i/><i/><i/></div></div></motion.article>})}</div></section>
}
