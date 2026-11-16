#!/usr/bin/env python3
"""Render the SVG masters in assets/brand to the app's launcher icons, splash icon and store icon.

The same masters and layout as ciphwren-notes' scripts/generate-brand-assets.py.
Development-only dependencies: Python, Pillow, PyGObject, pycairo and librsvg.
Run from anywhere: python3 scripts/generate-brand-assets.py
"""
import io
from pathlib import Path
import xml.etree.ElementTree as ET

import cairo
import gi
from PIL import Image, ImageDraw

gi.require_version("Rsvg", "2.0")
from gi.repository import Rsvg  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
RES = MAIN / "res"
SVG = "{http://www.w3.org/2000/svg}"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
NAVY = "#0B1F33"
SOURCE = (ROOT / "assets/brand/icon.svg").read_text()
# The mark's navy body disappears on navy. Lift its dark gradient stops for the inverse variant
# (launcher icon, drawer header, dark splash); keep the original geometry.
DARK = SOURCE.replace("#0B3559", "#9AE8E8").replace(NAVY, "#E6F9FA")
MONO = (ROOT / "assets/brand/icon-monochrome.svg").read_text()


def render(svg, size=1024):
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    handle = Rsvg.Handle.new_from_data(svg.encode())
    viewport = Rsvg.Rectangle()
    viewport.x = viewport.y = 0
    viewport.width = viewport.height = size
    handle.render_document(cairo.Context(surface), viewport)
    output = io.BytesIO()
    surface.write_to_png(output)
    return Image.open(output).convert("RGBA")


def centered(mark, size, fraction, background=None):
    canvas = Image.new("RGBA", (size, size), background or (0, 0, 0, 0))
    fitted = mark.copy()
    fitted.thumbnail((round(size * fraction), round(size * fraction)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    return canvas


def save(image, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.suffix == ".webp":
        image.save(target, lossless=True, method=6)
    else:
        image.save(target, optimize=True)


def masked(image, circle=False):
    mask = Image.new("L", (image.width * 4, image.height * 4))
    draw = ImageDraw.Draw(mask)
    box = (0, 0, mask.width - 1, mask.height - 1)
    if circle:
        draw.ellipse(box, fill=255)
    else:
        draw.rounded_rectangle(box, radius=mask.width * .20, fill=255)
    image.putalpha(mask.resize(image.size, Image.Resampling.LANCZOS))
    return image


def attr(**values):
    return {f"{{{ANDROID}}}{key}": str(value) for key, value in values.items()}


def color(value):
    return {"white": "#FFFFFF", "black": "#000000", "none": "#00000000"}.get(value, value)


def vector(svg, name, dp, fraction):
    """Convert this logo pack's paths, circles and linear gradients, not arbitrary SVG."""
    source = ET.fromstring(svg)
    root = ET.Element("vector", attr(width=f"{dp}dp", height=f"{dp}dp", viewportWidth=512, viewportHeight=512))
    # Match the PNG layout, using the visible mark's bounds instead of the SVG's
    # built-in margins. Both adaptive layers occupy 54dp of the 108dp canvas.
    bounds = render(svg).getbbox()
    x0, y0, x1, y1 = [v / 2 for v in bounds]
    scale = 512 * fraction / max(x1 - x0, y1 - y0)
    group = ET.SubElement(root, "group", attr(scaleX=scale, scaleY=scale,
        translateX=256 - scale * (x0 + x1) / 2, translateY=256 - scale * (y0 + y1) / 2))
    clip = source.find(f"{SVG}defs/{SVG}clipPath/{SVG}path")
    if clip is not None:
        ET.SubElement(group, "clip-path", attr(pathData=clip.attrib["d"], fillType="evenOdd"))
    gradients = {g.attrib["id"]: g for g in source.findall(f"{SVG}defs/{SVG}linearGradient")}
    drawing = source.find(f"{SVG}g")
    for shape in drawing:
        if shape.tag == f"{SVG}circle":
            cx, cy, r = (float(shape.attrib[k]) for k in ("cx", "cy", "r"))
            path = f"M{cx-r},{cy}a{r},{r} 0,1 0,{2*r},0a{r},{r} 0,1 0,{-2*r},0Z"
        elif shape.tag == f"{SVG}path":
            path = shape.attrib["d"]
        else:
            raise ValueError(f"Unsupported logo shape: {shape.tag}")
        node = ET.SubElement(group, "path", attr(pathData=path))
        for kind in ("fill", "stroke"):
            value = shape.get(kind, drawing.get(kind, "black" if kind == "fill" else "none"))
            key = f"{kind}Color"
            if value.startswith("url(#"):
                gradient = gradients[value[5:-1]]
                holder = ET.SubElement(node, f"{{{AAPT}}}attr", {"name": f"android:{key}"})
                grad = ET.SubElement(holder, "gradient", attr(type="linear", startX=gradient.get("x1"),
                    startY=gradient.get("y1"), endX=gradient.get("x2"), endY=gradient.get("y2")))
                for stop in gradient:
                    ET.SubElement(grad, "item", attr(offset=stop.get("offset", "0"), color=color(stop.get("stop-color"))))
            else:
                node.set(f"{{{ANDROID}}}{key}", color(value))
        for svg_key, android_key in (("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap")):
            if svg_key in shape.attrib:
                node.set(f"{{{ANDROID}}}{android_key}", shape.attrib[svg_key])
    ET.indent(root, space="  ")
    target = RES / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by scripts/generate-brand-assets.py. -->\n' + ET.tostring(root, encoding="unicode") + "\n")


light, dark = [render(source) for source in (SOURCE, DARK)]
light, dark = [image.crop(image.getbbox()) for image in (light, dark)]
store_icon = centered(dark, 512, .62, NAVY).convert("RGB")
save(store_icon, ROOT / "fastlane/metadata/android/en-US/images/icon.png")
save(store_icon, MAIN / "ic_launcher-web.png")
# Launcher icons of Android 7.1 and older; newer versions use the adaptive icon in mipmap-anydpi-v26
for density, factor in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
    for name, circular in (("ic_launcher", False), ("ic_launcher_round", True)):
        save(masked(centered(dark, round(48 * factor), .60 if circular else .64, NAVY), circular),
             RES / f"mipmap-{density}/{name}.webp")
vector(DARK, "drawable/ic_launcher_foreground.xml", 108, .50)
vector(MONO, "drawable/ic_launcher_monochrome.xml", 108, .50)
vector(DARK, "drawable/ic_brand_mark.xml", 64, 1)
# Android 12's splash screen: the mark without the icon's background, on the theme's background
vector(SOURCE, "drawable/splashscreen_icon.xml", 288, 2 / 3)
vector(DARK, "drawable-night/splashscreen_icon.xml", 288, 2 / 3)
print("Generated launcher, store, drawer and splash screen assets.")
