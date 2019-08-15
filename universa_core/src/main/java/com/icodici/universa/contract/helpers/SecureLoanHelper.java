package com.icodici.universa.contract.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemState;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

@Deprecated
public class SecureLoanHelper {

    static final private String FIELD_REPAYMENT_AMOUNT = "repayment_amount";
    static final private String PATH_REPAYMENT_AMOUNT= "definition.data."+FIELD_REPAYMENT_AMOUNT;

    static final private String FIELD_MINTABLE_REPAYMENT_ISSUER = "repayment_issuer";
    static final private String PATH_MINTABLE_REPAYMENT_ISSUER= "definition.data."+FIELD_MINTABLE_REPAYMENT_ISSUER;

    static final private String FIELD_MINTABLE_REPAYMENT_CURRENCY = "repayment_currency";
    static final private String PATH_MINTABLE_REPAYMENT_CURRENCY= "definition.data."+FIELD_MINTABLE_REPAYMENT_CURRENCY;

    static final private String FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN = "repayment_origin";
    static final private String PATH_FIXED_SUPPLY_REPAYMENT_ORIGIN= "definition.data."+FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN;





    static final private String FIELD_REPAYMENT = "rep_id";
    static final private String PATH_REPAYMENT = "state.data."+FIELD_REPAYMENT;

    static final private String FIELD_COLLATERAL_ID = "col_id";
    static final private String PATH_COLLATERAL_ID = "state.data."+FIELD_COLLATERAL_ID;

    static final private String FIELD_VERSION = "version";

    static final public String FIELD_STATUS = "status";
    static final public String PATH_STATUS = "state.data."+FIELD_STATUS;
    static final public String FIELD_EXPIRES = "defaultAt";
    static final public String PATH_EXPIRES = "definition.data."+FIELD_EXPIRES;
    static final public String STATUS_INIT = "init";
    static final public String STATUS_IN_PROGRESS = "in_progress";
    static final public String STATUS_DEFAULT = "default";
    static final public String STATUS_REPAID = "repaid";
    static final public String STATUS_CLOSED = "closed";



    private static void setCollateralOwnerAndRefs(Contract contract, HashId loanContractId, KeyAddress lenderAddress, KeyAddress borrowerAddress) {


        Reference refDefault = new Reference(contract);
        refDefault.name = "refDefault";
        refDefault.type = Reference.TYPE_TRANSACTIONAL;
        refDefault.setConditions(Binder.of("all_of", Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_DEFAULT+"\""
        )));

        contract.addReference(refDefault);

