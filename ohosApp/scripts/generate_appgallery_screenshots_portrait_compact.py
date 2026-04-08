from pathlib import Path
import shutil
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont


ROOT = Path("/Users/machunjiang/MaFei/ohosApp")
OUT_DIR = ROOT / "store-assets" / "appgallery-portrait-submit"
SCREENSHOT_DIR = ROOT / "screenshots"
CUSTOM_BG_PATH = ROOT / "store-assets" / "custom-bg.webp"
CN_FONT = "/System/Library/Fonts/Hiragino Sans GB.ttc"

WIDTH = 1080
HEIGHT = 1920

BG_TOP = (224, 229, 236)
BG_BOTTOM = (210, 216, 225)
CARD_BG = (255, 255, 255)
TEXT_DARK = (248, 249, 252)
TEXT_BODY = (238, 241, 246)
SHADOW = (14, 24, 39, 28)

SLIDES = [
    {
        "file": "01-home.jpg",
        "chip": "首页",
        "title": "接续观看更顺手",
        "body": "继续观看与最近添加集中展示，\n打开应用即可接上次内容。",
        "image": "homepage.jpg",
        "overlay": (56, 92, 156, 58),
        "image_scale": 1.04,
    },
    {
        "file": "02-detail.jpg",
        "chip": "详情",
        "title": "影片信息集中查看",
        "body": "评分、简介和播放入口同页呈现，\n查看信息后可直接播放。",
        "image": "detail.jpg",
        "overlay": (96, 72, 142, 58),
        "image_scale": 1.03,
    },
    {
        "file": "03-library.jpg",
        "chip": "媒体库",
        "title": "分类浏览更清晰",
        "body": "按分类整理影片与剧集，\n查找不同内容时路径更清晰。",
        "image": "media.jpg",
        "overlay": (44, 106, 108, 56),
        "image_scale": 1.00,
    },
    {
        "file": "04-favorite.jpg",
        "chip": "收藏",
        "title": "回看入口更清晰",
        "body": "收藏内容集中显示，\n常看影片与回看入口一眼可见。",
        "image": "favorite.jpg",
        "overlay": (130, 88, 52, 54),
        "image_scale": 1.00,
    },
]


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(CN_FONT, size=size, index=1 if bold else 0)
    except OSError:
        return ImageFont.load_default()


def make_vertical_gradient(width: int, height: int, top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGB", (width, height), top)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        ratio = y / max(1, height - 1)
        color = tuple(int(top[i] * (1 - ratio) + bottom[i] * ratio) for i in range(3))
        draw.line((0, y, width, y), fill=color)
    return image


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size[0], size[1]), radius=radius, fill=255)
    return mask


def add_shadow(base: Image.Image, box: tuple[int, int, int, int], radius: int = 34, blur: int = 42) -> None:
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    draw.rounded_rectangle(box, radius=radius, fill=SHADOW)
    overlay = overlay.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(overlay)


