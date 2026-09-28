import sharp from 'sharp';
import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * Generate ikon launcher Android (mipmap) untuk APK GitPush.
 * Sumber: brand/icon-raw.png — tile rounded-square gelap dengan POJOK TRANSPARAN.
 * - ic_launcher        : tile di atas bidang warna tile (pojok terisi rapi)
 * - ic_launcher_round  : lingkaran penuh
 * - ic_launcher_foreground : adaptive icon, full-bleed (mask launcher memotong mulus
 *                            karena latar adaptive = warna tile yang sama)
 */
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(root, 'brand', 'icon-raw.png');
const RES = join(root, 'native', 'app', 'src', 'main', 'res');
const TILE_BG = { r: 0x2A, g: 0x33, b: 0x3D, alpha: 1 }; // warna tile logo (hasil sampling)

// 1) Tile di atas bidang warna sama → "full-bleed" mulus tanpa pojok
const fullBleed = await sharp(SRC)
  .flatten({ background: TILE_BG })
  .png()
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

// 4) Latar adaptive icon = warna tile → mask launcher potong mulus
await writeFile(
  join(RES, 'values', 'ic_launcher_background.xml'),
  `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#2A333D</color>\n</resources>\n`
);

console.log('OK GitPush Android: ic_launcher (5), ic_launcher_round (5), ic_launcher_foreground (5), bg #2A333D');
