#!/usr/bin/env bash
# Gateway :8080 üzerinden Milestone 1 smoke. Servisler + api-gateway ayakta olmalı.
set -euo pipefail

GW=http://localhost:8080

json_field() {
  python3 -c "import json,sys; print(json.load(sys.stdin)${1})"
}

echo "== gateway health =="
curl -sf "${GW}/actuator/health" | json_field "['status']"

echo "== restaurant via gateway =="
REST=$(curl -sS -X POST "${GW}/api/restaurants" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Adana Sofrası","address":"Kadıköy","cuisine":"Türk","open":true}')
echo "$REST"
RID=$(echo "$REST" | json_field "['id']")
curl -sf "${GW}/api/restaurants/${RID}" >/dev/null
curl -sS -o /dev/null -w "DELETE restaurant %{http_code}\n" -X DELETE "${GW}/api/restaurants/${RID}"

echo "== customer + address via gateway =="
CUS=$(curl -sS -X POST "${GW}/api/customers" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Ali Veli","email":"ali@example.com","phone":"5551112233"}')
echo "$CUS"
CID=$(echo "$CUS" | json_field "['id']")
curl -sS -X POST "${GW}/api/customers/${CID}/addresses" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Ev","street":"Caferağa","city":"İstanbul"}' >/dev/null
curl -sf "${GW}/api/customers/${CID}/addresses" >/dev/null

echo "== order / payment / delivery / notification via gateway =="
ORD=$(curl -sS -X POST "${GW}/api/orders" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"${CID}\",\"restaurantId\":\"22222222-2222-2222-2222-222222222222\",\"totalAmount\":149.90}")
echo "$ORD"
OID=$(echo "$ORD" | json_field "['id']")
PAY=$(curl -sS -X POST "${GW}/api/payments" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"${OID}\",\"amount\":149.90}")
echo "$PAY"
DEL=$(curl -sS -X POST "${GW}/api/deliveries" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"${OID}\",\"courierName\":\"Ahmet\"}")
echo "$DEL"
NOTIF=$(curl -sS -X POST "${GW}/api/notifications" \
  -H 'Content-Type: application/json' \
  -d '{"recipient":"5551112233","channel":"SMS","message":"Siparis alindi"}')
echo "$NOTIF"

echo "== errors via gateway =="
curl -sS -o /dev/null -w "404 %{http_code}\n" "${GW}/api/orders/00000000-0000-0000-0000-000000000000"
curl -sS -o /dev/null -w "400 %{http_code}\n" -X POST "${GW}/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"00000000-0000-0000-0000-000000000001","restaurantId":"00000000-0000-0000-0000-000000000002","totalAmount":-1}'

echo "OK"
