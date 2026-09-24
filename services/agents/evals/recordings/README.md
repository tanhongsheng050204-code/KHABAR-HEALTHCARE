# Transcription test recordings

The plan's one-hour transcription test, with five fictional consultation notes in Manglish.

1. Read each script aloud (`01.txt` to `05.txt`) as a doctor would dictate, and save the recording next
   to it with the same number: `01.m4a`, `02.m4a`, … (m4a, mp3, wav, webm and ogg all work).
   If you change a word while speaking, edit the `.txt` so it matches what you actually said.
2. Put `GROQ_API_KEY` in `services/agents/.env` (never commit it).
3. From `services/agents`, run:

   ```
   python -m scripts.score_transcripts --models whisper-large-v3-turbo whisper-large-v3 --out ../../docs/evals/transcription.md
   ```

The number that decides is **medicines missed or heard as another medicine**. Word error rate is shown
too, but a wrong filler word matters far less than "gliclazide" heard as "glipizide".

Use only these fictional scripts; never record a real patient. Audio files here are ignored by git.
