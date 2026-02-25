import './style.css';

// ============================================
// API Client
// ============================================
const api = {
    async scan(repoPath) {
        const res = await fetch('/api/scan', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ repoPath })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || '扫描失败');
        return data;
    },

    async uploadRules(file) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch('/api/rules/upload', {
            method: 'POST',
            body: formData
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || '上传失败');
        return data;
    },

    async getRules() {
        const res = await fetch('/api/rules');
        return res.json();
    },

    async getDefaultRules() {
        const res = await fetch('/api/rules/default');
        return res.json();
    },

    async clearCustomRules() {
        const res = await fetch('/api/rules/custom', { method: 'DELETE' });
        return res.json();
    },

    async scanSql(file) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch('/api/scan/sql', {
            method: 'POST',
            body: formData
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'SQL 脚本审查失败');
        return data;
    }
};

// ============================================
// State
// ============================================
let state = {
    rules: [],
    scanReport: null,
    scanning: false,
    filter: 'ALL',
    rulesExpanded: false,
    currentPage: 1,
    itemsPerPage: 50
};

// ============================================
// Toast Notifications
// ============================================
function showToast(message, type = 'success') {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(40px)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// ============================================
// Render — New Layout
// ============================================
function render() {
    const app = document.getElementById('app');
    app.innerHTML = `
        ${renderHeader()}
        <div class="main-columns section-gap">
            <div class="col-left">
                <div class="workflow-label"><span class="workflow-step">1</span> 规则配置</div>
                ${renderRulesConfig()}
                ${renderRulesSection()}
            </div>
            <div class="col-right">
                <div class="workflow-label"><span class="workflow-step">2</span> 代码审查</div>
                ${renderScanSection()}
            </div>
        </div>
        ${state.scanReport ? renderResults() : ''}
    `;
    bindEvents();
}

function renderHeader() {
    return `
        <header class="app-header">
            <h1 class="app-logo">⚡ Ultimate-SQL Audit</h1>
            <p class="app-subtitle">OpenGauss SQL 合规审查 · MyBatis XML 扫描 & SQL 脚本上传</p>
        </header>
    `;
}

function renderRulesConfig() {
    return `
            <div class="rules-config-card glass">
                <div class="rules-config-inner">
                    <div class="rules-config-left">
                        <span class="card-icon">📄</span>
                        <div>
                            <div class="card-label">自定义规则</div>
                            <div class="card-desc">上传 Word 规范文档定义自定义审查规则</div>
                        </div>
                    </div>
                    <div class="rules-config-right">
                        <div class="word-upload-area compact" id="uploadZone">
                            <span class="upload-icon">📎</span>
                            <div class="upload-info">
                                <div class="text">拖拽 .docx 或点击上传</div>
                            </div>
                            <input type="file" id="ruleFileInput" accept=".docx" />
                        </div>
                    </div>
                </div>
                <div class="example-toggle" id="toggleFormatExample">💡 查看规范文档格式示例</div>
                <div class="format-example hidden" id="formatExamplePanel">
                    <div class="format-example-header">推荐格式：表格</div>
                    <div class="format-example-table">
                        <table>
                            <thead><tr><th>编号</th><th>规则描述</th><th>等级</th></tr></thead>
                            <tbody>
                                <tr><td>R001</td><td>禁止使用 SELECT * 查询</td><td>错误</td></tr>
                                <tr><td>R002</td><td>UPDATE/DELETE 必须包含 WHERE</td><td>错误</td></tr>
                                <tr><td>R003</td><td>建议使用 UNION ALL 代替 UNION</td><td>警告</td></tr>
                                <tr><td>R004</td><td>禁止使用 \${} 拼接，防止注入</td><td>错误</td></tr>
                            </tbody>
                        </table>
                    </div>
                    <div class="format-example-header" style="margin-top: var(--s-md);">段落/列表格式</div>
                    <div class="format-example-text">
                        <code>1. 禁止使用 SELECT * 查询所有字段</code><br/>
                        <code>2. UPDATE 和 DELETE 语句必须包含 WHERE 子句</code><br/>
                        <code>3. 建议使用 #{} 参数绑定，禁止 \${} 拼接</code><br/>
                        <code>• 查询建议添加 LIMIT 限制</code>
                    </div>
                    <div class="format-example-header" style="margin-top: var(--s-md);">触发关键词</div>
                    <div class="format-example-keywords">
                        <span class="keyword-tag">禁止</span>
                        <span class="keyword-tag">必须</span>
                        <span class="keyword-tag">建议</span>
                        <span class="keyword-tag">不允许</span>
                        <span class="keyword-tag">不得</span>
                        <span class="keyword-tag">SELECT</span>
                        <span class="keyword-tag">WHERE</span>
                        <span class="keyword-tag">索引</span>
                        <span class="keyword-tag">注入</span>
                    </div>
                    <div class="format-example-header" style="margin-top: var(--s-md);">等级识别</div>
                    <div class="format-example-keywords">
                        <span class="keyword-tag severity-ERROR">错误 / ERROR</span>
                        <span class="keyword-tag severity-WARNING">警告 / WARNING</span>
                        <span class="keyword-tag severity-INFO">提示 / INFO</span>
                    </div>
                </div>
            </div>
    `;
}

function renderScanSection() {
    return `
            <div class="scan-methods">
                <div class="scan-method-card glass">
                    <div class="scan-method-header">
                        <span class="card-icon">🔍</span>
                        <div>
                            <div class="card-label">MyBatis XML 扫描</div>
                            <div class="card-desc">输入 Java 项目路径，扫描所有 MyBatis Mapper XML</div>
                        </div>
                    </div>
                    <div class="scan-input-wrapper">
                        <input type="text" class="scan-input" id="repoPath"
                               placeholder="输入 Java 项目路径..."
                               value="${state.lastRepoPath || ''}" />
                        <button class="scan-btn" id="scanBtn" ${state.scanning ? 'disabled' : ''}>
                            ${state.scanning
            ? '<span class="loading-spinner"><span class="spinner"></span> 扫描中</span>'
            : '🚀 扫描'}
                        </button>
                    </div>
                </div>
                <div class="scan-method-card glass">
                    <div class="scan-method-header">
                        <span class="card-icon">📝</span>
                        <div>
                            <div class="card-label">SQL 脚本审查</div>
                            <div class="card-desc">上传 .sql 变更脚本直接进行合规检查</div>
                        </div>
                    </div>
                    <div class="sql-drop-area" id="sqlUploadZone">
                        <div class="drop-text">📎 拖拽 .sql 文件或点击上传</div>
                        <input type="file" id="sqlFileInput" accept=".sql" />
                    </div>
                </div>
            </div>
    `;
}

function renderRulesSection() {
    const defaultRules = state.rules.filter(r => r.source === 'DEFAULT');
    const customRules = state.rules.filter(r => r.source === 'CUSTOM');

    // Group by category
    const categories = {};
    defaultRules.forEach(r => {
        const cat = r.category || '其他';
        if (!categories[cat]) categories[cat] = [];
        categories[cat].push(r);
    });

    return `
        <div class="rules-section glass section-gap">
            <div class="section-header">
                <span class="section-title">审查规则</span>
                <span class="card-badge">${state.rules.length} 条</span>
            </div>

            ${customRules.length > 0 ? `
                <div class="custom-rules-bar">
                    <span>✨ 已加载 ${customRules.length} 条自定义规则</span>
                    <button class="btn btn-sm btn-danger" id="clearCustomRules">清除</button>
                </div>
            ` : ''}

            ${Object.entries(categories).map(([cat, rules]) => `
                <div class="rules-category-group">
                    <div class="rules-category-label">§ ${cat}</div>
                    <div class="rules-chips">
                        ${rules.map(rule => `
                            <span class="rule-chip" title="${rule.description || rule.name}">
                                <span class="dot dot-${rule.severity}"></span>
                                ${rule.section ? `<span class="section-num">${rule.section}</span>` : ''}
                                ${rule.name}
                            </span>
                        `).join('')}
                    </div>
                </div>
            `).join('')}

            ${customRules.length > 0 ? `
                <div class="rules-category-group">
                    <div class="rules-category-label">📄 自定义规则</div>
                    <div class="rules-chips">
                        ${customRules.map(rule => `
                            <span class="rule-chip" title="${rule.description || rule.name}">
                                <span class="dot dot-${rule.severity}"></span>
                                ${rule.name}
                            </span>
                        `).join('')}
                    </div>
                </div>
            ` : ''}
        </div>
    `;
}

function renderResults() {
    const report = state.scanReport;

    // Group violations by file
    const grouped = {};
    report.violations.forEach(v => {
        const path = v.sqlFragment.relativePath;
        if (!grouped[path]) grouped[path] = [];
        grouped[path].push(v);
    });

    // Filter
    const filteredGrouped = {};
    for (const [path, violations] of Object.entries(grouped)) {
        const filtered = state.filter === 'ALL'
            ? violations
            : violations.filter(v => v.rule.severity === state.filter);
        if (filtered.length > 0) {
            filteredGrouped[path] = filtered;
        }
    }

    return `
        <div class="scan-results">
            <div class="glass">
                <div class="results-header">
                    <div class="results-title">📊 审查结果</div>
                    <div class="results-meta">
                        <span class="results-time">${report.scanTime || ''}</span>
                        <button class="btn btn-sm btn-ghost" id="clearResultsBtn">🗑️ 清除</button>
                    </div>
                </div>

                ${report.limitReached ? `
                <div style="background: rgba(255, 171, 0, 0.1); border: 1px solid rgba(255, 171, 0, 0.3); color: #b77900; padding: 12px; border-radius: 8px; margin-bottom: 20px; font-size: 14px; display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 18px;">⚠️</span>
                    <div>
                        <strong>扫描结果被截断</strong><br/>
                        检测到极多违规项，为保证系统性能，仅为您展示前 1000 条。建议缩小扫描范围或优化当前规则集。
                    </div>
                </div>
                ` : ''}

                <!-- Stats -->
                <div class="stats-grid">
                    <div class="stat-item">
                        <div class="stat-value cyan">${report.totalFiles}</div>
                        <div class="stat-label">扫描文件</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value purple">${report.totalStatements}</div>
                        <div class="stat-label">SQL 语句</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value error">${report.errorCount}</div>
                        <div class="stat-label">❌ 错误</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value warning">${report.warningCount}</div>
                        <div class="stat-label">⚠️ 警告</div>
                    </div>
                </div>

                ${report.totalViolations === 0 ? renderPassResult(report) : renderViolations(filteredGrouped, report)}

                <!-- Scanned files -->
                <div class="scanned-files-toggle" id="toggleScannedFiles">
                    ▸ 查看已扫描的 ${report.scannedFiles.length} 个文件
                </div>
                <ul class="scanned-files-list hidden" id="scannedFilesList">
                    ${report.scannedFiles.map(f => `<li>📄 ${f}</li>`).join('')}
                </ul>
            </div>
        </div>
    `;
}

function renderPassResult(report) {
    return `
        <div class="pass-result">
            <div class="pass-icon">✅</div>
            <div class="pass-title">恭喜！所有 SQL 语句均符合规范</div>
            <div class="pass-desc">共扫描 ${report.totalFiles} 个文件，${report.totalStatements} 条 SQL 语句，未发现违规项</div>
        </div>
    `;
}

window.loadMoreViolations = function () {
    state.currentPage++;
    render();
};

function renderViolations(grouped, report) {
    // 拍平以实现分页
    const flattened = [];
    for (const [path, violations] of Object.entries(grouped)) {
        violations.forEach(v => flattened.push({ path, v }));
    }

    const totalFiltered = flattened.length;
    const limit = state.currentPage * state.itemsPerPage;
    const paginated = flattened.slice(0, limit);
    const hasMore = limit < totalFiltered;

    // 重新分组
    const paginatedGrouped = {};
    paginated.forEach(item => {
        if (!paginatedGrouped[item.path]) paginatedGrouped[item.path] = [];
        paginatedGrouped[item.path].push(item.v);
    });

    return `
        <!-- Filters -->
        <div class="filter-bar">
            <button class="filter-pill ${state.filter === 'ALL' ? 'active' : ''}" data-filter="ALL">
                全部 (${report.totalViolations})
            </button>
            <button class="filter-pill ${state.filter === 'ERROR' ? 'active' : ''}" data-filter="ERROR">
                ❌ 错误 (${report.errorCount})
            </button>
            <button class="filter-pill ${state.filter === 'WARNING' ? 'active' : ''}" data-filter="WARNING">
                ⚠️ 警告 (${report.warningCount})
            </button>
            ${report.infoCount > 0 ? `
                <button class="filter-pill ${state.filter === 'INFO' ? 'active' : ''}" data-filter="INFO">
                    ℹ️ 提示 (${report.infoCount})
                </button>
            ` : ''}
        </div>

        <!-- Violations by file -->
        ${Object.entries(paginatedGrouped).map(([path, violations]) => `
            <div class="violation-group">
                <div class="violation-file-header">
                    <span>📄</span>
                    <span class="violation-file-path">${path}</span>
                    <span class="violation-file-count">${violations.length} 项</span>
                </div>
                ${violations.map(v => renderViolationItem(v)).join('')}
            </div>
        `).join('')}

        ${hasMore ? `
            <div style="text-align: center; margin: 20px 0;">
                <button class="btn btn-secondary" onclick="loadMoreViolations()">
                    展示更多 (已展示 ${limit} / ${totalFiltered})
                </button>
            </div>
        ` : ''}
    `;
}

function renderViolationItem(v) {
    return `
        <div class="violation-item severity-border-${v.rule.severity}">
            <div class="violation-meta">
                <span class="rule-severity severity-${v.rule.severity}">${v.rule.severity}</span>
                ${v.rule.section ? `<span class="rule-section">§${v.rule.section}</span>` : ''}
                <span class="violation-rule-name">${v.rule.name}</span>
                <span class="violation-statement-id">${v.sqlFragment.statementType.toUpperCase()} #${v.sqlFragment.statementId}</span>
                <span class="violation-line">行 ${v.sqlFragment.lineNumber}</span>
            </div>
            <div class="violation-message">${v.message}</div>
            ${v.matchedText ? `<code class="violation-matched">${escapeHtml(v.matchedText)}</code>` : ''}
        </div>
    `;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============================================
// Event Binding
// ============================================
function bindEvents() {
    // Scan button
    const scanBtn = document.getElementById('scanBtn');
    if (scanBtn) {
        scanBtn.addEventListener('click', handleScan);
    }

    // Enter key on input
    const repoInput = document.getElementById('repoPath');
    if (repoInput) {
        repoInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') handleScan();
        });
    }

    // Word file upload
    const uploadZone = document.getElementById('uploadZone');
    const fileInput = document.getElementById('ruleFileInput');
    if (uploadZone && fileInput) {
        uploadZone.addEventListener('click', (e) => {
            if (e.target !== fileInput) fileInput.click();
        });
        uploadZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadZone.classList.add('dragover');
        });
        uploadZone.addEventListener('dragleave', () => {
            uploadZone.classList.remove('dragover');
        });
        uploadZone.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadZone.classList.remove('dragover');
            const file = e.dataTransfer.files[0];
            if (file) handleFileUpload(file);
        });
        fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) handleFileUpload(file);
        });
    }

    // Clear custom rules
    const clearBtn = document.getElementById('clearCustomRules');
    if (clearBtn) {
        clearBtn.addEventListener('click', handleClearCustomRules);
    }

    // Toggle format example
    const toggleExample = document.getElementById('toggleFormatExample');
    if (toggleExample) {
        toggleExample.addEventListener('click', () => {
            const panel = document.getElementById('formatExamplePanel');
            panel.classList.toggle('hidden');
            toggleExample.textContent = panel.classList.contains('hidden')
                ? '💡 查看规范文档格式示例'
                : '💡 收起格式示例';
        });
    }

    // Toggle scanned files
    const toggleFiles = document.getElementById('toggleScannedFiles');
    if (toggleFiles) {
        toggleFiles.addEventListener('click', () => {
            const list = document.getElementById('scannedFilesList');
            list.classList.toggle('hidden');
            toggleFiles.textContent = list.classList.contains('hidden')
                ? `▸ 查看已扫描的 ${state.scanReport.scannedFiles.length} 个文件`
                : `▾ 隐藏文件列表`;
        });
    }

    // Filter pills
    document.querySelectorAll('.filter-pill').forEach(btn => {
        btn.addEventListener('click', () => {
            state.filter = btn.dataset.filter;
            render();
        });
    });

    // Clear Results button
    const clearResultsBtn = document.getElementById('clearResultsBtn');
    if (clearResultsBtn) {
        clearResultsBtn.addEventListener('click', handleClearResults);
    }

    // SQL file upload
    const sqlUploadZone = document.getElementById('sqlUploadZone');
    const sqlFileInput = document.getElementById('sqlFileInput');
    if (sqlUploadZone && sqlFileInput) {
        sqlUploadZone.addEventListener('click', (e) => {
            if (e.target !== sqlFileInput) sqlFileInput.click();
        });
        sqlUploadZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            sqlUploadZone.classList.add('dragover');
        });
        sqlUploadZone.addEventListener('dragleave', () => {
            sqlUploadZone.classList.remove('dragover');
        });
        sqlUploadZone.addEventListener('drop', (e) => {
            e.preventDefault();
            sqlUploadZone.classList.remove('dragover');
            const file = e.dataTransfer.files[0];
            if (file) handleSqlFileScan(file);
        });
        sqlFileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) handleSqlFileScan(file);
        });
    }
}

