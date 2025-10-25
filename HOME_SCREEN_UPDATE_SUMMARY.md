# 首页交互更新总结

## 更新内容

### 1. 数据库查询优化 (Podcast.sq)
- 添加了 `selectRecentPlaybackUnique` - 每个播客只显示最近播放的一集
- 添加了 `selectRecentEpisodesUnique` - 每个播客只显示最新发布的一集
- 添加了 `selectAllRecentPlayback` - 获取所有最近收听记录（用于查看更多页面）
- 添加了 `selectAllRecentEpisodes` - 获取所有最近更新记录（用于查看更多页面）

### 2. 数据访问层更新 (PodcastDao.kt)
- 添加了 `observeRecentEpisodesUnique(limit: Int)` - 观察去重后的最近更新
- 添加了 `observeRecentListeningUnique(limit: Int)` - 观察去重后的最近收听
- 添加了 `observeAllRecentEpisodes()` - 观察所有最近更新
- 添加了 `observeAllRecentListening()` - 观察所有最近收听

### 3. Repository层更新 (PodcastRepository.kt)
- 修改了 `observeHomeState()` - 使用去重查询，每部分最多显示6条记录
- 添加了 `observeAllRecentListening()` - 用于查看更多页面
- 添加了 `observeAllRecentUpdates()` - 用于查看更多页面

### 4. Controller层更新 (PodiumController.kt)
- 添加了 `allRecentListening` Flow - 提供完整的最近收听列表
- 添加了 `allRecentUpdates` Flow - 提供完整的最近更新列表

### 5. UI组件更新

#### 新增组件
- **HorizontalEpisodeCard.kt** - 横向滚动的单集卡片组件
  - `HorizontalEpisodeRow` - 横向滚动列表容器
  - `HorizontalEpisodeCard` - 单个横向卡片，显示封面、播放按钮、标题和播客名称

- **ViewMoreScreen.kt** - 查看更多页面
  - 显示完整的单集列表
  - 带返回按钮的顶部导航栏
  - 支持最近收听和最近更新两种类型

#### 更新的组件
- **HomeScreen.kt**
  - 最近收听部分：使用横向滚动显示，最多6个不同播客的单集
  - 最近更新部分：使用列表显示，每个播客显示最新一集，最多6个
  - 每个部分都有"查看更多"按钮
  - 添加了 `onViewMoreRecentPlayed` 和 `onViewMoreRecentUpdates` 回调

### 6. 应用导航更新 (App.kt)
- 添加了 `ViewMoreType` 枚举，定义查看更多的类型（最近收听/最近更新）
- 在 `PodiumApp` 中添加了 `showViewMore` 状态管理
- 更新了 `DesktopLayout` 和 `MobileLayout`：
  - 添加了查看更多页面的导航逻辑
  - 当显示查看更多页面时隐藏底部导航栏
  - 将完整的列表数据传递给查看更多页面

## 主要特性

### 首页显示
1. **最近收听**：
   - 横向滚动显示
   - 每个播客只显示最近播放的一集
   - 最多显示6个单集
   - 卡片式设计，显示封面、播放按钮、单集标题和播客名称
   - 点击卡片或播放按钮即可继续播放

2. **最近更新**：
   - 列表形式向下展示
   - 每个播客只显示最新发布的一集
   - 按发布时间排序，显示最新的6个
   - 使用原有的 `PodcastEpisodeCard` 组件

3. **查看更多**：
   - 每个部分右上角都有"查看更多"按钮
   - 点击后进入独立页面显示完整列表
   - 保留播放功能
   - 支持返回首页

### 数据去重逻辑
- SQL层面实现去重，确保每个播客只出现一次
- 最近收听：根据播放时间(updatedAt)排序后，每个播客取最近播放的一集
- 最近更新：根据发布时间(publishDate)排序后，每个播客取最新发布的一集

## 技术亮点

1. **性能优化**：在SQL层面进行去重，减少数据传输和处理
2. **响应式设计**：使用Flow实现数据的响应式更新
3. **代码复用**：查看更多页面复用了现有的 `PodcastEpisodeCard` 组件
4. **导航灵活**：支持在首页和查看更多页面之间流畅切换
5. **跨平台兼容**：桌面端和移动端都支持新的交互方式

## 测试建议

1. 确保订阅多个播客节目
2. 播放不同播客的单集以生成播放记录
3. 验证首页最多显示6个不同播客的单集
4. 点击"查看更多"按钮验证完整列表显示
5. 测试播放功能在各个页面的正常工作
6. 验证在桌面端和移动端的显示效果

