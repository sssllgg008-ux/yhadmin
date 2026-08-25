/**
 * PluginUploader - Vue2 plugin upload component (reusable)
 *
 * Configurable via props for different upload scenarios:
 *   uploadUrl     upload endpoint       default '/api/plugins/upload'
 *   accept        allowed file types    default '.jar'
 *   maxSize       max file size(bytes)  default 104857600 (100MB)
 *   autoStart     auto-start after upload default false
 *   autoFillName  auto-fill name from filename default true
 *   label         upload button text    default 'Upload Plugin'
 *
 * Events:
 *   @success  upload succeeded, returns { plugin }
 *   @error    upload failed,    returns { message }
 */
Vue.component('plugin-uploader', {
  props: {
    uploadUrl:    { type: String,  default: '/api/plugins/upload' },
    accept:       { type: String,  default: '.jar' },
    maxSize:      { type: Number,  default: 100 * 1024 * 1024 },
    autoStart:    { type: Boolean, default: false },
    autoFillName: { type: Boolean, default: true },
    label:        { type: String,  default: 'Upload Plugin' },
    authToken:    { type: String,  default: '' },
    maintenanceCore: { type: Boolean, default: false }
  },
  data: function () {
    return {
      dragging: false,
      selectedFile: null,
      pluginName: '',
      startAfterUpload: this.autoStart,
      uploading: false,
      progress: 0,
      errorMsg: ''
    }
  },
  computed: {
    acceptArray: function () {
      return this.accept.split(',').map(function (s) { return s.trim().toLowerCase() })
    }
  },
  methods: {
    formatSize: function (bytes) {
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / 1048576).toFixed(1) + ' MB'
    },

    validateFile: function (file) {
      if (!file) return 'Please select a file'
      var name = (file.name || '').toLowerCase()
      var ok = this.acceptArray.some(function (ext) { return name.endsWith(ext) })
      if (!ok) return 'Only ' + this.accept + ' files are supported'
      if (file.size > this.maxSize) return 'File too large (max ' + this.formatSize(this.maxSize) + ')'
      return ''
    },

    onFileChange: function (e) {
      var files = e.target.files || (e.dataTransfer && e.dataTransfer.files)
      if (!files || files.length === 0) return
      this.pickFile(files[0])
    },

    onDrop: function (e) {
      e.preventDefault()
      this.dragging = false
      var files = e.dataTransfer.files
      if (!files || files.length === 0) return
      this.pickFile(files[0])
    },

    onDragOver: function (e) { e.preventDefault(); this.dragging = true },
    onDragLeave: function (e) { e.preventDefault(); this.dragging = false },

    pickFile: function (file) {
      this.errorMsg = ''
      var err = this.validateFile(file)
      if (err) { this.errorMsg = err; this.selectedFile = null; return }
      this.selectedFile = file
      if (this.autoFillName) {
        var n = file.name
        var dot = n.lastIndexOf('.')
        this.pluginName = dot > 0 ? n.substring(0, dot) : n
      }
    },

    triggerSelect: function () { this.$refs.fileInput.click() },

    reset: function () {
      this.selectedFile = null
      this.pluginName = ''
      this.progress = 0
      this.uploading = false
      this.errorMsg = ''
      if (this.$refs.fileInput) this.$refs.fileInput.value = ''
    },

    doUpload: function () {
      if (!this.selectedFile || this.uploading) return
      this.errorMsg = ''
      this.uploading = true
      this.progress = 0

      var self = this
      var formData = new FormData()
      formData.append('file', this.selectedFile)
      formData.append('name', this.maintenanceCore ? 'pluginSystem' : (this.pluginName || ''))
      formData.append('autoStart', this.startAfterUpload ? 'true' : 'false')

      var xhr = new XMLHttpRequest()
      xhr.open('POST', this.uploadUrl)
      if (this.authToken) xhr.setRequestHeader('Authorization', 'Bearer ' + this.authToken)

      xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) self.progress = Math.round((e.loaded / e.total) * 100)
      }

      xhr.onload = function () {
        self.uploading = false
        var data = null
        try { data = JSON.parse(xhr.responseText) } catch (e) {}
        if (xhr.status >= 200 && xhr.status < 300 && data && data.success) {
          self.$emit('success', { plugin: data.data })
          self.reset()
        } else {
          var msg = (data && (data.message || data.msg)) ||
            (xhr.status === 413 ? '插件包超过服务端上传限制' :
              (xhr.status === 503 ? '权限插件维护凭证无效或已过期' : ('Upload failed (HTTP ' + xhr.status + ')')))
          self.errorMsg = msg
          self.$emit('error', { message: msg })
        }
      }

      xhr.onerror = function () {
        self.uploading = false
        var msg = 'Network error, upload failed'
        self.errorMsg = msg
        self.$emit('error', { message: msg })
      }

      xhr.send(formData)
    }
  },
  render: function (h) {
    var self = this

    // drop zone
    var dropZoneChildren = []
    if (!this.selectedFile) {
      dropZoneChildren.push(
        h('div', { class: 'drop-hint' }, [
          h('span', { class: 'upload-icon' }, '+'),
          h('p', 'Click or drag ' + this.accept + ' file here'),
          h('p', { class: 'hint' }, 'Max ' + this.formatSize(this.maxSize))
        ])
      )
    } else {
      dropZoneChildren.push(
        h('div', { class: 'file-info' }, [
          h('span', { class: 'file-name' }, this.selectedFile.name),
          h('span', { class: 'file-size' }, this.formatSize(this.selectedFile.size))
        ])
      )
    }

    var dropZone = h('div', {
      class: { 'drop-zone': true, active: this.dragging, hasFile: !!this.selectedFile },
      on: {
        click: this.triggerSelect,
        drop: this.onDrop,
        dragover: this.onDragOver,
        dragleave: this.onDragLeave
      }
    }, [
      h('input', {
        ref: 'fileInput',
        attrs: { type: 'file', accept: this.accept },
        style: { display: 'none' },
        on: { change: this.onFileChange }
      }),
      dropZoneChildren
    ])

    // config area (always render, toggle visibility via CSS)
    var configArea = h('div', {
      class: 'upload-config',
      style: { display: this.selectedFile ? 'block' : 'none' }
    }, [
      h('div', { class: 'form-row' }, [
        h('label', 'Plugin Name'),
        h('input', {
          attrs: { type: 'text', placeholder: 'Leave blank to use filename' },
          domProps: { value: this.pluginName },
          on: {
            input: function (e) { self.pluginName = e.target.value }
          }
        })
      ]),
      h('div', { class: 'form-row checkbox-row' }, [
        h('label', [
          h('input', {
            attrs: { type: 'checkbox' },
            domProps: { checked: this.startAfterUpload },
            on: {
              change: function (e) { self.startAfterUpload = e.target.checked }
            }
          }),
          ' Auto-start after upload'
        ])
      ]),
      h('div', { class: 'actions' }, [
        h('button', {
          class: { btn: true, 'btn-primary': true },
          attrs: { disabled: this.uploading },
          on: { click: this.doUpload }
        }, this.label),
        h('button', {
          class: { btn: true, 'btn-default': true },
          on: { click: this.reset }
        }, 'Cancel')
      ])
    ])

    // progress bar
    var progressBar = null
    if (this.uploading) {
      progressBar = h('div', { class: 'progress-bar' }, [
        h('div', {
          class: 'progress-fill',
          style: { width: this.progress + '%' }
        }),
        h('span', { class: 'progress-text' }, this.progress + '%')
      ])
    }

    // error message
    var errorMsg = null
    if (this.errorMsg) {
      errorMsg = h('div', { class: 'error-msg' }, this.errorMsg)
    }

    return h('div', { class: 'plugin-uploader' }, [
      dropZone,
      configArea,
      progressBar,
      errorMsg
    ])
  }
})
