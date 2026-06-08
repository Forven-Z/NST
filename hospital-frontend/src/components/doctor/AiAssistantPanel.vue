<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useDoctorWorkspaceStore } from '../../stores/doctorWorkspace'
import { useMock } from '../../utils/mock'

const mockMode = useMock()
const workspace = useDoctorWorkspaceStore()
const { draftMessages } = storeToRefs(workspace)

const input = ref('')
const loading = ref(false)
const scrollRef = ref(null)

const FAQ = [
  {
    q: /普通号|专家号|挂什么号/,
    a: '初诊常见病建议挂普通号；疑难或普通门诊疗效不佳再挂专家号（副主任医师及以上）。专家号号源少，非每时段都有。',
  },
  {
    q: /流程|顺序|怎么看病/,
    a: '典型流程：挂号缴费 → 候诊叫号 → 医生问诊开单 → 再次缴费 → 检验/检查/取药 → 复诊或结束看诊。',
  },
  {
    q: /检验|检查|先做什么/,
    a: '医生根据病情开立检验（如血常规）或检查（如 CT）；均需先缴费，检验科/放射科才会执行并出报告。',
  },
  {
    q: /处方|取药/,
    a: '处方开立后患者须至收费处缴费，再到药房窗口发药；药师会核对身份与药品。',
  },
  {
    q: /退费|退药/,
    a: '未执行的医技项目、未发药处方可到收费窗口退费；已发药需先药房退药再退费。',
  },
]

const welcomeText = mockMode
  ? '【AI 助理】左侧生成的检查/检验/处置草稿将显示在此处；也可询问门诊流程、号别选择等。'
  : 'AI 助理将展示诊疗草稿与辅助信息。'

const chatMessages = ref([{ id: 'welcome', role: 'assistant', text: welcomeText }])

const allMessages = computed(() => [...chatMessages.value, ...draftMessages.value])

watch(
  () => allMessages.value.length,
  async () => {
    await nextTick()
    if (scrollRef.value) {
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    }
  },
)

function reply(text) {
  for (const item of FAQ) {
    if (item.q.test(text)) return item.a
  }
  return '【Mock】暂未匹配到知识库条目。可尝试问：普通号和专家号区别、门诊流程、检验检查顺序、如何取药退费。'
}

async function onSend() {
  const text = input.value.trim()
  if (!text) return
  chatMessages.value.push({ id: `user-${Date.now()}`, role: 'user', text })
  input.value = ''
  loading.value = true
  await new Promise((r) => setTimeout(r, 400))
  chatMessages.value.push({ id: `reply-${Date.now()}`, role: 'assistant', text: reply(text) })
  loading.value = false
}

function onQuick(q) {
  input.value = q
  onSend()
}
</script>

<template>
  <div class="ai-panel">
    <div class="ai-header">
      <h3>AI 助理</h3>
      <el-tag size="small" :type="mockMode ? 'warning' : 'info'">
        {{ mockMode ? 'Mock 问答' : '在线' }}
      </el-tag>
    </div>

    <div ref="scrollRef" class="ai-messages">
      <div
        v-for="msg in allMessages"
        :key="msg.id"
        class="msg"
        :class="[msg.role, msg.draftType ? 'draft' : '']"
      >
        {{ msg.text }}
      </div>
      <div v-if="loading" class="msg assistant muted">思考中…</div>
    </div>

    <div v-if="mockMode" class="quick-qs">
      <el-button size="small" link @click="onQuick('普通号和专家号怎么选？')">号别选择</el-button>
      <el-button size="small" link @click="onQuick('门诊完整流程是什么？')">门诊流程</el-button>
      <el-button size="small" link @click="onQuick('检验和检查要先缴费吗？')">缴费顺序</el-button>
    </div>

    <div class="ai-input">
      <el-input
        v-model="input"
        placeholder="输入问题…"
        :disabled="!mockMode"
        @keyup.enter="onSend"
      />
      <el-button type="primary" :loading="loading" :disabled="!mockMode" @click="onSend">
        发送
      </el-button>
    </div>
    <p v-if="!mockMode" class="tip">关闭 Mock 后等待 Spring AI 接入</p>
  </div>
</template>

<style scoped>
.ai-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.ai-header {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-header h3 {
  margin: 0;
  font-size: 16px;
  color: #334155;
}

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg {
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  max-width: 95%;
  white-space: pre-wrap;
}

.msg.user {
  align-self: flex-end;
  background: #0f766e;
  color: #fff;
}

.msg.assistant {
  align-self: flex-start;
  background: #f1f5f9;
  color: #334155;
}

.msg.assistant.draft {
  background: #ecfdf5;
  border: 1px solid #99f6e4;
  color: #134e4a;
}

.msg.muted {
  opacity: 0.7;
}

.quick-qs {
  padding: 0 16px 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ai-input {
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  gap: 8px;
}

.tip {
  margin: 0;
  padding: 0 16px 12px;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}
</style>
