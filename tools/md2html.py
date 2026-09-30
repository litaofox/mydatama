# -*- coding: utf-8 -*-
"""将 docs 下的 Markdown 文档批量转换为带统一样式的单文件 HTML，并生成导航首页。"""
import os
import re
import markdown

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "html")
os.makedirs(OUT, exist_ok=True)

# (源文件相对路径, 分组, 卡片描述)
DOCS = [
    ("docs/01-详细设计文档.md", "Lite MVP 方案", "企业数据中台 MVP 的架构、流程、数据模型与接口设计"),
    ("docs/02-部署文档与演示指南.md", "Lite MVP 方案", "一键部署、演示脚本、冒烟验证与故障排查"),
    ("docs/03-系统部署开销评估报告.md", "Lite MVP 方案", "资源占用、硬件配置、许可成本与 TCO 估算"),
    ("docs/research/01-数据中台功能与应用场景分析.md", "北斗项目前置研究", "数据中台六大功能域、应用场景与数据流转"),
    ("docs/research/02-中文开源项目功能与应用场景调研.md", "北斗项目前置研究", "10+ 中文开源项目对比、成熟度评估与选型建议"),
    ("docs/research/03-导航系统数据采集与应用平台设计.md", "北斗项目前置研究", "轨迹与视频采集、治理合规、平台总体技术设计"),
    ("docs/research/04-数据产品加工生成模块设计.md", "北斗项目前置研究", "数据产品加工流水线、合规校验、登记挂牌与交易全流程对接"),
    ("docs/design/00-文档体系总览与模板规范.md", "详细设计文档体系", "文档编号规则、完整清单、标准模板与编写规范"),
    ("docs/design/ddl/DDL-TRAJ-001-TRAJ-Schema数据库设计.md", "TRAJ 轨迹模块设计", "PostgreSQL+PostGIS 轨迹点表、8张业务表DDL、种子数据与ER图"),
    ("docs/design/mod/MOD-TRAJ-001-轨迹数据模拟与可视化模块设计.md", "TRAJ 轨迹模块设计", "模拟器架构、核心组件、业务流程、接口清单、异常处理与扩展点"),
    ("docs/design/uc/UC-TRAJ-001-模拟数据生成与启动.md", "TRAJ 轨迹模块设计", "模拟器启停流程、参数配置、数据推送与异常处理"),
    ("docs/design/uc/UC-TRAJ-002-轨迹回放.md", "TRAJ 轨迹模块设计", "轨迹回放查询、抽样策略、播放控件与报警点高亮"),
    ("docs/design/uc/UC-TRAJ-003-轨迹查询.md", "TRAJ 轨迹模块设计", "多条件筛选查询、地图视野筛选、结果导出与联动"),
    ("docs/design/uc/UC-TRAJ-004-轨迹总览大屏.md", "TRAJ 轨迹模块设计", "4统计卡片+实时位置地图+2图表、轮询刷新与降级策略"),
    ("docs/design/api/API-TRAJ-001-轨迹数据接口规格.md", "TRAJ 轨迹模块设计", "查询/回放/指标/导出/大屏聚合共8个接口完整规格"),
    ("docs/design/api/API-TRAJ-002-模拟器控制接口规格.md", "TRAJ 轨迹模块设计", "数据接入/模拟器启停/状态/批量导入共6个接口规格"),
]

