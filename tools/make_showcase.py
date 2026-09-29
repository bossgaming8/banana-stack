"""Turns the frames recorded by ShowcaseRecorder into the media used on the Modrinth page and in the README.

    ./gradlew runClientGameTest -Pshowcase
    python tools/make_showcase.py [screenshots folder]
"""
import colorsys
import math
import sys
from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SHOTS = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/run/clientGameTest/screenshots"
OUT = ROOT / "docs/assets/showcase"
GALLERY = ROOT / "build/gallery"
ICON = ROOT / "src/main/resources/assets/bananastack/icon.png"

CLIP_SIZE = (800, 450)
CLIP_FPS = 20
# The first frames show an empty plaza while the players join.
CLIP_FIRST_FRAME = 6

# Stills embedded in the README and the Modrinth description, by the name they get there.
WEB_STILLS = {
    "tall-a270": "tower",
    "tower-a270": "stack",
    "piggyback-a275": "piggyback",
    "shake-after-3": "shake-off",
}
# Everything that goes to the Modrinth gallery, in order.
GALLERY_STILLS = ["tall-a270", "tower-a255", "piggyback-a275", "shake-before", "shake-after-3", "tower-a285", "tall-a250"]


def clip():
    frames = sorted((SHOTS / "clip").glob("frame-*.png"))[CLIP_FIRST_FRAME:]
    if not frames:
        raise SystemExit("no clip frames; run the showcase recorder first")
    images = [Image.open(f).convert("RGB").resize(CLIP_SIZE, Image.LANCZOS) for f in frames]
    # Hold the last frame for a moment before the loop starts again.
    images += [images[-1]] * 12
    out = OUT / "stack-walk.webp"
    images[0].save(out, save_all=True, append_images=images[1:], duration=round(1000 / CLIP_FPS), loop=0, quality=62, method=6)
    print(out.name, len(frames), "frames,", out.stat().st_size // 1024, "KB")


def stills():
    for shot, name in WEB_STILLS.items():
        image = Image.open(SHOTS / f"showcase-{shot}.png").convert("RGB")
        web = image.resize((1280, round(image.height * 1280 / image.width)), Image.LANCZOS)
        web.save(OUT / f"{name}.webp", quality=86, method=6)
        print(name, "<-", shot)
    for index, shot in enumerate(GALLERY_STILLS, start=1):
        Image.open(SHOTS / f"showcase-{shot}.png").convert("RGB").save(GALLERY / f"{index:02d}-{shot}.png")


def cutout(shot, floor_y=870):
    """The players in a keyed shot, cut out of the magenta wall behind them."""
    image = Image.open(SHOTS / f"showcase-{shot}.png").convert("RGB")
    window = image.crop((image.width // 2 - 300, 0, image.width // 2 + 300, floor_y))
    pixels = window.load()
    width, height = window.size
    keep = [[False] * width for _ in range(height)]
    for y in range(height):
        for x in range(width):
            hue, saturation, value = colorsys.rgb_to_hsv(*(channel / 255 for channel in pixels[x, y]))
            wall = 0.76 < hue < 0.92 and saturation > 0.3 and value > 0.12
            floor = saturation < 0.12 and value > 0.55
            keep[y][x] = not (wall or floor)

    # Only big connected shapes are players; specks of floor at the edges are dropped.
    mask = Image.new("L", window.size, 0)
    seen = [[False] * width for _ in range(height)]
    for y in range(height):
        for x in range(width):
            if keep[y][x] and not seen[y][x]:
                seen[y][x] = True
                queue, shape = deque([(x, y)]), []
                while queue:
                    cx, cy = queue.popleft()
                    shape.append((cx, cy))
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < width and 0 <= ny < height and keep[ny][nx] and not seen[ny][nx]:
                            seen[ny][nx] = True
                            queue.append((nx, ny))
                if len(shape) > 1500:
                    for point in shape:
                        mask.putpixel(point, 255)
    players = window.convert("RGBA")
    players.putalpha(mask)
    return players.crop(mask.getbbox())


def sunburst(size):
    """Banana yellow fading to peel orange, with faint rays."""
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            dx, dy = x - size / 2, y - size * 0.55
            t = min(1.0, math.hypot(dx, dy) / (size * 0.72))
            ray = 1.0 + 0.05 * (0.5 + 0.5 * math.cos(math.atan2(dy, dx) * 14)) * (1.0 - t * 0.5)
            base = (255 + (246 - 255) * t, 224 + (171 - 224) * t, 110 + (26 - 110) * t)
            pixels[x, y] = tuple(min(255, int(channel * ray)) for channel in base) + (255,)
    return image


def icon():
    """A three-player stack as a sticker on a banana sunburst."""
    size = 512
    players = cutout("keyed-3-a290")
    height = int(size * 0.88)
    players = players.resize((max(1, round(players.width * height / players.height)), height), Image.LANCZOS)

    grow = size // 80
    pad = grow + 2
    sticker = Image.new("RGBA", (players.width + pad * 2, players.height + pad * 2))
    outline = Image.new("RGBA", players.size, (43, 27, 14, 255))
    outline.putalpha(players.split()[3].filter(ImageFilter.MaxFilter(grow * 2 + 1)))
    sticker.alpha_composite(outline, (pad, pad))
    sticker.alpha_composite(players, (pad, pad))
    shadow = Image.new("RGBA", sticker.size, (120, 60, 0, 0))
    shadow.putalpha(sticker.split()[3].filter(ImageFilter.GaussianBlur(size / 60)).point(lambda value: int(value * 0.45)))

    canvas = sunburst(size)
    x, y = (size - sticker.width) // 2, size - sticker.height - size // 50
    canvas.alpha_composite(shadow, (x + size // 90, y + size // 70))
    canvas.alpha_composite(sticker, (x, y))
    canvas.save(GALLERY / "icon-512.png")
    canvas.resize((256, 256), Image.LANCZOS).save(ICON)
    print("icon", ICON.relative_to(ROOT))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    GALLERY.mkdir(parents=True, exist_ok=True)
    ICON.parent.mkdir(parents=True, exist_ok=True)
    stills()
    icon()
    clip()


if __name__ == "__main__":
    main()
