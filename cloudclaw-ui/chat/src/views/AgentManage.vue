<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><SetUp /></el-icon></div>
        <div>
          <h2>{{ $t('nav.agent') }}</h2>
          <div class="header-desc">{{ $t('agent.subtitle') }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon> {{ $t('agent.newAgent') }}</el-button>
      </div>
    </div>

    <!-- Table Card -->
    <el-card shadow="hover" class="admin-card">
      <div class="admin-table-toolbar">
        <el-input v-model="search" :placeholder="$t('agent.searchAgent')" style="width: 300px" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    <!-- Mobile Card List -->
    <div class="mobile-card-list" v-if="isMobile">
      <el-card v-for="item in filteredData" :key="item.id" shadow="hover">
        <div class="mobile-card-item">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ item.name }}</span>
            <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enabled') : t('common.disabled') }}</el-tag>
          </div>
          <div class="mobile-card-meta">
            <span>{{ $t('common.model') }}: {{ item.modelId || '-' }}</span>
            <el-tag v-if="item.workflowMode" :type="workflowModeTagType(item.workflowMode)" size="small" style="margin-left: 8px">{{ item.workflowMode }}</el-tag>
            <el-tag v-else-if="item.subAgents" type="warning" size="small" style="margin-left: 8px">Multi-Agent</el-tag>
          </div>
          <div v-if="item.description" class="mobile-card-desc">{{ item.description }}</div>
          <div class="mobile-card-actions">
            <el-button size="small" @click="openDialog(item)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" @click="deleteAgent(item)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </el-card>
      <div v-if="filteredData.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
    </div>

          <el-table :data="filteredData" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="220" />
        <el-table-column prop="name" :label="$t('common.name')" width="160" />
        <el-table-column prop="modelId" :label="$t('common.model')" width="140" />
        <el-table-column :label="$t('agent.workflowMode')" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.workflowMode" :type="workflowModeTagType(row.workflowMode)" size="small">{{ row.workflowMode }}</el-tag>
            <template v-else-if="parsedSubAgents(row).length > 0">
              <el-tag type="warning" size="small">{{ parsedSubAgents(row).length }} sub-agents</el-tag>
            </template>
            <span v-else style="color: #909399; font-size: 12px;">Single</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('common.description')" show-overflow-tooltip />
        <el-table-column :label="$t('common.actions')" fixed="right" width="240">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggle(row)" style="margin-right: 8px" />
            <el-button link type="primary" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('agent.editAgent') : t('agent.newAgent')" width="780" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <!-- ========== Agent Type Selector ========== -->
        <div class="agent-type-selector">
          <div
            class="agent-type-card"
            :class="{ 'agent-type-card--active': agentType === 'single' }"
            @click="agentType = 'single'"
          >
            <div class="agent-type-card__icon">
              <el-icon :size="28"><Monitor /></el-icon>
            </div>
            <div class="agent-type-card__content">
              <div class="agent-type-card__title">{{ $t('agent.agentTypeSingle') }}</div>
              <div class="agent-type-card__desc">{{ $t('agent.agentTypeSingleDesc') }}</div>
            </div>
          </div>
          <div
            class="agent-type-card"
            :class="{ 'agent-type-card--active': agentType === 'multi' }"
            @click="agentType = 'multi'"
          >
            <div class="agent-type-card__icon">
              <el-icon :size="28"><Connection /></el-icon>
            </div>
            <div class="agent-type-card__content">
              <div class="agent-type-card__title">{{ $t('agent.agentTypeMulti') }}</div>
              <div class="agent-type-card__desc">{{ $t('agent.agentTypeMultiDesc') }}</div>
            </div>
          </div>
        </div>

        <el-form-item :label="$t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="agentType === 'multi' ? $t('agent.agentTypeRootModel') : $t('common.model')" prop="modelId">
          <el-select v-model="form.modelId" :placeholder="$t('agent.selectModel')" filterable style="width: 100%">
            <el-option v-for="m in llmModels" :key="m.id" :label="m.displayName || m.modelName" :value="m.id">
              <span>{{ m.displayName || m.modelName }}</span>
              <span style="color: #999; font-size: 12px; margin-left: 8px">({{ m.providerName || m.providerId }})</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="agentType === 'multi' ? $t('agent.agentTypeRootPrompt') : $t('agent.systemPrompt')" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="6" :placeholder="$t('agent.systemPromptPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('agent.mcpServers')">
          <el-select v-model="form.mcpServerIds" multiple :placeholder="$t('agent.bindMcp')" style="width: 100%">
            <el-option v-for="s in mcpServers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('agent.skills')">
          <el-select v-model="form.skillIds" multiple :placeholder="$t('agent.bindSkills')" style="width: 100%">
            <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>

        <!-- ========== Workflow V3 Configuration (only when Multi Agent) ========== -->
        <template v-if="agentType === 'multi'">
        <el-divider content-position="left">{{ $t('agent.workflowConfig') }}</el-divider>

        <!-- Workflow Mode Selector -->
        <el-form-item :label="$t('agent.workflowMode')" prop="workflowMode" :rules="[{ required: true, message: t('common.required'), trigger: 'change' }]">
          <el-select v-model="form.workflowMode" :placeholder="$t('agent.workflowMode')" style="width: 100%">
            <el-option value="pipeline" :label="$t('agent.workflowModePipeline')" />
            <el-option value="parallel" :label="$t('agent.workflowModeParallel')" />
            <el-option value="router" :label="$t('agent.workflowModeRouter')" />
            <el-option value="supervisor" :label="$t('agent.workflowModeSupervisor')" />
            <el-option value="handoff" :label="$t('agent.handoff')" />
          </el-select>
        </el-form-item>

        <!-- Node List (shown when a workflow mode is selected) -->
        <template v-if="form.workflowMode">
          <el-form-item :label="$t('agent.nodeList')">
            <div class="workflow-nodes-container">
              <div v-for="(node, idx) in form.workflowNodes" :key="idx" class="workflow-node-card">
                <div class="workflow-node-header">
                  <span class="workflow-node-index">{{ idx + 1 }}</span>
                  <span class="workflow-node-title">{{ node.displayName || node.name || ('Node ' + (idx + 1)) }}</span>
                  <div class="workflow-node-actions">
                    <el-button :icon="ArrowUp" circle size="small" :disabled="idx === 0" @click="moveNodeUp(idx)" />
                    <el-button :icon="ArrowDown" circle size="small" :disabled="idx === form.workflowNodes.length - 1" @click="moveNodeDown(idx)" />
                    <el-button type="danger" :icon="Delete" circle size="small" @click="removeNode(idx)" />
                  </div>
                </div>

                <div class="workflow-node-body">
                  <!-- Node definition fields -->
                  <template>
                    <el-form-item :label="$t('agent.nodeName')" label-width="100px" style="margin-bottom: 8px">
                      <el-input v-model="node.name" :placeholder="$t('agent.nodeNamePlaceholder')" />
                    </el-form-item>
                    <el-form-item :label="$t('agent.nodeDisplayName')" label-width="100px" style="margin-bottom: 8px">
                      <el-input v-model="node.displayName" :placeholder="$t('agent.nodeDisplayNamePlaceholder')" />
                    </el-form-item>
                    <el-form-item :label="$t('agent.nodePrompt')" label-width="100px" style="margin-bottom: 8px">
                      <el-input v-model="node.systemPrompt" type="textarea" :rows="3" :placeholder="$t('agent.nodePromptPlaceholder')" />
                    </el-form-item>
                  </template>

                  <!-- Description (always visible, used by Router/Handoff/Supervisor) -->
                  <el-form-item :label="$t('agent.nodeDescription')" label-width="100px" style="margin-bottom: 8px">
                    <el-input v-model="node.description" type="textarea" :rows="2" :placeholder="$t('agent.nodeDescriptionPlaceholder')" />
                  </el-form-item>

                  <!-- Model (optional override) -->
                  <el-form-item :label="$t('agent.nodeModel')" label-width="100px" style="margin-bottom: 8px">
                    <el-select v-model="node.modelId" :placeholder="$t('agent.nodeModelInherit')" clearable style="width: 100%">
                      <el-option v-for="m in llmModels" :key="m.id" :label="m.displayName || m.modelName" :value="m.id" />
                    </el-select>
                  </el-form-item>

                  <!-- MCP Servers -->
                  <el-form-item :label="$t('agent.nodeMcp')" label-width="100px" style="margin-bottom: 8px">
                    <el-select v-model="node.mcpServerIds" multiple :placeholder="$t('agent.bindMcp')" style="width: 100%">
                      <el-option v-for="s in mcpServers" :key="s.id" :label="s.name" :value="s.id" />
                    </el-select>
                  </el-form-item>

                  <!-- Skills -->
                  <el-form-item :label="$t('agent.nodeSkills')" label-width="100px" style="margin-bottom: 8px">
                    <el-select v-model="node.skillIds" multiple :placeholder="$t('agent.bindSkills')" style="width: 100%">
                      <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.id" />
                    </el-select>
                  </el-form-item>
                </div>
              </div>

              <el-button type="primary" plain @click="addNode" style="width: 100%">
                <el-icon><Plus /></el-icon> {{ $t('agent.addNode') }}
              </el-button>
            </div>
          </el-form-item>

          <!-- Mode-Specific Configuration -->
          <el-form-item :label="$t('agent.modeConfig')">
            <div class="mode-config-section">
              <!-- Pipeline Config -->
              <template v-if="form.workflowMode === 'pipeline'">
                <div class="mode-config-item">
                  <span class="mode-config-label">{{ $t('agent.pipelinePassthrough') }}:</span>
                  <el-radio-group v-model="form.pipelineConfig.passthroughMode">
                    <el-radio value="append">{{ $t('agent.pipelinePassthroughAppend') }}</el-radio>
                    <el-radio value="replace">{{ $t('agent.pipelinePassthroughReplace') }}</el-radio>
                  </el-radio-group>
                </div>
              </template>

              <!-- Parallel Config -->
              <template v-if="form.workflowMode === 'parallel'">
                <div class="mode-config-item">
                  <span class="mode-config-label">{{ $t('agent.parallelMergeStrategy') }}:</span>
                  <el-radio-group v-model="form.parallelConfig.mergeStrategy">
                    <el-radio value="concat">{{ $t('agent.parallelMergeConcat') }}</el-radio>
                    <el-radio value="summarize">{{ $t('agent.parallelMergeSummarize') }}</el-radio>
                  </el-radio-group>
                </div>
                <div class="mode-config-item">
                  <span class="mode-config-label">{{ $t('agent.parallelMaxConcurrent') }}:</span>
                  <el-input-number v-model="form.parallelConfig.maxConcurrent" :min="1" :max="20" :step="1" />
                </div>
              </template>

              <!-- Router Config -->
              <template v-if="form.workflowMode === 'router'">
                <div class="mode-config-item">
                  <el-switch v-model="form.routerConfig.allowFallback" />
                  <span style="margin-left: 8px; font-size: 13px; color: #606266">{{ $t('agent.routerAllowFallbackDesc') }}</span>
                </div>
              </template>

              <!-- Supervisor Config -->
              <template v-if="form.workflowMode === 'supervisor'">
                <div class="mode-config-item">
                  <span class="mode-config-label">{{ $t('agent.supervisorMaxIterations') }}:</span>
                  <el-input-number v-model="form.supervisorConfig.maxIterations" :min="1" :max="50" :step="1" />
                </div>
                <div class="mode-config-item" style="flex-direction: column; align-items: flex-start; gap: 4px">
                  <span class="mode-config-label">{{ $t('agent.supervisorPlannerPrompt') }}:</span>
                  <el-input v-model="form.supervisorConfig.plannerPrompt" type="textarea" :rows="2" :placeholder="$t('agent.supervisorPlannerPromptPlaceholder')" style="width: 100%" />
                </div>
                <div class="mode-config-item" style="flex-direction: column; align-items: flex-start; gap: 4px">
                  <span class="mode-config-label">{{ $t('agent.supervisorReviewerPrompt') }}:</span>
                  <el-input v-model="form.supervisorConfig.reviewerPrompt" type="textarea" :rows="2" :placeholder="$t('agent.supervisorReviewerPromptPlaceholder')" style="width: 100%" />
                </div>
              </template>

              <!-- Handoff Config -->
              <template v-if="form.workflowMode === 'handoff'">
                <div class="mode-config-item">
                  <el-switch v-model="form.handoffConfig.autoReturn" />
                  <span style="margin-left: 8px; font-size: 13px; color: #606266">{{ $t('agent.handoffAutoReturnDesc') }}</span>
                </div>
              </template>
            </div>
          </el-form-item>
        </template>
        </template>

        <!-- Legacy Sub-Agents Configuration (only when Single Agent) -->
        <template v-if="agentType === 'single'">
          <el-divider content-position="left">{{ $t('agent.subAgents') }}</el-divider>
          <div v-for="(sub, idx) in form.subAgentList" :key="idx" class="sub-agent-card">
            <div class="sub-agent-header">
              <span class="sub-agent-title">{{ sub.displayName || sub.name || ('Sub Agent ' + (idx + 1)) }}</span>
              <el-button type="danger" :icon="Delete" circle size="small" @click="form.subAgentList.splice(idx, 1)" />
            </div>
            <el-form-item :label="$t('agent.subAgentName')" label-width="80px" style="margin-bottom: 8px">
              <el-input v-model="sub.name" :placeholder="$t('agent.subAgentName')" />
            </el-form-item>
            <el-form-item :label="$t('agent.subAgentDisplayName')" label-width="80px" style="margin-bottom: 8px">
              <el-input v-model="sub.displayName" :placeholder="$t('agent.subAgentDisplayName')" />
            </el-form-item>
            <el-form-item :label="$t('agent.subAgentDescription')" label-width="80px" style="margin-bottom: 8px">
              <el-input v-model="sub.description" type="textarea" :rows="2" :placeholder="$t('agent.subAgentDescriptionPlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('agent.subAgentPrompt')" label-width="80px" style="margin-bottom: 8px">
              <el-input v-model="sub.systemPrompt" type="textarea" :rows="3" :placeholder="$t('agent.systemPromptPlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('agent.subAgentModel')" label-width="80px" style="margin-bottom: 8px">
              <el-select v-model="sub.modelId" :placeholder="$t('agent.subAgentModelInherit')" clearable style="width: 100%">
                <el-option v-for="m in llmModels" :key="m.id" :label="m.displayName || m.modelName" :value="m.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('agent.mcpServers')" label-width="80px" style="margin-bottom: 8px">
              <el-select v-model="sub.mcpServerIds" multiple :placeholder="$t('agent.bindMcp')" style="width: 100%">
                <el-option v-for="s in mcpServers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('agent.skills')" label-width="80px" style="margin-bottom: 8px">
              <el-select v-model="sub.skillIds" multiple :placeholder="$t('agent.bindSkills')" style="width: 100%">
                <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </div>
          <el-button type="primary" plain @click="addSubAgent"><el-icon><Plus /></el-icon> {{ $t('agent.addSubAgent') }}</el-button>
        </template>

        <!-- Advanced Settings (collapsed) -->
        <el-divider>
          <el-button text @click="showAdvanced = !showAdvanced" style="font-size: 13px; color: #909399">
            {{ showAdvanced ? t('common.collapseAdvanced') : t('common.expandAdvanced') }}
            <el-icon style="margin-left: 4px"><component :is="showAdvanced ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
          </el-button>
        </el-divider>
        <template v-if="showAdvanced">
          <el-form-item :label="$t('agent.maxToolCalls')">
            <el-input-number v-model="form.maxToolCalls" :min="1" :max="200" :step="1" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.maxToolCallsDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('agent.compressionThreshold')">
            <el-input-number v-model="form.compressionThreshold" :min="5" :max="100" :step="1" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.compressionThresholdDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('agent.keepRounds')">
            <el-input-number v-model="form.compressionKeepRounds" :min="2" :max="50" :step="1" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.keepRoundsDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('agent.contextThreshold')">
            <el-input-number v-model="form.contextUsageThreshold" :min="0.1" :max="0.99" :step="0.05" :precision="2" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.contextThresholdDesc') }}</span>
          </el-form-item>
          <el-divider content-position="left">{{ $t('agent.sandboxConfig') }}</el-divider>
          <el-form-item :label="$t('agent.enableSandbox')">
            <el-switch v-model="form.sandboxEnabled" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.enableSandboxDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('sandbox.providers')" v-if="form.sandboxEnabled">
            <el-select v-model="form.sandboxProviderId" style="width: 100%" :placeholder="$t('agent.selectSandboxProvider')" clearable>
              <el-option v-for="p in sandboxProviders" :key="p.id" :label="p.name + ' (' + p.type + ')'" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('agent.sandboxMode')" v-if="form.sandboxEnabled">
            <el-radio-group v-model="form.sandboxMode">
              <el-radio value="STATELESS">{{ $t('agent.statelessMode') }}</el-radio>
              <el-radio value="SESSION" :disabled="!isSessionModeAllowed">{{ $t('agent.sessionMode') }}{{ !isSessionModeAllowed ? t('agent.sessionModeNote') : '' }}</el-radio>
            </el-radio-group>
            <div v-if="!isSessionModeAllowed && form.sandboxMode === 'SESSION'" style="color: #e6a23c; font-size: 12px; margin-top: 4px;">
              {{ $t('agent.sessionModeWarning') }}
            </div>
          </el-form-item>
          <el-form-item :label="$t('agent.execTimeout')" v-if="form.sandboxEnabled">
            <el-input-number v-model="form.sandboxTimeout" :min="5" :max="300" :step="5" />
          </el-form-item>
        </template>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { SetUp, Plus, Search, ArrowUp, ArrowDown, Delete, Monitor, Connection } from '@element-plus/icons-vue'
