/**
 * 生成 81x81 TabBar 图标（微信要求有效 PNG，不可用 1x1 占位）
 * 运行: node scripts/gen-tab-icons.js
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

function crc32(buf) {
  let c = ~0
  for (let i = 0; i < buf.length; i += 1) {
    c ^= buf[i]
    for (let k = 0; k < 8; k += 1) {
      c = (c >>> 1) ^ (0xedb88320 & -(c & 1))
    }
  }
  return ~c >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const typeBuf = Buffer.from(type)
  const crcBuf = Buffer.alloc(4)
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0)
  return Buffer.concat([len, typeBuf, data, crcBuf])
}

function pngRgb(w, h, r, g, b) {
  const raw = Buffer.alloc((w * 3 + 1) * h)
  for (let y = 0; y < h; y += 1) {
    const row = y * (w * 3 + 1)
    raw[row] = 0
    for (let x = 0; x < w; x += 1) {
      const i = row + 1 + x * 3
      raw[i] = r
      raw[i + 1] = g
      raw[i + 2] = b
    }
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0)
  ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8
  ihdr[9] = 2
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0
  const idat = zlib.deflateSync(raw, { level: 9 })
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', idat),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

const dir = path.join(__dirname, '..', 'static', 'tab')
const gray = [100, 116, 139]
const blue = [22, 119, 255]
const pairs = [
  ['home.png', gray],
  ['home-active.png', blue],
  ['message.png', gray],
  ['message-active.png', blue],
  ['mine.png', gray],
  ['mine-active.png', blue],
]
pairs.forEach(function ([name, rgb]) {
  fs.writeFileSync(path.join(dir, name), pngRgb(81, 81, rgb[0], rgb[1], rgb[2]))
  console.log('wrote', name)
})
