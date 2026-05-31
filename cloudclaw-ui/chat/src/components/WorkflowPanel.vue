<template>
  <div v-if="workflowState" class="workflow-panel" :class="{ dark: isDark }">
    <div class="workflow-panel-header">
      <el-icon class="workflow-panel-icon"><SetUp /></el-icon>
      <span class="workflow-panel-title">
        <template v-if="workflowState.mode === 'pipeline'">{{ t('chat.workflowPipeline').replace(': ', '') }}</template>
        <template v-else-if="workflowState.mode === 'parallel'">{{ t('chat.workflowParallel').replace(': ', '') }}</template>
        <template v-else-if="workflowState.mode === 'router'">{{ t('chat.workflowRouter').replace(': ', '').replace(':','') }}</template>
        <template v-else-if="workflowState.mode === 'supervisor'">{{ t('chat.workflowSupervisor').replace(': ', '') }}</template>
        <template v-else-if="workflowState.mode === 'handoff'">{{ t('chat.workflowHandoff').replace(': ', '').replace(':','') }}</template>
      </span>
      <span class="workflow-panel-mode-tag">{{ workflowState.mode }}</span>
    </div>
    <div class="workflow-panel-body">
      <!-- Pipeline -->
      <template v-if="workflowState.mode === 'pipeline'">
        <div class="workflow-steps-row">
          <template v-for="(step, si) in workflowState.steps" :key="si">
            <div v-if="si > 0" class="workflow-step-arrow"><el-icon><Right /></el-icon></div>
            <div class="workflow-step-item" :class="'step-' + step.status">
              <span v-if="step.status === 'running'" class="step-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
              <span v-else-if="step.status === 'done'" class="step-icon"><el-icon style="color: var(--cc-success, #34c759)"><Check /></el-icon></span>
              <span v-else class="step-icon"><el-icon><Clock /></el-icon></span>
              <span class="step-label">{{ step.name }}</span>
            </div>
          </template>
        </div>
      </template>
      <!-- Parallel -->
      <template v-else-if="workflowState.mode === 'parallel'">
        <div class="workflow-parallel-grid">
          <div v-for="(step, si) in workflowState.steps" :key="si" class="workflow-parallel-node" :class="'step-' + step.status">
            <span v-if="step.status === 'running'" class="step-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
            <span v-else-if="step.status === 'done'" class="step-icon"><el-icon style="color: var(--cc-success, #34c759)"><Check /></el-icon></span>
            <span v-else class="step-icon"><el-icon><Clock /></el-icon></span>
            <span class="step-label">{{ step.name }}</span>
          </div>
        </div>
        <div v-if="workflowState.mergeStatus === 'merging'" class="workflow-merge-row">
          <el-icon class="is-loading" style="color: var(--cc-warning, #ff9500)"><Loading /></el-icon>
          <span class="merge-label">{{ t('chat.workflowParallelMerging') }}</span>
        </div>
      </template>
      <!-- Router -->
      <template v-else-if="workflowState.mode === 'router'">
        <div class="workflow-router-info">
          <el-tag type="primary" effect="plain" size="small" class="router-target-tag">
            <el-icon style="margin-right:4px"><Right /></el-icon>{{ workflowState.activeNode }}
          </el-tag>
          <span v-if="workflowState.reason" class="workflow-router-reason">{{ workflowState.reason }}</span>
        </div>
      </template>
      <!-- Supervisor -->
      <template v-else-if="workflowState.mode === 'supervisor'">
        <div v-if="workflowState.steps.length > 0" class="workflow-supervisor-steps">
          <div v-for="(step, si) in workflowState.steps" :key="si" class="workflow-supervisor-step" :class="'step-' + step.status">
            <span v-if="step.status === 'running'" class="step-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
            <span v-else-if="step.status === 'done'" class="step-icon"><el-icon style="color: var(--cc-success, #34c759)"><Check /></el-icon></span>
            <span v-else class="step-icon"><el-icon><Clock /></el-icon></span>
            <span class="step-label">{{ step.name }}</span>
          </div>
        </div>
        <div v-if="workflowState.activeNode" class="workflow-supervisor-delegate">
          <el-tag type="warning" effect="plain" size="small">
            <el-icon style="margin-right:4px"><Promotion /></el-icon>{{ workflowState.activeNode }}
          </el-tag>
        </div>
        <div v-if="workflowState.supervisorAction" class="workflow-supervisor-action">
          <el-icon style="margin-right:4px"><Monitor /></el-icon>{{ workflowState.supervisorAction }}
        </div>
      </template>
      <!-- Handoff -->
      <template v-else-if="workflowState.mode === 'handoff'">
        <div class="workflow-handoff-info">
          <el-tag type="success" effect="plain" size="small">
            <el-icon style="margin-right:4px"><Promotion /></el-icon>{{ workflowState.activeNode }}
          </el-tag>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SetUp, Loading, Check, Clock, Right, Promotion, Monitor } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { inject, ref } from 'vue'

