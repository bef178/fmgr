# File Manager for Android

## 特色功能

- async loading
  - 流式显示目录子项，异步加载详情，不卡UI
- scroll restoration & highlight recent
  - 后退、前进、向上到目录时，恢复到上次离开该目录时的滚动位置，并高亮刚才所在的项
- search
  - 按路径、内容查找，查找空目录/空文件，查找重复文件

## Develop

```shell
./gradlew installDebug
# adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n pd.droidapp.fmgr/.MainActivity
```
