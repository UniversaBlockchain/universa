package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@BiType(name = "QuorumVoteRole")
public class QuorumVoteRole extends Role {

    public QuorumVoteRole() {

    }

    public QuorumVoteRole(String name, Contract contract, String source, String quorum) {
        super(name,contract);
        this.source = source;
        this.quorum = quorum;


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

        int idx = source.indexOf(".");
        String from = source.substring(0,idx);
        String what = source.substring(idx+1);
        Contract fromContract;
        if(from.equals("this")) {
            fromContract = getContract();
        } else {
            Reference ref = getContract().getReferences().get(from);
            if(ref == null) {
                return false;
            }

            List<Approvable> matchingItems = ref.matchingItems;
            if(matchingItems.size() == 0) {
                return false;
            } else {
                fromContract = (Contract) matchingItems.get(0);
            }
        }
        List<Role> roles;
        Object o = fromContract.get(what);
        if(o instanceof Role) {
            o = ((Role) o).resolve();
            if(!(o instanceof ListRole)) {
                return false;
            } else {
                roles = new ArrayList(((ListRole) o).getRoles());
            }
        } else if(o instanceof List) {
            roles = new ArrayList<>();
            try {
                ((List)o).forEach(item -> {
                    if(item instanceof Role) {
                        roles.add((Role) item);
                    } else if(item instanceof KeyAddress || item instanceof PublicKey) {
                        roles.add(new SimpleRole("@role"+roles.size(), Do.listOf(item)));
                    } else if(item instanceof String) {
                        try {
                            roles.add(new SimpleRole("@role"+roles.size(), Do.listOf(new KeyAddress((String) item))));
                        } catch (KeyAddress.IllegalAddressException e) {
                            throw new IllegalArgumentException();
                        }
                    }
                });
            } catch (IllegalArgumentException e) {
                return false;
            }

        } else {
            return false;
        }

        int minValidCount;
        if(quorum.endsWith("%")) {
            minValidCount = (int) Math.ceil(new BigDecimal(quorum.substring(0,quorum.length()-1)).doubleValue()*roles.size()/100.0f);
        } else {
            minValidCount = Integer.parseInt(quorum);
        }

        for(Role r : roles) {
            if(r.isAllowedForKeys(keys)) {
                minValidCount--;
            }

            if(minValidCount == 0) {
                break;
            }
        }

        return minValidCount == 0;
    }

    static {
        DefaultBiMapper.registerClass(QuorumVoteRole.class);
    }

}
