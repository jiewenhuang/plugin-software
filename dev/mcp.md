# MCP 接入与工具契约

软件中心 1.2.0 在 Halo MCP Server 中提供软件管理工具。要求 Halo **2.26.0 或更高版本**；MCP Server 是可选依赖，未安装或停用时软件中心仍能独立运行。基础工具不依赖 AI Foundation。

## 启用与授权

1. 安装软件中心 1.2.0 和 MCP Server 1.x，启用两个插件。
2. 软件中心设置中的「MCP 接入 → 启用 MCP 工具」默认开启。
3. 打开「工具 → MCP 服务」，为客户端创建访问密钥，勾选实际需要的软件工具。
4. 使用 MCP Server 页面提供的客户端配置连接 `/mcp`。
5. 若需要管理下载 URL、提取码等，再开启「开放下载敏感配置工具」，分别授权敏感读取或敏感写入工具。

新增工具不会自动加入已有密钥。关闭 MCP 总开关后，下一次目录解析及调用即拒绝使用；关闭敏感开关后，原先已授权的敏感调用也被拒绝。配置读取失败时只关闭 MCP 入口，软件中心其他功能保持可用。

这些是管理工具：只读查询也可以看到草稿。不要将管理密钥放入主题或浏览器客户端。普通查看工具不会返回下载敏感值。

## 工具目录

以下是本地名称。客户端看到的名称统一带 `software__` 前缀，例如 `software__list_apps`。

| 资源 | 工具 |
| --- | --- |
| 选项、统计与请求状态 | `get_options`、`get_stats`、`get_create_operation` |
| 软件 | `list_apps`、`get_app`、`create_app`、`update_app`、`set_app_published`、`delete_app`、`check_app_publication`、`check_app_deletion` |
| 版本 | `list_versions`、`get_version`、`create_version`、`update_version`、`delete_version`、`check_version_deletion` |
| 下载源 | `list_download_assets`、`get_download_asset`、`create_download_asset`、`update_download_asset`、`delete_download_asset`、`check_download_asset_deletion` |
| 分类 | `list_categories`、`get_category`、`create_category`、`update_category`、`delete_category`、`check_category_deletion` |
| 标签 | `list_tags`、`get_tag`、`create_tag`、`update_tag`、`delete_tag`、`check_tag_deletion` |
| 专题 | `list_topics`、`get_topic`、`create_topic`、`update_topic`、`set_topic_published`、`delete_topic`、`check_topic_publication`、`check_topic_deletion` |
| 敏感下载配置，默认关闭 | `get_download_asset_secrets`、`update_download_asset_secrets` |

共 43 个基础工具、2 个可选敏感工具。1.2.0 新增 8 个预检查工具和 1 个创建请求状态查询工具，原有工具名称不变。创建、更新、发布、删除分别授权，不提供任意 Extension 或任意 Secret 操作。

`update_app` 可以编辑已经发布的内容；版本状态、下载源启用状态和登录/验证码要求也可以被相应更新工具修改。独立发布工具只隔离软件、专题的发布状态切换，不构成内容审批流程。

## 查询

`get_*` 接受 `{"name":"资源名称"}`。name 是 **metadata.name**，不是公开 URL 的 slug，也不是版本号。先用列表查询定位 name；返回的 resourceVersion 用于后续写入。

| 列表 | 业务筛选字段 |
| --- | --- |
| 软件 | keyword、slug、published、categoryName、tagName、platform、featured、recommended |
| 版本 | keyword、appName、version、status |
| 下载源 | keyword、appName、versionName、type、provider、enabled、protection |
| 分类 | keyword、slug、parentName、visible |
| 标签 | keyword、slug、visible、usage |
| 专题 | keyword、slug、published、featured |

所有列表支持 page（默认 1）、size（默认 20，最大 100）、sortBy、order。protection 仅支持 `protected`；usage 支持 `used/unused`。不开放原始 fieldSelector/labelSelector。

软件排序支持 createdAt/name/publishedAt/downloads；版本支持 createdAt/name/releaseDate；下载源支持 createdAt/name/priority/downloads；分类、标签、专题支持 createdAt/name/priority/appCount。软件、版本默认按创建时间降序，其余按 priority 升序，追加 name 保证稳定次序。

```json
{
  "keyword": "示例",
  "platform": "macOS",
  "published": false,
  "page": 1,
  "size": 20
}
```