import { getAgents, createAgent, updateAgent, deleteAgent, getMcpServers, getSkills, getLlmModels, getSandboxProviders } from '@/api/admin'
import '@/assets/admin.css'
import { useMobile } from '@/composables/useMobile'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const tableData = ref<any[]>([])
const mcpServers = ref<any[]>([])
const skills = ref<any[]>([])
const llmModels = ref<any[]>([])
const sandboxProviders = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editId = ref('')
const { isMobile } = useMobile()
const search = ref('')
const formRef = ref<FormInstance>()
const agentType = ref<'single' | 'multi'>('single')

// ===== Workflow V3 Types =====
interface WorkflowNode {
  id: string
  name: string
  displayName: string
  description: string
  systemPrompt: string
  modelId: string
  mcpServerIds: string[]
  skillIds: string[]
  refAgentId: string
}

interface SubAgent {
  name: string
  displayName: string
  description: string
  systemPrompt: string
  modelId: string
  mcpServerIds: string[]
  skillIds: string[]
}

interface PipelineConfig {
  passthroughMode: 'append' | 'replace'
}

interface ParallelConfig {
  mergeStrategy: 'concat' | 'summarize'
  maxConcurrent: number
}

interface RouterConfig {
  allowFallback: boolean
}

interface SupervisorConfig {
  maxIterations: number
  plannerPrompt: string
  reviewerPrompt: string
}

