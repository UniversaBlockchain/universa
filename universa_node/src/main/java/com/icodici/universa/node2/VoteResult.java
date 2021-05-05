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
import java.util.Set;

public class VoteResult {
    //both
    public final HashId votingItem;
    public final String roleName;
    public final HashId candidateId;
    

    //bote result for a candidate
    public Long votesCount;
    public Set<KeyAddress> votes;

    public VoteResult(HashId candidateId, HashId votingItem, String roleName) {
        this.candidateId = candidateId;
        this.votingItem = votingItem;
        this.roleName = roleName;
    }

    public void setVotes(Set<KeyAddress> votes) {
        this.votes = votes;
        this.votesCount = (long)votes.size();
    }

    public void setVotesCount(Long votesCount) {
        this.votesCount = votesCount;
    }


    static {
        DefaultBiMapper.registerAdapter(VoteResult.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                VoteResult vr = (VoteResult) object;
                Binder res = Binder.of(
                        "candidate_id", vr.candidateId,
                        "vote_id", vr.votingItem,
                        "role_name", vr.roleName
                );

                if(vr.votesCount != null) {
                    res.put("votes_count",vr.votesCount);
                }

                if(vr.votes != null) {
                    res.put("votes",serializer.serialize(vr.votes));
                }

                return serializer.serialize(res);
            }

            @Override
            public VoteResult deserialize(Binder binder, BiDeserializer deserializer) {
                VoteResult vr = new VoteResult((HashId) binder.getOrThrow("candidate_id"), (HashId) binder.getOrThrow("vote_id"), binder.getStringOrThrow("role_name"));

                if(binder.containsKey("votes")) {
                    vr.setVotes(new HashSet<>(deserializer.deserialize(binder.getListOrThrow("votes"))));
                }

                if(binder.containsKey("votes_count")) {
                    vr.setVotesCount(binder.getLongOrThrow("votes_count"));
                }

                return vr;
            }

            @Override
            public String typeName() {
                return "VoteResult";
            }
        });
    }

}
