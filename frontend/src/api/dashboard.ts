import type {DashboardSummary} from "../types/dashboard.ts";
import {apiFetch} from "./client.ts";

export async function fetchDashboardSummary(): Promise<DashboardSummary> {

    return apiFetch<DashboardSummary>('/api/dashboard/summary');
}