        Reference refClosed = new Reference(contract);
        refClosed.name = "refClosed";
        refClosed.type = Reference.TYPE_TRANSACTIONAL;
        refClosed.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));

        contract.addReference(refClosed);

        SimpleRole borrower = new SimpleRole("@b",contract,Do.listOf(borrowerAddress));
        borrower.addRequiredReference("refClosed", Role.RequiredMode.ALL_OF);

        SimpleRole lender = new SimpleRole("@l",contract,Do.listOf(lenderAddress));
        lender.addRequiredReference("refDefault", Role.RequiredMode.ALL_OF);

        ListRole owner = new ListRole("owner",contract);
        owner.addRole(borrower);
        owner.addRole(lender);
        owner.setMode(ListRole.Mode.ANY);
        contract.addRole(owner);

    }


    private static void setRepaymentOwnerAndRefs(Contract contract, HashId loanContractId, KeyAddress lenderAddress) {

        contract.getTransactionalData().set("is_repayment","yes");

        Reference refClosedRepaymentCheck = new Reference(contract);
        refClosedRepaymentCheck.name = "refClosedRepaymentCheck";
        refClosedRepaymentCheck.type = Reference.TYPE_TRANSACTIONAL;
        refClosedRepaymentCheck.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));

        contract.addReference(refClosedRepaymentCheck);

        SimpleRole owner = new SimpleRole("owner",contract,Do.listOf(lenderAddress));
        owner.addRequiredReference("refClosedRepaymentCheck", Role.RequiredMode.ALL_OF);
        contract.addRole(owner);
    }

    private static KeyAddress getLender(Contract secureLoan) {
        return secureLoan.getRole("lender").getSimpleAddress();
    }

    private static KeyAddress getBorrower(Contract secureLoan) {
        return secureLoan.getRole("borrower").getSimpleAddress();
    }

    private static Contract getCollateral(Contract secureLoan) {
        HashId id = HashId.withDigest((String) secureLoan.get(PATH_COLLATERAL_ID));
        if(secureLoan.get(PATH_STATUS).equals(STATUS_IN_PROGRESS)) {
            return secureLoan.getTransactionPack().getSubItem(id);
        } else if(secureLoan.get(PATH_STATUS).equals(STATUS_REPAID)) {
            return secureLoan.getTransactionPack().getReferencedItems().get(id);
        } else {
            throw new IllegalArgumentException("invalid secure loan contract state. must be in progress or repaid");
        }
    }


    private static Contract getRepayment(Contract secureLoan) {
        if(secureLoan.get(PATH_STATUS).equals(STATUS_REPAID)) {
            return secureLoan.getTransactionPack().getSubItem(HashId.withDigest((String) secureLoan.get(PATH_REPAYMENT)));
        } else {
            throw new IllegalArgumentException("invalid secure loan contract state. must be in repaid");
        }
    }

    /**
     * Prepares secure loan agreement contract and its satellites.
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and lender) and registered to get its satellites registered and usable
     *
     * @param definitionData free-form data to put into loan contract definition.data section
     * @param lenderAddress address of lender key
     * @param borrowerAddress address of borrower key
     * @param loan contract that is given to borrower by lender. must be owned by lender by this time.
     * @param loanDuration duration of a loan
     * @param collateral contract that is acts as collateral for a loan must be owned by borrower by this time.
     * @param repaymentAmount the amount to be repaid at the end of the loan
     * @param mintable flag indicates if repayment is mintable token. Fixed supply otherwise
     * @param repaymentOrigin the expected origin of repayment token. passed for fixed supply tokens only. pass null otherwise.
     * @param repaymentIssuer the expected issuer of repayment token. passed for mintable tokens only. pass null otherwise.
     * @param repaymentCurrency the expected currency of repayment token. passed for mintable tokens only. pass null otherwise.
     * @return the array of contracts [secure loan agreement contract, loan contract owned by borrower]
     */
    public static Contract[] initSecureLoan(Binder definitionData, KeyAddress lenderAddress, KeyAddress borrowerAddress, Contract loan, Duration loanDuration, Contract collateral, String repaymentAmount, boolean mintable, HashId repaymentOrigin, KeyAddress repaymentIssuer, String repaymentCurrency) {
        return initSecureLoan(Do.listOf(lenderAddress,borrowerAddress),definitionData,lenderAddress,borrowerAddress,loan,loanDuration,collateral,repaymentAmount,mintable,repaymentOrigin,repaymentIssuer,repaymentCurrency);
    }

    /**
     * Prepares secure loan agreement contract and its satellites.
     *
     * Contract returned is not signed/registered. Must be signed (by borrower, lender and issuer keys) and registered to get its satellites registered and usable
     *
     * @param issuerKeys keys/addresses to set "issuer" of secure loan contract to.
     * @param definitionData free-form data to put into loan contract definition.data section
     * @param lenderAddress address of lender key
     * @param borrowerAddress address of borrower key
     * @param loan contract that is given to borrower by lender. must be owned by lender by this time.
     * @param loanDuration duration of a loan
     * @param collateral contract that is acts as collateral for a loan must be owned by borrower by this time.
     * @param repaymentAmount the amount to be repaid at the end of the loan
     * @param mintable flag indicates if repayment is mintable token. Fixed supply otherwise
     * @param repaymentOrigin the expected origin of repayment token. passed for fixed supply tokens only. pass null otherwise.
     * @param repaymentIssuer the expected issuer of repayment token. passed for mintable tokens only. pass null otherwise.
     * @param repaymentCurrency the expected currency of repayment token. passed for mintable tokens only. pass null otherwise.
     * @return the array of contracts [secure loan agreement contract, loan contract owned by borrower]
     */
    public static Contract[] initSecureLoan(Collection<?> issuerKeys, Binder definitionData, KeyAddress lenderAddress, KeyAddress borrowerAddress, Contract loan, Duration loanDuration, Contract collateral, String repaymentAmount, boolean mintable, HashId repaymentOrigin, KeyAddress repaymentIssuer, String repaymentCurrency) {
        Contract secureLoan = new Contract();
        secureLoan.setExpiresAt(ZonedDateTime.now().plusYears(100));
        secureLoan.setIssuerKeys(issuerKeys);
        secureLoan.setOwnerKeys(lenderAddress,borrowerAddress);
        secureLoan.setCreatorKeys(lenderAddress,borrowerAddress);

        if(definitionData != null)
            secureLoan.getDefinition().getData().putAll(definitionData);

//        secureLoan.getDefinition().getData().put(FIELD_LENDER,lenderAddress.toString());
//        secureLoan.getDefinition().getData().put(FIELD_BORROWER,borrowerAddress.toString());
        secureLoan.getDefinition().getData().put(FIELD_VERSION,"2");

        SimpleRole lenderRole = new SimpleRole("lender",secureLoan,Do.listOf(lenderAddress));
        secureLoan.addRole(lenderRole);

        SimpleRole borrowerRole = new SimpleRole("borrower",secureLoan,Do.listOf(borrowerAddress));
        secureLoan.addRole(borrowerRole);

        //this role is used to compare to repayment contract owner for equality
        SimpleRole repaymentRole = new SimpleRole("repayment",secureLoan,Do.listOf(lenderAddress));
        repaymentRole.addRequiredReference("refClosedRepaymentCheck", Role.RequiredMode.ALL_OF);
        secureLoan.addRole(repaymentRole);



        //MODIFY STATE DATA PERMISSIONS
        //INIT->IN_PROGRESS
        SimpleRole initRole = new SimpleRole("@init",secureLoan,Do.listOf(lenderAddress,borrowerAddress));
        initRole.addRequiredReference("refInit", Role.RequiredMode.ALL_OF);

        ModifyDataPermission initPermission =
                new ModifyDataPermission(initRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_IN_PROGRESS),
                                "/references",null,
                                FIELD_COLLATERAL_ID, null
                        )));
        secureLoan.addPermission(initPermission);

        //IN_PROGRESS->DEFAULT
        SimpleRole defaultRole = new SimpleRole("@default",secureLoan,Do.listOf(lenderAddress));
        defaultRole.addRequiredReference("refDefault", Role.RequiredMode.ALL_OF);

        ModifyDataPermission defaultPermission =
                new ModifyDataPermission(defaultRole,
                        Binder.of("fields",Binder.of(FIELD_STATUS,Do.listOf(STATUS_DEFAULT))));
        secureLoan.addPermission(defaultPermission);



        //IN_PROGRESS->REPAID
        SimpleRole repaidRole = new SimpleRole("@repaid",secureLoan,Do.listOf(borrowerAddress));
        repaidRole.addRequiredReference("refRepayment", Role.RequiredMode.ALL_OF);
        repaidRole.addRequiredReference("refRepaid", Role.RequiredMode.ALL_OF);



        ModifyDataPermission repaidPermission =
                new ModifyDataPermission(repaidRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_REPAID),
                                FIELD_REPAYMENT, null
                                )));
        secureLoan.addPermission(repaidPermission);


        //REPAID->CLOSED
        SimpleRole closedRole = new SimpleRole("@closed",secureLoan,Do.listOf(borrowerAddress,lenderAddress));
        closedRole.addRequiredReference("refClosed", Role.RequiredMode.ALL_OF);

        ModifyDataPermission closedPermission =
                new ModifyDataPermission(closedRole,
                        Binder.of("fields",Binder.of(FIELD_STATUS,Do.listOf(STATUS_CLOSED))));
        secureLoan.addPermission(closedPermission);


        secureLoan.getDefinition().getData().put(FIELD_EXPIRES,ZonedDateTime.now().plus(loanDuration));

        secureLoan.getDefinition().getData().put(FIELD_REPAYMENT_AMOUNT,repaymentAmount);
        if(mintable) {
            secureLoan.getDefinition().getData().put(FIELD_MINTABLE_REPAYMENT_CURRENCY, repaymentCurrency);
            secureLoan.getDefinition().getData().put(FIELD_MINTABLE_REPAYMENT_ISSUER, repaymentIssuer.toString());
        } else {
            secureLoan.getDefinition().getData().put(FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN, repaymentOrigin.toBase64String());
        }

        secureLoan.getStateData().put(FIELD_STATUS, STATUS_INIT);


        Reference refInit = new Reference(secureLoan);
        refInit.name = "refInit";
        refInit.type = Reference.TYPE_EXISTING_STATE;
        refInit.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_INIT+"\""
                )));
        secureLoan.addReference(refInit);


        //REGISTER 1ST REV of secure loan to get its origin
        secureLoan.seal();

        Contract secureLoanRoot = secureLoan;



        //transfer collateral to complex secure loan status dependant role
        collateral = collateral.createRevision();
        collateral.setCreatorKeys(borrowerAddress,lenderAddress);
        setCollateralOwnerAndRefs(collateral,secureLoan.getOrigin(),lenderAddress,borrowerAddress);
        collateral.seal();




        //transfer loan directry to borrower
        loan = loan.createRevision();
        loan.setCreatorKeys(borrowerAddress,lenderAddress);
        loan.setOwnerKey(borrowerAddress);
        loan.seal();


        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(lenderAddress,borrowerAddress);
        secureLoan.getStateData().put(FIELD_STATUS,STATUS_IN_PROGRESS);
        secureLoan.getStateData().put(FIELD_COLLATERAL_ID, collateral.getId().toBase64String());


        //this reference is used to compare to corresponding repayment contract reference for equality
        Reference refClosedRepaymentCheck = new Reference(secureLoan);
        refClosedRepaymentCheck.name = "refClosedRepaymentCheck";
        refClosedRepaymentCheck.type = Reference.TYPE_EXISTING_STATE;
        refClosedRepaymentCheck.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+secureLoan.getOrigin().toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));
        secureLoan.addReference(refClosedRepaymentCheck);


        //Reference to valid repayment contract
        Reference refRepayment = new Reference(secureLoan);
        refRepayment.name = "refRepayment";
        refRepayment.type = Reference.TYPE_EXISTING_STATE;
        List<Object> conditions = Do.listOf(
                "ref.state.data.amount==this." + PATH_REPAYMENT_AMOUNT,
                "ref.transactional.data.is_repayment==\"yes\""
        );
        if(mintable) {
            conditions.add("ref.issuer==this."+PATH_MINTABLE_REPAYMENT_ISSUER);
            conditions.add("ref.definition.data.currency==this."+PATH_MINTABLE_REPAYMENT_CURRENCY);
        } else {
            conditions.add("ref.origin==this."+PATH_FIXED_SUPPLY_REPAYMENT_ORIGIN);
        }
        refRepayment.setConditions(Binder.of("all_of",conditions));
        secureLoan.addReference(refRepayment);


        //Reference reqiured to set secure loan contract to default
        Reference refDefault = new Reference(secureLoan);
        refDefault.name = "refDefault";
        refDefault.type = Reference.TYPE_EXISTING_STATE;
        refDefault.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_IN_PROGRESS+"\"",
                        "now>this."+PATH_EXPIRES
                )));
        secureLoan.addReference(refDefault);

        //Reference reqiured to set secure loan contract to repaid.
        // It checks that there is valid reference to repayment сontract
        // and that repayment owner role and its (owner) reference are set correctly
        Reference refRepaid = new Reference(secureLoan);
        refRepaid.name = "refRepaid";
        refRepaid.type = Reference.TYPE_EXISTING_STATE;
        refRepaid.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_IN_PROGRESS+"\"",
                        "refRepayment.owner==this.state.roles.repayment",
                        "refRepayment.transactional.references.refClosedRepaymentCheck==this.state.references.refClosedRepaymentCheck",
                        "now<this."+PATH_EXPIRES
                )));
        secureLoan.addReference(refRepaid);

        Reference refclosed = new Reference(secureLoan);
        refclosed.name = "refClosed";
        refclosed.type = Reference.TYPE_EXISTING_STATE;
        refclosed.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+ STATUS_REPAID+"\""
                )));
        secureLoan.addReference(refclosed);

        secureLoan.addNewItems(collateral);
        //secureLoan.addNewItems(collateralTemplate);
        secureLoan.addNewItems(loan);
        secureLoan.addNewItems(secureLoanRoot);

        secureLoan.seal();

        return new Contract[] {secureLoan,loan};
    }


    /**
     * Transfers secure loan agreement contract into DEFAULT state and transfers collateral to lender.
     *
     * Contract returned is not signed/registered. Must be signed (by lender) and registered to get collateral registered and usable by lender
     *
     * @param secureLoan secure loan agreement contract
     * @return the array of contracts [secure loan agreement contract, collateral contract owner by lender]
     */

    public static Contract[] defaultSecureLoan(Contract secureLoan) {
        if(!secureLoan.getStateData().get(FIELD_STATUS).equals(STATUS_IN_PROGRESS)) {
            throw new IllegalArgumentException("wrong secure loan state. expected " + STATUS_IN_PROGRESS + " found " + secureLoan.getStateData().get(FIELD_STATUS));
        }

        KeyAddress lenderAddress = getLender(secureLoan);
        Contract collateral = getCollateral(secureLoan);

        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(lenderAddress);
        secureLoan.getStateData().set(FIELD_STATUS,STATUS_DEFAULT);


        collateral = collateral.createRevision();
        collateral.setCreatorKeys(lenderAddress);
        collateral.setOwnerKeys(lenderAddress);
        collateral.seal();

        secureLoan.addNewItems(collateral);
        secureLoan.seal();

        return new Contract[]{ secureLoan,collateral};
    }

    /**
     * Transfers secure loan agreement contract into REPAID state (repayment and collateral aren't owned by neither lender nor borrower at this point).
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and registered
     *
     * @param secureLoan secure loan agreement contract
     * @param repayment contract owned by burrower by this time
     * @return the array of contracts [secure loan agreement contract]
     */

    public static Contract[] repaySecureLoan(Contract secureLoan, Contract repayment) {

        if(!secureLoan.getStateData().get(FIELD_STATUS).equals(STATUS_IN_PROGRESS)) {
            throw new IllegalArgumentException("wrong secure loan state. expected " + STATUS_IN_PROGRESS + " found " + secureLoan.getStateData().get(FIELD_STATUS));
        }


        Contract collateral = getCollateral(secureLoan);

        KeyAddress borrowerAddress = getBorrower(secureLoan);
        KeyAddress lenderAddress = getLender(secureLoan);
        repayment = repayment.createRevision();
        repayment.setCreatorKeys(borrowerAddress);
        setRepaymentOwnerAndRefs(repayment,secureLoan.getOrigin(),lenderAddress);
        repayment.seal();

        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(borrowerAddress);
        secureLoan.getStateData().set(FIELD_STATUS,STATUS_REPAID);
        secureLoan.addNewItems(repayment);
        secureLoan.getStateData().put(FIELD_REPAYMENT,repayment.getId().toBase64String());
        secureLoan.seal();
        secureLoan.getTransactionPack().addReferencedItem(collateral);

        return new Contract[] {secureLoan};
    }


    /**
     * Transfers secure loan agreement contract into CLOSED state (repayment becomes owned by lender and collateral by borrower).
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and lender), service contract must be added as referenced item to transaction and transaction can be registered then
     *
     * @param secureLoan secure loan agreement contract
     * @return the array of contracts [secure loan agreement contract, repayment contract owner by lender, collateral contract owner by borrower]
     */

    public static Contract[] closeSecureLoan(Contract secureLoan) {

        if(!secureLoan.getStateData().get(FIELD_STATUS).equals(STATUS_REPAID)) {
            throw new IllegalArgumentException("wrong secure loan state. expected " + STATUS_REPAID + " found " + secureLoan.getStateData().get(FIELD_STATUS));
        }

        Contract repayment = getRepayment(secureLoan);
        Contract collateral = getCollateral(secureLoan);

        KeyAddress borrowerAddress = getBorrower(secureLoan);
        KeyAddress lenderAddress = getLender(secureLoan);

        repayment = repayment.createRevision();
        repayment.setCreatorKeys(borrowerAddress,lenderAddress);
        repayment.setOwnerKeys(lenderAddress);

        collateral = collateral.createRevision();
        collateral.setCreatorKeys(borrowerAddress,lenderAddress);
        collateral.setOwnerKeys(borrowerAddress);



        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(borrowerAddress,lenderAddress);
        secureLoan.getStateData().set(FIELD_STATUS,STATUS_CLOSED);
        secureLoan.addNewItems(repayment);
        secureLoan.addNewItems(collateral);
        secureLoan.seal();

        return new Contract[] {secureLoan,repayment,collateral};
    }
}