interface HandoffConfig {
  autoReturn: boolean
}

// ===== Form Default =====
const defaultPipelineConfig: PipelineConfig = { passthroughMode: 'append' }
const defaultParallelConfig: ParallelConfig = { mergeStrategy: 'concat', maxConcurrent: 5 }
const defaultRouterConfig: RouterConfig = { allowFallback: true }
const defaultSupervisorConfig: SupervisorConfig = { maxIterations: 5, plannerPrompt: '', reviewerPrompt: '' }
const defaultHandoffConfig: HandoffConfig = { autoReturn: false }

const defaultForm = {
  name: '', modelId: '', description: '', systemPrompt: '',
  subAgentList: [] as SubAgent[],
  mcpServerIds: [] as string[], skillIds: [] as string[],
  enabled: true, maxToolCalls: 50, compressionThreshold: 20,
  compressionKeepRounds: 6, contextUsageThreshold: 0.75,
  sandboxEnabled: false, sandboxBackend: 'LOCAL', sandboxProviderId: '',
  sandboxMode: 'STATELESS', sandboxTimeout: 30,
  // Workflow V3 fields
  workflowMode: '' as string,
  workflowNodes: [] as WorkflowNode[],
  pipelineConfig: { ...defaultPipelineConfig },
  parallelConfig: { ...defaultParallelConfig },
  routerConfig: { ...defaultRouterConfig },
  supervisorConfig: { ...defaultSupervisorConfig },
  handoffConfig: { ...defaultHandoffConfig },
}
const form = reactive({ ...defaultForm })
const showAdvanced = ref(false)
const rules = {
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  modelId: [{ required: true, message: t('common.required'), trigger: 'change' }],
  systemPrompt: [{ required: true, message: t('common.required'), trigger: 'blur' }]
}

