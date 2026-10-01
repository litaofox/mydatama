<template>
  <div class="stage-timeline">
    <div v-for="(stage, idx) in stages" :key="stage.key" class="stage-item">
      <div class="stage-dot" :class="stageState(stage, idx)">
        <el-icon v-if="stageState(stage, idx) === 'done'"><Check /></el-icon>
        <el-icon v-else-if="stageState(stage, idx) === 'failed'"><Close /></el-icon>
        <el-icon v-else-if="stageState(stage, idx) === 'running'" class="is-loading"><Loading /></el-icon>
        <span v-else>{{ idx + 1 }}</span>
      </div>
      <div class="stage-name">{{ stage.name }}</div>
      <div v-if="idx < stages.length - 1" class="stage-line" :class="{ active: currentIndex > idx }"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, Close, Loading } from '@element-plus/icons-vue'

const props = defineProps<{
  stage: string
  status: string
}>()

const stages = [
  { key: 'COLLECT_VALIDATE', name: '采集校验' },
  { key: 'CLEAN', name: '清洗' },
  { key: 'STANDARDIZE', name: '标准化' },
  { key: 'MASK', name: '脱敏' },
  { key: 'ANNOTATE_REGISTER', name: '标注登记' }
]

const currentIndex = computed(() => {
  const idx = stages.findIndex((s) => s.key === props.stage)
  if (props.status === 'SUCCESS') return stages.length
  return idx < 0 ? 0 : idx
})

function stageState(_stage: { key: string }, idx: number): 'done' | 'running' | 'failed' | 'pending' {
  if (props.status === 'SUCCESS') return 'done'
  if (props.status === 'FAILED') {
    if (idx < currentIndex.value) return 'done'
    if (idx === currentIndex.value) return 'failed'
    return 'pending'
  }
  if (idx < currentIndex.value) return 'done'
  if (idx === currentIndex.value) return 'running'
  return 'pending'
}
</script>

<style scoped>
.stage-timeline {
  display: flex;
  align-items: flex-start;
  padding: 12px 0;
}
.stage-item {
  display: flex;
  align-items: center;
  flex: 1;
  position: relative;
}
.stage-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e4e7ed;
  color: #909399;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
  z-index: 1;
}
.stage-dot.done {
  background: #67c23a;
  color: #fff;
}
.stage-dot.running {
  background: #409eff;
  color: #fff;
}
.stage-dot.failed {
  background: #f56c6c;
  color: #fff;
}
.stage-name {
  position: absolute;
  top: 38px;
  left: 0;
  width: 80px;
  font-size: 12px;
  color: #606266;
}
.stage-line {
  flex: 1;
  height: 2px;
  background: #e4e7ed;
  margin: 0 8px;
  transform: translateY(-14px);
}
.stage-line.active {
  background: #67c23a;
}
.stage-item:last-child {
  flex: 0;
}
</style>
