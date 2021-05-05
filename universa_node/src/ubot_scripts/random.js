const BigDecimal  = require("big").Big;

async function getRandom(max) {
    //generate random and write its hash to multi storage
    let rnd = Math.random();
    let hash = crypto.HashId.of(rnd.toString()).base64;

    await writeMultiStorage({hash : hash});

    //calculate hash of hashes and write it to single storage
    let records = await getMultiStorage();
    let hashes = [];
    for (let r of records)
        hashes.push(r.hash);

    hashes.sort();
    let hashesHash = crypto.HashId.of(hashes.join()).base64;
    await writeSingleStorage({hashesHash : hashesHash});

    //add actual random to multi storage
    await writeMultiStorage({hash : hash, rnd : rnd});

    //verify hashesOfHash and rnd -> hash
    records = await getMultiStorage();
    hashes = [];
    let rands = [];
    for (let r of records) {
        if (r.hash !== crypto.HashId.of(r.rnd.toString()).base64)
            throw new Error("Hash does not match the random value");

        hashes.push(r.hash);
        rands.push(r.rnd.toString());
    }
    hashes.sort();
    rands.sort();
    hashesHash = crypto.HashId.of(hashes.join()).base64;

    let singleStorage = await getSingleStorage();
    if (hashesHash !== singleStorage.hashesHash)
        throw new Error("Hash of hashes does not match the previously saved: " + hashesHash + "!==" + singleStorage.hashesHash);

    let randomHash = crypto.HashId.of(rands.join());
    let bigRandom = new BigDecimal(0);
    randomHash.digest.forEach(byte => bigRandom = bigRandom.mul(256).add(byte));

    let result = Number.parseInt(bigRandom.mod(max).toFixed());

    await writeSingleStorage({hashesHash: hashesHash, result: result});

    return result;
}

async function readRandom() {
    return (await getSingleStorage()).result;
}