interface WorkflowStepStatus {
  name: string
  status: 'pending' | 'running' | 'done'
}

interface WorkflowState {
  mode: string
  steps: WorkflowStepStatus[]
  activeNode: string
  supervisorAction: string
  mergeStatus: string
  reason: string
}

defineProps<{
  workflowState: WorkflowState | null
}>()

const { t } = useI18n()
const isDark = inject('isDark', ref(false))
</script>

<style scoped>
.workflow-panel {
  margin-bottom: 10px;
  background: var(--cc-bg-tertiary, #eef0f4);
  border-radius: 10px;
  border: 1px solid var(--cc-border, #e8eaed);
  overflow: hidden;
  font-size: 13px;
}
.dark .workflow-panel {
  background: #262627;
}
.workflow-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(51,112,255,0.06);
  border-bottom: 1px solid var(--cc-border, #e8eaed);
}
.dark .workflow-panel-header {
  background: rgba(51,112,255,0.12);
}
.workflow-panel-icon {
  color: var(--cc-accent, #3370ff);
  font-size: 16px;
}
.workflow-panel-title {
  font-weight: 600;
  color: var(--cc-accent, #3370ff);
  white-space: nowrap;
}
.workflow-panel-mode-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--cc-accent-light, #e8f0ff);
  color: var(--cc-accent, #3370ff);
  font-weight: 500;
  text-transform: uppercase;
  margin-left: auto;
}
.dark .workflow-panel-mode-tag {
  background: #1a2a44;
}
.workflow-panel-body { padding: 8px 12px; }
.workflow-steps-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.workflow-step-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.workflow-step-item.step-pending { color: var(--cc-text-muted, #8f959e); background: transparent; }
.workflow-step-item.step-running { color: var(--cc-accent, #3370ff); background: var(--cc-accent-light, #e8f0ff); }
.dark .workflow-step-item.step-running { background: #1a2a44; }
.workflow-step-item.step-done { color: var(--cc-success, #34c759); background: #e8f5e9; }
.dark .workflow-step-item.step-done { background: #1a2a1c; }
.workflow-step-arrow { color: var(--cc-text-muted, #8f959e); font-size: 12px; display: flex; align-items: center; }
.step-icon { display: inline-flex; align-items: center; font-size: 14px; }
.step-label { font-weight: 500; }
.workflow-parallel-grid { display: flex; flex-wrap: wrap; gap: 6px; }
.workflow-parallel-node {
  display: flex; align-items: center; gap: 4px; padding: 3px 10px;
  border-radius: 6px; font-size: 12px;
  border: 1px solid var(--cc-border, #e8eaed);
  background: var(--cc-bg-primary, #fff);
}
.workflow-parallel-node.step-pending { color: var(--cc-text-muted, #8f959e); opacity: 0.7; }
.workflow-parallel-node.step-running { color: var(--cc-accent, #3370ff); border-color: var(--cc-accent, #3370ff); }
.workflow-parallel-node.step-done { color: var(--cc-success, #34c759); border-color: var(--cc-success, #34c759); }
.workflow-merge-row { display: flex; align-items: center; gap: 6px; margin-top: 6px; padding: 4px 0; font-size: 12px; color: var(--cc-warning, #ff9500); }
.merge-label { font-weight: 500; }
.workflow-router-info { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.router-target-tag { font-weight: 600; }
.workflow-router-reason { color: var(--cc-text-secondary, #646a73); font-size: 12px; font-style: italic; }
.workflow-supervisor-steps { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 4px; }
.workflow-supervisor-step { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 6px; font-size: 12px; }
.workflow-supervisor-step.step-pending { color: var(--cc-text-muted, #8f959e); }
.workflow-supervisor-step.step-running { color: var(--cc-accent, #3370ff); background: var(--cc-accent-light, #e8f0ff); }
.dark .workflow-supervisor-step.step-running { background: #1a2a44; }
.workflow-supervisor-step.step-done { color: var(--cc-success, #34c759); background: #e8f5e9; }
.dark .workflow-supervisor-step.step-done { background: #1a2a1c; }
.workflow-supervisor-delegate { margin: 4px 0; }
.workflow-supervisor-action { display: flex; align-items: center; color: var(--cc-text-secondary, #646a73); font-size: 12px; margin-top: 2px; }
.workflow-handoff-info { display: flex; align-items: center; gap: 6px; }
</style>
