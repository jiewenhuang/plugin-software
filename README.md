<p align="center">
  <img src="./src/main/resources/logo.png" width="96" height="96" alt="Software Manager Plugin Logo" />
</p>

<h1 align="center">Software Manager for Halo</h1>

<p align="center">独立、主题中立的软件内容与版本管理插件</p>

<p align="center">
  <a href="https://github.com/jiewenhuang/plugin-software/actions/workflows/ci.yaml"><img src="https://github.com/jiewenhuang/plugin-software/actions/workflows/ci.yaml/badge.svg" alt="CI" /></a>
  <img src="https://img.shields.io/badge/Halo-%E2%89%A52.26.0-6248ff" alt="Halo >= 2.26.0" />
  <img src="https://img.shields.io/badge/Java-21-e76f00" alt="Java 21" />
  <img src="https://img.shields.io/badge/Vue-3.5-42b883" alt="Vue 3.5" />
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-3da639" alt="GPL-3.0" /></a>
</p>

<p align="center">
  <a href="#核心能力">核心能力</a> ·
  <a href="#安装与使用">安装</a> ·
  <a href="#开发者文档">文档</a> ·
  <a href="#开发与构建">开发</a>
</p>

## 项目定位

Software Manager 为 Halo 提供完整的软件领域模型、Console 管理界面、公共查询能力和下载门禁。它可以被任意 Halo 主题消费，也可以通过 REST API 服务其他客户端。

> [!IMPORTANT]
> 插件不是某个主题的附属后端。主题决定页面如何展示，插件决定软件数据如何建模、查询、授权和发布。

