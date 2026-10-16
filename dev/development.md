# 开发环境搭建

本文档说明 `plugin-software` 的本地开发、验证和构建流程。主题路由、Finder 与 REST API 的公共契约分别见 [主题 API 文档](./theme-api.md) 和 [REST API 文档](./rest-api.md)。

## 环境要求

| 组件    | 要求                                                 |
| ------- | ---------------------------------------------------- |
| Halo    | `>= 2.26.0`                                          |
| JDK     | 21                                                   |
| Docker  | 用于 Halo DevTools 开发实例                          |
| Node.js | 建议 24，与 CI 保持一致；最低按项目 Node 20 配置运行 |
| pnpm    | 10，项目锁定版本为 `10.12.4`                         |

Gradle 和 pnpm 均应使用项目提供的 Wrapper 或锁定版本，避免本地工具版本差异影响构建结果。

## 克隆仓库

```bash
git clone https://github.com/jiewenhuang/plugin-software.git
cd plugin-software
```

## 项目结构

```text
plugin-software/
├── dev/                                  # 开发与公共 API 文档
├── src/main/java/run/jiewen/software/
│   ├── extension/                        # 软件领域 Extension
│   ├── api/                              # Console Command 与下载授权请求
│   ├── download/                         # 下载目标策略与验证码校验
│   ├── reconciler/                       # 状态、计数与旧字段迁移
│   ├── finders/                          # Console 与公共查询服务
│   ├── vo/                               # Public、Finder 与 Console VO
│   ├── comment/                          # SoftwareApp 评论资源适配
│   ├── ai/                               # 可选 AI 编辑辅助
│   ├── operations/                       # 持久化创建回执、内容版本和只读预检查
│   ├── mcp/                              # 可选 MCP 工具适配与明确输入输出契约
│   ├── query/                            # HTTP / MCP 共用查询构造
│   ├── SoftwareThemeRouter.java          # 主题路由与模板模型
│   ├── SoftwareQueryEndpoint.java        # 公共 REST API
│   └── SoftwareConsoleEndpoint.java      # Console REST API
├── src/main/resources/
│   ├── extensions/                       # Settings、ConfigMap 与 RBAC
│   ├── logo.png
│   └── plugin.yaml
├── src/test/                             # Java 单元与契约测试
├── api-docs/openapi/v3_0/                # 生成的 OpenAPI JSON
├── ui/
│   ├── src/api/generated/                # OpenAPI 生成的 TypeScript Client 与模型
│   ├── src/api/software.ts               # 生成模型之上的 Console 适配层
│   ├── src/components/                   # Console 管理组件
│   ├── src/views/                        # Console 页面
│   └── src/__tests__/                    # Vue / Vitest 测试
├── build.gradle
└── settings.gradle
```

## 启动 Halo 开发实例

先确保 Docker 正在运行，然后在插件根目录执行：

```bash
./gradlew haloServer
```

当前 DevTools 配置使用：

- Halo：`2.26.0`
- 访问地址：<http://localhost:8099>
- Console：<http://localhost:8099/console>
- Swagger UI：<http://localhost:8099/swagger-ui.html>
- 本地数据目录：`workplace/`

任务启动日志会显示开发实例的管理员账号信息。`workplace/` 只用于本地调试，不应提交到仓库。

修改 Java、资源文件或 Console 产物后，重新构建并加载插件：

```bash
./gradlew reloadPlugin
```

## Console UI 开发

进入 `ui/` 安装依赖：

```bash
cd ui
pnpm install --frozen-lockfile
```

常用命令：

```bash
pnpm dev                 # Vite 开发监听构建
pnpm build               # 类型检查并生成生产产物
pnpm lint:check          # oxlint + ESLint，只检查不改文件
pnpm type-check          # vue-tsc
pnpm test:unit           # Vitest
pnpm prettier            # 格式化 ui/src
```

`pnpm dev` 是监听构建而不是独立 Web 服务器。需要在 Halo Console 中验证产物时，等待构建完成后回到插件根目录执行 `./gradlew reloadPlugin`。

## API Client 与 Swagger

公共和 Console Endpoint 使用 Springdoc Route Builder 声明 OpenAPI 元数据。启动 Halo 开发实例后，可在 Swagger UI 中查看以下 API Group：

```text
api.software.jiewen.run/v1alpha1
console.api.software.jiewen.run/v1alpha1
```

项目通过 `build.gradle` 中的 `haloPlugin.openApi` 同时生成 OpenAPI JSON 和 TypeScript Client：

```text
api-docs/openapi/v3_0/softwareApi.json
ui/src/api/generated/
```

从插件根目录执行：

```bash
./gradlew generateApiClient --no-daemon
```

