# 主题 API 文档

本文档描述 `plugin-software` 向 Halo 主题提供的稳定公共契约：插件可用性守卫、模板路由、模板变量、Finder API、公共 VO 和评论资源。

> [!IMPORTANT]
> 插件负责软件数据的建模、查询、授权和发布；主题负责页面布局、交互、颜色、动画和响应式呈现。主题是插件公共能力的消费者，插件不是某个主题的专用后端。

## 插件可用性与降级

插件的 `metadata.name` 为 `software`。主题在输出软件入口或调用 Finder 前必须先检查插件状态：

```html
<th:block th:if="${pluginFinder.available('software')}">
    <a href="/soft/all/p1">软件</a>
</th:block>
```

安装状态对应的主题行为：

| 插件状态       | `pluginFinder.available('software')` | 插件路由 | 主题建议                                                     |
| -------------- | ------------------------------------ | -------- | ------------------------------------------------------------ |
| 未安装、未启用 | `false`                              | 不可用   | 隐藏软件导航和 Finder 区块；主题自有 Page 可显示静态降级内容 |
| 已安装并启用   | `true`                               | 可用     | 显示入口，并使用本文档中的模板变量或 Finder                  |

插件不会为主题自有的 `/special`、`/perfect` 等页面提供兜底；主题需要自行定义这些页面及插件不可用时的表现。

## 模板路由

插件注册路由并提供模型，当前主题负责提供对应的 Thymeleaf 模板文件。

| 访问路径                   | 主题模板                          | 说明                                         |
| -------------------------- | --------------------------------- | -------------------------------------------- |
| `/soft`                    | —                                 | `308 Permanent Redirect` 到 `/soft/all/p1`   |
| `/soft/all/p{page}`        | `templates/software-list.html`    | 全部已发布软件                               |
| `/soft/{category}/p{page}` | `templates/software-list.html`    | 按可见分类 slug 过滤，包含其全部可见后代分类 |
| `/soft/tag/{slug}/p{page}` | `templates/software-list.html`    | 按可见标签 slug 过滤                         |
| `/soft/{slug}`             | `templates/software-detail.html`  | 已发布软件详情                               |
| `/special/{slug}`          | `templates/software-special.html` | 已发布软件专题详情                           |

列表页 `page` 从 1 开始，固定每页 20 条。`page` 不是正整数时返回 `404 Not Found`；分类、标签、软件或专题不存在、不可见或未发布时同样返回 `404`。

### 列表页查询参数

主题 Router 对外稳定的查询参数只有：

| 参数      | 说明                                                                               |
| --------- | ---------------------------------------------------------------------------------- |
| `keyword` | 按 `SoftwareApp.spec.displayName` 包含匹配                                         |
| `sort`    | `latest`、`downloads`、`likes`、`comments` 或 `recommended`；未知值回退为 `latest` |

分类和标签使用路径表达，不应再由主题拼接 `category` 或 `tag` 查询参数。`grid/list`、动画状态和其他浏览器偏好属于主题端状态，不是插件 Router 参数。

排序规则：

| `sort`        | 排序与过滤                                   |
| ------------- | -------------------------------------------- |
| `latest`      | 最新发布日期降序，创建时间降序兜底           |
| `downloads`   | 下载数降序，创建时间降序兜底                 |
| `likes`       | 喜欢数降序，创建时间降序兜底                 |
| `comments`    | 评论数降序，创建时间降序兜底                 |
| `recommended` | 只保留推荐软件，按最新发布日期和创建时间降序 |

## `software-list.html`

模板变量：

| 变量                       | 类型                         | 说明                                             |
| -------------------------- | ---------------------------- | ------------------------------------------------ |
| `_templateId`              | `String`                     | 固定为 `software-list`                           |
| `softwareApps`             | `ListResult<SoftwareAppVo>`  | 当前页已发布软件，分页从 1 开始                  |
| `softwareCategories`       | `List<SoftwareCategoryVo>`   | 可见分类树；首项是插件生成的“全部软件”节点       |
| `softwareTags`             | `List<SoftwareTagVo>`        | 最多 100 个可见标签，按优先级升序                |
| `softwareRecommendations`  | `List<SoftwareAppVo>`        | 最多 6 个已发布推荐软件                          |
| `softwareActiveCategory`   | `SoftwareCategoryVo \| null` | 当前分类                                         |
| `softwareActiveTag`        | `SoftwareTagVo \| null`      | 当前标签                                         |
| `softwareListTitle`        | `String`                     | “全部软件”、分类显示名或标签显示名               |
| `softwarePageBase`         | `String`                     | 不带 `/p{page}` 的分页基础路径                   |
| `softwareKeyword`          | `String \| null`             | 当前搜索词                                       |
| `softwareSort`             | `String`                     | 规范化后的排序值                                 |
| `softwareCurrentPage`      | `int`                        | 当前页码                                         |
| `softwareTotalPages`       | `int`                        | 总页数，最小值为 1                               |
| `softwareQuerySuffix`      | `String`                     | 已编码的 `?sort=...&keyword=...` 后缀            |
| `softwareKeywordQueryPart` | `String`                     | 为空或已编码的 `&keyword=...` 片段               |
| `softwareActiveParentName` | `String`                     | 分类树展开节点的 `metadata.name`；全部页为 `all` |

