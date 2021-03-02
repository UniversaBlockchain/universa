package com.icodici.universa.contract.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.QuorumVoteRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Helper class that represents mintable token contract structure with the ability to track the total amount minted.
 * 1. Issue root contract holding multisig configuration and the total number of protocol contracts issued
 * 2. Issue protocol contract holding the amount issued (initialy 0)
 * 3. Issuing or revoking tokens is only possible together with updating one of the protocol contracts.
 */

public class ManagedToken {

    public static class MintingRoot {
        private static final String TP_TAG = "managed_token_root";
        Contract mintingRootContract;
        TransactionPack toBeRegistered;

        /**
         * Create new minting root object holding contract of appropriate structure.
         * Providing this object is required for all operations with protocols and tokens.
         *
         * This method creates transaction to be registered {@link #getTransaction()}
         *
         * @param multisigAddresses addresses of root authority
         * @param quorum quorum for issuing / revoking tokens
         * @param strongQuorum quorum for issuing root contract and subsequent protocol contracts
         */

        public MintingRoot(Set<KeyAddress> multisigAddresses,int quorum, int strongQuorum) {
            mintingRootContract = new Contract();
            mintingRootContract.setExpiresAt(ZonedDateTime.now().plusYears(1000));

            //MULTISIG ADDRESSES
            mintingRootContract.getStateData().put("addresses", Do.list(multisigAddresses));
            //MULTISIG QUORUM
            mintingRootContract.getStateData().put("quorum", quorum);
            //STRONG MULTISIG QUORUM
            mintingRootContract.getStateData().put("strong_quorum", strongQuorum);
            mintingRootContract.getDefinition().getData().put("type",TP_TAG);

            //ISSUER, OWNER, CREATOR
            QuorumVoteRole issuerRole = new QuorumVoteRole("issuer", mintingRootContract,"this.state.data.addresses","this.state.data.strong_quorum");
            mintingRootContract.addRole(issuerRole);
            mintingRootContract.addRole(new RoleLink("owner", mintingRootContract,"issuer"));
            mintingRootContract.addRole(new RoleLink("creator", mintingRootContract,"issuer"));

            //ISSUE COUNTER
            QuorumVoteRole issueCounter = new QuorumVoteRole("issue_counter", mintingRootContract,"this.state.data.addresses","this.state.data.strong_quorum");
            issueCounter.addRequiredReference("ref_parent", Role.RequiredMode.ALL_OF);
            issueCounter.addRequiredReference("ref_counter",Role.RequiredMode.ALL_OF);
            mintingRootContract.addRole(issueCounter);

            Reference parentReference = new Reference(mintingRootContract);
            parentReference.name = "ref_parent";
            parentReference.setConditions(Binder.of("all_of", Do.listOf(
                    "ref.id==this.state.parent"
            )));
            mintingRootContract.addReference(parentReference);

            Reference childReference = new Reference(mintingRootContract);
            childReference.name = "ref_child";
            childReference.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.state.parent==this.id"
            )));
            mintingRootContract.addReference(childReference);

            Reference counterReference = new Reference(mintingRootContract);
            counterReference.name = "ref_counter";
            counterReference.setConditions(Binder.of("all_of",Do.listOf(
                    "ref_parent.id in this.revokingItems", //ensure we are registering new revision within currenct transaction
                    "ref_parent.state.data.counters_issued::number+1==this.state.data.counters_issued::number", //ensure we increase counters counter )
                    "ref.id in this.newItems", //counter is in new items
                    "size(this.newItems)==1",
                    "ref.definition.data.multisig_origin==this.origin", //points to this
                    "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                    "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
            )));
            mintingRootContract.addReference(counterReference);


            Reference counterReferenceChnp = new Reference(mintingRootContract);
            counterReferenceChnp.name = "ref_counter_chnp";
            counterReferenceChnp.setConditions(Binder.of("all_of",Do.listOf(
                    "this.id in ref_child.revokingItems", //ensure we are registering new revision within currenct transaction
                    "this.state.data.counters_issued::number+1==ref_child.state.data.counters_issued::number", //ensure we increase counters counter )
                    "ref.id in ref_child.newItems", //counter is in new items
                    "size(ref_child.newItems)==1",
                    "ref.definition.data.multisig_origin==this.origin", //points to this
                    "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                    "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
            )));
            mintingRootContract.addReference(counterReferenceChnp);

            SimpleRole expectedCounterIssuerRole = new SimpleRole("expected_counter_issuer", mintingRootContract);
            expectedCounterIssuerRole.addRequiredReference("ref_issue_counter",Role.RequiredMode.ALL_OF);
            mintingRootContract.addRole(expectedCounterIssuerRole);

            Reference expectedCounterIssuerRef = new Reference(mintingRootContract);
            expectedCounterIssuerRef.name = "expected_counter_issuer";
            expectedCounterIssuerRef.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.origin==this.definition.data.multisig_origin",
                    "this.id in ref.newItems",
                    "this can_perform ref.state.roles.issue_counter"
            )));
            mintingRootContract.addReference(expectedCounterIssuerRef);


            SimpleRole expectedCoinIssuerRole = new SimpleRole("expected_coin_issuer", mintingRootContract);
            expectedCoinIssuerRole.addRequiredReference("ref_counter_rev", Role.RequiredMode.ALL_OF);
            expectedCoinIssuerRole.addRequiredReference("ref_coin_issuer",Role.RequiredMode.ALL_OF);
            mintingRootContract.addRole(expectedCoinIssuerRole);

            Reference expectedCoinIssuerRef = new Reference(mintingRootContract);
            expectedCoinIssuerRef.name = "expected_coin_issuer";
            expectedCoinIssuerRef.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.origin==this.definition.data.multisig_origin",
                    "this.id in ref_counter_rev.newItems",
                    "this can_perform ref.state.roles.issue_coin"
            )));
            mintingRootContract.addReference(expectedCoinIssuerRef);


            ChangeOwnerPermission expectedChown = new ChangeOwnerPermission(new RoleLink("@chown", mintingRootContract,"owner"));
            expectedChown.setId("expected_chown");
            mintingRootContract.addPermission(expectedChown);

            RoleLink expectedRevokeRoleCoin = new RoleLink("@revoke", mintingRootContract, "owner");
            expectedRevokeRoleCoin.addRequiredReference("revoke_ref", Role.RequiredMode.ALL_OF);
            RevokePermission expectedRevoke = new RevokePermission(expectedRevokeRoleCoin);
            expectedRevoke.setId("expected_revoke");
            mintingRootContract.addPermission(expectedRevoke);

            Reference expectedRevokeRefCoin = new Reference(mintingRootContract);
            expectedRevokeRefCoin.name = "expected_revoke_ref";
            expectedRevokeRefCoin.setConditions(Binder.of("all_of",Do.listOf("this.id in ref_counter_rev.revokingItems")));
            mintingRootContract.addReference(expectedRevokeRefCoin);

            SplitJoinPermission expectedSjp = new SplitJoinPermission(new RoleLink("@chown", mintingRootContract,"owner"),Binder.of(
                    "field_name", "amount",
                    "join_match_fields", Do.listOf(
                            "issuer",
                            "definition.references.ref_counter_rev",
                            "definition.references.ref_coin_issuer",
                            "definition.data.coin_code",
                            "definition.data.multisig_origin",
                            "definition.permissions.revoke",
                            "definition.references.revoke_ref"
                    )
            ));
            expectedSjp.setId("expected_sjp");
            mintingRootContract.addPermission(expectedSjp);


            SimpleRole dummyMultisig = new SimpleRole("dummy", mintingRootContract);
            dummyMultisig.addRequiredReference("expected_counter_issuer",Role.RequiredMode.ALL_OF);
            dummyMultisig.addRequiredReference("expected_coin_issuer",Role.RequiredMode.ALL_OF);
            dummyMultisig.addRequiredReference("expected_coin_counter_rev",Role.RequiredMode.ALL_OF);
            dummyMultisig.addRequiredReference("expected_revoke_ref",Role.RequiredMode.ALL_OF);


            mintingRootContract.addRole(dummyMultisig);

            //MODIFY QUORUMS PERMISSION
            QuorumVoteRole updateQuorumRole = new QuorumVoteRole("update_quorum", mintingRootContract,"this.state.data.addresses","this.state.data.strong_quorum");
            mintingRootContract.addRole(updateQuorumRole);

            ModifyDataPermission mdpMultisig = new ModifyDataPermission(new RoleLink("@mdp", mintingRootContract,"update_quorum"),Binder.of("fields",Binder.of(
                    "addresses",null,
                    "quorum",null,
                    "strong_quorum",null
            )));
            mintingRootContract.addPermission(mdpMultisig);

            QuorumVoteRole chnpRoleMultisig = new QuorumVoteRole("@chnp", mintingRootContract,"this.state.data.addresses","this.state.data.strong_quorum");
            chnpRoleMultisig.addRequiredReference("ref_child", Role.RequiredMode.ALL_OF);
            chnpRoleMultisig.addRequiredReference("ref_counter_chnp", Role.RequiredMode.ALL_OF);
            //COUNTER CONTRACTS ISSUED COUNT
            ChangeNumberPermission chnpMultisig = new ChangeNumberPermission(chnpRoleMultisig,Binder.of(
                    "min_step","1",
                    "max_step","1",
                    "field_name","counters_issued"
            ));
            mintingRootContract.addPermission(chnpMultisig);
            mintingRootContract.getStateData().put("counters_issued",0);


            Reference counterRevReference = new Reference(mintingRootContract);
            counterRevReference.name = "ref_counter_rev";
            counterRevReference.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.state.parent in ref.revokingItems", //
                    "ref.definition.data.multisig_origin==this.origin", //points to this
                    "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                    "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
            )));
            mintingRootContract.addReference(counterRevReference);

            Reference expectedCoinCounterRevReference = new Reference(mintingRootContract);
            expectedCoinCounterRevReference.name = "expected_coin_counter_rev";
            expectedCoinCounterRevReference.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.state.parent in ref.revokingItems", //
                    "ref.definition.data.multisig_origin==this.definition.data.multisig_origin", //points to this
                    "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                    "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
            )));
            mintingRootContract.addReference(expectedCoinCounterRevReference);

            Reference counterRevParentReference = new Reference(mintingRootContract);
            counterRevParentReference.name = "ref_counter_rev_parent";
            counterRevParentReference.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.id==ref_counter_rev.state.parent"
            )));
            mintingRootContract.addReference(counterRevParentReference);

            Reference tokenRef = new Reference(mintingRootContract);
            tokenRef.name = "ref_token";
            tokenRef.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.definition.data.coin_code==ref_counter_rev.definition.data.coin_code",
                    "ref.issuer==this.state.roles.expected_coin_issuer",
                    "ref.definition.references.ref_coin_issuer==this.definition.references.expected_coin_issuer",
                    "ref.definition.references.ref_counter_rev==this.definition.references.expected_coin_counter_rev",
                    "ref.definition.data.multisig_origin==this.origin",
                    "ref.state.roles.expected_counter_issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                    "ref.definition.references.expected_counter_issuer==this.definition.references.expected_counter_issuer", //expected ref in issuer
                    "size(ref.definition.permissions)==3",
                    "ref.definition.references.revoke_ref==this.definition.references.expected_revoke_ref",
                    "ref.definition.permissions.chown==this.definition.permissions.expected_chown",
                    "ref.definition.permissions.sjp==this.definition.permissions.expected_sjp",
                    "ref.definition.permissions.revoke==this.definition.permissions.expected_revoke"

            )));
            mintingRootContract.addReference(tokenRef);


            Reference issuedCongruenceRef = new Reference(mintingRootContract);
            issuedCongruenceRef.name = "ref_issued_congruence";
            issuedCongruenceRef.setConditions(Binder.of("any_of",Do.listOf(
                    Binder.of("all_of", Do.listOf(
                            "ref_token.id in ref_counter_rev.newItems",
                            "size(ref_counter_rev.newItems)==1",
                            "size(ref_counter_rev.revokingItems)==1",
                            "ref_counter_rev.state.data.issued::number-ref_counter_rev_parent.state.data.issued::number==ref_token.state.data.amount::number"
                    )),
                    Binder.of("all_of", Do.listOf(
                            "ref_token.id in ref_counter_rev.revokingItems",
                            "size(ref_counter_rev.newItems)==0",
                            "size(ref_counter_rev.revokingItems)==2",
                            "ref_counter_rev_parent.state.data.issued::number-ref_counter_rev.state.data.issued::number==ref_token.state.data.amount::number"
                    ))
            )));
            mintingRootContract.addReference(issuedCongruenceRef);


            QuorumVoteRole issueCoinRole = new QuorumVoteRole("issue_coin", mintingRootContract,"this.state.data.addresses","this.state.data.quorum");
            issueCoinRole.addRequiredReference("ref_token",Role.RequiredMode.ANY_OF);
            issueCoinRole.addRequiredReference("ref_counter_rev",Role.RequiredMode.ALL_OF);
            issueCoinRole.addRequiredReference("ref_counter_rev_parent",Role.RequiredMode.ALL_OF);
            issueCoinRole.addRequiredReference("ref_issued_congruence",Role.RequiredMode.ALL_OF);
            mintingRootContract.addRole(issueCoinRole);

            mintingRootContract.seal();

            Contract transactionRoot = new Contract();
            transactionRoot.setExpiresAt(ZonedDateTime.now().plusDays(30));
            transactionRoot.setIssuerKeys(multisigAddresses);
            transactionRoot.addRole(new RoleLink("owner",transactionRoot,"issuer"));
            transactionRoot.addRole(new RoleLink("creator",transactionRoot,"issuer"));
            transactionRoot.addNewItems(mintingRootContract);
            transactionRoot.seal();
            toBeRegistered = transactionRoot.getTransactionPack();
            toBeRegistered.addTag(TP_TAG,mintingRootContract.getId());
        }

        private void checkValidity() {
            if(mintingRootContract == null) {
                throw new IllegalArgumentException("minting root contract is not found");
            }
            if(!mintingRootContract.getDefinition().getData().getString("type").equals(TP_TAG)) {
                throw new IllegalArgumentException("invalid minting root contract: expected definition.data.type is '" + TP_TAG + "' found '" + mintingRootContract.getDefinition().getData().getString("type")  + "'");
            }
            toBeRegistered = null;
        }

        private MintingRoot() {

        }

        /**
         * Restore root object from previously registered transaction or contract's sealed binary.
         *
         * @param packed packed transaction involving root contract or sealed binary
         */
        public MintingRoot(byte[] packed) throws IOException {
            Object x = Boss.load(packed);

            if (x instanceof TransactionPack) {
                initFromTransactionPack((TransactionPack)x);
            } else {
                mintingRootContract = Contract.fromSealedBinary(packed);
                checkValidity();
            }
        }

        protected void initFromTransactionPack(TransactionPack pack) {
            mintingRootContract = pack.getTags().get(TP_TAG);
            checkValidity();
        }

        /**
         * Get root contract object
         * @return root contract
         */

        public Contract getContract() {
            return mintingRootContract;
        }

        /**
         * Get transaction to be registered
         *
         * @return transaction to be registered or null if there is nothing to register
         */
        public TransactionPack getTransaction() {
            return toBeRegistered ;
        }

    }

    public static class MintingProtocol {

        private static final String TP_TAG = "managed_token_protocol";
        private TransactionPack toBeRegistered;
        MintingRoot mintingRoot;
        Contract protocolContract;

        /**
         * Creates new protocol object holding contract of appropriate structure.
         * Contract mentioned will then be used to track the amount of minted tokens
         *
         * @param coinCode 'currency' code of tokens
         * @param packedMintingRoot sealed binary of root contract or transaction involving root contract
         * @throws IOException
         */
        public MintingProtocol(String coinCode, byte[] packedMintingRoot) throws IOException {
            this(coinCode,new MintingRoot(packedMintingRoot));
        }

        /**
         * Creates new protocol object holding contract of appropriate structure.
         * Contract mentioned will then be used to track the amount of minted tokens
         *
         * This method creates transaction to be registered {@link #getTransaction()}
         *
         * @param coinCode 'currency' code of tokens
         * @param root root object
         */
        public MintingProtocol(String coinCode, MintingRoot root) {
            Contract rootRev = root.getContract().createRevision();
            rootRev.getStateData().put("counters_issued",1);

            //COUNTER CONTRACT
            protocolContract = new Contract();
            protocolContract.setExpiresAt(ZonedDateTime.now().plusYears(1000));
            protocolContract.getDefinition().getData().put("multisig_origin",rootRev.getOrigin());
            protocolContract.getDefinition().getData().put("coin_code",coinCode);
            protocolContract.getDefinition().getData().put("type",TP_TAG);

            SimpleRole counterIssuer = new SimpleRole("issuer",protocolContract);
            counterIssuer.addRequiredReference("ref_issue_counter",Role.RequiredMode.ALL_OF);
            protocolContract.addRole(counterIssuer);

            Reference counterIssueCounterRef = new Reference(protocolContract);
            counterIssueCounterRef.name = "ref_issue_counter";
            counterIssueCounterRef.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.origin==this.definition.data.multisig_origin",
                    "this.id in ref.newItems",
                    "this can_perform ref.state.roles.issue_counter"
            )));
            protocolContract.addReference(counterIssueCounterRef);




            protocolContract.addRole(new RoleLink("owner",protocolContract,"issuer"));
            protocolContract.addRole(new RoleLink("creator",protocolContract,"issuer"));

            protocolContract.getStateData().put("issued","0");

            ModifyDataPermission mdpCounter = new ModifyDataPermission(new RoleLink("@mdp",protocolContract,"issue_coin"),Binder.of("fields",Binder.of(
                    "issued",null
            )));
            protocolContract.addPermission(mdpCounter);


            SimpleRole issueCoinRoleCounter = new SimpleRole("issue_coin",protocolContract);
            issueCoinRoleCounter.addRequiredReference("ref_issue_coin",Role.RequiredMode.ALL_OF);
            protocolContract.addRole(issueCoinRoleCounter);

            Reference issueCoinRef = new Reference(protocolContract);
            issueCoinRef.name = "ref_issue_coin";
            issueCoinRef.setConditions(Binder.of("all_of",Do.listOf(
                    "ref.origin==this.definition.data.multisig_origin",
                    "this can_perform ref.state.roles.issue_coin"
            )));
            protocolContract.addReference(issueCoinRef);

            protocolContract.seal();

            rootRev.addNewItems(protocolContract);
            rootRev.seal();

            try {
                mintingRoot = new MintingRoot(rootRev.getLastSealedBinary());
            } catch (IOException e) {
                throw new IllegalStateException("should never happen");
            }

            Contract transactionRoot = new Contract(new PrivateKey(2048));
            transactionRoot.setExpiresAt(ZonedDateTime.now().plusDays(30));
            transactionRoot.addNewItems(rootRev);
            transactionRoot.seal();
            toBeRegistered = transactionRoot.getTransactionPack();
            toBeRegistered.addTag(MintingRoot.TP_TAG,rootRev.getId());
            toBeRegistered.addTag(TP_TAG,protocolContract.getId());

        }

        /**
         * Restore protocol object from previously registered transaction
         *
         * @param previousTransaction packed transaction involving root contract and protocol contract
         */
        public MintingProtocol(byte[] previousTransaction) throws IOException {
            this(previousTransaction,new MintingRoot(previousTransaction));
        }

        /**
         * Restore protocol object from previously registered transaction or contract's sealed binary.
         *
         * @param packed packed transaction involving protocol contract or sealed binary
         * @param packedMintingRoot packed transaction involving root contract or sealed binary
         */
        public MintingProtocol(byte[] packed, byte[] packedMintingRoot) throws IOException {
            this(packed,new MintingRoot(packedMintingRoot));
        }

        /**
         * Restore protocol object from previously registered transaction or contract's sealed binary.
         *
         * @param packed packed transaction involving protocol contract or sealed binary
         * @param root root object
         */
        public MintingProtocol(byte[] packed, MintingRoot root) throws IOException {

            if(root == null) {
                throw new IllegalArgumentException("MintingRoot must be provided");
            }
            mintingRoot = root;

            Object x = Boss.load(packed);
            if (x instanceof TransactionPack) {
                initFromTransactionPack((TransactionPack)x);
            } else {
                protocolContract = Contract.fromSealedBinary(packed);
                checkValidity();
            }

        }

        private void initFromTransactionPack(TransactionPack pack) {
            protocolContract = pack.getTags().get(TP_TAG);
            checkValidity();
        }

        private void checkValidity() {
            if(protocolContract == null) {
                throw new IllegalArgumentException("minting root contract is not found");
            }
            if(!protocolContract.getDefinition().getData().getString("type").equals(TP_TAG)) {
                throw new IllegalArgumentException("invalid minting protocol contract: expected definition.data.type is '" + TP_TAG + "' found '" + protocolContract.getDefinition().getData().getString("type")  + "'");
            }

            if(!protocolContract.getDefinition().getData().get("multisig_origin").equals(mintingRoot.getContract().getOrigin())) {
                throw new IllegalArgumentException("multisig origin missmatch: protocol refers to '" + protocolContract.getDefinition().getData().get("multisig_origin") + "' while root has '" + mintingRoot.getContract().getOrigin()  + "'");
            }

            toBeRegistered = null;
        }

        /**
         * Get protocol contract
         * @return protocol contract
         */
        public Contract getContract() {
            return protocolContract;
        }

        /**
         * Get root object
         * @return root object
         */
        public MintingRoot getMintingRoot() {
            return mintingRoot;
        }

        /**
         * Get transaction to be registered
         *
         * @return transaction to be registered or null if there is nothing to register
         */
        public  TransactionPack getTransaction() {
            return toBeRegistered;
        }

    }

    private static final String TP_TAG = "managed_token";
    private TransactionPack toBeRegistered;
    private MintingProtocol mintingProtocol;
    private Contract tokenContract;

    /**
     * The last known root contract could become outdated due to multisig changes or issuing new protocol
     * In this case there is a way to updated root contract within already formed transaction by calling this method
     *
     * @param transactionPack transaction to updated
     * @param mintingRoot root object containing an updated contract
     */
    public static void updateMintingRootReference(TransactionPack transactionPack, MintingRoot mintingRoot) {
        transactionPack.getReferencedItems().remove(transactionPack.getTags().get(MintingRoot.TP_TAG).getId());
        transactionPack.addReferencedItem(mintingRoot.getContract());
        transactionPack.addTag(MintingRoot.TP_TAG,mintingRoot.getContract().getId());
    }

    /**
     * The last known root contract could become outdated due to multisig changes or issuing new protocol
     * In this case there is a way to updated root contract within already formed transaction by calling this method
     *
     * @param transactionPack transaction to updated
     * @param packedMintingRoot packed transaction involving root contract or sealed binary
     */
    public static void updateMintingRootReference(TransactionPack transactionPack, byte[] packedMintingRoot) throws IOException {
        updateMintingRootReference(transactionPack,new MintingRoot(packedMintingRoot));
    }

    /**
     * Create new token object ready to issue. Coin code is taken from protocol contract provided
     *
     * @param amount amount to issue
     * @param owner owner address
     * @param previousTransaction packed transaction involving root contract and protocol contract
     * @throws IOException
     */
    public ManagedToken(BigDecimal amount, KeyAddress owner, byte[] previousTransaction) throws IOException {
        this(amount, owner, new MintingProtocol(previousTransaction));
    }

    /**
     * Create new token object ready to issue. Coin code is taken from protocol contract provided
     *
     * @param amount amount to issue
     * @param owner owner address
     * @param packedMintingProtocol packed transaction involving protocol contract or its sealed binary
     * @param packedMintingRoot packed transaction involving root contract or its sealed binary
     * @throws IOException
     */
    public ManagedToken(BigDecimal amount, KeyAddress owner, byte[] packedMintingProtocol, byte[] packedMintingRoot) throws IOException {
        this(amount, owner, new MintingProtocol(packedMintingProtocol,packedMintingRoot));
    }

    /**
     * Create new token object ready to issue. Coin code is taken from protocol contract provided
     *
     * This method creates transaction to be registered {@link #getTransaction()}
     *
     * @param amount amount to issue
     * @param owner owner address
     * @param protocol restored protocol object
     * @throws IOException
     */
    public ManagedToken(BigDecimal amount, KeyAddress owner, MintingProtocol protocol) {
        Contract protocolRev = protocol.getContract().createRevision();
        protocolRev.addRole(new RoleLink("creator",protocolRev,"issue_coin"));
        protocolRev.getStateData().put("issued",amount.add(new BigDecimal(protocolRev.getStateData().getString("issued"))).toString());

        tokenContract = new Contract();
        tokenContract.setExpiresAt(ZonedDateTime.now().plusYears(1000));
        SimpleRole issuerCoin = new SimpleRole("issuer",tokenContract);
        issuerCoin.addRequiredReference("ref_counter_rev", Role.RequiredMode.ALL_OF);
        issuerCoin.addRequiredReference("ref_coin_issuer", Role.RequiredMode.ALL_OF);
        tokenContract.addRole(issuerCoin);
        tokenContract.setOwnerKey(owner);
        tokenContract.addRole(new RoleLink("creator",tokenContract,"issuer"));

        ChangeOwnerPermission chown = new ChangeOwnerPermission(new RoleLink("@chown",tokenContract,"owner"));
        chown.setId("chown");
        tokenContract.addPermission(chown);

        RoleLink revokeRole = new RoleLink("@revoke", tokenContract, "owner");
        revokeRole.addRequiredReference("revoke_ref", Role.RequiredMode.ALL_OF);
        RevokePermission revoke = new RevokePermission(revokeRole);
        revoke.setId("revoke");
        tokenContract.addPermission(revoke);

        Reference revokeRefCoin = new Reference(tokenContract);
        revokeRefCoin.name = "revoke_ref";
        revokeRefCoin.setConditions(Binder.of("all_of",Do.listOf("this.id in ref_counter_rev.revokingItems")));
        tokenContract.addReference(revokeRefCoin);

        SplitJoinPermission sjp = new SplitJoinPermission(new RoleLink("@chown",tokenContract,"owner"),Binder.of(
                "field_name", "amount",
                "join_match_fields", Do.listOf(
                        "issuer",
                        "definition.references.ref_counter_rev",
                        "definition.references.ref_coin_issuer",
                        "definition.data.coin_code",
                        "definition.data.multisig_origin",
                        "definition.permissions.revoke",
                        "definition.references.revoke_ref"
                )
        ));
        sjp.setId("sjp");
        tokenContract.addPermission(sjp);


        Reference coinIssuerRef = new Reference(tokenContract);
        coinIssuerRef.name = "ref_coin_issuer";
        coinIssuerRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref_counter_rev.newItems",
                "this can_perform ref.state.roles.issue_coin"
        )));
        tokenContract.addReference(coinIssuerRef);

        Reference counterRevReferenceCoin = new Reference(tokenContract);
        counterRevReferenceCoin.name = "ref_counter_rev";
        counterRevReferenceCoin.setConditions(Binder.of("all_of",Do.listOf(
                "ref.state.parent in ref.revokingItems", //
                "ref.definition.data.multisig_origin==this.definition.data.multisig_origin", //points to this
                "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
        )));
        tokenContract.addReference(counterRevReferenceCoin);

        SimpleRole dummy = new SimpleRole("dummy",tokenContract);
        dummy.addRequiredReference("expected_counter_issuer", Role.RequiredMode.ALL_OF);
        tokenContract.addRole(dummy);

        SimpleRole expectedCounterIssuerRoleCoin = new SimpleRole("expected_counter_issuer",tokenContract);
        expectedCounterIssuerRoleCoin.addRequiredReference("ref_issue_counter",Role.RequiredMode.ALL_OF);
        tokenContract.addRole(expectedCounterIssuerRoleCoin);

        Reference expectedCounterIssuerRefCoin = new Reference(tokenContract);
        expectedCounterIssuerRefCoin.name = "expected_counter_issuer";
        expectedCounterIssuerRefCoin.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref.newItems",
                "this can_perform ref.state.roles.issue_counter"
        )));
        tokenContract.addReference(expectedCounterIssuerRefCoin);

        tokenContract.getStateData().put("amount",amount.toString());
        tokenContract.getDefinition().getData().put("multisig_origin",protocol.getContract().getDefinition().getData().get("multisig_origin"));
        tokenContract.getDefinition().getData().put("coin_code",protocol.getContract().getDefinition().getData().get("coin_code"));
        tokenContract.getDefinition().getData().put("type",TP_TAG);

        tokenContract.seal();

        protocolRev.addNewItems(tokenContract);
        protocolRev.seal();

        try {
            mintingProtocol = new MintingProtocol(protocolRev.getLastSealedBinary(),protocol.getMintingRoot());
        } catch (IOException e) {
            e.printStackTrace();
        }


        Contract transactionRoot = new Contract(new PrivateKey(2048));
        transactionRoot.setExpiresAt(ZonedDateTime.now().plusDays(30));
        transactionRoot.addNewItems(protocolRev);
        transactionRoot.seal();
        toBeRegistered = transactionRoot.getTransactionPack();
        toBeRegistered.addTag(MintingProtocol.TP_TAG,protocolRev.getId());
        toBeRegistered.addTag(TP_TAG,tokenContract.getId());
        toBeRegistered.addReferencedItem(mintingProtocol.getMintingRoot().getContract());
        toBeRegistered.addTag(MintingRoot.TP_TAG,mintingProtocol.getMintingRoot().getContract().getId());

    }

    /**
     * Restore token object.
     *
     * @param sealedBinary token sealed binary
     * @param previousTransaction packed transaction involving root contract and protocol contract
     */
    public ManagedToken(byte[] sealedBinary, byte[] previousTransaction) throws IOException {
        this(sealedBinary,new MintingProtocol(previousTransaction));
    }

    /**
     * Restore token object.
     *
     * @param sealedBinary token sealed binary
     * @param packedMintingProtocol packed transaction involving protocol contract or sealed binary
     * @param packedMintingRoot packed transaction involving root contract or sealed binary
     */
    public ManagedToken(byte[] sealedBinary, byte[] packedMintingProtocol, byte[] packedMintingRoot) throws IOException {
        this(sealedBinary,new MintingProtocol(packedMintingProtocol,packedMintingRoot));
    }

    /**
     * Restore token object.
     *
     * @param sealedBinary token sealed binary
     * @param protocol protocol object
     */
    public ManagedToken(byte[] sealedBinary, MintingProtocol protocol) throws IOException {
        if(protocol == null) {
            throw new IllegalArgumentException("MintingProtocol must be provided");
        }
        mintingProtocol = protocol;

        Object x = Boss.load(sealedBinary);
        if (x instanceof TransactionPack) {
            throw new IllegalArgumentException("Expected contract sealed binary. Found packed transaciton");
        } else {
            tokenContract = Contract.fromSealedBinary(sealedBinary);
            checkValidity();
        }
    }

    private void checkValidity() {
        if(tokenContract == null) {
            throw new IllegalArgumentException("token contract is not found");
        }
        if(!tokenContract.getDefinition().getData().getString("type").equals(TP_TAG)) {
            throw new IllegalArgumentException("invalid minting protocol contract: expected definition.data.type is '" + TP_TAG + "' found '" + tokenContract.getDefinition().getData().getString("type")  + "'");
        }

        if(!tokenContract.getDefinition().getData().get("multisig_origin").equals(mintingProtocol.getContract().getDefinition().getData().get("multisig_origin"))) {
            throw new IllegalArgumentException("multisig origin missmatch: token refers to '" + tokenContract.getDefinition().getData().get("multisig_origin") + "' while protocol referencing to '" + mintingProtocol.getContract().getDefinition().getData().get("multisig_origin")  + "'");
        }

        toBeRegistered = null;
    }

    /**
     * Get token contract
     * @return token contract
     */
    public Contract getContract() {
        return tokenContract;
    }

    /**
     * Get protocol object
     * @return
     */

    public MintingProtocol getMintingProtocol() {
        return mintingProtocol;
    }

    /**
     * Get transaction to be registered
     *
     * @return transaction to be registered or null if there is nothing to register
     */
    public TransactionPack getTransaction() {
        return toBeRegistered;
    }

    /**
     * Revoke token
     *
     * This method creates transaction to be registered {@link #getTransaction()}
     *
     */
    public void revoke() {
        if(toBeRegistered != null)
            throw new IllegalStateException("transaction already generated");
        Contract protocolRev = mintingProtocol.getContract().createRevision();
        protocolRev.addRole(new RoleLink("creator",protocolRev,"issue_coin"));
        protocolRev.getStateData().put("issued",new BigDecimal(protocolRev.getStateData().getString("issued")).subtract(new BigDecimal(tokenContract.getStateData().getString("amount"))).toString());

        protocolRev.addRevokingItems(tokenContract);
        protocolRev.seal();

        try {
            mintingProtocol = new MintingProtocol(protocolRev.getLastSealedBinary(),mintingProtocol.getMintingRoot());
        } catch (IOException e) {
            e.printStackTrace();
        }


        Contract transactionRoot = new Contract(new PrivateKey(2048));
        transactionRoot.setExpiresAt(ZonedDateTime.now().plusDays(30));
        transactionRoot.addNewItems(protocolRev);
        transactionRoot.seal();
        toBeRegistered = transactionRoot.getTransactionPack();
        toBeRegistered.addTag(MintingProtocol.TP_TAG,protocolRev.getId());
        toBeRegistered.addTag(TP_TAG,tokenContract.getId());
        toBeRegistered.addReferencedItem(mintingProtocol.getMintingRoot().getContract());
        toBeRegistered.addTag(MintingRoot.TP_TAG,mintingProtocol.getMintingRoot().getContract().getId());

    }
}

