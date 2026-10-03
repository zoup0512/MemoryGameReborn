# PK 服务器部署包（LAN 联调）

目标：192.168.110.140（端口 **8090**——该机 8080 已被 SRS 流媒体服务占用）。

| 文件 | 用途 |
|---|---|
| `memorygames-pk-server-0.1.0.jar` | 服务端可执行 fat jar（Spring Boot 3 / Java 17） |
| `deploy.sh` | Linux 远程部署：`./deploy.sh <user>@192.168.110.140`（scp 上传 + 重启 + 健康检查） |
| `deploy.bat` | 目标机是 Windows 时，拷贝整个目录到 140 本地双击运行 |

两种方式任选其一。数据落在 `./data/pk`（H2 文件库，重启不丢账号/金币）。

## 客户端对接

- debug 构建 APK 默认连接 `ws://192.168.110.140:8090/ws`（`OnlineConfig.LAN_TEST_ENDPOINT`，
  按 `FLAG_DEBUGGABLE` 区分；release 构建仍走生产地址）。
- Manifest 已加 `usesCleartextTraffic="true"`，允许 ws:// 明文。
- 真机若连不上：确认手机与 140 同网段、140 防火墙放行 8090。

## 验证

```
curl -i -m 5 -H "Connection: Upgrade" -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" -H "Sec-WebSocket-Version: 13" \
  http://192.168.110.140:8090/ws
# 期望 HTTP/1.1 101 Switching Protocols
```
