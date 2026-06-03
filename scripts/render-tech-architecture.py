#!/usr/bin/env python3
"""Render NST tech architecture diagram to docs/images/tech-architecture.png"""

from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

# Chinese font on Windows
plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Arial Unicode MS", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

OUT = Path(__file__).resolve().parents[1] / "docs" / "images" / "tech-architecture.png"
W, H = 16, 11
fig, ax = plt.subplots(figsize=(W, H))
ax.set_xlim(0, W)
ax.set_ylim(0, H)
ax.axis("off")
fig.patch.set_facecolor("#F8FAFC")

# Colors
C_TITLE = "#0F172A"
C_LAYER = "#E2E8F0"
C_FE = "#DBEAFE"
C_FE_BORDER = "#2563EB"
C_GW = "#1E40AF"
C_JAVA = "#EFF6FF"
C_JAVA_BORDER = "#3B82F6"
C_HIS = "#DBEAFE"
C_LIS = "#E0E7FF"
C_PACS = "#EDE9FE"
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


# Title
text(W / 2, H - 0.45, "智慧云脑诊疗平台 技术架构图", size=20, weight="bold", color=C_TITLE)
text(W / 2, H - 0.85, "NST · Nexus Smart Treatment  |  8×Java jar + 1×Python  |  Gateway :9000", size=11, color=C_MUTED)

# --- Layer 1: Frontend ---
layer1_y = 8.55
layer_band(layer1_y, 1.35, "前端层")
box(2.0, layer1_y + 0.25, 4.8, 0.85, C_FE, C_FE_BORDER)
text(4.4, layer1_y + 0.72, "患者微信小程序", size=12, weight="bold")
text(4.4, layer1_y + 0.42, "原生 · /api/v1/patient/**", size=9, color=C_MUTED)

box(9.2, layer1_y + 0.25, 4.8, 0.85, C_FE, C_FE_BORDER)
text(11.6, layer1_y + 0.72, "医生 / 管理 PC", size=12, weight="bold")
text(11.6, layer1_y + 0.42, "Vue3 + Element Plus + Pinia", size=9, color=C_MUTED)

# --- Layer 2: Gateway ---
layer2_y = 7.35
layer_band(layer2_y, 0.95, "网关层")
box(4.8, layer2_y + 0.18, 6.4, 0.62, C_GW, "#1D4ED8", lw=2.2)
text(8.0, layer2_y + 0.58, "Spring Cloud Gateway", size=13, weight="bold", color="white")
text(8.0, layer2_y + 0.32, "统一入口 · JWT 校验 · 路由  |  :9000", size=9, color="#DBEAFE")

# --- Layer 3: Microservices ---
layer3_y = 3.55
layer_band(layer3_y, 3.55, "微服务层")

text(4.35, layer3_y + 3.15, "Java 微服务集群（Spring Boot 3.4 · Java 17）", size=11, weight="bold", color="#1D4ED8")
box(0.65, layer3_y + 0.35, 8.4, 2.55, C_JAVA, C_JAVA_BORDER, lw=1.5)

# Platform services row
svc_w, svc_h = 1.85, 0.95
platform = [
    ("hospital-auth", ":9101", "认证 · JWT 签发"),
    ("hospital-management", ":9105", "字典 · 排班"),
]
for i, (name, port, desc) in enumerate(platform):
    x = 1.0 + i * 2.05
    y = layer3_y + 1.65
    box(x, y, svc_w, svc_h, "#FFFFFF", C_JAVA_BORDER)
    text(x + svc_w / 2, y + 0.68, name, size=8.5, weight="bold")
    text(x + svc_w / 2, y + 0.42, port, size=8, color=C_MUTED)
    text(x + svc_w / 2, y + 0.18, desc, size=7.5, color=C_MUTED)

# Business HIS/LIS/PACS row
business = [
    ("hospital-his", ":9102", "HIS 门诊主业务", C_HIS, "#2563EB"),
    ("hospital-lis", ":9103", "LIS 检验执行", C_LIS, "#4F46E5"),
    ("hospital-pacs", ":9104", "PACS 检查执行", C_PACS, "#7C3AED"),
]
for i, (name, port, desc, fc, ec) in enumerate(business):
    x = 1.0 + i * 2.05
    y = layer3_y + 0.55
    box(x, y, svc_w, svc_h, fc, ec, lw=2.0)
    text(x + svc_w / 2, y + 0.68, name, size=8.5, weight="bold")
    text(x + svc_w / 2, y + 0.42, port, size=8, color=C_MUTED)
    text(x + svc_w / 2, y + 0.18, desc, size=7.5, color=C_MUTED)

# his internal modules note
text(4.35, layer3_y + 0.22, "his 内含：patient · doctor · pharmacy · registrar · disposal", size=8, color=C_MUTED)

# AI bridge
box(5.15, layer3_y + 1.65, 1.85, 0.95, "#FFFFFF", C_JAVA_BORDER)
text(6.075, layer3_y + 2.33, "hospital-ai-bridge", size=8.5, weight="bold")
text(6.075, layer3_y + 2.07, ":9106", size=8, color=C_MUTED)
text(6.075, layer3_y + 1.83, "Spring AI · LLM", size=7.5, color=C_MUTED)

