package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.ItemResult;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VoteInfo {
    public final HashId votingItem;
    public final String roleName;
    public final Contract contract;
    public final ZonedDateTime expires;
    public final Set<HashId> candidates;

    //these do not serialize
    public Long votingId;
    public Map<HashId,Long> candidateIds;


    public VoteInfo(HashId votingItem, String roleName, ZonedDateTime expires, Contract contract, Set<HashId> candidates) {
        this.votingItem = votingItem;
        this.roleName = roleName;
        this.expires = expires;
        this.contract = contract;
        this.candidates = candidates;
    }


    static {
        DefaultBiMapper.registerAdapter(VoteResult.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                VoteInfo vi = (VoteInfo) object;
                return Binder.of(
                        "vote_id", vi.votingItem,
                        "role_name", vi.roleName,
                        "expires_at",vi.expires,
                        "contract",vi.contract.getPackedTransaction(),
                        "candidates",vi.candidates);

            }

            @Override
            public VoteInfo deserialize(Binder binder, BiDeserializer deserializer) {
                try {
                    return new VoteInfo(binder.getOrThrow("vote_id"),
                           binder.getStringOrThrow("role_name"),
                            binder.getOrThrow("expires_at"),
                            Contract.fromPackedTransaction(binder.getBinaryOrThrow("contract")),
                            new HashSet<>(binder.getListOrThrow("candidates")));
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public String typeName() {
                return "VoteInfo";
            }
        });
    }

}
