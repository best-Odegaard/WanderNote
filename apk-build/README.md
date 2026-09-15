# APK 打包工程（Capacitor 封装）

把 `frontend/` 的 uni-app **H5 构建产物**用 Capacitor 包成原生 Android 应用，最终产出可安装的 APK。

## 为什么不走 uni-app 官方 App 打包

uni-app 出 APK 有两条官方路径，本机都跑不通：

| 路径 | 卡在哪 |
|---|---|
| HBuilderX 云打包 | 需要登录 DCloud 账号 + 真实 appid（当前 `manifest.json` 里是 `__UNI__WANDERNOTE`，是占位值），且 HBuilderX 里没装 App 打包插件 |
| uni-app Android 离线打包 | 需要从 DCloud 下载「App 离线打包 SDK」和 appkey，同样要账号 |

所以改用 Capacitor：先出 H5 产物，再套一层原生 WebView 壳。功能上等价（本项目用的是 H5 兼容 API，路由是 hash 模式，套壳后无副作用），且全程离线可复现。

## 前置条件

| 依赖 | 本机版本 | 说明 |
|---|---|---|
| Node.js | v24 | 跑 `frontend` 的构建和 `cap` 命令 |
| JDK | 17 | AGP 8.2 要求 17+ |
| Android SDK | `D:\Android\Sdk`（platform 34 + build-tools 34.0.0） | 路径写在 `android/local.properties` |
| Gradle | 8.5（系统安装版） | **不要用 `./gradlew`**，见下方说明 |
| Python + Pillow | 3.11 | 只在重新生成图标时需要 |

## 完整打包流程

```bash
# 1) 构建 H5 产物（在 frontend/ 下）
cd frontend
npm install
npm run build:h5          # 产出 frontend/dist/build/h5

# 2) 同步进 Android 工程并打包（在 apk-build/ 下）
cd ../apk-build
npm install
npm run apk:release       # 产出 android/app/build/outputs/apk/release/app-release.apk
```

日常只改了前端代码时，重复第 1、2 步即可。

## 几个必须知道的坑（都已在工程里绕过了）

1. **工程路径含中文会直接构建失败。**
   项目在 `D:\geek\AI文旅\...`，AGP 检测到非 ASCII 路径会拒绝构建。已在 `android/gradle.properties` 加 `android.overridePathCheck=true` 关掉这道检查（本项目无 native 代码，aapt2 处理 UTF-8 路径没问题）。

2. **lint 在中文路径下会崩。**
   `lintVitalAnalyzeRelease` 报 `Internal error: Unexpected lint invalid arguments`。已在根 `build.gradle` 用 `subprojects` 对所有 Android 模块关掉 `checkReleaseBuilds`。

3. **不要用 `./gradlew`，用系统 `gradle`。**
   wrapper 配的是 `services.gradle.org`，本机不通；而 `~/.gradle/wrapper/dists` 里缓存的那份 8.2.1 对应的不是这个 URL，wrapper 会尝试重新下载并超时。系统装的 Gradle 8.5 与 AGP 8.2.1 兼容，直接用它。

4. **Maven 中央仓库不通，走阿里云镜像。**
   根 `build.gradle` 里仓库顺序是「阿里云 public → 阿里云 google → google() → mavenCentral()」。

5. **后端是 http 明文。**
   `AndroidManifest.xml` 已加 `android:usesCleartextTraffic="true"`，否则 Android 9+ 会拦掉所有 http 请求（真机连局域网后端时会踩到）。

6. **图标源图是「改了扩展名的 JPEG」。**
   `豆包生产物料/app图标/` 下只有 `用户选中_原图.png` 是真 PNG（1024×1024），其余 12 个文件实际是 JPEG。另外源图四角是白色而非透明，`scripts/gen-android-assets.py` 会按原设计半径重新裁圆角遮罩，把白角变成透明。

## 重新生成图标 / 启动图

```bash
python scripts/gen-android-assets.py
```

会覆盖 `android/app/src/main/res/` 下的 `mipmap-*/ic_launcher*.png` 和 `drawable-*/splash_logo.png`。
自适应图标的背景层用的是 `drawable/ic_launcher_bg.xml`（与源图同色系的对角渐变），前景层是满幅位图、圆角处透明，所以启动器遮罩裁完之后看不到白边或接缝。

## Release 签名

签名配置在 `android/keystore.properties`（**已在 .gitignore 里，不入库**）：

```properties
storeFile=wandernote-release.jks
storePassword=wandernote2026
keyAlias=wandernote
keyPassword=wandernote2026
```

> 换机器打包时，把 `wandernote-release.jks` 和 `keystore.properties` 一起带过去。
> **升级覆盖安装必须用同一个签名**，换了签名只能先卸载再装（会丢本地数据）。
> 这个 keystore 只是为了自己装手机用，要上架应用商店请另建一个并妥善保管。

`keystore.properties` 缺失时 release 走未签名分支，不影响 debug 构建，也不影响别人 clone 下来编译。

## 目录说明

```
apk-build/
├── capacitor.config.json      # appId / appName / webDir
├── package.json
├── scripts/
│   ├── copy-www.mjs           # frontend/dist/build/h5 -> www/
│   └── gen-android-assets.py  # 生成图标与启动图
├── www/                       # H5 产物副本（生成物，不入库）
└── android/                   # Capacitor 生成的 Android 工程
    ├── keystore.properties    # 本机签名配置（不入库）
    ├── wandernote-release.jks # 签名密钥（不入库）
    └── local.properties       # Android SDK 路径（不入库）
```
