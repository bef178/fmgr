from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen

FONT_PATH = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'
CAP_HEIGHT = 1466
SCALE = 6.0 / CAP_HEIGHT
BASELINE = 16.5
COMPRESS_SCALE = 0.88
FONT = TTFont(FONT_PATH)
CMAP = FONT.getBestCmap()
GS = FONT.getGlyphSet()
HMTX = FONT['hmtx']
KERN = FONT['kern'].kernTables[0].kernTable

def num(v):
    r = round(v, 2)
    if abs(r) < 0.005:
        r = 0.0
    if abs(r - round(r)) < 1e-9:
        return str(int(round(r)))
    return ('%.2f' % r).rstrip('0').rstrip('.')

def gp(p, pen_x, anchor, compressed):
    x = pen_x + p[0] * SCALE
    if compressed:
        x = anchor + (x - anchor) * COMPRESS_SCALE
    return num(x) + ',' + num(BASELINE - p[1] * SCALE)

def glyph_path(ch, pen_x, anchor, compressed):
    gn = CMAP[ord(ch)]
    rp = RecordingPen()
    GS[gn].draw(rp)
    parts = []
    cur = None
    start = None
    for op, args in rp.value:
        if op == 'moveTo':
            p = args[0]
            start = p
            cur = p
            parts.append('M ' + gp(p, pen_x, anchor, compressed))
        elif op == 'lineTo':
            p = args[0]
            cur = p
            parts.append('L ' + gp(p, pen_x, anchor, compressed))
        elif op == 'qCurveTo':
            pts = list(args)
            if pts[-1] is None:
                offs = pts[:-1]
                end = ((offs[-1][0] + start[0]) / 2.0, (offs[-1][1] + start[1]) / 2.0)
            else:
                offs = pts[:-1]
                end = pts[-1]
            for i in range(len(offs) - 1):
                mid = ((offs[i][0] + offs[i+1][0]) / 2.0, (offs[i][1] + offs[i+1][1]) / 2.0)
                parts.append('Q ' + gp(offs[i], pen_x, anchor, compressed) + ' ' + gp(mid, pen_x, anchor, compressed))
            parts.append('Q ' + gp(offs[-1], pen_x, anchor, compressed) + ' ' + gp(end, pen_x, anchor, compressed))
            cur = end
        elif op == 'curveTo':
            pts = args
            parts.append('C ' + ' '.join(gp(p, pen_x, anchor, compressed) for p in pts))
            cur = pts[-1]
        elif op == 'closePath':
            parts.append('Z')
    return ' '.join(parts)

def bounds(ch):
    from fontTools.pens.boundsPen import BoundsPen
    gn = CMAP[ord(ch)]
    bp = BoundsPen(GS)
    GS[gn].draw(bp)
    return bp.bounds

def first_anchor(ch):
    if ch in 'AXWV':
        return 12.5
    if ch in 'OCGQ':
        return 9.898
    return 10.0

def first_pen_x(ch):
    xmin, ymin, xmax, ymax = bounds(ch)
    anchor = first_anchor(ch)
    if ch in 'AXWV':
        return anchor - (xmin + xmax) / 2.0 * SCALE
    return anchor - xmin * SCALE

def layout(text):
    pens = []
    pen = first_pen_x(text[0])
    prev = CMAP[ord(text[0])]
    for i, ch in enumerate(text):
        if i > 0:
            pen += (HMTX[prev][0] + KERN.get((prev, CMAP[ord(ch)]), 0)) * SCALE
        pens.append(pen)
        prev = CMAP[ord(ch)]
    anchor = first_anchor(text[0])
    compressed = last_logical_right(text, pens) > 32
    return pens, anchor, compressed

def last_logical_right(text, pens):
    ch = text[-1]
    xmin, ymin, xmax, ymax = bounds(ch)
    pen = pens[-1]
    if ch in 'AXWV':
        return pen + (xmin + xmax) / 2.0 * SCALE + 2.5
    if ch in 'OGQP':
        return pen + xmax * SCALE - 0.102
    return pen + xmax * SCALE

def bar_right(text, pens, anchor, compressed):
    import math
    right = last_logical_right(text, pens)
    if compressed:
        right = anchor + (right - anchor) * COMPRESS_SCALE
    return min(32, math.ceil(round(right + 2, 6)))

if __name__ == '__main__':
    import sys
    for text in sys.argv[1:]:
        pens, anchor, compressed = layout(text)
        print(text, 'pens', [round(p,4) for p in pens], 'anchor', anchor, 'compressed', compressed,
              'lastRight', round(last_logical_right(text,pens),4), 'bar', bar_right(text,pens,anchor,compressed))
        for ch, pen in zip(text, pens):
            print(' ', ch, glyph_path(ch, pen, anchor, compressed))