def draw_multiline_center(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, fill: tuple[int, int, int],
                          center_x: int, top_y: int, line_spacing: int) -> int:
    lines = text.split("\n")
    current_y = top_y
    for line in lines:
        box = draw.textbbox((0, 0), line, font=font)
        line_width = box[2] - box[0]
        draw.text((center_x - line_width // 2, current_y), line, font=font, fill=fill)
        current_y += (box[3] - box[1]) + line_spacing
    return current_y


def draw_text_shadow(draw: ImageDraw.ImageDraw, position: tuple[int, int], text: str,
                     font: ImageFont.FreeTypeFont, fill: tuple[int, int, int],
                     shadow_fill: tuple[int, int, int, int] = (0, 0, 0, 120)) -> None:
    x, y = position
    draw.text((x, y + 3), text, font=font, fill=shadow_fill)
    draw.text((x, y), text, font=font, fill=fill)


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, max_width: int) -> str:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        current = ""
        for char in paragraph:
            candidate = current + char
            box = draw.textbbox((0, 0), candidate, font=font)
            if box[2] - box[0] <= max_width or len(current) == 0:
                current = candidate
            else:
                lines.append(current)
                current = char
        if current:
            lines.append(current)
    return "\n".join(lines)


def fit_crop_fill(image: Image.Image, target_size: tuple[int, int], extra_scale: float = 1.0,
                  focus_y: float = 0.0) -> Image.Image:
    target_w, target_h = target_size
    scale = max(target_w / image.width, target_h / image.height) * extra_scale
    resized = image.resize((int(image.width * scale), int(image.height * scale)), Image.LANCZOS)
    max_left = max(0, resized.width - target_w)
    max_top = max(0, resized.height - target_h)
    left = max_left // 2
    top = int(max_top * min(max(focus_y, 0.0), 1.0))
    return resized.crop((left, top, left + target_w, top + target_h))


def fit_crop_fill_top(image: Image.Image, target_size: tuple[int, int]) -> Image.Image:
    target_w, target_h = target_size
    scale = max(target_w / image.width, target_h / image.height)
    resized = image.resize((int(image.width * scale), int(image.height * scale)), Image.LANCZOS)
    max_left = max(0, resized.width - target_w)
    left = max_left // 2
    return resized.crop((left, 0, left + target_w, target_h))


def build_background(slide: dict) -> Image.Image:
    canvas = make_vertical_gradient(WIDTH, HEIGHT, BG_TOP, BG_BOTTOM).convert("RGBA")
    base_bg = Image.open(CUSTOM_BG_PATH).convert("RGB")
    base_bg = fit_crop_fill_top(base_bg, (WIDTH, HEIGHT)).convert("RGBA")
    base_bg = base_bg.filter(ImageFilter.GaussianBlur(4))
    base_bg = ImageEnhance.Brightness(base_bg).enhance(0.66)
    base_bg = ImageEnhance.Color(base_bg).enhance(0.95)

    color_overlay = Image.new("RGBA", (WIDTH, HEIGHT), slide["overlay"])
    wall_overlay = Image.new("RGBA", (WIDTH, HEIGHT), (255, 255, 255, 24))
    top_scrim = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    scrim_draw = ImageDraw.Draw(top_scrim)
    for y in range(460):
        alpha = int(116 * (1 - y / 460))
        scrim_draw.line((0, y, WIDTH, y), fill=(8, 14, 25, alpha))

    canvas.alpha_composite(base_bg)
    canvas.alpha_composite(color_overlay)
    canvas.alpha_composite(wall_overlay)
    canvas.alpha_composite(top_scrim)
    return canvas


def compose_slide(slide: dict) -> Image.Image:
    canvas = build_background(slide)
    draw = ImageDraw.Draw(canvas)

    title_font = load_font(56, bold=True)
    body_font = load_font(32, bold=False)

    title_text = f"{slide['chip']} · {slide['title']}"
    title_text = wrap_text(draw, title_text, title_font, 900)
    title_box = draw.multiline_textbbox((0, 0), title_text, font=title_font, spacing=10)
    title_x = WIDTH // 2 - (title_box[2] - title_box[0]) // 2
    title_y = 86
    draw.multiline_text((title_x, title_y + 4), title_text, font=title_font, fill=(0, 0, 0, 144), spacing=10, align="center")
    draw.multiline_text((title_x, title_y), title_text, font=title_font, fill=TEXT_DARK, spacing=10, align="center")
    title_bottom = title_y + (title_box[3] - title_box[1])

    body_text = wrap_text(draw, slide["body"], body_font, 820)
    body_box = draw.multiline_textbbox((0, 0), body_text, font=body_font, spacing=18)
    body_w = body_box[2] - body_box[0]
    body_x = WIDTH // 2 - body_w // 2
    body_y = title_bottom + 24
    draw.multiline_text((body_x, body_y + 4), body_text, font=body_font, fill=(0, 0, 0, 128), spacing=18, align="center")
    draw.multiline_text((body_x, body_y), body_text, font=body_font, fill=TEXT_BODY, spacing=18, align="center")

    screenshot_box = (124, 364, WIDTH - 124, HEIGHT - 42)
    add_shadow(canvas, (screenshot_box[0] + 6, screenshot_box[1] + 14, screenshot_box[2] - 6, screenshot_box[3] + 10))
    draw.rounded_rectangle(screenshot_box, radius=42, fill=CARD_BG)

    screenshot = Image.open(SCREENSHOT_DIR / slide["image"]).convert("RGB")
    screenshot = fit_crop_fill(
        screenshot,
        (screenshot_box[2] - screenshot_box[0], screenshot_box[3] - screenshot_box[1]),
        extra_scale=slide.get("image_scale", 1.0),
        focus_y=slide.get("focus_y", 0.0),
    ).convert("RGBA")
    screenshot.putalpha(rounded_mask(screenshot.size, 42))
    canvas.alpha_composite(screenshot, (screenshot_box[0], screenshot_box[1]))

    gloss = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    gloss_draw = ImageDraw.Draw(gloss)
    gloss_draw.rounded_rectangle(
        (screenshot_box[0], screenshot_box[1], screenshot_box[2], screenshot_box[1] + 150),
        radius=42,
        fill=(255, 255, 255, 32)
    )
    canvas.alpha_composite(gloss)

    return canvas.convert("RGB")


def main() -> None:
    if OUT_DIR.exists():
        shutil.rmtree(OUT_DIR)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for slide in SLIDES:
        image = compose_slide(slide)
        jpg_path = OUT_DIR / slide["file"]
        image.save(jpg_path, format="JPEG", quality=92, optimize=True)
    print(f"generated: {OUT_DIR}")


if __name__ == "__main__":
    main()
