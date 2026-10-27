# fmgr

It is a fast, solid, clean, and intuitive file manager for Android.

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
