import sharp from 'sharp';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * GitPush icon pipeline.
 * Source: brand/icon-raw.png (AI-generated, rounded square on white canvas).
 * Output: full-bleed dark PNGs for PWA (any + maskable) + apple-touch-icon.
 */
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(root, 'brand', 'icon-raw.png');
const BG = { r: 13, g: 17, b: 23, alpha: 1 }; // #0d1117

// 1) Temukan bounding box rounded-square gelap di atas kanvas putih
const { data, info } = await sharp(SRC).raw().toBuffer({ resolveWithObject: true });
const isDarkish = (x, y) => {
  const i = (y * info.width + x) * info.channels;
  return data[i] + data[i + 1] + data[i + 2] < 600; // bukan putih
};
let minX = info.width, minY = info.height, maxX = 0, maxY = 0;
for (let y = 0; y < info.height; y++) {
  for (let x = 0; x < info.width; x++) {
    if (isDarkish(x, y)) {
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
    }
  }
}

// 2) Inset 7% tiap sisi agar sudut membulat terpotong bersih → full-bleed gelap
const side = Math.min(maxX - minX, maxY - minY) + 1;
const inset = Math.round(side * 0.07);
const crop = {
  left: minX + inset,
  top: minY + inset,
  width: side - inset * 2,
  height: side - inset * 2,
};
const fullBleed = await sharp(SRC).extract(crop).toBuffer();

// 3) Ekspor semua ukuran PWA (motif sudah berada dalam safe-zone maskable)
await sharp(fullBleed).resize(512, 512).png().toFile(join(root, 'public', 'icon-512.png'));
await sharp(fullBleed).resize(192, 192).png().toFile(join(root, 'public', 'icon-192.png'));
await sharp(fullBleed).resize(512, 512).png().toFile(join(root, 'public', 'icon-maskable-512.png'));
await sharp(fullBleed).flatten({ background: BG }).resize(180, 180).png().toFile(join(root, 'public', 'apple-touch-icon.png'));

console.log('OK GitPush: icon-512, icon-192, icon-maskable-512, apple-touch-icon');
