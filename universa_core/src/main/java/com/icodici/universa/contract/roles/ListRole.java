/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 9/28/17.
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.KeyRecord;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Role combining other roles (sub-roles) in the "and", "or" and "any N of" principle.
 */
@BiType(name = "ListRole")
public class ListRole extends Role {

    private Mode mode;

    private Set<Role> roles = new HashSet<>();

    private int quorumSize = 0;

    public ListRole() {
    }

    public ListRole(String name) {
        super(name);
    }

    public ListRole(String name, Mode mode, @NonNull Collection<Role> roles) {
        super(name);
        setMode(mode);
        addAll(roles);
    }

    public ListRole(String name, int quorumSize, @NonNull Collection<Role> roles) {
        super(name);
        this.mode = Mode.QUORUM;
        this.quorumSize = quorumSize;
        addAll(roles);
    }

    public void addAll(Collection<Role> roles) {
        this.roles.addAll(roles);
    }

    public Set<Role> getRoles() {
        return roles;
    }

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


    @Override
    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        if(this.mode == null) {
            this.mode = Mode.ALL;
        }

        return this.mode == Mode.ANY && this.processAnyMode(keys) ||
                this.mode == Mode.ALL && this.processAllMode(keys) ||
                this.mode == Mode.QUORUM && this.processQuorumMode(keys);
    }

    /**
     * @param keys to check the roles by mode
     * @return
     */
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

    @Override
    public boolean isValid() {
        return !this.roles.isEmpty();
    }

    @Override
    public Set<PublicKey> getKeys() {
        return this.roles.stream()
                .flatMap(role -> role.getKeyRecords().stream()
                        .map(KeyRecord::getPublicKey))
                .collect(Collectors.toSet());
    }

    /**
     * Mode of combining roles
     */
    enum Mode {
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
        if (mode != null && mode instanceof Mode) {
            this.mode = (Mode) mode;
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
                "mode", s.serialize(this.mode),
                "roles", s.serialize(this.roles));
    }

    static {
        DefaultBiMapper.registerClass(ListRole.class);
    }
}