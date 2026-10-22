"""Generate the launcher icon (Android adaptive icon layers + docs/logo/icon.svg preview) from one shape list.

Run from the repo root: python3 docs/logo/gen.py .
"""
import sys, os
OUT = sys.argv[1]

def rrect(x, y, w, h, r):
    return (f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} "
            f"H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z")
def circle(cx, cy, r):
    return f"M{cx-r},{cy} A{r},{r} 0 1 0 {cx+r},{cy} A{r},{r} 0 1 0 {cx-r},{cy} Z"

def lin(x1, y1, x2, y2, *stops):
    return ("lin", x1, y1, x2, y2, stops)

def P(d, fill=None, fa=1, stroke=None, sw=0, sa=1):
    return ("path", d, fill, fa, stroke, sw, sa)
def G(children, tx=0, ty=0, s=1, rot=0, px=0, py=0):
    return ("group", children, tx, ty, s, rot, px, py)

WHITE = "#FFFFFF"
RIM = dict(stroke=WHITE, sw=1.2, sa=.6)
glass = lambda x1, y1, x2, y2: lin(x1, y1, x2, y2, (0, WHITE, .42), (1, WHITE, .12))
blue = lambda x1, y1, x2, y2: lin(x1, y1, x2, y2, (0, "#5AB0FF", 1), (1, "#0A6BFF", 1))

background = [
    P("M0,0 H108 V108 H0 Z", lin(0, 0, 108, 108, (0, "#3A3A3E", 1), (1, "#121214", 1))),
    P("M0,0 H108 V108 H0 Z", lin(0, 0, 0, 108, (0, WHITE, .22), (.5, WHITE, 0))),
]

scallops = " ".join(f"M{x},{y} q2,1.8 4,0" for x, y in
                    [(1, -30), (5, -30), (-1, -25), (3, -25), (7, -25), (1, -20), (5, -20), (9, -20), (3, -15), (7, -15)])

parrot = G([
    G([
        P("M-6,-9 C-9,-3 -11,4 -11,11 L-7.5,9 L-5,12 L-3.5,5 C-3,1 -2.5,-3 -2,-6 Z", "#D7262B"),
        P("M-6.5,-2 C-8,2 -8.6,6 -8.4,9.6", stroke="#A31B1F", sw=.9),
        P("M-3,-38 C-12,-32 -14,-16 -9,-6 C-6,-1 3,-1 7,-5 C14.5,-12 16,-27 9,-36 Z", "#A4ADB8"),
        P("M1,-34 C10.5,-33 15.5,-24 12.5,-12 C10,-6 6,-3 1,-2.5 C4,-10 4,-24 1,-34 Z", "#BCC3CC"),
        P(scallops, stroke="#8D96A1", sw=.75, sa=.75),
        P("M-1,-33 C-9,-31 -12.5,-20 -10.5,-8 L-8.5,-1 C-4,-6 0,-14 1,-22 C2,-28 1,-32 -1,-33 Z", "#717A86"),
        P("M-2,-28 C-6,-25 -7.5,-19 -7,-14 M0,-24 C-3,-20 -4,-15 -4,-11", stroke="#8F98A3", sw=.8),
        P("M-10.5,-8 L-8.5,-1 C-6.5,-3.5 -4.8,-6.5 -3.6,-9.5 C-6,-9.5 -8.5,-9 -10.5,-8 Z", "#4E5561"),
        P(circle(4.3, -42, 10.2), "#A4ADB8"),
        P("M-5,-44 C-3,-50 4,-53 10,-49.5 C6,-50 0,-49 -5,-44 Z", "#949DA8"),
        P("M3.5,-44.5 C5,-48.5 10.5,-48.5 12.5,-45 C13.5,-42 13,-38.5 11,-37 C8,-36.5 4.5,-38.5 3.5,-44.5 Z", "#EEF1F4"),
        P("M11.5,-47 C18.5,-49.5 23,-44 20.5,-36 C19.6,-38.4 17.6,-40.2 14.6,-39.8 C12.6,-40.4 11.4,-43 11.5,-47 Z", "#232428"),
        P("M13.5,-46.6 C17.5,-47.4 20.4,-45 20.8,-41.5", stroke="#55585F", sw=.8),
        P("M12.4,-39.6 C15.2,-39.4 17.2,-38.4 17.6,-36.6 C15.6,-35.4 13.4,-36 12.4,-37.2 Z", "#3A3C42"),
        P(circle(9, -43.6, 2.7), "#F2E3A0"),
        P(circle(9.3, -43.5, 1.4), "#121214"),
        P(circle(9.9, -44.2, .55), WHITE),
        P("M6.1,-45.6 Q9,-47.6 12,-45.8", stroke="#6E7782", sw=1.2),
    ], rot=-4),
    P("M-4.5,-1.5 C-2.5,-0.5 -1,0.5 -0.5,2.6 M-1.5,-1.5 C1,-0.8 2.6,0.6 2.8,2.8 M2,-2 C4,-1.6 5.6,-0.2 5.8,2.4", stroke="#5E6570", sw=1.7),
], tx=53.5, ty=51, s=.66)

