import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    base: './',
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    plugins: [
      vue(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        // main.js 已全局引入 element-plus/dist/index.css，这里关闭按需样式导入，
        // 避免与全量样式冲突导致 dev 模式下 el-*.css 资源 404 / ERR_ABORTED
        resolvers: [ElementPlusResolver({ importStyle: false })],
        dts: 'src/auto-imports.d.ts',
        eslintrc: { enabled: false }
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: false })],
        dts: 'src/components.d.ts'
      }),
      viteCompression({ verbose: true, threshold: 10240, algorithm: 'gzip' })
    ],
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler'
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 80,
      open: false,
      proxy: {
        '/dev-api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            'vue-vendor': ['vue', 'vue-router', 'pinia']
          }
        }
      }
    }
  }
})
