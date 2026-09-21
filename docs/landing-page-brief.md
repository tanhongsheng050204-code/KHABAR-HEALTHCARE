# Khabar landing page: design brief and Higgsfield prompts

A scroll-driven, cinematic landing page. As the visitor scrolls, a continuous film plays forward. It tells one story: **Mak Cik Aminah leaves the clinic, and Khabar follows her home.**

This file has four parts:
1. **Design direction:** concept, look, type, colour
2. **Page storyboard:** every section, with its clip, the copy shown over it, and its interaction
3. **Higgsfield prompts:** characters, keyframe stills and video clips, ready to paste
4. **Build notes:** how to make the video respond to scrolling in Next.js

---

## 1. Design direction

### Concept: "The thread home"
A thin, warm **thread of light** leaves the clinic with Aminah and follows her home. It pulses when Khabar checks in, turns amber when something is wrong, and reaches her daughter in KL. In the finale it joins with hundreds of other threads across the town.

The thread is the single visual idea that links every clip. Where it appears, it's the brand.

### Mood
Warm, dignified and quietly hopeful. It should feel like a Malaysian family film, **not** a hospital advert:
- No sterile blue-white
- No stock-photo smiles
- No distress shown for shock value

### Colour tokens
| Token | Hex | Use |
|---|---|---|
| `santan` | `#F6EFE4` | Page background (light) |
| `kopi` | `#3B2A20` | Main text |
| `teh-tarik` | `#B08968` | Secondary text, dividers |
| `pandan` | `#2F6B4F` | Brand, "all good", the thread's normal state |
| `sunset-amber` | `#E8A33D` | Attention, the thread's warning state |
| `cili` | `#C8452D` | True red flag only, used sparingly |
| `dusk` | `#1E2A44` | Night sections, dark mode |

The video grade should match these colours: cream highlights, warm browns, pandan-green accents, amber practical lights.

### Type (all on Google Fonts)
- **Display:** Fraunces, a warm serif with character, for headlines
- **Body and UI:** Plus Jakarta Sans
- **Multilingual:** Noto Sans SC (Chinese) and Noto Sans Tamil, loaded only when needed

