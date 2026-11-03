# Device validation

Tested on **2026-11-03** using an **FH156A17-D / RK3566**, **Android 11**, and two **1920 × 1080** displays. The APK ran under its normal application UID. The launcher uses Android launch APIs, not root or an in-app ADB client.

| Check | 检查项 | Result |
|---|---|---|
| English / Chinese switching and process-restart persistence | 中英文切换，进程重启后保留选择 | PASS |
| App search and an empty result | 应用搜索和空结果提示 | PASS |
| CDS cold and warm launches on the second display | CDS 在副屏冷启动和再次打开 | PASS |
| Picker stays on the main screen after launch | 启动后应用列表留在主屏 | PASS |
| Go to desktop button | 回到桌面按钮 | PASS |
| A different app, Android Settings, on the second display | 将系统 Settings 打开到副屏 | PASS |
| Move CDS from the main display to the second display | 将主屏 CDS 移到副屏 | PASS |
| Persistent launch history with an explicit history label | 保留启动记录，并明确标记为历史 | PASS |
| Optional Accessibility consent and service binding | 可选的无障碍授权及服务连接 | PASS |
| Live status follows an app change made outside the tool | 识别本工具之外发起的副屏应用切换 | PASS |
| Revoking access restores history mode | 撤销授权后恢复启动记录模式 | PASS |
| Other enabled Accessibility services are preserved | 保留原有的其他无障碍服务 | PASS |
| Desktop shortcut launches the tool | 从桌面图标打开工具 | PASS |
| Keyboard search action hides the keyboard | 搜索键收起键盘 | PASS |

The target apps checked were Loyverse CDS 1.8 and Android Settings. Live status followed an external change from CDS to Settings. Temporary Accessibility access used for testing was turned off afterward. These tests did not create sales or perform checkout.

The published APK is the same signed binary verified on the device. Raw device logs and UI dumps remain local. The public [English screenshot](../docs/images/main-en.png) and [Chinese screenshot](../docs/images/main-zh.png) show the shipping interface.

Full device reboot, physical display disconnection, several simultaneous secondary displays, other Android versions, and other device models were not tested. The app is installed through normal streamed APK installation, not host-dependent incremental installation.

## 简体中文

本次检查的目标应用是 Loyverse CDS 1.8 和系统 Settings。实时状态能跟随在本工具之外发起的 CDS 到 Settings 切换。测试结束后已关闭临时无障碍授权；测试没有创建销售单或进行结账。

发布的 APK 与设备上核验过的签名二进制文件一致。原始设备日志和界面树保留在本地，公开的中英文截图来自实际发布界面。

尚未测试整机重启、物理断开副屏、同时连接多块副屏、其他 Android 版本及其他设备型号。安装使用正常的 APK 流式安装，不依赖电脑持续提供增量安装文件。