// ===== Workflow Helpers =====
let nodeCounter = 0

const generateNodeId = (): string => {
  nodeCounter++
  return 'node_' + Date.now() + '_' + nodeCounter
}

const createEmptyNode = (): WorkflowNode => ({
  id: generateNodeId(),
  name: '',
  displayName: '',
  description: '',
  systemPrompt: '',
  modelId: '',
  mcpServerIds: [],
  skillIds: [],
  refAgentId: ''
})

const addNode = () => {
  form.workflowNodes.push(createEmptyNode())
}

const removeNode = (idx: number) => {
  form.workflowNodes.splice(idx, 1)
}

const moveNodeUp = (idx: number) => {
  if (idx <= 0) return
  const nodes = form.workflowNodes
  const temp = nodes[idx]
  nodes[idx] = nodes[idx - 1]
  nodes[idx - 1] = temp
}

const moveNodeDown = (idx: number) => {
  const nodes = form.workflowNodes
  if (idx >= nodes.length - 1) return
  const temp = nodes[idx]
  nodes[idx] = nodes[idx + 1]
  nodes[idx + 1] = temp
}

const workflowModeTagType = (mode: string): string => {
  const map: Record<string, string> = {
    pipeline: '',
    parallel: 'success',
    router: 'warning',
    supervisor: 'danger',
    handoff: 'info'
  }
  return map[mode] || ''
}

