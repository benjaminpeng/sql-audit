# ⚡ Ultimate-SQL Audit

**[English](#english) | [中文](#中文)**

---

<a id="english"></a>

## English

A powerful, automated SQL compliance scanner designed for **MyBatis XML Mapper** files and **standalone SQL scripts**. This tool helps developers enforce **OpenGauss SQL Development Standards** through static analysis with a modern, premium UI.

### 🚀 Key Features

- **20 Built-in OpenGauss Rules** — Covers sections 3.2 (Schema), 3.3 (WHERE), 3.4 (SELECT), 3.6 (UPDATE), 3.7 (DELETE), 3.8 (Joins), 3.9 (Subqueries), and Security.
- **Dual Scan Modes** — Scan MyBatis XML Mapper directories **or** upload standalone `.sql` change scripts.
- **MyBatis Safety** — Detects potential SQL injection risks (`${}`).
- **Custom Rules** — Upload a `.docx` Word document to dynamically load custom regex-based rules.
- **Modern UI** — Dark-theme glassmorphism design with workflow-based layout, animated gradients, and responsive two-column interface.
- **Visual Reports** — Interactive dashboard to filter violations by Severity (ERROR / WARNING / INFO) and Category.

### 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21 (Virtual Threads), Spring Boot 3.4.1, SQLite (embedded) |
| **Frontend** | Vite 6, Vanilla JS (ES Modules), CSS3 Glassmorphism |
| **Parsing** | DOM-based XML parsing, Regex rule engine |
| **Build** | Maven, Shell (`start.sh`) |

### 🏁 Getting Started

#### Prerequisites

- **Java 21** or higher
- **Node.js 18** or higher
- **Maven 3.8+**

#### Quick Start (One-Click)

```bash
chmod +x start.sh
./start.sh
```

This will:
1. Kill any existing processes on ports 8080 and 5174.
2. Build and start the Spring Boot backend.
3. Start the Vite frontend dev server.

Access the app at: **http://localhost:5174**

#### Manual Setup

**Backend:**
```bash
cd backend
mvn clean spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev -- --port 5174
```

### 🏗 Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Frontend    │────▶│  REST API    │────▶│  Rule Engine │
│  (Vite+JS)  │◀────│  (Spring)    │◀────│  (Checkers)  │
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
                    ┌──────▼───────┐
                    │  Parsers     │
                    │  - MyBatis   │
                    │  - SQL Script│
                    │  - Word Doc  │
                    └──────────────┘
```

1. **Scanning** — `ScanController` receives a directory path or uploaded `.sql` file.
2. **Parsing** — `MyBatisMapperParser` extracts SQL from XML; `SqlScriptParser` splits standalone scripts.
3. **Rule Engine** — `RuleService` applies all active `SqlChecker` implementations against each `SqlFragment`.
4. **Reporting** — Returns a `ScanReport` with categorized `Violation` records.

### 📏 Built-in Rules (OpenGauss)

| Section | Rule | Sev | Description |
|---|---|---|---|
| 3.2.2 | `SCHEMA_PREFIX` | 🟡 | Recommend schema prefix for table references |
| 3.3.1 | `NULL_COMPARISON` | 🔴 | Use `IS NULL` instead of `= NULL` |
| 3.3.3 | `WHERE_FUNCTION` | 🟡 | Avoid functions on WHERE columns |
| 3.3.4 | `NOT_EQUAL_OPS` | 🟡 | Avoid `!=`, `<>` (index invalidation) |
| 3.3.5 | `LIKE_PERCENT` | 🟡 | Avoid leading `%` in LIKE patterns |
| 3.3.6 | `IN_SUBQUERY_SIZE` | 🟡 | Keep IN-clause subsets small |
| 3.4.1 | `NO_SELECT_STAR` | 🔴 | Explicitly list columns |
| 3.4.3 | `LOCK_TABLE` | 🔴 | Forbid LOCK TABLE |
| 3.4.4 | `UNION_ALL` | 🟡 | Prefer UNION ALL over UNION |
| 3.4.5 | `COUNT_USAGE` | 🟡 | Use count() cautiously |
| 3.4.6 | `SELECT_PAGINATION` | 🟡 | Recommend pagination for large queries |
| 3.6.1 | `UPDATE_LIMIT` | 🔴 | Forbid LIMIT in UPDATE |
| 3.6.3 | `UPDATE_WHERE` | 🔴 | UPDATE must have WHERE |
| 3.7.2 | `TRUNCATE` | 🔴 | Forbid TRUNCATE for full-table deletes |
| 3.7.3 | `DELETE_WHERE` | 🔴 | DELETE must have WHERE |
| 3.8.1 | `JOIN_TABLE_LIMIT` | 🔴 | Limit number of joined tables |
| 3.8.3 | `IMPLICIT_JOIN` | 🔴 | Use explicit JOIN syntax |
| 3.9.3 | `SUBQUERY_IN_TARGET` | 🔴 | Avoid subqueries in SELECT target list |
| 3.9.4 | `SUBQUERY_DEPTH` | 🔴 | Limit subquery nesting to 2 levels |
| MyBatis | `SQL_INJECTION` | 🔴 | Use `#{}` instead of `${}` |

### ❓ Troubleshooting

- **Port 8080 in use?** — Run `./start.sh` (auto-kills), or `lsof -ti:8080 | xargs kill -9`.
- **Frontend can't connect?** — Verify backend at `http://localhost:8080/api/rules`.
- **"invalid source release: 21"?** — Ensure JDK 21 is installed and `JAVA_HOME` is set.

---

<a id="中文"></a>

## 中文

一款强大的自动化 SQL 合规扫描工具，支持 **MyBatis XML Mapper** 文件和**独立 SQL 脚本**的静态分析。帮助开发者严格遵循 **OpenGauss SQL 开发规范**，配备现代化高级 UI。

### 🚀 核心功能

- **20 条内置 OpenGauss 规则** — 覆盖 3.2（Schema）、3.3（WHERE）、3.4（SELECT）、3.6（UPDATE）、3.7（DELETE）、3.8（关联查询）、3.9（子查询）及安全规范。
- **双模式扫描** — 扫描 MyBatis XML Mapper 目录 **或** 直接上传 `.sql` 变更脚本。
- **MyBatis 安全检测** — 识别 `${}` 拼接的 SQL 注入风险。
- **自定义规则** — 上传 `.docx` Word 规范文档，动态加载自定义正则规则。
- **现代化 UI** — 深色主题 + 玻璃拟态设计，工作流程左右分栏布局，动态渐变动画，完全响应式。
- **可视化报告** — 交互式仪表盘，按严重程度（错误 / 警告 / 提示）和分类筛选违规项。

### 🛠 技术栈

| 层级 | 技术 |
|---|---|
| **后端** | Java 21（虚拟线程）、Spring Boot 3.4.1、SQLite（内嵌） |
| **前端** | Vite 6、原生 JS（ES Modules）、CSS3 玻璃拟态 |
| **解析** | DOM XML 解析、正则规则引擎 |
| **构建** | Maven、Shell（`start.sh`） |

### 🏁 快速开始

#### 环境要求

- **Java 21** 或更高版本
- **Node.js 18** 或更高版本
- **Maven 3.8+**

#### 一键启动

```bash
chmod +x start.sh
./start.sh
```

脚本会自动：
1. 终止 8080 和 5174 端口的已有进程。
2. 编译并启动 Spring Boot 后端。
3. 启动 Vite 前端开发服务器。

打开浏览器访问：**http://localhost:5174**

#### 手动启动

**后端：**
```bash
cd backend
mvn clean spring-boot:run
```

**前端：**
```bash
cd frontend
npm install
npm run dev -- --port 5174
```

### 🏗 架构概览

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  前端        │────▶│  REST API    │────▶│  规则引擎    │
│  (Vite+JS)  │◀────│  (Spring)    │◀────│  (Checkers)  │
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
                    ┌──────▼───────┐
                    │  解析器       │
                    │  - MyBatis   │
                    │  - SQL 脚本  │
                    │  - Word 文档 │
                    └──────────────┘
```

1. **扫描入口** — `ScanController` 接收目录路径或上传的 `.sql` 文件。
2. **解析** — `MyBatisMapperParser` 从 XML 中提取 SQL；`SqlScriptParser` 拆分独立脚本。
3. **规则引擎** — `RuleService` 对每个 `SqlFragment` 执行所有 `SqlChecker` 检查。
4. **报告生成** — 返回 `ScanReport`，包含分类的 `Violation` 记录。

### 📏 内置规则（OpenGauss 规范）

| 章节 | 规则 | 等级 | 描述 |
|---|---|---|---|
| 3.2.2 | `SCHEMA_PREFIX` | 🟡 | 建议使用 Schema 前缀 |
| 3.3.1 | `NULL_COMPARISON` | 🔴 | 禁止用 `=` 或 `!=` 判断 NULL |
| 3.3.3 | `WHERE_FUNCTION` | 🟡 | WHERE 条件字段禁用函数 |
| 3.3.4 | `NOT_EQUAL_OPS` | 🟡 | 少用负向操作符 |
| 3.3.5 | `LIKE_PERCENT` | 🟡 | LIKE 禁止前缀 `%` |
| 3.3.6 | `IN_SUBQUERY_SIZE` | 🟡 | IN 子集不宜过大 |
| 3.4.1 | `NO_SELECT_STAR` | 🔴 | 禁止 SELECT * |
| 3.4.3 | `LOCK_TABLE` | 🔴 | 禁止 LOCK TABLE |
| 3.4.4 | `UNION_ALL` | 🟡 | 优先使用 UNION ALL |
| 3.4.5 | `COUNT_USAGE` | 🟡 | 慎用 count() |
| 3.4.6 | `SELECT_PAGINATION` | 🟡 | SELECT 建议分页 |
| 3.6.1 | `UPDATE_LIMIT` | 🔴 | UPDATE 禁用 LIMIT |
| 3.6.3 | `UPDATE_WHERE` | 🔴 | UPDATE 必须有 WHERE |
| 3.7.2 | `TRUNCATE` | 🔴 | 全表删除用 TRUNCATE |
| 3.7.3 | `DELETE_WHERE` | 🔴 | DELETE 必须有 WHERE |
| 3.8.1 | `JOIN_TABLE_LIMIT` | 🔴 | 限制关联表数量 |
| 3.8.3 | `IMPLICIT_JOIN` | 🔴 | 禁止隐式 JOIN |
| 3.9.3 | `SUBQUERY_IN_TARGET` | 🔴 | 目标列禁用子查询 |
| 3.9.4 | `SUBQUERY_DEPTH` | 🔴 | 子查询嵌套不超过 2 层 |
| MyBatis | `SQL_INJECTION` | 🔴 | MyBatis SQL 注入风险 |

### ❓ 常见问题

- **端口 8080 被占用？** — 运行 `./start.sh`（自动终止），或 `lsof -ti:8080 | xargs kill -9`。
- **前端无法连接后端？** — 确认后端已启动：`http://localhost:8080/api/rules`。
- **编译报 "invalid source release: 21"？** — 确保已安装 JDK 21 并正确设置 `JAVA_HOME`。

---

*Built with ❤️ by Antigravity Agent*
