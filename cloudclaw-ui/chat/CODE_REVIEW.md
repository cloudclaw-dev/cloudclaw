# CloudClaw 前端 Code Review 报告

## 🐛 Bug（需修复）

### 1. UserManage.vue - `deleteUser` 函数未定义但被模板调用
**严重程度：高（运行时报错）**
- 第24行移动端卡片中 `@click="deleteUser(item)"` 直接调用 `deleteUser`
- 但实际定义的删除函数是 `handleDelete`，且 `deleteUser` 是从 API 导入的函数（异步），不是事件处理器
- 移动端点击删除会直接调用 API 但无确认弹窗、无反馈

### 2. LlmManage.vue - 日期选择器语法错误
**严重程度：高（编译报错）**
- 第 `start-:placeholder` 和 `end-:placeholder` 属性语法错误
```html
start-:placeholder="$t('monitor.startTime')"
end-:placeholder="$t('monitor.endTime')"
```
应为 `start-placeholder` 和 `end-placeholder`

### 3. SkillManage.vue - `loadData` 中逐个请求文件计数（N+1 问题）
**严重程度：中（性能）**
- 每次加载 skill 列表时，逐个发请求获取文件数量
- 如果有50个 skill，就会额外发50个请求
- 应由后端返回文件数量，或批量查询

### 4. SkillManage.vue - 打开文件编辑器时未加载文件内容
**严重程度：中（功能缺陷）**
- `openFileEditor` 直接用 `file.content`，但列表接口可能不返回文件内容
- 需要先调用 `getSkillFile` 获取文件内容再编辑

### 5. Dashboard.vue - echarts 图表未销毁，内存泄漏
**严重程度：中（内存泄漏）**
- `renderUsageChart` 和 `renderSessionChart` 每次调用都 `echarts.init()` 创建新实例
- 没有 `onUnmounted` 中销毁，也没有检查是否已存在实例
- 切换页面再回来会累积实例

### 6. LlmManage.vue - 多处 i18n key 误用
**严重程度：中（UI 显示错误）**
- `contextWindow` 和 `maxOutput` 列标签都用了 `$t('llm.providers')`（"提供商"），应为各自独立的 key
- `inputPrice` 和 `outputPrice` 表单标签都用了 `$t('llm.unitPrice')`（"单价"），应区分输入/输出
- 凭据表格的 `priority` 和 `weight` 列都用了 `$t('llm.unitPrice')`
- 用量统计多列标签重复（`$t('llm.usageCount')`、`$t('llm.tokenStats')`）

### 7. McpServerManage.vue - `handleToggle` 没有 catch 回滚
**严重程度：低**
- 如果 API 失败，`row.enabled` 不会回滚到原值（其他管理页面有处理）

### 8. SandboxManage.vue - 表格列用了错误的模板语法
**严重程度：高（显示异常）**
- `el-table-column` 的内容使用了函数式写法而非 template slot：
```html
<el-table-column label="ID" width="100">{{ (row: any) => shortId(row.id) }}</el-table-column>
```
这会直接把函数体转成字符串显示，不会渲染短ID。需要改为：
```html
<el-table-column label="ID" width="100">
  <template #default="{ row }">{{ shortId(row.id) }}</template>
</el-table-column>
```
- 影响列：ID, Session, Agent, createdAt（共4列）

### 9. SandboxManage.vue - echarts LOCAL 选项的 label 用了模板字符串
**严重程度：低（可能正常）**
- `<el-option value="LOCAL" label="{{ $t('sandbox.local') }}" />` — 双花括号在属性中不会解析，应改为 `:label`

### 10. MemoryPanel.vue - `watch(filterSessionId)` 和 `@change="loadTasks"` 双重触发
**严重程度：低**
- 下拉框同时绑了 `@change="loadTasks"` 和 `watch(filterSessionId, loadTasks)`
- 切换一次会触发两次加载

### 11. ChatLayout.vue - `nav-bar-header-link` 样式放在 `<style scoped>` 外
**严重程度：低（样式可能不生效）**
- 之前通过字符串替换添加的 `.nav-bar-header-link` 样式插在 CSS 中间，如果 scoped 不匹配可能不生效
