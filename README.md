# ⚡ Ultimate-SQL Audit

**[English](#english) | [中文](#中文)**

---

<a id="english"></a>

## English

A powerful, automated SQL compliance scanner designed for **MyBatis XML Mapper** files and **standalone SQL scripts**. This tool helps developers enforce **OpenGauss SQL Development Standards** through static analysis with a modern, premium UI.

### 🚀 Key Features

- **41 Built-in OpenGauss Rules** — `21` shared rules for MyBatis and `.sql` scans, plus `20` SQL-script-only DDL/object design rules.
- **Dual Scan Modes** — Scan MyBatis XML Mapper directories **or** upload standalone `.sql` change scripts.
- **MyBatis Safety** — Detects potential SQL injection risks (`${}`).
- **DDL / Script Governance** — Enforces object naming, database locale/encoding, primary-key/comment requirements, forbidden triggers/functions/procedures, and other OpenGauss-focused script rules.
- **CLI + Skill Ready** — Supports fat-jar CLI execution and a local `sql-audit-scan` Codex skill wrapper.
- **Custom Rules** — Upload a `.docx` Word document to dynamically load custom regex-based rules.
- **Modern UI** — Dark-theme glassmorphism design with workflow-based layout, animated gradients, and responsive two-column interface.
- **Visual Reports** — Interactive dashboard to filter violations by Severity (ERROR / WARNING / INFO) and Category.

### 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.4.2 |
| **Frontend** | Vite 6, Vanilla JS (ES Modules), CSS3 Glassmorphism |
| **Parsing** | DOM-based MyBatis parsing, SQL script splitting, Word rule extraction |
| **Rule Engine** | Built-in `SqlChecker` + script-level `SqlScriptChecker` |
| **Build** | Maven, Shell (`start.sh`) |

### 🏁 Getting Started

#### Prerequisites

- **Java 21** or higher
- **Node.js 18** or higher
- **Maven 3.8+**

#### Quick Start (One-Click)

```bash
bash ./start.sh
```

This will:
1. Kill any existing processes on ports 8081 and 5174.
2. Validate Java / Maven / Node.js versions (Java 21+, Node 18+).
3. Auto-install frontend dependencies on first run (`npm install`).
4. Build and start the Spring Boot backend.
5. Start the Vite frontend dev server.

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

#### CLI Mode

The backend also supports non-web CLI execution for local automation:

```bash
cd backend
mvn -q -DskipTests package
java -jar target/sql-audit-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --sql-audit.cli.enabled=true \
  --sql-audit.cli.sql-file=/abs/path/to/change.sql \
  --sql-audit.cli.json-out=/tmp/sql-audit-report.json \
  --sql-audit.cli.markdown-out=/tmp/sql-audit-report.md
```

Scan a MyBatis repository directory by replacing `--sql-audit.cli.sql-file=...` with:

```bash
--sql-audit.cli.repo-path=/abs/path/to/mybatis-project
```

#### Codex Skill Usage

Install the local skill symlink once:

```bash
./scripts/install-skill.sh
```

The skill wrapper will build the backend jar when needed, run the CLI, and print a compact JSON envelope with summary counts and report paths:

```bash
./skills/sql-audit-scan/scripts/invoke_scan.sh /abs/path/to/mybatis-project
./skills/sql-audit-scan/scripts/invoke_scan.sh /abs/path/to/change.sql
printf 'SELECT * FROM demo_user;' | ./skills/sql-audit-scan/scripts/invoke_scan.sh
```

Installed target:

```text
~/.codex/skills/sql-audit-scan
```

#### WSL (Windows Subsystem for Linux) Notes

- Prefer running via `bash ./start.sh` (more reliable than `./start.sh` when the repo is on `/mnt/c` and execute permissions are limited).
- Use **Linux-native Node/npm** in WSL. Do not use Windows `node.exe` / `npm.cmd` from `/mnt/c/...` (this often causes install/build failures such as platform-mismatched `esbuild` binaries).
- Quick install inside WSL (Ubuntu/Debian):
  ```bash
  sudo apt update
  sudo apt install -y curl
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
  source ~/.nvm/nvm.sh
  nvm install --lts
  node -v && npm -v
  ```
- In the UI, repository paths can be:
  - Linux paths like `/home/you/project`
  - WSL mount paths like `/mnt/c/Users/you/project`
  - Windows paths like `C:\Users\you\project` (backend will auto-convert to `/mnt/c/...` on WSL)
- For better performance, keep the project and scanned repositories under `/home/...` instead of `/mnt/c/...` when possible.
- If frontend hot reload is unstable on WSL, this project already enables polling file watch mode automatically when running inside WSL.

#### WSL Corporate Intranet Setup (Maven + npm, Detailed)

If your company network only allows internal mirrors, complete the following once in WSL.

1. Collect required information from IT/platform team
   - Maven mirror URL (for example `https://maven.company.local/repository/maven-public/`)
   - Maven account/token
   - npm registry URL (for example `https://npm.company.local/repository/npm-group/`)
   - npm token/account
   - Corporate root/intermediate CA certificate (`.crt`)
   - (Optional) internal proxy (`http://proxy.company.local:8080`)

2. Ensure WSL base tools are installed
   ```bash
   sudo apt update
   sudo apt install -y ca-certificates curl unzip jq
   ```

3. Install Java 21 in WSL
   ```bash
   sudo apt install -y openjdk-21-jdk maven
   java -version
   mvn -v
   ```
   Expected: Java major version is `21` or above.

4. Install Linux-native Node.js in WSL (recommended with nvm)
   ```bash
   curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
   source ~/.nvm/nvm.sh
   nvm install --lts
   node -v
   npm -v
   ```
   Important: `which node` and `which npm` should point to Linux paths (`/usr/...` or `~/.nvm/...`), not `/mnt/c/...`.

5. Import corporate CA certificate (required in many company networks)
   ```bash
   sudo cp /path/to/company-root-ca.crt /usr/local/share/ca-certificates/company-root-ca.crt
   sudo update-ca-certificates
   ```
   If you store cert on Windows side, a common source path is:
   `/mnt/c/Users/<YourUser>/Downloads/company-root-ca.crt`

6. Configure Maven mirror
   - Create directory and copy template:
   ```bash
   mkdir -p ~/.m2
   cp docs/templates/maven-settings-corp.xml ~/.m2/settings.xml
   ```
   - Edit `~/.m2/settings.xml`:
     - Replace `https://maven.company.local/...` with your real mirror URL
     - Keep `<mirror><id>corp-mirror</id></mirror>` and `<server><id>corp-mirror</id></server>` identical
     - Prefer env vars for credentials (already prepared in template)
   - Set credentials (do not commit these values):
   ```bash
   export CORP_MAVEN_USER='your_user'
   export CORP_MAVEN_PASS='your_password_or_token'
   ```
   - Verify:
   ```bash
   mvn -s ~/.m2/settings.xml -q help:effective-settings
   ```

7. Configure npm registry mirror
   - Copy template:
   ```bash
   cp docs/templates/npmrc-corp ~/.npmrc
   ```
   - Edit `~/.npmrc`:
     - Replace `npm.company.local/...` with your npm mirror URL
   - Set token:
   ```bash
   export NPM_TOKEN='your_npm_token'
   ```
   - Verify:
   ```bash
   npm config get registry
   npm view vite version
   ```

8. (Optional) Configure proxy if your network requires it
   ```bash
   npm config set proxy http://proxy.company.local:8080
   npm config set https-proxy http://proxy.company.local:8080
   export MAVEN_OPTS="-Dhttp.proxyHost=proxy.company.local -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.company.local -Dhttps.proxyPort=8080"
   ```

9. Persist env vars for next shell session
   Add to `~/.bashrc` or `~/.zshrc`:
   ```bash
   export CORP_MAVEN_USER='your_user'
   export CORP_MAVEN_PASS='your_password_or_token'
   export NPM_TOKEN='your_npm_token'
   ```
   Then reload:
   ```bash
   source ~/.bashrc
   ```

10. Start project with mirror-aware settings
   ```bash
   cd /path/to/sql-audit
   MAVEN_SETTINGS_FILE="$HOME/.m2/settings.xml" bash ./start.sh
   ```
   Notes:
   - `start.sh` now supports `MAVEN_SETTINGS_FILE` (or `MAVEN_SETTINGS`) to force Maven mirror settings.
   - On WSL, script will reject Windows `node.exe`/`npm.cmd` to prevent cross-platform dependency corruption.

11. Quick diagnosis commands (copy/paste when troubleshooting)
   ```bash
   which java && java -version
   which mvn && mvn -v
   which node && node -v
   which npm && npm -v
   npm config get registry
   mvn -s ~/.m2/settings.xml help:effective-settings -Doutput=/tmp/effective-settings.xml
   tail -n 100 backend/backend.log
   tail -n 100 frontend/frontend.log
   ```

12. Typical failures and fixes
   - `PKIX path building failed`:
     - Corporate CA not installed correctly. Re-check Step 5.
   - `401 Unauthorized` (Maven or npm):
     - Wrong credentials/token, or mirror URL path mismatch.
   - `esbuild` platform mismatch / `Cannot find module ...`:
     - Usually Windows Node/npm used in WSL before. Delete `frontend/node_modules` and rerun `bash ./start.sh`.
   - `Maven settings file not found`:
     - `MAVEN_SETTINGS_FILE` path is wrong; use absolute path like `/home/<you>/.m2/settings.xml`.
   - `connect timed out`:
     - Proxy not configured or mirror not reachable from your VLAN/VPN.

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
3. **Rule Engine** — `RuleService` applies statement-level `SqlChecker` rules and script-level `SqlScriptChecker` rules, filtered by rule scope.
4. **Reporting** — Returns a `ScanReport` with categorized `Violation` records.

### 📏 Built-in Rules (OpenGauss)

| Scope | Count | Coverage |
|---|---:|---|
| Shared rules | 21 | Schema prefix, WHERE, SELECT, UPDATE, DELETE, JOIN, subquery, MyBatis injection, keyword uppercase |
| SQL script only | 20 | Object naming, database encoding/locale, PK/comment requirements, foreign key / trigger / function / procedure bans, index column count, `ustore` / column-store rules |

For the exact default rule list, see:

- `skills/sql-audit-scan/references/default-rules.md`
- `backend/src/main/java/com/sqlaudit/service/RuleService.java`

### ❓ Troubleshooting

- **Port 8081 in use?** — Run `./start.sh` (auto-kills), or `lsof -ti:8081 | xargs kill -9`.
- **Frontend can't connect?** — Verify backend at `http://localhost:8081/api/rules`.
- **"invalid source release: 21"?** — Ensure JDK 21 is installed and `JAVA_HOME` is set.

---

<a id="中文"></a>

## 中文

一款强大的自动化 SQL 合规扫描工具，支持 **MyBatis XML Mapper** 文件和**独立 SQL 脚本**的静态分析。帮助开发者严格遵循 **OpenGauss SQL 开发规范**，配备现代化高级 UI。

### 🚀 核心功能

- **41 条内置 OpenGauss 规则** — `21` 条共享规则覆盖 MyBatis 与 `.sql` 两种模式，另有 `20` 条 SQL 脚本专用 DDL / 对象设计规则。
- **双模式扫描** — 扫描 MyBatis XML Mapper 目录 **或** 直接上传 `.sql` 变更脚本。
- **MyBatis 安全检测** — 识别 `${}` 拼接的 SQL 注入风险。
- **DDL / 脚本治理能力** — 覆盖对象命名、数据库字符集/Locale、主键/字段注释、禁用触发器/函数/存储过程等 OpenGauss 规范。
- **CLI + Skill 可调用** — 后端支持 fat jar CLI，本仓库内置 `sql-audit-scan` 本地 skill 包装。
- **自定义规则** — 上传 `.docx` Word 规范文档，动态加载自定义正则规则。
- **现代化 UI** — 深色主题 + 玻璃拟态设计，工作流程左右分栏布局，动态渐变动画，完全响应式。
- **可视化报告** — 交互式仪表盘，按严重程度（错误 / 警告 / 提示）和分类筛选违规项。

### 🛠 技术栈

| 层级 | 技术 |
|---|---|
| **后端** | Java 21、Spring Boot 3.4.2 |
| **前端** | Vite 6、原生 JS（ES Modules）、CSS3 玻璃拟态 |
| **解析** | DOM MyBatis 解析、SQL 脚本切分、Word 规则提取 |
| **规则引擎** | 内置 `SqlChecker` + 脚本级 `SqlScriptChecker` |
| **构建** | Maven、Shell（`start.sh`） |

### 🏁 快速开始

#### 环境要求

- **Java 21** 或更高版本
- **Node.js 18** 或更高版本
- **Maven 3.8+**

#### 一键启动

```bash
bash ./start.sh
```

脚本会自动：
1. 终止 8081 和 5174 端口的已有进程。
2. 检查 Java / Maven / Node.js 版本（要求 Java 21+、Node 18+）。
3. 首次运行自动安装前端依赖（`npm install`）。
4. 编译并启动 Spring Boot 后端。
5. 启动 Vite 前端开发服务器。

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

#### CLI 模式

后端支持无 Web 的本地 CLI 调用，适合脚本化或被 agent 直接调用：

```bash
cd backend
mvn -q -DskipTests package
java -jar target/sql-audit-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --sql-audit.cli.enabled=true \
  --sql-audit.cli.sql-file=/绝对路径/change.sql \
  --sql-audit.cli.json-out=/tmp/sql-audit-report.json \
  --sql-audit.cli.markdown-out=/tmp/sql-audit-report.md
```

如果要扫 MyBatis 仓库目录，把 `--sql-audit.cli.sql-file=...` 替换为：

```bash
--sql-audit.cli.repo-path=/绝对路径/mybatis-project
```

#### Skill 使用方式

先安装一次本地 skill 软链接：

```bash
./scripts/install-skill.sh
```

skill 包装脚本会在需要时自动构建后端 jar，调用 CLI，并输出一份精简 JSON 摘要，包含统计信息和报告路径：

```bash
./skills/sql-audit-scan/scripts/invoke_scan.sh /绝对路径/mybatis-project
./skills/sql-audit-scan/scripts/invoke_scan.sh /绝对路径/change.sql
printf 'SELECT * FROM demo_user;' | ./skills/sql-audit-scan/scripts/invoke_scan.sh
```

安装目标：

```text
~/.codex/skills/sql-audit-scan
```

#### WSL（Windows Subsystem for Linux）运行说明

- 推荐使用 `bash ./start.sh` 启动（仓库放在 `/mnt/c` 时比 `./start.sh` 更稳，避免执行权限问题）。
- 在 WSL 中请使用 **Linux 原生 Node/npm**，不要使用 Windows 的 `node.exe` / `npm.cmd`（常见后果是依赖安装失败或 `esbuild` 平台不匹配）。
- WSL 内快速安装 Node（Ubuntu/Debian）：
  ```bash
  sudo apt update
  sudo apt install -y curl
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
  source ~/.nvm/nvm.sh
  nvm install --lts
  node -v && npm -v
  ```
- 页面里的“仓库路径”支持：
  - Linux 路径：`/home/you/project`
  - WSL 挂载路径：`/mnt/c/Users/you/project`
  - Windows 路径：`C:\Users\you\project`（后端会在 WSL 下自动转换为 `/mnt/c/...`）
- 若追求性能，建议把项目和被扫描仓库放在 `/home/...`，避免 `/mnt/c/...` 的 I/O 开销。
- 本项目在 WSL 下会自动启用 Vite 轮询监听，降低前端热更新不触发的问题。

#### WSL 企业内网环境配置（Maven + npm，详细版）

如果你的公司电脑只能访问内网镜像，请在 WSL 中一次性完成以下配置。

1. 先向公司平台/网络同学拿到这几项信息
   - Maven 内网镜像地址（示例：`https://maven.company.local/repository/maven-public/`）
   - Maven 账号/Token
   - npm 内网镜像地址（示例：`https://npm.company.local/repository/npm-group/`）
   - npm Token/账号
   - 公司根证书/中间证书（`.crt`）
   - （可选）代理地址（示例：`http://proxy.company.local:8080`）

2. 安装 WSL 基础工具
   ```bash
   sudo apt update
   sudo apt install -y ca-certificates curl unzip jq
   ```

3. 在 WSL 内安装 Java 21
   ```bash
   sudo apt install -y openjdk-21-jdk maven
   java -version
   mvn -v
   ```
   预期：Java 主版本号为 `21` 或更高。

4. 在 WSL 内安装 Linux 原生 Node.js（推荐 nvm）
   ```bash
   curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
   source ~/.nvm/nvm.sh
   nvm install --lts
   node -v
   npm -v
   ```
   关键检查：`which node`、`which npm` 必须是 Linux 路径（`/usr/...` 或 `~/.nvm/...`），不能是 `/mnt/c/...`。

5. 导入公司证书（很多内网环境不做这步会 TLS 失败）
   ```bash
   sudo cp /path/to/company-root-ca.crt /usr/local/share/ca-certificates/company-root-ca.crt
   sudo update-ca-certificates
   ```
   如果证书放在 Windows 下载目录，常见路径是：
   `/mnt/c/Users/<你的用户名>/Downloads/company-root-ca.crt`

6. 配置 Maven 内网镜像
   - 创建目录并复制模板：
   ```bash
   mkdir -p ~/.m2
   cp docs/templates/maven-settings-corp.xml ~/.m2/settings.xml
   ```
   - 编辑 `~/.m2/settings.xml`：
     - 把模板里的 `https://maven.company.local/...` 改成真实地址
     - `<mirror><id>corp-mirror</id></mirror>` 和 `<server><id>corp-mirror</id></server>` 必须一致
     - 建议使用环境变量注入凭据（模板已预留）
   - 设置凭据（不要提交到 Git）：
   ```bash
   export CORP_MAVEN_USER='你的账号'
   export CORP_MAVEN_PASS='你的密码或token'
   ```
   - 验证：
   ```bash
   mvn -s ~/.m2/settings.xml -q help:effective-settings
   ```

7. 配置 npm 内网镜像
   - 复制模板：
   ```bash
   cp docs/templates/npmrc-corp ~/.npmrc
   ```
   - 编辑 `~/.npmrc`：
     - 将 `npm.company.local/...` 替换为公司真实 npm 镜像地址
   - 设置 token：
   ```bash
   export NPM_TOKEN='你的npm token'
   ```
   - 验证：
   ```bash
   npm config get registry
   npm view vite version
   ```

8. （可选）如果公司网络要求走代理
   ```bash
   npm config set proxy http://proxy.company.local:8080
   npm config set https-proxy http://proxy.company.local:8080
   export MAVEN_OPTS="-Dhttp.proxyHost=proxy.company.local -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy.company.local -Dhttps.proxyPort=8080"
   ```

9. 把凭据变量写入 shell 配置，避免每次手动 export
   在 `~/.bashrc` 或 `~/.zshrc` 增加：
   ```bash
   export CORP_MAVEN_USER='你的账号'
   export CORP_MAVEN_PASS='你的密码或token'
   export NPM_TOKEN='你的npm token'
   ```
   然后执行：
   ```bash
   source ~/.bashrc
   ```

10. 使用内网配置启动项目
    ```bash
    cd /path/to/sql-audit
    MAVEN_SETTINGS_FILE="$HOME/.m2/settings.xml" bash ./start.sh
    ```
    说明：
    - `start.sh` 已支持 `MAVEN_SETTINGS_FILE`（或 `MAVEN_SETTINGS`），可强制 Maven 使用你的内网配置。
    - 在 WSL 下，脚本会主动拒绝 Windows 的 `node.exe`/`npm.cmd`，防止跨平台依赖损坏。

11. 一键排查命令（出问题直接贴这些输出）
    ```bash
    which java && java -version
    which mvn && mvn -v
    which node && node -v
    which npm && npm -v
    npm config get registry
    mvn -s ~/.m2/settings.xml help:effective-settings -Doutput=/tmp/effective-settings.xml
    tail -n 100 backend/backend.log
    tail -n 100 frontend/frontend.log
    ```

12. 常见报错与处理
    - `PKIX path building failed`：
      - 基本都是公司证书没导入成功，回到第 5 步重做。
    - `401 Unauthorized`（Maven 或 npm）：
      - 凭据错误，或镜像 URL 路径不对。
    - `esbuild` 平台不匹配 / `Cannot find module ...`：
      - 之前在 WSL 里误用了 Windows Node/npm；删除 `frontend/node_modules` 后重新 `bash ./start.sh`。
    - `Maven settings file not found`：
      - `MAVEN_SETTINGS_FILE` 路径写错，请使用绝对路径（如 `/home/<you>/.m2/settings.xml`）。
    - `connect timed out`：
      - 代理未配置，或当前网络/VPN 无法访问内网镜像。

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
3. **规则引擎** — `RuleService` 会按规则作用域执行逐语句 `SqlChecker` 和脚本级 `SqlScriptChecker`。
4. **报告生成** — 返回 `ScanReport`，包含分类的 `Violation` 记录。

### 📏 内置规则（OpenGauss 规范）

| 作用域 | 数量 | 覆盖范围 |
|---|---:|---|
| 共享规则 | 21 | Schema 前缀、WHERE、SELECT、UPDATE、DELETE、JOIN、子查询、MyBatis 注入、关键字大写 |
| SQL 脚本专用 | 20 | 对象命名、数据库字符集/Locale、主键/字段注释要求、外键/触发器/函数/存储过程禁用、索引列数、`ustore` / 列存规则 |

精确规则清单见：

- `skills/sql-audit-scan/references/default-rules.md`
- `backend/src/main/java/com/sqlaudit/service/RuleService.java`

### ❓ 常见问题

- **端口 8081 被占用？** — 运行 `./start.sh`（自动终止），或 `lsof -ti:8081 | xargs kill -9`。
- **前端无法连接后端？** — 确认后端已启动：`http://localhost:8081/api/rules`。
- **编译报 "invalid source release: 21"？** — 确保已安装 JDK 21 并正确设置 `JAVA_HOME`。

---

*Built with ❤️ by Antigravity Agent*
