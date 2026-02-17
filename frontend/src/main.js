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
    }
};

// ============================================
// State
// ============================================
let state = {
    rules: [],
    scanReport: null,
    scanning: false,
    filter: 'ALL' // ALL, ERROR, WARNING, INFO
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
// Render
// ============================================
function render() {
    const app = document.getElementById('app');
    app.innerHTML = `
        ${renderHeader()}
        <div class="grid-2">
            ${renderRulesCard()}
            ${renderScanCard()}
        </div>
        ${state.scanReport ? renderResults() : ''}
    `;
    bindEvents();
}

function renderHeader() {
    return `
        <header class="app-header">
            <h1 class="app-logo">🛡️ SQL Audit</h1>
            <p class="app-subtitle">OpenGauss SQL 开发规范审查 — 基于 MyBatis XML 的静态分析工具</p>
        </header>
    `;
}

function renderRulesCard() {
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
        <div class="card">
            <div class="card-header">
                <h2 class="card-title"><span class="icon">📋</span> OpenGauss 审查规则</h2>
                <span class="card-badge">${state.rules.length} 条规则</span>
            </div>

            <!-- 上传区域 -->
            <div class="upload-zone" id="uploadZone">
                <span class="icon">📄</span>
                <div class="upload-text">上传 Word 审查规范文档</div>
                <div class="upload-hint">拖拽 .docx 文件到此处，或点击选择文件</div>
                <input type="file" id="ruleFileInput" accept=".docx" />
            </div>

            ${customRules.length > 0 ? `
                <div style="margin-top: var(--space-md); display: flex; justify-content: space-between; align-items: center;">
                    <span style="font-size: 0.85rem; color: var(--text-secondary);">已加载 ${customRules.length} 条自定义规则</span>
                    <button class="btn btn-danger btn-sm" id="clearCustomRules">清除自定义</button>
                </div>
            ` : ''}

            <!-- 按分类分组的规则列表 -->
            <div class="rules-grouped" style="margin-top: var(--space-md);">
                ${Object.entries(categories).map(([cat, rules]) => `
                    <div class="rule-category">
                        <div class="rule-category-header">§ ${cat}</div>
                        <ul class="rules-list">
                            ${rules.map(rule => `
                                <li class="rule-item">
                                    <span class="rule-section">${rule.section || ''}</span>
                                    <span class="rule-severity severity-${rule.severity}">${rule.severity}</span>
                                    <span class="rule-name">${rule.name}</span>
                                </li>
                            `).join('')}
                        </ul>
                    </div>
                `).join('')}

                ${customRules.length > 0 ? `
                    <div class="rule-category">
                        <div class="rule-category-header">📄 自定义规则</div>
                        <ul class="rules-list">
                            ${customRules.map(rule => `
                                <li class="rule-item">
                                    <span class="rule-severity severity-${rule.severity}">${rule.severity}</span>
                                    <span class="rule-name">${rule.name}</span>
                                    <span class="rule-desc">${rule.description}</span>
                                </li>
                            `).join('')}
                        </ul>
                    </div>
                ` : ''}
            </div>
        </div>
    `;
}

function renderScanCard() {
    return `
        <div class="card">
            <div class="card-header">
                <h2 class="card-title"><span class="icon">🔍</span> 扫描配置</h2>
            </div>
            <div class="form-group">
                <label class="form-label">代码仓库路径</label>
                <input type="text" class="form-input" id="repoPath"
                       placeholder="/Users/xxx/your-java-project"
                       value="${state.lastRepoPath || ''}" />
            </div>
            <div style="display: flex; gap: var(--space-sm); align-items: center;">
                <button class="btn btn-primary" id="scanBtn" ${state.scanning ? 'disabled' : ''}>
                    ${state.scanning
            ? '<span class="loading-spinner"><span class="spinner"></span> 扫描中...</span>'
            : '🚀 开始扫描'}
                </button>
            </div>

            ${!state.scanReport && !state.scanning ? `
                <div class="empty-state">
                    <div class="icon">📂</div>
                    <p>输入 Java 项目路径，点击扫描<br/>将检查所有 MyBatis XML 文件的 SQL 合规性</p>
                </div>
            ` : ''}
        </div>
    `;
}

function renderResults() {
    const report = state.scanReport;

    // 按文件分组违规
    const grouped = {};
    report.violations.forEach(v => {
        const path = v.sqlFragment.relativePath;
        if (!grouped[path]) grouped[path] = [];
        grouped[path].push(v);
    });

    // 过滤
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
            <div class="card">
                <div class="card-header">
                    <h2 class="card-title"><span class="icon">📊</span> 扫描结果</h2>
                    <span style="font-size: 0.8rem; color: var(--text-muted);">${report.scanTime}</span>
                </div>

                <!-- 统计 -->
                <div class="stats-bar">
                    <div class="stat-card">
                        <div class="stat-value" style="color: var(--text-accent);">${report.totalFiles}</div>
                        <div class="stat-label">扫描文件数</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value" style="color: var(--text-primary);">${report.totalStatements}</div>
                        <div class="stat-label">SQL 语句数</div>
                    </div>
                    <div class="stat-card stat-error">
                        <div class="stat-value">${report.errorCount}</div>
                        <div class="stat-label">❌ 错误</div>
                    </div>
                    <div class="stat-card stat-warning">
                        <div class="stat-value">${report.warningCount}</div>
                        <div class="stat-label">⚠️ 警告</div>
                    </div>
                </div>

                ${report.totalViolations === 0 ? renderPassResult(report) : renderViolations(filteredGrouped, report)}

                <!-- 扫描的文件列表 -->
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

function renderViolations(grouped, report) {
    return `
        <!-- 过滤器 -->
        <div class="filter-bar">
            <button class="filter-btn ${state.filter === 'ALL' ? 'active' : ''}" data-filter="ALL">
                全部 (${report.totalViolations})
            </button>
            <button class="filter-btn ${state.filter === 'ERROR' ? 'active' : ''}" data-filter="ERROR">
                ❌ 错误 (${report.errorCount})
            </button>
            <button class="filter-btn ${state.filter === 'WARNING' ? 'active' : ''}" data-filter="WARNING">
                ⚠️ 警告 (${report.warningCount})
            </button>
            ${report.infoCount > 0 ? `
                <button class="filter-btn ${state.filter === 'INFO' ? 'active' : ''}" data-filter="INFO">
                    ℹ️ 提示 (${report.infoCount})
                </button>
            ` : ''}
        </div>

        <!-- 按文件分组展示 -->
        ${Object.entries(grouped).map(([path, violations]) => `
            <div class="violation-group">
                <div class="violation-file-header">
                    <span>📄</span>
                    <span class="violation-file-path">${path}</span>
                    <span class="violation-file-count">${violations.length} 项</span>
                </div>
                ${violations.map(v => `
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
                `).join('')}
            </div>
        `).join('')}
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

    // File upload
    const uploadZone = document.getElementById('uploadZone');
    const fileInput = document.getElementById('ruleFileInput');
    if (uploadZone) {
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
    }
    if (fileInput) {
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

    // Filter buttons
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            state.filter = btn.dataset.filter;
            render();
        });
    });
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
