package com.icodici.universa.node2.network;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.ExtendedSignature;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import net.sergeych.utils.Base64u;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TopologyBuilder {
    private final List<Binder> inputList;

    public static final String TOPOLOGY_DIR = System.getProperty("user.home")+"/.universa/topology/";

    private static final double MIN_CONFIRMED_RATIO = 0.4;
    private static final double CONFIRMED_QUORUM_RATIO = 0.9;
    private final File cachedFile;
    private String version;
    private boolean reacquireDone;
    Set<PublicKey> knownKeys = new HashSet<>();
    Map<PublicKey, List<Binder>> nodeCoordinates = new HashMap<>();
    Set<PublicKey> confirmedKeys = new HashSet<>();
    Set<PublicKey> processedKeys = ConcurrentHashMap.newKeySet();
    Object updateLock = new Object();


    private static Binder extractTopologyFromStream(InputStream inputStream) throws IOException {
        Object result = JsonTool.fromJson(new String(Do.read(inputStream)));
        if(result instanceof List) {
            Map<String,Object> map = new HashMap<>();
            map.put("list",result);
            map.put("updated", 0);
            return Binder.convertAllMapsToBinders(map);
        } else {
            return Binder.convertAllMapsToBinders(result);
        }

    }


    private List<Binder> loadTopologyFrom(String someNodeUrl, PublicKey verifyWith) throws IOException {

        if(someNodeUrl.startsWith("https")) {
            someNodeUrl = someNodeUrl.replace(":8080","");
        }

        URL url = new URL(someNodeUrl + "/" + (verifyWith == null ? "network" : "topology"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
        connection.setConnectTimeout(10000);
        connection.setRequestMethod("GET");
        if (connection.getResponseCode() != 200)
            throw new IOException("failed to access " + url + ", reponseCode " + connection.getResponseCode());

        byte[] bytes = (Do.read(connection.getInputStream()));


        Binder bres = Boss.unpack(bytes)
                .getBinderOrThrow("response");

        List<Binder> topology = null;

        byte[] packedData = bres.getBinaryOrThrow("packed_data");
        byte[] signature = bres.getBinaryOrThrow("signature");
        if (ExtendedSignature.verify(verifyWith, signature, packedData) == null) {
            throw new IOException("failed to verify node " + url + ", with " + verifyWith);
        }
        bres = Boss.unpack(packedData);

        topology = bres.getListOrThrow("nodes");

        this.version = bres.getStringOrThrow("version");

        if(topology != null) {
            for (Object o : topology) {
                Binder b = (Binder) o;
                b.put("key", new PublicKey(b.getBinaryOrThrow("key")));
            }
        }

        return topology;
    }

    private void receiveFrom(Binder inputInfo, PublicKey key) {
        //performing two attempts. 1st by ip over http, 2nd by hostname over https
        for(int attempt = 0; attempt < 2; attempt++) {
            try {
                List<Binder> receivedTopology = loadTopologyFrom((String) ((List) inputInfo.get(attempt == 0 ? "direct_urls" : "domain_urls")).get(0), key);

                synchronized (updateLock) {
                    for (Binder receivedInfo : receivedTopology) {
                        PublicKey receivedKey = (PublicKey) receivedInfo.get("key");

                        if (!nodeCoordinates.containsKey(receivedKey)) {
                            nodeCoordinates.put(receivedKey, new ArrayList<>());
                        }
                        receivedInfo.put("key", receivedKey.packToBase64String());
                        nodeCoordinates.get(receivedKey).add(receivedInfo);
                    }
                    confirmedKeys.add(key);
                }
                break;
            } catch (IOException ignored) {

            }
        }

    }

    private void doReacquire() {
        reacquireDone = true;
        processedKeys.clear();

        for (PublicKey knownKey : knownKeys) {
            synchronized (updateLock) {
                if (confirmedKeys.contains(knownKey)) {
                    processedKeys.add(knownKey);
                    continue;
                }
            }

            if (nodeCoordinates.containsKey(knownKey)) {
                Do.inParallel(() -> {

                    Binder inputInfo;
                    synchronized (updateLock) {
                        Map<Binder, Long> result = nodeCoordinates.get(knownKey).stream()
                                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                        long max = Collections.max(result.values());

                        inputInfo = result.entrySet().stream().filter(entry -> entry.getValue() == max).map(Map.Entry::getKey).findFirst().get();
                    }
                    receiveFrom(inputInfo,knownKey);
                    processedKeys.add(knownKey);
                });
            } else {
                processedKeys.add(knownKey);
            }
        }
    }

    public TopologyBuilder(String topologyInput, String topologyCacheDir) throws IOException {
        if (topologyCacheDir == null) {
            topologyCacheDir = TOPOLOGY_DIR;
        }

        if (!topologyCacheDir.endsWith("/")) {
            topologyCacheDir = topologyCacheDir + "/";
        }

        File providedFile = null;
        InputStream resourceStream = null;
        String topologyName;

        ClassLoader classLoader = getClass().getClassLoader();
        Files.createDirectories(Paths.get(topologyCacheDir));


        //create new / update existing from a given file
        if (topologyInput.endsWith(".json")) {
            providedFile = new File(topologyInput);
            topologyName = providedFile.getName().substring(0, providedFile.getName().length() - ".json".length());

        } else {
            topologyName = topologyInput;
        }

        this.cachedFile = new File(topologyCacheDir + topologyName + ".json");
        resourceStream = classLoader.getResourceAsStream("topologies/" + topologyName + ".json");

        Binder topology = null;

        if (providedFile != null && providedFile.exists()) {
            Binder providedTopology = extractTopologyFromStream(new FileInputStream(providedFile));
            if (topology == null || providedTopology.getLong("updated", 0) > topology.getLong("updated", 0)) {
                topology = providedTopology;
            }
        }

        if (cachedFile.exists()) {
            Binder cachedTopology = extractTopologyFromStream(new FileInputStream(cachedFile));
            if (topology == null || cachedTopology.getLong("updated", 0) > topology.getLong("updated", 0)) {
                topology = cachedTopology;
            }
        } else {
            if (!new File(topologyCacheDir).exists())
                Files.createDirectories(Paths.get(topologyCacheDir));
        }

        if (resourceStream != null) {
            Binder resourceTopology = extractTopologyFromStream(resourceStream);
            if (topology == null || resourceTopology.getLong("updated", 0) > topology.getLong("updated", 0)) {
                topology = resourceTopology;
            }
        }


        if (topology == null)
            throw new IllegalArgumentException("Topology is not provided/not found in cache or resources");


        this.inputList = (List<Binder>) topology.get("list");

        for (Binder inputInfo : inputList) {
            PublicKey inputKey = new PublicKey(Base64u.decodeCompactString((String) inputInfo.get("key")));
            knownKeys.add(inputKey);
        }
        nodeCoordinates.clear();
        confirmedKeys.clear();
        reacquireDone = false;
        //Collecting data from last known topology
        for (Binder inputInfo : inputList) {
            Do.inParallel(() -> {
                PublicKey inputKey = new PublicKey(Base64u.decodeCompactString((String) inputInfo.get("key")));
                receiveFrom(inputInfo,inputKey);
                processedKeys.add(inputKey);
            });
        }

        Do.waitFor(() -> processedKeys.size() == knownKeys.size() || confirmedKeys.size() >= knownKeys.size() * MIN_CONFIRMED_RATIO);

        //All keys processed. We still don't have MIN_CONFIRMED_RATIO
        //Trying to reacquire connection to known nodes using the updated information from confirmed nodes
        if (confirmedKeys.size() < knownKeys.size() * MIN_CONFIRMED_RATIO) {
            doReacquire();
            Do.waitFor(() -> processedKeys.size() == knownKeys.size() || confirmedKeys.size() >= knownKeys.size() * MIN_CONFIRMED_RATIO );
        }

        if (confirmedKeys.size() < knownKeys.size() * MIN_CONFIRMED_RATIO)
            throw new IllegalStateException("Topology can't be trusted");

        while(true) {
            Set<PublicKey> confirmedKeysSnapshot;
            Map<PublicKey, List<Binder>> nodeCoordinatesSnapshot;
            synchronized (updateLock) {
                confirmedKeysSnapshot = new HashSet<>(confirmedKeys);
                nodeCoordinatesSnapshot = new HashMap<>();
                nodeCoordinates.forEach((k,v) -> nodeCoordinatesSnapshot.put(k,new ArrayList<>(v)));
            }

            inputList.clear();

            for (List<Binder> list : nodeCoordinatesSnapshot.values()) {
                Map<Binder, Long> result = list.stream()
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                long max = Collections.max(result.values());
                if (max > confirmedKeysSnapshot.size() * CONFIRMED_QUORUM_RATIO) {
                    Binder inputInfo = result.entrySet().stream().filter(entry -> entry.getValue() == max).map(Map.Entry::getKey).findFirst().get();
                    inputList.add(inputInfo);
                }

            }

            if (inputList.size() >= knownKeys.size() * MIN_CONFIRMED_RATIO) {
                break;
            } else {
                if(!reacquireDone) {
                    doReacquire();
                } else {
                    if(confirmedKeysSnapshot.size() == knownKeys.size()) {
                        throw new IllegalStateException("Topology can't be trusted");
                    }
                }
            }
        }


        Collections.sort(inputList, Comparator.comparingInt(o -> Integer.parseInt(o.get("number").toString())));


        Binder dataToCache = Binder.of("updated", ZonedDateTime.now().toEpochSecond(), "list", inputList);

        FileOutputStream fos = new FileOutputStream(cachedFile);
        fos.write(JsonTool.toJsonString(dataToCache).getBytes());
        fos.close();
    }


    public List<Binder> getTopology() throws IOException {
        return inputList;
    }

    public String getVersion() {
        return version;
    }

}
