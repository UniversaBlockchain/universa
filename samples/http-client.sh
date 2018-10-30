#!/bin/bash

f=/tmp/script.js
out="./files/contract-http-client.unicon"

if [ ! -f $out ]; then

  printf "\ncreate javascript file with http client code...\n"
  rm $f
  cat > $f << EndOfMessage
var jsApiEvents = new Object();
jsApiEvents.main = function() {
  var httpClient = jsApi.getHttpClient();
  var res0 = httpClient.sendGetRequest('http://localhost:8880/contract1/getRevision', 'text');
  var res1 = httpClient.sendGetRequest('http://localhost:8880/contract1/getTime', 'text');
  var res2 = httpClient.sendGetRequest('http://localhost:8880/contract1/getIssuer', 'text');
  print('/contract1/getRevision: ' + res0);
  print('/contract1/getTime' + res1);
  print('/contract1/getIssuer' + res2);
}
EndOfMessage

  printf "\ncreate new contract with this javascript...\n"
  java -jar ./files/uniclient.jar --create-contract-with-js $f -o $out -k ./files/key.priv

  printf "\nregister contract...\n"
  java -jar ./files/uniclient.jar --register $out -k ./files/key.priv -u ./files/Upackage.unicon

else

  printf "\ncontract $out already exists, registration skipped\n"

fi

printf "\nexecute js from contract...\n"
java -jar ./files/uniclient.jar --exec-js $out

printf "\ndone\n"
