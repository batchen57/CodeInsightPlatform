import axios from 'axios';
import { message } from 'antd';
import { useAuthStore } from '../stores/auth';
import { getCurrentOperator } from './auth';

/**
 * 全局 Axios HTTP 客户端基础实例
 * 配置统一的基础接口路径（baseURL）和请求超时时间限制。
 */
const request = axios.create({
  // 从 Vite 环境变量中读取服务端点 API URL，若未配置则默认回退到当前域名的 /api 路径
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 设置 30 秒超时断开机制，防范因大模型分析接口响应迟缓导致的请求无限期挂起
  timeout: 30000,
});

request.interceptors.request.use((config) => {
  const session = useAuthStore.getState().session;
  if (session?.token) {
    config.headers.Authorization = `Bearer ${session.token}`;
  }
  return config;
});

/**
 * 请求拦截器：自动注入 X-Operator 请求头
 * 后端 OperatorHeaderFilter 读取该头写入 OperatorContext ThreadLocal，
 * 后续 service 层可以通过 OperatorContext.get() 获取当前操作人。
 */
request.interceptors.request.use((config) => {
  config.headers['X-Operator'] = getCurrentOperator();
  return config;
});

/**
 * 响应拦截器配置
 * 统一在前端拦截并解析后端返回的 ApiResponse 格式数据包，并自动过滤全局的错误弹框。
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    // code 等于 0 说明业务成功，直接拆箱返回 data 对象中的内容
    if (res.code !== 0) {
      // 否则说明后端处理报错，通过 Antd Message 全局弹出后端 message 提示，并触发 Promise.reject
      message.error(res.message || '系统请求出错');
      return Promise.reject(new Error(res.message || 'Error'));
    }
    return res.data;
  },
  (error) => {
    // 捕获网络连接超时、404、500 等网络级底层的异常
    const msg = error.response?.data?.message || error.message || '网络连接异常，请稍后重试';
    message.error(msg);
    return Promise.reject(error);
  }
);

export default request;