# common jar
text(7.35, layer3_y + 2.1, "hospital-common", size=8.5, weight="bold", color=C_MUTED, ha="left")
text(7.35, layer3_y + 1.85, "共享 jar（非进程）", size=7.5, color=C_MUTED, ha="left")

# Python AI
text(12.0, layer3_y + 3.15, "AI 影像服务（Python）", size=11, weight="bold", color="#C2410C")
box(9.55, layer3_y + 0.55, 5.0, 2.55, C_AI_PY, C_AI_BORDER, lw=2.0)
text(12.05, layer3_y + 2.35, "hospital-ai", size=12, weight="bold", color="#9A3412")
text(12.05, layer3_y + 2.05, "FastAPI + PyTorch CNN", size=9, color=C_MUTED)
text(12.05, layer3_y + 1.75, "医学影像推理 · :8000", size=9, color=C_MUTED)
text(12.05, layer3_y + 1.15, "P4 阶段 · 异步回调 pacs", size=8.5, color="#B45309")
text(12.05, layer3_y + 0.85, "bridge 经 HTTP 调 LLM（预留）", size=8.5, color="#B45309")

# --- Layer 4: Infrastructure ---
layer4_y = 0.55
layer_band(layer4_y, 2.65, "基础设施与存储")
infra = [
    ("PostgreSQL\n+ pgvector", "业务库 hospital\n向量检索 P4+"),
    ("MinIO", "影像 / 附件\n对象存储"),
    ("Nacos", "注册发现\n配置中心"),
    ("Redis", "缓存 / 会话\nP1 不依赖"),
]
for i, (title, desc) in enumerate(infra):
    x = 1.0 + i * 3.55
    dashed = i == 3
    box(x, layer4_y + 0.45, 3.15, 1.75, C_INFRA, C_INFRA_BORDER, lw=1.5 if not dashed else 1.2)
    if dashed:
        rect = FancyBboxPatch(
            (x, layer4_y + 0.45), 3.15, 1.75,
            boxstyle="round,pad=0.02,rounding_size=0.08",
            facecolor="none", edgecolor=C_INFRA_BORDER, linewidth=1.2, linestyle="--", zorder=3,
        )
        ax.add_patch(rect)
    text(x + 1.575, layer4_y + 1.65, title, size=11, weight="bold")
    text(x + 1.575, layer4_y + 1.05, desc, size=8.5, color=C_MUTED)

# --- Arrows ---
# Frontend -> Gateway
arrow(4.4, layer1_y + 0.25, 7.0, layer2_y + 0.8)
arrow(11.6, layer1_y + 0.25, 9.0, layer2_y + 0.8)

# Gateway -> Java cluster (center)
arrow(8.0, layer2_y + 0.18, 4.35, layer3_y + 3.55)

# Java -> Infra (solid)
for tx in [2.575, 6.125, 9.675]:
    arrow(tx, layer3_y + 0.35, tx, layer4_y + 2.2, color="#475569")

# pacs -> MinIO (dashed)
arrow(9.675, layer3_y + 0.55, 6.125, layer4_y + 2.2, color="#7C3AED", style="--", lw=1.4)

# pacs/bridge -> hospital-ai (dashed)
arrow(5.025, layer3_y + 1.0, 9.55, layer3_y + 1.5, color="#EA580C", style="--", lw=1.6, rad=0.15)
arrow(6.075, layer3_y + 1.65, 10.2, layer3_y + 1.8, color="#EA580C", style="--", lw=1.6, rad=-0.1)

# Legend
leg_x, leg_y = 0.55, 0.15
text(leg_x + 0.1, leg_y + 0.35, "图例", size=10, weight="bold", ha="left")
arrow(leg_x + 0.9, leg_y + 0.35, leg_x + 1.5, leg_y + 0.35, color="#475569")
text(leg_x + 1.65, leg_y + 0.35, "P1～P3 主链路（Java 门诊闭环）", size=9, ha="left")
arrow(leg_x + 5.4, leg_y + 0.35, leg_x + 6.0, leg_y + 0.35, color="#EA580C", style="--")
text(leg_x + 6.15, leg_y + 0.35, "P4 AI / 影像 CNN（HTTP 异步）", size=9, ha="left")
rect = mpatches.Rectangle((leg_x + 10.2, leg_y + 0.22), 0.35, 0.25, fill=False, edgecolor=C_INFRA_BORDER, linestyle="--")
ax.add_patch(rect)
text(leg_x + 10.75, leg_y + 0.35, "可选组件", size=9, ha="left")

OUT.parent.mkdir(parents=True, exist_ok=True)
plt.tight_layout(pad=0.2)
fig.savefig(OUT, dpi=180, bbox_inches="tight", facecolor=fig.get_facecolor())
plt.close()
print(f"Saved: {OUT}")
