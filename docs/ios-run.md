## iOS 运行指南（Podium）

### 前置
- 安装 Xcode（用于模拟器/真机运行，提供 SDK 环境变量）。
- JDK 21（脚本会默认选用 `/usr/libexec/java_home -v 21`）。
- 已拉取完整仓库（路径示例：`/Users/xx/IdeaProjects/Podium`）。

### 快速运行（推荐：直接用 Xcode）
1. 打开 `iosApp/iosApp.xcodeproj`。
2. 选中 scheme `iosApp`，选择模拟器设备（如 iPhone 16 Pro）。
3. Product ▶ Run。首次会自动调用 Gradle 生成 Compose 框架。

### 彻底重建并运行（脚本）
在仓库根目录执行：
```bash
chmod +x scripts/rebuild_ios.sh   # 仅首次需要
./scripts/rebuild_ios.sh
```
脚本动作：
- 设定 `JAVA_HOME`（默认 JDK 21）
- 清理旧框架产物与 Xcode DerivedData
- 重建 Rust 音频库
- 重新链接 iOS 模拟器框架（arm64）

完成后回到 Xcode Run，或用命令行：
```bash
cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro'
```

### 仅重建框架（不清缓存）
如果只想更新框架而不清理：
```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --no-build-cache --rerun-tasks
```
随后在 Xcode Run。

### 常见提示
- 若出现 “Could not infer iOS target architectures” 或 `embedAndSignAppleFrameworkForXcode` 缺少变量，请从 Xcode 运行，或手动提供 `SDK_NAME/ARCHS/...` 环境（Xcode 会自动注入）。
- 迁移/数据库相关崩溃：若要彻底重置，删除模拟器里的 App 数据或卸载重装；代码已将迁移逻辑改为幂等，正常情况下不会因重复列/表崩溃。
