/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;
import net.sergeych.tools.Binder;

import java.util.*;

import static java.util.Arrays.asList;

/**
 * Permission allows to change some numeric (as for now, integer) field, controlling it's range and delta. This
 * permission could be used more than once allowing for different roles to change in different range and directions.
 */

@BiType(name = "SplitJoinPermission")
public class SplitJoinPermission extends Permission {

    private Decimal minValue;
    private Decimal minUnit;
    private String fieldName;
    private int newValue;
    private List<String> mergeFields;

    /**
     * Create new permission for change some numeric field.
     *
     * @param role   allows to permission
     * @param params is parameters of permission: field_name, min_value, min_unit, join_match_fields
     */
    public SplitJoinPermission(Role role, Binder params) {
        super("split_join", role, params);
        initFromParams();
    }

    protected void initFromParams() {
        fieldName = params.getStringOrThrow("field_name");
        minValue = new Decimal(params.getString("min_value", "0"));
        minUnit = new Decimal(params.getString("min_unit", "1e-9"));
        mergeFields = params.getList("join_match_fields", asList("state.origin"));
    }

    private SplitJoinPermission() {
        super();
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        initFromParams();
    }

    /**
     * Check and remove changes that this permission allow. Note that it does not add errors itself, to allow using
     * several such permission, from which some may allow the change, and some may not. If a check will add error,
     * though, it will prevent subsequent permission objects to allow the change.
     *
     * @param contract           source (valid) contract
     * @param changed            is contract for checking
     * @param stateChanges       map of changes, see {@link Delta} for details
     * @param revokingItems items to be revoked. The ones are getting joined will be removed during check
     * @param keys keys contract is sealed with. Keys are used to check other contracts permissions
     * @param checkingReferences are used to check other contracts permissions
     */
    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences) {
        MapDelta<String, Binder, Binder> dataChanges = (MapDelta<String, Binder, Binder>) stateChanges.get("data");
        if (dataChanges == null)
            return;
        Delta delta = dataChanges.getChange(fieldName);
        if (delta != null) {
            if (!(delta instanceof ChangedItem))
                return;
            try {
                Decimal oldValue = new Decimal(delta.oldValue().toString());
                Decimal newValue = new Decimal(delta.newValue().toString());

                int cmp = oldValue.compareTo(newValue);
                if (cmp > 0)
                    checkSplit(changed, dataChanges, revokingItems, keys, checkingReferences, oldValue, newValue);
                else if (cmp < 0)
                    checkMerge(changed, dataChanges, revokingItems, keys, checkingReferences, newValue);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private void checkMerge(Contract changed, MapDelta<String, Binder, Binder> dataChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences, Decimal newValue) {
        boolean isValid;

        // merge means there are mergeable contracts in the revoking items
        Decimal sum = Decimal.ZERO;
        Set<Contract> revokesToRemove = new HashSet<>();
        for (Approvable a : changed.getRevokingItems()) {
            if (a instanceof Contract) {
                Contract c = (Contract) a;

                if (!isMergeable(c) || !validateMergeFields(changed, c) || !hasSimilarPermission(c, keys, checkingReferences))
                    continue;

                revokesToRemove.add(c);

                sum = sum.add(new Decimal(getFieldName(c)));

            }
        }

        isValid = sum.compareTo(newValue) == 0;

        if (!isValid) {
            revokesToRemove.clear();
            isValid = checkSplitJoinCase(changed, revokesToRemove, keys, checkingReferences);
        }


        if (isValid) {
            dataChanges.remove(fieldName);
            revokingItems.removeAll(revokesToRemove);
        }
    }

    private void checkSplit(Contract changed, MapDelta<String, Binder, Binder> dataChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences, Decimal oldValue, Decimal newValue) {
        boolean isValid;

        // We need to find the splitted contracts
        Decimal sum = Decimal.ZERO;
        Set<Contract> revokesToRemove = new HashSet<>();
        for (Contract s : changed.getSiblings()) {


            if (!isMergeable(s) || !validateMergeFields(changed, s) || !hasSimilarPermission(s, keys, checkingReferences, false)) {
                int a = 0;
                a++;
                continue;
            }

            sum = sum.add(new Decimal(s.getStateData().getString(fieldName)));
        }

        // total value should not be changed or check split-join case
        isValid = sum.equals(oldValue);

        if (!isValid)
            isValid = checkSplitJoinCase(changed, revokesToRemove, keys, checkingReferences);


        if (isValid && newValue.compareTo(minValue) >= 0 && newValue.ulp().compareTo(minUnit) >= 0) {
            dataChanges.remove(fieldName);
            revokingItems.removeAll(revokesToRemove);
        }
    }

    private boolean checkSplitJoinCase(Contract changed, Set<Contract> revokesToRemove, Collection<PublicKey> keys, Collection<String> checkingReferences) {
        Decimal splitJoinSum = Decimal.ZERO;

        for (Contract c : changed.getSiblings()) {
            if (!isMergeable(c) || !validateMergeFields(changed, c) || !hasSimilarPermission(c, keys, checkingReferences, false))
                continue;

            splitJoinSum = splitJoinSum.add(new Decimal(c.getStateData().getString(fieldName)));
        }

        Decimal rSum = Decimal.ZERO;

        for (Approvable r : changed.getRevokingItems()) {
            if (r instanceof Contract) {
                Contract c = (Contract) r;
                if (!isMergeable(c) || !validateMergeFields(changed, c) || !hasSimilarPermission(c, keys, checkingReferences))
                    continue;

                revokesToRemove.add(c);
                rSum = rSum.add(new Decimal(((Contract) r).getStateData().getString(fieldName)));
            }
        }


        return splitJoinSum.compareTo(rSum) == 0;
    }

    private boolean hasSimilarPermission(Contract contract, Collection<PublicKey> keys, Collection<String> references) {
        return hasSimilarPermission(contract,keys,references,false);
    }

    private boolean hasSimilarPermission(Contract contract, Collection<PublicKey> keys, Collection<String> references,boolean checkAllowance) {
        Collection<Permission> permissions = contract.getPermissions().get("split_join");
        if(permissions == null)
            return false;

        return permissions.stream().anyMatch(p -> {
            if(!((SplitJoinPermission)p).fieldName.equals(fieldName)) {
                return false;
            }
            if(!((SplitJoinPermission)p).minUnit.equals(minUnit)) {
                return false;
            }
            if(!((SplitJoinPermission)p).minValue.equals(minValue)) {
                return false;
            }
            if(((SplitJoinPermission)p).mergeFields.size() != mergeFields.size()) {
                return false;
            }
            if(!((SplitJoinPermission)p).mergeFields.containsAll(mergeFields)) {
                return false;
            }
            if(checkAllowance && !p.isAllowedFor(keys,references)) {
                return false;
            }
            return true;
        });
    }

    /**
     * Check matching fields of two contracts.
     * @param changed contract to check
     * @param c contract to check
     * @return true if all fields are matching, else false
     */
    public boolean validateMergeFields(Contract changed, Contract c) {
        // check matching fields
        for (String name : mergeFields) {
            Object v1 = changed.get(name);
            Object v2 = c.get(name);
            if (!v1.equals(v2))
                return false;
        }
        return true;
    }

    private boolean isMergeable(Contract c) {
        // Checking that it is mergeable:
        String s = getFieldName(c);
        // no field?
        return s != null;
    }

    private String getFieldName(Contract c) {
        return c.getStateData().getString(fieldName, null);
    }

    static {
        DefaultBiMapper.registerClass(SplitJoinPermission.class);
    }

}
