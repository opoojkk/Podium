# 🔐 Podium Private Signing Repository Setup Guide

本文档说明如何创建和配置Podium的私有签名仓库，用于安全存储Android签名配置。

## 📖 概述

私有签名仓库用于存储敏感的签名配置文件：
- `keystore.properties` - 签名配置信息
- `podium-release.jks` - Android签名密钥文件

通过Git Submodule机制，可以将签名配置与主项目分离：
- ✅ 签名信息保持私密
- ✅ 不影响其他开发者克隆主项目
- ✅ 多台机器自动同步签名配置
- ✅ 独立的访问权限控制

## 🚀 快速开始

### 使用自动化脚本（推荐）

在Podium项目根目录运行：

```bash
./signing-setup.sh
```

脚本会自动引导你完成所有设置步骤。

### 手动设置

如果你更喜欢手动控制每一步，请继续阅读下面的详细说明。

---

## 📝 详细步骤

### 步骤1: 创建私有GitHub仓库

1. 登录GitHub
2. 点击右上角 **+** → **New repository**
3. 填写仓库信息：
   - **Repository name**: `Podium-Signing`（或其他名称）
   - **Description**: `Private signing configuration for Podium`
   - **Visibility**: ⚠️ **必须选择 Private**
4. 不要初始化README、.gitignore或LICENSE
5. 点击 **Create repository**

### 步骤2: 准备签名文件

#### 如果已有签名密钥

将现有的签名文件准备好：
- `podium-release.jks`（或其他.jks文件）
- `keystore.properties`（包含签名配置）

#### 如果还没有签名密钥

生成新的签名密钥：

```bash
keytool -genkey -v -keystore podium-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias podium
```

按提示输入：
- Keystore密码（请记住，后续需要）
- Key密码（请记住，后续需要）
- 组织信息（可以随意填写）

创建 `keystore.properties` 文件：

```properties
# Android签名配置
storeFile=podium-release.jks
storePassword=你的keystore密码
keyAlias=podium
keyPassword=你的key密码
```

### 步骤3: 初始化本地仓库并上传

```bash
# 创建临时目录
mkdir temp-podium-signing
cd temp-podium-signing

# 初始化Git仓库
git init

# 复制签名文件（替换路径为你的实际文件路径）
cp /path/to/podium-release.jks .
cp /path/to/keystore.properties .

# 创建README说明文件（可选）
cat > README.md << 'EOF'
# Podium Signing Configuration

This private repository contains signing configuration for Podium Android app.

**Files:**
- `podium-release.jks` - Android keystore file
- `keystore.properties` - Signing configuration

⚠️ **Security Notice:**
- Keep this repository PRIVATE
- Never share keystore passwords
- Backup these files securely
EOF

# 提交文件
git add .
git commit -m "Initial commit: Add signing configuration"

# 添加远程仓库（替换为你的仓库URL）
git remote add origin git@github.com:YourUsername/Podium-Signing.git

# 推送到GitHub
git push -u origin main

# 返回上级目录并清理
cd ..
rm -rf temp-podium-signing
```

### 步骤4: 在Podium项目中添加Submodule

```bash
# 进入Podium项目目录
cd /path/to/Podium

# 添加签名仓库作为submodule
git submodule add git@github.com:YourUsername/Podium-Signing.git signing

# 初始化和更新submodule
git submodule update --init --recursive

# 验证配置
ls -la signing/
# 应该看到：keystore.properties 和 podium-release.jks
```

### 步骤5: 提交Submodule配置

```bash
# 在Podium项目中提交submodule配置
git add .gitmodules signing
git commit -m "Add signing configuration submodule"
git push
```

## ✅ 验证配置

### 检查Submodule状态

```bash
# 查看submodule状态
git submodule status

# 应该显示类似：
# a1b2c3d4... signing (heads/main)
```

### 测试构建

```bash
# 尝试构建release版本
./gradlew assembleRelease

# 检查构建日志，应该看到：
# 📦 Using signing configuration from submodule: signing/keystore.properties
```

