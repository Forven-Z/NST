#!/usr/bin/env python3
"""Render NST tech architecture diagram (English) to docs/images/tech-architecture-en.png"""

from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Arial Unicode MS", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

OUT = Path(__file__).resolve().parents[1] / "docs" / "images" / "tech-architecture-en.png"
W, H = 18, 12
fig, ax = plt.subplots(figsize=(W, H))
ax.set_xlim(0, W)
ax.set_ylim(0, H)
ax.axis("off")
fig.patch.set_facecolor("#F8FAFC")

C_TITLE = "#0F172A"
C_LAYER = "#E2E8F0"
C_FE = "#DBEAFE"
C_FE_BORDER = "#2563EB"
C_GW = "#1E40AF"
C_JAVA = "#EFF6FF"
C_JAVA_BORDER = "#3B82F6"
C_HIS = "#DBEAFE"
C_PATIENT = "#BFDBFE"
C_PHARM = "#93C5FD"
C_LIS = "#E0E7FF"
C_PACS = "#EDE9FE"
C_DISPOSAL = "#FEF3C7"
C_PLATFORM = "#F8FAFC"
C_AI_PY = "#FFF7ED"
C_AI_BORDER = "#EA580C"
C_INFRA = "#F0FDF4"
C_INFRA_BORDER = "#16A34A"
C_TEXT = "#1E293B"
C_MUTED = "#64748B"


def box(x, y, w, h, fc, ec, lw=1.8, radius=0.08, alpha=1.0):
    p = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad=0.02,rounding_size={radius}",
        facecolor=fc, edgecolor=ec, linewidth=lw, alpha=alpha,
        transform=ax.transData, zorder=2,
    )
    ax.add_patch(p)
    return p


def text(x, y, s, size=10, weight="normal", color=C_TEXT, ha="center", va="center", zorder=5):
    ax.text(x, y, s, fontsize=size, fontweight=weight, color=color, ha=ha, va=va, zorder=zorder)


def arrow(x1, y1, x2, y2, color="#64748B", style="-", lw=1.6, rad=0.0):
    a = FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle="-|>", mutation_scale=12,
        color=color, linewidth=lw, linestyle=style,
        connectionstyle=f"arc3,rad={rad}",
        zorder=1,
    )
    ax.add_patch(a)


def layer_band(y, h, label):
    box(0.35, y, W - 0.7, h, C_LAYER, "#CBD5E1", lw=1.2, radius=0.12, alpha=0.55)
    text(1.15, y + h - 0.22, label, size=11, weight="bold", color=C_MUTED, ha="left")


def svc_box(x, y, w, h, name, port, desc, fc, ec, lw=1.8):
    box(x, y, w, h, fc, ec, lw=lw)
    text(x + w / 2, y + h - 0.28, name, size=8.2, weight="bold")
    text(x + w / 2, y + h - 0.52, port, size=7.8, color=C_MUTED)
    text(x + w / 2, y + 0.18, desc, size=7.2, color=C_MUTED)


# Title
text(W / 2, H - 0.45, "Smart Cloud-Brain Healthcare Platform · Technical Architecture", size=20, weight="bold", color=C_TITLE)
text(
    W / 2, H - 0.88,
    "NST · Nexus Smart Treatment  |  11×Java jar + 1×Python  |  Gateway :9000  |  ADR-019 HIS split (3 jars)",
    size=10.5, color=C_MUTED,
)

# Layer 1: Frontend
layer1_y = 9.35
layer_band(layer1_y, 1.35, "Frontend Layer")
box(1.8, layer1_y + 0.25, 5.2, 0.85, C_FE, C_FE_BORDER)
text(4.4, layer1_y + 0.72, "Patient WeChat Mini Program", size=12, weight="bold")
text(4.4, layer1_y + 0.42, "/api/v1/patient/** · registrar", size=9, color=C_MUTED)

box(11.0, layer1_y + 0.25, 5.2, 0.85, C_FE, C_FE_BORDER)
text(13.6, layer1_y + 0.72, "Doctor / Admin PC", size=12, weight="bold")
text(13.6, layer1_y + 0.42, "Vue3 + Element Plus  ·  :5173", size=9, color=C_MUTED)

# Layer 2: Gateway
layer2_y = 8.05
layer_band(layer2_y, 1.0, "Gateway Layer")
box(5.2, layer2_y + 0.2, 7.6, 0.62, C_GW, "#1D4ED8", lw=2.2)
text(9.0, layer2_y + 0.62, "Spring Cloud Gateway", size=13, weight="bold", color="white")
text(9.0, layer2_y + 0.36, "Single entry · JWT · Routing  |  :9000", size=9, color="#DBEAFE")

# Layer 3: Microservices
layer3_y = 3.15
layer_band(layer3_y, 4.65, "Microservices Layer")
text(5.0, layer3_y + 4.35, "Java Microservices (Spring Boot 3.2 · Java 17 · Nacos)", size=11, weight="bold", color="#1D4ED8")
box(0.55, layer3_y + 0.35, 10.2, 3.75, C_JAVA, C_JAVA_BORDER, lw=1.5)

svc_w, svc_h = 2.95, 0.88
row_y = [layer3_y + 2.55, layer3_y + 1.45, layer3_y + 0.45]
col_x = [0.85, 3.95, 7.05]