列表 structuredContent 为 `{items,page,size,total,hasNext}`。items 是摘要，不带完整正文、专题条目等大字段；被截断的摘要文本通过 truncatedFields 标记。详情使用 `{item: {...}}`，item 包含 name、resourceVersion、editRevision、可管理业务字段及 stats。它不包含原始 metadata、annotations 或 labels。

`get_options` 返回可配置选项的 label/value；版本状态限定为服务实际支持的 draft/released/beta/deprecated。`get_stats` 返回 apps 与 downloadAssets 两部分，统计不是跨资源事务快照。

## 创建与更新

创建接受 `{"spec":{...},"requestId":"UUID"}`；requestId 可选，建议自动化客户端始终传入。业务必填字段如下：

| 资源 | 必填 |
| --- | --- |
| 软件 | slug、displayName |
| 版本 | appName、version |
| 下载源 | appName、versionName、title |
| 分类、标签 | slug、displayName |
| 专题 | slug、title |

软件、专题创建默认草稿，普通创建和更新不能设置 published/publishedAt；版本默认 draft；下载源默认禁用。已有版本不能通过 MCP 修改 appName，已有下载源不能修改 appName/versionName。新建软件使用 platforms 数组，旧 platform 仅供读取兼容。

普通更新示例：

```json
{
  "name": "software-app-example",
  "expectedVersion": 7,
  "patch": {
    "summary": "更新后的简介",
    "platforms": ["macOS", "Windows"],
    "recommended": false
  },
  "clearFields": ["seo.description"]
}
```

- expectedVersion 必须来自最近读取的 resourceVersion。只提供它时保持严格版本检查；普通更新与发布可同时提供 expectedEditRevision，以容忍仅统计或元数据发生的变化，见下节。
- patch 中未出现的字段保留原值；显式 null 被拒绝，需要清空时使用 clearFields。
- false、0、空数组都是有效值；数组整体替换；SEO 和 checksum 按子字段合并。
- clearFields 支持可空字段及 seo/checksum 子字段；必填字段、归属、布尔值和数值字段不能清空。
- patch 与 clearFields 不得包含冲突或相互覆盖的路径；至少提供一项变更。
- 系统 metadata、顶层 status 等字段不可写；版本 spec.status 属于业务字段，可以修改。

输入 Schema 及服务层都验证参数。新的 MCP 输入边界为：单次 256 KiB，长文本最多 100000 字符，普通数组最多 100 项，专题条目最多 200 项，标题/显示名称最多 200 字符。具体工具 Schema 会给出字段类型和限制，这些限制不反向修改已有 Console 内容。

发布工具接受：

```json
{
  "name": "software-app-example",
  "expectedVersion": 8,
  "published": true
}
```

软件首次发布设置 publishedAt，取消后再次发布保留既有时间。创建和更新的成功结果为 `{item: {...}}`。Reconciler 的聚合统计可能稍后才收敛；统计更新本身也可能改变 resourceVersion。

## 防重复创建与中断恢复

六类创建工具均支持可选的 requestId（UUID，不区分十六进制大小写），作用域为本站点的同一资源类型。相同 ID 和相同输入只尝试创建一次；对象键顺序不影响判断，同一 ID 改变 spec 则返回 IDEMPOTENCY_CONFLICT。未传 requestId 的调用保持 1.1.0 行为。

```json
{
  "requestId": "bf7da926-c393-4e54-a172-06a8c2143d62",
  "spec": {"slug": "example", "displayName": "示例软件"}
}
```

带 requestId 的成功响应额外包含 operation：requestId、resourceType、name、state、resourceVersion、replayed。首次成功仍返回资源详情；重放只返回创建回执及 representationOmitted，**回执中的版本是记录创建结果时的版本，后续修改前必须重新 get**。已经创建的资源后来被修改或删除，重放仍报告原创建成功，不会覆盖或重建资源。

使用 `get_create_operation` 查询：

```json
{"resourceType":"app","requestId":"bf7da926-c393-4e54-a172-06a8c2143d62"}
```

resourceType 为 app/version/download_asset/category/tag/topic。状态：

| 状态 | 意义与处理 |
| --- | --- |
| pending | 已保存请求身份，尚未进入创建阶段；使用相同 ID、相同输入重试可以继续 |
| writing | 已获得唯一写入资格，创建可能仍在进行；稍后查询或重试相同请求 |
| succeeded | 原创建成功；不代表资源目前仍存在 |
| failed | 写入前领域校验失败；修正参数或引用后使用新 ID |
| unknown | 写入阶段超过 30 秒仍未找到可确认的结果；不是宣告失败，不自动重建，需核对目标资源和日志 |