## 🔄 日常使用

### 更新签名配置

如果需要更新签名文件（如更换密钥）：

```bash
# 进入signing目录
cd signing

# 修改文件
cp /path/to/new-keystore.jks podium-release.jks
# 或编辑keystore.properties

# 提交更改
git add .
git commit -m "Update signing configuration"
git push

# 返回主项目
cd ..

# 更新submodule引用
git add signing
git commit -m "Update signing submodule reference"
git push
```

### 在新机器上使用

```bash
# 克隆Podium项目
git clone git@github.com:YourUsername/Podium.git
cd Podium

# 拉取submodule（会要求GitHub认证）
git submodule update --init --recursive

# 现在可以构建release版本
./gradlew assembleRelease
```

### 同步最新签名配置

```bash
# 在Podium项目中更新signing子模块
git submodule update --remote signing

# 如果有更新，提交引用变更
git add signing
git commit -m "Update signing configuration to latest"
git push
```

## 🔒 安全最佳实践

### ✅ 推荐做法

1. **保持仓库私有**
   - 签名仓库必须设置为Private
   - 定期检查仓库可见性设置

2. **限制访问权限**
   - 只授权必要的人访问签名仓库
   - 使用GitHub的Collaborators功能管理权限

3. **备份签名文件**
   - 在密码管理器中保存keystore密码
   - 将.jks文件备份到安全位置（加密U盘、云盘等）
   - 记录密钥指纹以便后续验证

4. **使用SSH认证**
   - 优先使用SSH方式访问私有仓库
   - 设置SSH密钥加密

5. **定期审计**
   - 检查谁有访问权限
   - 查看Git日志确认没有异常修改

### ❌ 避免做法

1. **不要将私有仓库设为公开**
   - 即使是临时的也绝对不可以

2. **不要在公开位置分享**
   - 不要将仓库URL发送到公开聊天/论坛
   - 不要截图包含仓库信息的内容

3. **不要使用弱密码**
   - Keystore密码应该足够强
   - 不要使用与其他服务相同的密码

4. **不要跳过备份**
   - 密钥丢失无法找回
   - 密钥丢失意味着无法更新已发布的应用

## 🆘 故障排除

### 问题1: 无法克隆私有仓库

**错误信息：**
```
fatal: could not read Username for 'https://github.com': terminal prompts disabled
```

**解决方案：**
使用SSH方式而不是HTTPS：
```bash
git submodule add git@github.com:YourUsername/Podium-Signing.git signing
```

确保已设置SSH密钥：
```bash
ssh -T git@github.com
# 应该显示：Hi YourUsername! You've successfully authenticated...
```

### 问题2: Submodule为空

**症状：** `signing/` 目录存在但为空

**解决方案：**
```bash
# 初始化并更新submodule
git submodule update --init --recursive
```

### 问题3: 权限被拒绝

**错误信息：**
```
Permission denied (publickey)
```

**解决方案：**
1. 检查SSH密钥是否已添加到GitHub账户
2. 确保你有该私有仓库的访问权限
3. 测试SSH连接：`ssh -T git@github.com`

### 问题4: 构建时找不到签名配置

**症状：** 构建使用debug签名而不是release签名

**检查步骤：**
```bash
# 1. 确认signing目录存在且有内容
ls -la signing/

# 2. 确认keystore.properties文件格式正确
cat signing/keystore.properties

# 3. 确认.jks文件存在
ls -la signing/*.jks

# 4. 重新运行构建并查看日志
./gradlew assembleRelease --info | grep -i signing
```

## 📚 相关资源

- [Git Submodules文档](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- [Android应用签名](https://developer.android.com/studio/publish/app-signing)
- [GitHub私有仓库](https://docs.github.com/en/repositories/creating-and-managing-repositories/about-repositories#about-repository-visibility)
- [Podium Release Build Guide](./RELEASE_BUILD_GUIDE.md)

---

**需要帮助？** 查看[RELEASE_BUILD_GUIDE.md](./RELEASE_BUILD_GUIDE.md)了解更多构建配置信息。
