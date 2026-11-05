// Performance gate (ADR-0014): a short, steady load on the booking API's main paths.
// Limits are not set here: the gate evaluator applies them from quality-gates.yml, so every
// threshold in the project lives in one file. This script only produces the measurements.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.API_BASE_URL || 'http://localhost:3001';
const HEADERS = { 'Content-Type': 'application/json', Accept: 'application/json' };

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const list = http.get(`${BASE}/booking`, { headers: HEADERS, tags: { name: 'list bookings' } });
  check(list, { 'list: 200': r => r.status === 200 });

  const body = JSON.stringify({
    firstname: `Load${__VU}`,
    lastname: `Iter${__ITER}`,
    totalprice: 245,
    depositpaid: true,
    bookingdates: { checkin: '2027-03-01', checkout: '2027-03-05' },
    additionalneeds: 'Breakfast',
  });
  const created = http.post(`${BASE}/booking`, body, { headers: HEADERS, tags: { name: 'create booking' } });
  check(created, { 'create: 200': r => r.status === 200 });

  if (created.status === 200) {
    const id = created.json('bookingid');
    const read = http.get(`${BASE}/booking/${id}`, { headers: HEADERS, tags: { name: 'get booking' } });
    check(read, { 'get: 200': r => r.status === 200 });
  }
}

export function handleSummary(data) {
  const out = __ENV.SUMMARY_FILE || 'gate-results/k6-summary.json';
  return { [out]: JSON.stringify(data, null, 2) };
}
