/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 9/28/17.
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.AnonymousId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Role combining other roles (sub-roles) in the "and", "or" and "any N of" principle.
 */
@BiType(name = "ListRole")
public class ListRole extends Role {

    private Mode mode = Mode.ALL;

    private Set<Role> roles = new HashSet<>();

    private int quorumSize = 0;

    /**
     * Create empty role combining other roles (sub-roles). To be initialized from dsl later.
     */
    public ListRole() {
    }

    /**
     * Create empty role combining other roles (sub-roles). To be initialized from dsl later.
     *
     * @param name is role name
     */
    public ListRole(String name) {
        super(name);
    }

    /**
     * Create new role combining other roles (sub-roles)
     *
     * @param name is role name
     * @param mode is mode of sub-roles combining: "and", "or" and "any N of" principle
     * @param roles is collection of sub-roles
     */
    public ListRole(String name, Mode mode, @NonNull Collection<Role> roles) {
        super(name);
        setMode(mode);
        addAll(roles);
    }

    /**
     * Create new role combining other roles (sub-roles) in the "any N of" principle ({@link Mode#QUORUM}).
     *
     * @param name is role name
     * @param quorumSize is N in "any N of" principle
     * @param roles is collection of sub-roles
     */
    public ListRole(String name, int quorumSize, @NonNull Collection<Role> roles) {
        super(name);
        this.mode = Mode.QUORUM;
        this.quorumSize = quorumSize;
        addAll(roles);
    }

    /**
     * Adds sub-roles to combining role.
     *
     * @param roles is collection of sub-roles
     */
    public void addAll(Collection<Role> roles) {
        this.roles.addAll(roles);
    }

    /**
     * Get sub-roles of combining role.
     *
     * @return set of sub-roles
     */
    public Set<Role> getRoles() {
        return roles;
    }

    /**
     * Adds sub-role to combining role.
     *
     * @param role is sub-role
     */
    public ListRole addRole(Role role) {
        this.roles.add(role);
        return this;
    }

    /**
     * Set mode to {@link Mode#QUORUM} and quorum size to any n roles.
     *
     * @param n how many subroles set of key must be able to play to play this role
     */
    public void setQuorum(int n) {
        mode = Mode.QUORUM;
        quorumSize = n;
    }

    /**
     * @return quorum size if in quorum mode, otherwise 0
     */
    public int getQuorum() {
        return this.mode == Mode.QUORUM ? this.quorumSize : 0;
    }

    /**
     * Set mode to either {@link Mode#ALL} or {@link Mode#ANY}, Quorum mode could be set only with {@link #setQuorum(int)}
     * call,
     *
     * @param newMode mode to set
     * @throws IllegalArgumentException if mode other than ANY/ALL is specified
     */
    public void setMode(Mode newMode) {
        if (newMode != Mode.QUORUM)
            this.mode = newMode;
        else
            throw new IllegalArgumentException("Only ANY or ALL of the modes should be set.");
    }


    /**
     * Returns mode of this role.
     */
    public Mode getMode() {
        return this.mode;
    }

    /**
     * Check role is allowed to keys
     *
     * @param keys is set of keys
     * @return true if role is allowed to keys
     */
    @Override
    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        if(!super.isAllowedForKeys(keys))
            return false;

        if(this.mode == null) {
            this.mode = Mode.ALL;
        }

