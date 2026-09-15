/**
 * useRecommendations — fetch ML-based bundle recommendations and churn scores.
 *
 * Returns:
 * - bundles: list of BundleRec for the connection
 * - churnScore: ChurnScore for the connection
 * - loading / error state
 * - refresh()
 *
 * Cache strategy: 1 hour TTL in MMKV. Cached data is returned immediately,
 * then revalidated in the background.
 */
import { useCallback, useEffect, useState } from 'react';
import { MMKV } from 'react-native-mmkv';
import { AIClient, BundleRec, ChurnScore } from '../config/AIClient';
import { ApiClient } from '../config/ApiClient';
import { useAuthStore } from './useAuth';
import { useTenant } from './useTenant';

const storage = new MMKV({ id: 'selfcare-recommendations' });
const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

interface CachedRecs {
  bundles: BundleRec[];
  churn: ChurnScore;
  cachedAt: number;
}

export function useRecommendations(connectionId: string | null | undefined) {
  const { tenantId } = useTenant();
  const auth = useAuthStore();
  const [bundles, setBundles] = useState<BundleRec[]>([]);
  const [churnScore, setChurnScore] = useState<ChurnScore | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(
    async (bypassCache = false) => {
      if (!connectionId) {
        setLoading(false);
        return;
      }

      const cacheKey = `${tenantId}:${connectionId}`;
      const cachedStr = storage.getString(cacheKey);
      let cached: CachedRecs | null = null;
      if (cachedStr) {
        try { cached = JSON.parse(cachedStr); } catch {}
      }

      // Use cache if fresh
      if (!bypassCache && cached && Date.now() - cached.cachedAt < CACHE_TTL_MS) {
        setBundles(cached.bundles);
        setChurnScore(cached.churn);
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const api = new ApiClient({
          tenantId: tenantId ?? 'unknown',
          baseUrl: (typeof process !== 'undefined' &&
            (process as any)?.env?.MOBILE_API_BASE_URL) ?? 'http://localhost:8080',
          getAccessToken: () => auth.accessToken,
        });
        const ai = new AIClient(api, tenantId ?? 'unknown');

        const [bundles, churn] = await Promise.all([
          ai.getBundleRecommendations(connectionId, 5),
          ai.getChurnScore(connectionId),
        ]);

        setBundles(bundles);
        setChurnScore(churn);

        // Cache
        const toCache: CachedRecs = { bundles, churn, cachedAt: Date.now() };
        storage.set(cacheKey, JSON.stringify(toCache));
      } catch (e: any) {
        setError(e.message);
        if (cached) {
          // Show stale data on error
          setBundles(cached.bundles);
          setChurnScore(cached.churn);
        }
      } finally {
        setLoading(false);
      }
    },
    [connectionId, tenantId, auth.accessToken]
  );

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  return {
    bundles,
    churnScore,
    loading,
    error,
    refresh: () => fetchAll(true),
  };
}