foreground = [G([
    P(rrect(32, 44, 44, 12, 6), WHITE, .14),
    P(rrect(28, 49, 52, 12, 6), WHITE, .22),
    P(rrect(19.5, 62, 5, 12, 2.5), WHITE, .35),
    P(rrect(83.5, 62, 5, 12, 2.5), WHITE, .35),
    P("M35,55 H73 A11,11 0 0 1 84,66 V72 A11,11 0 0 1 73,83 H41 L31,89.5 L33,82.7 A11,11 0 0 1 24,72 V66 A11,11 0 0 1 35,55 Z",
      glass(24, 55, 60, 89.5), **RIM),
    P(rrect(35, 62, 8, 10, 4), WHITE, .85),
    P(rrect(65, 60, 8, 12, 4), WHITE, .85),
    P(rrect(50, 51, 8, 21, 3), blue(50, 51, 58, 72)),
    parrot,
], s=.84, px=54, py=54)]

# ---------- Android vector drawable ----------
def argb(c, a):
    return "#%02X%s" % (round(a * 255), c[1:].upper())

def vd(node, ind):
    sp = " " * ind
    if node[0] == "group":
        _, ch, tx, ty, s, rot, px, py = node
        attrs = []
        if tx or ty: attrs += [f'android:translateX="{tx}"', f'android:translateY="{ty}"']
        if s != 1: attrs += [f'android:scaleX="{s}"', f'android:scaleY="{s}"']
        if rot: attrs.append(f'android:rotation="{rot}"')
        if px or py: attrs += [f'android:pivotX="{px}"', f'android:pivotY="{py}"']
        a = ("\n" + sp + "    ").join(attrs)
        return f"{sp}<group\n{sp}    {a}>\n" + "".join(vd(c, ind + 4) for c in ch) + f"{sp}</group>\n"
    _, d, fill, fa, stroke, sw, sa = node
    out = f'{sp}<path\n{sp}    android:pathData="{d}"'
    inner = ""
    if isinstance(fill, tuple):
        _, x1, y1, x2, y2, stops = fill
        inner = (f'{sp}    <aapt:attr name="android:fillColor">\n'
                 f'{sp}        <gradient\n{sp}            android:type="linear"\n'
                 f'{sp}            android:startX="{x1}"\n{sp}            android:startY="{y1}"\n'
                 f'{sp}            android:endX="{x2}"\n{sp}            android:endY="{y2}">\n'
                 + "".join(f'{sp}            <item android:offset="{o}" android:color="{argb(c, al)}" />\n' for o, c, al in stops)
                 + f'{sp}        </gradient>\n{sp}    </aapt:attr>\n')
    elif fill:
        out += f'\n{sp}    android:fillColor="{fill}"'
        if fa != 1: out += f'\n{sp}    android:fillAlpha="{fa}"'
    if stroke:
        out += (f'\n{sp}    android:strokeColor="{stroke}"\n{sp}    android:strokeWidth="{sw}"'
                f'\n{sp}    android:strokeLineCap="round"\n{sp}    android:strokeLineJoin="round"')
        if sa != 1: out += f'\n{sp}    android:strokeAlpha="{sa}"'
    return out + (f">\n{inner}{sp}</path>\n" if inner else " />\n")

def vector(nodes, comment):
    body = "".join(vd(n, 4) for n in nodes)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated from docs/logo/gen.py; edit there and re-run. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}</vector>
'''

# ---------- SVG preview ----------
gid = [0]
def svg(node, defs):
    if node[0] == "group":
        _, ch, tx, ty, s, rot, px, py = node
        t = f"translate({tx} {ty}) " if (tx or ty) else ""
        if s != 1: t += f"translate({px} {py}) scale({s}) translate({-px} {-py}) "
        if rot: t += f"rotate({rot} {px} {py})"
        return f'<g transform="{t.strip()}">' + "".join(svg(c, defs) for c in ch) + "</g>"
    _, d, fill, fa, stroke, sw, sa = node
    a = f'd="{d}"'
    if isinstance(fill, tuple):
        gid[0] += 1; i = f"g{gid[0]}"
        _, x1, y1, x2, y2, stops = fill
        defs.append(f'<linearGradient id="{i}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
                    + "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{al}"/>' for o, c, al in stops) + "</linearGradient>")
        a += f' fill="url(#{i})"'
    else:
        a += f' fill="{fill or "none"}" fill-opacity="{fa}"'
    if stroke:
        a += f' stroke="{stroke}" stroke-width="{sw}" stroke-opacity="{sa}" stroke-linecap="round" stroke-linejoin="round"'
    return f"<path {a}/>"

defs = []
art = "".join(svg(n, defs) for n in background + foreground)
os.makedirs(f"{OUT}/app/src/main/res/drawable", exist_ok=True)
os.makedirs(f"{OUT}/app/src/main/res/mipmap-anydpi-v26", exist_ok=True)
os.makedirs(f"{OUT}/docs/logo", exist_ok=True)
open(f"{OUT}/docs/logo/icon.svg", "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432"><defs>{"".join(defs)}</defs>{art}</svg>\n')
open(f"{OUT}/app/src/main/res/drawable/ic_launcher_background.xml", "w").write(
    vector(background, "Launcher icon back layer: graphite gradient with a soft top sheen."))
open(f"{OUT}/app/src/main/res/drawable/ic_launcher_foreground.xml", "w").write(
    vector(foreground, "Launcher icon front layer: African grey parrot on a glass bar-chart card that doubles as a chat-bot face."))
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
for n in ("ic_launcher", "ic_launcher_round"):
    open(f"{OUT}/app/src/main/res/mipmap-anydpi-v26/{n}.xml", "w").write(adaptive)
