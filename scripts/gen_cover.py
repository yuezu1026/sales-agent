# -*- coding: utf-8 -*-
"""为 doc/soft-article.md 生成知乎封面图（1920x1080, 16:9 科技风）。"""
import math
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1920, 1080
OUT = r"d:\project-ai\ai-customer\doc\cover-soft-article.png"

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"

# 配色
BG_TOP = (9, 14, 30)          # 深藏青
BG_BOTTOM = (32, 22, 66)      # 深紫
ACCENT1 = (56, 189, 248)      # 青蓝
ACCENT2 = (167, 139, 250)     # 紫
TEXT_MAIN = (245, 248, 255)
TEXT_SUB = (155, 170, 195)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def make_background():
    """对角渐变背景（先画小图再放大，速度快）。"""
    sw, sh = 192, 108
    img = Image.new("RGB", (sw, sh))
    px = img.load()
    for y in range(sh):
        for x in range(sw):
            t = 0.55 * (x / sw) + 0.45 * (y / sh)
            px[x, y] = lerp(BG_TOP, BG_BOTTOM, t)
    return img.resize((W, H), Image.BILINEAR).convert("RGBA")


def add_glow(base, center, radius, color, alpha):
    """在 base 上叠加一处柔和径向光晕。"""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
              fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    return Image.alpha_composite(base, layer)


def add_grid(base):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    step = 90
    for x in range(0, W, step):
        d.line([(x, 0), (x, H)], fill=(255, 255, 255, 9), width=1)
    for y in range(0, H, step):
        d.line([(0, y), (W, y)], fill=(255, 255, 255, 9), width=1)
    return Image.alpha_composite(base, layer)


def add_network(base):
    """右侧画神经网络节点 + 连线（带发光）。"""
    random.seed(42)
    nodes = []
    for _ in range(17):
        x = random.randint(1020, 1830)
        y = random.randint(110, 960)
        r = random.choice([7, 9, 11, 14])
        nodes.append((x, y, r))

    edges = []
    for i in range(len(nodes)):
        for j in range(i + 1, len(nodes)):
            x1, y1, _ = nodes[i]
            x2, y2, _ = nodes[j]
            dist = math.hypot(x2 - x1, y2 - y1)
            if dist < 300:
                edges.append((i, j, dist))

    # 连线
    line_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(line_layer)
    for i, j, dist in edges:
        a = int(70 * (1 - dist / 300)) + 18
        d.line([nodes[i][:2], nodes[j][:2]], fill=ACCENT1 + (a,), width=2)
    base = Image.alpha_composite(base, line_layer)

    # 节点光晕
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dg = ImageDraw.Draw(glow)
    for idx, (x, y, r) in enumerate(nodes):
        color = ACCENT1 if idx % 3 != 2 else ACCENT2
        dg.ellipse([x - r * 3, y - r * 3, x + r * 3, y + r * 3],
                   fill=color + (55,))
    glow = glow.filter(ImageFilter.GaussianBlur(14))
    base = Image.alpha_composite(base, glow)

    # 节点实体
    node_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dn = ImageDraw.Draw(node_layer)
    for idx, (x, y, r) in enumerate(nodes):
        color = ACCENT1 if idx % 3 != 2 else ACCENT2
        dn.ellipse([x - r, y - r, x + r, y + r], fill=color + (235,))
        dn.ellipse([x - r + 3, y - r + 3, x + r - 3, y + r - 3],
                   fill=(230, 245, 255, 210))
    return Image.alpha_composite(base, node_layer)


def fit_font(text, max_width, start_size, bold=True, min_size=30):
    """自动缩小字号直到文本不超过 max_width。"""
    path = FONT_BOLD if bold else FONT_REG
    size = start_size
    while size > min_size:
        font = ImageFont.truetype(path, size)
        if font.getlength(text) <= max_width:
            return font
        size -= 2
    return ImageFont.truetype(path, min_size)


def draw_pill(base, xy, text, font, border_color, text_color,
              fill=(148, 190, 255, 26)):
    """在独立透明层上画胶囊标签后合成，避免半透明 fill 直接覆盖主图 alpha。"""
    x, y = xy
    tw = font.getlength(text)
    pad_x, pad_y = 28, 12
    th = font.size
    box = [x, y, x + tw + pad_x * 2, y + th + pad_y * 2]
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle(box, radius=(box[3] - box[1]) / 2, fill=fill,
                        outline=border_color + (160,), width=2)
    d.text((x + pad_x, y + pad_y - 2), text, font=font, fill=text_color)
    return Image.alpha_composite(base, layer), box[2]


