# REST API 文档

本文档描述 `plugin-software` 的公共 REST API、Console API、原始 Extension API、认证边界和错误行为。主题模板与 Finder 契约见 [主题 API 文档](./theme-api.md)。

## API 概览

| API           | 前缀                                             | 访问范围           | 响应模型                   |
| ------------- | ------------------------------------------------ | ------------------ | -------------------------- |
| 公共 API      | `/apis/api.software.jiewen.run/v1alpha1`         | 匿名查询与下载授权 | 脱敏 Public VO             |
| Console API   | `/apis/console.api.software.jiewen.run/v1alpha1` | 登录并具备软件权限 | 原始 Extension、Console VO |
| Extension API | `/apis/software.jiewen.run/v1alpha1`             | 登录并具备软件权限 | 原始 Extension             |

插件内置 `role-template-software-anonymous` 并聚合到 Halo 匿名角色，因此公共 API 默认无需登录。匿名角色不会获得 Console 或原始 Extension API 权限。

## Swagger

启动本地 Halo 开发实例后访问：

<http://localhost:8099/swagger-ui.html>

选择以下 API Group 查看 Springdoc 路由声明：

```text
api.software.jiewen.run/v1alpha1
console.api.software.jiewen.run/v1alpha1
```

仓库提交 `api-docs/openapi/v3_0/softwareApi.json`，并由同一契约生成 `ui/src/api/generated/`。Endpoint 变化后执行 `./gradlew generateApiClient --no-daemon`，并同步审查 Springdoc、生成物、本文档和契约测试。

## 公共 API

公共 API 只返回已发布软件、已发布专题以及可见分类和标签。

### 端点列表

| 方法与路径                               | 响应                             | 说明                                                  |
| ---------------------------------------- | -------------------------------- | ----------------------------------------------------- |
| `GET /apps`                              | `ListResult<SoftwareAppVo>`      | 分页查询已发布软件                                    |
| `GET /apps/{slug}`                       | `SoftwareAppDetailVo`            | 软件详情、版本和脱敏下载源                            |
| `GET /apps/{slug}/download`              | `DownloadAssetVo`                | 通过下载门禁按优先级顺序获取可用下载源及 URL          |
| `GET /apps/{slug}/download?asset={name}` | `DownloadAssetVo`                | 按 `DownloadAsset.metadata.name` 获取指定下载源及 URL |
| `POST /download-grants`                  | `DownloadGrantVo`                | 校验登录/验证码并返回短期、不可缓存的敏感下载授权     |
| `GET /download-options`                  | `DownloadSecurityVo`             | 获取脱敏的验证码 provider、Site Key 与配置状态        |
| `GET /categories`                        | `ListResult<SoftwareCategoryVo>` | 分页查询可见分类                                      |
| `GET /tags`                              | `ListResult<SoftwareTagVo>`      | 分页查询可见标签                                      |
| `GET /topics`                            | `ListResult<SoftwareTopicVo>`    | 分页查询已发布专题                                    |
| `GET /topics/{slug}`                     | `SoftwareTopicDetailVo`          | 专题及其已发布软件                                    |

以上路径需要拼接公共前缀。例如：

```text
GET /apis/api.software.jiewen.run/v1alpha1/apps?page=1&size=20
```

### 分页与排序

公共分页行为：

| 参数   | 默认值 | 规则                                                       |
| ------ | ------ | ---------------------------------------------------------- |
| `page` | `1`    | 必须是正整数                                               |
| `size` | `20`   | 必须是正整数；大于 100 时收敛为 100                        |
| `sort` | 未指定 | Halo 排序语法：`field,(asc\|desc)`，可重复传递多个排序条件 |

`page` 或 `size` 不是正整数时返回 `400 Bad Request`。需要稳定顺序的客户端应显式传入 `sort`，例如：

```text
sort=status.latestReleaseDate,desc
sort=metadata.creationTimestamp,desc
```

公共列表还接受 Halo 标准的 `labelSelector` 和 `fieldSelector`。这些选择器只能改变筛选结果，响应仍会转换为脱敏 Public VO。

### 软件筛选

`GET /apps` 支持：

| 参数                             | 语义                                                               |
| -------------------------------- | ------------------------------------------------------------------ |
| `keyword`                        | `spec.displayName` 包含匹配                                        |
| `category`                       | `SoftwareCategory.metadata.name`；父分类会同时包含全部可见后代分类 |
| `tag`                            | `SoftwareTag.metadata.name`                                        |
| `platform`                       | `spec.platforms` 精确匹配，并兼容旧 `spec.platform`                 |
| `featured`                       | `true` 或 `false`                                                  |
| `recommended`                    | `true` 或 `false`                                                  |
| `page`, `size`, `sort`           | 分页与排序                                                         |
| `labelSelector`, `fieldSelector` | Halo 高级选择器                                                    |

