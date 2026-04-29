import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const consumerLag = new Trend('consumer_lag');
const confirmedRevenue = new Trend('confirmed_revenue');

export const options = {
    scenarios: {
        order_load: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: 100 },
                { duration: '2m', target: 100 },
                { duration: '30s', target: 500 },
                { duration: '2m', target: 500 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
        analytics_poller: {
            executor: 'constant-vus',
            vus: 1,
            duration: '5m30s',
            exec: 'pollAnalytics',
        },
    },
    thresholds: {
        'http_req_duration{scenario:order_load}': ['p(95)<2000'],
        'http_req_failed{scenario:order_load}': ['rate<0.1'],
    },
};

const PRODUCTS = [
    '550e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440004',
    '550e8400-e29b-41d4-a716-446655440005',
];

export default function () {
    const payload = JSON.stringify({
        customerId: '550e8400-e29b-41d4-a716-446655440000',
        items: [
            {
                productId: PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)],
                quantity: Math.floor(Math.random() * 3) + 1,
                pricePerItem: 10.00,
            },
        ],
    });

    const res = http.post('http://localhost:8080/api/v1/orders', payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, {
        'status is 201': (r) => r.status === 201,
        'has orderId': (r) => JSON.parse(r.body).orderId !== undefined,
    });

    sleep(1);
}

export function pollAnalytics() {
    const topProducts = http.get('http://localhost:8084/api/v1/analytics/top-products');
    const outcomes = http.get('http://localhost:8084/api/v1/analytics/payment-outcomes');
    const revenue = http.get('http://localhost:8084/api/v1/analytics/confirmed-revenue');
    const ordersConnector = http.get('http://localhost:8083/connectors/debezium-orders-outbox/status');
    const paymentsConnector = http.get('http://localhost:8083/connectors/debezium-payments-outbox/status');

    if (topProducts.status === 200 && outcomes.status === 200) {
        const products = JSON.parse(topProducts.body);
        const outcomesData = JSON.parse(outcomes.body);

        const totalOrders = Object.values(products).reduce((a, b) => a + b, 0);
        const totalPayments = (outcomesData.SUCCESS || 0) + (outcomesData.FAILED || 0);
        const lag = totalOrders - totalPayments;

        consumerLag.add(lag);
        console.log(`Orders: ${totalOrders} | Payments: ${totalPayments} | Lag: ${lag} | SUCCESS: ${outcomesData.SUCCESS || 0} | FAILED: ${outcomesData.FAILED || 0}`);
    }

    if (revenue.status === 200) {
        const revenueValue = parseFloat(revenue.body);
        confirmedRevenue.add(revenueValue);
        console.log(`Confirmed Revenue: ${revenueValue}`);
    }

    if (ordersConnector.status === 200) {
        const status = JSON.parse(ordersConnector.body);
        console.log(`Orders connector: ${status.connector.state} | Task: ${status.tasks[0]?.state}`);
    }

    if (paymentsConnector.status === 200) {
        const status = JSON.parse(paymentsConnector.body);
        console.log(`Payments connector: ${status.connector.state} | Task: ${status.tasks[0]?.state}`);
    }

    sleep(5);
}