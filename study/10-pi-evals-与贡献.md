# 10. pi-evals 与贡献流程(进阶)

> 阶段:支线(进阶,想贡献/发版时学) | 前置:[09 coding-agent](./09-pi-coding-agent.md)

评测框架 + 构建/发版/供应链加固 + 贡献纪律。理解 agent 之后,这一节帮你把项目「跑起来交付」的部分补齐。

## 学习目标

- 知道 evals 怎么对 agent 做评测
- 看懂构建、独立二进制打包、发版流程
- 理解供应链加固(锁定依赖、shrinkwrap、lifecycle script 白名单)
- 掌握多 session 并存的 git 纪律与 lockstep 版本

## 关键文件

- `packages/evals/` —— 评测框架(`npm run eval`)
- `scripts/`(项目根):
  - `build-binaries.sh` —— 打独立二进制(Bun compile)
  - `release.mjs` / `local-release.mjs` / `release-notes.mjs` —— 发版与本地冒烟
  - `generate-coding-agent-shrinkwrap.mjs` —— 生成 `npm-shrinkwrap.json`(带 lifecycle script 白名单)
  - `generate-coding-agent-install-lock.mjs` —— 安装锁
  - `check-pinned-deps.mjs` / `check-ts-relative-imports.mjs` / `check-browser-smoke.mjs` / `check-lockfile-commit.mjs` —— 各类校验
  - `sync-versions.js` —— lockstep 版本同步
  - `profile-coding-agent-node.mjs` —— 性能 profile
  - `publish.mjs` / `publish-model-catalog.mjs` / `diff-model-catalog.mjs`
- `pi-test.sh` / `pi-test.bat` / `pi-test.ps1` —— 跨平台从源码跑 pi
- `test.sh` —— 跑非 e2e 测试
- `AGENTS.md` 的 Git / Dependency / Releasing / Changelog 节 —— **权威**
- `CONTRIBUTING.md` —— 贡献门禁与质量标准

## 核心概念

- **evals**:用一组任务 + 评判标准,量化 agent 表现;`npm run eval` 入口。
- **独立二进制**:Bun `--compile` 把 `pi` 打成单文件可执行,带运行时资产(`build:binary` 脚本)。
- **lockstep 版本**:所有包共享一个版本号,一起升(`patch`=修复+新增,`minor`=破坏性,无 major)。
- **供应链加固**(见 README「Supply-chain hardening」):
  - 直接外部依赖锁定到精确版本(`.npmrc` 的 `save-exact` + `min-release-age=2`)
  - `package-lock.json` 是依赖真相;pre-commit 拦截除非 `PI_ALLOW_LOCKFILE_CHANGE=1`
  - 发布包带 `npm-shrinkwrap.json`,锁定传递依赖
  - lifecycle script 有显式白名单,新依赖的 lifecycle script 不通过校验直到评审
  - `npm install` / `npm ci` 都 `--ignore-scripts`
- **多 session git 纪律**(`AGENTS.md` Git 节):可能多个 pi session 同时在此 cwd 改不同文件;只 `git add` 自己改的文件,绝不用 `git add -A/.`、`reset --hard`、`stash` 等。
- **CHANGELOG**:每包一个 `CHANGELOG.md`,新条目进 `## [Unreleased]`,已发布版本段不可改。

## 建议阅读顺序

1. `AGENTS.md` 重读 Git / Dependency and Install Security / Releasing / Changelog 四节。
2. `packages/evals/`:看一个 eval 怎么定义、怎么跑(`npm run eval -- <args>`)。
3. `scripts/release.mjs` + `local-release.mjs`:走一遍发版与本地冒烟流程(只读,别真发)。
4. `scripts/build-binaries.sh`:独立二进制怎么打。
5. `scripts/generate-coding-agent-shrinkwrap.mjs`:看 lifecycle script 白名单机制。
6. `scripts/check-*.mjs`:看 `npm run check` 背后都校验了什么。
7. `CONTRIBUTING.md`:贡献门禁(`lgtm`/`lgtmi`、自动关闭、质量标准)。

## 检查点

- [ ] 想发一个 patch 版本,完整流程是什么?哪些步骤是 release blocker?
- [ ] 为什么 lifecycle script 要白名单?新依赖的 lifecycle script 会怎样?
- [ ] 多 session 并存时,git 有哪些绝对不能用的命令?
- [ ] lockstep 版本下,patch 和 minor 的区别是什么?为什么没有 major?

---

## 笔记

### evals 用法
（待补充）

### 发版流程
（待补充）

### 供应链 / 校验机制
（待补充）

### 疑问与待查
（待补充）
