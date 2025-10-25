# 桌面平台UI更新 - Spotify风格

## 概述
为桌面平台（JVM）创建了全新的Spotify风格用户界面，包括侧边导航栏和底部播放控制器。

## 主要更改

### 1. 新增组件

#### DesktopPlaybackBar.kt
- 位置：`composeApp/src/commonMain/kotlin/com/opoojkk/podium/ui/components/DesktopPlaybackBar.kt`
- 功能：
  - Spotify风格的深色主题（#181818背景色）
  - 固定在底部，横跨整个窗口宽度
  - 三栏布局：左侧显示歌曲信息，中间播放控制，右侧音量控制
  - 可拖动的进度条（Spotify绿色 #1DB954）
  - 大圆形播放/暂停按钮（白色背景，黑色图标）
  - 快进/快退按钮（15秒/30秒）
  - 时间显示（当前/总时长）
  - 专辑封面占位符
  - 支持缓冲状态显示

#### DesktopNavigationRail.kt
- 位置：`composeApp/src/commonMain/kotlin/com/opoojkk/podium/ui/components/DesktopNavigationRail.kt`
- 功能：
  - Spotify风格的侧边导航栏
  - 纯黑背景（#000000）
  - 240dp固定宽度
  - 显示应用Logo "Podium"
  - 导航项带有图标和标签
  - 选中状态高亮显示（#282828背景）

### 2. App.kt 重构

- 添加平台检测逻辑
- 根据平台类型选择不同的布局：
  - **桌面平台（JVM）**：使用 `DesktopLayout`
    - 左侧侧边导航栏
    - 主内容区域占据剩余空间
    - 底部Spotify风格播放控制器
  - **移动平台**：保留原有的 `MobileLayout`
    - 底部导航栏
    - 原有播放条在导航栏上方

### 3. 设计特点

#### 颜色方案（Spotify风格）
- 主背景：`#181818`（深灰）
- 侧边栏背景：`#000000`（纯黑）
- 导航项选中：`#282828`（中等灰）
- 强调色：`#1DB954`（Spotify绿）
- 主要文本：`#FFFFFF`（白色）
- 次要文本：`#B3B3B3`（浅灰）
- 三级元素：`#4D4D4D`（暗灰）

#### 布局特点
- 播放控制器固定在底部，始终可见
- 侧边导航栏固定在左侧（全屏播放时隐藏）
- 主内容区域可滚动
- 响应式设计，适配不同窗口大小

## 使用方式

运行桌面应用：
```bash
./gradlew :composeApp:run
```

应用会自动检测平台类型并显示相应的UI布局。

## 兼容性

- 保持了所有现有功能
- 移动平台（Android/iOS）不受影响，继续使用原有UI
- 桌面平台获得全新的Spotify风格体验

## 未来改进建议

1. 添加实际的专辑封面图片加载
2. 实现音量控制功能
3. 添加播放列表功能
4. 实现随机播放和循环播放
5. 添加收藏功能
6. 支持键盘快捷键（空格键播放/暂停等）
7. 添加窗口大小调整时的响应式布局优化

