# -*- coding: utf-8 -*-
"""Рисует 6distinct-иконок Sweetgram и раскладывает их в legacy-mipmap PNG.

Вектора для adaptive-icon пишутся руками в тех же координатах (108x108),
этот скрипт — источник правды по геометрии и заодно генератор PNG для
устройств старше Android 8.

Каждая иконка: свой цвет фона + белая фигура.
  default  розовый градиент + самолётик (sweetgram_plane)
  icon_6   rose    два сердца
  icon_4   sand    самолётик со следом из сердечек
  icon_3   lavender сердце и искра
  icon_5   sea     кольцо с самолётиком
  icon_2   night   месяц и сердце
"""
import math
import os
from PIL import Image, ImageDraw

S = 108          # нормированный холст (как во векторах)
SS = 4           # суперсэмплинг

# --- утилиты -------------------------------------------------------------

def bezier(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(1, n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt**2*t*p1[0] + 3*mt*t**2*p2[0] + t**3*p3[0]
        y = mt**3*p0[1] + 3*mt**2*t*p1[1] + 3*mt*t**2*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

# Сплошное сердце из 24-единичной сетки (тот же path, что в sweetgram_monochrome).
HEART_SEGS = []  # список ("L", (x,y)) | ("C", p1, p2, p3)
HEART_SEGS.append(("L", (9.23, 19.54)))
HEART_SEGS.append(("C", (4.66, 15.39), (1.75, 12.76), (1.75, 9.5)))
HEART_SEGS.append(("C", (1.75, 6.87), (3.81, 4.81), (6.44, 4.81)))
HEART_SEGS.append(("C", (7.38, 4.81), (8.27, 5.09), (9.03, 5.59)))
HEART_SEGS.append(("L", (9.62, 5.95)))
HEART_SEGS.append(("L", (10.21, 5.58)))
HEART_SEGS.append(("C", (10.97, 5.09), (11.86, 4.81), (12.80, 4.81)))
HEART_SEGS.append(("C", (15.43, 4.81), (17.49, 6.87), (17.49, 9.5)))
HEART_SEGS.append(("C", (17.49, 12.76), (14.58, 15.39), (10.01, 19.54)))
HEART_SEGS.append(("L", (10.4, 20.6)))
HEART_START = (10.4, 20.6)
HEART_CENTER = (9.62, 12.7)

def heart_polygon(cx, cy, width):
    s = width / (17.49 - 1.75)
    tx, ty = cx - HEART_CENTER[0]*s, cy - HEART_CENTER[1]*s
    pts = [(HEART_START[0]*s + tx, HEART_START[1]*s + ty)]
    for seg in HEART_SEGS:
        if seg[0] == "L":
            pts.append((seg[1][0]*s + tx, seg[1][1]*s + ty))
        else:
            p1 = [(seg[1][0]*s + tx, seg[1][1]*s + ty)]
            p2 = [(seg[2][0]*s + tx, seg[2][1]*s + ty)]
            p3 = [(seg[3][0]*s + tx, seg[3][1]*s + ty)]
            pts.extend(bezier(pts[-1], p1[0], p2[0], p3[0]))
    return pts

# Самолётик sweetgram_plane (те же точки, 108-сетка).
PLANE = [(54, 29.88), (27.55, 67.46), (52.05, 59.11),
         (54, 64.12), (55.95, 59.11), (80.45, 67.46)]
PLANE_CENTER = (54.0, 48.67)

def plane_polygon(cx, cy, scale):
    tx, ty = cx - PLANE_CENTER[0]*scale, cy - PLANE_CENTER[1]*scale
    return [(x*scale + tx, y*scale + ty) for x, y in PLANE]

def scale_pts(pts, s, cx, cy):
    """Масштабирует полигон вокруг его центра (cx, cy)."""
    return [(cx + (x - cx)*s, cy + (y - cy)*s) for x, y in pts]

def star_polygon(cx, cy, r_out, r_in):
    pts = []
    for i in range(8):
        ang = -math.pi/2 + i*math.pi/4
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + r*math.cos(ang), cy + r*math.sin(ang)))
    return pts

# --- фоны ----------------------------------------------------------------

def gradient(draw, box, top, bottom):
    x0, y0, x1, y1 = box
    h = y1 - y0
    c0 = tuple(int(top[i:i+2], 16) for i in (1, 3, 5))
    c1 = tuple(int(bottom[i:i+2], 16) for i in (1, 3, 5))
    for y in range(int(y0), int(y1)):
        t = (y - y0) / max(1, h)
        col = tuple(int(a + (b - a)*t) for a, b in zip(c0, c1))
        draw.line([(x0, y), (x1, y)], fill=col)

PINK_TOP, PINK_BOTTOM = "#FFC0CB", "#E59CB8"

BACKGROUNDS = {
    "default": None,            # розовый градиент
    "icon_2": "#2A2D33",
    "icon_3": "#B7A8E0",
    "icon_4": "#E7C88C",
    "icon_5": "#7FC4D6",
    "icon_6": "#E09A9A",
}

WHITE = (255, 255, 255)

# --- рисунок каждой иконки (в нормированных координатах) ------------------

