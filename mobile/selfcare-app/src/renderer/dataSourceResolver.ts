/**
 * DataSourceResolver — generic bridge from manifest dataSource ids to API calls.
 *
 * The manifest declares dataSource mappings in `dataSources` (authored in admin
 * portal). This resolver reads those mappings at runtime and calls the
 * corresponding service endpoint via ApiHandle. NO hardcoded endpoints here.
 */

import { ApiHandle } from '../hooks/useApi';
import { DataSourceResolver, ExperienceManifest, DataSourceConfig } from '../manifest/types';

interface ResolvedDataSourceConfig extends DataSourceConfig {
  _resolvedParams?: Record<string, unknown>;
}

export function createManifestDataSourceResolver(
  api: ApiHandle,
  manifest: ExperienceManifest | null | undefined
): DataSourceResolver {
  const dataSources = manifest?.dataSources ?? {};

  return {
    async resolve(
      dataSourceId: string,
      ctx: { tenantId?: string | null; connectionId?: string | null }
    ): Promise<unknown> {
      const config = dataSources[dataSourceId];
      if (!config) {
        console.warn(`[DataSourceResolver] no mapping for dataSource: ${dataSourceId}`);
        return null;
      }

      // Resolve params with context substitution
      const resolvedParams: Record<string, unknown> = {};
      if (config.params) {
        for (const [key, value] of Object.entries(config.params)) {
          if (typeof value === 'string') {
            // Substitute {tenantId}, {connectionId} etc.
            resolvedParams[key] = value
              .replace('{tenantId}', ctx.tenantId ?? '')
              .replace('{connectionId}', ctx.connectionId ?? '');
          } else {
            resolvedParams[key] = value;
          }
        }
      }

      // Get service namespace from ApiHandle
      const service = (api as unknown as Record<string, unknown>)[config.service] as
        | Record<string, (...args: unknown[]) => Promise<unknown>>
        | undefined;

      if (!service) {
        console.warn(`[DataSourceResolver] service not found in ApiHandle: ${config.service}`);
        return null;
      }

      // Get endpoint function
      const endpointFn = service[config.endpoint];
      if (typeof endpointFn !== 'function') {
        console.warn(
          `[DataSourceResolver] endpoint not found: ${config.service}.${config.endpoint}`
        );
        return null;
      }

      try {
        const result = await endpointFn(resolvedParams);
        // Optional transform (e.g., "extract.data" to unwrap {data})
        if (config.transform === 'extract.data' && result && typeof result === 'object' && 'data' in result) {
          return (result as Record<string, unknown>).data;
        }
        return result;
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Unknown error';
        console.error(`[DataSourceResolver] ${dataSourceId} failed:`, message);
        throw err;
      }
    },
  };
}