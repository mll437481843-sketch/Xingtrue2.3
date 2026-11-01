# 洛天依 · 聊天 App

装在自己手机上的洛天依角色聊天应用。原生 Kotlin 外壳 + WebView UI，不依赖第三方框架。

## 功能

- 六套立绘：设置里切换，点立绘可换图并调上下裁切位置。
- 多服务商：DeepSeek / OpenAI / Kimi / 通义千问 / 智谱 / 自定义 OpenAI 兼容接口。每个服务商各存一份 Key，可一键删除、可测试连通性。
- 模型列表来自服务商：请求 /models 拉取全量，按对话/识图/语音/向量自动分类，可收藏（★）只显示常用。
- 思考强度：按模型动态给档位（关闭 / 一些 / 很多 / Pro Max），用不同数量的小笼包表示。
- 联网搜索：DeepSeek 走官方 /v1/tools/search；其他服务商用「搜索中继」（填一个 DeepSeek Key 代搜）或回退 Bing。搜索前先让模型自己拟 2~3 个搜索词。
- 记忆：对话中自动提炼，也可手动增删改；支持「整合记忆」「整合对话」（增量、不重复整合）。
- 天依唱片机：内置网易云搜索/播放/歌词/歌单，支持登录（Cookie 存本机），歌词跟随与展开。
- 小笼包账单：DeepSeek 余额查询 + 每轮按官方定价估算花费。
- 调试模式：默认关闭，开启后才写诊断日志，也可手动导出。

## 隐私

- API Key、网易云 Cookie 只存在本机（SharedPreferences / localStorage），不随 APK 或仓库分发。
- 仓库里没有真实 Key。

## 构建

- build.sh 是手机端工具链用的脚本（依赖本地 JDK / aapt2 / R8）。
- 常规开发建议用 Android Studio 打开 app/ 目录。

## 目录

- app/src/main/ ：Kotlin 源码 + assets（index.html、立绘、表情）
- build.sh ：本机构建脚本
- tools/Scale.java ：图片压缩工具
- release/ ：已签名 APK