def draw_default(d, u):
    gradient(d, u(0, 0, S, S), PINK_TOP, PINK_BOTTOM)
    d.polygon([u(x, y) for x, y in plane_polygon(54, 52, 0.95)], fill=WHITE)

def draw_hearts(d, u):
    d.rectangle(u("bg"), fill=(224, 154, 154))
    d.polygon([u(x, y) for x, y in heart_polygon(52, 56, 44)], fill=WHITE)
    d.polygon([u(x, y) for x, y in heart_polygon(74, 35, 15)], fill=WHITE)

def draw_trail(d, u):
    d.rectangle(u("bg"), fill=(231, 200, 140))
    d.polygon([u(x, y) for x, y in plane_polygon(42, 58, 0.72)], fill=WHITE)
    for cx, cy, w in ((68, 40, 11), (77.5, 30.5, 8), (84.5, 23, 5.6)):
        d.polygon([u(x, y) for x, y in heart_polygon(cx, cy, w)], fill=WHITE)

def draw_sparkle(d, u):
    d.rectangle(u("bg"), fill=(183, 168, 224))
    d.polygon([u(x, y) for x, y in heart_polygon(54, 55, 46)], fill=WHITE)
    d.polygon([u(x, y) for x, y in star_polygon(75, 31, 9.5, 3.4)], fill=WHITE)

def draw_ring(d, u):
    d.rectangle(u("bg"), fill=(127, 196, 214))
    d.ellipse(u.circle(54, 54, 28), fill=WHITE)
    d.ellipse(u.circle(54, 54, 23), fill=(127, 196, 214))
    d.polygon([u(x, y) for x, y in plane_polygon(54, 54, 0.58)], fill=WHITE)

def draw_night(d, u):
    d.rectangle(u("bg"), fill=(42, 45, 51))
    d.ellipse(u.circle(52, 54, 27), fill=WHITE)
    d.ellipse(u.circle(64, 44, 23), fill=(42, 45, 51))
    d.polygon([u(x, y) for x, y in heart_polygon(78, 72, 13)], fill=WHITE)

DESIGNS = {
    "default": draw_default,
    "icon_6": draw_hearts,
    "icon_4": draw_trail,
    "icon_3": draw_sparkle,
    "icon_5": draw_ring,
    "icon_2": draw_night,
}

# --- рендер ---------------------------------------------------------------

class U:
    """Масштабирует нормированные координаты в пиксели холста."""
    def __init__(self, size):
        self.k = size / S
        self.size = size

    def __call__(self, *args):
        if args and args[0] == "bg":
            return (0, 0, self.size, self.size)
        return tuple(v * self.k for v in args)

    def circle(self, cx, cy, r):
        return ((cx - r)*self.k, (cy - r)*self.k, (cx + r)*self.k, (cy + r)*self.k)

def render(design, size, round_mask):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    big = size * SS
    work = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(work)
    u = U(big)
    DESIGNS[design](d, u)
    work = work.resize((size, size), Image.LANCZOS)
    # Маска: круг для round, скруглённый квадрат для обычной.
    mask = Image.new("L", (size*SS, size*SS), 0)
    md = ImageDraw.Draw(mask)
    if round_mask:
        md.ellipse((0, 0, size*SS - 1, size*SS - 1), fill=255)
    else:
        r = int(size*SS*0.21)
        md.rounded_rectangle((0, 0, size*SS - 1, size*SS - 1), radius=r, fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    canvas.paste(work, (0, 0), mask)
    return canvas

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

def export(repo):
    main_res = os.path.join(repo, "TMessagesProj", "src", "main", "res")
    sa_res = os.path.join(repo, "TMessagesProj_AppStandalone", "src", "main", "res")
    for name in DENSITIES:
        size = DENSITIES[name]
        sq = render("default", size, False)
        rd = render("default", size, True)
        sq.save(os.path.join(main_res, f"mipmap-{name}", "ic_launcher.png"))
        rd.save(os.path.join(main_res, f"mipmap-{name}", "ic_launcher_round.png"))
        sq.save(os.path.join(sa_res, f"mipmap-{name}", "ic_launcher_sa.png"))
        for icon in ("icon_2", "icon_3", "icon_4", "icon_5", "icon_6"):
            sq = render(icon, size, False)
            rd = render(icon, size, True)
            sq.save(os.path.join(main_res, f"mipmap-{name}", f"{icon}_launcher.png"))
            rd.save(os.path.join(main_res, f"mipmap-{name}", f"{icon}_launcher_round.png"))
            sq.save(os.path.join(sa_res, f"mipmap-{name}", f"{icon}_launcher_sa.png"))

def preview(path):
    size = 160
    sheet = Image.new("RGBA", (size*6 + 70, size + 30), (250, 245, 248, 255))
    for i, name in enumerate(["default", "icon_6", "icon_4", "icon_3", "icon_5", "icon_2"]):
        img = render(name, size, True)
        sheet.paste(img, (10 + i*(size + 10), 15), img)
    sheet.save(path)

if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    export(repo)
    import sys
    if len(sys.argv) > 1:
        preview(sys.argv[1])
    print("done")
