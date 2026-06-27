import request from './request';
import type { LoginRequest, LoginResponse } from '../types';

export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return request.post('/auth/login', data);
};
