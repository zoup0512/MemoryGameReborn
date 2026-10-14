# 2026-10-14 远端同步与集成说明

## 基线

本次同步前当前分支为 main，HEAD 与 origin/main 都是 `ee57124`。`git fetch origin --prune` 与 `git pull --ff-only origin main` 确认没有新的文本合并冲突。为避免直接改默认分支，使用 `feat/account-admin-integration` 提交账号体系、管理控制台与集成修复。

## 实际集成冲突

前期账号和管理控制台的新增文件仍在工作区，但配套 tracked 文件已回到最新 main 的旧游客/旧管理接口实现。Git 没有生成冲突标记，不代表源码可以直接组合使用：

- Android 初次构建 22 个缺失方法错误，账号 Activity/Manager 未接入 OnlineSession/Config/Identity/Ui。
- 服务端初次回归 49 项中14项失败，包括 access token 无法登录 WS、UUID 无凭证恢复未禁止、删除 token 唯一约束冲突及管理理由/查询接口不匹配。

修复策略是在最新 main 上恢复接入逻辑，不整体回滚客户端硬化：

- 保留 R8、资源压缩、签名完整性检查、既有 Keystore 密文格式和 ActiveAndroid 迁移修复。
- 恢复账号 UI/认证/安全迁移/会话撤销、在线余额隔离与协议字段。
- 添加账号反射 DTO 的 R8 保留规则，避免 release JSON 字段被混淆。
- 恢复管理接口 reason 校验、维护/日志只读接口、单次限流与审计容量；旧测试的成功写请求同步增加理由，不降低原断言。
- 服务端数据库追加 V4/V5/V6，不修改 V1～V3。

## 提交范围

提交源码、资源、测试、验收脚本和文档。`android_memory-game-reborn/release/` 作为本地发布产物加入忽略规则，不上传 APK、旧发布元数据、构建日志、临时数据库、凭证或 Python 缓存；没有删除这些本地文件。

前期账号和控制台验收报告是当时状态的记录，本次以当前重新运行的构建/回归结果为准。未部署生产、未修改生产数据库、TLS或SMTP，未接受 Android SDK licenses。

## 最终验证

- 服务端 `test bootJar`：13 个套件、49 项测试，0 failures、0 errors、0 skipped。
- 管理网页 `node --test src/test/js/admin-console.test.cjs`：19 项通过。
- Android `assembleDebug assembleRelease -x publishReleaseApk`：成功，保留 R8/资源压缩；正常包名与默认 endpoint，不注入签名。
- 独立 jar 启动 V1～V6 Flyway/Hibernate 校验及健康检查通过。
- `account_smoke.py` 真实本地 SMTP/REST/WebSocket 七组流程通过，包括原地注册、刷新重放撤销、注销取消和退出关闭 WS。
- 暂存源码检查未发现冲突标记、私钥/token模式或新增二进制产物。

本次未安装真机或重新执行 UI 回归，未运行需要 Docker 的 PostgreSQL `pgTest`；APK release 为 unsigned。