[`frontend-macwk-halo-theme`](https://github.com/jiewenhuang/frontend-macwk-halo-theme) 是一个完整接入示例，但不是插件运行的前置条件。

## 核心能力

| 能力         | 说明                                                           |
| ------------ | -------------------------------------------------------------- |
| 软件内容管理 | 软件基础信息、平台、架构、图标、截图、详情、SEO 与关联内容     |
| 版本管理     | 版本号、发布日期、系统要求、架构、语言、更新日志和校验值       |
| 下载源管理   | 下载类型、来源、优先级、登录/Turnstile 门禁和敏感字段隔离      |
| 内容组织     | 多级分类、标签、专题、推荐、精选和发布状态                     |
| Console UI   | 软件列表与编辑器，以及分类、标签、专题和下载源管理页面         |
| 公共查询     | `softwareFinder`、主题 Router 和版本化 REST API                |
| 评论集成     | 将 `SoftwareApp` 注册为 Halo 评论资源，并提供 Console 资源引用 |
| 权限与安全   | 查看/管理/敏感配置分权、HTML 净化、目标白名单和短期下载授权     |
| 可选 MCP     | 43 个基础管理工具与 2 个独立授权的敏感下载配置工具             |
| 可选 AI      | 通过 AI Foundation 为摘要、详情和 SEO 提供编辑建议             |

## 领域模型

| Extension          | 职责                                           |
| ------------------ | ---------------------------------------------- |
| `SoftwareApp`      | 软件主记录、展示信息、发布状态、SEO 和聚合统计 |
| `SoftwareVersion`  | 版本、兼容信息、更新日志、校验值和下载统计     |
| `DownloadAsset`    | 下载源、供应方、门禁标记、敏感字段和健康状态   |
| `SoftwareCategory` | 可见性、优先级和父子分类关系                   |
| `SoftwareTag`      | 软件标签、可见性和排序                         |
| `SoftwareTopic`    | 专题内容、软件集合、精选和发布状态             |

资源 API Group 为 `software.jiewen.run/v1alpha1`。Public VO、Console VO 和原始 Extension 分开维护，避免把管理字段或 Secret 暴露给访客。

## 安装与使用

### 环境要求

| 组件           | 要求                                    |
| -------------- | --------------------------------------- |
| Halo           | `>= 2.26.0`                             |
| Java           | 源码构建需要 Java 21                    |
| Node.js / pnpm | Console UI 构建建议 Node.js 24、pnpm 10 |
| MCP Server     | 可选，`>=1.0.0 & <2.0.0`；使用正式版 API 1.0.0 |
| AI Foundation  | 可选，不影响软件管理核心能力            |

### 安装插件

1. 从 [GitHub Releases](https://github.com/jiewenhuang/plugin-software/releases) 下载插件 JAR。
2. 在 Halo Console 的“插件”页面上传并启用。
3. 使用管理员账号进入“软件”菜单，配置分类、标签、专题和下载源。
4. 非管理员账号按需授予“软件查看”或“软件管理”角色模板；只有确需查看 URL、提取码等字段的账号再授予“软件敏感下载配置”。
5. 如需 AI 编辑建议，再安装并启用兼容版本的 AI Foundation。

推荐先配置选项、分类、标签和专题，再创建软件、版本与下载源，最后检查公开详情并发布。

### 下载安全配置

下载源的 URL、`storageKey`、提取码和密码 Secret 引用通过独立的 Console Secret API 或单独授权的 MCP 敏感工具读写，普通“软件查看/管理”角色无法读取。需要验证码时：

1. 在 Halo 中创建 Secret，并把 Turnstile Secret Key 放在 `data.secret`（数据键可配置）；
2. 在插件“下载安全”设置填写 Turnstile Site Key、Halo Secret 名称和数据键；
3. 主题通过公开的 `download-options` 获取 Site Key，再将 Turnstile token 提交给 `download-grants`。

验证码服务异常、Secret 缺失或目标 URL 不安全时下载授权会 fail-closed。插件只允许 HTTP(S) 或安全的站内绝对路径，并为敏感响应设置 `no-store`。

## 插件与主题的边界

| 插件负责                   | 主题负责                  |
| -------------------------- | ------------------------- |
| 软件领域模型与状态         | 卡片、列表和详情布局      |
| 搜索、过滤、排序和分页     | 网格/列表切换与浏览器偏好 |
| Finder、Router 和 REST API | 调用公共契约并渲染数据    |
| 下载登录/验证门禁          | 下载面板和用户反馈        |
| RBAC、Public VO 和字段隔离 | 颜色、动画和响应式设计    |

插件不会提供 `grid/list` 参数、主题 CSS class 或某个主题专用的卡片模型。

`getHome()` / `SoftwareHomeVo` 是保留的主题中立聚合接口。它聚合最新、热门、推荐、评论、专题和分类数据，但不包含任何布局状态。

## 开发者文档

| 文档                                 | 内容                                                            |
| ------------------------------------ | --------------------------------------------------------------- |
| [主题 API 文档](./dev/theme-api.md)  | 插件守卫、主题路由、模板变量、Finder、Public VO、评论与降级行为 |
| [REST API 文档](./dev/rest-api.md)   | 公共/Console/Extension API、参数、RBAC、错误状态和下载门禁      |
| [开发环境搭建](./dev/development.md) | Halo DevTools、Console UI、测试、构建和公共契约变更流程         |

[MCP 接入文档](./dev/mcp.md)介绍工具清单、参数、密钥授权、下载敏感字段隔离、并发和错误契约。

README 只提供项目入口；上述文档是相应公共契约的详细来源。修改 Endpoint、Router、Finder 或 Public VO 时，应同时更新测试和对应文档。

## 主题接入概览

插件注册软件公共路由并提供模板模型，当前主题负责实现模板：

| 路由                                | 主题模板                |
| ----------------------------------- | ----------------------- |
| `/soft/all/p{page}`、分类页、标签页 | `software-list.html`    |
| `/soft/{slug}`                      | `software-detail.html`  |
| `/special/{slug}`                   | `software-special.html` |

主题应先检查插件状态：

```html
<th:block th:if="${pluginFinder.available('software')}">
    <a href="/soft/all/p1">软件</a>
</th:block>
```

插件未安装或未启用时，插件路由不存在；主题应隐藏软件入口，并为主题自己拥有的页面提供降级内容。完整变量、Finder 方法和 VO 字段见 [主题 API 文档](./dev/theme-api.md)。

## REST API 概览

匿名公共 API 前缀：

```text
/apis/api.software.jiewen.run/v1alpha1
```

公共 API 提供软件、分类、标签、专题和下载门禁查询。普通软件详情不会暴露最终下载 URL、`storageKey`、提取码或密码 Secret；新客户端通过 `POST /download-grants` 获取短期授权，通过 `GET /download-options` 获取安全公开的 Turnstile 启动配置。

Console API、分页筛选、错误状态、AI Endpoint 和 RBAC 说明见 [REST API 文档](./dev/rest-api.md)。

## 评论与权限

软件评论资源三元组：

```text
group: software.jiewen.run
kind: SoftwareApp
name: <SoftwareApp.metadata.name>
```

插件注册“软件查看”“软件管理”“软件敏感下载配置”和匿名公共 API 四类角色。主题不得通过 Console API 或原始 Extension 绕过公共 VO 和下载门禁。

## 可选 MCP 集成

安装并启用 MCP Server 后，软件中心贡献 43 个基础管理工具，客户端工具名以 `software__` 开头。在「工具 → MCP 服务」中为访问密钥勾选所需工具；新工具不会自动加入已有密钥。

下载敏感配置的两个工具默认关闭，可在软件中心「MCP 接入」设置中开启，并分别授权读取或写入。普通工具始终隐藏下载 URL、提取码等字段。未安装或停用 MCP Server 时，软件中心仍能独立运行。

1.2.0 增加持久化 requestId 防重复创建、容忍统计变更的编辑，以及发布准备和删除影响查询。已有客户端可继续使用原参数；新工具需要为密钥另外授权。

完整使用方法见 [MCP 接入文档](./dev/mcp.md)。软件中心 1.2.0 延续 1.1.0 的最低 Halo 2.26.0 要求。

## 可选 AI 集成

AI 只用于 Console 编辑辅助，不是软件管理的核心依赖：

```yaml
pluginDependencies:
    ai-foundation?: ">=1.0.0-SNAPSHOT & <2.0.0"
```

未安装或禁用 AI Foundation 时，软件管理、公共 API、Finder 和主题路由仍正常工作；Console 隐藏 AI 操作。

## 项目结构

```text
plugin-software/
├── dev/                              # 开发、主题 API 与 REST API 文档
├── src/main/java/run/jiewen/software/
│   ├── extension/                    # 六类领域 Extension
│   ├── api/                          # 写入 Command 与下载授权请求
│   ├── download/                     # Turnstile 与下载目标安全策略
│   ├── reconciler/                   # 聚合状态、计数和兼容迁移
│   ├── finders/                      # Console 与公共查询服务
│   ├── vo/                           # Public、Finder 与 Console VO
│   ├── comment/                      # SoftwareApp 评论资源
│   ├── ai/                           # 可选 AI 编辑辅助
│   ├── SoftwareThemeRouter.java      # 主题公共路由
│   └── *Endpoint.java                # Console 与公共 REST API
├── src/main/resources/               # 插件清单、设置、RBAC 与 Logo
├── src/test/                         # Java 单元与契约测试
├── api-docs/                         # 生成的 OpenAPI JSON
├── ui/                               # Vue Console UI、生成 API Client 与测试
├── build.gradle
└── settings.gradle
```

## 开发与构建

完整检查与打包：

```bash
./gradlew build
```

更新 OpenAPI 与 TypeScript Client：

```bash
./gradlew generateApiClient --no-daemon
```

Halo DevTools：

```bash
./gradlew haloServer
./gradlew reloadPlugin
```

开发实例默认运行在 <http://localhost:8099>，插件 JAR 输出到 `build/libs/`。环境准备、UI 命令和验证清单见 [开发环境搭建](./dev/development.md)。

## 兼容与发布策略

- `v1alpha1` 已发布字段不做无迁移重命名或删除；允许向后兼容地新增可选字段。
- Public VO、Console VO 和 Finder 聚合 VO 分开维护。
- Router 模板变量、Finder 方法、RBAC 和敏感字段隔离均由契约测试保护。
- GitHub CI/CD 使用 Halo 官方插件工作流；Halo 应用市场发布当前未启用。

## 许可证

[GPL-3.0](./LICENSE) © jiewenhuang