`ui/src/api/generated/` 是服务端契约生成物，不要手工修改。`ui/src/api/software.ts` 只保留调用适配、创建态 metadata 放宽，以及“普通下载源 VO + 单独授权的敏感字段”这一 Console 边界。

后端 Endpoint、Command、请求参数、Extension 或响应模型发生变化时，应依次完成：

1. Java 路由中的 Springdoc 声明；
2. 执行 `generateApiClient` 并审查 OpenAPI/TypeScript 生成差异；
3. 按需调整 `ui/src/api/software.ts` 的适配逻辑；
4. Java 和 UI 测试；
5. [主题 API 文档](./theme-api.md) 或 [REST API 文档](./rest-api.md)。

嵌套模型应使用明确的 `@Schema(name = "...")`，避免生成 `spec.ts`、`status.ts` 等跨资源冲突的通用文件。生成后应确认这些通用模型名没有重新出现。

## 测试与构建

只运行 Java 测试：

```bash
./gradlew test
```

只验证 Console UI：

```bash
./gradlew :ui:check
```

执行完整检查并构建插件：

```bash
./gradlew build
```

完整构建覆盖 Java 测试、Console UI lint、Vue TypeScript 检查、Vitest 和生产构建。插件 JAR 输出到：

```text
build/libs/
```

发布前建议额外执行生成物和依赖检查：

```bash
./gradlew generateApiClient --no-daemon
pnpm --dir ui audit --prod
git diff --check
```

## 公共契约变更检查

修改公共能力时，应按下面的边界同步验证：

| 变更类型           | 必须同步检查                                                                          |
| ------------------ | ------------------------------------------------------------------------------------- |
| 主题 Router        | 路径、模板名、模板变量、404/重定向行为、`SoftwareThemeRouterTest`、`dev/theme-api.md` |
| Finder / Public VO | 方法签名、空值与排序、敏感字段隔离、契约测试、`dev/theme-api.md`                      |
| 公共 REST API      | 参数、响应 VO、认证与错误状态、Endpoint 测试、`dev/rest-api.md`                       |
| Console API        | RBAC、`ui/src/api/software.ts`、Console 测试、`dev/rest-api.md`                       |
| Extension 字段     | Console 表单、查询索引、状态聚合、迁移与兼容性                                        |
| 下载门禁           | 匿名/登录/验证、Turnstile、目标协议白名单、`no-store` 与 Secret 隔离测试               |

`v1alpha1` 的既有字段和方法不能无迁移地删除或改名。新增字段优先采用可选、向后兼容的方式，并为主题和 REST 消费者保留明确的默认行为。

## CI 与发布

- `.github/workflows/ci.yaml` 使用 Halo 官方可复用工作流，在 Java 21、Node.js 24 和 pnpm 10 环境执行构建。
- `.github/workflows/cd.yaml` 在 GitHub Release 发布时生成插件制品。
- Halo 应用市场发布当前未启用，启用前需要配置对应 App ID。

## MCP 开发与验证

MCP API 使用 Maven Central 的正式版 `1.0.0`，仅加入 compileOnly 及测试类路径。正式包不能嵌入 `run/halo/mcpserver/api` 或 MCP Java SDK。工具列表、权限和错误契约见 [mcp.md](./mcp.md)。

后端重点测试为 `SoftwareResourceOperationsTest` 和 `SoftwareMcpToolProviderTest`，分别覆盖带版本的领域修改、字段清空与敏感配置，以及 45 个 Handler 的输出、开关、参数和超时。现有 Console 查询测试保护 HTTP 兼容行为。

真实环境至少验证：未安装 MCP 时软件插件启用；安装后工具发现；密钥白名单拒绝；敏感开关与独立读写授权；MCP 停用、重启、卸载；软件插件热重载。只完成单元测试不能宣称完成这些验证。临时测试环境应独立创建并在验证后清理。

### MCP 1.2.0 回归重点

- 内部 `SoftwareCreateOperation` 位于 `operations.software.jiewen.run/v1alpha1`，没有授予普通软件角色或匿名用户原始资源权限。其 API 不属于现有业务客户端生成分组。
- 请求摘要与目标标记、pending → writing 的版本竞争和成功回执共同实现创建去重；不能通过清理“超时记录”或自动重置 writing 来恢复，否则可能重复创建。
- 必须覆盖回执持久化失败、写入后中断恢复、相同 ID 并发、资源已删除后的重放，以及相同 ID 不同输入的拒绝。
- expectedEditRevision 只适用于普通更新和发布。重试必须重新读取并检查业务摘要，不能用统计变化为理由无条件覆盖。
- 删除检查新增 `spec.relatedAppNames` 和 `spec.items.appName` 索引，预检查返回引用总数及有限名称样本；发布检查返回准备度警告，不改变既有发布规则。