// Build the workflow JSON payload for the backend
const buildWorkflowPayload = (): any => {
  const mode = form.workflowMode
  if (!mode) return null

  const nodes = form.workflowNodes
    .filter(n => !!n.name)
    .map((n, idx) => {
      const node: any = {
        id: n.id || ('node_' + (idx + 1)),
        name: n.name,
        display_name: n.displayName,
        description: n.description,
        system_prompt: n.systemPrompt,
        model_id: n.modelId || null,
        mcp_server_ids: n.mcpServerIds || [],
        skill_ids: n.skillIds || [],
      }

      return node
    })

  const workflow: any = {
    mode,
    nodes,
  }

  // Add mode-specific config
  if (mode === 'pipeline') {
    workflow.pipeline_config = { passthrough_mode: form.pipelineConfig.passthroughMode }
  } else if (mode === 'parallel') {
    workflow.parallel_config = {
      merge_strategy: form.parallelConfig.mergeStrategy,
      max_concurrent: form.parallelConfig.maxConcurrent,
    }
  } else if (mode === 'router') {
    workflow.router_config = { allow_fallback: form.routerConfig.allowFallback }
  } else if (mode === 'supervisor') {
    const supervisorCfg: any = { max_iterations: form.supervisorConfig.maxIterations }
    if (form.supervisorConfig.plannerPrompt) {
      supervisorCfg.planner_prompt = form.supervisorConfig.plannerPrompt
    }
    if (form.supervisorConfig.reviewerPrompt) {
      supervisorCfg.reviewer_prompt = form.supervisorConfig.reviewerPrompt
    }
    workflow.supervisor_config = supervisorCfg
  } else if (mode === 'handoff') {
    workflow.handoff_config = { auto_return: form.handoffConfig.autoReturn }
  }

  return workflow
}

