# GitHub API 完整对照

## 现状: 57 个动作
## GitHub REST API v3: ~200+ 端点

## 对写代码真正有用但缺的（按重要性排序）

### 🔴 该加但没加的

1. **download_artifact** — ci_log 里混着下载逻辑，分开来 AI 能单独用
2. **get_workflow_run** — 当前 ci_status 是列表，缺单个 run 的详情
3. **list_commit_comments** — commit 上留言（code review 用）
4. **create_commit_comment** — 在 commit 的某行上评论
5. **search_commits** — 搜索提交历史
6. **add_collaborator** — 加协作者
7. **remove_collaborator** — 删协作者
8. **create_repository_dispatch** — 触发自定义 CI（比 workflow_dispatch 更灵活）
9. **star_repo** / **unstar_repo** — 收藏仓库
10. **list_notifications** / **mark_notification_read** — 看通知

### 🟡 现有但返回原始 JSON，AI 读不了

这些动作在工具里返回的是 `gh(url)` 的原始 JSON，AI 看不懂：

- `pr_list` → 返回 JSON，AI 不知道谁提的、是否draft、合并状态
- `list_issues` → 返回 JSON
- `list_branches` → 返回 JSON
- `list_tags` → 返回 JSON
- `list_releases` → 返回 JSON
- `list_contributors` → 返回 JSON
- `list_workflows` → 返回 JSON
- `ci_jobs` → 返回 JSON（至少改成人话表格）
- `list_my_repos` → 返回 JSON（刚加的）
- `list_files` → 返回 JSON
- `list_commits` → 返回 JSON

### 🟢 已有且好的

search_repo/code/issue/user, get_repo, compare_repos, 
create_repo, fork_repo, create_issue, issue_comment/update/labels/assign,
pr_view/create/update/review/merge/comment/request_reviewers,
ci_log/jobs/job_log/artifacts/cancel/rerun,
read_file/commit/commit_files/delete_file/diff_local,
list_branches/create_branch/delete_branch,
compare_commits/get_diff/commit_status/revert_commit,
create_gist/user_info/rate_limit

### 总结

**真正缺 10 个动作。** 现有 57 个里大概 12 个需要加格式化输出。
最多 22 个改动就能到达"真正对写代码有用"的程度。
