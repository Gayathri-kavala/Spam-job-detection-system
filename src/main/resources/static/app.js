const form = document.querySelector("#analysisForm");
const sampleButton = document.querySelector("#sampleButton");
const refreshButton = document.querySelector("#refreshButton");
const statusEl = document.querySelector("#systemStatus");
const riskBadge = document.querySelector("#riskBadge");
const riskScore = document.querySelector("#riskScore");
const scoreRing = document.querySelector("#scoreRing");
const summaryText = document.querySelector("#summaryText");
const signalsList = document.querySelector("#signalsList");
const recommendationsList = document.querySelector("#recommendationsList");
const historyBody = document.querySelector("#historyBody");

const colors = {
    LOW: "#1e8f5a",
    MEDIUM: "#bd7b00",
    HIGH: "#c83d3d",
    CRITICAL: "#8b2332"
};

const sample = {
    title: "Remote Data Entry Assistant",
    company: "Global Hiring Team",
    contactEmail: "hr.globalhiring@gmail.com",
    jobUrl: "http://bit.ly/instant-job-offer",
    description: "Congratulations, you are hired instantly with no interview. Earn $1200 weekly working 1-2 hours daily. Limited slots available. Pay a refundable equipment fee by gift card and send passport, bank account, routing number and OTP to start today!!"
};

sampleButton.addEventListener("click", () => {
    Object.entries(sample).forEach(([key, value]) => {
        form.elements[key].value = value;
    });
});

refreshButton.addEventListener("click", () => loadAll());

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus("Analyzing...");
    const payload = Object.fromEntries(new FormData(form).entries());

    try {
        const response = await fetch("/api/analyses", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            throw new Error(`Analysis failed with status ${response.status}`);
        }
        const analysis = await response.json();
        renderResult(analysis);
        await loadAll();
        setStatus("Analysis saved");
    } catch (error) {
        setStatus(error.message);
    }
});

async function loadAll() {
    await Promise.all([loadStats(), loadHistory()]);
}

async function loadStats() {
    const stats = await fetchJson("/api/analyses/stats");
    document.querySelector("#totalScans").textContent = stats.totalScans;
    document.querySelector("#lowRisk").textContent = stats.lowRisk;
    document.querySelector("#mediumRisk").textContent = stats.mediumRisk;
    document.querySelector("#highRisk").textContent = stats.highRisk;
    document.querySelector("#criticalRisk").textContent = stats.criticalRisk;
}

async function loadHistory() {
    const rows = await fetchJson("/api/analyses");
    historyBody.innerHTML = "";
    if (rows.length === 0) {
        historyBody.innerHTML = `<tr><td colspan="6">No scans yet.</td></tr>`;
        return;
    }

    for (const row of rows) {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${escapeHtml(row.title)}</td>
            <td>${escapeHtml(row.company)}</td>
            <td><span class="risk-badge ${row.riskLevel.toLowerCase()}">${row.riskLevel}</span></td>
            <td>${row.riskScore}</td>
            <td>${new Date(row.createdAt).toLocaleString()}</td>
            <td><button class="delete-button" data-id="${row.id}" type="button">Delete</button></td>
        `;
        tr.addEventListener("click", (event) => {
            if (event.target.matches("button")) {
                return;
            }
            renderResult(row);
        });
        historyBody.appendChild(tr);
    }

    historyBody.querySelectorAll(".delete-button").forEach((button) => {
        button.addEventListener("click", async () => {
            await fetch(`/api/analyses/${button.dataset.id}`, { method: "DELETE" });
            await loadAll();
            setStatus("Scan deleted");
        });
    });
}

function renderResult(analysis) {
    const level = analysis.riskLevel;
    const score = analysis.riskScore;
    riskBadge.className = `risk-badge ${level.toLowerCase()}`;
    riskBadge.textContent = level;
    riskScore.textContent = score;
    scoreRing.style.background = `conic-gradient(${colors[level]} ${score * 3.6}deg, #e7edf0 0deg)`;
    summaryText.textContent = analysis.summary;

    signalsList.innerHTML = "";
    analysis.signals.forEach((signal) => {
        const li = document.createElement("li");
        const parts = signal.split(" | ");
        li.textContent = parts.length >= 4 ? `${parts[1]}: ${parts[2]} (${parts[3]})` : signal;
        signalsList.appendChild(li);
    });
    if (analysis.signals.length === 0) {
        signalsList.innerHTML = "<li>No risk signals found.</li>";
    }

    recommendationsList.innerHTML = "";
    analysis.recommendations.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = item;
        recommendationsList.appendChild(li);
    });
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`);
    }
    return response.json();
}

function setStatus(message) {
    statusEl.textContent = message;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

loadAll().catch((error) => setStatus(error.message));