`category` 和 `tag` 使用资源的 `metadata.name`，不是公开 slug。浏览器页面按 slug 访问分类或标签时，应使用 [主题 Router](./theme-api.md#模板路由)。

### 公共响应与脱敏

公共查询遵循以下安全约束：

- `SoftwareApp.spec.published` 必须为 `true`。
- `SoftwareTopic.spec.published` 必须为 `true`。
- 分类和标签的 `spec.visible` 必须为 `true`。
- `SoftwareApp.description` 在返回前经过 HTML 净化。
- 软件详情只包含 `spec.enabled=true` 的下载源，并按 `spec.priority` 升序排列。
- 普通详情中的 `DownloadAssetVo.url` 固定为 `null`。
- Public VO 不包含 `storageKey`、`extractionCode`、`passwordSecretName`、健康检查内部信息或其他 Console 私有字段。
- 关联软件只返回仍已发布的软件；关联文章只返回 Halo 中公开且已发布的文章，并净化摘要 HTML。
- 专题条目使用安全的公开投影，不暴露内部 `SoftwareApp.metadata.name`。
- 草稿、隐藏资源与不存在的资源对匿名访问统一表现为不可见。

Public VO 字段见 [主题 API 文档的公共 VO 章节](./theme-api.md#公共-vo)。

## 下载门禁

推荐使用下载授权端点获取最终 URL、提取码或密码：

```text
POST /apis/api.software.jiewen.run/v1alpha1/download-grants
```

请求体：

```json
{
    "slug": "example-app",
    "assetName": "download-asset-optional",
    "captchaToken": "turnstile-token-when-required"
}
```

`assetName` 为空时，插件只从最新已发布版本中选择启用的首选下载源；显式指定时，该下载源也必须属于当前已发布软件的已发布版本。授权成功返回：

```json
{
    "assetName": "download-asset-name",
    "versionName": "software-version-name",
    "title": "Universal DMG",
    "url": "https://downloads.example.com/app.dmg",
    "extractionCode": "optional-code",
    "password": "optional-password",
    "expiresAt": "2026-07-26T12:00:00Z"
}
```

`expiresAt` 当前为授权决策后的 60 秒，用于客户端及时消费；它不会改变第三方下载服务自身 URL 的有效期。响应包含 `Cache-Control: no-store`、`Pragma: no-cache`，并按 Cookie/Authorization 设置 `Vary`。

兼容端点 `GET /apps/{slug}/download[?asset={name}]` 仍返回 `DownloadAssetVo`，适用于无需验证码、无需返回提取码或密码的旧客户端；新主题应统一使用 `POST /download-grants`。

| 条件                                             | 状态                  | 行为                                             |
| ------------------------------------------------ | --------------------- | ------------------------------------------------ |
| 软件、已发布版本或启用下载源不存在               | `404 Not Found`       | 不返回敏感字段                                   |
| `requiresLogin=true` 且当前为匿名用户            | `401 Unauthorized`    | 不返回敏感字段                                   |
| `requiresCaptcha=true` 且 Turnstile token 无效   | `403 Forbidden`       | 不返回敏感字段                                   |
| 验证码 Secret 缺失、服务异常或 5 秒内未响应      | `503 Service Unavailable` | fail-closed，不返回敏感字段                   |
| 授权通过但没有安全可用的下载目标                 | `409 Conflict`        | 不返回 URL                                       |
| 全部门禁通过                                     | `200 OK`              | 返回短期 `DownloadGrantVo` 并增加下载计数         |

下载目标只允许 `http`、`https` 或非 `//` 开头且不含反斜杠的站内绝对路径。Secret 中的非法 URL 不会覆盖安全的普通 URL；若没有任何安全目标，授权失败。Turnstile 校验地址固定为 Cloudflare 官方端点，不能通过插件设置改写为任意 URL。

主题不得从 Console API 或原始 Extension API 绕过门禁，也不得把管理权限授予匿名角色。

## 公共错误行为

| 状态               | 典型原因                                                       |
| ------------------ | -------------------------------------------------------------- |
| `400 Bad Request`  | 非法公共分页参数                                               |
| `401 Unauthorized` | 下载源要求登录，但当前用户为匿名用户                           |
| `403 Forbidden`    | 下载源要求当前尚未完成的验证码流程                             |
| `404 Not Found`    | 软件、分类、标签、专题或下载源不存在，或资源不满足公共可见条件 |
| `409 Conflict`     | 下载源通过门禁但没有安全可用的目标                             |
| `503 Service Unavailable` | Turnstile 配置缺失、验证服务异常或超时                  |

Halo 会使用其统一错误响应结构返回状态与错误信息。客户端不应依赖 Java 异常类名。

## Console API

Console API 前缀：

```text
/apis/console.api.software.jiewen.run/v1alpha1
```

这些端点返回原始 Extension 或 Console 专用 VO，可能包含公共 API 不允许暴露的管理字段，仅供 Halo Console 和受信任的管理客户端使用。

### 软件与设置

| 方法与路径            | 说明                                               |
| --------------------- | -------------------------------------------------- |
| `GET /options`        | 获取规范化后的插件选项配置；读取失败时返回默认选项 |
| `GET /apps`           | 分页查询软件                                       |
| `GET /apps/stats`     | 软件总数、发布/草稿数、下载数和平台枚举            |
| `GET /apps/{name}`    | 按 `metadata.name` 获取软件                        |
| `POST /apps`          | 创建软件                                           |
| `PUT /apps/{name}`       | 完整替换 spec；metadata/status 始终由服务端维护     |
| `PATCH /apps/{name}`     | 非 null 字段合并更新；metadata/status 始终由服务端维护 |
| `DELETE /apps/{name}` | 删除软件                                           |

### 版本与下载源

| 方法与路径                       | 说明           |
| -------------------------------- | -------------- |
| `GET /versions`                  | 分页查询版本   |
| `POST /versions`                 | 创建版本       |
| `PUT /versions/{name}`           | 完整替换版本 spec |
| `PATCH /versions/{name}`         | 非 null 字段合并更新版本 |
| `DELETE /versions/{name}`        | 删除版本       |
| `GET /download-assets`           | 分页查询脱敏下载源 |
| `GET /download-assets/stats`     | 下载源总数、启用/停用、受保护数与下载数 |
| `POST /download-assets`          | 创建非敏感下载源配置 |
| `PUT /download-assets/{name}`    | 完整替换普通 spec 字段，保留敏感字段和 status |
| `PATCH /download-assets/{name}`  | 非 null 普通字段合并更新，保留敏感字段和 status |
| `GET /download-assets/{name}/secrets` | 读取 URL、storageKey、提取码和 Secret 引用 |
| `PUT /download-assets/{name}/secrets` | 独立更新上述敏感字段 |
| `DELETE /download-assets/{name}` | 删除下载源并返回脱敏 Console VO |

### 分类、标签与专题

| 方法与路径                    | 说明                       |
| ----------------------------- | -------------------------- |
| `GET /categories`             | 分页查询分类               |
| `POST /categories`            | 创建分类                   |
| `PUT /categories/{name}`      | 完整替换分类 spec          |
| `PATCH /categories/{name}`    | 非 null 字段合并更新分类   |
| `DELETE /categories/{name}`   | 删除分类                   |
| `GET /tags`                   | 分页查询标签               |
| `GET /tags/{name}`            | 获取标签                   |
| `POST /tags`                  | 创建标签                   |
| `PUT /tags/{name}`            | 完整替换标签 spec          |
| `PATCH /tags/{name}`          | 非 null 字段合并更新标签   |
| `DELETE /tags/{name}`         | 删除标签                   |
| `GET /topics`                 | 分页查询专题               |
| `POST /topics`                | 创建专题                   |
| `PUT /topics/{name}`          | 完整替换专题 spec          |
| `PATCH /topics/{name}`        | 非 null 字段合并更新专题   |
| `DELETE /topics/{name}`       | 删除专题                   |

### Console 列表参数

所有列表端点支持 Halo 标准的 `page`、`size`、`sort`、`labelSelector` 和 `fieldSelector`。常用领域参数：

| 资源   | 参数                                                            |
| ------ | --------------------------------------------------------------- |
| 软件   | `keyword`（显示名或 slug）、`published`、`category`、`platform` |
| 版本   | `keyword`（版本号）、`appName`                                  |
| 下载源 | `keyword`（标题）、`appName`、`versionName`、`type`、`enabled`、`protection=protected` |
| 分类   | `keyword`（显示名或 slug）、`visible`、`parentName`              |
| 标签   | `keyword`（显示名或 slug）、`visible`、`usage=used|unused`       |
| 专题   | `keyword`（标题或 slug）、`published`、`featured`                |

`appName` 和 `versionName` 均指对应 Extension 的 `metadata.name`。

### Console 更新语义

Console 写入接口采用明确的替换/合并语义：表单保存完整资源时使用 `PUT`，请求中的
`spec` 会整体替换原 spec，因此省略的可选字段会被清空；局部调用方使用 `PATCH`，只合并
值不为 `null` 的字段。两种方式都忽略客户端 metadata/status，保留服务端拥有的并发版本、
聚合计数和健康状态。下载源的 URL、storageKey、提取码与 Secret 引用不接受普通写接口
修改；即使使用 `PUT` 也会保留原值，必须通过 `/download-assets/{name}/secrets` 独立更新。

### Console 写入约束

服务端会验证并规范化以下规则：

- 软件、分类、标签和专题 slug 只允许小写字母、数字和连字符，并且在各自资源内唯一。
- 软件引用的分类、标签必须存在。
- 软件的 `platforms` 为规范字段；旧 `platform` 会自动迁移并继续用于兼容读取。
- 关联软件不能指向自身且必须存在；关联文章必须存在。
- 版本引用的软件必须存在。
- 版本大小不能为负数，架构与安装警告去重，SHA-256/MD5 必须是对应长度的十六进制摘要。
- 下载源引用的软件和版本必须存在，且该版本必须属于同一软件。
- 启用下载源必须能解析为安全 URL/站内路径；敏感字段只通过 `/secrets` 子资源读写。
- 删除软件前必须先删除其版本和下载源。
- 删除版本前必须先删除其下载源。
- 删除仍被软件引用的分类或标签会返回 `400 Bad Request`。
- 标签颜色规范化为六位十六进制颜色。

资源不存在时返回 `404 Not Found`；请求体、引用关系、slug 或删除前置条件不合法时返回 `400 Bad Request`。

## 可选 AI API

AI Foundation 是可选依赖，不影响软件管理、Finder、主题 Router 或公共 API。

| 方法与路径                  | 权限     | 说明                            |
| --------------------------- | -------- | ------------------------------- |
| `GET /ai/capabilities`      | 软件查看 | 返回 `{ "available": boolean }` |
| `POST /ai/apps/suggestions` | 软件管理 | 生成软件摘要、详情和 SEO 建议   |

建议请求：

```json
{
    "app": {
        "spec": {
            "slug": "example-app",
            "displayName": "Example App"
        }
    },
    "instruction": "使用简洁的中文介绍",
    "target": "all"
}
```

`target` 支持 `all`、`basic`、`description` 和 `seo`，缺失或未知值回退为 `all`。响应为：

```json
{
    "summary": "...",
    "description": "...",
    "seo": {
        "title": "...",
        "description": "...",
        "keywords": []
    },
    "warnings": []
}
```

AI Foundation 未安装或插件条件不满足时，AI Endpoint 不会注册；Console 应根据 capability 和插件状态隐藏相关操作。

## 原始 Extension API

Halo 会为插件 Extension 提供标准 CRUD API：

| 资源               | 路径                                                    |
| ------------------ | ------------------------------------------------------- |
| `SoftwareApp`      | `/apis/software.jiewen.run/v1alpha1/softwareapps`       |
| `SoftwareVersion`  | `/apis/software.jiewen.run/v1alpha1/softwareversions`   |
| `DownloadAsset`    | `/apis/software.jiewen.run/v1alpha1/downloadassets`     |
| `SoftwareCategory` | `/apis/software.jiewen.run/v1alpha1/softwarecategories` |
| `SoftwareTag`      | `/apis/software.jiewen.run/v1alpha1/softwaretags`       |
| `SoftwareTopic`    | `/apis/software.jiewen.run/v1alpha1/softwaretopics`     |

原始 Extension API 不执行公共 VO 脱敏，不能用于匿名前台、主题数据请求或下载门禁。插件内置的
查看、管理、敏感配置和匿名角色模板均不授予该 API；只有超级管理员或另行显式授予原始资源
权限的主体才能访问。Console 必须使用 Console API，因为其中包含敏感字段分权、引用校验、
slug 唯一性和删除前置条件。

## RBAC

| 角色模板                           | UI 权限                  | 能力                                             |
| ---------------------------------- | ------------------------ | ------------------------------------------------ |
| `role-template-software-view`      | `plugin:software:view`   | 读取软件领域资源、Console 查询和 AI capability   |
| `role-template-software-manage`    | `plugin:software:manage` | 创建、更新、删除资源和调用 AI 建议；依赖查看角色 |
| `role-template-software-secret`    | `plugin:software:secret` | 读取/更新下载 URL、storageKey、提取码和 Secret 引用；依赖查看角色 |
| `role-template-software-anonymous` | 隐藏                     | 只读公共 API 与下载门禁                          |

新增或修改 Endpoint 时，必须同步检查 `src/main/resources/extensions/roleTemplate.yaml`，避免出现“UI 能看到但 API 被拒绝”或匿名权限扩大到管理资源的情况。

## 兼容性规则

- API 版本当前为 `v1alpha1`。
- 已发布 Public VO 字段、Finder 字段与 Endpoint 路径不做无迁移删除或改名。
- 新增响应字段优先保持可选，客户端必须容忍未知字段。
- 公共响应只能新增经过安全审查的字段，不能直接返回原始 `DownloadAsset` 或 `SoftwareApp`。
- 修改查询默认值、排序、认证状态或错误码也属于契约变更，必须同步测试和文档。