// Parse workflow JSON from backend into form data
const parseWorkflowIntoForm = (workflowJson: string, mode: string) => {
  if (!workflowJson || !mode) return

  try {
    const wf = JSON.parse(workflowJson)
    form.workflowNodes = (wf.nodes || []).map((n: any, idx: number) => ({
      id: n.id || ('node_' + (idx + 1)),
      name: n.name || '',
      displayName: n.display_name || '',
      description: n.description || '',
      systemPrompt: n.system_prompt || '',
      modelId: n.model_id || '',
      mcpServerIds: n.mcp_server_ids || [],
      skillIds: n.skill_ids || [],
      refAgentId: '',
    }))

    // Parse mode-specific config
    if (mode === 'pipeline' && wf.pipeline_config) {
      form.pipelineConfig.passthroughMode = wf.pipeline_config.passthrough_mode || 'append'
    } else if (mode === 'parallel' && wf.parallel_config) {
      form.parallelConfig.mergeStrategy = wf.parallel_config.merge_strategy || 'concat'
      form.parallelConfig.maxConcurrent = wf.parallel_config.max_concurrent || 5
    } else if (mode === 'router' && wf.router_config) {
      form.routerConfig.allowFallback = wf.router_config.allow_fallback !== false
    } else if (mode === 'supervisor' && wf.supervisor_config) {
      form.supervisorConfig.maxIterations = wf.supervisor_config.max_iterations || 5
      form.supervisorConfig.plannerPrompt = wf.supervisor_config.planner_prompt || ''
      form.supervisorConfig.reviewerPrompt = wf.supervisor_config.reviewer_prompt || ''
    } else if (mode === 'handoff' && wf.handoff_config) {
      form.handoffConfig.autoReturn = wf.handoff_config.auto_return || false
    }
  } catch {
    form.workflowNodes = []
  }
}

// ===== Legacy helpers =====
const parsedSubAgents = (row: any): any[] => {
  if (!row.subAgents) return []
  try { return JSON.parse(row.subAgents) } catch { return [] }
}

const addSubAgent = () => {
  form.subAgentList.push({
    name: '', displayName: '', description: '', systemPrompt: '',
    modelId: '', mcpServerIds: [], skillIds: []
  })
}

