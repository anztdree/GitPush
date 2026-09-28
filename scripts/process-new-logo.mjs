/**
 * Proses logo baru GitPush:
 * 1. Buang background putih (flood-fill dari tepi) → transparan + dehalo tepi
 * 2. Trim ke bounding box konten
 * 3. Sampling warna tile gelap (untuk adaptive icon background)
 * 4. Simpan brand/icon-raw.png (RGBA transparan)
 */
import sharp from 'sharp';

const SRC = '/tmp/logo-new.png';
const OUT = '/home/z/my-project/brand/icon-raw.png';

const { data, info } = await sharp(SRC).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const W = info.width, H = info.height, CH = info.channels;

// --- 1) flood-fill dari semua piksel tepi yang near-white ---
const isWhiteish = (x, y) => {
  const i = (y * W + x) * CH;
  return Math.min(data[i], data[i + 1], data[i + 2]) > 242;
};
const visited = new Uint8Array(W * H);
const stack = [];
for (let x = 0; x < W; x++) { stack.push([x, 0], [x, H - 1]); }
for (let y = 0; y < H; y++) { stack.push([0, y], [W - 1, y]); }
while (stack.length) {
  const [x, y] = stack.pop();
  if (x < 0 || y < 0 || x >= W || y >= H) continue;
  const p = y * W + x;
  if (visited[p]) continue;
  visited[p] = 1;
  if (!isWhiteish(x, y)) continue;
  const i = p * CH;
  data[i + 3] = 0; // transparan
  stack.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
}

// --- 2) dehalo: piksel tetangga area transparan yang masih keputihan → alpha parsial ---
const alphaSnapshot = new Uint8Array(W * H);
for (let p = 0; p < W * H; p++) alphaSnapshot[p] = data[p * CH + 3];
const soften = (x, y) => {
  const i = (y * W + x) * CH;
  if (data[i + 3] === 0) return;
  const minc = Math.min(data[i], data[i + 1], data[i + 2]);
  if (minc > 205) {
    const a = Math.max(0, Math.min(255, Math.round(((255 - minc) * 255) / 50)));
    data[i + 3] = a;
  }
};
for (let y = 1; y < H - 1; y++) {
  for (let x = 1; x < W - 1; x++) {
    const p = y * W + x;
    if (alphaSnapshot[p] === 0) continue;
    if (alphaSnapshot[p - 1] === 0 || alphaSnapshot[p + 1] === 0 ||
        alphaSnapshot[p - W] === 0 || alphaSnapshot[p + W] === 0) soften(x, y);
  }
}

// --- 3) bounding box konten non-transparan ---
let minX = W, minY = H, maxX = 0, maxY = 0;
for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    if (data[(y * W + x) * CH + 3] > 8) {
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
    }
  }
}
const bw = maxX - minX + 1, bh = maxY - minY + 1;
console.log(`bbox: ${bw}x${bh} @ (${minX},${minY})`);

// sampling warna tile: rata-rata piksel gelap di 4 sudut dalam bbox
const sampleSize = Math.round(Math.min(bw, bh) * 0.04);
const pts = [
  [minX + sampleSize, minY + sampleSize], [maxX - sampleSize, minY + sampleSize],
  [minX + sampleSize, maxY - sampleSize], [maxX - sampleSize, maxY - sampleSize],
];
let sr = 0, sg = 0, sb = 0, n = 0;
for (const [px, py] of pts) {
  for (let dy = -2; dy <= 2; dy++) {
    for (let dx = -2; dx <= 2; dx++) {
      const i = ((py + dy) * W + (px + dx)) * CH;
      if (data[i + 3] > 200) { sr += data[i]; sg += data[i + 1]; sb += data[i + 2]; n++; }
    }
  }
}
const tile = n > 0
  ? { r: Math.round(sr / n), g: Math.round(sg / n), b: Math.round(sb / n) }
  : { r: 22, g: 27, b: 34 };
const hex = (v) => v.toString(16).padStart(2, '0').toUpperCase();
console.log(`warna tile: #${hex(tile.r)}${hex(tile.g)}${hex(tile.b)} (n=${n})`);

// --- 4) crop + square-kan (pad transparan sampai persegi) + simpan ---
// PENTING: crop dari buffer `data` yang SUDAH diproses (bukan dari file asli)
const side = Math.max(bw, bh);
const cropped = Buffer.alloc(bw * bh * 4);
for (let y = 0; y < bh; y++) {
  for (let x = 0; x < bw; x++) {
    const si = ((minY + y) * W + (minX + x)) * CH;
    const di = (y * bw + x) * 4;
    cropped[di] = data[si]; cropped[di + 1] = data[si + 1];
    cropped[di + 2] = data[si + 2]; cropped[di + 3] = data[si + 3];
  }
}
const padded = await sharp(cropped, { raw: { width: bw, height: bh, channels: 4 } })
  .extend({
    top: Math.floor((side - bh) / 2), bottom: Math.ceil((side - bh) / 2),
    left: Math.floor((side - bw) / 2), right: Math.ceil((side - bw) / 2),
    background: { r: 0, g: 0, b: 0, alpha: 0 }
  })
  .png()
  .toFile(OUT);
console.log(`OK brand/icon-raw.png: ${side}x${side}, alpha OK, tile #${hex(tile.r)}${hex(tile.g)}${hex(tile.b)}`);
