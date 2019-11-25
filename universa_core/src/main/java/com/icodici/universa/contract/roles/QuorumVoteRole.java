package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.AnonymousId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@BiType(name = "QuorumVoteRole")
public class QuorumVoteRole extends Role {

    private Long votesCount;


    public enum QuorumOperators {
        OPERATOR_ADD,
        OPERATOR_SUBTRACT
    }

    public static final Map<Character,QuorumOperators> operatorSymbols  = new HashMap();

    static {
        operatorSymbols.put('+',QuorumOperators.OPERATOR_ADD);
        operatorSymbols.put('-',QuorumOperators.OPERATOR_SUBTRACT);
    }

    public QuorumVoteRole() {

    }

    private List<String> quorumValues;
    private List<QuorumOperators> quorumOperators;

    public QuorumVoteRole(String name, Contract contract, String source, String quorum) {
        super(name,contract);
        this.source = source;
        this.quorum = quorum;

        int idx = source.indexOf(".");
        String from = source.substring(0,idx);
        String what = source.substring(idx+1);


        extractValuesAndOperators();

    }

    private void extractValuesAndOperators() {
        quorumValues = new ArrayList<>();
        quorumOperators = new ArrayList<>();

        int pos = 0;
        for(int i = 0; i < quorum.length(); i++) {
            if(operatorSymbols.containsKey(quorum.charAt(i))) {
                String value = quorum.substring(pos,i);
                if(value.length() == 0) {
                    throw new IllegalArgumentException("Invalid quorum format");
                }
                quorumValues.add(value);
                quorumOperators.add(operatorSymbols.get(quorum.charAt(i)));
                pos = i+1;
            }
        }
        String value = quorum.substring(pos);
        if(value.length() == 0) {
            throw new IllegalArgumentException("Invalid quorum format");
        }
        quorumValues.add(value);
    }


    String source;
    String quorum;


    @Override
    public boolean isValid() {
        //TODO: additional check (parse quorum and source)
        return source != null && quorum != null;
    }

    @Override
    protected boolean equalsIgnoreNameAndRefs(Role otherRole) {
        return false;
    }

    @Override
    public void initWithDsl(Binder serializedRole) {
        source = serializedRole.getStringOrThrow("source");
        quorum = serializedRole.getStringOrThrow("quorum");
        int idx = source.indexOf(".");
        String from = source.substring(0,idx);
        String what = source.substring(idx+1);
    }

    @Override
    public Set<PublicKey> getKeys() {
        return null;
    }

    @Override
    public Set<AnonymousId> getAnonymousIds() {
        return null;
    }

    @Override
    public Set<KeyAddress> getKeyAddresses() {
        return null;
    }

    @Override
    public Set<KeyRecord> getKeyRecords() {
        return null;
    }

    @Override
    public void anonymize() {

    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        this.quorum = data.getStringOrThrow("quorum");
        this.source = data.getStringOrThrow("source");
        extractValuesAndOperators();
    }

    @Override
    public Binder serialize(BiSerializer s) {

        return super.serialize(s).putAll(
                "quorum", quorum,
                "source", source);
    }

    /**
     * Check role is allowed to keys
     *
     * @param keys is set of keys
     * @return true if role is allowed to keys
     */
    @Override
    public boolean isAllowedForKeysQuantized(Set<? extends AbstractKey> keys) throws Quantiser.QuantiserException {
        if(!super.isAllowedForKeysQuantized(keys)) {
            return false;
        }

        List<KeyAddress> votingAddresses;
        try {
            votingAddresses = getVotingAddresses();
        } catch (Exception e) {
            return false;
        }


        long minValidCount = calculateMinValidCount(votingAddresses.size());

        if(votesCount != null) {
            return minValidCount <= votesCount;
        } else {
            for (KeyAddress va : votingAddresses) {

                if (keys.stream().anyMatch(k -> ((AbstractKey) k).isMatchingKeyAddress(va))) {
                    minValidCount--;
                }

                if (minValidCount == 0) {
                    break;
                }
            }

            return minValidCount == 0;
        }
    }