// ===== Data Loading =====
const loadData = async () => {
  loading.value = true
  try {
    const [agentsRes, mcpRes, skillRes, modelRes, providerRes]: any[] = await Promise.all([getAgents(), getMcpServers(), getSkills(), getLlmModels(), getSandboxProviders()])
    tableData.value = Array.isArray(agentsRes?.data) ? agentsRes.data : Array.isArray(agentsRes?.data?.list) ? agentsRes.data.list : Array.isArray(agentsRes) ? agentsRes : []
    mcpServers.value = Array.isArray(mcpRes?.data) ? mcpRes.data : Array.isArray(mcpRes?.data?.list) ? mcpRes.data.list : Array.isArray(mcpRes) ? mcpRes : []
    skills.value = Array.isArray(skillRes?.data) ? skillRes.data : Array.isArray(skillRes?.data?.list) ? skillRes.data.list : Array.isArray(skillRes) ? skillRes : []
    llmModels.value = Array.isArray(modelRes?.data) ? modelRes.data : Array.isArray(modelRes?.data?.list) ? modelRes.data.list : Array.isArray(modelRes) ? modelRes : []
    sandboxProviders.value = Array.isArray(providerRes?.data) ? providerRes.data : []
  } catch {} finally { loading.value = false }
}

const filteredData = computed(() => {
  if (!search.value) return tableData.value
  const kw = search.value.toLowerCase()
  return tableData.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.description?.toLowerCase().includes(kw)
  )
})

const isSessionModeAllowed = computed(() => {
  if (form.sandboxProviderId) {
    const provider = sandboxProviders.value.find((p: any) => p.id === form.sandboxProviderId)
    return provider && provider.type === 'E2B'
  }
  return form.sandboxBackend === 'E2B'
})

watch(isSessionModeAllowed, (allowed) => {
  if (!allowed && form.sandboxMode === 'SESSION') form.sandboxMode = 'STATELESS'
})

// Reset workflow nodes when mode changes
let skipWorkflowWatch = false

watch(() => form.workflowMode, (newMode, oldMode) => {
  // Skip reset during programmatic form load (openDialog)
  if (skipWorkflowWatch) return
  // Only reset nodes if mode actually changed (not during initial load)
  if (oldMode !== undefined && newMode !== oldMode) {
    // Keep nodes if both modes need them, but reset config
    if (!newMode) {
      form.workflowNodes = []
    }
    // Reset mode-specific configs to defaults
    form.pipelineConfig = { ...defaultPipelineConfig }
    form.parallelConfig = { ...defaultParallelConfig }
    form.routerConfig = { ...defaultRouterConfig }
    form.supervisorConfig = { ...defaultSupervisorConfig }
    form.handoffConfig = { ...defaultHandoffConfig }
  }
})

// Watch agentType to clear opposing section's data
watch(agentType, (newType, oldType) => {
  if (oldType === undefined || newType === oldType) return
  if (newType === 'single') {
    // Clear workflow fields
    form.workflowMode = ''
    form.workflowNodes = []
    form.pipelineConfig = { ...defaultPipelineConfig }
    form.parallelConfig = { ...defaultParallelConfig }
    form.routerConfig = { ...defaultRouterConfig }
    form.supervisorConfig = { ...defaultSupervisorConfig }
    form.handoffConfig = { ...defaultHandoffConfig }
  } else {
    // Clear legacy sub-agents
    form.subAgentList = []
  }
})