### Rules for every clip
1. **No text, logos or readable screens inside the video.** AI video garbles lettering, and a WhatsApp logo would impersonate a real brand. All words live in HTML on top of the video.
2. **Leave space for the headline.** Keep the left 40% of the frame calm (desktop) and the top 35% calm (mobile).
3. **One camera move per clip.**
4. **Each clip's last frame is the next clip's first frame.** That's what makes scrolling feel like one continuous film.
5. **The same three characters throughout,** kept consistent with Soul ID (Higgsfield's character-consistency feature).

---

## 2. Page storyboard

Each "act" is pinned while its clip advances with the scroll. Typical length: 150–250 vh of scrolling per act.

| Act | Clip | Copy over the video (HTML) | Interaction |
|---|---|---|---|
| **0 · Hero** | C1 (loops until scrolling starts) | **The visit ends. The care shouldn't.** · *Khabar follows your patients home: in their language, for 30 days.* · A greeting cycles through *Apa khabar? → 你好吗? → நலமா? → How are you?* | Clicking the greeting switches language, and the copy lower on the page follows it. "Scroll to follow Aminah" hint. |
| **1 · Lost in the paper** | C2 | **Instructions in English jargon. Patients who think in BM, 中文 or தமிழ்.** · Counter: **35%** of Malaysian adults have limited health literacy | The count-up starts when the section comes into view |
| **2 · Everything she takes** | C3 | **Three clinics. One kitchen table. One hidden duplicate.** · Sub: *Khabar reads every packet, including jamu and supplements, and flags what clashes.* | Hovering a packet (tapping on mobile) draws a line to its duplicate |
| **3 · Apa khabar?** | C4 → C5 | **Asked every day, for 30 days.** · A vertical 30-day thread fills as you scroll: day 1, 3, 7, 14, 30 | The day markers light up as the time-lapse passes |
| **4 · In every language** | C6a / C6b / C6c (three panels side by side) | **Apa khabar? · 你好吗? · நலமா?** · *Summaries and check-ins in BM, English, Chinese and Tamil.* | Hovering a panel plays it and shows a sample summary in that language |
| **5 · The red flag** | C7 → C8 | Quote: ***"Pening dan berpeluh."*** · **Khabar hears it. The clinic calls.** | **Try-it demo:** "Type how you feel." The visitor types a reply and sees the triage result (§4.4) |
| **6 · Family knows too** | C9 | **Her daughter knows too, with her consent.** · Sub: *And Aminah can see exactly who viewed her record.* | A mini "who viewed my record" timeline slides in |
| **7 · Ramadan** | C10 | **Sahur. Berbuka. Reminders that fast with her.** | The sky colour of the page shifts with the clip |
| **8 · Finale** | C11 | **Know who needs a call today.** · A preview of the clinic's call list animates in · Buttons: **See the demo** / **Read the plan** | The call-list rows are clickable (they expand) |
| **Footer** | — | *Scenes are AI-generated; characters are fictional. Demo uses fake data. Khabar is not a medical device.* · Credits | — |

**Clip priority if time is short:** C1, C3, C4, C7, C11 are the **must-haves**. The rest are nice-to-have. Without them, the page falls back to still keyframes with crossfades.

---

## 3. Higgsfield prompts

### 3.0 Workflow
1. **Characters first.** Create a Soul ID for each character (3.2) and use it in every image and clip.
2. **Keyframes second.** Generate the stills K1–K12 (3.3) in image mode. Spend your credits here: in a scroll-driven film, the first and last frame of each clip are what visitors look at longest.
3. **Clips last.** In Cinema Studio (or any model that accepts a start *and* end frame), set **start = K(n), end = K(n+1)**, pick the one camera move, and paste the clip prompt (3.4).
4. **Two aspect ratios.** Generate **16:9** for desktop and **9:16** for mobile. If credits are tight, generate 16:9 with the subject centred and crop for mobile.
5. **Settings:** the highest resolution available, 24 fps, 5–8 seconds per clip.

### 3.1 Style block (paste at the end of every image and video prompt)
> Photoreal, filmed look: ARRI Alexa 35, Cooke S4 lens, shallow depth of field, soft natural light with warm practical lamps. Colour grade: cream highlights, warm teh-tarik browns, deep pandan-green accents, amber glows. Subtle 35mm film grain, gentle halation on lights. Authentic present-day Malaysian setting with real everyday detail. Dignified, warm, quietly hopeful mood.

**Negative prompt (every generation):**
> text, letters, words, subtitles, captions, logos, watermarks, brand names, readable phone or computer screens, WhatsApp logo, hospital-blue colour cast, stock-photo posing, exaggerated distress, extra fingers, deformed hands, distorted faces, face morphing between frames, flicker, jump cuts, fast shaky camera

### 3.2 Characters (Soul ID sheets)
Generate each as a character sheet: front, three-quarter and profile views, neutral background.

**Mak Cik Aminah (main character):**
> Malaysian Malay woman, 67, kind round face with soft smile lines, reading glasses on a beaded chain, cream tudung bawal, soft pastel floral baju kurung, small gold stud earrings, gentle unhurried posture. Character sheet: front, three-quarter and profile views, neutral warm-grey background, even soft light.

**Nurul (her daughter):**
> Malaysian Malay woman, 34, office professional in Kuala Lumpur, dusty-rose tudung, navy blazer over a cream blouse, company lanyard, tired but warm eyes. Character sheet: front, three-quarter and profile views, neutral warm-grey background, even soft light.

**Dr Priya (clinic doctor):**
> Malaysian Indian woman, 40, family doctor, white coat over a teal blouse, stethoscope around her neck, dark hair in a low bun, calm and attentive expression. Character sheet: front, three-quarter and profile views, neutral warm-grey background, even soft light.

**Montage patients (for C6 only):**
- *Uncle Tan:* Malaysian Chinese man, 72, white singlet and short-sleeve shirt, sitting at a marble kopitiam table
- *Mr Muthu:* Malaysian Indian man, 65, neat polo shirt, sitting on the porch of a terrace house with potted plants
- *Pak Hassan:* Malaysian Malay man, 70, songkok and batik shirt, sitting on a wooden kampung house veranda

### 3.3 Keyframe stills (image mode)
Append the **style block** to each one. The composition notes keep the headline area clear.

| Key | Prompt |
|---|---|
| **K1** | Golden hour outside a small Malaysian neighbourhood clinic in a two-storey shoplot, glass door, potted plants, motorbikes softly out of focus. **@Aminah** has just stepped out holding a small clear plastic medicine bag in one hand and a folded paper prescription in the other, pausing and looking down at the paper with mild uncertainty. Medium-wide shot, subject on the right third, left 40% of the frame calm street bokeh. |
| **K2** | Over-the-shoulder close-up from behind **@Aminah**, looking down at an unfolded prescription paper in her hands. The printed lines are dense and **completely illegible, softly out of focus**. Her reading glasses catch the amber sunset. Very shallow focus. |
| **K3** | The same paper, now dissolving: its printed lines lift off the page as tiny warm glowing particles, drifting upward like fireflies. The paper is fading into soft amber light. Abstract, magical-realist, still grounded. |
| **K4** | Top-down view of a Malaysian home kitchen table (wood with a lace tablecloth). Scattered on it: medicine packets from three different clinics in different colours, two amber herbal-remedy bottles, jamu sachets, a supplement tub and an empty weekly pill organiser. Warm pendant lamp light. **No readable labels.** A few glowing particles are still settling onto the table. |
| **K5** | The same top-down table, now tidy: every packet and bottle lined up neatly in one row. Two identical-looking packets from different clinics glow with a soft **amber** outline, and one herbal bottle glows faintly amber. Everything else has a soft **pandan-green** glow. |
| **K6** | Side angle at the same table. **@Aminah** sits and picks up her phone, which is emitting a soft pandan-green glow (**screen not readable**). She smiles gently as she reads. Warm lamp light, a rattan chair and a framed family photo softly blurred behind her. |
| **K7** | Exterior of **@Aminah's** single-storey terrace house at dusk: tiled roof, metal gate, bougainvillea, a warm window light. A thin, delicate thread of warm pandan-green light rises from the window and stretches away towards the distant town lights. |
| **K8** | Indoors, afternoon. **@Aminah** sits on her sofa with one hand on her forehead, eyes half closed, looking dizzy but safe. The room's light has shifted to a soft **amber** tone. Her phone glows amber beside her. Tender, not alarming. |
| **K9** | A small Malaysian clinic consultation room. **@DrPriya** at her desk looks up from a monitor emitting a soft amber glow (**screen not readable**), phone raised to her ear, attentive and calm. Late-afternoon window light. |
| **K10** | Night, a high-rise office in Kuala Lumpur, city skyline and the Twin Towers softly out of focus through the window. **@Nurul** at her desk holds her phone, which glows pandan-green (**screen not readable**), and exhales with relief, a small smile. A thin thread of light enters the frame from the far distance. |
| **K11** | Pre-dawn sahur at **@Aminah's** home: a small table with dates, a glass of water, a bowl of rice and a pill organiser, lit by a warm lantern. **@Aminah** takes a tablet with water, calm. Deep blue window light behind her. |
| **K12** | Aerial view at blue hour over a Malaysian town: rows of terrace houses, a mosque dome, shoplots, a highway ribbon. From hundreds of homes, thin threads of warm light rise and connect to a small glowing clinic at the centre, forming a gentle web. Serene and hopeful. |

Also generate one **montage still** per C6 panel (Uncle Tan, Mr Muthu, Pak Hassan), each holding a softly glowing phone with a warm smile. Use a vertical 4:5 crop, subject centred.

### 3.4 Video clips (start frame → end frame)
Every clip uses **one camera move**. Append the **style block** and the **negative prompt**.

---

**C1 · Hero loop (K1 → K1, 6 s)** · Camera: *slow dolly in, very subtle*
> @Aminah stands just outside the clinic door at golden hour, gently breathing, a light breeze moving the edge of her tudung and the paper in her hand. Background motorbikes pass softly out of focus, and leaves flicker in the warm light. She glances down at the paper, then back up. The camera creeps forward only slightly. The motion is calm and continuous and can loop.

*Tip:* if the start = end loop looks unnatural, generate K1 → K1b (the same frame one second later) and play it forwards and backwards in code.

---

**C2 · Lost in the paper (K1 → K2 → K3, two clips of 5 s)** · Camera: *dolly in to over-the-shoulder*, then *static with a slow push*
> Clip A: The camera glides forward and around @Aminah's shoulder until the unfolded prescription fills the lower frame. The text stays illegible and out of focus, and sunset glints on her glasses.
> Clip B: The camera holds steady on the paper. Its printed lines slowly lift off the page as tiny warm glowing particles, rising and drifting like fireflies, and the paper fades into soft amber light.

---

**C3 · Everything she takes (K4 → K5, 7 s)** · Camera: *slow overhead rotation, 30° clockwise*
> Top-down on the kitchen table. The glowing particles settle and a soft band of light sweeps across the table like a gentle scanner. As it passes, each packet, bottle and sachet glides smoothly into one neat row. Two matching packets from different clinics light up with an amber outline, and one herbal bottle glows faint amber. Smooth, satisfying, precise movement, with objects sliding rather than teleporting.

---

**C4 · Apa khabar? (K5 → K6, 6 s)** · Camera: *crane down and tilt from top-down to eye level*
> From top-down, the camera descends and tilts to a side angle at the table as @Aminah sits and picks up her phone. The phone glows soft pandan green and its screen is not readable. She reads and a gentle smile spreads across her face. Warm lamp light.

---

**C5 · Thirty days (K6 → K7, 8 s)** · Camera: *pull back through the window, then rise to a wide exterior*
> The camera pulls back from @Aminah through the window to outside her terrace house. Time-lapse: the sky cycles gently through several sunsets and sunrises and the window light turns on and off, while a thin pandan-green thread of light pulses once each day from the window towards the distant town. It ends at dusk.

---

**C6a / C6b / C6c · In every language (still montage frame → same, 4 s each)** · Camera: *slow push in*
> [Uncle Tan at a kopitiam table / Mr Muthu on his terrace porch / Pak Hassan on his kampung veranda] looks at his softly glowing phone, which is not readable, and breaks into a warm smile. Natural ambient movement around him: steam from a kopi cup / a ceiling fan / leaves in the breeze.

---

**C7 · The red flag (K6 → K8, 6 s)** · Camera: *slow lateral truck to the right*
> Later that afternoon. @Aminah moves from the table to the sofa and sits slowly with one hand on her forehead, feeling dizzy. The room's warm light gradually shifts to a soft amber tone, and her phone beside her glows amber. Tender and calm, not dramatic.

---

**C8 · The clinic calls (K9 → K9b, 5 s)** · Camera: *slow dolly in*
> @DrPriya notices a soft amber glow on her monitor, which is not readable. She picks up her phone, raises it to her ear and speaks calmly and reassuringly. Late-afternoon window light and a quiet clinic room.

(**K9b:** the same shot, with @DrPriya mid-sentence and a gentle reassuring expression.)

---

**C9 · Family knows too (K10 → K10b, 6 s)** · Camera: *slow orbit, 20°*
> Night in a Kuala Lumpur office. @Nurul looks at her glowing phone, which is not readable, exhales in relief and smiles softly. A thin thread of warm light arrives from the far distance across the city skyline and touches the window near her. The city lights twinkle.

(**K10b:** the same scene after a 20° orbit, with the thread touching the glass.)

---

**C10 · Ramadan (K11 → K11b, 7 s)** · Camera: *slow truck to the left*
> Before dawn, @Aminah finishes sahur, takes her tablet with water and sets down the pill organiser. Through the window the deep blue sky warms toward dawn. The camera slides sideways and the scene transitions softly to golden-hour berbuka at the same table with family hands reaching for dates.

(**K11b:** a golden-hour berbuka table with the same pill organiser in view.)

---

**C11 · Finale (K7 → K12, 8 s)** · Camera: *crane up into an aerial pull-back*
> From @Aminah's terrace house at dusk, the camera rises smoothly into the sky. As it climbs, threads of warm light appear from more and more homes across the town, all connecting to a small glowing clinic at the centre and forming a gentle web of light at blue hour. Serene and hopeful.

---

## 4. Build notes (for `apps/web`)

### 4.1 Making the video follow the scroll
- **Recommended: image sequence on a `<canvas>`.** Extract each clip to WebP frames and draw the frame that matches the scroll position. This is smooth on iOS Safari, where scrubbing an MP4 stutters.
  ```bash
  # desktop frames (~1600px wide) and mobile frames (~900px)
  ffmpeg -i c3.mp4 -vf "fps=24,scale=1600:-2" -c:v libwebp -quality 70 public/seq/c3/d_%04d.webp
  ffmpeg -i c3_9x16.mp4 -vf "fps=24,scale=900:-2" -c:v libwebp -quality 65 public/seq/c3/m_%04d.webp
  ```
- **Simpler alternative:** re-encode each MP4 so every frame is a keyframe, then set `video.currentTime` from the scroll position.
  ```bash
  ffmpeg -i c3.mp4 -c:v libx264 -g 1 -crf 23 -pix_fmt yuv420p -movflags +faststart c3_scrub.mp4
  ```
- **Scroll control:** GSAP ScrollTrigger (`pin: true`, `scrub: true`), with Lenis for smooth scrolling.
- **Loading:** load Act 0–1 frames first, lazy-load later acts, and show the matching K-frame as a poster until frames arrive. Budget about 2 MB above the fold on mobile.

### 4.2 Accessibility and fallbacks
- **`prefers-reduced-motion`:** no scrubbing. Show the K-frames as still images with a simple crossfade. The copy stays the same.
- **Readability:** headlines sit on a soft gradient behind the text (`santan`, or `dusk` at night), and contrast must meet WCAG AA against every frame.
- **Language:** the hero language switch sets `lang` on the swapped text (`ms`, `zh`, `ta`, `en`) and loads the Noto fonts on demand.
- **Mobile:** pin for less scrolling (about 120 vh per act), use the 9:16 frame set, and use tap in place of hover.

### 4.3 Honesty on the page
- The footer states that scenes are AI-generated, characters are fictional, the demo uses fake data, and Khabar is not a medical device.
- No real clinic names or logos, and no WhatsApp logo. Say "on WhatsApp" in text only.

### 4.4 "Type how you feel" demo (Act 5)
A client-side keyword demo that shows the triage logic without calling any AI. Label it *"Illustrative demo, not medical advice."*
| Result | Example words (BM / EN / 中文 / தமிழ்) | What appears |
|---|---|---|
| 🔴 Red flag | *sakit dada, sesak nafas, pengsan* / chest pain, can't breathe, fainted / 胸痛, 呼吸困难 / நெஞ்சு வலி | "The clinic has been alerted and will call you now." The thread turns `cili`. |
| 🟠 Watch | *pening, berpeluh, menggeletar* / dizzy, sweating, shaky / 头晕, 冒汗 / மயக்கம் | "Your clinic will check on you today." The thread turns `sunset-amber`. |
| 🟢 OK | *okay, sihat, dah makan ubat* / fine, took my medicine / 很好 / நலம் | "Glad to hear it. Next check-in: tomorrow." The thread stays `pandan`. |

Ask a native speaker to check the Chinese and Tamil words before launch.
