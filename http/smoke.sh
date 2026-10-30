#!/usr/bin/env bash
# Gateway :8080 üzerinden Milestone 4 smoke (JWT + CRUD + saga).
# Servisler + api-gateway + Kafka ayakta olmalı.
set -euo pipefail

GW=http://localhost:8080

json_field() {
  python3 -c "import json,sys; print(json.load(sys.stdin)${1})"
}

AUTH=()

api() {
  curl -sS "${AUTH[@]}" "$@"
}

api_f() {
  curl -sf "${AUTH[@]}" "$@"
}

wait_order_status() {
  local id="$1"
  local expected="$2"
  local body=""
  local status=""
  for i in $(seq 1 20); do
    body=$(api_f "${GW}/api/orders/${id}")
    status=$(echo "$body" | json_field "['status']")
    echo "  poll ${i}: ${status}"
    if [ "${status}" = "${expected}" ]; then
      echo "${body}"
      return 0
    fi
    sleep 1
  done
  echo "timeout waiting for ${expected}, last=${body}" >&2
  return 1
}

echo "== gateway health (JWT yok) =="
curl -sf "${GW}/actuator/health" | json_field "['status']"

echo "== 401 without token =="
curl -sS -o /dev/null -w "orders %{http_code}\n" "${GW}/api/orders"

EMAIL="ali.m4.$(date +%s)@example.com"

echo "== customer + login =="
CUS=$(curl -sS -X POST "${GW}/api/customers" \
  -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Ali Veli\",\"email\":\"${EMAIL}\",\"phone\":\"5551112233\"}")
echo "$CUS"
CID=$(echo "$CUS" | json_field "['id']")
LOGIN=$(curl -sS -X POST "${GW}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\"}")
echo "$LOGIN"
TOKEN=$(echo "$LOGIN" | json_field "['accessToken']")
AUTH=(-H "Authorization: Bearer ${TOKEN}")

echo "== restaurant via gateway =="
REST=$(api -X POST "${GW}/api/restaurants" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Adana Sofrası","address":"Kadıköy","cuisine":"Türk","open":true}')
echo "$REST"
RID=$(echo "$REST" | json_field "['id']")
api_f "${GW}/api/restaurants/${RID}" >/dev/null

echo "== address via gateway =="
api -X POST "${GW}/api/customers/${CID}/addresses" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Ev","street":"Caferağa","city":"İstanbul"}' >/dev/null
api_f "${GW}/api/customers/${CID}/addresses" >/dev/null

echo "== CRUD order (status=PENDING, saga yok) =="
ORD=$(api -X POST "${GW}/api/orders" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"${CID}\",\"restaurantId\":\"${RID}\",\"totalAmount\":149.90,\"status\":\"PENDING\"}")
echo "$ORD"
OID=$(echo "$ORD" | json_field "['id']")
PAY=$(api -X POST "${GW}/api/payments" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"${OID}\",\"amount\":149.90}")
echo "$PAY"
DEL=$(api -X POST "${GW}/api/deliveries" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"${OID}\",\"courierName\":\"Ahmet\"}")
echo "$DEL"
NOTIF=$(api -X POST "${GW}/api/notifications" \
  -H 'Content-Type: application/json' \
  -d '{"recipient":"5551112233","channel":"SMS","message":"Siparis alindi"}')
echo "$NOTIF"

echo "== saga mutlu yol (forceRestaurantAccept) =="
SAGA_OK=$(api -X POST "${GW}/api/orders" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"${CID}\",\"restaurantId\":\"${RID}\",\"totalAmount\":149.90,\"forceRestaurantAccept\":true}")
echo "$SAGA_OK"
SID=$(echo "$SAGA_OK" | json_field "['id']")
wait_order_status "${SID}" "CONFIRMED" >/dev/null

echo "== outbox published (order-service) =="
OUTBOX=$(api_f "${GW}/api/orders/outbox")
echo "$OUTBOX" | python3 -c "
import json,sys
rows=json.load(sys.stdin)
sid='''${SID}'''
mine=[r for r in rows if r.get('messageKey')==sid]
topics={r.get('topic') for r in mine}
assert any(r.get('publishedAt') for r in mine), mine
assert 'payment-commands' in topics, topics
print('outbox rows for order:', len(mine), 'topics:', sorted(topics))
"

echo "== order history (CQRS) =="
HIST=$(api_f "${GW}/api/orders/${SID}/history")
echo "$HIST" | python3 -c "
import json,sys
rows=json.load(sys.stdin)
steps=[r.get('step') for r in rows]
assert 'ORDER_CREATED' in steps and 'SAGA_STARTED' in steps, steps
assert 'PAYMENT_CHARGED' in steps and 'COURIER_ASSIGNED' in steps, steps
print('history steps:', steps)
"

echo "== processed events (payment) =="
PE=$(api_f "${GW}/api/payments/processed-events")
echo "$PE" | python3 -c "
import json,sys
rows=json.load(sys.stdin)
sid='''${SID}'''
mine=[r for r in rows if r.get('orderId')==sid]
assert any(r.get('eventType')=='ChargePaymentCommand' for r in mine), mine
print('processed charge rows:', len(mine))
"

echo "== saga compensate (forceRestaurantReject) =="
SAGA_KO=$(api -X POST "${GW}/api/orders" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"${CID}\",\"restaurantId\":\"${RID}\",\"totalAmount\":89.50,\"forceRestaurantReject\":true}")
echo "$SAGA_KO"
KID=$(echo "$SAGA_KO" | json_field "['id']")
wait_order_status "${KID}" "CANCELLED" >/dev/null

echo "== errors via gateway =="
api -o /dev/null -w "404 %{http_code}\n" "${GW}/api/orders/00000000-0000-0000-0000-000000000000"
api -o /dev/null -w "400 %{http_code}\n" -X POST "${GW}/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"00000000-0000-0000-0000-000000000001","restaurantId":"00000000-0000-0000-0000-000000000002","totalAmount":-1}'

echo "OK"
