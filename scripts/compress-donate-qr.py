"""M8.9 捐助页收款码压缩脚本
目标：alipay.jpg / wechat-pay.jpg 压到 < 20KB，且二维码仍可解码（内容一致）。
策略：cv2 定位二维码 → 裁剪白卡区域（二维码+名称，去掉品牌页头/页脚）→ 等比缩放 + JPEG 质量迭代，
选「可解码且 <20KB」中质量最高（画质最好）的组合。
"""
import io
import os
import sys

import cv2
import numpy as np
from PIL import Image

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

PUBLIC_DIR = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "frontend", "public"))
TARGET_BYTES = 20 * 1024

# 裁剪后候选宽度 + 候选质量
WIDTHS = [640, 560, 500, 440, 400, 360, 320, 288]
QUALITIES = [80, 74, 68, 62, 56, 50, 44, 38, 32, 26]

# 原图备份目录（保证脚本可重复执行，始终从原图压缩）
BACKUP_DIR = os.path.join(os.path.dirname(__file__), "orig-backup")


def decode_qr(cv_img):
    if cv_img is None:
        return ""
    det = cv2.QRCodeDetector()
    data, _, _ = det.detectAndDecode(cv_img)
    return data or ""


def pil_to_cv(pil_img):
    return cv2.cvtColor(np.array(pil_img.convert("RGB")), cv2.COLOR_RGB2BGR)


def jpeg_bytes(pil_img, quality):
    buf = io.BytesIO()
    pil_img.convert("RGB").save(buf, "JPEG", quality=quality, optimize=True)
    return buf.getvalue()


def main():
    os.makedirs(BACKUP_DIR, exist_ok=True)
    results = []
    for name in ["alipay.jpg", "wechat-pay.jpg"]:
        path = os.path.join(PUBLIC_DIR, name)
        backup = os.path.join(BACKUP_DIR, name)
        if not os.path.exists(backup):
            import shutil
            shutil.copy2(path, backup)
        img = Image.open(backup)
        baseline = decode_qr(cv2.imread(backup))
        print(f"\n=== {name} === 原始 {img.width}x{img.height}, {os.path.getsize(backup)/1024:.1f} KB")
        print(f"原始解码: {baseline!r}")
        if not baseline:
            print("❌ 原始图无法解码，跳过")
            results.append((name, None))
            continue

        # 定位二维码 → 裁剪白卡区域
        cv_img = pil_to_cv(img)
        _, pts, _ = cv2.QRCodeDetector().detectAndDecode(cv_img)
        p = pts[0]
        x0, y0 = p.min(axis=0).astype(int)
        x1, y1 = p.max(axis=0).astype(int)
        s = x1 - x0
        # 白卡 ≈ 二维码四周留白 + 底部名称行：左右/上 0.15s，下 0.35s
        cx0 = max(0, int(x0 - 0.15 * s))
        cy0 = max(0, int(y0 - 0.15 * s))
        cx1 = min(img.width, int(x1 + 0.15 * s))
        cy1 = min(img.height, int(y1 + 0.35 * s))
        cropped = img.crop((cx0, cy0, cx1, cy1))
        print(f"裁剪区域: ({cx0},{cy0})-({cx1},{cy1}) → {cropped.width}x{cropped.height}")

        candidates = []
        for w in WIDTHS:
            if w >= cropped.width:
                continue
            ratio = w / cropped.width
            resized = cropped.resize((w, round(cropped.height * ratio)), Image.LANCZOS)
            min_size = None
            for q in QUALITIES:
                data = jpeg_bytes(resized, q)
                min_size = len(data) if min_size is None else min(min_size, len(data))
                if len(data) >= TARGET_BYTES:
                    continue
                if decode_qr(pil_to_cv(Image.open(io.BytesIO(data)))) == baseline:
                    candidates.append((q, w, len(data), data))
            print(f"  宽 {w}: 最小体积 {min_size/1024:.1f} KB, 累计候选 {len(candidates)}")
        if not candidates:
            print("❌ 无法在保持可解码前提下压到 20KB 以下")
            results.append((name, None))
            continue
        # 优先宽度大（二维码像素多更清晰），同宽度取质量最高
        candidates.sort(key=lambda c: (-c[1], -c[0]))
        q, w, size, data = candidates[0]
        with open(path, "wb") as f:
            f.write(data)
        print(f"✅ 写入: 宽 {w}px, quality={q}, {size/1024:.1f} KB, 解码验证通过")
        results.append((name, (size, w, q)))

    print("\n=== 汇总 ===")
    for name, r in results:
        if r:
            print(f"{name}: {r[0]/1024:.1f} KB (宽 {r[1]}px, q={r[2]})")
        else:
            print(f"{name}: 未达标")


if __name__ == "__main__":
    main()