最小示例：

```html
<h1 th:text="${softwareListTitle}">全部软件</h1>

<article th:each="app : ${softwareApps.items}">
    <a th:href="${app.permalink}">
        <img th:src="${app.icon}" th:alt="|${app.displayName} 图标|" width="64" />
        <h2 th:text="${app.displayName}">软件名称</h2>
        <p th:text="${app.summary}">软件摘要</p>
    </a>
</article>

<nav aria-label="软件分页">
    <a
        th:if="${softwareCurrentPage > 1}"
        th:href="|${softwarePageBase}/p${softwareCurrentPage - 1}${softwareQuerySuffix}|"
        >上一页</a
    >
    <a
        th:if="${softwareCurrentPage < softwareTotalPages}"
        th:href="|${softwarePageBase}/p${softwareCurrentPage + 1}${softwareQuerySuffix}|"
        >下一页</a
    >
</nav>
```

## `software-detail.html`

模板变量：

| 变量          | 类型                  | 说明                                 |
| ------------- | --------------------- | ------------------------------------ |
| `_templateId` | `String`              | 固定为 `software-detail`             |
| `software`    | `SoftwareAppDetailVo` | 已发布软件、版本和安全的下载源元数据 |

`software.description` 在进入公共 VO 前经过 HTML 净化。`software.downloadAssets` 只包含已启用下载源，并按优先级升序；其中 `url` 固定为空，主题必须通过下载授权 REST API 获取最终地址。`relatedApps` 只包含已发布软件，`relatedPosts` 只包含公开且已发布的 Halo 文章，文章摘要同样经过 HTML 净化。

```html
<article>
    <h1 th:text="${software.displayName}">软件名称</h1>
    <div th:utext="${software.description}"></div>

    <a th:each="version : ${software.versions}" th:text="${version.version}">1.0.0</a>
</article>
```

### 下载授权与 Turnstile

主题不得从详情 VO、Console API 或原始 Extension 读取下载敏感字段。推荐流程：

1. 调用 `GET /apis/api.software.jiewen.run/v1alpha1/download-options` 获取 `captchaProvider`、`captchaSiteKey` 和 `captchaConfigured`；
2. 当下载源的 `requiresCaptcha=true` 时，用公开 Site Key 渲染 Cloudflare Turnstile；
3. 把 token 与软件 slug、可选下载源 name 一起提交到 `POST /apis/api.software.jiewen.run/v1alpha1/download-grants`；
4. 只在成功响应后展示或跳转 `url`，并按需显示 `extractionCode` / `password`。

Site Key 也可在服务端模板中通过 `${softwareFinder.getDownloadSecurityOptions()}` 取得。`captchaConfigured=false` 时主题应禁用受验证码保护的下载操作并提示管理员检查插件设置，不能回退为绕过验证。

