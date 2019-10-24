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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@BiType(name = "QuorumVoteRole")
public class QuorumVoteRole extends Role {

    private Long votesCount;

    public QuorumVoteRole() {

    }

    public QuorumVoteRole(String name, Contract contract, String source, String quorum) {
        super(name,contract);
        this.source = source;
        this.quorum = quorum;

        int idx = source.indexOf(".");
        String from = source.substring(0,idx);
        String what = source.substring(idx+1);


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
    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        if(!super.isAllowedForKeys(keys)) {
            return false;
        }

        List<KeyAddress> votingAddresses;
        try {
            votingAddresses = getVotingAddresses();
        } catch (Exception e) {
            return false;
        }


        int minValidCount;
        if(quorum.endsWith("%")) {
            minValidCount = (int) Math.ceil(new BigDecimal(quorum.substring(0,quorum.length()-1)).doubleValue()*votingAddresses.size()/100.0f);
        } else {
            minValidCount = Integer.parseInt(quorum);
        }

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
        return quorum.endsWith("%");
    }

    public void setVotesCount(Long votesCount) {
        this.votesCount = votesCount;
    }
}
