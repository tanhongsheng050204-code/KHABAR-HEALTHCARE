"""
Draws the fictional medicine packets in evals/packets/ and writes their expected answers to manifest.json.
Brands, clinic and patient are invented; nothing here is a real product label. Needs Pillow, which the
service itself does not use, so run it with any Python that has it:

    python scripts/make_fictional_packets.py
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parents[1] / "evals" / "packets"
CLINIC = "KLINIK DEMO KHABAR (FICTIONAL)"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for name in (["arialbd.ttf", "DejaVuSans-Bold.ttf"] if bold else ["arial.ttf", "DejaVuSans.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def packet(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, colour: str, lines: list[tuple[str, int, bool]]):
    draw.rounded_rectangle([x, y, x + w, y + h], radius=18, fill=colour, outline="#333333", width=3)
    ty = y + 24
    for text, size, bold in lines:
        draw.text((x + 24, ty), text, fill="#111111", font=font(size, bold))
        ty += int(size * 1.35)


def clinic_label(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, instruction: str):
    draw.rectangle([x, y, x + w, y + 120], fill="#ffffff", outline="#555555", width=2)
    draw.text((x + 14, y + 10), CLINIC, fill="#111111", font=font(20, True))
    draw.text((x + 14, y + 40), "Nama: PESAKIT DEMO    Tarikh: 01/09/2026", fill="#111111", font=font(18))
    draw.text((x + 14, y + 72), instruction, fill="#111111", font=font(18))


def canvas(w: int = 900, h: int = 640) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGB", (w, h), "#d9d2c5")  # a kitchen table
    return image, ImageDraw.Draw(image)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = []

    image, d = canvas()
    packet(d, 60, 60, 780, 360, "#e8f1fb", [("GLUCOFAKE 500", 54, True), ("Metformin Hydrochloride 500 mg", 32, False),
                                           ("60 film-coated tablets", 26, False), ("For oral use", 22, False)])
    clinic_label(d, 60, 450, 780, "Ambil 1 biji, 2 kali sehari selepas makan")
    image.save(OUT / "01-clear-metformin.png")
    manifest.append({"file": "01-clear-metformin.png", "expect_generics": ["metformin"], "expect_herbs": [], "unreadable": False,
                     "note": "Clear packet, fictional brand, generic printed"})

    image, d = canvas()
    packet(d, 60, 60, 780, 360, "#fdf1e3", [("Brand A 500mg", 54, True), ("Tablet", 30, False), ("30 tablets", 26, False)])
    clinic_label(d, 60, 450, 780, "1 tablet twice daily after food")
    image.save(OUT / "02-brand-only.png")
    manifest.append({"file": "02-brand-only.png", "expect_generics": ["metformin"], "expect_herbs": [], "unreadable": False,
                     "note": "Only a local brand name; the generic must come from our brand table, not the model"})

    image, d = canvas()
    packet(d, 60, 60, 780, 360, "#eef7ee", [("NORMOFAKE 5", 54, True), ("Amlodipine besylate", 32, False),
                                           ("equivalent to amlodipine 5 mg", 26, False), ("28 tablets", 22, False)])
    clinic_label(d, 60, 450, 780, "Ambil 1 biji sekali sehari")
    image.save(OUT / "03-amlodipine.png")
    manifest.append({"file": "03-amlodipine.png", "expect_generics": ["amlodipine"], "expect_herbs": [], "unreadable": False,
                     "note": "Salt name printed (besylate)"})

    image, d = canvas()
    packet(d, 60, 60, 780, 360, "#f3f8e6", [("JUS PERIA ASLI", 54, True), ("Bitter gourd (Momordica charantia)", 30, False),
                                           ("Minuman herba tradisional", 26, False), ("Produk contoh - FIKTIF", 22, False)])
    image.save(OUT / "04-herb-bitter-gourd.png")
    manifest.append({"file": "04-herb-bitter-gourd.png", "expect_generics": [], "expect_herbs": ["bitter gourd"], "unreadable": False,
                     "note": "A traditional remedy, which the list must keep as a herb"})

    image, d = canvas(1000, 640)
    packet(d, 40, 60, 440, 420, "#fbe9ec", [("CARDIOFAKE", 44, True), ("Aspirin 100 mg", 30, False), ("Gastro-resistant", 22, False)])
    packet(d, 520, 60, 440, 420, "#e9eefb", [("LIPOFAKE 20", 44, True), ("Simvastatin 20 mg", 30, False), ("28 tablets", 22, False)])
    image.save(OUT / "05-two-packets.png")
    manifest.append({"file": "05-two-packets.png", "expect_generics": ["aspirin", "simvastatin"], "expect_herbs": [], "unreadable": False,
                     "note": "Two products in one photo; each should be listed once"})

    image, d = canvas()
    packet(d, 120, 140, 660, 300, "#e8f1fb", [("GLUCOFAKE 500", 40, True), ("Metformin HCl 500 mg", 26, False)])
    image = image.rotate(14, expand=True, fillcolor="#d9d2c5").filter(ImageFilter.GaussianBlur(1.6))
    image = image.resize((image.width // 2, image.height // 2))
    image.save(OUT / "06-small-tilted-blurred.png")
    manifest.append({"file": "06-small-tilted-blurred.png", "expect_generics": ["metformin"], "expect_herbs": [], "unreadable": False,
                     "note": "Hard: small, tilted and slightly blurred, as a real phone photo can be"})

    random.seed(7)
    image, d = canvas()
    packet(d, 60, 60, 780, 360, "#e8f1fb", [("GLUCOFAKE 500", 54, True), ("Metformin Hydrochloride 500 mg", 32, False)])
    image = image.filter(ImageFilter.GaussianBlur(14))
    image.save(OUT / "07-unreadable.png")
    manifest.append({"file": "07-unreadable.png", "expect_generics": [], "expect_herbs": [], "unreadable": True,
                     "note": "Too blurred to read: the reader must say so rather than guess"})

    (OUT / "manifest.json").write_text(json.dumps({
        "_note": "Fictional packets drawn by scripts/make_fictional_packets.py. Brands, clinic and patient are invented.",
        "packets": manifest}, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(manifest)} packets to {OUT}")


if __name__ == "__main__":
    main()
