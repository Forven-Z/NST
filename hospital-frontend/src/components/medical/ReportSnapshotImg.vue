<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchPacsReportSnapshotBlob } from '../../api/pacs'
import { isInlineSnapshotSrc, parseReportSnapshotUrl } from '../../utils/reportSnapshot'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
})

const displaySrc = ref('')
const loading = ref(false)
const failed = ref(false)
let objectUrl = ''

function revokeObjectUrl() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

async function loadSnapshot(src) {
  revokeObjectUrl()
  displaySrc.value = ''
  failed.value = false
  if (!src) return

  if (isInlineSnapshotSrc(src)) {
    displaySrc.value = src
    return
  }

  const parsed = parseReportSnapshotUrl(src)
  if (!parsed) {
    displaySrc.value = src
    return
  }

  loading.value = true
  try {
    objectUrl = await fetchPacsReportSnapshotBlob(parsed.checkRequestId, parsed.plane)
    displaySrc.value = objectUrl
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => props.src,
  (src) => {
    loadSnapshot(src)
  },
  { immediate: true },
)

onBeforeUnmount(revokeObjectUrl)
</script>

<template>
  <div v-if="loading" class="snapshot-img snapshot-state">加载中…</div>
  <div v-else-if="failed" class="snapshot-img snapshot-state snapshot-failed">采图加载失败</div>
  <img v-else-if="displaySrc" :src="displaySrc" :alt="alt" class="snapshot-img" />
</template>

<style scoped>
.snapshot-img {
  width: 100%;
  min-height: 140px;
  max-height: 220px;
  height: auto;
  aspect-ratio: 4 / 3;
  object-fit: contain;
  display: block;
  background: #000;
}

.snapshot-state {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #94a3b8;
  background: #0f172a;
}

.snapshot-failed {
  color: #f87171;
}
</style>
