// Malaysian names: "bin", "binti", "a/l", "anak" and the like join a person's name to a parent's,
// so they are never a name of their own. Titles stay with the name they belong to ("Dr Priya").
const CONNECTORS = new Set(["bin", "binti", "bt", "bte", "a/l", "a/p", "s/o", "d/o", "al", "ap", "anak"])
const TITLES = new Set(["dr", "dr.", "prof", "prof.", "mr", "mr.", "mrs", "mrs.", "ms", "ms.", "encik", "puan", "cik", "datuk", "dato", "dato'", "datin", "tan sri"])

/** How to greet someone: "Dr Priya" stays whole, "Aminah binti Yusof" becomes "Aminah". */
export function greetingName(displayName: string): string {
  const words = displayName.trim().split(/\s+/)
  if (words.length > 1 && TITLES.has(words[0].toLowerCase())) return `${words[0]} ${words[1]}`
  return words[0] ?? ""
}

/** Two initials from the person's own names: "Aminah binti Yusof" is AY, "Awang anak Sagan" is AS. */
export function initials(fullName: string): string {
  const words = fullName.trim().split(/\s+/).filter(word => word && !CONNECTORS.has(word.toLowerCase()))
  const picked = words.length > 1 ? [words[0], words[words.length - 1]] : words
  return picked.map(word => word[0]).join("").toUpperCase()
}