完整请求、状态码、缓存头和 60 秒授权决策有效期见 [REST API 文档](./rest-api.md#下载门禁)。

## `software-special.html`

模板变量：

| 变量             | 类型                    | 说明                                |
| ---------------- | ----------------------- | ----------------------------------- |
| `_templateId`    | `String`                | 固定为 `software-special`           |
| `softwareTopic`  | `SoftwareTopicDetailVo` | 当前已发布专题及其已发布软件        |
| `softwareTopics` | `List<SoftwareTopicVo>` | 最多 100 个已发布专题，按优先级升序 |

`softwareTopic.apps` 遵循专题条目的优先级；不存在、未发布或已从专题中移除的软件不会进入结果。

```html
<h1 th:text="${softwareTopic.title}">软件专题</h1>

<a th:each="app : ${softwareTopic.apps}" th:href="${app.permalink}">
    <span th:text="${app.displayName}">软件名称</span>
</a>
```

## Finder API

Finder 名称为 `softwareFinder`。以下方法构成面向主题的稳定 Finder 契约：

| 方法                                | 返回值                        | 说明                                                           |
| ----------------------------------- | ----------------------------- | -------------------------------------------------------------- |
| `getHome()`                         | `SoftwareHomeVo`              | 首页聚合，每个软件栏目最多 12 条                               |
| `getHome(int size)`                 | `SoftwareHomeVo`              | 每个软件栏目限制为 1–12 条；超出范围自动收敛                   |
| `recommendedApps(int size)`         | `ListResult<SoftwareAppVo>`   | 推荐软件，限制为 1–100 条，同时保留总数                        |
| `publishedTopics()`                 | `List<SoftwareTopicVo>`       | 最多 100 个已发布专题                                          |
| `topicDetails(List<String> slugs)`  | `List<SoftwareTopicDetailVo>` | 批量获取专题及软件；空列表表示全部，非空时保持调用方 slug 顺序 |
| `getAppBySlug(String slug)`         | `SoftwareAppVo`               | 按 slug 获取已发布软件摘要                                     |
| `getAppDetailBySlug(String slug)`   | `SoftwareAppDetailVo`         | 按 slug 获取已发布软件详情                                     |
| `getCategoryBySlug(String slug)`    | `SoftwareCategoryVo`          | 按 slug 获取可见分类                                           |
| `getTagBySlug(String slug)`         | `SoftwareTagVo`               | 按 slug 获取可见标签                                           |
| `getTopicDetailBySlug(String slug)` | `SoftwareTopicDetailVo`       | 按 slug 获取已发布专题及软件                                   |
| `getDownloadSecurityOptions()`      | `DownloadSecurityVo`          | 公开验证码 provider、Site Key 与配置状态；不含 Secret 元数据   |

`ListOptions`、`PageRequest` 和下载源查询等 Java 服务方法属于 Endpoint 内部查询能力，不作为推荐的 Thymeleaf Finder 接口。主题获取最终下载 URL 时应使用 [REST API 文档](./rest-api.md) 中的下载门禁端点。

### 首页聚合

`SoftwareHomeVo` 不包含卡片样式、网格/列表模式、颜色或其他主题状态：

| 字段               | 类型                       | 排序或语义                                 |
| ------------------ | -------------------------- | ------------------------------------------ |
| `latestApps`       | `List<SoftwareAppVo>`      | 最新发布日期降序                           |
| `popularApps`      | `List<SoftwareAppVo>`      | 下载数降序                                 |
| `recommendedApps`  | `List<SoftwareAppVo>`      | 已发布且推荐，最新发布日期降序             |
| `recommendedTotal` | `long`                     | 全部已发布推荐软件数量，不受栏目 size 限制 |
| `commentedApps`    | `List<SoftwareAppVo>`      | 评论数降序                                 |
| `topics`           | `List<SoftwareTopicVo>`    | 已发布专题，优先级升序                     |
| `categories`       | `List<SoftwareCategoryVo>` | 可见分类，优先级升序                       |

主题首页示例：

```html
<section th:if="${pluginFinder.available('software')}" th:with="softwareHome=${softwareFinder.getHome(12)}">
    <article th:each="app : ${softwareHome.latestApps}">
        <a th:href="${app.permalink}" th:text="${app.displayName}">软件名称</a>
    </article>
</section>
```

### Finder 空值与可见性

- Finder 只返回已发布软件、已发布专题以及可见分类/标签。
- 按 slug 查询不到公共资源时返回 `404 Not Found`，不会回退到草稿或隐藏资源。
- `topicDetails(null)`、`topicDetails([])` 表示全部已发布专题。
- `topicDetails(slugs)` 会丢弃 `null`、空白和重复 slug，最多处理 100 个。
- 专题与首页聚合使用批量查询，主题不应再对每个专题逐一调用详情方法。

## 公共 VO

公共 VO 与原始 Extension、Console 响应分开维护，主题不得假设能读取未列出的管理字段。

### `SoftwareAppVo`

| 字段组     | 字段                                                                                                                                                                                     |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 标识与链接 | `name`, `slug`, `displayName`, `permalink`, `officialWebsite`                                                                                                                            |
| 展示内容   | `subtitle`, `icon`, `cover`, `screenshots`, `summary`, `description`                                                                                                                     |
| 分类与标签 | `categoryName`, `categoryDisplayName`, `categorySlug`, `tagNames`, `tagDisplayNames`                                                                                                     |
| 最新版本   | `latestVersion`, `latestVersionName`, `latestReleaseDate`, `minOsVersion`, `sizeBytes`                                                                                                   |
| 统计       | `downloadCount`, `likeCount`, `commentCount`                                                                                                                                             |
| 展示格式   | `downloadCountText`, `likeCountText`, `commentCountText`, `sizeText`, `sizeValue`, `sizeUnit`, `minOsDisplay`, `latestReleaseDateText`, `latestReleaseDateShort`, `latestReleaseWeekday` |
| 平台信息   | `platform`（兼容字段）, `platforms`, `architectures`, `licenseType`, `locale`, `localeValue`, `localeUnit`, `localeLabel`                                                               |
| 发布信息   | `featured`, `recommended`, `publishedAt`, `seo`                                                                                                                                          |

`name` 是 `SoftwareApp.metadata.name`，`slug` 是公开路径标识，两者不能混用。评论资源和 Extension 引用使用 `name`；页面 URL 使用 `slug` 或 `permalink`。

### 其他 VO

| 类型                    | 关键字段与说明                                                                                                                                                                                                                                  |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SoftwareAppDetailVo`   | 包含 `SoftwareAppVo` 的全部字段，并增加 `versions`、`downloadAssets`、`relatedApps`、`relatedPosts`                                                                                                                                              |
| `SoftwareCategoryVo`    | `name`, `slug`, `displayName`, `parentName`, `description`, `icon`, `priority`, `appCount`, `children`                                                                                                                                          |
| `SoftwareTagVo`         | `name`, `slug`, `displayName`, `description`, `color`, `priority`, `appCount`                                                                                                                                                                   |
| `SoftwareTopicVo`       | `name`, `slug`, `title`, `subtitle`, `description`, `cover`, `items`, `featured`, `priority`, `appCount`；公开 `items` 只含 slug、展示字段、reason 和 priority，不含内部 app name                                                            |
| `SoftwareTopicDetailVo` | 包含 `SoftwareTopicVo` 的字段，并增加 `apps`                                                                                                                                                                                                    |
| `SoftwareVersionVo`     | `name`, `version`, `build`, `releaseDate`, `sizeBytes`, `minOsVersion`, `maxOsVersion`, `architectures`, `locale`, `changelog`, `installGuide`, `installWarnings`, `sha256`, `md5`, `status`, `assetCount`, `downloadCount`, `primaryAssetName` 以及展示格式字段 |
| `DownloadAssetVo`       | `name`, `versionName`, `title`, `type`, `provider`, `url`, `requiresLogin`, `requiresCaptcha`, `region`, `priority`, `downloadCount`                                                                                                            |
| `DownloadSecurityVo`    | `captchaProvider`, `captchaSiteKey`, `captchaConfigured`；不包含 Secret 名称、数据键或 Secret 值                                                                                                                                                  |
| `DownloadGrantVo`       | 授权成功后短期返回 `assetName`, `versionName`, `title`, `url`, `extractionCode`, `password`, `expiresAt`；只通过不可缓存 REST 响应取得                                                                                                            |
| `SoftwareSeoVo`         | `title`, `description`, `keywords`                                                                                                                                                                                                              |

公共 VO 不包含 `storageKey`、提取码、密码 Secret 或其他 Console 私有字段。普通详情中的 `DownloadAssetVo.url` 为 `null`。

## 评论接入

插件将 `SoftwareApp` 注册为 Halo 评论资源。资源三元组固定为：

```text
group: software.jiewen.run
kind: SoftwareApp
name: <SoftwareApp.metadata.name>
```

在 `software-detail.html` 中可使用：

```html
<halo:comment group="software.jiewen.run" kind="SoftwareApp" th:attr="name=${software.name}" />
```

评论展示依赖 Halo 评论能力和主题采用的评论组件。评论不可使用 `${software.slug}` 作为 `name`，否则会形成另一条错误的评论归属记录。

## 主题边界约束

- 不向插件新增 `grid/list`、动画开关、主题色、CSS class 等展示参数。
- 不为了单个主题重命名公共 VO 或增加主题专用卡片模型。
- 不通过 Console API 或原始 Extension 在访客页面读取下载 URL、Secret 或管理状态。
- 主题需要新组合数据时，优先提出主题中立的领域查询；`getHome()` / `SoftwareHomeVo` 就是此类聚合接口。
- 变更路由、模板变量、Finder 方法或公共 VO 时，必须同步更新契约测试和本文档。
