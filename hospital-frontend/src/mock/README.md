# 前端 Mock 数据说明

> 协作规范见 [docs/TEAM_COLLABORATION.md](../../docs/TEAM_COLLABORATION.md) §3.4

## 启用方式

在 `hospital-frontend/.env.development` 中设置：

```env
VITE_USE_MOCK=true
```

关闭 Mock、联调真实 Gateway：

```env
VITE_USE_MOCK=false
VITE_API_BASE=http://127.0.0.1:9000/api/v1
```

## 目录约定

| 文件 | 模块 |
|------|------|
| `doctor.js` | 门诊医生 |
| `lis.js` | 检验科 |
| `pacs.js` | 检查科 |
| `disposal.js` | 处置科（PENDING API） |
| `registrar.js` | 挂号收费 |
| `pharmacy.js` | 药房 |
| `admin.js` | 管理端 |

## 返回格式

必须与后端一致：

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "data": { }
}
```

字段名以 [docs/API.md](../../docs/API.md) 为准。

## PENDING 标记

后端未实现的接口在 [docs/FRONTEND_API_MAP.md](../../docs/FRONTEND_API_MAP.md) 标 **PENDING**；实现并验收通过后改为 **DONE**，删除或禁用对应 Mock。
