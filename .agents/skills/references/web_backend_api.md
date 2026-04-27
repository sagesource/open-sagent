---
name: web-backend-api
description: Web模块后端REST API接口规范，包含认证、对话管理、SSE流式对话接口定义
type: reference
---

# Web模块后端REST API接口规范

## 接口基地址

```
http://localhost:8080/api
```

## 认证方式

除注册和登录接口外，所有接口需在请求头中携带JWT Token：

```
Authorization: Bearer {token}
```

Token有效期24小时，通过登录接口获取。

## 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code`: 0表示成功，非0表示失败
- `message`: 提示消息
- `data`: 响应数据

## 认证接口

### 1. 用户注册

```
POST /api/auth/register
```

**请求体：**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**约束：**
- email: 必填，邮箱格式
- password: 必填，至少6位

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 2. 用户登录

```
POST /api/auth/login
```

**请求体：**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

### 3. 获取当前用户信息

```
GET /api/auth/me
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "user"
  }
}
```

### 4. 修改个人信息

```
PUT /api/auth/profile
```

**请求体：**
```json
{
  "nickname": "新昵称",
  "password": "newpassword123"
}
```

- nickname: 可选，修改昵称
- password: 可选，修改密码

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "新昵称"
  }
}
```

## 对话管理接口

### 5. 获取对话列表

```
GET /api/conversations
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "sessionId": "a1b2c3d4e5f6",
      "title": "新对话",
      "agentVersion": "simple",
      "updatedAt": "2026-04-26T10:00:00"
    }
  ]
}
```

### 6. 创建新对话

```
POST /api/conversations
```

**请求体：**
```json
{
  "agentVersion": "simple"
}
```

- agentVersion: 可选，默认"simple"，可选值["simple", "smart"]

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "sessionId": "a1b2c3d4e5f6",
    "title": "新对话",
    "agentVersion": "simple",
    "updatedAt": "2026-04-26T10:00:00"
  }
}
```

### 7. 删除对话

```
DELETE /api/conversations/{id}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 8. 修改对话标题

```
PUT /api/conversations/{id}/title
```

**请求体：**
```json
{
  "title": "新标题"
}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 9. 获取对话消息历史

```
GET /api/conversations/{id}/messages
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "role": "user",
      "content": "你好",
      "createdAt": "2026-04-26T10:00:00"
    },
    {
      "id": 2,
      "role": "assistant",
      "content": "你好！有什么我可以帮助你的？",
      "createdAt": "2026-04-26T10:00:05"
    }
  ]
}
```

## SSE流式对话接口

### 10. 流式对话

```
GET /api/chat/stream?sessionId={sessionId}&message={message}&agentVersion={agentVersion}
```

**参数：**
- sessionId: 必填，对话会话ID
- message: 必填，用户输入消息
- agentVersion: 可选，默认"simple"，可选值["simple", "smart"]

**响应格式：** SSE (text/event-stream)

```
event: message
data: 你好

event: message
data: ！

event: action
data: AGENT_ACTION[EXECUTE_TOOL]:search

event: title
data: 问候对话

event: done
data: 
```

### SSE事件类型说明

| 事件名 | 说明 |
|--------|------|
| `message` | 流式文本增量，逐字/逐句输出 |
| `action` | Agent动作标记，如 `AGENT_ACTION[EXECUTE_TOOL]:工具名` |
| `title` | 对话标题生成结果（前5条消息内触发） |
| `done` | 流式输出结束标记 |
| `error` | 异常信息 |

### 11. 中断对话

```
POST /api/chat/{sessionId}/cancel
```

**响应：** HTTP 200 (无响应体)

## 错误码说明

| HTTP状态码 | code | 说明 |
|-----------|------|------|
| 200 | 0 | 成功 |
| 400 | 400 | 业务异常（参数错误等） |
| 401 | 401 | 未认证或Token无效/过期 |
| 500 | 500 | 服务器内部错误 |

## 前端调用示例

### 认证流程

```typescript
// 登录
const resp = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});
const data = await resp.json();
localStorage.setItem('token', data.data.token);

// 带认证的API调用
const headers = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${localStorage.getItem('token')}`
});
```

### SSE流式对话

```typescript
const params = new URLSearchParams({ sessionId, message, agentVersion });
const es = new EventSource(`/api/chat/stream?${params.toString()}`);

es.addEventListener('message', (e) => {
  console.log('收到文本:', e.data);
});

es.addEventListener('action', (e) => {
  console.log('Agent动作:', e.data);
});

es.addEventListener('done', () => {
  es.close();
});
```
