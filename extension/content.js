// AI Job Pilot  - Bulletproof Content Script
console.log("🚀 AI Job Pilot  Active");

// 1. CSP-Safe Detection
document.documentElement.setAttribute('data-jobc-copilot-active', 'true');

// 2. Safety Helper to check if extension context is still valid
function isContextValid() {
    return typeof chrome !== 'undefined' && chrome.runtime && !!chrome.runtime.id;
}

// 3. Listen for messages from the Dashboard page (Context-Safe)
window.addEventListener('message', (event) => {
    if (event.data.type === 'SET_COPILOT_RESUME' && isContextValid()) {
        try {
            chrome.storage.local.set({ selectedResumeId: event.data.resumeId });
            console.log("Resume Context Synced.");
        } catch (e) {
            console.warn("Context invalidated, ignoring sync.");
        }
    }
});

// 4. Listen for messages from Background Script (Context-Safe)
if (isContextValid()) {
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        if (!isContextValid()) return;

        if (request.type === 'TOGGLE_SIDEBAR') {
            try {
                createSidebar();
                const sidebar = document.getElementById('jobc-copilot-sidebar');
                if (sidebar) {
                    sidebar.classList.toggle('active');
                    sendResponse({ status: "toggled" });
                }
            } catch (e) { console.error(e); }
        }
        return true;
    });
}

// 5. Sidebar UI Construction
function createSidebar() {
    if (document.getElementById('jobc-copilot-sidebar')) return;
    if (!document.body) return;

    const sidebar = document.createElement('div');
    sidebar.id = 'jobc-copilot-sidebar';
    sidebar.innerHTML = `
        <div class="jobc-header">
            <div class="jobc-logo">⚡</div>
            <div class="jobc-title">Job Pilot </div>
            <button id="jobc-close">×</button>
        </div>
        <div class="jobc-content">
            <div class="jobc-status-card">
                <div id="jobc-badge" class="jobc-badge">READY</div>
                <h3 id="jobc-status-title">Magic Fill Available</h3>
                <p id="jobc-status-desc">I'm ready to assist with this application.</p>
                <button id="jobc-magic-fill" class="jobc-primary-btn">✨ Magic Fill</button>
            </div>
            
            <div class="jobc-tools">
                <button class="jobc-tool-btn">📄 Tailor Resume</button>
                <button class="jobc-tool-btn">✍️ Cover Letter</button>
            </div>

            <div id="jobc-logs" class="jobc-terminal">
                > System initialized...
                <br/>> Waiting for command...
            </div>
        </div>
    `;
    document.body.appendChild(sidebar);

    document.getElementById('jobc-close').onclick = () => sidebar.classList.remove('active');
    document.getElementById('jobc-magic-fill').onclick = performMagicFill;
}

// 6. Magic Fill Logic (Context-Safe)
async function performMagicFill() {
    if (!isContextValid()) {
        alert("Extension reloaded. Please refresh the page.");
        return;
    }

    const logs = document.getElementById('jobc-logs');
    const btn = document.getElementById('jobc-magic-fill');
    
    btn.disabled = true;
    btn.innerText = "⏳ Processing...";
    logs.innerHTML += '<br/>[AGENT] Scanning form fields...';
    
    const inputs = document.querySelectorAll('input, select, textarea');
    const fieldLabels = [];
    const fieldMap = new Map();

    inputs.forEach((input) => {
        const label = findLabel(input);
        if (label && label.length > 1) {
            fieldLabels.push(label);
            fieldMap.set(label, input);
        }
    });

    logs.innerHTML += `<br/>[AGENT] Found ${fieldLabels.length} relevant fields.`;
    
    try {
        const storage = await chrome.storage.local.get('selectedResumeId');
        if (!storage.selectedResumeId) {
            logs.innerHTML += '<br/>[ERROR] No resume selected! Visit dashboard.';
            btn.disabled = false;
            btn.innerText = "✨ Magic Fill";
            return;
        }

        logs.innerHTML += '<br/>[AI] Mapping fields...';
        
        const result = await new Promise((resolve) => {
            if (!isContextValid()) return resolve({ error: "Context invalidated" });
            chrome.runtime.sendMessage({
                type: 'MAP_FIELDS',
                resumeId: storage.selectedResumeId,
                fields: fieldLabels
            }, (response) => resolve(response));
        });

        if (result.error) throw new Error(result.error);
        
        const mappings = result.data;
        let fillCount = 0;
        for (const [label, value] of Object.entries(mappings)) {
            const input = fieldMap.get(label);
            if (input) {
                input.value = value;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                fillCount++;
            }
        }
        logs.innerHTML += `<br/>[SUCCESS] ${fillCount} fields populated.`;
    } catch (error) {
        logs.innerHTML += `<br/>[ERROR] ${error.message}`;
    } finally {
        btn.disabled = false;
        btn.innerText = "✨ Magic Fill";
    }
}

