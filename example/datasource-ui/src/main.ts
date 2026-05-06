import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import App from './App.vue'
import router from './router'
import 'element-plus/dist/index.css'
import './styles/main.css'
import './styles/element-plus.css'

const app = createApp(App)
app.use(ElementPlus)
app.use(router)
app.mount('#app')
