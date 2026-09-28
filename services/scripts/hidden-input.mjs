// Hidden terminal input for the Telegram setup scripts: nothing typed or pasted is echoed.

// Typed characters not yet consumed: piped input can bring both answers in one chunk.
let pending = "";
let lastWasCr = false;

// Terminals can wrap a paste in bracketed-paste markers (ESC[200~ ... ESC[201~) or pass Ctrl+V and other
// keys through as control characters; none of that is part of a token or secret.
export function clean(value) {
  return value.replace(/\u001b\[20[01]~/g, "").replace(/[\u0000-\u001f\u007f\u200b\ufeff]/g, "").trim();
}

// Says what is wrong with a value without showing it.
export function describe(value) {
  const problems = [];
  if (/\s/.test(value)) problems.push("a space");
  if (/["'`]/.test(value)) problems.push("quote marks");
  if (/[^\x20-\x7e]/.test(value)) problems.push("non-ASCII characters");
  const other = value.replace(/[A-Za-z0-9_\-\s"'`]|[^\x20-\x7e]/g, "");
  if (other) problems.push(`other symbols (${[...new Set(other)].join(" ")})`);
  return `${value.length} characters received${problems.length ? `, including ${problems.join(", ")}` : ""}`;
}

export function hidden(question) {
  return new Promise((resolve) => {
    process.stdout.write(question);
    const stdin = process.stdin;
    let value = "";
    const take = (text) => {
      for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (ch === "\n" && lastWasCr) {
          lastWasCr = false;
          continue;
        }
        lastWasCr = ch === "\r";
        if (ch === "\r" || ch === "\n") {
          pending = text.slice(i + 1);
          return true;
        }
        if (ch === "\u0003") process.exit(1);
        if (ch === "\u007f" || ch === "\b") value = value.slice(0, -1);
        else value += ch;
      }
      pending = "";
      return false;
    };
    const finish = () => {
      stdin.setRawMode?.(false);
      stdin.pause();
      stdin.off("data", onData);
      process.stdout.write("\n");
      resolve(clean(value));
    };
    const onData = (chunk) => {
      if (take(chunk)) finish();
    };
    if (take(pending)) {
      process.stdout.write("\n");
      resolve(clean(value));
      return;
    }
    stdin.setRawMode?.(true);
    stdin.setEncoding("utf8");
    stdin.resume();
    stdin.on("data", onData);
  });
}
