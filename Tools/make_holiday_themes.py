# -*- coding: utf-8 -*-
"""
Генератор праздничных тем Sweetgram.

Берёт night.attheme как тёмную базу, перекрашивает её в палитру праздника
(плавный поворот оттенка всех цветов — контрастная структура темы при этом
сохраняется) и дописывает свои обои: градиент ключами chat_wallpaper_* и
картинка с праздничным узором вложением после строки WPS, как умеет формат
.attheme.

Запуск из корня TMessagesProj/src/main/assets:
    python ../../../../Tools/make_holiday_themes.py
"""
import colorsys
import io
import math
import os
import random
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "TMessagesProj", "src", "main", "assets")
if not os.path.isdir(ASSETS):
    ASSETS = os.path.join(os.path.dirname(HERE), "TMessagesProj", "src", "main", "assets")
BASE = os.path.join(ASSETS, "night.attheme")

W, H = 1080, 1920


def parse_base():
    colors = {}
    with open(BASE, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if "=" in line and not line.startswith("WPS"):
                key, _, value = line.partition("=")
                colors[key.strip()] = value.strip()
    return colors


def unpack(argb):
    a = (argb >> 24) & 0xFF
    r = (argb >> 16) & 0xFF
    g = (argb >> 8) & 0xFF
    b = argb & 0xFF
    return a, r, g, b


def pack(a, r, g, b):
    return (a << 24) | (r << 16) | (g << 8) | b


def to_int(value):
    value = value.strip()
    if value.startswith("0x") or value.startswith("0X"):
        return int(value[2:], 16) & 0xFFFFFFFF
    if value.startswith("#"):
        hexv = value[1:]
        if len(hexv) == 6:
            return int(hexv, 16) | 0xFF000000
        if len(hexv) == 8:
            return int(hexv, 16) & 0xFFFFFFFF
    try:
        return int(value) & 0xFFFFFFFF
    except ValueError:
        return None


def to_str(value):
    return "0x%08x" % (value & 0xFFFFFFFF)



def rgba(argb, alpha=None):
    """ARGB-число в кортеж (r, g, b, a) для PIL."""
    a = (argb >> 24) & 0xFF
    r = (argb >> 16) & 0xFF
    g = (argb >> 8) & 0xFF
    b = argb & 0xFF
    return (r, g, b, alpha if alpha is not None else a)

def shift_color(argb, target_hue, strength, sat_mul=1.0, val_mul=1.0):
    """Поворачивает оттенок к целевому, сохраняя светлоту и насыщенность."""
    a, r, g, b = unpack(argb)
    if a == 0:
        return argb
    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    hue_deg = h * 360.0
    # Серые почти не крутим: нейтральный серый должен остаться нейтральным.
    local = strength * (1.0 if s > 0.08 else 0.25)
    dh = ((target_hue - hue_deg + 540.0) % 360.0) - 180.0
    hue = (hue_deg + dh * local) % 360.0
    s = max(0.0, min(1.0, s * sat_mul))
    v = max(0.0, min(1.0, v * val_mul))
    r2, g2, b2 = colorsys.hsv_to_rgb(hue / 360.0, s, v)
    return pack(a, int(r2 * 255 + 0.5), int(g2 * 255 + 0.5), int(b2 * 255 + 0.5))


def recolor(base, target_hue, strength, sat_mul, val_mul, overrides):
    out = {}
    for key, value in base.items():
        parsed = to_int(value)
        if parsed is None or key == "wallpaperFileOffset":
            continue
        out[key] = shift_color(parsed, target_hue, strength, sat_mul, val_mul)
    for key, color in overrides.items():
        out[key] = color & 0xFFFFFFFF
    return out


# Праздничные градиенты обоев: base → to1 → to2 → to3.
GRADIENTS = {
    "sept1":   (0xff20343a, 0xff28504a, 0xff3b6b52, 0xff5d7a3a),
    "halloween": (0xff191026, 0xff2a1440, 0xff431c46, 0xff5c2a1e),
    "newyear": (0xff0e1b33, 0xff16294f, 0xff1d3a66, 0xff274f80),
    "defender": (0xff151a20, 0xff20262e, 0xff2c343e, 0xff3b454f),
    "women":   (0xff2c1220, 0xff471a30, 0xff612238, 0xff7c3050),
}


def apply_gradient(colors, name):
    base, to1, to2, to3 = GRADIENTS[name]
    colors["chat_wallpaper"] = base
    colors["chat_wallpaper_gradient_to"] = to1
    colors["key_chat_wallpaper_gradient_to2"] = to2
    colors["key_chat_wallpaper_gradient_to3"] = to3
    colors["chat_wallpaper_gradient_rotation"] = 45


def gradient_preview(name, w=W, h=H):
    """Вертикальный градиент, теми же цветами, что и ключи темы."""
    stops = GRADIENTS[name]
    img = Image.new("RGB", (w, h))
    px = img.load()
    rows = stops + (stops[-1],)
    for y in range(h):
        t = y / float(h - 1)
        # 4 опорных цвета через весь экран.
        pos = t * 3.0
        i = min(int(pos), 2)
        frac = pos - i
        c1, c2 = rows[i], rows[i + 1]
        r = int(((c1 >> 16) & 0xFF) * (1 - frac) + ((c2 >> 16) & 0xFF) * frac)
        g = int(((c1 >> 8) & 0xFF) * (1 - frac) + ((c2 >> 8) & 0xFF) * frac)
        b = int((c1 & 0xFF) * (1 - frac) + (c2 & 0xFF) * frac)
        for x in range(w):
            px[x, y] = (r, g, b)
    return img.convert("RGBA")


from PIL import Image, ImageDraw  # noqa: E402


def scatter(rng, count, area_h):
    return [(rng.uniform(0, W), rng.uniform(0, area_h)) for _ in range(count)]


def draw_leaves(draw, overlay, rng):
    for x, y in scatter(rng, 26, H):
        size = rng.uniform(26, 64)
        ang = rng.uniform(0, math.tau)
        color = rgba(rng.choice([0xffd08a3a, 0xffe0a44a, 0xffb5762e, 0xff9c5a24]), rng.randint(70, 95))
        leaf = Image.new("RGBA", (int(size * 2), int(size * 2)), (0, 0, 0, 0))
        ld = ImageDraw.Draw(leaf)
        ld.ellipse([size * 0.5, size * 0.7, size * 1.5, size * 1.3], fill=color)
        ld.line([size, size * 0.55, size, size * 1.45], fill=(90, 60, 20, 110), width=3)
        leaf = leaf.rotate(math.degrees(ang), expand=False)
        overlay.alpha_composite(leaf, (int(x - size), int(y - size)))


def draw_halloween(overlay, rng):
    # Луна.
    moon = Image.new("RGBA", (420, 420), (0, 0, 0, 0))
    md = ImageDraw.Draw(moon)
    md.ellipse([30, 30, 390, 390], fill=(255, 223, 168, 235))
    md.ellipse([90, 70, 150, 130], fill=(216, 184, 106, 120))
    md.ellipse([250, 210, 320, 280], fill=(216, 184, 106, 110))
    overlay.alpha_composite(moon, (W - 430, 120))
    # Летучие мыши.
    for x, y in scatter(rng, 14, H * 0.75):
        s = rng.uniform(24, 58)
        bat = Image.new("RGBA", (int(s * 2.6), int(s * 1.4)), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bat)
        col = (30, 18, 46, 235)
        bd.polygon([(0.3 * s, 0), (0.7 * s, 0.35 * s), (1.3 * s, 0), (1.6 * s, 0.5 * s),
                    (1.3 * s, 0.42 * s), (1.0 * s, 0.75 * s), (0.65 * s, 0.55 * s),
                    (0.35 * s, 0.8 * s), (0.05 * s, 0.45 * s)], fill=col)
        bd.ellipse([0.85 * s, 0.15 * s, 1.15 * s, 0.6 * s], fill=col)
        overlay.alpha_composite(bat, (int(x - s), int(y)))
    # Тыквы.
    for x, y in scatter(rng, 7, H):
        s = rng.uniform(30, 60)
        pum = Image.new("RGBA", (int(s * 2), int(s * 1.6)), (0, 0, 0, 0))
        pd = ImageDraw.Draw(pum)
        pd.ellipse([s * 0.2, s * 0.35, s * 1.8, s * 1.55], fill=(255, 139, 42, 200))
        pd.ellipse([s * 0.55, s * 0.35, s * 1.1, s * 1.55], fill=(255, 156, 60, 170))
        pd.ellipse([s * 1.0, s * 0.35, s * 1.5, s * 1.55], fill=(255, 139, 42, 170))
        pd.rectangle([s * 0.95, s * 0.05, s * 1.06, s * 0.45], fill=(74, 107, 42, 220))
        overlay.alpha_composite(pum, (int(x - s), int(y - s)))


def draw_snow(draw, overlay, rng):
    for x, y in scatter(rng, 40, H):
        s = rng.uniform(10, 34)
        alpha = rng.randint(70, 170)
        col = (220, 235, 255, alpha)
        for k in range(6):
            ang = k * math.pi / 3.0
            dx, dy = math.cos(ang) * s, math.sin(ang) * s
            draw.line([x, y, x + dx, y + dy], fill=col, width=max(2, int(s / 7)))
            # Веточки.
            for t in (0.55, 0.8):
                bx, by = x + dx * t, y + dy * t
                for side in (-1, 1):
                    ang2 = ang + side * 0.6
                    draw.line([bx, by, bx + math.cos(ang2) * s * 0.3,
                               by + math.sin(ang2) * s * 0.3], fill=col,
                              width=max(1, int(s / 10)))
    for x, y in scatter(rng, 10, H):
        star(overlay, x, y, rng.uniform(8, 18), (255, 230, 150, rng.randint(150, 230)))


def star(overlay, x, y, s, color):
    pts = []
    for i in range(10):
        r = s if i % 2 == 0 else s * 0.45
        ang = -math.pi / 2 + i * math.pi / 5
        pts.append((x + math.cos(ang) * r, y + math.sin(ang) * r))
    d = ImageDraw.Draw(overlay)
    d.polygon(pts, fill=color)


def draw_stars(draw, overlay, rng):
    for x, y in scatter(rng, 30, H):
        star(overlay, x, y, rng.uniform(9, 30),
             tuple(list(rng.choice([(200, 70, 70), (222, 120, 90), (190, 60, 60)])) + [rng.randint(120, 220)]))
    for x, y in scatter(rng, 12, H):
        star(overlay, x, y, rng.uniform(6, 12), (240, 220, 160, rng.randint(140, 220)))


def draw_tulips(draw, overlay, rng):
    for x, y in scatter(rng, 26, H):
        s = rng.uniform(30, 64)
        ang = rng.uniform(-0.5, 0.5)
        tul = Image.new("RGBA", (int(s * 1.6), int(s * 2.4)), (0, 0, 0, 0))
        td = ImageDraw.Draw(tul)
        col = rgba(rng.choice([0xff9ab0, 0xff7f9c, 0xffb78ba6, 0xfff4a7c3]), rng.randint(175, 210))
        col2 = rgba(0xffd7e0f0, rng.randint(160, 200))
        # Бутон-силуэт: два боковых лепестка и средний между ними.
        bud = [(0.30, 1.00), (0.22, 0.45), (0.34, 0.14), (0.52, 0.42),
               (0.72, 0.10), (0.90, 0.42), (1.06, 0.16), (1.16, 0.50), (1.02, 1.00)]
        td.polygon([(px_ * s, py_ * s) for px_, py_ in bud], fill=col)
        # Стебель и пара листьев.
        td.line([s * 0.66, s * 0.95, s * 0.62, s * 2.3], fill=(60, 110, 70, 210), width=max(2, int(s * 0.08)))
        leaf = [(0.64, 1.5), (0.98, 1.35), (1.16, 1.6), (0.85, 1.85)]
        td.polygon([(px_ * s, py_ * s) for px_ in [0.64, 0.98, 1.16, 0.85] for py_ in []], fill=None) if False else None
        td.polygon([(0.66 * s, 1.45 * s), (1.05 * s, 1.3 * s), (1.2 * s, 1.62 * s), (0.8 * s, 1.8 * s)], fill=(70, 130, 80, 190))
        td.polygon([(0.6 * s, 1.9 * s), (0.35 * s, 1.75 * s), (0.24 * s, 2.0 * s), (0.55 * s, 2.15 * s)], fill=(60, 118, 70, 190))
        tul = tul.rotate(math.degrees(ang), expand=False)
        overlay.alpha_composite(tul, (int(x - s), int(y - s)))


THEMES = [
    {
        "name": "1 сентября",
        "file": "sweetgram_sept1.attheme",
        "hue": 130.0, "strength": 0.4, "sat": 0.95, "val": 1.0,
        "wall": "sept1", "draw": lambda d, o, r: draw_leaves(d, o, r),
    },
    {
        "name": "Хеллоуин",
        "file": "sweetgram_halloween.attheme",
        "hue": 272.0, "strength": 0.5, "sat": 1.05, "val": 0.95,
        "wall": "halloween", "draw": lambda d, o, r: draw_halloween(o, r),
    },
    {
        "name": "Новый год",
        "file": "sweetgram_newyear.attheme",
        "hue": 215.0, "strength": 0.55, "sat": 0.9, "val": 0.98,
        "wall": "newyear", "draw": lambda d, o, r: draw_snow(d, o, r),
    },
    {
        "name": "23 февраля",
        "file": "sweetgram_defender.attheme",
        "hue": 210.0, "strength": 0.35, "sat": 0.6, "val": 0.96,
        "wall": "defender", "draw": lambda d, o, r: draw_stars(d, o, r),
    },
    {
        "name": "8 марта",
        "file": "sweetgram_women.attheme",
        "hue": 340.0, "strength": 0.5, "sat": 1.0, "val": 1.02,
        "wall": "women", "draw": lambda d, o, r: draw_tulips(d, o, r),
    },
]


def make_wallpaper(theme, seed):
    rng = random.Random(seed)
    img = gradient_preview(theme["wall"])
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    theme["draw"](draw, overlay, rng)
    img.alpha_composite(overlay)
    out = io.BytesIO()
    img.convert("RGB").save(out, "JPEG", quality=87)
    return out.getvalue()


def main():
    base = parse_base()
    for i, theme in enumerate(THEMES):
        colors = recolor(base, theme["hue"], theme["strength"], theme["sat"], theme["val"], {})
        apply_gradient(colors, theme["wall"])
        lines = []
        for k, v in sorted(colors.items()):
            lines.append("%s=%s" % (k, str(v) if k == "chat_wallpaper_gradient_rotation" else to_str(v)))
        lines.append("wallpaperFileOffset=-1")
        jpeg = make_wallpaper(theme, 42 + i)
        data = ("\n".join(lines) + "\nWPS\n").encode("utf-8") + jpeg
        path = os.path.join(ASSETS, theme["file"])
        with open(path, "wb") as f:
            f.write(data)
        print("wrote %s (%d bytes)" % (path, len(data)))


if __name__ == "__main__":
    main()
