import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(`${__ENV.BASE_URL}/api/news?page=1&size=1`);

  check(response, {
    'news API returns 200': (result) => result.status === 200,
  });

  sleep(1);
}