CSS = r"""
:root{
  --primary:#1f5fbf; --primary-dark:#174a96; --bg:#f4f6fa; --card:#ffffff;
  --text:#1f2733; --muted:#667085; --border:#e3e8f0; --code-bg:#1e2530;
  --accent-bg:#eef4ff; --th-bg:#1f5fbf; --th-text:#fff;
}
*{box-sizing:border-box;}
html{scroll-behavior:smooth;}
body{margin:0;font-family:"PingFang SC","Microsoft YaHei","Segoe UI",sans-serif;
  background:var(--bg);color:var(--text);line-height:1.85;font-size:16px;}
.topbar{position:fixed;top:0;left:0;right:0;height:56px;background:var(--primary-dark);
  color:#fff;display:flex;align-items:center;padding:0 20px;z-index:100;
  box-shadow:0 2px 8px rgba(0,0,0,.15);}
.topbar .home{color:#fff;text-decoration:none;font-size:14px;opacity:.85;margin-right:18px;white-space:nowrap;}
.topbar .home:hover{opacity:1;}
.topbar .title{font-size:15px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.layout{display:flex;max-width:1280px;margin:56px auto 0;}
.toc{width:260px;flex-shrink:0;padding:24px 0 24px 20px;}
.toc-inner{position:sticky;top:76px;max-height:calc(100vh - 100px);overflow-y:auto;
  background:var(--card);border:1px solid var(--border);border-radius:10px;padding:16px 14px;}
.toc-inner .toc-title{font-size:13px;font-weight:700;color:var(--muted);
  letter-spacing:1px;margin:0 0 10px 4px;}
.toc-inner ul{list-style:none;margin:0;padding:0;}
.toc-inner ul ul{padding-left:14px;}
.toc-inner li{margin:3px 0;}
.toc-inner a{display:block;color:#44506a;text-decoration:none;font-size:13.5px;
  line-height:1.5;padding:3px 8px;border-radius:6px;border-left:2px solid transparent;}
.toc-inner a:hover{background:var(--accent-bg);color:var(--primary);}
.content{flex:1;min-width:0;background:var(--card);margin:20px 24px 60px;padding:40px 52px;
  border:1px solid var(--border);border-radius:12px;
  box-shadow:0 1px 4px rgba(16,42,90,.05);}
h1{font-size:28px;color:var(--primary-dark);border-bottom:3px solid var(--primary);
  padding-bottom:14px;margin-top:0;}
h2{font-size:22px;color:var(--primary-dark);margin-top:42px;
  border-left:5px solid var(--primary);padding-left:12px;}
h3{font-size:18px;color:#28456e;margin-top:32px;}
h4{font-size:16px;color:#344054;margin-top:24px;}
p{margin:14px 0;}
blockquote{margin:18px 0;padding:12px 18px;background:var(--accent-bg);
  border-left:4px solid var(--primary);color:#3d4a5c;border-radius:0 8px 8px 0;}
blockquote p{margin:6px 0;}
a{color:var(--primary);}
ul,ol{padding-left:26px;}
li{margin:5px 0;}
code{font-family:Consolas,"Courier New",monospace;font-size:13.5px;
  background:#eef1f6;color:#b02a5e;padding:2px 6px;border-radius:4px;}
pre{background:var(--code-bg);color:#d7e0ee;border-radius:10px;padding:18px 20px;
  overflow-x:auto;line-height:1.55;font-size:13.5px;
  box-shadow:inset 0 0 0 1px rgba(255,255,255,.06);}
pre code{background:none;color:inherit;padding:0;font-size:inherit;}
table{border-collapse:collapse;width:100%;margin:20px 0;font-size:14.5px;
  box-shadow:0 1px 3px rgba(16,42,90,.08);border-radius:8px;overflow:hidden;}
th{background:var(--th-bg);color:var(--th-text);font-weight:600;padding:10px 14px;
  text-align:left;border:1px solid var(--primary);white-space:nowrap;}
td{padding:9px 14px;border:1px solid var(--border);vertical-align:top;}
tbody tr:nth-child(even){background:#f7f9fd;}
tbody tr:hover{background:var(--accent-bg);}
hr{border:none;border-top:1px solid var(--border);margin:36px 0;}
strong{color:#16345f;}
h1 strong,h2 strong,h3 strong{color:inherit;}
.toc-btn{display:none;background:rgba(255,255,255,.15);border:1px solid rgba(255,255,255,.35);
  color:#fff;border-radius:6px;padding:5px 12px;font-size:13px;cursor:pointer;margin-right:12px;}
@media (max-width:900px){
  .toc{display:block;position:fixed;top:56px;left:0;bottom:0;width:280px;z-index:90;
    padding:16px;background:var(--bg);transform:translateX(-100%);transition:transform .25s;
    overflow-y:auto;}
  .toc.open{transform:translateX(0);box-shadow:4px 0 16px rgba(0,0,0,.15);}
  .toc-btn{display:inline-block;}
  .content{margin:12px 10px 40px;padding:24px 18px;}
  .topbar .title{font-size:13px;}
  table{font-size:13px;display:block;overflow-x:auto;}
}
@media print{
  .topbar,.toc{display:none;}
  .layout{margin:0;}
  .content{border:none;box-shadow:none;margin:0;padding:0;}
  pre,table,blockquote{break-inside:avoid;}
}
"""

