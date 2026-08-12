import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 开发环境代理：/api → 后端 8080
// base=/app/：与 BrowserRouter basename 配套，生产经外层 nginx /app/ → 8081 剥离前缀，
// 资源引用 /app/assets/... 仍能正确回源（React Router 匹配 /app/xxx）
export default defineConfig({
  base: "/app/",
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
