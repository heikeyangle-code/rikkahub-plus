# 群聊问题清单

## Bug（影响正常使用）

1. **NATURAL 选人概率低** — `talkativeness` 默认 0.5，每人独立掷骰子，2 人时一起回的几率只有 25%。应该至少选 2 人
2. **ChatMessage 传错 speaker** — 第 402 行 `assistant = members.firstOrNull()`，所有消息都标成群聊第一个人说的，头像/名字不对
3. **默认 APPEND 不默认** — 创建对话框默认 `SWAP`，创建后第一条回复会删自己的消息
4. **SWAP 实现有 bug** — 第 294 行 `indexOfLast { it.role == ASSISTANT }` 删最后一条助手消息不分角色，A 的回会删掉 B 的

## 缺失功能

5. **不流式** — `generateForAssistant` 等完整结果才显示，看不到逐字输出和思考过程
6. **自动接话** — `autoModeDelay` 字段有但代码完全没用，一轮结束就停了
7. **群聊模型覆盖** — `GroupChat.chatModelId` 字段有但生成时没用（第 325-327 行其实单独处理了，但没覆盖到所有管线）
8. **成员 talkativeness ** — 设置里有滑块但助理默认 0.5，新角色都是 0.5

## 小问题

9. **创建群聊默认 SWAP** — 第 107 行
10. **长按发送走错管线** — 第 365 行 `onLongSendClick` 直接调 `chatService.sendMessage`，不走群聊选人
11. **初始化 assistantId 是第一个成员** — 第 99 行 `gc.memberIds.firstOrNull()`，可能影响模型选择
12. **生成失败后的重试** — 第 335-339 行 catch 后 only delay 2s continue，没有重试机制

## 建议修复顺序

1. 默认 APPEND（创建 + 默认值）
2. NATURAL 至少选 2 人
3. ChatMessage 传正确 speaker
4. 生成流式显示（用 session state 实时更新）
5. 自动接话（`autoModeDelay` 秒后触发下一轮）
6. 删掉 SWAP 那块有 bug 的代码