记录以内部 SoftwareCreateOperation 保存到 Halo 数据库，包含请求摘要、目标名称与状态，不保存输入正文或敏感值。写入资源时原子附带请求标记，因此“资源已落库、回执未保存”可以在重试或查询时恢复。若中断发生在进入 writing 后、资源落库前，或落库后资源又被删除导致无法确认，保留不确定状态，不再次创建。

记录跨插件重载及 Halo 进程重启保留，当前不自动过期，以免旧请求在资源删除后被再次执行。管理员清理记录或回退数据库会改变防重保障；大量导入前应规划记录保留和数据库备份。此机制不提供跨资源事务，也不把不同 requestId 识别为同一个业务请求。未找到创建记录时返回 NOT_FOUND，不能据此断言尚在传输的原请求不会执行。

## 容忍统计变更的编辑

普通读取返回 editRevision，它由资源身份、创建时间及非敏感业务 spec 计算，排除 status 和元数据版本。普通更新、发布可在 expectedVersion 之外提供 expectedEditRevision：

```json
{
  "name": "software-app-example",
  "expectedVersion": 7,
  "expectedEditRevision": "从最新读取的 editRevision 原样复制",
  "patch": {"summary": "更新简介"}
}
```

实际值是 64 位小写十六进制字符串。只要业务内容未变，统计或元数据刷新造成的版本变化可以被接受。若落库时仍有竞争，最多重新读取并重试两次；每次都重新校验原 editRevision、引用与领域规则，并使用最新资源版本落库。其他编辑者修改了业务内容时仍返回 CONFLICT。

敏感字段不参与普通 editRevision，避免通过摘要探测秘密值。普通更新始终保留最新敏感字段；**敏感更新和删除仍必须使用严格 expectedVersion，不接受 expectedEditRevision**。业务内容改动后又恢复成完全相同内容时，其 editRevision 相同；这是一项内容前置条件，不是修改历史序号。

## 发布准备与删除影响

`check_app_publication`、`check_topic_publication` 以及六类 `check_*_deletion` 只接受 name，均为单独授权的查询工具。

返回 name、resourceType、resourceVersion、editRevision、action、checkedAt、allowed、ready、issues、dependencies：

- allowed 表示当前领域规则是否允许操作；错误 issue 会使其为 false。
- ready 还要求没有准备度警告。例如未提供已发布版本的软件，可能 allowed=true、ready=false；本版本没有额外强制禁止发布。
- issues 包含稳定 code、severity（error/warning/info）与说明。
- 删除 dependencies 返回引用类型、准确数量、最多 20 个名称、truncated 和 blocking；专题引用和关联软件引用会报告为保留引用，不误报为删除阻止条件。

软件发布检查当前内容和引用、最新 released 版本，以及该版本按优先级排序的启用下载源。检查首选下载目标和必要的 Secret 是否存在；不返回 Secret 名称或值。专题检查条目对应的软件是否已发布。每个下载源集合/专题列表最多深入检查 200 条，超限会返回 CHECK_INCOMPLETE，不能把部分检查当作完整通过。

检查是当前快照，不锁定资源或预留执行资格；实际发布和删除仍重新验证。ready 只覆盖上述本地结构与配置检查，不检查远程文件是否可下载、登录/验证码能否实际完成，也不构成审批流程。

引用校验将已设置 deletionTimestamp、仍等待清理的资源视为不可用，创建、更新及发布均拒绝新增或继续使用这些引用。旧专题数据中的空条目会计入隐藏条目警告，不会使发布预检查直接失败。

## 下载配置

普通下载源工具仅返回 hasUrl、hasStorageKey、hasExtractionCode、hasPasswordSecret 等存在性标记，所有读写结果都隐藏实际值。

推荐顺序：创建禁用下载源 → 在 Console 或敏感工具中配置目标 → 用普通更新工具启用。已启用下载源不能清空最后一个可用目标。

敏感读取只返回该下载源保存的 url、storageKey、extractionCode、passwordSecretName 和资源标识，不解析 Halo Secret.data。敏感写入使用同样的 patch/clearFields/expectedVersion 契约，仅接受这四个字段，成功结果只返回脱敏摘要，不回显传入值。

