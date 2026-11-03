# Development

**English** · [简体中文](#简体中文)

## Build

Requirements: Python 3, JDK 17 or later, Android SDK Platform 35, and Build Tools 35.0.0. Building has been verified on macOS; other hosts have not been tested.

```sh
python3 build.py --java-home /path/to/jdk --sdk /path/to/Android/sdk
```

`JAVA_HOME` can supply the JDK path. `ANDROID_HOME` or `ANDROID_SDK_ROOT` can supply the SDK path. The script also checks for `javac` on `PATH`; the system Java launcher stub on macOS is not treated as a JDK.

The build runs AAPT2, javac, D8, ZIP alignment, and APK signing. It verifies the signature and alignment, then writes the APK and `build-info.json` to `dist/`. There are no Gradle downloads or third-party runtime libraries.

On a fresh checkout, the script generates a local signing identity under `.keys/`. Keep this directory private and back it up if you want to update your own builds. A different signing key cannot update an already installed release APK. The repository does not contain the key used for published releases.

## Source layout

| Path | Purpose |
|---|---|
| `src/com/dylan/displaylauncher/MainActivity.java` | App list, search, language selection, display selection, launch history, and status UI |
| `src/com/dylan/displaylauncher/DisplayStatusService.java` | Optional window-owner lookup for live status on Android 11+ |
| `res/values/`, `res/values-zh/` | English and Simplified Chinese interface strings |
| `res/drawable-nodpi/` | Packaged desktop artwork and transparent interface logo |
| `build.py` | Local build and signing |
| `qa/` | Device test scripts and validation summary |

## Device tests

The scripts expect an authorized, already connected test device with two displays, English Android system settings, and Loyverse CDS installed. They use logical displays 0 and 1. They are device-specific QA fixtures, not a general installation script.

```sh
python3 qa/smoke_device.py --serial <device-serial>
```

This test navigates the UI, restarts the launcher and CDS, opens Settings on the second display, checks display assignments, and leaves the launcher in Chinese. Raw results are written to ignored `qa/evidence/`.

The optional status test enables Accessibility access through Android's consent UI and then revokes it. Run it only when you explicitly authorize those temporary changes on a test device. Live-status access must start off, CDS must be visible on display 1, and the launch history must already name CDS.

```sh
python3 qa/check_optional_status.py --serial <device-serial> --allow-temporary-access
```

This checks an app change made outside the launcher, restores the original enabled-service setting through the UI, and verifies the history fallback. If a device's settings UI differs, review the selectors before running it.

## Artwork

The desktop artwork in `assets/icon-desktop-opaque.png` was generated with ImageGen. The prompt is in `assets/icon-desktop-prompt.txt`. The transparent interface logo is in `assets/logo-interface-transparent.png`; its alpha edge was processed locally from the clean opaque artwork.

Optional image-development dependencies are Pillow, NumPy, and SciPy. They are not used by the Android app or its normal build.

```sh
python3 assets/make_transparent_logo.py
```

This regenerates the transparent logo, packaged header image, local previews, and matte report. The app uses separate image resources for the transparent in-app logo and pale-background desktop icon.

## 简体中文

### 构建

需要 Python 3、JDK 17 及以上、Android SDK Platform 35，以及 Build Tools 35.0.0。构建已在 macOS 上验证，其他构建环境尚未测试。

```sh
python3 build.py --java-home /path/to/jdk --sdk /path/to/Android/sdk
```

也可以用 `JAVA_HOME` 提供 JDK 路径，用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 提供 SDK 路径。脚本会检查 `PATH` 中的 `javac`，但不会将 macOS 的系统 Java 启动占位程序当作 JDK。

构建依次调用 AAPT2、javac、D8、ZIP 对齐和 APK 签名工具，验证签名和对齐后，将 APK 与 `build-info.json` 输出到 `dist/`。构建不下载 Gradle，也不引入第三方运行时库。

首次构建会在 `.keys/` 创建本地签名身份。若需要持续更新自己的构建版本，请私下备份这个目录。不同签名的 APK 无法覆盖更新已安装的发布版。仓库不包含正式发布版所用的签名密钥。

### 源码结构

| 路径 | 用途 |
|---|---|
| `src/com/dylan/displaylauncher/MainActivity.java` | 应用列表、搜索、语言和屏幕选择、启动记录及状态界面 |
| `src/com/dylan/displaylauncher/DisplayStatusService.java` | Android 11 及以上可选的窗口所属应用识别 |
| `res/values/`、`res/values-zh/` | 英文、简体中文界面文案 |
| `res/drawable-nodpi/` | 打包进应用的桌面图标和透明界面 logo |
| `build.py` | 本地构建和签名 |
| `qa/` | 设备测试脚本和验证摘要 |

### 设备测试

测试脚本要求设备已授权并连接 ADB，具备两块屏幕，系统设置语言为英文，并已安装 Loyverse CDS。脚本按逻辑屏幕 0 和 1 编写，是针对实测设备的测试流程，不是通用安装脚本。

```sh
python3 qa/smoke_device.py --serial <device-serial>
```

测试会操作界面、重启启动器和 CDS、将 Settings 打开到副屏，并核对屏幕分配；结束时将启动器留在中文界面。原始结果保存在已忽略的 `qa/evidence/`。

可选的状态测试会通过 Android 系统授权页临时开启无障碍访问，再撤销授权。仅在明确允许这些临时更改的测试设备上运行。开始前，实时状态权限应关闭，CDS 应在副屏 1 显示，启动记录也应已指向 CDS。

```sh
python3 qa/check_optional_status.py --serial <device-serial> --allow-temporary-access
```

该测试检查本工具之外发起的应用切换，通过系统界面恢复原有服务授权状态，并验证启动记录模式。如果设备设置界面不同，应先检查脚本中的界面选择条件。

### 图标资源

桌面图标原图为 `assets/icon-desktop-opaque.png`，由 ImageGen 生成，提示词保存在 `assets/icon-desktop-prompt.txt`。透明界面 logo 为 `assets/logo-interface-transparent.png`，由干净的实心底图通过本地蒙版处理得到。

图像开发脚本的可选依赖是 Pillow、NumPy 和 SciPy，安卓应用及正常 APK 构建不使用这些依赖。

```sh
python3 assets/make_transparent_logo.py
```

该脚本重新生成透明 logo、打包用的界面图片、本地预览和蒙版报告。应用内部使用透明 logo，桌面图标使用浅色实心底，两者是独立资源。
