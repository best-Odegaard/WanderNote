// WanderNote 行笺 · H5 原型公共脚本

(function() {
  'use strict';

  // 当前会话槽位状态
  window.sessionSlots = {};

  // TabBar 渲染
  window.renderTabBar = function(activeIndex) {
    const tabs = [
      { text: '首页', icon: '🏠', href: 'home.html' },
      { text: '行程', icon: '🗓️', href: 'trip-index.html' },
      { text: '探索', icon: '🔍', href: 'explore.html' },
      { text: '我的', icon: '👤', href: 'profile.html' }
    ];
    const items = tabs.map((tab, i) => `
      <a href="${tab.href}" class="tab-item ${i === activeIndex ? 'active' : ''}">
        <span class="tab-icon">${tab.icon}</span>
        <span class="tab-text">${tab.text}</span>
      </a>
    `).join('');
    return `<nav class="tabbar">${items}</nav>`;
  };

  // 挂载 TabBar
  window.mountTabBar = function(activeIndex) {
    const bar = document.createElement('div');
    bar.innerHTML = window.renderTabBar(activeIndex);
    document.body.appendChild(bar.firstElementChild);
  };

  // 读取 URL 参数
  window.getQuery = function(key) {
    const params = new URLSearchParams(window.location.search);
    return params.get(key);
  };

  // 设置 URL 参数（不刷新）
  window.setQuery = function(key, value) {
    const url = new URL(window.location.href);
    if (value == null || value === '') {
      url.searchParams.delete(key);
    } else {
      url.searchParams.set(key, value);
    }
    window.history.replaceState({}, '', url);
  };

  // 轻震动（支持则调用）
  window.haptic = function() {
    if (navigator.vibrate) navigator.vibrate(8);
  };

  // 打开地图选择弹窗
  window.openMapModal = function() {
    const mask = document.getElementById('mapModal');
    if (mask) {
      mask.classList.add('show');
      window.haptic();
    }
  };

  window.closeMapModal = function() {
    const mask = document.getElementById('mapModal');
    if (mask) mask.classList.remove('show');
  };

  // 选择地图并跳转
  window.chooseMap = function(provider) {
    const mapUrls = {
      amap: 'https://uri.amap.com/navigation?from=112.47,23.05,星湖假日酒店&to=112.465,23.06,七星岩&mode=car&callnative=1',
      baidu: 'baidumap://map/direction?origin=latlng:23.05,112.47|name:星湖假日酒店&destination=latlng:23.06,112.465|name:七星岩&mode=driving',
      tencent: 'https://apis.map.qq.com/uri/v1/routeplan?type=drive&from=星湖假日酒店&fromcoord=23.05,112.47&to=七星岩&tocoord=23.06,112.465'
    };
    window.open(mapUrls[provider] || mapUrls.amap, '_blank');
    window.closeMapModal();
  };

  // 通用返回
  window.goBack = function() {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      window.location.href = 'home.html';
    }
  };

  // 保存会话槽位到 sessionStorage
  window.saveSlots = function(slots) {
    sessionStorage.setItem('wn_slots', JSON.stringify(slots));
  };

  // 读取会话槽位
  window.loadSlots = function() {
    try {
      return JSON.parse(sessionStorage.getItem('wn_slots') || '{}');
    } catch (e) {
      return {};
    }
  };

  // 设置单个槽位
  window.setSlot = function(key, value) {
    const slots = window.loadSlots();
    slots[key] = value;
    window.saveSlots(slots);
    return slots;
  };

  // 清空会话
  window.clearSlots = function() {
    sessionStorage.removeItem('wn_slots');
    window.sessionSlots = {};
  };

  // ===== 必去项清单（探索页「加入行程」需二次确认后写入） =====

  // 读取必去项列表
  window.getMustList = function() {
    try {
      return JSON.parse(sessionStorage.getItem('wn_must') || '[]');
    } catch (e) {
      return [];
    }
  };

  // 写入单个必去项（去重）
  window.addMustItem = function(name) {
    const list = window.getMustList();
    if (!list.includes(name)) list.push(name);
    sessionStorage.setItem('wn_must', JSON.stringify(list));
    return list;
  };

  // 清空必去项
  window.clearMustList = function() {
    sessionStorage.removeItem('wn_must');
  };

  // 轻提示（可附带「去规划」动作链接）
  window.toast = function(msg, actionHtml) {
    const old = document.getElementById('wnToast');
    if (old) old.remove();
    const t = document.createElement('div');
    t.id = 'wnToast';
    t.style.cssText = 'position:fixed;left:50%;transform:translateX(-50%);bottom:130px;z-index:500;'
      + 'background:rgba(15,23,42,0.92);color:#fff;padding:10px 18px;border-radius:999px;font-size:13px;'
      + 'display:flex;gap:12px;align-items:center;white-space:nowrap;box-shadow:0 8px 24px rgba(0,0,0,0.25);'
      + 'animation:fadeIn 0.25s ease;';
    t.innerHTML = `<span>✓ ${msg}</span>${actionHtml || ''}`;
    document.body.appendChild(t);
    setTimeout(() => {
      t.style.opacity = '0';
      t.style.transition = 'opacity 0.3s';
      setTimeout(() => t.remove(), 300);
    }, 2400);
  };

  // 「加入行程」二次确认弹窗：不直接默认加入，用户确认后才写入
  window.openAddConfirm = function(name, cityId) {
    const exist = document.getElementById('addConfirmModal');
    if (exist) exist.remove();
    window.haptic();
    const safeName = String(name).replace(/'/g, '');
    const mask = document.createElement('div');
    mask.className = 'modal-mask show';
    mask.id = 'addConfirmModal';
    mask.innerHTML = `
      <div class="modal-panel">
        <div class="modal-title">加入行程</div>
        <div style="text-align:center;padding:4px 0 20px;">
          <div style="font-size:40px;margin-bottom:8px;">📍</div>
          <div style="font-size:17px;font-weight:700;color:var(--text-main);">${safeName}</div>
          <div style="font-size:13px;color:var(--text-secondary);margin-top:8px;line-height:1.6;">
            将作为「必去项」带入 AI 对话<br>由小笺编排进你的路线
          </div>
        </div>
        <div style="display:flex;gap:10px;">
          <button class="btn btn-ghost" style="flex:1;" onclick="closeAddConfirm()">再想想</button>
          <button class="btn btn-primary" style="flex:2;" onclick="confirmAdd('${safeName}')">确认加入</button>
        </div>
      </div>
    `;
    document.body.appendChild(mask);
    mask.addEventListener('click', (e) => {
      if (e.target.id === 'addConfirmModal') window.closeAddConfirm();
    });
  };

  window.closeAddConfirm = function() {
    document.getElementById('addConfirmModal')?.remove();
  };

  window.confirmAdd = function(name) {
    window.addMustItem(name);
    window.closeAddConfirm();
    const count = window.getMustList().length;
    window.toast(
      `已加入行程（${count}）`,
      `<a href="ai-chat.html" style="color:#5eead4;font-weight:600;">去规划 →</a>`
    );
  };

  // 初始化 sheet 拖拽（简化版：仅支持点击把手切换展开/半屏）
  window.initSheet = function() {
    const grip = document.querySelector('.sheet-grip');
    const sheet = document.querySelector('.sheet');
    if (!grip || !sheet) return;

    let expanded = false;
    grip.addEventListener('click', () => {
      expanded = !expanded;
      if (expanded) {
        sheet.style.maxHeight = '85vh';
        sheet.style.top = '15%';
        sheet.style.bottom = 'auto';
        sheet.style.transition = 'top 0.3s ease';
      } else {
        sheet.style.maxHeight = '75vh';
        sheet.style.top = 'auto';
        sheet.style.bottom = '0';
      }
    });
  };

  // 页面加载完成后绑定通用事件
  document.addEventListener('DOMContentLoaded', () => {
    // 全局返回按钮
    document.querySelectorAll('[data-back]').forEach(btn => {
      btn.addEventListener('click', window.goBack);
    });

    // 地图弹窗关闭
    document.getElementById('mapModal')?.addEventListener('click', (e) => {
      if (e.target.id === 'mapModal') window.closeMapModal();
    });
  });
})();
