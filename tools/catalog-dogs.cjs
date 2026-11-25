/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
// Read-only cache inspection. Formats documented by RuneLite's cache module:
// https://github.com/runelite/runelite/tree/master/cache/src/main/java/net/runelite/cache
// Run from dog-mode: node tools/catalog-dogs.cjs <cache directory> <Gradle ant JAR>
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { execFileSync } = require('node:child_process');
const [cache, antJar] = process.argv.slice(2);
const output = path.resolve('build/dog-catalog');
fs.mkdirSync(output, { recursive: true });
class Reader {
    constructor(data) { this.data = data; this.pos = 0; }
    u8() { return this.data.readUInt8(this.pos++); }
    u16() { const v = this.data.readUInt16BE(this.pos); this.pos += 2; return v; }
    i32() { const v = this.data.readInt32BE(this.pos); this.pos += 4; return v; }
    smart() { return this.data[this.pos] & 128 ? this.i32() & 0x7fffffff : this.u16(); }
    string() { const end = this.data.indexOf(0, this.pos); if (end < 0) throw Error('Unterminated string'); const v = this.data.toString('latin1', this.pos, end); this.pos = end + 1; return v; }
    skip(n) { this.pos += n; if (this.pos > this.data.length) throw Error('Truncated definition'); }
}
function archive(index, id) {
    const idx = fs.readFileSync(path.join(cache, `main_file_cache.idx${index}`));
    const length = idx.readUIntBE(id * 6, 3);
    let sector = idx.readUIntBE(id * 6 + 3, 3);
    if (!length || !sector) throw Error(`Archive ${index}/${id} is not cached`);
    const result = Buffer.alloc(length);
    const file = fs.openSync(path.join(cache, 'main_file_cache.dat2'), 'r');
    try {
        let written = 0, chunk = 0;
        while (written < length) {
            const block = Buffer.alloc(520);
            fs.readSync(file, block, 0, 520, sector * 520);
            if (block.readUInt16BE(0) !== id || block.readUInt16BE(2) !== chunk++ || block[7] !== index) throw Error('Invalid cache sector');
            sector = block.readUIntBE(4, 3);
            const amount = Math.min(512, length - written);
            block.copy(result, written, 8, 8 + amount);
            written += amount;
        }
    } finally { fs.closeSync(file); }
    const type = result[0], size = result.readInt32BE(1);
    if (type === 0) return result.subarray(5, 5 + size);
    let decompressed;
    if (type === 2) decompressed = zlib.gunzipSync(result.subarray(9, 9 + size));
    else if (type === 1) {
        const compressedFile = path.join(output, `archive-${index}-${id}.bz`);
        const rawFile = path.join(output, `archive-${index}-${id}.raw`);
        fs.writeFileSync(compressedFile, result.subarray(9, 9 + size));
        execFileSync('java', ['-cp', antJar, 'tools/Bunzip.java', compressedFile, rawFile]);
        decompressed = fs.readFileSync(rawFile);
    } else throw Error(`Unknown compression ${type}`);
    if (decompressed.length !== result.readInt32BE(5)) throw Error('Wrong decompressed length');
    return decompressed;
}
function indexFiles(data, wanted) {
    const r = new Reader(data), protocol = r.u8();
    if (protocol >= 6) r.i32();
    const flags = r.u8(), number = () => protocol >= 7 ? r.smart() : r.u16();
    if (flags & ~5) throw Error(`Unsupported index flags ${flags}`);
    const count = number(), ids = [];
    let id = 0;
    for (let i = 0; i < count; i++) ids.push(id += number());
    if (flags & 1) r.skip(4 * count);
    r.skip(4 * count); // CRCs.
    if (flags & 4) r.skip(8 * count);
    r.skip(4 * count); // Revisions.
    const sizes = ids.map(() => number());
    for (let i = 0; i < count; i++) {
        let last = 0;
        const files = Array.from({ length: sizes[i] }, () => last += number());
        if (ids[i] === wanted) return files;
    }
    throw Error('Missing NPC archive in index');
}
function split(data, ids) {
    const chunks = data[data.length - 1];
    const table = new Reader(data); table.pos = data.length - 1 - chunks * ids.length * 4;
    const out = ids.map(() => []); let pos = 0;
    for (let i = 0; i < chunks; i++) {
        let size = 0;
        for (let j = 0; j < ids.length; j++) {
            size += table.i32();
            out[j].push(data.subarray(pos, pos + size)); pos += size;
        }
    }
    if (pos !== data.length - 1 - chunks * ids.length * 4) throw Error('Invalid chunk table');
    return new Map(ids.map((id, i) => [id, Buffer.concat(out[i])]));
}
function npc(id, data) {
    const r = new Reader(data), result = { id, name: '', idle: -1, walk: -1, run: -1, models: [], recolors: [], textures: [], width: 128, height: 128, ambient: 0, contrast: 0 };
    while (true) {
        const op = r.u8(); if (!op) break;
        if (op === 1 || op === 61) result.models = Array.from({ length: r.u8() }, () => op === 1 ? r.u16() : r.i32());
        else if (op === 2) result.name = r.string();
        else if (op === 12) r.u8();
        else if (op === 13) result.idle = r.u16();
        else if (op === 14) result.walk = r.u16();
        else if (op === 15 || op === 16 || op === 18) r.u16();
        else if (op === 17) { result.walk = r.u16(); r.skip(6); }
        else if (op >= 30 && op <= 34) r.string();
        else if (op === 40 || op === 41) result[op === 40 ? 'recolors' : 'textures'] = Array.from({ length: r.u8() }, () => [r.u16(), r.u16()]);
        else if (op === 42) { result.fullRecolor = r.u16(); }
        else if (op === 60 || op === 62) r.skip(r.u8() * (op === 60 ? 2 : 4));
        else if ((op >= 74 && op <= 79) || [95, 103, 116, 124, 126, 146].includes(op)) r.u16();
        else if (op === 97) result.width = r.u16();
        else if (op === 98) result.height = r.u16();
        else if (op === 100 || op === 101) result[op === 100 ? 'ambient' : 'contrast'] = (r.u8() << 24) >> 24;
        else if ([93, 99, 107, 109, 111, 122, 123, 129, 130, 145, 147].includes(op)) {}
        else if (op === 102) { const bits = r.u8(); for (let i = 0; i < 8; i++) if (bits & 1 << i) { r.smart(); if (r.data[r.pos] & 128) r.u16(); else r.u8(); } }
        else if (op === 106 || op === 118) { r.skip(op === 106 ? 4 : 6); r.skip((r.u8() + 1) * 2); result.morph = true; }
        else if (op === 114) result.run = r.u16();
        else if (op === 115) { result.run = r.u16(); r.skip(6); }
        else if (op === 117) r.skip(8);
        else if (op === 249) { const n = r.u8(); for (let i = 0; i < n; i++) { const str = r.u8(); r.skip(3); if (str) r.string(); else r.i32(); } }
        else throw Error(`NPC ${id}: unsupported opcode ${op} at ${r.pos - 1}`);
    }
    if (r.pos !== data.length) throw Error(`NPC ${id}: trailing bytes`);
    return result;
}
const ids = indexFiles(archive(255, 2), 9);
const files = split(archive(2, 9), ids);
const constants = fs.readFileSync(path.join(output, 'npc-ids.txt'), 'utf8');
const entries = [...constants.matchAll(/int (\w+) = (\d+);/g)]
    .filter(m => /DOG|LABRADOR|CHIHUAHUA|COLLIE|CORGI|GREYHOUND|HUSKY|PUG|SAMOYED|SHEPARD|SHIBA|SPANIEL|YORKIE|HELLPUP|HELLPET|HELLHOUND|BLOODHOUND/.test(m[1]))
    .filter(m => !/HANDLER|SERVANT|PILLAR_ATTACKER|LINK_ATTACKER/.test(m[1]))
    .filter(m => files.has(+m[2]))
    .map(m => ({ symbol: m[1], ...npc(+m[2], files.get(+m[2])) }));
fs.writeFileSync(path.join(output, 'definitions.json'), JSON.stringify(entries, null, 2));
console.log(`Read ${ids.length} NPC definitions; inspected ${entries.length} dog candidates.`);
for (const d of entries.filter(d => d.models.length && !d.morph)) console.log(`${d.id} ${d.symbol}: ${d.name}, idle=${d.idle}, walk=${d.walk}, models=${d.models}, textures=${JSON.stringify(d.textures)}`);