    private long calculateMinValidCount(long totalVotesCount) {
        long value = 0;
        for(int i = 0; i < quorumValues.size();i++) {
            long curValue;
            String valueString = quorumValues.get(i);
            boolean isPercentageBased = valueString.endsWith("%");
            if(isPercentageBased) {
                if(totalVotesCount == 0)
                    throw new IllegalArgumentException("Percentage based quorum requires vote list to be provided at registration");
                valueString = valueString.substring(0,valueString.length()-1);
            } else if(valueString.equals("N")) {
                curValue = totalVotesCount;
            }

            try {
                curValue = isPercentageBased ? (long) Math.floor(totalVotesCount * Double.parseDouble(valueString) / 100) : Long.parseLong(valueString);
            } catch (NumberFormatException ignored) {
                int idx = valueString.indexOf(".");
                String from;
                String what;
                if(idx == -1) {
                    from = "this";
                    what = "state.data."+valueString;
                } else {
                    from = valueString.substring(0,idx);
                    what = valueString.substring(idx+1);
                }

                if(from.equals("this")) {
                    valueString = getContract().get(what).toString();
                } else {
                    Reference ref = getContract().getReferences().get(from);
                    if(ref == null) {
                        throw  new IllegalArgumentException("Reference with name '" + from + "' wasn't found for role " + getName());
                    }

                    if(ref.matchingItems.size() != 1) {
                        throw  new IllegalArgumentException("Reference with name '" + from + "' should be matching exactly one contract within transaction to be used in QuorumVoteRole");
                    }

                    valueString = ((Contract)ref.matchingItems.get(0)).get(what).toString();
                }

                try {
                    curValue = isPercentageBased ? (long) Math.floor(totalVotesCount * Double.parseDouble(valueString) / 100) : Long.parseLong(valueString);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            }


            if(i == 0) {
                value = curValue;
            } else {
                switch (quorumOperators.get(i-1)) {
                    case OPERATOR_SUBTRACT:
                        value -= curValue;
                        break;
                    case OPERATOR_ADD:
                        value += curValue;
                        break;
                }
            }
        }

        return value;
    }

    public List<KeyAddress> getVotesForKeys(Set<? extends AbstractKey> keys) {
        List<KeyAddress> votingAddresses = getVotingAddresses();

        List<KeyAddress> result = new ArrayList<>();
        for(KeyAddress va : votingAddresses) {
            if(keys.stream().anyMatch(k->((AbstractKey) k).isMatchingKeyAddress(va))) {
                result.add(va);
            }
        }
        return result;
    }

    public List<KeyAddress> getVotingAddresses() {
        int idx = source.indexOf(".");
        String from = source.substring(0,idx);
        String what = source.substring(idx+1);
        List<Contract> fromContracts = new ArrayList<>();
        if(from.equals("this")) {
            fromContracts.add(getContract());
        } else {
            Reference ref = getContract().getReferences().get(from);
            if(ref == null) {
                throw  new IllegalArgumentException("Reference with name '" + from + "' wasn't found for role " + getName());
            }

            ref.matchingItems.forEach(a -> fromContracts.add((Contract) a));
        }

        List<KeyAddress> addresses = new ArrayList<>();
        for(Contract fromContract :  fromContracts) {
            Object o = fromContract.get(what);
            if (o instanceof Role) {
                o = ((Role) o).resolve();
                if (!(o instanceof ListRole)) {
                    throw  new IllegalArgumentException("Path '" + what + "' is pointing to a role '" + ((Role) o).getName() + "' that is not ListRole");
                } else {
                    for (Role r : ((ListRole) o).getRoles()) {
                        KeyAddress ka = r.getSimpleAddress();
                        if(ka == null)
                            throw  new IllegalArgumentException("Unable to extract simple address from " + r.getName() + ". Check if role is a simple role with single address and no references");
                        checkAddress(ka);
                        addresses.add(ka);
                    }
                }
            } else if (o instanceof List) {
                for(Object item : (List)o) {
                    if (item instanceof Role) {
                        KeyAddress ka = ((Role)item).getSimpleAddress();
                        if(ka == null)
                            throw  new IllegalArgumentException("Unable to extract simple address from " + ((Role)item).getName() + ". Check if role is a simple role with single address and no references");
                        checkAddress(ka);
                        addresses.add(ka);
                    } else if (item instanceof KeyAddress) {
                        checkAddress((KeyAddress) item);
                        addresses.add((KeyAddress) item);
                    } else if(item instanceof PublicKey) {
                        throw  new IllegalArgumentException("Public keys are not allowed in QourumVoteRole source");
                    } else if (item instanceof String) {
                        try {
                            KeyAddress ka = new KeyAddress((String) item);
                            checkAddress(ka);
                            addresses.add(ka);
                        } catch (KeyAddress.IllegalAddressException e) {
                            throw  new IllegalArgumentException("Unable to parse '" + item + "' into an address");
                        }
                    }
                }
            } else {
                throw  new IllegalArgumentException("Path '" + what + "' is pointing to neither Role nor List<?>.");
            }
        }
        return addresses;
    }

    private void checkAddress(KeyAddress ka) {
        if(!ka.isLong() || ka.getTypeMark() != 0) {
            throw  new IllegalArgumentException("Only the long addresses with type mark 0 are supported by QuorumVoteRole as a source");
        }

    }

    /**
     * Get names of {@link Reference} that are not required but are used in voting.
     */
    @Override
    public Set<String> getSpecialReferences() {
        Set<String> refs = new HashSet<>();

        String sourceReference = source.substring(0, source.indexOf("."));

        if (!sourceReference.equals("this")) {
            refs.add(sourceReference);
            // add internal references
            Reference ref = getContract().getReferences().get(sourceReference);
            if (ref != null)
                refs.addAll(ref.getInternalReferences());
        }

        return refs;
    }

    static {
        DefaultBiMapper.registerClass(QuorumVoteRole.class);
    }

    public boolean isQuorumPercentageBased() {
        return quorumValues.stream().anyMatch(v->v.endsWith("%") || v.equals("N"));
    }

    public void setVotesCount(Long votesCount) {
        this.votesCount = votesCount;
    }
}
