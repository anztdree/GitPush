import sharp from 'sharp';
import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * Generate ikon launcher Android (mipmap) + splash untuk APK GitPush.
 * Sumber: brand/icon-raw.png (rounded square di kanvas putih → di-crop full-bleed).
 */
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(root, 'brand', 'icon-raw.png');
const RES = join(root, 'apk-build', 'android', 'app', 'src', 'main', 'res');

// 1) Deteksi bounding box rounded-square gelap (sama dengan gen-icons.mjs)
const { data, info } = await sharp(SRC).raw().toBuffer({ resolveWithObject: true });
const isDarkish = (x, y) => {
  const i = (y * info.width + x) * info.channels;
  return data[i] + data[i + 1] + data[i + 2] < 600;
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
const side = Math.min(maxX - minX, maxY - minY) + 1;
const inset = Math.round(side * 0.07);
const fullBleed = await sharp(SRC)
  .extract({ left: minX + inset, top: minY + inset, width: side - inset * 2, height: side - inset * 2 })
  .toBuffer();

const circleMask = (size) =>
  Buffer.from(
    `<svg width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="#fff"/></svg>`
  );

// 2) Ikon launcher legacy per density
const LAUNCHER = {
  'mipmap-mdpi': 48,
  'mipmap-hdpi': 72,
  'mipmap-xhdpi': 96,
  'mipmap-xxhdpi': 144,
  'mipmap-xxxhdpi': 192,
};
for (const [dir, size] of Object.entries(LAUNCHER)) {
  await sharp(fullBleed).resize(size, size).png().toFile(join(RES, dir, 'ic_launcher.png'));
  await sharp(fullBleed)
    .resize(size, size)
    .composite([{ input: circleMask(size), blend: 'dest-in' }])
    .png()
    .toFile(join(RES, dir, 'ic_launcher_round.png'));
}

// 3) Foreground adaptive icon — full-bleed penuh (motif sudah di safe-zone)
const FG = {
  'mipmap-mdpi': 108,
  'mipmap-hdpi': 162,
  'mipmap-xhdpi': 216,
  'mipmap-xxhdpi': 324,
  'mipmap-xxxhdpi': 432,
};
for (const [dir, size] of Object.entries(FG)) {
  await sharp(fullBleed).resize(size, size).png().toFile(join(RES, dir, 'ic_launcher_foreground.png'));
}

// 4) Latar adaptive icon + splash gelap khas GitPush
await writeFile(
  join(RES, 'values', 'ic_launcher_background.xml'),
  `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#161B22</color>\n</resources>\n`
);
await sharp({
  create: { width: 64, height: 64, channels: 3, background: { r: 13, g: 17, b: 23 } },
})
  .png()
  .toFile(join(RES, 'drawable', 'splash.png'));

console.log('OK GitPush Android: ic_launcher (5), ic_launcher_round (5), ic_launcher_foreground (5), splash, bg color');
