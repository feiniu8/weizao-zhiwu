# 唯造智物 · WeiZao

一个用 Kotlin + Jetpack Compose 写的影视聚合客户端：多采集接口聚合搜索、播放、历史与收藏、直播源、主题设置，更新直接从 GitHub Releases 拉取。

## 功能

- **多源聚合**：内置多个采集接口，可自行增删；搜索时并行查询所有已启用接口，去重合并后展示
- **搜索结果**：按资源线路分组，支持评分、年份、类型筛选
- **播放**：ExoPlayer（含 HLS），倍速、选集、记忆续播、全屏、常亮/亮度调节
- **榜单**：全网榜单聚合
- **直播**：支持 M3U 订阅源与自建频道
- **收藏 / 历史**：Room 本地存储，支持一键清空
- **个性化**：主题色、壁纸、播放设置集中管理
- **软件更新**：启动时自动检查 GitHub Releases，发现新版本可直接下载安装；也可在「设置 → 检查更新」手动触发

## 构建

环境：JDK 17、Android SDK（compileSdk 34）。

```bash
# local.properties 里写 sdk.dir=/path/to/android-sdk
./gradlew assembleRelease        # 产出 app/build/outputs/apk/release/app-release-unsigned.apk
```

仓库里没有 `gradlew`，直接用本机 Gradle 8.7 也可以：

```bash
gradle assembleRelease
```

签名（自行生成密钥）：

```bash
apksigner sign --ks release.jks --ks-key-alias weizao \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out weizao.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## 在线更新

更新逻辑在 `network/Updater.kt`：

- 默认仓库 `feiniu8/weizao-zhiwu`，请求 `https://api.github.com/repos/{owner}/{repo}/releases/latest`
- 取 `tag_name` 与当前 `versionName` 比较，取 release 里第一个 `.apk` 资源作为下载直链
- 如果设置里填了 `https://github.com/owner/repo` 形式的地址，会优先用那个仓库
- 更新包下载完成后调用系统安装器覆盖安装（签名需与旧版一致）

发版流程：改 `versionCode` / `versionName` → 构建签名 → 新建 tag（如 `v1.7.6`）→ 上传 APK 到该 Release 即可被客户端检测到。

## 项目结构

```
app/src/main/java/com/weizao/movieapp/
├── MainActivity.kt          # 主界面、导航、更新流程
├── network/                 # OkHttp 封装、采集接口、更新器
├── db/                      # Room：源、历史、收藏、直播、订阅
├── ui/                      # Compose 页面：首页/搜索/榜单/直播/我的/播放器
└── utils/                   # 下载器、工具函数
```

## 说明

采集接口均来自第三方公开 API，本项目只提供客户端聚合与播放能力，不托管任何影视内容。

## 许可证

MIT，见 [LICENSE](LICENSE)。
