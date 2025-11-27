# 📦 Podium Android Release Build Guide

本文档说明如何配置和构建Podium的Android release包。

## 🔐 签名配置

### 1. 生成签名密钥（首次发布）

```bash
keytool -genkey -v -keystore podium-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias podium
```

按提示输入：
- Keystore密码
- Key密码  
- 组织信息等

**重要提示：** 
- 请妥善保管 `.jks` 文件和密码
- 密钥一旦丢失，将无法更新已发布的应用
- 建议备份到安全的位置

### 2. 配置签名信息

```bash
# 复制示例文件
cp keystore.properties.example keystore.properties

# 编辑配置文件，填入实际信息
# 注意：此文件已添加到.gitignore，不会被提交
```

`keystore.properties` 内容：
```properties
storeFile=podium-release.jks
storePassword=你的keystore密码
keyAlias=podium
keyPassword=你的key密码
```

## 🏗️ 构建Release包

### 构建所有变体

```bash
# 构建所有release APK（包含分离ABI和通用包）
./gradlew assembleRelease
```

生成的APK位于：`composeApp/build/outputs/apk/release/`

### APK类型说明

构建完成后会生成以下APK：

| APK文件 | 架构 | 大小 | 用途 |
|---------|------|------|------|
| `app-armeabi-v7a-release.apk` | 32位ARM | ~小 | 旧设备 |
| `app-arm64-v8a-release.apk` | 64位ARM | ~小 | 现代设备（推荐） |
| `app-x86-release.apk` | 32位x86 | ~小 | 模拟器/特殊设备 |
| `app-x86_64-release.apk` | 64位x86 | ~小 | 模拟器/特殊设备 |
| `app-universal-release.apk` | 全平台 | ~大 | 兼容所有设备 |

**推荐发布策略：**
- Google Play：上传所有APK，让系统自动分发对应架构
- 直接分发：提供 `universal` APK（兼容性最好）和 `arm64-v8a` APK（现代设备专用）

## ⚙️ 构建配置说明

### 代码混淆

Release包已启用R8混淆和资源缩减：
- ✅ 代码混淆（减小体积，提高安全性）
- ✅ 资源缩减（移除未使用资源）
- ✅ 优化级别：5次优化
- ✅ 自动移除Log输出

混淆规则文件：`composeApp/proguard-rules.pro`

### 版本号管理

版本号自动计算规则：
```
versionCode = baseVersionCode * 10 + abiCode

ABI Codes:
- armeabi-v7a: 1
- arm64-v8a: 2  
- x86: 3
- x86_64: 4
```

示例：
- 基础版本 = 1
- arm64-v8a APK versionCode = 12
- armeabi-v7a APK versionCode = 11

## 🚀 发布流程

### 本地构建发布

1. 确保已配置签名：`keystore.properties` 存在
2. 运行构建命令：`./gradlew assembleRelease`
3. 测试APK：安装到设备测试
4. 发布到商店或分发平台

### 使用GitHub Actions（推荐）

本项目支持GitHub Actions自动构建：

1. 在GitHub仓库设置中添加Secrets：
   - `KEYSTORE_BASE64`: keystore文件的base64编码
   - `KEYSTORE_PASSWORD`: keystore密码
   - `KEY_ALIAS`: key别名
   - `KEY_PASSWORD`: key密码

2. 推送tag触发自动构建：
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. Actions会自动构建并上传APK到GitHub Releases

## 🔍 验证Release包

### 检查签名

```bash
# 查看APK签名信息
keytool -printcert -jarfile composeApp/build/outputs/apk/release/app-arm64-v8a-release.apk
```

### 检查混淆

```bash
# 查看mapping文件（用于还原混淆后的堆栈）
cat composeApp/build/outputs/mapping/release/mapping.txt
```

### 安装测试

```bash
# 安装到设备测试
adb install composeApp/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## 📝 常见问题

### Q: 没有keystore.properties时能构建吗？

A: 可以。构建系统会使用debug签名。但不建议用于正式发布。

### Q: 如何更新已发布应用？

A: 使用相同的keystore文件和配置，增加versionCode和versionName。

### Q: 混淆后崩溃如何调试？

A: 使用mapping文件还原堆栈：
```bash
retrace.sh mapping.txt crash_stacktrace.txt
```

### Q: 可以只构建某个ABI吗？

A: 可以，使用：
```bash
./gradlew assembleArm64-v8aRelease
```

## 🔒 安全提示

- ❌ 不要将 `keystore.properties` 提交到Git
- ❌ 不要将 `.jks` 文件提交到Git  
- ✅ 定期备份keystore文件
- ✅ 使用强密码
- ✅ 在CI/CD中使用加密的Secrets

## 📚 相关资源

- [Android应用签名](https://developer.android.com/studio/publish/app-signing)
- [ProGuard规则](https://www.guardsquare.com/manual/configuration/usage)
- [APK拆分](https://developer.android.com/studio/build/configure-apk-splits)