const openDialog = (row?: any) => {
  skipWorkflowWatch = true
  isEdit.value = !!row
  if (row) {
    editId.value = row.id
    agentType.value = row.workflowMode ? 'multi' : 'single'
    let subAgentList: SubAgent[] = []
    if (row.subAgents) {
      try { subAgentList = JSON.parse(row.subAgents) } catch { subAgentList = [] }
    }
    Object.assign(form, {
      name: row.name, modelId: row.modelId, description: row.description || '',
      systemPrompt: row.systemPrompt || '',
      subAgentList,
      mcpServerIds: row.mcpServerIds || [], skillIds: row.skillIds || [],
      enabled: row.enabled !== false, maxToolCalls: row.maxToolCalls || 50,
      compressionThreshold: row.compressionThreshold || 20,
      compressionKeepRounds: row.compressionKeepRounds || 6,
      contextUsageThreshold: row.contextUsageThreshold || 0.75,
      sandboxEnabled: row.sandboxEnabled || false, sandboxBackend: row.sandboxBackend || 'LOCAL',
      sandboxProviderId: row.sandboxProviderId || '', sandboxMode: row.sandboxMode || 'STATELESS',
      sandboxTimeout: row.sandboxTimeout || 30,
      // Workflow V3
      workflowMode: row.workflowMode || '',
      workflowNodes: [] as WorkflowNode[],
      pipelineConfig: { ...defaultPipelineConfig },
      parallelConfig: { ...defaultParallelConfig },
      routerConfig: { ...defaultRouterConfig },
      supervisorConfig: { ...defaultSupervisorConfig },
      handoffConfig: { ...defaultHandoffConfig },
    })
    // Parse existing workflow data
    if (row.workflowMode && row.workflow) {
      parseWorkflowIntoForm(row.workflow, row.workflowMode)
    }
  } else {
    editId.value = ''
    agentType.value = 'single'
    Object.assign(form, {
      ...defaultForm,
      workflowNodes: [] as WorkflowNode[],
      pipelineConfig: { ...defaultPipelineConfig },
      parallelConfig: { ...defaultParallelConfig },
      routerConfig: { ...defaultRouterConfig },
      supervisorConfig: { ...defaultSupervisorConfig },
      handoffConfig: { ...defaultHandoffConfig },
    })
  }
  dialogVisible.value = true
  // Reset skip flag after Vue's reactive updates have settled
  setTimeout(() => { skipWorkflowWatch = false }, 0)
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: any = { ...form }

    // Enforce agentType rules
    if (agentType.value === 'single') {
      payload.workflowMode = null
      payload.workflow = null
    }

    // Build workflow payload for V3
    if (agentType.value === 'multi' && form.workflowMode) {
      payload.workflowMode = form.workflowMode
      const workflow = buildWorkflowPayload()
      payload.workflow = workflow ? JSON.stringify(workflow) : null
      // Clear legacy sub-agents when using workflow
      payload.subAgents = null
    } else {
      payload.workflowMode = null
      payload.workflow = null
      // Serialize sub-agents to JSON (legacy)
      if (form.subAgentList.length > 0) {
        payload.subAgents = JSON.stringify(form.subAgentList.filter(s => s.name))
      } else {
        payload.subAgents = null
      }
    }

    // Remove internal form fields not expected by backend
    delete payload.subAgentList
    delete payload.workflowNodes
    delete payload.pipelineConfig
    delete payload.parallelConfig
    delete payload.routerConfig
    delete payload.supervisorConfig
    delete payload.handoffConfig

    if (isEdit.value) { await updateAgent(editId.value, payload) } else { await createAgent(payload) }
    ElMessage.success(isEdit.value ? t('common.updateSuccess') : t('common.createSuccess'))
    dialogVisible.value = false
    loadData()
  } catch {} finally { saving.value = false }
}

const handleDelete = async (id: string) => {
  await deleteAgent(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

const handleToggle = async (row: any) => {
  try {
    await updateAgent(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? t('common.enabled') : t('agent.stopped'))
  } catch {
    row.enabled = !row.enabled
  }
}

onMounted(loadData)
</script>

<style scoped>
.sub-agent-card {
  background: var(--el-fill-color-light, #f5f7fa);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
}
.sub-agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.sub-agent-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-color-primary);
}

/* ===== Workflow V3 Styles ===== */
.workflow-nodes-container {
  width: 100%;
}
.workflow-node-card {
  background: var(--el-fill-color-light, #f5f7fa);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}
.workflow-node-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--el-fill-color, #f0f2f5);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}
.workflow-node-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.workflow-node-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workflow-node-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.workflow-node-body {
  padding: 12px 16px;
}
.mode-config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}
.mode-config-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mode-config-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  min-width: 100px;
}

/* ===== Agent Type Selector ===== */
.agent-type-selector {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
.agent-type-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border: 2px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--el-fill-color-blank, #fff);
  user-select: none;
}
.agent-type-card:hover {
  border-color: var(--el-color-primary-light-5, #b3d8ff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.agent-type-card--active {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
  box-shadow: 0 0 0 1px var(--el-color-primary, #409eff);
}
.agent-type-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: var(--el-fill-color, #f0f2f5);
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
  transition: all 0.2s ease;
}
.agent-type-card--active .agent-type-card__icon {
  background: var(--el-color-primary-light-8, #d9ecff);
  color: var(--el-color-primary, #409eff);
}
.agent-type-card__content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.agent-type-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.agent-type-card__desc {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
