#!/usr/bin/env python3
"""
Converts the CC0 models the game uses (Kenney, Quaternius; see app/src/main/assets/models/CREDITS.md)
from OBJ into the game's own small binary meshes, in app/src/main/assets/models/.

The game's renderer has no general textures: colours are per vertex. So
- a model's colour-map texture (Kenney) or its opaque textures (bark, rock) are sampled once per
  vertex, from a blurred copy, into vertex colours;
- cut-out textures (leaves, petals, grass: they have transparent pixels) stay textures, kept small,
  and their faces are a "leaf" part drawn by the leaf shader.

Run once after downloading the packs (it reads, never runs, the downloaded files):
    python3 -I tools/assets/convert_models.py /path/to/deepline-assets

Format of a .dlm file (little-endian): b"DLM1", u32 parts; per part: u8 kind (0 painted, 1 leaf),
u16 texture name length + utf-8 name (empty for painted), u32 vertices, u32 indices, then floats
positions (3), normals (3), uvs (2), colours (4) per vertex, then u32 indices.
"""
import os
import struct
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "models")
TEX_OUT = os.path.join(OUT, "tex")

# (pack folder, OBJ folder inside it, texture folder inside it, model names)
PACKS = [
    ("kenney-pirate-kit", "extracted/Models/OBJ format", "extracted/Models/OBJ format/Textures", [
        "palm-detailed-bend", "palm-detailed-straight", "barrel", "crate", "boat-row-small", "rocks-sand-a", "grass-plant",
    ]),
    ("kenney-watercraft-kit", "extracted/Models/OBJ format", "extracted/Models/OBJ format/Textures", [
        "buoy-flag",
    ]),
    ("quaternius-nature-megakit", "extracted/OBJ", "extracted/Textures", [
        "CommonTree_3", "Bush_Common", "Flower_3_Group", "Rock_Medium_1", "Rock_Medium_2",
        "Pebble_Round_1", "Pebble_Round_2", "Pebble_Square_1", "Pebble_Square_2", "Plant_1", "Plant_7", "Grass_Common_Short",
    ]),
]
LEAF_TEXTURE_SIZE = 256
# Leaf cards kept (one in [n]) and how much the kept ones grow: the same look with less overdraw,
# which is what cut-out leaves cost on a phone's GPU.
THIN_LEAVES = {"CommonTree_3": (2, 1.3), "Bush_Common": (2, 1.25)}
# Cut-out textures recoloured for the island: the bush's leaves come autumn red, the game's are green.
# The tree's olive leaves take the island's own greens (its lawns and bushes) too.
RECOLOR = {
    "Leaves_TwistedTree_C.png": ((0x56, 0xA8, 0x4C), "Leaves_Bush.png"),
    "Leaves_NormalTree_C.png": ((0x5C, 0xB2, 0x4E), "Leaves_Tree.png"),
}


def recolor(im, rgb):
    """[im] in shades of [rgb], keeping its light and dark and its alpha."""
    lum = im.convert("L")
    out = Image.new("RGBA", im.size)
    px, lp = out.load(), lum.load()
    a = im.getchannel("A").load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            k = 0.55 + 0.9 * lp[x, y] / 255.0
            px[x, y] = (min(255, int(rgb[0] * k)), min(255, int(rgb[1] * k)), min(255, int(rgb[2] * k)), a[x, y])
    return out


def read_mtl(path):
    mats, cur = {}, None
    if not os.path.exists(path):
        return mats
    for line in open(path, encoding="utf-8", errors="replace"):
        p = line.split()
        if not p:
            continue
        if p[0] == "newmtl":
            cur = mats.setdefault(" ".join(p[1:]), {"Kd": (1.0, 1.0, 1.0), "map": None})
        elif cur is not None and p[0] == "Kd":
            cur["Kd"] = tuple(float(x) for x in p[1:4])
        elif cur is not None and p[0] == "map_Kd":
            cur["map"] = os.path.basename(" ".join(p[1:]).replace("\\", "/"))
    return mats


_images = {}


def texture(folder, name):
    key = (folder, name)
    if key not in _images:
        im = Image.open(os.path.join(folder, name)).convert("RGBA")
        alpha = im.getchannel("A").getextrema()[0] < 128
        # A blurred copy for per-vertex sampling: a vertex takes its neighbourhood's colour.
        small = im.convert("RGB").resize((32, 32), Image.BOX)
        _images[key] = (im, small, alpha)
    return _images[key]


def sample(small, u, v):
    w, h = small.size
    x = min(w - 1, max(0, int((u % 1.0) * w)))
    y = min(h - 1, max(0, int((1.0 - (v % 1.0)) * h)))
    r, g, b = small.getpixel((x, y))
    return r / 255.0, g / 255.0, b / 255.0


