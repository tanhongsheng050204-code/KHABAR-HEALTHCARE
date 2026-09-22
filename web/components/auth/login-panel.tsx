"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { ArrowRight, Check, ChevronLeft, KeyRound, LoaderCircle, Mail, Stethoscope, UserRound } from "lucide-react"
import { apiRequest } from "@/lib/api"
import { setToken } from "@/lib/session"
import type { Me } from "@/lib/types"
import styles from "@/app/login/login.module.css"

type Mode = "email" | "code"

export function LoginPanel() {
  const router = useRouter()
  const [mode,setMode]=useState<Mode>("email")
  const [email,setEmail]=useState("")
  const [otp,setOtp]=useState("")
  const [manualToken,setManualToken]=useState("")
  const [inviteCode,setInviteCode]=useState("")
  const [displayName,setDisplayName]=useState("")
  const [busy,setBusy]=useState<string|null>(null)
  const [message,setMessage]=useState<{tone:"error"|"success";text:string}|null>(null)
  const supabaseUrl=process.env.NEXT_PUBLIC_SUPABASE_URL?.replace(/\/$/,"")
  const supabaseKey=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY

  async function complete(token:string){
    setToken(token)
    await apiRequest<Me>("/api/me",{},token)
    if(inviteCode.trim()) await apiRequest(`/api/invites/${encodeURIComponent(inviteCode.trim())}/accept`,{method:"POST",body:JSON.stringify({displayName:displayName.trim()||undefined})},token)
    router.replace("/home")
  }
  async function demo(role:"doctor"|"patient"){
    setBusy(role);setMessage(null)
    try{const result=await apiRequest<{token:string}>(`/dev/token?as=${role}`,{method:"POST"},null);await complete(result.token)}catch(error){setMessage({tone:"error",text:error instanceof Error?error.message:"The demo could not start."})}finally{setBusy(null)}
  }
  async function sendCode(event:React.FormEvent){
    event.preventDefault();setMessage(null)
    if(!supabaseUrl||!supabaseKey){setMessage({tone:"error",text:"Email sign-in is not configured in this local prototype. Use either demo below."});return}
    setBusy("email")
    try{const response=await fetch(`${supabaseUrl}/auth/v1/otp`,{method:"POST",headers:{apikey:supabaseKey,"Content-Type":"application/json"},body:JSON.stringify({email,create_user:true})});if(!response.ok)throw new Error("We could not send the code. Check the email and try again.");setMode("code");setMessage({tone:"success",text:`A secure code is on its way to ${email}.`})}catch(error){setMessage({tone:"error",text:error instanceof Error?error.message:"The code could not be sent."})}finally{setBusy(null)}
  }
  async function verifyCode(event:React.FormEvent){
    event.preventDefault();if(!supabaseUrl||!supabaseKey)return;setBusy("code");setMessage(null)
    try{const response=await fetch(`${supabaseUrl}/auth/v1/verify`,{method:"POST",headers:{apikey:supabaseKey,"Content-Type":"application/json"},body:JSON.stringify({email,token:otp,type:"email"})});const body=await response.json() as {access_token?:string;msg?:string};if(!response.ok||!body.access_token)throw new Error(body.msg||"That code was not accepted.");await complete(body.access_token)}catch(error){setMessage({tone:"error",text:error instanceof Error?error.message:"The code could not be verified."})}finally{setBusy(null)}
  }
  async function useToken(event:React.FormEvent){event.preventDefault();setBusy("token");setMessage(null);try{await complete(manualToken.trim())}catch(error){setMessage({tone:"error",text:error instanceof Error?error.message:"That token was not accepted."})}finally{setBusy(null)}}

  return <div className={styles.panel}><div className={styles.formHeading}><p>{mode==="email"?"Welcome back":"Check your inbox"}</p><h2>{mode==="email"?"Continue your care":"Enter the secure code"}</h2><span>{mode==="email"?"Use your clinic email, or explore with fictional demo data.":`We sent a one-time code to ${email}.`}</span></div>
    {message&&<div className={`${styles.message} ${styles[message.tone]}`} role={message.tone==="error"?"alert":"status"}>{message.tone==="success"&&<Check size={16}/>}<span>{message.text}</span></div>}
    {mode==="email"?<form onSubmit={sendCode} className={styles.form}><label>Email address<div className={styles.inputWrap}><Mail size={17}/><input type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="name@clinic.my" autoComplete="email" required/></div></label><button className="button-primary" disabled={!!busy}>{busy==="email"?<LoaderCircle className={styles.spin} size={17}/>:<ArrowRight size={17}/>} Email me a code</button></form>:<form onSubmit={verifyCode} className={styles.form}><label>One-time code<div className={styles.inputWrap}><KeyRound size={17}/><input value={otp} onChange={e=>setOtp(e.target.value)} placeholder="6-digit code" inputMode="numeric" autoComplete="one-time-code" required/></div></label><button className="button-primary" disabled={!!busy}>{busy==="code"?<LoaderCircle className={styles.spin} size={17}/>:<ArrowRight size={17}/>} Continue securely</button><button className={styles.backButton} type="button" onClick={()=>{setMode("email");setMessage(null)}}><ChevronLeft size={15}/> Use another email</button></form>}
    <div className={styles.divider}><span>Explore the local prototype</span></div><div className={styles.demoGrid}><button onClick={()=>void demo("doctor")} disabled={!!busy}><span><Stethoscope size={18}/></span><div><strong>Doctor view</strong><small>Priorities, patients and visits</small></div><ArrowRight size={16}/></button><button onClick={()=>void demo("patient")} disabled={!!busy}><span><UserRound size={18}/></span><div><strong>Patient view</strong><small>Plan, readings and appointments</small></div><ArrowRight size={16}/></button></div>
    <details className={styles.advanced}><summary>Invitation or developer access</summary><div className={styles.advancedBody}><label>Invitation code<input value={inviteCode} onChange={e=>setInviteCode(e.target.value)} placeholder="Optional invite code"/></label><label>Your name<input value={displayName} onChange={e=>setDisplayName(e.target.value)} placeholder="Needed for a new invitation"/></label><form onSubmit={useToken}><label>Supabase access token<textarea rows={3} value={manualToken} onChange={e=>setManualToken(e.target.value)} required/></label><button className="button-secondary" disabled={!!busy}>Validate and continue</button></form></div></details>
    <p className={styles.prototypeNote}>This prototype uses fictional patient data. Do not enter real health information.</p>
  </div>
}
