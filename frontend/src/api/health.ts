import type { HealthResponse } from '../types/health'

export async function fetchHealth(): Promise<HealthResponse> {
  // Wait 5 seconds max for a response
  const response = await fetch('/api/health', { signal: AbortSignal.timeout(5000)});

  // 200 - All up and 503 - DB down

  if(!response.ok && response.status !== 503) {
    throw new Error(`Unexpected response from /api/health: ${response.status}`);
  }

  return (await response.json()) as HealthResponse;
}