def thin(verts, ids, keep_one_in, grow):
    """Keeps one leaf card in [keep_one_in] (cards: triangles sharing vertices), each a bit bigger."""
    parent = list(range(len(verts)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for t in range(0, len(ids), 3):
        a, b, c = find(ids[t]), find(ids[t + 1]), find(ids[t + 2])
        parent[b] = a
        parent[find(c)] = a
    cards = {}
    for t in range(0, len(ids), 3):
        cards.setdefault(find(ids[t]), []).append(t)
    kept, out_ids = set(), []
    for k, (root, tris) in enumerate(sorted(cards.items())):
        if k % keep_one_in:
            continue
        vs = sorted({ids[t + j] for t in tris for j in range(3)})
        cx = sum(verts[v][0][0] for v in vs) / len(vs); cy = sum(verts[v][0][1] for v in vs) / len(vs); cz = sum(verts[v][0][2] for v in vs) / len(vs)
        for v in vs:
            if v in kept:
                continue
            kept.add(v)
            p, n, uv, col = verts[v]
            verts[v] = ((cx + (p[0] - cx) * grow, cy + (p[1] - cy) * grow, cz + (p[2] - cz) * grow), n, uv, col)
        for t in tris:
            out_ids += ids[t:t + 3]
    # Drop the vertices nothing uses any more.
    remap, new_verts = {}, []
    for i in out_ids:
        if i not in remap:
            remap[i] = len(new_verts); new_verts.append(verts[i])
    return new_verts, [remap[i] for i in out_ids]


def convert(obj_path, tex_folder):
    mats = read_mtl(obj_path[:-4] + ".mtl")
    vs, vts, vns = [], [], []
    groups = {}  # material -> list of (v, vt, vn) triangles
    mat = None
    for line in open(obj_path, encoding="utf-8", errors="replace"):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            vs.append(tuple(float(x) for x in p[1:4]))
        elif p[0] == "vt":
            vts.append(tuple(float(x) for x in p[1:3]))
        elif p[0] == "vn":
            vns.append(tuple(float(x) for x in p[1:4]))
        elif p[0] == "usemtl":
            mat = " ".join(p[1:])
        elif p[0] == "f":
            idx = []
            for c in p[1:]:
                a = c.split("/")
                vi = int(a[0]); ti = int(a[1]) if len(a) > 1 and a[1] else 0; ni = int(a[2]) if len(a) > 2 and a[2] else 0
                idx.append((vi - 1 if vi > 0 else len(vs) + vi, ti - 1 if ti > 0 else (len(vts) + ti if ti < 0 else -1), ni - 1 if ni > 0 else (len(vns) + ni if ni < 0 else -1)))
            for k in range(1, len(idx) - 1):
                groups.setdefault(mat, []).append((idx[0], idx[k], idx[k + 1]))
    parts = []  # kind, texname, verts, indices
    painted = ([], [])
    for m, tris in groups.items():
        info = mats.get(m, {"Kd": (1, 1, 1), "map": None})
        tex = texture(tex_folder, info["map"]) if info["map"] and os.path.exists(os.path.join(tex_folder, info["map"])) else None
        leaf = tex is not None and tex[2]
        verts, ids = ([], []) if leaf else painted
        cache = {}
        for tri in tris:
            for corner in tri:
                if corner in cache:
                    ids.append(cache[corner]); continue
                vi, ti, ni = corner
                pos = vs[vi]
                uv = vts[ti] if ti >= 0 else (0.0, 0.0)
                n = vns[ni] if ni >= 0 else (0.0, 1.0, 0.0)
                kd = info["Kd"]
                if tex is not None and not leaf:
                    c = sample(tex[1], uv[0], uv[1]); col = (c[0] * kd[0], c[1] * kd[1], c[2] * kd[2], 1.0)
                else:
                    col = (kd[0], kd[1], kd[2], 1.0)
                cache[corner] = len(verts)
                ids.append(len(verts))
                verts.append((pos, n, uv, col))
        if leaf:
            name = info["map"]
            tint = RECOLOR.get(name)
            if tint:
                name = tint[1]
            model = os.path.basename(obj_path)[:-4]
            if model in THIN_LEAVES:
                verts, ids = thin(verts, ids, *THIN_LEAVES[model])
            parts.append((1, name, verts, ids))
            out = os.path.join(TEX_OUT, name)
            if not os.path.exists(out):
                im = tex[0].resize((LEAF_TEXTURE_SIZE, LEAF_TEXTURE_SIZE), Image.LANCZOS)
                if tint:
                    im = recolor(im, tint[0])
                im.save(out, optimize=True)
    if painted[0]:
        parts.insert(0, (0, "", painted[0], painted[1]))
    return parts


def write(path, parts):
    with open(path, "wb") as f:
        f.write(b"DLM1")
        f.write(struct.pack("<I", len(parts)))
        for kind, name, verts, ids in parts:
            nb = name.encode("utf-8")
            f.write(struct.pack("<BH", kind, len(nb)))
            f.write(nb)
            f.write(struct.pack("<II", len(verts), len(ids)))
            for pos, _, _, _ in verts: f.write(struct.pack("<3f", *pos))
            for _, n, _, _ in verts: f.write(struct.pack("<3f", *n))
            for _, _, uv, _ in verts: f.write(struct.pack("<2f", *uv))
            for _, _, _, c in verts: f.write(struct.pack("<4f", *c))
            f.write(struct.pack("<%dI" % len(ids), *ids))


def main():
    src = sys.argv[1]
    os.makedirs(TEX_OUT, exist_ok=True)
    for pack, objs, texs, names in PACKS:
        for n in names:
            parts = convert(os.path.join(src, pack, objs, n + ".obj"), os.path.join(src, pack, texs))
            out = os.path.join(OUT, n + ".dlm")
            write(out, parts)
            tris = sum(len(p[3]) // 3 for p in parts)
            xs = [v[0][0] for p in parts for v in p[2]]; ys = [v[0][1] for p in parts for v in p[2]]; zs = [v[0][2] for p in parts for v in p[2]]
            print("%-24s %6d tris  parts %s  x %.2f..%.2f  y %.2f..%.2f  z %.2f..%.2f  %6d B" % (
                n, tris, [(k, t) for k, t, _, _ in parts], min(xs), max(xs), min(ys), max(ys), min(zs), max(zs), os.path.getsize(out)))


if __name__ == "__main__":
    main()