        return this.mode == Mode.ANY && this.processAnyMode(keys) ||
                this.mode == Mode.ALL && this.processAllMode(keys) ||
                this.mode == Mode.QUORUM && this.processQuorumMode(keys);
    }


    private boolean processQuorumMode(Set<? extends AbstractKey> keys) {
        int counter = this.quorumSize;
        boolean result = counter == 0;

        Set<Role> roles = this.roles;

        for (Role role : roles) {
            if (result) break;

            if (role != null && role.isAllowedForKeys(keys) && --counter == 0) {
                result = true;
                break;
            }
        }

        return result;
    }


    private boolean processAllMode(Set<? extends AbstractKey> keys) {
        return this.roles.stream().allMatch(role -> role.isAllowedForKeys(keys));
    }

    private boolean processAnyMode(Set<? extends AbstractKey> keys) {
        return this.roles.stream().anyMatch(role -> role.isAllowedForKeys(keys));
    }

    /**
     * Check availability sub-roles of combining role.
     *
     * @return true if set of sub-roles is not empty
     */
    @Override
    public boolean isValid() {
        return !this.roles.isEmpty();
    }

    @Override
    protected boolean equalsIgnoreNameAndRefs(Role otherRole) {
        if(!(otherRole instanceof ListRole))
            return false;

        if(!roles.equals(((ListRole) otherRole).roles))
            return false;

        if(mode != ((ListRole) otherRole).mode)
            return false;

        if(mode == Mode.QUORUM && quorumSize != ((ListRole) otherRole).quorumSize)
            return false;
        return true;
    }

    /**
     * Initializes combining role from dsl.
     *
     * @param serializedRole is {@link Binder} from dsl with data of combining role
     */
    @Override
    public void initWithDsl(Binder serializedRole) {
        List<Object> roleBinders = serializedRole.getListOrThrow("roles");

        mode = Mode.valueOf(serializedRole.getStringOrThrow("mode").toUpperCase());
        if(mode == Mode.QUORUM)
            quorumSize = serializedRole.getIntOrThrow("quorumSize");

        roleBinders.stream().forEach(x -> {

            if(x instanceof String) {
                roles.add(new RoleLink(x+"link"+ Instant.now().toEpochMilli(), (String) x));
            } else {
                Binder binder = Binder.of(x);
                if (binder.size() == 1) {
                    String name = binder.keySet().iterator().next();
                    roles.add(Role.fromDslBinder(name, binder.getBinderOrThrow(name)));
                } else {
                    roles.add(Role.fromDslBinder(null, binder));
                }
            }
        });
    }

    /**
     * Set role contract.
     *
     * @param contract is role contract
     */
    @Override
    public void setContract(Contract contract) {
        super.setContract(contract);
        roles.forEach(r -> r.setContract(contract));
    }

    /**
     * Get set of all key records in sub-roles.
     *
     * @return set of public keys (see {@link PublicKey})
     */
    @Override
    @Deprecated
    public Set<KeyRecord> getKeyRecords() {
        return this.roles.stream()
                .flatMap(role -> RoleExtractor.extractKeyRecords(role).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get set of all keys in sub-roles.
     *
     * @return set of public keys (see {@link PublicKey})
     */
    @Override
    @Deprecated
    public Set<PublicKey> getKeys() {
        return this.roles.stream()
                .flatMap(role -> RoleExtractor.extractKeys(role).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get set of all anonymous identifiers in sub-roles.
     *
     * @return set of anonymous identifiers (see {@link AnonymousId})
     */
    @Override
    @Deprecated
    public Set<AnonymousId> getAnonymousIds() {
        return this.roles.stream()
                .flatMap(role -> RoleExtractor.extractAnonymousIds(role).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get set of all key addresses in sub-roles.
     *
     * @return set of key addresses (see {@link KeyAddress})
     */
    @Override
    @Deprecated
    public Set<KeyAddress> getKeyAddresses() {
        return this.roles.stream()
                .flatMap(role -> RoleExtractor.extractKeyAddresses(role).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Mode of combining roles
     */
    public enum Mode {
        /**
         * Role could be performed only if set of keys could play all sub-roles
         */
        ALL,
        /**
         * Role could be performed if set of keys could play any role of the list
         */
        ANY,
        /**
         * Role could be played if set of keys could play any quorrum set of roles, e.g. at least any N subroles,
         * controlled by the {@link #setQuorum(int)} method
         */
        QUORUM
    }

    /**
     * Get role as string.
     *
     * @return string with data of role
     */
    @Override
    public String toString() {
        return String.format("ListRole<%s:%s:%s:%s>", System.identityHashCode(this), getName(),
                this.mode == null ? "" : this.mode == Mode.QUORUM ? this.mode.name().toLowerCase() + "_" + this.quorumSize
                        : this.mode.name().toLowerCase(), this.roles);
    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        this.quorumSize = data.getInt("quorumSize", 0);

        Object mode = data.getOrDefault("mode", null);
        if (mode != null) {
            this.mode = Mode.valueOf((String) mode);
        }

        List<Binder> roles = data.getList("roles", null);
        if (roles != null) {
            this.roles.clear();
            roles.forEach(role -> addRole(deserializer.deserialize(role)));
        }
    }

    @Override
    public Binder serialize(BiSerializer s) {

        return super.serialize(s).putAll(
                "quorumSize", s.serialize(this.quorumSize),
                "mode", s.serialize(this.mode == null ? null : this.mode.name()),
                "roles", s.serialize(this.roles));
    }

    /**
     * If this role has public keys, they will be replaced with {@link AnonymousId}.
     */
    @Override
    public void anonymize() {
        for (Role role : roles)
            role.anonymize();
    }

    @Override
    @Nullable KeyAddress getSimpleAddress(boolean ignoreRefs) {
        if(!ignoreRefs  && (requiredAnyReferences.size() > 0 || requiredAllReferences.size() > 0))
            return null;

        if(roles.size() == 1 && (mode != Mode.QUORUM || quorumSize == 1)) {
            return roles.iterator().next().getSimpleAddress(ignoreRefs);
        }
        return null;
    }

    static {
        DefaultBiMapper.registerClass(ListRole.class);
    }
}