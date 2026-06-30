import type { EntryScanConfig, Repository } from '../../types';

const DEFAULT_EXCLUDE_CLASSPATHS = ['**/*Test', '**/*Tests', '**/*TestCase'];

export const buildScanConfigWithDefaults = (
  config: EntryScanConfig | Record<string, unknown> | undefined,
): EntryScanConfig => {
  const base = (config || {}) as EntryScanConfig;
  return {
    ...base,
    excludeClasspaths:
      Array.isArray(base.excludeClasspaths) && base.excludeClasspaths.length > 0
        ? base.excludeClasspaths
        : DEFAULT_EXCLUDE_CLASSPATHS,
  };
};

export const parseRepoEntryScanConfig = (repo: Repository | undefined): EntryScanConfig => {
  if (!repo?.entryScanConfig) return buildScanConfigWithDefaults(undefined);
  if (typeof repo.entryScanConfig === 'string') {
    try {
      return buildScanConfigWithDefaults(JSON.parse(repo.entryScanConfig));
    } catch {
      return buildScanConfigWithDefaults(undefined);
    }
  }
  return buildScanConfigWithDefaults(repo.entryScanConfig);
};
