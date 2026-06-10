cd /data/data/com.termux/files/home/rikkahub

# delete_branch - add confirm
sed -i 's/^            "delete_branch" -> {/            "delete_branch" -> {\n                val delBranch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: error("branch required")\n                if (fullRepo.isBlank()) error("owner and repo required")\n                requireConfirm("删除分支 '\''" + delBranch + "'\''", "将永久删除分支。如果分支有未合并的提交，数据将丢失！")\n                gh("DELETE", "https://api.github.com/repos/'\'' + fullRepo + '\''/git/refs/heads/'\'' + delBranch)/' app/src/main/java/me/rerere/rikkahub/data/ai/tools/GitHubTool.kt
