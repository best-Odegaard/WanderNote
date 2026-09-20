<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner">
        <text class="back" @tap="goBack">‹</text>
        <text class="nav-title">生成专属攻略</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="body"
      :style="{ paddingTop: navHeight + 'px' }"
    >
      <!-- 三步进度 -->
      <view class="steps">
        <view
          v-for="(step, index) in flowSteps"
          :key="step.key"
          class="step-item"
          :class="{ active: index === 0, done: index < 0 }"
        >
          <view class="step-dot">{{ index + 1 }}</view>
          <text class="step-label">{{ step.label }}</text>
        </view>
        <view class="step-line" />
      </view>

      <text class="ai-hint">AI小蚂正在确认您的旅行需求</text>

      <!-- 问题列表 -->
      <view
        v-for="(q, qIndex) in questions"
        :key="q.id"
        class="question-block"
      >
        <text class="question-text">
          {{ q.text }}{{ q.emoji ? ` ${q.emoji}` : '' }}
        </text>
        <view class="options">
          <view
            v-for="opt in q.options"
            :key="opt"
            class="option"
            :class="{ selected: answers[q.id] === opt }"
            @tap="selectOption(q.id, opt)"
          >{{ opt }}</view>
        </view>
        <view
          v-if="qIndex === 0"
          class="add-custom"
          @tap="openCustomInput"
        >
          <text>手动添加需求</text>
          <text class="plus">+</text>
        </view>
      </view>

      <!-- 手动添加的需求 -->
      <view v-if="customNeeds.length" class="custom-list">
        <view
          v-for="(item, i) in customNeeds"
          :key="i"
          class="custom-tag"
        >
          <text>{{ item }}</text>
          <text class="remove" @tap="removeCustom(i)">×</text>
        </view>
      </view>

      <view style="height: 200rpx" />
    </scroll-view>

    <view class="footer safe-bottom">
      <button class="btn-submit" @tap="submitRequirements">提交需求</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useTripStore } from '@/store/trip'
import {
  buildSurveyQuestions,
  budgetToAmount,
  paceToTag
} from '@/utils/surveyQuestions'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 48

const flowSteps = [
  { key: 'understand', label: '理解需求' },
  { key: 'research', label: '深度研究' },
  { key: 'generate', label: '生成攻略' }
]

const destination = ref('重庆')
const days = ref('3天')
const answers = reactive<Record<string, string>>({})
const customNeeds = ref<string[]>([])

const questions = computed(() => buildSurveyQuestions(destination.value, days.value))

onLoad((query) => {
  if (query?.city) {
    destination.value = decodeURIComponent(String(query.city))
  }
  if (query?.days) {
    const d = decodeURIComponent(String(query.days))
    days.value = d.includes('天') ? d : `${d}天`
  }
})

function selectOption(questionId: string, option: string) {
  answers[questionId] = option
}

function openCustomInput() {
  uni.showModal({
    title: '手动添加需求',
    editable: true,
    placeholderText: '如：想住江景房、避开网红店排队等',
    success(res) {
      if (!res.confirm) return
      const text = (res.content || '').trim()
      if (!text) return
      if (!customNeeds.value.includes(text)) {
        customNeeds.value.push(text)
      }
    }
  })
}

function removeCustom(index: number) {
  customNeeds.value.splice(index, 1)
}

function goBack() {
  uni.navigateBack()
}

function submitRequirements() {
  const missing = questions.value.find((q) => !answers[q.id])
  if (missing) {
    uni.showToast({ title: '请完成所有问题', icon: 'none' })
    return
  }

  const tags = [
    paceToTag(answers.pace),
    answers.interest,
    answers.companion,
    ...customNeeds.value
  ]

  tripStore.resetForNewTrip()
  tripStore.currentTrip = {
    title: `${destination.value}${days.value.replace('天', '')}日游`,
    fromCity: '当前城市',
    toCity: destination.value,
    days: parseInt(days.value, 10) || 3,
    budget: budgetToAmount(answers.budget),
    people: answers.companion.includes('独自') ? 1
      : answers.companion.includes('双人') ? 2
      : answers.companion.includes('亲子') ? 3
      : 4,
    tags,
    dayPlans: []
  }

  uni.navigateTo({ url: '/pages/ai/chat' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: linear-gradient(180deg, var(--survey-top) 0%, var(--bg-page) 40%, var(--bg-page) 100%);
}

.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: transparent;
}

.nav-inner {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 32rpx;
}

.back {
  font-size: 56rpx;
  color: var(--text-main);
  margin-right: 16rpx;
  line-height: 1;
}

.nav-title {
  font-size: var(--fs-subhead);
  font-weight: 700;
  color: var(--text-main);
}

.body {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

.steps {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  position: relative;
  padding: 24rpx 16rpx 32rpx;
}

.step-line {
  position: absolute;
  top: 44rpx;
  left: 80rpx;
  right: 80rpx;
  height: 4rpx;
  background: var(--border);
  z-index: 0;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  z-index: 1;
  flex: 1;

  .step-dot {
    width: 48rpx;
    height: 48rpx;
    border-radius: 50%;
    background: var(--bg-input);
    color: var(--text-tertiary);
    font-size: var(--fs-meta);
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .step-label {
    font-size: var(--fs-meta);
    color: var(--text-tertiary);
  }

  &.active {
    .step-dot {
      background: #4a9ef5;
      color: #fff;
    }

    .step-label {
      color: #4a9ef5;
      font-weight: 600;
    }
  }
}

.ai-hint {
  display: block;
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-bottom: 40rpx;
}

.question-block {
  margin-bottom: 48rpx;
}

.question-text {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.5;
  display: block;
  margin-bottom: 24rpx;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.option {
  padding: 28rpx 32rpx;
  background: var(--bg-muted);
  border-radius: 16rpx;
  font-size: var(--fs-body);
  color: var(--text-main);
  line-height: 1.4;
  border: 2rpx solid transparent;
  transition: all 0.2s;

  &.selected {
    background: var(--primary-soft);
    border-color: #4a9ef5;
    color: var(--primary-strong);
  }
}

.add-custom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin-top: 20rpx;
  padding: 20rpx;
  background: var(--bg-card);
  border-radius: 16rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);
  border: 1rpx solid var(--border);
}

.plus {
  font-size: var(--fs-title);
  color: #4a9ef5;
}

.custom-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.custom-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: var(--bg-card);
  border: 1rpx solid #4a9ef5;
  border-radius: 999rpx;
  font-size: var(--fs-meta);
  color: var(--primary-strong);
}

.remove {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
  padding-left: 4rpx;
}

.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 32rpx;
  background: transparent;
}

.btn-submit {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: #4a9ef5;
  color: #fff;
  border-radius: 48rpx;
  font-size: var(--fs-title);
  font-weight: 600;
  border: none;

  &::after {
    border: none;
  }
}
</style>
