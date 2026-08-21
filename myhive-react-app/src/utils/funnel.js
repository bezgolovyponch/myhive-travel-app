// Funnel-param helpers: compute the P0 params (nights, vote_id, source_campaign,
// group_size, activities_count) at the call site so pushEvent stays stateless.
import { getAttribution } from './attribution';

const MS_PER_NIGHT = 24 * 60 * 60 * 1000;

export function nightsBetween(startDate, endDate) {
  if (!startDate || !endDate) return undefined;
  const start = new Date(startDate).getTime();
  const end = new Date(endDate).getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return undefined;
  return Math.round((end - start) / MS_PER_NIGHT);
}

export function funnelParams({ startDate, endDate, groupSize, activitiesCount, voteId } = {}) {
  return {
    nights: nightsBetween(startDate, endDate),
    group_size: groupSize,
    activities_count: activitiesCount,
    vote_id: voteId,
    source_campaign: getAttribution().utm_campaign,
  };
}
