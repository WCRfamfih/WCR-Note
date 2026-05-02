# AI Note Completion

安卓端 AI 文字补全记事本 MVP。

## 已实现

- Kotlin + Jetpack Compose + Material 3 项目结构
- MVVM 分层，包含 `data`、`domain`、`ui`、`di`
- Room 本地笔记数据库
- DataStore 用户设置
- 笔记列表、新建、编辑、删除、搜索、最近编辑排序
- 编辑页 800ms 自动保存、标题自动提取、字数统计
- 深色模式跟随系统
- Fake AI 补全服务与候选卡片，点击后插入光标位置
- 设置页保存 API Provider、API Key、自动补全、补全延迟、最大长度、上下文范围

## 运行

用 Android Studio 打开项目根目录并同步 Gradle。当前仓库没有 Gradle Wrapper，本机也未检测到 Android SDK 环境变量；建议在 Android Studio 中使用内置 Gradle 运行 `app`。

## 后续优先级

1. 接入真实云端 API Provider。
2. 增加“继续写 / 总结 / 生成标题”等手动 AI 操作。
3. 将候选卡片升级为编辑器内 ghost text。
