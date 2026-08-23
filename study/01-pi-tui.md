# 01. pi-tui(终端 UI 库)

> 阶段:支线(独立叶子库,可快速过 / 也可推迟) | 前置:无

`packages/tui` —— 一个带**差分渲染**的终端 UI 库。coding-agent 的交互式界面构建在它之上。它本身不涉及 agent/LLM 概念,是熟悉项目 TS 风格的很好热身。

> 如果你的目标是尽快理解 agent,这一节可以快速浏览「核心概念」后直接跳到 [03 pi-ai](./03-pi-ai.md),等需要做交互式界面相关的事再回来。

## 学习目标

- 理解「差分渲染」终端 UI 的工作方式
- 看懂 layout 树、终端抽象、editor 组件、键位系统的关系
- 知道 coding-agent 怎么用它

## 关键文件

- `src/index.ts` —— 包导出入口
- `src/tui.ts` —— TUI 顶层入口
- `src/terminal.ts` —— 原始终端抽象(写入、光标、尺寸)
- `src/layout.ts` + `src/layout-node.ts` —— 布局树(节点如何排列/测量)
- `src/tui-main-screen.ts` / `src/tui-alt-screen.ts` —— 主屏 / 全屏(alt screen)两种屏幕模式
- `src/editor-component.ts` —— 文本编辑器组件(用户输入框的基础)
- `src/components/` —— 复用 UI 组件集合
- `src/keybindings.ts` + `src/keys.ts` + `src/native-modifiers.ts` —— 键位解析与默认绑定(注意:`AGENTS.md` 要求键位走 `DEFAULT_EDITOR_KEYBINDINGS` / `DEFAULT_APP_KEYBINDINGS`,禁止硬编码)
- `src/autocomplete.ts` + `src/fuzzy.ts` —— 补全与模糊匹配
- `src/kill-ring.ts` / `src/undo-stack.ts` / `src/word-navigation.ts` —— 剪贴环、撤销栈、按词导航等编辑能力
- `src/terminal-colors.ts` / `src/terminal-image.ts` —— 颜色、终端内联图片

## 核心概念

- **差分渲染**:不每帧重画整屏,而是计算与上一帧的差异、只输出变化的部分(性能关键)。
- **layout 树**:UI 用树形节点组织,各节点负责自己的测量与绘制,父节点决定子节点布局。
- **input → keybinding → action**:原始按键 → 解析为按键事件 → 命中绑定 → 触发 action。

## 建议阅读顺序

1. `src/index.ts` 看导出范围,建立「这个库对外提供什么」的全貌。
2. `src/terminal.ts` → `src/tui.ts`:从最底层的终端抽象往上看。
3. `src/layout.ts` + `src/layout-node.ts`:布局怎么算。
4. `src/tui-main-screen.ts`:主屏如何组装。
5. `src/editor-component.ts` + `src/keybindings.ts`:输入与交互。
6. 跑它的测试:`node --test`(tui 用 `node:test`,不是 vitest),从包根目录 `node --test test/<file>.test.ts`。

## 检查点

- [ ] 差分渲染相比「整屏重画」省了什么?
- [ ] 一个按键从敲下到触发 action,经过哪几层?
- [ ] 为什么键位不能硬编码、要走默认绑定表?

---

## 笔记

### 概念与要点
（待补充）

### 代码走读
（待补充）

### 疑问与待查
（待补充）
