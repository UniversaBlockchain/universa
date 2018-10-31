#!/bin/bash

printf "\ncreate routes-slot1.json...\n"
r=/tmp/routes-slot1.json
rm $r 2>/dev/null
cat > $r << EndOfMessage
{
  "listenPort": "8880",
  "routes": [
    {
      "endpoint": "/contract1/getRevision",
      "handlerName": "httpHandler_getRevision",
      "scriptName": "script1.js",
      "slotId": "g9p2MJ0ZtdL6XlRr1MIgcPrV/50ML36BmQKwRrNoiKq54XvcweXXZSkrs6AYdoM3NdxORD/5HTBjiaOw6Or4NLRa00VEGsNTGRGDbWnwd/8/ZZFbb6wZL/chRwT+YaZZ",
      "originId": "UbcDWVCgkAoZQDA/39u3rdg+GJL/GhmdqEAeYG7ODF2I0gmJ82nyf77XhjceKrp2o9VIjTY4nj25xfSS4VGSannbUA6E7IpwN+Ci/S8sCTBZX2gkxiok/GVMQIbd3UUE"
    },
    {
      "endpoint": "/contract1/getTime",
      "handlerName": "httpHandler_getTime",
      "scriptName": "script1.js",
      "slotId": "g9p2MJ0ZtdL6XlRr1MIgcPrV/50ML36BmQKwRrNoiKq54XvcweXXZSkrs6AYdoM3NdxORD/5HTBjiaOw6Or4NLRa00VEGsNTGRGDbWnwd/8/ZZFbb6wZL/chRwT+YaZZ",
      "originId": "UbcDWVCgkAoZQDA/39u3rdg+GJL/GhmdqEAeYG7ODF2I0gmJ82nyf77XhjceKrp2o9VIjTY4nj25xfSS4VGSannbUA6E7IpwN+Ci/S8sCTBZX2gkxiok/GVMQIbd3UUE"
    },
    {
      "endpoint": "/contract1/getIssuer",
      "handlerName": "httpHandler_getIssuer",
      "scriptName": "script1.js",
      "slotId": "g9p2MJ0ZtdL6XlRr1MIgcPrV/50ML36BmQKwRrNoiKq54XvcweXXZSkrs6AYdoM3NdxORD/5HTBjiaOw6Or4NLRa00VEGsNTGRGDbWnwd/8/ZZFbb6wZL/chRwT+YaZZ",
      "originId": "UbcDWVCgkAoZQDA/39u3rdg+GJL/GhmdqEAeYG7ODF2I0gmJ82nyf77XhjceKrp2o9VIjTY4nj25xfSS4VGSannbUA6E7IpwN+Ci/S8sCTBZX2gkxiok/GVMQIbd3UUE"
    }
  ]
}
EndOfMessage

printf "\nstart http server from slot1...\n"
java -jar ./files/uniclient.jar --start-http-server /tmp/routes-slot1.json

printf "\ndone\n"
