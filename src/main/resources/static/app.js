const { createApp } = Vue;

createApp({
  data() {
    return {
      loading: false,
      error: '',
      generatedAt: '',
      limit: 10,
      rangeMode: '0',
      start: '',
      end: '',
      dataMode: '',
      dataStart: '',
      dataEnd: '',
      refreshSeconds: 10,
      countdown: 10,
      power: null,
      pressure: null,
      th: null,
      now: Date.now(),
      detail: { visible: false, title: '', rows: [] }
    };
  },
  computed: {
    powerRows() { return this.power ? this.power.rows : []; },
    pressureRows() { return this.pressure ? this.pressure.rows : []; },
    thRows() { return this.th ? this.th.rows : []; },
    powerSpan() {
      const rows = this.powerRows;
      if (!rows.length) { return '无数据'; }
      const oldest = rows[rows.length - 1];
      const newest = rows[0];
      const tail = newest.running ? '至今' : (newest.endTime || '—');
      return oldest.startTime + ' ~ ' + tail;
    },
    pressureSpan() { return this.spanOf(this.pressureRows); },
    thSpan() { return this.spanOf(this.thRows); }
  },
  methods: {

    onRangeChange() {
      if (this.rangeMode === 'custom') {
        return;
      }
      this.start = '';
      this.end = '';
      this.refresh(false);
    },
    resetRange() {
      this.rangeMode = '0';
      this.start = '';
      this.end = '';
      this.refresh(false);
    },
    queryPower() {
      if (this.rangeMode === 'custom' && !this.start && !this.end) {
        this.error = '请先选择开始时间和结束时间，再点查询';
        return;
      }
      this.refresh(false);
    },
    queryData() {
      if (this.dataMode === 'custom' && !this.dataStart && !this.dataEnd) {
        this.error = '请先选择开始时间和结束时间，再点查询';
        return;
      }
      this.refresh(false);
    },
    onDataRangeChange() {
      if (this.dataMode === 'custom') {
        return;
      }
      this.dataStart = '';
      this.dataEnd = '';
      this.refresh(false);
    },
    resetDataRange() {
      this.dataMode = '';
      this.dataStart = '';
      this.dataEnd = '';
      this.refresh(false);
    },
    spanOf(rows) {
      if (!rows.length) { return '无数据'; }
      return rows[rows.length - 1].collectTime + ' ~ ' + rows[0].collectTime;
    },
    queryUrl(force) {
      const parts = ['limit=' + this.limit, 'refresh=' + (force ? 'true' : 'false')];
      if (this.rangeMode === 'custom' && (this.start || this.end)) {
        if (this.start) { parts.push('start=' + encodeURIComponent(this.start)); }
        if (this.end) { parts.push('end=' + encodeURIComponent(this.end)); }
      } else {
        parts.push('days=' + (this.rangeMode === 'custom' ? 0 : Number(this.rangeMode)));
      }
      if (this.dataMode === 'custom' && (this.dataStart || this.dataEnd)) {
        if (this.dataStart) { parts.push('dataStart=' + encodeURIComponent(this.dataStart)); }
        if (this.dataEnd) { parts.push('dataEnd=' + encodeURIComponent(this.dataEnd)); }
      } else if (this.dataMode && this.dataMode !== 'custom') {
        parts.push('dataDays=' + Number(this.dataMode));
      }
      return '/api/overview?' + parts.join('&');
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : value;
    },
    async refresh(force) {
      this.loading = true;
      try {
        const url = this.queryUrl(force);
        const resp = await fetch(url, { cache: 'no-store' });
        const text = await resp.text();
        let data = null;
        try {
          data = JSON.parse(text);
        } catch (parseError) {
          throw new Error('返回内容不是 JSON：' + text.slice(0, 120));
        }
        if (!resp.ok) {
          throw new Error(data && data.error ? data.error : ('HTTP ' + resp.status));
        }
        this.power = data.power;
        this.pressure = data.pressure;
        this.th = data.th;
        this.generatedAt = data.generatedAt;
        this.error = '';
        this.countdown = this.refreshSeconds;
      } catch (e) {
        this.error = '数据获取失败：' + (e.message || String(e));
      } finally {
        this.loading = false;
      }
    },
    durationOf(row) {
      if (!row.running) {
        return row.durationText;
      }
      const seconds = Math.max(0, Math.floor((this.now - row.startTs) / 1000));
      return this.formatDuration(seconds);
    },
    formatDuration(seconds) {
      const days = Math.floor(seconds / 86400);
      const hours = Math.floor((seconds % 86400) / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      const secs = seconds % 60;
      return days + '天' + hours + '小时' + minutes + '分' + secs + '秒';
    },
    openPressureDetail(row) {
      this.detail = {
        visible: true,
        title: row.deviceName + ' 采集详情 · ' + row.collectTime,
        rows: row.raw || []
      };
    },
    openThDetail(row) {
      this.detail = {
        visible: true,
        title: row.deviceName + ' 采集详情 · ' + row.collectTime,
        rows: row.raw || []
      };
    },
    closeDetail() {
      this.detail = { visible: false, title: '', rows: [] };
    }
  },
  mounted() {
    this.refresh(true);
    setInterval(() => { this.now = Date.now(); }, 1000);
    setInterval(() => {
      if (this.countdown > 0) {
        this.countdown -= 1;
      }
      if (this.countdown <= 0) {
        this.countdown = this.refreshSeconds;
        this.refresh(false);
      }
    }, 1000);
  }
}).mount('#app');
