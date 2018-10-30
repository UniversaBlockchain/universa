#!/bin/bash

f=/tmp/script.js
r=/tmp/routes.json
out="./files/contract-http-server.unicon"

if [ ! -f $out ]; then

  printf "\ncreate javascript file with http server code...\n"
  rm $f
  cat > $f << EndOfMessage
var jsApiEvents = new Object();
jsApiEvents.httpHandler_getRevision = function(request, response) {
  response.setBodyAsPlainText('revision: '+jsApi.getCurrentContract().getRevision());
};
jsApiEvents.httpHandler_getTime = function(request, response) {
  var currentDate = new Date();
  response.setBodyAsPlainText(currentDate.toLocaleDateString() + ' ' + currentDate.toLocaleTimeString());
};
jsApiEvents.httpHandler_getIssuer = function(request, response) {
  response.setBodyAsPlainText(jsApi.getCurrentContract().getIssuer());
};
EndOfMessage

  printf "\ncreate new contract with this javascript...\n"
  java -jar ./files/uniclient.jar --create-contract-with-js $f -o $out -k ./files/key.priv

  printf "\nregister contract...\n"
  java -jar ./files/uniclient.jar --register $out -k ./files/key.priv -u ./files/Upackage.unicon

else

  printf "\ncontract $out already exists, registration skipped\n"

fi

printf "\ncreate routes.json...\n"
rm $r 2>/dev/null
cat > $r << EndOfMessage
{
  "listenPort": "8880",
  "routes": [
    {"endpoint": "/contract1/getRevision", "handlerName": "httpHandler_getRevision", "contractPath": "$out", "scriptName": "script.js"},
    {"endpoint": "/contract1/getTime", "handlerName": "httpHandler_getTime", "contractPath": "$out", "scriptName": "script.js"},
    {"endpoint": "/contract1/getIssuer", "handlerName": "httpHandler_getIssuer", "contractPath": "$out", "scriptName": "script.js"}
  ]
}
EndOfMessage


printf "\nstart http server...\n"
java -jar ./files/uniclient.jar --start-http-server $r

printf "\ndone\n"