platform = [
    ("hospital-auth", ":9101", "Auth · JWT issuance", C_PLATFORM, C_JAVA_BORDER),
    ("hospital-management", ":9107", "Dictionary · Scheduling", C_PLATFORM, C_JAVA_BORDER),
    ("hospital-ai-bridge", ":9106", "Spring AI · RAG · LLM", C_PLATFORM, C_JAVA_BORDER),
]
for i, item in enumerate(platform):
    svc_box(col_x[i], row_y[0], svc_w, svc_h, *item)

his_row = [
    ("hospital-patient", ":9108", "Patient · Reg · Billing", C_PATIENT, "#2563EB"),
    ("hospital-his", ":9102", "Clinical · Doctor · Orders", C_HIS, "#2563EB"),
    ("hospital-pharmacy", ":9109", "Pharmacy · Dispense", C_PHARM, "#2563EB"),
]
for i, item in enumerate(his_row):
    svc_box(col_x[i], row_y[1], svc_w, svc_h, *item, lw=2.0)

text(5.4, row_y[1] - 0.22, "Courseware HIS = patient + clinical + pharmacy (3 jars)", size=8, color=C_MUTED)

medtech = [
    ("hospital-lis", ":9103", "LIS lab execution", C_LIS, "#4F46E5"),
    ("hospital-pacs", ":9104", "PACS imaging", C_PACS, "#7C3AED"),
    ("hospital-disposal", ":9105", "Disposal execution", C_DISPOSAL, "#D97706"),
]
for i, item in enumerate(medtech):
    svc_box(col_x[i], row_y[2], svc_w, svc_h, *item, lw=2.0)

text(0.75, layer3_y + 3.55, "hospital-common (shared jar, not a process)", size=8.5, weight="bold", color=C_MUTED, ha="left")

# Python AI
text(14.2, layer3_y + 4.35, "AI Imaging Service (Python)", size=11, weight="bold", color="#C2410C")
box(11.15, layer3_y + 0.55, 5.8, 3.55, C_AI_PY, C_AI_BORDER, lw=2.0)
text(14.05, layer3_y + 3.35, "hospital-ai", size=12, weight="bold", color="#9A3412")
text(14.05, layer3_y + 3.05, "FastAPI + PyTorch CNN", size=9, color=C_MUTED)
text(14.05, layer3_y + 2.75, "Head / Lung / Tumor (3 tasks)", size=9, color=C_MUTED)
text(14.05, layer3_y + 2.35, "Internal :8000 · Not via Gateway", size=8.5, color="#B45309")
text(14.05, layer3_y + 1.85, "pacs async call → callback to DB", size=8.5, color="#B45309")
text(14.05, layer3_y + 1.35, "MinIO read · GPU inference", size=8.5, color="#B45309")

# Layer 4: Infrastructure
layer4_y = 0.55
layer_band(layer4_y, 2.35, "Infrastructure & Storage")
infra = [
    ("PostgreSQL\n+ pgvector", "DB hospital\nRAG vector search"),
    ("MinIO", "Imaging / files\nObject storage :9001"),
    ("Nacos", "Service discovery\nConfig center :8848"),
]
for i, (title, desc) in enumerate(infra):
    x = 1.0 + i * 4.15
    box(x, layer4_y + 0.4, 3.65, 1.65, C_INFRA, C_INFRA_BORDER, lw=1.5)
    text(x + 1.825, layer4_y + 1.55, title, size=11, weight="bold")
    text(x + 1.825, layer4_y + 1.0, desc, size=8.5, color=C_MUTED)

# Arrows
arrow(4.4, layer1_y + 0.25, 7.5, layer2_y + 0.82)
arrow(13.6, layer1_y + 0.25, 10.5, layer2_y + 0.82)
arrow(9.0, layer2_y + 0.2, 5.4, layer3_y + 4.1)

for tx in [2.825, 6.375, 10.0, 14.05]:
    arrow(tx, layer3_y + 0.35, tx if tx < 13 else 10.0, layer4_y + 2.05, color="#475569")

# pacs -> MinIO
arrow(8.375, layer3_y + 0.45, 6.375, layer4_y + 2.05, color="#7C3AED", style="--", lw=1.4)

# pacs -> hospital-ai
arrow(8.375, layer3_y + 0.95, 11.15, layer3_y + 2.0, color="#EA580C", style="--", lw=1.6, rad=0.12)

# patient -> his (Feign / internal)
arrow(2.325, row_y[1] + svc_h / 2, 5.425, row_y[1] + svc_h / 2, color="#2563EB", style="--", lw=1.2, rad=0.08)

# Legend
leg_x, leg_y = 0.55, 0.08
text(leg_x + 0.1, leg_y + 0.35, "Legend", size=10, weight="bold", ha="left")
arrow(leg_x + 0.75, leg_y + 0.35, leg_x + 1.35, leg_y + 0.35, color="#475569")
text(leg_x + 1.5, leg_y + 0.35, "P1–P3 main path (via Gateway)", size=9, ha="left")
arrow(leg_x + 5.0, leg_y + 0.35, leg_x + 5.6, leg_y + 0.35, color="#EA580C", style="--")
text(leg_x + 5.75, leg_y + 0.35, "P4 CNN / imaging async (internal HTTP)", size=9, ha="left")
arrow(leg_x + 10.5, leg_y + 0.35, leg_x + 11.1, leg_y + 0.35, color="#2563EB", style="--")
text(leg_x + 11.25, leg_y + 0.35, "Feign / internal API", size=9, ha="left")

OUT.parent.mkdir(parents=True, exist_ok=True)
plt.tight_layout(pad=0.2)
fig.savefig(OUT, dpi=180, bbox_inches="tight", facecolor=fig.get_facecolor())
plt.close()
print(f"Saved: {OUT}")
