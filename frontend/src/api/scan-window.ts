import request from './request';
import type { ScanWindow } from '../types';

export const getScanWindow = (repositoryId: number): Promise<ScanWindow | null> =>
  request.get(`/scan-windows/by-repository/${repositoryId}`);

export const upsertScanWindow = (dto: Partial<ScanWindow> & { repositoryId: number }): Promise<ScanWindow> =>
  request.post('/scan-windows', dto);

export const deleteScanWindow = (repositoryId: number): Promise<void> =>
  request.delete(`/scan-windows/by-repository/${repositoryId}`);

export const listScanWindows = (): Promise<ScanWindow[]> =>
  request.get('/scan-windows');