INDEX_CSS = r"""
:root{--primary:#1f5fbf;--primary-dark:#174a96;--bg:#f4f6fa;--text:#1f2733;--muted:#667085;}
*{box-sizing:border-box;}
body{margin:0;font-family:"PingFang SC","Microsoft YaHei","Segoe UI",sans-serif;
  background:var(--bg);color:var(--text);line-height:1.8;}
.hero{background:linear-gradient(135deg,#174a96 0%,#1f5fbf 60%,#3b82d6 100%);
  color:#fff;padding:64px 24px 56px;text-align:center;}
.hero h1{margin:0 0 12px;font-size:32px;letter-spacing:1px;}
.hero p{margin:0;font-size:15px;opacity:.9;}
.wrap{max-width:1060px;margin:-30px auto 60px;padding:0 20px;}
.group{margin-top:36px;}
.group h2{font-size:18px;color:var(--primary-dark);border-left:5px solid var(--primary);
  padding-left:12px;margin-bottom:16px;}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:18px;}
.card{display:block;background:#fff;border:1px solid #e3e8f0;border-radius:12px;
  padding:22px 24px;text-decoration:none;color:inherit;
  box-shadow:0 1px 4px rgba(16,42,90,.06);transition:all .2s;}
.card:hover{transform:translateY(-3px);box-shadow:0 8px 22px rgba(31,95,191,.15);
  border-color:var(--primary);}
.card .num{font-size:13px;color:var(--primary);font-weight:700;letter-spacing:1px;}
.card h3{margin:8px 0 8px;font-size:17px;color:var(--primary-dark);}
.card p{margin:0;font-size:14px;color:var(--muted);}
.footer{text-align:center;color:var(--muted);font-size:13px;padding:24px 0 40px;}
"""


def to_html(md_path: str, title: str):
    with open(md_path, "r", encoding="utf-8") as f:
        text = f.read()
    md = markdown.Markdown(
        extensions=["markdown.extensions.tables", "markdown.extensions.fenced_code",
                    "markdown.extensions.sane_lists", "markdown.extensions.toc"],
        extension_configs={"toc": {"permalink": False}},
    )
    body = md.convert(text)
    # 去掉 toc 扩展自带的 <div class="toc"> 包裹，避免与侧边栏类名冲突
    toc = re.sub(r"^<div class=\"toc\">|</div>\s*$", "", md.toc.strip(), flags=re.S)
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title}</title>
<style>{CSS}</style>
</head>
<body>
<div class="topbar">
  <button class="toc-btn" onclick="document.querySelector('.toc').classList.toggle('open')">☰ 目录</button>
  <a class="home" href="index.html">← 文档首页</a>
  <span class="title">{title}</span>
</div>
<div class="layout">
  <aside class="toc"><div class="toc-inner">
    <p class="toc-title">目录导航</p>
    {toc}
  </div></aside>
  <main class="content">
{body}
  </main>
</div>
<script>
// 移动端：点击目录链接后自动收起抽屉
document.querySelectorAll('.toc a').forEach(function(a){{
  a.addEventListener('click',function(){{document.querySelector('.toc').classList.remove('open');}});
}});
</script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<script>
// 初始化 Mermaid 图表渲染
if (window.mermaid) {{
  mermaid.initialize({{ startOnLoad: true, theme: 'default', securityLevel: 'loose' }});
  // 将 fenced code block 的 mermaid 代码转换为 mermaid div
  document.querySelectorAll('pre code.language-mermaid').forEach(function(block) {{
    var pre = block.parentElement;
    var div = document.createElement('div');
    div.className = 'mermaid';
    div.textContent = block.textContent;
    pre.replaceWith(div);
  }});
  mermaid.run();
}}
</script>
</body>
</html>"""


def main():
    groups = {}
    produced = []
    for rel, group, desc in DOCS:
        src = os.path.join(ROOT, rel.replace("/", os.sep))
        name = os.path.splitext(os.path.basename(rel))[0]
        title = re.sub(r"^\d+-", "", name)
        out_name = name + ".html"
        with open(os.path.join(OUT, out_name), "w", encoding="utf-8") as f:
            f.write(to_html(src, title))
        groups.setdefault(group, []).append((out_name, title, desc))
        produced.append(out_name)
        print("generated:", out_name)

    cards_html = []
    for group, items in groups.items():
        cards_html.append(f'<div class="group"><h2>{group}</h2><div class="cards">')
        for i, (href, title, desc) in enumerate(items, 1):
            cards_html.append(
                f'<a class="card" href="{href}"><div class="num">DOC {i:02d}</div>'
                f"<h3>{title}</h3><p>{desc}</p></a>"
            )
        cards_html.append("</div></div>")

    index = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>企业数据中台 · 项目文档中心</title>
<style>{INDEX_CSS}</style>
</head>
<body>
<div class="hero">
  <h1>企业数据中台项目文档中心</h1>
  <p>企业级数据中台 MVP（Lite 架构） · 北斗交通运输数智融合平台前置研究</p>
</div>
<div class="wrap">
{''.join(cards_html)}
</div>
<div class="footer">生成日期：2026-09-30 · 点击任意卡片在线阅读，支持目录跳转、移动端浏览与浏览器打印</div>
</body>
</html>"""
    with open(os.path.join(OUT, "index.html"), "w", encoding="utf-8") as f:
        f.write(index)
    print("generated: index.html")


if __name__ == "__main__":
    main()