function findLabel(el) {
    if (el.id) {
        const label = document.querySelector(`label[for="${el.id}"]`);
        if (label) return label.innerText.trim().replace(/\*$/, '');
    }
    const parentLabel = el.closest('label');
    if (parentLabel) return parentLabel.innerText.trim().replace(/\*$/, '');
    if (el.getAttribute('aria-label')) return el.getAttribute('aria-label');
    if (el.placeholder) return el.placeholder;
    return "";
}

// Inject CSS
const style = document.createElement('style');
style.textContent = `
    #jobc-copilot-sidebar {
        position: fixed;
        right: -350px;
        top: 0;
        width: 320px;
        height: 100vh;
        background: #ffffff;
        box-shadow: -10px 0 30px rgba(0,0,0,0.1);
        z-index: 9999999;
        transition: right 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        font-family: 'Inter', system-ui, sans-serif;
        display: flex;
        flex-direction: column;
    }
    #jobc-copilot-sidebar.active { right: 0; }
    .jobc-header { padding: 20px; background: #0f172a; color: white; display: flex; align-items: center; gap: 12px; }
    .jobc-logo { background: #4f46e5; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 8px; font-weight: bold; }
    .jobc-title { font-weight: 800; font-size: 18px; }
    #jobc-close { margin-left: auto; background: none; border: none; color: white; font-size: 24px; cursor: pointer; }
    .jobc-content { padding: 20px; flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 20px; }
    .jobc-status-card { background: #f8fafc; border: 1px solid #e2e8f0; padding: 20px; border-radius: 20px; text-align: center; }
    .jobc-badge { display: inline-block; padding: 4px 12px; background: #f0fdf4; color: #166534; font-weight: 900; font-size: 10px; border-radius: 100px; margin-bottom: 12px; }
    .jobc-primary-btn { width: 100%; padding: 14px; background: #4f46e5; color: white; border: none; border-radius: 12px; font-weight: bold; cursor: pointer; margin-top: 15px; }
    .jobc-primary-btn:hover:not(:disabled) { background: #4338ca; }
    .jobc-primary-btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .jobc-tools { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .jobc-tool-btn { padding: 10px; background: white; border: 1px solid #e2e8f0; border-radius: 12px; font-size: 12px; font-weight: bold; cursor: pointer; }
    .jobc-terminal { background: #000; color: #10b981; font-family: monospace; font-size: 11px; padding: 15px; border-radius: 12px; height: 150px; overflow-y: auto; }
`;
document.head.appendChild(style);

// Auto-show logic
if (location.href.includes('jobs') || location.href.includes('apply') || location.href.includes('post')) {
    setTimeout(() => {
        if (isContextValid()) {
            createSidebar();
            const s = document.getElementById('jobc-copilot-sidebar');
            if (s) s.classList.add('active');
        }
    }, 1000);
}
