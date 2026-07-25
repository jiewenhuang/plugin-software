# 开发环境搭建

本文档说明 `plugin-software` 的本地开发、验证和构建流程。主题路由、Finder 与 REST API 的公共契约分别见 [主题 API 文档](./theme-api.md) 和 [REST API 文档](./rest-api.md)。

## 环境要求

| 组件    | 要求                                                 |
| ------- | ---------------------------------------------------- |
| Halo    | `>= 2.25.0`                                          |
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
│   ├── finders/                          # Console 与公共查询服务
│   ├── vo/                               # Public、Finder 与 Console VO
│   ├── comment/                          # SoftwareApp 评论资源适配
│   ├── ai/                               # 可选 AI 编辑辅助
│   ├── SoftwareThemeRouter.java          # 主题路由与模板模型
│   ├── SoftwareQueryEndpoint.java        # 公共 REST API
│   └── SoftwareConsoleEndpoint.java      # Console REST API
├── src/main/resources/
│   ├── extensions/                       # Settings、ConfigMap 与 RBAC
│   ├── logo.png
│   └── plugin.yaml
├── src/test/                             # Java 单元与契约测试
├── ui/
│   ├── src/api/software.ts               # 手写 Console API Client 与类型
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

- Halo：`2.25`
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

当前项目没有配置 `generateApiClient`，也没有提交生成式 OpenAPI 文件。Console API Client 和 TypeScript 类型维护在：

```text
ui/src/api/software.ts
```

后端 Endpoint、请求参数、Extension 或响应模型发生变化时，应同时更新：

1. Java 路由中的 Springdoc 声明；
2. `ui/src/api/software.ts` 中受影响的请求与类型；
3. Java 和 UI 测试；
4. [主题 API 文档](./theme-api.md) 或 [REST API 文档](./rest-api.md)。

不要执行不存在的 `generateApiClient` 流程，也不要把手写 Client 描述成生成目录。

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

## 公共契约变更检查

修改公共能力时，应按下面的边界同步验证：

| 变更类型           | 必须同步检查                                                                          |
| ------------------ | ------------------------------------------------------------------------------------- |
| 主题 Router        | 路径、模板名、模板变量、404/重定向行为、`SoftwareThemeRouterTest`、`dev/theme-api.md` |
| Finder / Public VO | 方法签名、空值与排序、敏感字段隔离、契约测试、`dev/theme-api.md`                      |
| 公共 REST API      | 参数、响应 VO、认证与错误状态、Endpoint 测试、`dev/rest-api.md`                       |
| Console API        | RBAC、`ui/src/api/software.ts`、Console 测试、`dev/rest-api.md`                       |
| Extension 字段     | Console 表单、查询索引、状态聚合、迁移与兼容性                                        |
| 下载门禁           | 匿名/登录/验证三种状态、URL 与 Secret 隔离测试                                        |

`v1alpha1` 的既有字段和方法不能无迁移地删除或改名。新增字段优先采用可选、向后兼容的方式，并为主题和 REST 消费者保留明确的默认行为。

## CI 与发布

- `.github/workflows/ci.yaml` 使用 Halo 官方可复用工作流，在 Java 21、Node.js 24 和 pnpm 10 环境执行构建。
- `.github/workflows/cd.yaml` 在 GitHub Release 发布时生成插件制品。
- Halo 应用市场发布当前未启用，启用前需要配置对应 App ID。