def gradient_text(base, xy, text, font, c1, c2):
    """绘制水平渐变文字。"""
    x, y = xy
    mask = Image.new("L", (W, H), 0)
    dm = ImageDraw.Draw(mask)
    dm.text((x, y), text, font=font, fill=255)
    grad = Image.new("RGB", (W, 1))
    gp = grad.load()
    tw = int(font.getlength(text))
    for i in range(W):
        t = max(0.0, min(1.0, (i - x) / max(tw, 1)))
        gp[i, 0] = lerp(c1, c2, t)
    grad = grad.resize((W, H))
    colored = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    colored.paste(grad, (0, 0))
    colored.putalpha(mask)
    return Image.alpha_composite(base, colored)


def main():
    img = make_background()
    img = add_glow(img, (1450, 330), 560, ACCENT1, 46)
    img = add_glow(img, (1650, 850), 480, ACCENT2, 40)
    img = add_glow(img, (200, 950), 520, (37, 99, 235), 34)
    img = add_grid(img)
    img = add_network(img)

    d = ImageDraw.Draw(img)
    mx = 110  # 左边距
    max_text_w = 1700

    # 顶部徽标
    badge_font = ImageFont.truetype(FONT_BOLD, 32)
    img, bx = draw_pill(img, (mx, 128), "真实项目 · 全栈实战复盘", badge_font,
                        ACCENT1, (190, 235, 255))
    img, _ = draw_pill(img, (bx + 24, 128), "B2B × AI 获客", badge_font,
                       ACCENT2, (228, 214, 255))
    d = ImageDraw.Draw(img)

    # 主标题
    t1 = "从 0 到 1 打造端到端 AI 销售智能体"
    f1 = fit_font(t1, max_text_w, 104)
    d.text((mx, 236), t1, font=f1, fill=TEXT_MAIN)

    t2 = "Spring Boot 4 + Spring AI 2.0"
    f2 = fit_font(t2, max_text_w, 92)
    img = gradient_text(img, (mx, 236 + f1.size + 34), t2, f2, ACCENT1, ACCENT2)
    d = ImageDraw.Draw(img)

    # 副标题
    sub_y = 236 + f1.size + 34 + f2.size + 56
    sub_font = ImageFont.truetype(FONT_REG, 42)
    d.text((mx, sub_y), "潜客挖掘 → 个性化触达 → 转化 → 数据复盘",
           font=sub_font, fill=TEXT_SUB)
    d.text((mx, sub_y + 62), "B2B 获客全链路自动化的落地细节、踩坑与取舍",
           font=sub_font, fill=TEXT_SUB)

    # 技术标签
    tag_font = ImageFont.truetype(FONT_BOLD, 34)
    tags = ["Spring Boot 4", "Spring AI 2.0", "RAG 画像", "MCP", "本地部署"]
    tx = mx
    ty = sub_y + 172
    for tag in tags:
        img, w = draw_pill(img, (tx, ty), tag, tag_font, ACCENT1, (205, 232, 252))
        tx = w + 22
    d = ImageDraw.Draw(img)

    # 底部流程点
    flow_font = ImageFont.truetype(FONT_REG, 34)
    steps = ["发现", "触达", "转化", "复盘"]
    fx = mx
    fy = H - 118
    for i, s in enumerate(steps):
        color = ACCENT1 if i % 2 == 0 else ACCENT2
        d.ellipse([fx, fy + 12, fx + 16, fy + 28], fill=color + (255,))
        d.text((fx + 30, fy), s, font=flow_font, fill=(210, 222, 240))
        fx += 30 + flow_font.getlength(s) + 26
        if i < len(steps) - 1:
            d.text((fx, fy), "→", font=flow_font, fill=(110, 128, 155))
            fx += flow_font.getlength("→") + 26

    # 右下角产品名
    brand_font = ImageFont.truetype(FONT_REG, 30)
    brand = "AI 智能获客助手 · sales-agent.top"
    d.text((W - 110 - brand_font.getlength(brand), H - 96), brand,
           font=brand_font, fill=(130, 145, 170))

    img.convert("RGB").save(OUT, quality=95)
    print(f"saved: {OUT} ({W}x{H})")


if __name__ == "__main__":
    main()