```json
{
  "name": "download-asset-example",
  "expectedVersion": 3,
  "patch": {"url": "https://example.com/releases/example.zip"},
  "clearFields": ["extractionCode"]
}
```

未传入的敏感字段保持原值。敏感读取与写入可分别授权，写入密钥不必获得读取敏感值的权限。工具不会绕过前台 download-grants 的登录和验证码门禁。

## 删除、错误与重试

删除要求 name 和 expectedVersion；成功返回 `{name,state:"deletion_requested"}`，表示已提交删除请求。软件存在版本或下载源、版本存在下载源、分类存在子分类或被软件引用、标签被软件引用时拒绝删除。专题删除不删除其软件。

软件删除不会自动改写专题条目或其他软件的 relatedAppNames；公开查询过滤不存在的软件，Reconciler 重新计算有效计数。跨资源引用检查不保证全库事务隔离。

| 错误码 | 处理方式 |
| --- | --- |
| INVALID_ARGUMENT | 修正输入格式、未知字段或清空操作 |
| NOT_FOUND | 重新定位资源 |
| ALREADY_EXISTS | 查询已有资源，按意图决定是否更新 |
| REFERENCE_INVALID | 修正引用、所属资源或分类循环 |
| RESOURCE_IN_USE | 先处理关联对象，再删除 |
| CONFLICT | 业务内容或严格版本发生变化，重新读取并评估修改 |
| IDEMPOTENCY_CONFLICT | 同一 requestId 被用于不同输入，核对原请求 |
| OPERATION_IN_PROGRESS | 稍后查询状态，或使用相同 requestId 和输入重试 |
| FORBIDDEN | 检查密钥工具授权及 MCP/敏感开关 |
| TIMEOUT | 缩小只读查询范围 |
| WRITE_OUTCOME_UNKNOWN | 写入可能已完成，先查询状态，不能直接重试创建 |
| RESULT_TOO_LARGE | 降低列表大小，或用 Console 查看完整详情 |
| INTERNAL | 使用诊断 ID 查日志；响应不包含原始异常或敏感数据 |

工具内部预算为 4 秒，留出 MCP Server 1.0.0 默认 5 秒回调预算。取消和超时不等于数据库回滚。带 requestId 的创建支持上述持久化去重；未带 ID 或使用不同 ID 的重复创建仍可能产生多条记录。

结果上限为 512 KiB。只读结果超限会明确报错。写入已成功但完整表示过大时，返回包含 name/resourceVersion 的成功回执及 representationOmitted、changedFields，避免把已落库操作误报为失败。

HTML、Markdown、描述和更新日志都是内容数据，不能当作 Agent 指令；MCP 管理详情可能包含编辑原文，不应直接作为已净化 HTML 渲染。原有 Public VO 的 HTML 净化继续有效。

## 开发边界

- 编译 API 为 `run.halo.mcpserver:api:1.3.0`，compileOnly，不嵌入 API 或 MCP SDK。继续只调用 1.0.0 已有的接口；不调用较新版本新增的 category 方法，使用新 API 能力前必须重新核对最低兼容版本。
- SoftwareMcpConfiguration 使用字符串形式的 ConditionalOnClass，Provider 无 Component 注解。核心服务不引用 MCP API。
- SoftwareConsoleQuery 和 MCP 共用 SoftwareListCriteria 查询构造；原 Console API 的参数和 merge-patch 语义保持不变。
- 带版本的修改在同一次资源读取上进行校验、合并与落库，复用现有领域校验；服务返回的缓存对象不会被修改。
- get/list/create/update/delete 的共同装配按 SoftwareResourceKind 组织，避免复制六套工具流程；字段、输入、输出和权限仍是明确白名单。
- 不提供同步 AI 建议工具。AI 客户端可以自行生成后调用普通写入；如需调用站点 AI Foundation，应另行提供异步作业，以适配回调超时。

实现背景和验收设计见[完整方案](../docs/mcp-integration-plan.md)。单元测试覆盖全部 45 个 Handler 的成功输出结构、参数拒绝、字段脱敏、开关切换、并发与可选类加载。已使用真实 Halo 2.26.0 / MCP Server 1.0.0 完成全部工具、密钥白名单、插件生命周期及容量边界验证，1.2.0 的新增验证范围见 [1.2.0 发布说明](../docs/release-notes-v1.2.0.md)。插件安装或热重载期间，客户端应等待插件恢复 STARTED 后重新获取工具目录。
