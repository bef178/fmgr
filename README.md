# fmgr

A fast, solid, clean, and intuitive file manager for Android.

## 特色功能

- async loading
  - 流式显示目录子项，异步加载详情，不卡UI
- scroll restoration & highlight recent
  - 高亮来时路
- search
  - 按路径、内容查找，查找空目录/空文件，查找重复文件

## Develop

```shell
./gradlew installDebug
# adb install -r app/target/outputs/apk/debug/app-debug.apk
adb shell am start -n pd.droidapp.fmgr/.FmgrActivity
```

## 设计语言

- 勾代表确认，叉代表否认
- Cancel 这个词有时代表关闭，有时代表中止，要仔细区分
- filetype 图标
  - 底图为文本文件图标
  - 横条覆于底图之上
    - 设计尺寸 21x10，左上角坐标 (8,8.5)。坐标轴 x 向右，y 向下。
  - 类型文本书于横条之内
    - 字形 Arial Bold，帽高 6，基线相对横条底部+2。不设格宽、不做逐字缩放
    - 首字定位：
      - A/V/W/X：斜向伸展，按墨迹中线锚定 x=12.5
      - C/G/O/Q：圆弧，向左让出纵向 overshoot 的量（Arial 系帽高 6 时约为 0.102）
      - 其它：墨迹左缘定位在 x=10
    - 续字按原 advance + kerning 自然接排
    - 横条右侧随末字逻辑右侧移动(步进1)，应保持 margin=2
      - 计算末字定位时与首字定位一致，末字 C 无圆弧修正
      - 若文字能完整显示但右 margin 已越界，则截断横条
      - 若文字墨迹被截断，则将文字整体压缩至 0.88，含字形与 advance
  - 配色
    - 按探测：彩色背景，白色文字
      - 大写字母 (WebM/WebP/fLaC 除外)
      - apk/doc/pdf 品牌色
      - 音频 紫
      - 视频 玫红
      - 图片 绿
      - 文档 天蓝
      - 压缩 亮棕
    - 按后缀：较淡的黄背景，彩色文字
      - 点 + 小写字母
      - txt 较深的灰色
      - md 文档蓝

# glry

A image viewer.

## feature

- 原图太大则采样
- 双指缩放旋转
- 三指翻页
- 双击在默认比例及 1:1 之间切换
