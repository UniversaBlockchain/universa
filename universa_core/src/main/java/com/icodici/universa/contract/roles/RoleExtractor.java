package com.icodici.universa.contract.roles;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.AnonymousId;
import com.icodici.universa.contract.KeyRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Extracts keys, addresses and anon ids of the role. This utility class drops the internal structure of the role. Use with caution.
 */

public class RoleExtractor {

    /**
     * Extract keys from the role.
     * @param role to extranct keys from
     * @return set of keys present in the role
     */
    public static Set<PublicKey> extractKeys(Role role) {
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleKeys();
        } else if(role instanceof RoleLink) {
            return extractKeys(((RoleLink)role).resolve(true));
        } else if(role instanceof ListRole) {
            Set<PublicKey> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractKeys(r)));
            return result;
        } else if(role instanceof QuorumVoteRole) {
            return new HashSet<>();
        }
        return null;
    }

    /**
     * Extract anon ids from the role.
     * @param role to extranct keys from
     * @return set of anon ids present in the role
     */
    public static Set<AnonymousId> extractAnonymousIds(Role role) {
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleAnonymousIds();
        } else if(role instanceof RoleLink) {
            return extractAnonymousIds(((RoleLink)role).resolve(true));
        } else if(role instanceof ListRole) {
            Set<AnonymousId> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractAnonymousIds(r)));
            return result;
        } else if(role instanceof QuorumVoteRole) {
            return new HashSet<>();
        }
        return null;
    }

    /**
     * Extract addresses from the role.
     * @param role to extranct keys from
     * @return set of addresses present in the role
     */
    public static Set<KeyAddress> extractKeyAddresses(Role role) {
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleKeyAddresses();
        } else if(role instanceof RoleLink) {
            return extractKeyAddresses(((RoleLink)role).resolve(true));
        } else if(role instanceof ListRole) {
            Set<KeyAddress> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractKeyAddresses(r)));
            return result;
        } else if(role instanceof QuorumVoteRole) {
            return new HashSet(((QuorumVoteRole) role).getVotingAddresses());
        }
        return null;
    }

    public static Set<KeyRecord> extractKeyRecords(Role role){
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleKeyRecords();
        } else if(role instanceof RoleLink) {
            return extractKeyRecords(((RoleLink)role).resolve(false));
        } else if(role instanceof ListRole) {
            Set<KeyRecord> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractKeyRecords(r)));
            return result;
        }  else if(role instanceof QuorumVoteRole) {
            return new HashSet<>();
        }
        return null;
    }

    /**
     * Get an address from the role, if it is just a single one.
     *
     * May be used to display a single bearer of a role in UIs. Returns  {@code null} if a single address cannot be decided
     * for the role (like, if there is no addresses/keys discoverable or if there is more than 1 address/key). If the role is bound
     * to a public key rather than an address, returns its short address.
     *
     * @apiNote: IMPORTANT: having references in the role doesn't affect the result. If you need references to be taken into account
     * use {@link Role#getSimpleAddress()}
     *
     * @return role address or null
     */
    public static @Nullable KeyAddress extractSimpleAddress(Role role) {
        return role.getSimpleAddress(true);
    }

}
