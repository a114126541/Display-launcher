# Privacy

**English** · [简体中文](#简体中文)

Display Launcher does not declare the Android `INTERNET` permission. It needs no account and contains no third-party runtime libraries.

## Local data

The app stores the chosen interface language and the package name and label of the last app it launched in local Android preferences. This is launch history, not proof of what is currently visible. Uninstalling the app removes its app data.

## Optional live status

Basic app launching works without Accessibility access. On Android 11 or later, users can opt in to live status through Android's Accessibility settings.

Android grants broad window access for this capability. The current implementation reads only the package name from the top application window on the selected secondary display. It does not inspect page text, traverse page contents, perform gestures, or click controls in other apps. It polls while the launcher UI is visible; the system-managed service may remain active while authorized.

When access is disabled, the UI returns to launch history. If access is enabled but a window cannot be identified, the UI reports that state rather than substituting an old launch record.

## Diagnostics

Android diagnostic logs may include app package names and display IDs, along with launch errors. The app does not upload these logs. QA scripts run separately on a computer and can save screenshots, UI trees, and system state locally; these raw results are excluded from the repository. Review them before sharing.

## 简体中文

Display Launcher 未声明 Android 的 `INTERNET` 权限，不需要账号，也没有第三方运行时库。

### 本地数据

应用在本地 Android 偏好设置中保存界面语言，以及最后一次由本工具启动的应用包名和名称。这是启动记录，不代表当前屏幕状态。卸载应用会移除其应用数据。

### 可选的实时状态

基本启动功能不需要无障碍权限。Android 11 及以上的用户可以选择在系统无障碍设置中开启实时状态。

Android 为此授予的窗口访问权限较广。当前实现只从所选副屏最上层应用窗口中取得包名，不检查页面文字、不遍历页面内容、不执行手势，也不点击其他应用的控件。工具界面可见时才进行轮询；已授权的服务可能继续由系统保持运行。

关闭权限后，界面恢复为启动记录。已授权但无法识别窗口时，会显示对应状态，不会将旧的启动记录当作实时结果。

### 诊断信息

Android 诊断日志可能包含应用包名、屏幕编号和启动错误，应用不会上传这些日志。独立运行在电脑上的测试脚本可能在本地保存截图、界面树和系统状态，这些原始结果不包含在仓库中。分享前请先检查内容。
