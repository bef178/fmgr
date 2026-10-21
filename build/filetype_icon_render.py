import sys
from filetype_icon_layout import layout, glyph_path, bar_right

HEAD = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">

    <path
        android:pathData="M 26,16 v -11.8 c 0,-0.85 -0.85,-1.7 -1.7,-1.7 h -16.6 c -0.85,0 -1.7,0.85 -1.7,1.7 v 23.6 c 0,0.85 0.85,1.7 1.7,1.7 h 16.6 c 0.85,0 1.7,-0.85 1.7,-1.7 z"
        android:strokeWidth="1.6"
        android:strokeColor="#000000" />
    <path
        android:pathData="M 9.33,7 H 22.67 M 9.33,11.5 H 22.67 M 9.33,16 H 22.67 M 9.33,20.5 H 22.67 M 9.33,25 H 22.67"
        android:strokeWidth="0.8"
        android:strokeColor="#000000" />
'''

def render(text, color):
    pens, anchor, compressed = layout(text)
    bar = bar_right(text, pens, anchor, compressed)
    out = HEAD
    out += '    <path\n        android:pathData="m 8,8.5 h %d v 10 h -%d z"\n        android:fillColor="%s" />\n' % (bar - 8, bar - 8, color)
    for ch, pen in zip(text, pens):
        out += '    <path\n        android:pathData="%s"\n        android:fillColor="#FFFFFF" />\n' % glyph_path(ch, pen, anchor, compressed)
    out += '\n</vector>\n'
    return out

if __name__ == '__main__':
    sys.stdout.write(render(sys.argv[1], sys.argv[2]))