// ============================================
// Handlers
// ============================================
async function handleScan() {
    const input = document.getElementById('repoPath');
    const repoPath = input?.value?.trim();
    if (!repoPath) {
        showToast('请输入代码仓库路径', 'error');
        return;
    }

    state.scanning = true;
    state.scanReport = null;
    state.lastRepoPath = repoPath;
    state.filter = 'ALL';
    render();

    try {
        const report = await api.scan(repoPath);
        state.scanReport = report;
        showToast(`扫描完成：${report.totalFiles} 个文件，${report.totalViolations} 条违规`);
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        state.scanning = false;
        render();
    }
}

async function handleFileUpload(file) {
    if (file.size > 10 * 1024 * 1024) {
        showToast('文件大小不能超过 10MB', 'error');
        return;
    }
    if (!file.name.toLowerCase().endsWith('.docx')) {
        showToast('请上传 .docx 格式的 Word 文档', 'error');
        return;
    }

    try {
        const result = await api.uploadRules(file);
        showToast(result.message);
        await loadRules();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function handleClearCustomRules() {
    try {
        await api.clearCustomRules();
        showToast('已清除自定义规则');
        await loadRules();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function handleClearResults() {
    state.scanReport = null;
    state.currentPage = 1;
    render();
    showToast('扫描结果已清除');
}

async function handleSqlFileScan(file) {
    if (file.size > 10 * 1024 * 1024) {
        showToast('文件大小不能超过 10MB', 'error');
        return;
    }
    if (!file.name.toLowerCase().endsWith('.sql')) {
        showToast('请上传 .sql 格式的 SQL 脚本文件', 'error');
        return;
    }

    state.scanning = true;
    state.scanReport = null;
    state.filter = 'ALL';
    state.currentPage = 1;
    render();

    try {
        const report = await api.scanSql(file);
        state.scanReport = report;
        showToast(`SQL 脚本审查完成：${report.totalStatements} 条语句，${report.totalViolations} 条违规`);
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        state.scanning = false;
        render();
    }
}

async function loadRules() {
    try {
        state.rules = await api.getRules();
    } catch (err) {
        console.error('加载规则失败', err);
    }
    render();
}

// ============================================
// Initialize
// ============================================
loadRules();
