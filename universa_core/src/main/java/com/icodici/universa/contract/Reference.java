package com.icodici.universa.contract;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.Role;
import com.icodici.crypto.KeyAddress;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64u;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.icodici.universa.contract.Reference.conditionsModeType.all_of;
import static com.icodici.universa.contract.Reference.conditionsModeType.any_of;

@BiType(name = "Reference")
public class Reference implements BiSerializable {

    public enum conditionsModeType {
        all_of,
        any_of,
        simple_condition
    }

    public String name = "";
    public int type = TYPE_EXISTING_DEFINITION;
    public String transactional_id = "";
    public HashId contract_id = null;
    public boolean required = true;
    public HashId origin = null;
    public List<Role> signed_by = new ArrayList<>();
    public List<String> fields = new ArrayList<>();
    public List<String> roles = new ArrayList<>();
    public List<Approvable> matchingItems = new ArrayList<>();
    private Binder conditions = new Binder();
    private Contract baseContract;
    private String comment = null;

    public static final int TYPE_TRANSACTIONAL = 1;
    public static final int TYPE_EXISTING_DEFINITION = 2;
    public static final int TYPE_EXISTING_STATE = 3;

    @Deprecated
    public Reference() {}

    /**
     *adds a basic contract for reference
     *
     *@param contract basic contract.
     */
    public Reference(Contract contract) {
        baseContract = contract;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.name = data.getString("name", null);

        this.type = data.getInt("type", null);
        this.comment = data.getString("comment", null);

        this.transactional_id = data.getString("transactional_id", "");

//        String str_contract_id = data.getString("contract_id", null);
//        if (str_contract_id != null)
//            this.contract_id = HashId.withDigest(Bytes.fromHex(str_contract_id).getData());
//        else
//            this.contract_id = null;
//
//        this.required = data.getBoolean("required", true);
//
//        String str_origin = data.getString("origin", null);
//        if (str_origin != null)
//            this.origin = HashId.withDigest(Bytes.fromHex(str_origin).getData());
//        else
//            this.origin = null;

        this.contract_id = deserializer.deserialize(data.get("contract_id"));
        this.origin = deserializer.deserialize(data.get("origin"));

        this.signed_by = deserializer.deserializeCollection(data.getList("signed_by", new ArrayList<>()));


        List<String> roles = data.getList("roles", null);
        if (roles != null) {
            this.roles.clear();
            roles.forEach(this::addRole);
        }

        List<String> fields = data.getList("fields", null);
        if (fields != null) {
            this.fields.clear();
            fields.forEach(this::addField);
        }

        conditions = data.getBinder("where");
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder data = new Binder();
        data.set("name", s.serialize(this.name));
        data.set("type", s.serialize(this.type));
        data.set("transactional_id", s.serialize(this.transactional_id));
//        if (this.contract_id != null)
//            data.set("contract_id", s.serialize(Bytes.toHex(this.contract_id.getDigest())));
//        data.set("required", s.serialize(this.required));
//        if (this.origin != null)
//            data.set("origin", s.serialize(Bytes.toHex(this.origin.getDigest())));
        if (this.contract_id != null)
            data.set("contract_id", s.serialize(this.contract_id));
        data.set("required", s.serialize(this.required));
        if (this.origin != null)
            data.set("origin", s.serialize(this.origin));
        data.set("signed_by", s.serialize(signed_by));

        data.set("roles", s.serialize(this.roles));
        data.set("fields", s.serialize(this.fields));

        data.set("where", s.serialize(this.conditions));

        if (comment != null)
            data.set("comment", comment);

        return data;
    }

    static public Reference fromDslBinder(Binder ref, Contract contract) {
        String name = ref.getString("name");
        String comment = ref.getString("comment", null);
        Binder where = null;
        try {
            where = ref.getBinderOrThrow("where");
        }
        catch (Exception e)
        {
            // Insert simple condition to binder with key all_of
            List<String> simpleConditions = ref.getList("where", null);
            if (simpleConditions != null)
                where = new Binder(all_of.name(), simpleConditions);
        }

        Reference reference = new Reference(contract);

        if (name == null)
            throw new IllegalArgumentException("Expected reference name");

        reference.setName(name);
        reference.setComment(comment);

        if (where != null)
            reference.setConditions(where);

        return reference;
    }


    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Reference))
            return false;
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = ((Reference)obj).serialize(new BiSerializer());
        return dataThis.equals(dataA);
    }

    public boolean equalsIgnoreType(Reference a) {
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = a.serialize(new BiSerializer());
        dataThis.remove("type");
        dataA.remove("type");
        return dataThis.equals(dataA);
    }

    public boolean equals(Reference a) {
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = a.serialize(new BiSerializer());
        return dataThis.equals(dataA);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    //Operators
    final static String[] operators = {" defined"," undefined","<=",">=","<",">","!=","=="," matches ",
            " is_a "," is_inherit ","inherits ","inherit "," can_play "," in "};

    final static int DEFINED = 0;
    final static int UNDEFINED = 1;
    final static int LESS_OR_EQUAL = 2;
    final static int MORE_OR_EQUAL = 3;
    final static int LESS = 4;
    final static int MORE = 5;
    final static int NOT_EQUAL = 6;
    final static int EQUAL = 7;
    final static int MATCHES = 8;
    final static int IS_A = 9;
    final static int IS_INHERIT = 10;
    final static int INHERITS = 11;
    final static int INHERIT = 12;
    final static int CAN_PLAY = 13;
    final static int IN = 14;

    //Operations
    final static String[] operations = {"+", "-", "*", "/"};
    final static String[] roundOperations = {"round(", "floor(", "ceil("};

    final static int PLUS = 0;
    final static int MINUS = 1;
    final static int MULT = 2;
    final static int DIV = 3;

    final static int ROUND_OPERATIONS = 100;
    final static int ROUND = 100;
    final static int FLOOR = 101;
    final static int CEIL = 102;

    //Conversions
    final static int NO_CONVERSION = 0;
    final static int CONVERSION_BIG_DECIMAL = 1;  // ::number

    enum compareOperandType {
        FIELD,
        CONSTSTR,
        CONSTOTHER,
        EXPRESSION
    }

    private boolean isObjectMayCastToDouble(Object obj) throws Exception {
        return obj.getClass().getName().endsWith("Float") || obj.getClass().getName().endsWith("Double");
    }

    private boolean isObjectMayCastToLong(Object obj) throws Exception {
        return obj.getClass().getName().endsWith("Byte") || obj.getClass().getName().endsWith("Short") ||
               obj.getClass().getName().endsWith("Integer") || obj.getClass().getName().endsWith("Long");
    }

    private double objectCastToDouble(Object obj) throws Exception {
        double val;

        if (isObjectMayCastToDouble(obj))
            val = (double) obj;
        else if (isObjectMayCastToLong(obj))
            val = objectCastToLong(obj);
        else
            throw new IllegalArgumentException("Expected floating point number operand in condition.");

        return val;
    }

    private long objectCastToLong(Object obj) throws Exception {
        long val;

        if (obj.getClass().getName().endsWith("Byte"))
            val = (byte) obj;
        else if (obj.getClass().getName().endsWith("Short"))
            val = (short) obj;
        else if (obj.getClass().getName().endsWith("Integer"))
            val = (int) obj;
        else if (obj.getClass().getName().endsWith("Long"))
            val = (long) obj;
        else
            throw new IllegalArgumentException("Expected number operand in condition.");

        return val;
    }

    private long objectCastToTimeSeconds(Object obj, String operand, compareOperandType typeOfOperand) throws Exception {
        long val;

        if ((obj == null) && (typeOfOperand == compareOperandType.FIELD))
            throw new IllegalArgumentException("Error getting operand: " + operand);

        if ((obj != null) && obj.getClass().getName().endsWith("ZonedDateTime"))
            val = ((ZonedDateTime) obj).toEpochSecond();
        else if ((obj != null) && obj.getClass().getName().endsWith("String"))
            val = ZonedDateTime.parse((String) obj, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))).toEpochSecond();
        else if ((obj != null) && isObjectMayCastToLong(obj))
            val = objectCastToLong(obj);
        else if (typeOfOperand == compareOperandType.CONSTSTR)
            val = ZonedDateTime.parse(operand, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))).toEpochSecond();
        else if (typeOfOperand == compareOperandType.CONSTOTHER)
            val = Long.parseLong(operand);
        else
            throw new IllegalArgumentException("Error parsing DateTime from operand: " + operand);

        return val;
    }

    private BigDecimal objectCastToBigDecimal(Object obj, String operand, compareOperandType typeOfOperand) throws Exception {
        BigDecimal val;

        if ((obj == null) && (typeOfOperand == compareOperandType.FIELD))
            throw new IllegalArgumentException("Error getting operand: " + operand);

        if ((obj != null) && obj.getClass().getName().endsWith("BigDecimal"))
            return (BigDecimal) obj;

        if ((obj != null) && obj.getClass().getName().endsWith("String"))
            val = new BigDecimal((String) obj);
        else if ((obj != null) && isObjectMayCastToLong(obj))
            val = new BigDecimal(objectCastToLong(obj));
        else if ((obj != null) && isObjectMayCastToDouble(obj))
            val = new BigDecimal(objectCastToDouble(obj));
        else if ((typeOfOperand == compareOperandType.CONSTSTR) || (typeOfOperand == compareOperandType.CONSTOTHER))
            val = new BigDecimal(operand);
        else
            throw new IllegalArgumentException("Error parsing BigDecimal from operand: " + operand);

        return val;
    }

    private Object evaluateOperand(String operand, compareOperandType typeOfOperand, int conversion, Contract refContract,
                                   Collection<Contract> contracts, int iteration) throws Exception {
        Contract operandContract = null;
        int firstPointPos;

        if (operand == null)
            throw new IllegalArgumentException("Error evaluate null operand");

        if (typeOfOperand == compareOperandType.FIELD) {
            if (operand.startsWith("ref.")) {
                operand = operand.substring(4);
                operandContract = refContract;
            } else if (operand.startsWith("this.")) {
                if (baseContract == null)
                    throw new IllegalArgumentException("Use left operand in expression: " + operand + ". But this contract not initialized.");

                operand = operand.substring(5);
                operandContract = baseContract;
            } else if ((firstPointPos = operand.indexOf(".")) > 0) {
                if (baseContract == null)
                    throw new IllegalArgumentException("Use left operand in expression: " + operand + ". But this contract not initialized.");

                Reference ref = baseContract.findReferenceByName(operand.substring(0, firstPointPos));
                if (ref == null)
                    throw new IllegalArgumentException("Not found reference: " + operand.substring(0, firstPointPos));

                for (Contract checkedContract : contracts)
                    if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                        operandContract = checkedContract;

                if (operandContract == null)
                    throw new IllegalArgumentException("Not found referenced contract for reference: " + operand.substring(0, firstPointPos));

                operand = operand.substring(firstPointPos + 1);
            } else
                throw new IllegalArgumentException("Invalid format of left operand in expression: " + operand + ". Missing contract field.");

            return operandContract.get(operand);
        } else {
            if (conversion == CONVERSION_BIG_DECIMAL || operand.length() > 9)   // 10 symbols > int32 => operand * operand > int64(long). Use BigDecimal.
                return new BigDecimal(operand);
            else if (operand.contains("."))
                return Double.parseDouble(operand);
            else
                return Long.parseLong(operand);
        }
    }

    private Object evaluateExpression(Binder expression, Contract refContract, Collection<Contract> contracts, int iteration) {
        Object left;
        Object right;
        Object result;
        compareOperandType typeOfLeftOperand;
        compareOperandType typeOfRightOperand;

        // unpack expression
        String leftOperand = expression.getString("leftOperand", null);
        String rightOperand = expression.getString("rightOperand", null);

        Binder leftExpression = expression.getBinder("left", null);
        Binder rightExpression = expression.getBinder("right", null);

        int operation = expression.getIntOrThrow("operation");

        int typeLeftOperand = expression.getIntOrThrow("typeOfLeftOperand");
        int typeRightOperand = expression.getIntOrThrow("typeOfRightOperand");

        typeOfLeftOperand = compareOperandType.values()[typeLeftOperand];
        typeOfRightOperand = compareOperandType.values()[typeRightOperand];

        int leftConversion = expression.getInt("leftConversion", NO_CONVERSION);
        int rightConversion = expression.getInt("rightConversion", NO_CONVERSION);

        try {
            // evaluate operands
            if (typeOfLeftOperand == compareOperandType.EXPRESSION)
                left = evaluateExpression(leftExpression, refContract, contracts, iteration);
            else
                left = evaluateOperand(leftOperand, typeOfLeftOperand, leftConversion, refContract, contracts, iteration);

            if (typeOfRightOperand == compareOperandType.EXPRESSION)
                right = evaluateExpression(rightExpression, refContract, contracts, iteration);
            else
                right = evaluateOperand(rightOperand, typeOfRightOperand, rightConversion, refContract, contracts, iteration);

            if (left == null || right == null)
                return null;

            // evaluate expression
            if (operation == ROUND)
                result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).setScale(
                        (int) objectCastToLong(right), RoundingMode.HALF_UP);
            else if (operation == FLOOR)
                result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).setScale(
                        (int) objectCastToLong(right), RoundingMode.FLOOR);
            else if (operation == CEIL)
                result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).setScale(
                        (int) objectCastToLong(right), RoundingMode.CEILING);

            else if ((leftConversion == CONVERSION_BIG_DECIMAL) || (rightConversion == CONVERSION_BIG_DECIMAL) ||
                left.getClass().getName().endsWith("BigDecimal") || right.getClass().getName().endsWith("BigDecimal")) {
                // BigDecimals
                if (operation == PLUS)
                    result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).add(
                             objectCastToBigDecimal(right, null, compareOperandType.FIELD));
                else if (operation == MINUS)
                    result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).subtract(
                             objectCastToBigDecimal(right, null, compareOperandType.FIELD));
                else if (operation == MULT)
                    result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).multiply(
                             objectCastToBigDecimal(right, null, compareOperandType.FIELD));
                else if (operation == DIV)
                    result = objectCastToBigDecimal(left, null, compareOperandType.FIELD).divide(
                             objectCastToBigDecimal(right, null, compareOperandType.FIELD), RoundingMode.HALF_UP);
                else
                    throw new IllegalArgumentException("Unknown operation: " + operation);

            } else if (isObjectMayCastToDouble(left) || isObjectMayCastToDouble(right)) {
                // Doubles
                if (operation == PLUS)
                    result = objectCastToDouble(left) + objectCastToDouble(right);
                else if (operation == MINUS)
                    result = objectCastToDouble(left) - objectCastToDouble(right);
                else if (operation == MULT)
                    result = objectCastToDouble(left) * objectCastToDouble(right);
                else if (operation == DIV)
                    result = objectCastToDouble(left) / objectCastToDouble(right);
                else
                    throw new IllegalArgumentException("Unknown operation: " + operation);

            } else if (isObjectMayCastToLong(left) || isObjectMayCastToLong(right)) {
                // Long integers
                if (operation == PLUS)
                    result = objectCastToLong(left) + objectCastToLong(right);
                else if (operation == MINUS)
                    result = objectCastToLong(left) - objectCastToLong(right);
                else if (operation == MULT)
                    result = objectCastToLong(left) * objectCastToLong(right);
                else if (operation == DIV)
                    result = objectCastToLong(left) / objectCastToLong(right);
                else
                    throw new IllegalArgumentException("Unknown operation: " + operation);

            } else
                throw new IllegalArgumentException("Incompatible operand types. Left: " + left.getClass().getName() +
                        ". Right: " + right.getClass().getName());
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error evaluate expression: " + e.getMessage());
        }

        return result;
    }

    private Role prepareRoleToComparison(Object item) {
        if (item instanceof RoleLink &&
                ((RoleLink) item).getReferences(Role.RequiredMode.ALL_OF).isEmpty() &&
                ((RoleLink) item).getReferences(Role.RequiredMode.ANY_OF).isEmpty())
            return ((RoleLink) item).resolve();
        else if (item instanceof String) {
            try {
                String roleString = ((String) item).replaceAll("\\s+", "");       // for key in quotes

                if (roleString.length() > 72) {
                    // Key
                    PublicKey publicKey = new PublicKey(Base64u.decodeCompactString(roleString));
                    return new SimpleRole("roleToComparison", null, Do.listOf(publicKey));
                } else {
                    // Address
                    KeyAddress ka = new KeyAddress(roleString);
                    return new SimpleRole("roleToComparison", null, Do.listOf(ka));
                }
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Key or address compare error in condition: " + e.getMessage());
            }
        } else
            return (Role) item;
    }

    /**
     *The comparison method for finding reference contract
     *
     * @param refContract contract to check for matching
     * @param leftOperand left operand (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param rightOperand right operand (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param leftExpression parsed left expression to evaluate
     * @param rightExpression parsed right expression to evaluate
     * @param typeOfRightOperand type of left operand
     * @param typeOfRightOperand type of right operand
     * @param indxOperator index operator in array of operators
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean compareOperands(Contract refContract,
                                   String leftOperand,
                                   String rightOperand,
                                   Binder leftExpression,
                                   Binder rightExpression,
                                   compareOperandType typeOfLeftOperand,
                                   compareOperandType typeOfRightOperand,
                                   boolean isBigDecimalConversion,
                                   int indxOperator,
                                   Collection<Contract> contracts,
                                   int iteration)
    {
        boolean ret = false;
        Contract leftOperandContract = null;
        Contract rightOperandContract = null;
        Object left = null;
        Object right = null;
        double leftValD = 0;
        double rightValD = 0;
        long leftValL = 0;
        long rightValL = 0;
        BigDecimal leftBigDecimal;
        BigDecimal rightBigDecimal;
        boolean isLeftDouble = false;
        boolean isRightDouble = false;
        int firstPointPos;

        // get operands
        if (leftOperand != null) {
            if (typeOfLeftOperand == compareOperandType.FIELD) {
                if (leftOperand.startsWith("ref.")) {
                    leftOperand = leftOperand.substring(4);
                    leftOperandContract = refContract;
                } else if (leftOperand.startsWith("this.")) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                    leftOperand = leftOperand.substring(5);
                    leftOperandContract = baseContract;
                } else if ((firstPointPos = leftOperand.indexOf(".")) > 0) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                    Reference ref = baseContract.findReferenceByName(leftOperand.substring(0, firstPointPos));
                    if (ref == null)
                        throw new IllegalArgumentException("Not found reference: " + leftOperand.substring(0, firstPointPos));

                    for (Contract checkedContract : contracts)
                        if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                            leftOperandContract = checkedContract;

                    if (leftOperandContract == null)
                        return false;
                        //throw new IllegalArgumentException("Not found referenced contract for reference: " + leftOperand.substring(0, firstPointPos));

                    leftOperand = leftOperand.substring(firstPointPos + 1);
                } else
                    throw new IllegalArgumentException("Invalid format of left operand in condition: " + leftOperand + ". Missing contract field.");
            } else if (typeOfLeftOperand == compareOperandType.CONSTOTHER) {
                if (indxOperator == CAN_PLAY) {
                    if (leftOperand.equals("ref")) {
                        leftOperandContract = refContract;
                    } else if (leftOperand.equals("this")) {
                        if (baseContract == null)
                            throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                        leftOperandContract = baseContract;
                    } else {
                        if (baseContract == null)
                            throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

                        Reference ref = baseContract.findReferenceByName(leftOperand);
                        if (ref == null)
                            throw new IllegalArgumentException("Not found reference: " + leftOperand);

                        for (Contract checkedContract : contracts)
                            if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                                leftOperandContract = checkedContract;

                        if (leftOperandContract == null)
                            return false;
                            //throw new IllegalArgumentException("Not found referenced contract for reference: " + leftOperand);
                    }
                } else if (leftOperand.equals("now"))
                    left = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
            }
        }

        if (rightOperand != null) {     // if != null, rightOperand then FIELD or CONSTANT
            if (typeOfRightOperand == compareOperandType.FIELD) {     // if typeOfRightOperand - FIELD
                if (rightOperand.startsWith("ref.")) {
                    rightOperand = rightOperand.substring(4);
                    rightOperandContract = refContract;
                } else if (rightOperand.startsWith("this.")) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

                    rightOperand = rightOperand.substring(5);
                    rightOperandContract = baseContract;
                } else if ((firstPointPos = rightOperand.indexOf(".")) > 0) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

                    Reference ref = baseContract.findReferenceByName(rightOperand.substring(0, firstPointPos));
                    if (ref == null)
                        throw new IllegalArgumentException("Not found reference: " + rightOperand.substring(0, firstPointPos));

                    for (Contract checkedContract : contracts)
                        if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                            rightOperandContract = checkedContract;

                    if (rightOperandContract == null)
                        return false;

                    rightOperand = rightOperand.substring(firstPointPos + 1);
                } else
                    throw new IllegalArgumentException("Invalid format of right operand in condition: " + rightOperand + ". Missing contract field.");
            } else if (typeOfRightOperand == compareOperandType.CONSTOTHER) {
                if (rightOperand.equals("now"))
                    right = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
            }
        }

        // check operator
        if (rightOperand != null || rightExpression != null) {
            if ((leftOperandContract != null) && (indxOperator != CAN_PLAY))
                left = leftOperandContract.get(leftOperand);
            if (rightOperandContract != null)
                right = rightOperandContract.get(rightOperand);

            if (leftExpression != null) {
                try {
                    left = evaluateExpression(leftExpression, refContract, contracts, iteration);
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("Not found referenced contract for reference"))
                        return false;
                    else
                        throw e;
                }

                typeOfLeftOperand = compareOperandType.FIELD;
                if (left != null && left.getClass().getName().endsWith("BigDecimal"))
                    isBigDecimalConversion = true;
            }

            if (rightExpression != null) {
                try {
                    right = evaluateExpression(rightExpression, refContract, contracts, iteration);
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("Not found referenced contract for reference"))
                        return false;
                    else
                        throw e;
                }

                typeOfRightOperand = compareOperandType.FIELD;
                if (right != null && right.getClass().getName().endsWith("BigDecimal"))
                    isBigDecimalConversion = true;
            }

            try {
                switch (indxOperator) {
                    case LESS:
                    case MORE:
                    case LESS_OR_EQUAL:
                    case MORE_OR_EQUAL:
                        if (typeOfLeftOperand == compareOperandType.FIELD && left == null)
                            break;

                        if (typeOfRightOperand == compareOperandType.FIELD && right == null)
                            break;

                        if (isBigDecimalConversion) {
                            leftBigDecimal = objectCastToBigDecimal(left, leftOperand, typeOfLeftOperand);
                            rightBigDecimal = objectCastToBigDecimal(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == LESS) && (leftBigDecimal.compareTo(rightBigDecimal) == -1)) ||
                                ((indxOperator == MORE) && (leftBigDecimal.compareTo(rightBigDecimal) == 1)) ||
                                ((indxOperator == LESS_OR_EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) < 1)) ||
                                ((indxOperator == MORE_OR_EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) > -1)))
                                ret = true;
                        } else if (((left != null) && left.getClass().getName().endsWith("ZonedDateTime")) ||
                                  ((right != null) && right.getClass().getName().endsWith("ZonedDateTime"))) {
                            long leftTime = objectCastToTimeSeconds(left, leftOperand, typeOfLeftOperand);
                            long rightTime = objectCastToTimeSeconds(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == LESS) && (leftTime < rightTime)) ||
                                ((indxOperator == MORE) && (leftTime > rightTime)) ||
                                ((indxOperator == LESS_OR_EQUAL) && (leftTime <= rightTime)) ||
                                ((indxOperator == MORE_OR_EQUAL) && (leftTime >= rightTime)))
                                ret = true;
                        } else {
                            if ((typeOfLeftOperand == compareOperandType.FIELD) && (left != null)) {
                                if (isLeftDouble = isObjectMayCastToDouble(left))
                                    leftValD = objectCastToDouble(left);
                                else
                                    leftValL = objectCastToLong(left);
                            }

                            if ((typeOfRightOperand == compareOperandType.FIELD) && (right != null)) {
                                if (isRightDouble = isObjectMayCastToDouble(right))
                                    rightValD = objectCastToDouble(right);
                                else
                                    rightValL = objectCastToLong(right);
                            }

                            if ((typeOfLeftOperand == compareOperandType.FIELD) && (typeOfRightOperand == compareOperandType.FIELD)) {
                                if (((indxOperator == LESS) && ((isLeftDouble ? leftValD : leftValL) < (isRightDouble ? rightValD : rightValL))) ||
                                    ((indxOperator == MORE) && ((isLeftDouble ? leftValD : leftValL) > (isRightDouble ? rightValD : rightValL))) ||
                                    ((indxOperator == LESS_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) <= (isRightDouble ? rightValD : rightValL))) ||
                                    ((indxOperator == MORE_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) >= (isRightDouble ? rightValD : rightValL))))
                                    ret = true;
                            } else if ((typeOfLeftOperand == compareOperandType.FIELD) && (typeOfRightOperand == compareOperandType.CONSTOTHER)) { // rightOperand is CONSTANT (null | number | true | false)
                                if ((rightOperand != "null") && (rightOperand != "false") && (rightOperand != "true"))
                                    if ((rightOperand.contains(".") &&
                                        (((indxOperator == LESS) && ((isLeftDouble ? leftValD : leftValL) < Double.parseDouble(rightOperand))) ||
                                        ((indxOperator == MORE) && ((isLeftDouble ? leftValD : leftValL) > Double.parseDouble(rightOperand))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) <= Double.parseDouble(rightOperand))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) >= Double.parseDouble(rightOperand))))) ||
                                        (!rightOperand.contains(".") &&
                                        (((indxOperator == LESS) && ((isLeftDouble ? leftValD : leftValL) < Long.parseLong(rightOperand))) ||
                                        ((indxOperator == MORE) && ((isLeftDouble ? leftValD : leftValL) > Long.parseLong(rightOperand))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) <= Long.parseLong(rightOperand))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && ((isLeftDouble ? leftValD : leftValL) >= Long.parseLong(rightOperand))))))
                                        ret = true;
                            } else if ((typeOfRightOperand == compareOperandType.FIELD) && (typeOfLeftOperand == compareOperandType.CONSTOTHER)) { // leftOperand is CONSTANT (null | number | true | false)
                                if ((leftOperand != "null") && (leftOperand != "false") && (leftOperand != "true"))
                                    if ((leftOperand.contains(".") &&
                                        (((indxOperator == LESS) && (Double.parseDouble(leftOperand) < (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE) && (Double.parseDouble(leftOperand) > (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && (Double.parseDouble(leftOperand) <= (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && (Double.parseDouble(leftOperand) >= (isRightDouble ? rightValD : rightValL))))) ||
                                        (!leftOperand.contains(".") &&
                                        (((indxOperator == LESS) && (Long.parseLong(leftOperand) < (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE) && (Long.parseLong(leftOperand) > (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == LESS_OR_EQUAL) && (Long.parseLong(leftOperand) <= (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == MORE_OR_EQUAL) && (Long.parseLong(leftOperand) >= (isRightDouble ? rightValD : rightValL))))))
                                        ret = true;
                            } else
                                throw new IllegalArgumentException("Invalid operator in condition for string: " + operators[indxOperator]);
                        }

                        break;

                    case NOT_EQUAL:
                    case EQUAL:
                        if (typeOfLeftOperand == compareOperandType.FIELD && left == null && (rightOperand == null || !rightOperand.equals("null")))
                            break;

                        if (typeOfRightOperand == compareOperandType.FIELD && right == null && (leftOperand == null || !leftOperand.equals("null")))
                            break;

                        if (isBigDecimalConversion) {
                            leftBigDecimal = objectCastToBigDecimal(left, leftOperand, typeOfLeftOperand);
                            rightBigDecimal = objectCastToBigDecimal(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) == 0)) ||
                                ((indxOperator == NOT_EQUAL) && (leftBigDecimal.compareTo(rightBigDecimal) != 0)))
                                ret = true;

                        } else if (((left != null) && left.getClass().getName().endsWith("HashId")) ||
                            ((right != null) && right.getClass().getName().endsWith("HashId"))) {
                            HashId leftID;
                            HashId rightID;

                            if ((left != null) && left.getClass().getName().endsWith("HashId"))
                                leftID = (HashId) left;
                            else if ((left != null) && left.getClass().getName().endsWith("String"))
                                leftID = HashId.withDigest((String) left);
                            else
                                leftID = HashId.withDigest(leftOperand);

                            if ((right != null) && right.getClass().getName().endsWith("HashId"))
                                rightID = (HashId) right;
                            else if ((right != null) && right.getClass().getName().endsWith("String"))
                                rightID = HashId.withDigest((String) right);
                            else
                                rightID = HashId.withDigest(rightOperand);

                            ret = leftID.equals(rightID);

                            if (indxOperator == NOT_EQUAL)
                                ret = !ret;

                        } else if (((left != null) && (left.getClass().getName().endsWith("Role") || left.getClass().getName().endsWith("RoleLink"))) ||
                                   ((right != null) && (right.getClass().getName().endsWith("Role") || right.getClass().getName().endsWith("RoleLink")))) { // if role - compare with role, key or address
                            if (((left != null) && (left.getClass().getName().endsWith("Role") || left.getClass().getName().endsWith("RoleLink"))) &&
                                ((right != null) && (right.getClass().getName().endsWith("Role") || right.getClass().getName().endsWith("RoleLink")))) {

                                Role leftRole = prepareRoleToComparison(left);
                                Role rightRole = prepareRoleToComparison(right);

                                if (((indxOperator == NOT_EQUAL) && !leftRole.equalsIgnoreName(rightRole)) ||
                                    ((indxOperator == EQUAL) && leftRole.equalsIgnoreName(rightRole)))
                                    ret = true;

                            } else {
                                Role role;
                                String compareOperand;
                                if ((left != null) && (left.getClass().getName().endsWith("Role") || left.getClass().getName().endsWith("RoleLink"))) {
                                    role = (Role) left;
                                    if ((right != null) && (right.getClass().getName().endsWith("String")))
                                        compareOperand = (String) right;
                                    else
                                        compareOperand = rightOperand;
                                } else {
                                    role = (Role) right;
                                    if ((left != null) && (left.getClass().getName().endsWith("String")))
                                        compareOperand = (String) left;
                                    else
                                        compareOperand = leftOperand;
                                }

                                role = prepareRoleToComparison(role);
                                Role compareRole = prepareRoleToComparison(compareOperand);

                                ret = role.equalsIgnoreName(compareRole);

                                if (indxOperator == NOT_EQUAL)
                                    ret = !ret;
                            }

                        } else if (((left != null) && left.getClass().getName().endsWith("ZonedDateTime")) ||
                                   ((right != null) && right.getClass().getName().endsWith("ZonedDateTime"))) {
                            long leftTime = objectCastToTimeSeconds(left, leftOperand, typeOfLeftOperand);
                            long rightTime = objectCastToTimeSeconds(right, rightOperand, typeOfRightOperand);

                            if (((indxOperator == NOT_EQUAL) && (leftTime != rightTime)) ||
                                ((indxOperator == EQUAL) && (leftTime == rightTime)))
                                ret = true;
                        }  else if ((left != null) && left.getClass().getName().endsWith("Reference") &&
                                    (right != null) && right.getClass().getName().endsWith("Reference")) {

                            boolean equals = ((Reference) left).equalsIgnoreType((Reference) right);

                            ret = indxOperator == (equals ? EQUAL : NOT_EQUAL);

                        }  else if (((left != null) && left.getClass().getName().endsWith("Binder") && ((Binder)left).get("contractForSearchByTag") != null) ||
                                ((right != null) && right.getClass().getName().endsWith("Binder") && ((Binder)right).get("contractForSearchByTag") != null)) {

                            Contract taggedContract = null;
                            String tag = null;
                            if ((left != null) && left.getClass().getName().endsWith("Binder") && ((Binder)left).get("contractForSearchByTag") != null) {
                                if (((Binder)left).get("contractForSearchByTag").getClass().getName().endsWith("Contract") &&
                                    (right == null || right.getClass().getName().endsWith("String"))) {
                                    taggedContract = (Contract) ((Binder)left).get("contractForSearchByTag");

                                    if (right != null && right.getClass().getName().endsWith("String"))
                                        tag = (String) right;
                                    else
                                        tag = rightOperand;
                                }
                            } else {
                                if (((Binder)right).get("contractForSearchByTag").getClass().getName().endsWith("Contract") &&
                                    (left == null || left.getClass().getName().endsWith("String"))) {
                                    taggedContract = (Contract) ((Binder)right).get("contractForSearchByTag");

                                    if (left != null && left.getClass().getName().endsWith("String"))
                                        tag = (String) left;
                                    else
                                        tag = leftOperand;
                                }
                            }

                            if (taggedContract == null || tag == null)
                                throw new IllegalArgumentException("Incorrect operands for search by tag");

                            Contract foundedContract = taggedContract.getTransactionPack().getTags().get(tag);

                            boolean equals = foundedContract != null && foundedContract.getId().equals(taggedContract.getId());

                            ret = indxOperator == (equals ? EQUAL : NOT_EQUAL);

                        } else if ((typeOfLeftOperand == compareOperandType.FIELD) && (typeOfRightOperand == compareOperandType.FIELD)) {   // operands is FIELDs
                            if ((left != null) && (right != null)) {
                                boolean isNumbers = true;

                                if (isLeftDouble = isObjectMayCastToDouble(left))
                                    leftValD = objectCastToDouble(left);
                                else if (isObjectMayCastToLong(left))
                                    leftValL = objectCastToLong(left);
                                else
                                    isNumbers = false;

                                if (isNumbers) {
                                    if (isRightDouble = isObjectMayCastToDouble(right))
                                        rightValD = objectCastToDouble(right);
                                    else if (isObjectMayCastToLong(right))
                                        rightValL = objectCastToLong(right);
                                    else
                                        isNumbers = false;
                                }

                                if (isNumbers) {
                                    if (((indxOperator == NOT_EQUAL) && ((isLeftDouble ? leftValD : leftValL) != (isRightDouble ? rightValD : rightValL))) ||
                                        ((indxOperator == EQUAL) && ((isLeftDouble ? leftValD : leftValL) == (isRightDouble ? rightValD : rightValL))))
                                        ret = true;
                                } else if (((indxOperator == NOT_EQUAL) && !left.equals(right)) ||
                                           ((indxOperator == EQUAL) && left.equals(right)))
                                    ret = true;
                            }

                        } else {
                            Object field;
                            String compareOperand;
                            compareOperandType typeCompareOperand;
                            if (typeOfLeftOperand == compareOperandType.FIELD) {
                                field = left;
                                compareOperand = rightOperand;
                                typeCompareOperand = typeOfRightOperand;
                            }
                            else if (typeOfRightOperand == compareOperandType.FIELD) {
                                field = right;
                                compareOperand = leftOperand;
                                typeCompareOperand = typeOfLeftOperand;
                            }
                            else
                                throw new IllegalArgumentException("At least one operand must be a field");

                            if (typeCompareOperand == compareOperandType.CONSTOTHER) {         // compareOperand is CONSTANT (null|number|true|false)
                                if (!compareOperand.equals("null") && !compareOperand.equals("false") && !compareOperand.equals("true")) {
                                    if (field != null)
                                    {
                                        if (isObjectMayCastToDouble(field)) {
                                            leftValD = objectCastToDouble(field);
                                            Double leftDouble = new Double(leftValD);

                                            if ((compareOperand.contains(".") &&
                                                (((indxOperator == NOT_EQUAL) && !leftDouble.equals(Double.parseDouble(compareOperand))) ||
                                                 ((indxOperator == EQUAL) && leftDouble.equals(Double.parseDouble(compareOperand))))) ||
                                                (!compareOperand.contains(".") &&
                                                 (((indxOperator == NOT_EQUAL) && (leftValD != Long.parseLong(compareOperand))) ||
                                                  ((indxOperator == EQUAL) && (leftValD == Long.parseLong(compareOperand))))))
                                                ret = true;
                                        } else {
                                            leftValL = objectCastToLong(field);
                                            Long leftLong = new Long(leftValL);

                                            if ((!compareOperand.contains(".") &&
                                                (((indxOperator == NOT_EQUAL) && !leftLong.equals(Long.parseLong(compareOperand))) ||
                                                 ((indxOperator == EQUAL) && leftLong.equals(Long.parseLong(compareOperand))))) ||
                                                (compareOperand.contains(".") &&
                                                 (((indxOperator == NOT_EQUAL) && (leftValL != Double.parseDouble(compareOperand))) ||
                                                  ((indxOperator == EQUAL) && (leftValL == Double.parseDouble(compareOperand))))))
                                                ret = true;
                                        }
                                    }
                                } else {          // if compareOperand : null|false|true
                                    if (((indxOperator == NOT_EQUAL) &&
                                        ((compareOperand.equals("null") && (field != null)) ||
                                         (compareOperand.equals("true") && ((field != null) && !(boolean) field)) ||
                                         (compareOperand.equals("false") && ((field != null) && (boolean) field))))
                                        || ((indxOperator == EQUAL) &&
                                        ((compareOperand.equals("null") && (field == null)) ||
                                         (compareOperand.equals("true") && ((field != null) && (boolean) field)) ||
                                         (compareOperand.equals("false") && ((field != null) && !(boolean) field)))))
                                        ret = true;
                                }
                            } else if (typeCompareOperand == compareOperandType.CONSTSTR) {          // compareOperand is CONSTANT (string)
                                 if ((field != null) &&
                                     (((indxOperator == NOT_EQUAL) && !field.equals(compareOperand)) ||
                                      ((indxOperator == EQUAL) && field.equals(compareOperand))))
                                    ret = true;
                            }
                            else
                                throw new IllegalArgumentException("Invalid type of operand: " + compareOperand);
                        }

                        break;

                    case MATCHES:

                        break;
                    case IS_INHERIT:
                        // deprecate warning
                        System.out.println("WARNING: Operator 'is_inherit' was deprecated. Use operator 'is_a'.");
                    case IS_A:
                        if ((left == null) || !left.getClass().getName().endsWith("Reference"))
                            throw new IllegalArgumentException("Expected reference in condition in left operand: " + leftOperand);

                        if ((right == null) || !right.getClass().getName().endsWith("Reference"))
                            throw new IllegalArgumentException("Expected reference in condition in right operand: " + rightOperand);

                        ret = ((Reference) left).isInherited((Reference) right, refContract, contracts, iteration + 1);

                        break;
                    case INHERIT:
                        // deprecate warning
                        System.out.println("WARNING: Operator 'inherit' was deprecated. Use operator 'inherits'.");
                    case INHERITS:
                        if ((right == null) || !right.getClass().getName().endsWith("Reference"))
                            throw new IllegalArgumentException("Expected reference in condition in right operand: " + rightOperand);

                        ret = ((Reference) right).isMatchingWith(refContract, contracts, iteration + 1);

                        break;
                    case CAN_PLAY:
                        if (right == null)
                            return false;

                        if (!(right.getClass().getName().endsWith("Role") || right.getClass().getName().endsWith("RoleLink")))
                            throw new IllegalArgumentException("Expected role in condition in right operand: " + rightOperand);

                        Set<PublicKey> keys;
                        keys = leftOperandContract.getReferenceContextKeys();


                        ret = ((Role) right).isAllowedForKeysQuantized(keys);

                        break;
                    case IN:
                        if (typeOfLeftOperand == compareOperandType.FIELD && left == null)
                            break;

                        if (typeOfRightOperand == compareOperandType.FIELD && right == null)
                            break;

                        if (!(right instanceof Set || right instanceof List))
                            break;

                        Set<Object> leftSet = new HashSet<>();
                        Set<Object> rightSet = new HashSet<>();

                        if (left == null)
                            leftSet.add(leftOperand);
                        else if (left instanceof Set || left instanceof List)
                            leftSet.addAll((Collection) left);
                        else
                            leftSet.add(left);

                        if (leftSet.isEmpty()) {
                            ret = true;
                            break;
                        }

                        rightSet.addAll((Collection) right);

                        if (leftSet.stream().anyMatch(item -> item instanceof HashId) ||
                            rightSet.stream().anyMatch(item -> item instanceof HashId)) {
                            Set<HashId> leftHashSet = new HashSet<>();
                            Set<HashId> rightHashSet = new HashSet<>();

                            for (Object item: leftSet) {
                                if (item instanceof HashId)
                                    leftHashSet.add((HashId) item);
                                else if (item instanceof String)
                                    leftHashSet.add(HashId.withDigest((String) item));
                                else
                                    throw new IllegalArgumentException(
                                        "Unexpected type (expect HashId or String) of collection item in left operand in condition: " + leftOperand);
                            }

                            for (Object item: rightSet) {
                                if (item instanceof HashId)
                                    rightHashSet.add((HashId) item);
                                else if (item instanceof String)
                                    rightHashSet.add(HashId.withDigest((String) item));
                                else
                                    throw new IllegalArgumentException(
                                        "Unexpected type (expect HashId or String) of collection item in right operand in condition: " + rightOperand);
                            }

                            ret = rightHashSet.containsAll(leftHashSet);

                        } else if (leftSet.stream().anyMatch(item -> item instanceof Role) ||
                                   rightSet.stream().anyMatch(item -> item instanceof Role)) {
                            Set<Role> leftRoleSet = new HashSet<>();
                            Set<Role> rightRoleSet = new HashSet<>();

                            for (Object item: leftSet) {
                                if (item instanceof Role || item instanceof String)
                                    leftRoleSet.add(prepareRoleToComparison(item));
                                else
                                    throw new IllegalArgumentException(
                                        "Unexpected type (expect Role or String) of collection item in left operand in condition: " + leftOperand);
                            }

                            for (Object item: rightSet) {
                                if (item instanceof Role || item instanceof String)
                                    rightRoleSet.add(prepareRoleToComparison(item));
                                else
                                    throw new IllegalArgumentException(
                                        "Unexpected type (expect Role or String) of collection item in right operand in condition: " + rightOperand);
                            }

                            ret = leftRoleSet.stream().allMatch(leftRole -> rightRoleSet.stream().anyMatch(leftRole::equalsIgnoreName));

                        } else if (leftSet.stream().allMatch(item -> item instanceof Reference) &&
                                   rightSet.stream().allMatch(item -> item instanceof Reference)) {
                            ret = leftSet.stream().allMatch(leftRef -> rightSet.stream().anyMatch(
                                    rightRef -> ((Reference) leftRef).equalsIgnoreType((Reference) rightRef)));

                        } else
                            ret = rightSet.containsAll(leftSet);

                        break;
                    default:
                        throw new IllegalArgumentException("Invalid operator in condition");
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Error compare operands in condition: " + e.getMessage());
            }
        } else {       // if rightOperand == null && rightExpression == null, then operation: defined / undefined
            if (indxOperator == DEFINED) {
                try {
                    if (leftOperandContract.get(leftOperand) != null)
                        ret = true;
                } catch (Exception e) {}
            } else if (indxOperator == UNDEFINED) {
                try {
                    ret = (leftOperandContract.get(leftOperand) == null);
                }
                catch (Exception e) {
                    ret = true;
                }
            }
            else
                throw new IllegalArgumentException("Invalid operator in condition");
        }

        return ret;
    }

    private Binder packOperands(String leftOperand,
                                String rightOperand,
                                Binder left,
                                Binder right,
                                compareOperandType typeOfLeftOperand,
                                compareOperandType typeOfRightOperand,
                                int leftConversion,
                                int rightConversion,
                                boolean leftParentheses,
                                boolean rightParentheses) {
        Binder packed = new Binder();

        if (left != null)
            packed.set("left", left);
        else
            packed.set("leftOperand", leftOperand);

        if (right != null)
            packed.set("right", right);
        else
            packed.set("rightOperand", rightOperand);

        packed.set("typeOfLeftOperand", typeOfLeftOperand.ordinal());
        packed.set("typeOfRightOperand", typeOfRightOperand.ordinal());

        packed.set("leftConversion", leftConversion);
        packed.set("rightConversion", rightConversion);

        if (leftParentheses)
            packed.set("leftParentheses", true);
        if (rightParentheses)
            packed.set("rightParentheses", true);

        return packed;
    }

    private Binder packExpression(int operation,
                                  String leftOperand,
                                  String rightOperand,
                                  Binder left,
                                  Binder right,
                                  compareOperandType typeOfLeftOperand,
                                  compareOperandType typeOfRightOperand,
                                  int leftConversion,
                                  int rightConversion,
                                  boolean leftParentheses,
                                  boolean rightParentheses) {
        Binder packedExpression = packOperands(leftOperand, rightOperand, left, right, typeOfLeftOperand,
                typeOfRightOperand, leftConversion, rightConversion, leftParentheses, rightParentheses);

        packedExpression.set("operation", operation);

        return packedExpression;
    }

    private Binder packCondition(int operator,
                                 String leftOperand,
                                 String rightOperand,
                                 Binder left,
                                 Binder right,
                                 compareOperandType typeOfLeftOperand,
                                 compareOperandType typeOfRightOperand,
                                 int leftConversion,
                                 int rightConversion) {
        Binder packedCondition = packOperands(leftOperand, rightOperand, left, right, typeOfLeftOperand,
                typeOfRightOperand, leftConversion, rightConversion, false, false);

        packedCondition.set("operator", operator);

        return packedCondition;
    }

    private boolean isFieldOperand(String operand) {
        int firstPointPos;
        return ((firstPointPos = operand.indexOf(".")) > 0) &&
                (operand.length() > firstPointPos + 1) &&
                ((operand.charAt(firstPointPos + 1) < '0') ||
                 (operand.charAt(firstPointPos + 1) > '9'));
    }

    private boolean isExpression(String operand) {
        if (baseContract == null)
            System.out.println("WARNING: Need base contract to check API level. Capabilities API level 4 and above disabled.");

        return baseContract != null && baseContract.getApiLevel() >= 4 && (Arrays.stream(operations).anyMatch(op ->
                operand.contains(op) && (!op.equals(operations[MINUS]) || operand.lastIndexOf(op) > 0)) ||
                Arrays.stream(roundOperations).anyMatch(operand::startsWith));
    }

    private int countCommonParentheses(String expression) {
        int commonLevel = 0;
        while (expression.charAt(commonLevel) == '(')
            commonLevel++;

        if (commonLevel == 0)
            return 0;

        int pos = commonLevel;
        int level = commonLevel;
        while (pos < expression.length() - commonLevel) {
            if (expression.charAt(pos) == '(')
                level++;

            if (expression.charAt(pos) == ')') {
                level--;
                if (level == 0)
                    return 0;

                if (level < commonLevel)
                    commonLevel = level;
            }

            pos++;
        }

        if (commonLevel > 0) {
            if (commonLevel != level)
                throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Expected ')'.");

            while (pos < expression.length()) {
                if (expression.charAt(pos) != ')')
                    throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Expected ')'.");
                pos++;
            }
        }

        return commonLevel;
    }

    private boolean isTopLevelOperation(String expression, int opPos) {
        int pos = 0;
        int level = 0;
        while (pos < expression.length()) {
            if (pos == opPos)
                return level == 0;

            if (expression.charAt(pos) == '(')
                level++;

            if (expression.charAt(pos) == ')') {
                level--;
                if (level < 0)
                    throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Not expected ')'.");
            }

            pos++;
        }

        throw new IllegalArgumentException("Internal parsing error in expression: " + expression + ". opPos not reached.");
    }

    private Binder parseExpression(String expression, boolean topLevel) {
        if (topLevel) {
            // remove top-level parentheses
            int countParentheses = countCommonParentheses(expression);
            if (countParentheses > 0)
                expression = expression.substring(countParentheses, expression.length() - countParentheses);
        }

        int opPos = -1;
        int i = -1;
        int opLen = 1;
        do {
            i++;
            while ((opPos = expression.indexOf(operations[i], opPos + 1)) > 0 && !isTopLevelOperation(expression, opPos));
        } while (opPos <= 0 && i < DIV);

        if (opPos <= 0) {
            // parse round operations
            for (i = ROUND; i <= CEIL; i++)
                if (expression.startsWith(roundOperations[i - ROUND_OPERATIONS])) {
                    if (!expression.endsWith(")"))
                        throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Not expected ')' after rounding operation.");

                    expression = expression.substring(roundOperations[i - ROUND_OPERATIONS].length(), expression.length() - 1);

                    while ((opPos = expression.indexOf(",", opPos + 1)) > 0 && !isTopLevelOperation(expression, opPos));
                    if (opPos <= 0)
                        throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Not expected ',' after rounding operation.");

                    break;
                }

            if (i > CEIL)
                throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Not found top-level operation.");
        }
        else
            opLen = operations[i].length();

        String leftOperand = expression.substring(0, opPos);
        if (leftOperand.length() == 0)
            throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Missing left operand.");

        compareOperandType typeLeftOperand = compareOperandType.CONSTOTHER;
        Binder left = null;
        boolean leftParentheses = false;

        int countParentheses = countCommonParentheses(leftOperand);
        if (countParentheses > 0) {
            leftOperand = leftOperand.substring(countParentheses, leftOperand.length() - countParentheses);
            leftParentheses = true;
        }

        if (isExpression(leftOperand)) {
            left = parseExpression(leftOperand, false);
            typeLeftOperand = compareOperandType.EXPRESSION;
        } else {
            if (isFieldOperand(leftOperand))
                typeLeftOperand = compareOperandType.FIELD;
        }

        String rightOperand = expression.substring(opPos + opLen);
        if (rightOperand.length() == 0)
            throw new IllegalArgumentException("Invalid format of expression: " + expression + ". Missing right operand.");

        compareOperandType typeRightOperand = compareOperandType.CONSTOTHER;
        Binder right = null;
        boolean rightParentheses = false;

        countParentheses = countCommonParentheses(rightOperand);
        if (countParentheses > 0) {
            rightOperand = rightOperand.substring(countParentheses, rightOperand.length() - countParentheses);
            rightParentheses = true;
        }

        if (isExpression(rightOperand)) {
            right = parseExpression(rightOperand, false);
            typeRightOperand = compareOperandType.EXPRESSION;
        } else if (isFieldOperand(rightOperand))
            typeRightOperand = compareOperandType.FIELD;

        int leftConversion = NO_CONVERSION;
        int rightConversion = NO_CONVERSION;

        if ((typeLeftOperand == compareOperandType.FIELD) && (leftOperand.endsWith("::number"))) {
            leftConversion = CONVERSION_BIG_DECIMAL;
            leftOperand = leftOperand.substring(0, leftOperand.length() - 8);
        }

        if ((typeRightOperand == compareOperandType.FIELD) && (rightOperand.endsWith("::number"))) {
            rightConversion = CONVERSION_BIG_DECIMAL;
            rightOperand = rightOperand.substring(0, rightOperand.length() - 8);
        }

        return packExpression(i, leftOperand, rightOperand, left, right, typeLeftOperand, typeRightOperand,
                leftConversion, rightConversion, leftParentheses, rightParentheses);
    }

    private Binder parseCondition(String condition) {

        int leftConversion = NO_CONVERSION;
        int rightConversion = NO_CONVERSION;

        for (int i = 0; i < 2; i++) {
            int operPos = condition.lastIndexOf(operators[i]);

            if ((operPos >= 0) && (condition.length() - operators[i].length() == operPos)) {

                String leftOperand = condition.substring(0, operPos).replaceAll("\\s+", "");

                if (leftOperand.endsWith("::number")) {
                    leftConversion = CONVERSION_BIG_DECIMAL;
                    leftOperand = leftOperand.substring(0, leftOperand.length() - 8);
                }

                return packCondition(i, leftOperand, null, null, null, compareOperandType.FIELD, compareOperandType.CONSTOTHER, leftConversion, rightConversion);
            }
        }

        for (int i = 2; i <= IN; i++) {
            if (i >= INHERITS && i <= CAN_PLAY)     // skipping operators with a different syntax
                continue;

            int operPos = condition.indexOf(operators[i]);
            int firstMarkPos = condition.indexOf("\"");
            int lastMarkPos = condition.lastIndexOf("\"");

            // Normal situation - operator without quotes
            while ((firstMarkPos >= 0) && (operPos > firstMarkPos) && (operPos < lastMarkPos))
                operPos = condition.indexOf(operators[i], operPos + 1);

            // Operator not found
            if (operPos < 0)
                continue;

            // Parsing left operand
            String subStrL = condition.substring(0, operPos);
            if (subStrL.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing left operand.");

            int lmarkPos1 = subStrL.indexOf("\"");
            int lmarkPos2 = subStrL.lastIndexOf("\"");

            if ((lmarkPos1 >= 0) && (lmarkPos1 == lmarkPos2))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Only one quote is found for left operand.");

            String leftOperand;
            Binder left = null;
            compareOperandType typeLeftOperand = compareOperandType.CONSTOTHER;

            if ((lmarkPos1 >= 0) && (lmarkPos1 != lmarkPos2)) {
                leftOperand = subStrL.substring(lmarkPos1 + 1, lmarkPos2);
                typeLeftOperand = compareOperandType.CONSTSTR;
            }
            else {
                leftOperand = subStrL.replaceAll("\\s+", "");

                if (isExpression(leftOperand)) {
                    if (i > EQUAL)
                        throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Operator incompatible with expression in left operand.");

                    left = parseExpression(leftOperand, true);
                    typeLeftOperand = compareOperandType.EXPRESSION;
                } else if (isFieldOperand(leftOperand))
                    typeLeftOperand = compareOperandType.FIELD;
            }

            // Parsing right operand
            String subStrR = condition.substring(operPos + operators[i].length());
            if (subStrR.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

            int rmarkPos1 = subStrR.indexOf("\"");
            int rmarkPos2 = subStrR.lastIndexOf("\"");

            if ((rmarkPos1 >= 0) && (rmarkPos1 == rmarkPos2))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Only one quote is found for right operand.");

            String rightOperand;
            Binder right = null;
            compareOperandType typeRightOperand = compareOperandType.CONSTOTHER;

            if ((rmarkPos1 >= 0) && (rmarkPos1 != rmarkPos2)) {
                rightOperand = subStrR.substring(rmarkPos1 + 1, rmarkPos2);
                typeRightOperand = compareOperandType.CONSTSTR;
            }
            else {
                rightOperand = subStrR.replaceAll("\\s+", "");

                if (isExpression(rightOperand)) {
                    if (i > EQUAL)
                        throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Operator incompatible with expression in right operand.");

                    right = parseExpression(rightOperand, true);
                    typeRightOperand = compareOperandType.EXPRESSION;
                } else if (isFieldOperand(rightOperand))
                    typeRightOperand = compareOperandType.FIELD;
            }

            //if ((typeLeftOperand != compareOperandType.FIELD) && (typeRightOperand != compareOperandType.FIELD))
            //    throw new IllegalArgumentException("At least one operand must be a field in condition: " + condition);

            if ((typeLeftOperand == compareOperandType.FIELD) && (leftOperand.endsWith("::number"))) {
                leftConversion = CONVERSION_BIG_DECIMAL;
                leftOperand = leftOperand.substring(0, leftOperand.length() - 8);
            }

            if ((typeRightOperand == compareOperandType.FIELD) && (rightOperand.endsWith("::number"))) {
                rightConversion = CONVERSION_BIG_DECIMAL;
                rightOperand = rightOperand.substring(0, rightOperand.length() - 8);
            }

            return packCondition(i, leftOperand, rightOperand, left, right, typeLeftOperand, typeRightOperand, leftConversion, rightConversion);
        }

        for (int i = INHERITS; i <= INHERIT; i++) {
            int operPos = condition.indexOf(operators[i]);

            if ((operPos == 0) || ((operPos > 0) && (condition.charAt(operPos - 1) != '_'))) {
                String subStrR = condition.substring(operPos + operators[i].length());
                if (subStrR.length() == 0)
                    throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

                String rightOperand = subStrR.replaceAll("\\s+", "");

                if (rightOperand.endsWith("::number")) {
                    rightConversion = CONVERSION_BIG_DECIMAL;
                    rightOperand = rightOperand.substring(0, rightOperand.length() - 8);
                }

                return packCondition(i, null, rightOperand, null, null, compareOperandType.FIELD, compareOperandType.FIELD, leftConversion, rightConversion);
            }
        }

        int operPos = condition.indexOf(operators[CAN_PLAY]);
        if (operPos > 0) {
            // Parsing left operand
            String subStrL = condition.substring(0, operPos);
            if (subStrL.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing left operand.");

            String leftOperand = subStrL.replaceAll("\\s+", "");
            if (leftOperand.contains("."))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Left operand must be a reference to a contract.");

            String subStrR = condition.substring(operPos + operators[CAN_PLAY].length());
            if (subStrR.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

            // Parsing right operand
            String rightOperand = subStrR.replaceAll("\\s+", "");
            if (!isFieldOperand(rightOperand))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Right operand must be a role field.");

            return packCondition(CAN_PLAY, leftOperand, rightOperand, null, null, compareOperandType.CONSTOTHER, compareOperandType.FIELD, leftConversion, rightConversion);
        }

        throw new IllegalArgumentException("Invalid format of condition: " + condition);
    }

    /**
     * Check condition of reference
     * @param condition condition to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean checkCondition(Binder condition, Contract ref, Collection<Contract> contracts, int iteration) {

        compareOperandType typeOfLeftOperand;
        compareOperandType typeOfRightOperand;

        String leftOperand = condition.getString("leftOperand", null);
        String rightOperand = condition.getString("rightOperand", null);

        Binder left = condition.getBinder("left", null);
        Binder right = condition.getBinder("right", null);

        int operator = condition.getIntOrThrow("operator");

        int typeLeftOperand = condition.getIntOrThrow("typeOfLeftOperand");
        int typeRightOperand = condition.getIntOrThrow("typeOfRightOperand");

        typeOfLeftOperand = compareOperandType.values()[typeLeftOperand];
        typeOfRightOperand = compareOperandType.values()[typeRightOperand];

        int leftConversion = condition.getInt("leftConversion", NO_CONVERSION);
        int rightConversion = condition.getInt("rightConversion", NO_CONVERSION);

        boolean isBigDecimalConversion = (leftConversion == CONVERSION_BIG_DECIMAL) || (rightConversion == CONVERSION_BIG_DECIMAL);

        return compareOperands(ref, leftOperand, rightOperand, left, right, typeOfLeftOperand, typeOfRightOperand, isBigDecimalConversion, operator, contracts, iteration);
    }

    /**
     * Check not pre-parsed condition of reference (old version)
     * @param condition condition to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean checkCondition(String condition, Contract ref, Collection<Contract> contracts, int iteration) {

        Binder parsed = parseCondition(condition);
        return checkCondition(parsed, ref, contracts, iteration);
    }

    /**
     * Pre-parsing conditions of reference
     * @param conds is binder of not-parsed (string) conditions
     * @return result {@link Binder} with parsed conditions
     */
    private Binder parseConditions(Binder conds) {

        if ((conds == null) || (conds.size() == 0))
            return null;

        if (conds.containsKey("operator"))
            return conds;

        boolean all = conds.containsKey(all_of.name());
        boolean any = conds.containsKey(any_of.name());

        if (all || any) {
            Binder result = new Binder();
            String keyName = all ? all_of.name() : any_of.name();
            List<Object> parsedList = new ArrayList<>();
            List<Object> condList = conds.getList(keyName, null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of or any_of conditions");

            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    parsedList.add(parseCondition((String) item));
                else if (item.getClass().getName().endsWith("LinkedHashMap")) {
                    LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    Binder insideBinder = new Binder(insideHashMap);
                    Binder parsed = parseConditions(insideBinder);
                    if (parsed != null)
                        parsedList.add(parsed);
                } else {
                    Binder parsed = parseConditions((Binder) item);
                    if (parsed != null)
                        parsedList.add(parsed);
                }
            }

            result.put(keyName, parsedList);
            return result;
        }
        else
            throw new IllegalArgumentException("Expected all_of or any_of");
    }

    /**
     * Check conditions of references
     * @param conditions binder with conditions to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean checkConditions(Binder conditions, Contract ref, Collection<Contract> contracts, int iteration) {

        boolean result;

        if ((conditions == null) || (conditions.size() == 0))
            return true;

        if (conditions.containsKey(all_of.name()))
        {
            List<Object> condList = conditions.getList(all_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of conditions");

            result = true;
            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    result = result && checkCondition((String) item, ref, contracts, iteration);        // not pre-parsed (old) version
                else
                    //LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    //Binder insideBinder = new Binder(insideHashMap);
                    result = result && checkConditions((Binder) item, ref, contracts, iteration);
            }
        }
        else if (conditions.containsKey(any_of.name()))
        {
            List<Object> condList = conditions.getList(any_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected any_of conditions");

            result = false;
            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    result = result || checkCondition((String) item, ref, contracts, iteration);        // not pre-parsed (old) version
                else
                    //LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    //Binder insideBinder = new Binder(insideHashMap);
                    result = result || checkConditions((Binder) item, ref, contracts, iteration);
            }
        }
        else if (conditions.containsKey("operator"))                                                    // pre-parsed version
            result = checkCondition(conditions, ref, contracts, iteration);
        else
            throw new IllegalArgumentException("Expected all_of or any_of");

        return result;
    }

    /**
     * Check if reference is valid i.e. have matching with criteria items
     * @return true or false
     */
    public boolean isValid() {
        return matchingItems.size() > 0;
    }

    /**
     * Check if given item matching with current reference criteria
     * @param a item to check for matching
     * @param contracts contract list to check for matching
     * @return true if match or false
     */
    public boolean isMatchingWith(Approvable a, Collection<Contract> contracts) {
        return isMatchingWith(a, contracts, 0);
    }

    /**
     * Check if given item matching with current reference criteria
     * @param a item to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    private boolean isMatchingWith(Approvable a, Collection<Contract> contracts, int iteration) {
        //todo: add this checking for matching with given item

        if (iteration > 16)
            throw new IllegalArgumentException("Recursive checking references have more 16 iterations");

        boolean result = true;

        if(a instanceof Contract) {
            //check roles
            Contract contract = (Contract) a;
            if (result) {
                Map<String, Role> contractRoles = contract.getRoles();
                result = roles.isEmpty() || roles.stream().anyMatch(role -> contractRoles.containsKey(role));
            }

            //check origin
            if (result) {
                result = (origin == null || !(contract.getOrigin().equals(origin)));
            }

            //check fields
            if (result) {
                Binder stateData = contract.getStateData();
                result = fields.isEmpty() || fields.stream().anyMatch(field -> stateData.get(field) != null);
            }

            //check conditions
            if (result) {
                result = checkConditions(conditions, contract, contracts, iteration);
            }
        }

        return result;
    }

    private boolean isInherited(Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        return isInherited(conditions, ref, refContract, contracts, iteration);
    }

    private boolean isInherited(Binder conditions, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        if ((conditions == null) || (conditions.size() == 0))
            return false;

        List<Object> condList = null;

        if (conditions.containsKey(all_of.name()))
        {
            condList = conditions.getList(all_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of conditions");
        }
        else if (conditions.containsKey(any_of.name()))
        {
            condList = conditions.getList(any_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected any_of conditions");
        }
        else if (conditions.containsKey("operator"))
            return isInheritedParsed(conditions, ref, refContract, contracts, iteration);
        else
            throw new IllegalArgumentException("Expected all_of or any_of");

        if (condList != null)
            for (Object item: condList)
                if (item.getClass().getName().endsWith("String")) {
                    if (isInherited((String) item, ref, refContract, contracts, iteration))
                        return true;
                } else if (isInherited((Binder) item, ref, refContract, contracts, iteration))
                        return true;

        return false;
    }

    private boolean isInheritedParsed(Binder condition, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {

        int operator = condition.getIntOrThrow("operator");
        String rightOperand = condition.getString("rightOperand", null);

        if (((operator == INHERITS) || (operator == INHERIT)) && (rightOperand != null))
            return isInheritedOperand(rightOperand, ref, refContract, contracts, iteration);

        return false;
    }

    private boolean isInherited(String condition, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        for (int i = INHERITS; i <= INHERIT; i++) {
            int operPos = condition.indexOf(operators[i]);

            if ((operPos == 0) || ((operPos > 0) && (condition.charAt(operPos - 1) != '_'))) {
                String subStrR = condition.substring(operPos + operators[i].length());
                if (subStrR.length() == 0)
                    throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

                String rightOperand = subStrR.replaceAll("\\s+", "");

                return isInheritedOperand(rightOperand, ref, refContract, contracts, iteration);
            }
        }

        return false;
    }

    private boolean isInheritedOperand(String rightOperand, Reference ref, Contract refContract, Collection<Contract> contracts, int iteration) {
        Contract rightOperandContract = null;
        Object right = null;
        int firstPointPos;

        if (rightOperand.startsWith("ref.")) {
            rightOperand = rightOperand.substring(4);
            rightOperandContract = refContract;
        } else if (rightOperand.startsWith("this.")) {
            if (baseContract == null)
                throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

            rightOperand = rightOperand.substring(5);
            rightOperandContract = baseContract;
        } else if ((firstPointPos = rightOperand.indexOf(".")) > 0) {
            if (baseContract == null)
                throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

            Reference refLink = baseContract.findReferenceByName(rightOperand.substring(0, firstPointPos));
            if (refLink == null)
                throw new IllegalArgumentException("Not found reference: " + rightOperand.substring(0, firstPointPos));

            for (Contract checkedContract : contracts)
                if (refLink.isMatchingWith(checkedContract, contracts, iteration + 1))
                    rightOperandContract = checkedContract;

            if (rightOperandContract == null)
                return false;

            rightOperand = rightOperand.substring(firstPointPos + 1);
        } else
            throw new IllegalArgumentException("Invalid format of right operand in condition: " + rightOperand + ". Missing contract field.");

        if (rightOperandContract != null)
            right = rightOperandContract.get(rightOperand);

        if ((right == null) || !right.getClass().getName().endsWith("Reference"))
            throw new IllegalArgumentException("Expected reference in condition in right operand: " + rightOperand);

        if (((Reference) right).equals(ref))
            return true;

        return false;
    }

    /**
     * Get the name from the reference
     * @return name reference
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name for the reference
     * @return this reference
     */
    public Reference setName(String name) {
        if (name != null)
            this.name = name;
        else
            this.name = "";
        return this;
    }

    /**
     * Get comment of the reference
     *
     * @return comment of the reference (may be null)
     */
    public String getComment() {
        return comment;
    }

    /**
     * Set comment of the reference
     *
     * @param comment of the reference
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Get the list of roles from the reference
     * @return roles from reference
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Add the roles for the reference
     * @return this reference
     */
    public Reference addRole(String role) {
        this.roles.add(role);
        return this;
    }

    /**
     * Set the list of roles for the reference
     * @return this reference
     */
    public Reference setRoles(List<String> roles) {
        this.roles = roles;
        return this;
    }

    /**
     * Get the list of fields from the reference
     * @return list of fields reference
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * Add the field for the reference
     * @return this reference
     */
    public Reference addField(String field) {
        this.fields.add(field);
        return this;
    }

    /**
     * Set the list of fields for the reference
     * @return this reference
     */
    public Reference setFields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    /**
     * Assembly expression of reference condition
     * @param expression is binder of parsed expression
     * @return result {@link String} with assembled expression
     */
    private String assemblyExpression(Binder expression) {
        String result = "";

        // get parsed data
        String leftOperand = expression.getString("leftOperand", null);
        String rightOperand = expression.getString("rightOperand", null);

        Binder left = expression.getBinder("left", null);
        Binder right = expression.getBinder("right", null);

        int operation = expression.getIntOrThrow("operation");

        int leftConversion = expression.getInt("leftConversion", NO_CONVERSION);
        int rightConversion = expression.getInt("rightConversion", NO_CONVERSION);

        boolean leftParentheses = expression.containsKey("leftParentheses");
        boolean rightParentheses = expression.containsKey("rightParentheses");

        // assembly expression
        if (leftParentheses)
            result += "(";

        if (operation >= ROUND_OPERATIONS)
            result += roundOperations[operation - ROUND_OPERATIONS];

        if (leftOperand != null) {
            result += leftOperand;

            if (leftConversion == CONVERSION_BIG_DECIMAL)
                result += "::number";

        } else if (left != null)
            result += assemblyExpression(left);

        if (leftParentheses)
            result += ")";

        if (operation >= ROUND_OPERATIONS)
            result += ",";
        else
            result += operations[operation];

        if (rightParentheses)
            result += "(";

        if (rightOperand != null) {
            result += rightOperand;

            if (rightConversion == CONVERSION_BIG_DECIMAL)
                result += "::number";

        } else if (right != null)
            result += assemblyExpression(right);

        if (operation >= ROUND_OPERATIONS)
            result += ")";

        if (rightParentheses)
            result += ")";

        return result;
    }

    /**
     * Assembly condition of reference
     * @param condition is binder of parsed condition
     * @return result {@link String} with assembled condition
     */
    private String assemblyCondition(Binder condition) {

        if ((condition == null) || (condition.size() == 0))
            return null;

        String result = "";

        // get parsed data
        String leftOperand = condition.getString("leftOperand", null);
        String rightOperand = condition.getString("rightOperand", null);

        Binder left = condition.getBinder("left", null);
        Binder right = condition.getBinder("right", null);

        int operator = condition.getIntOrThrow("operator");

        int leftConversion = condition.getInt("leftConversion", NO_CONVERSION);
        int rightConversion = condition.getInt("rightConversion", NO_CONVERSION);

        int typeLeftOperand = condition.getIntOrThrow("typeOfLeftOperand");
        int typeRightOperand = condition.getIntOrThrow("typeOfRightOperand");

        // assembly condition
        if (leftOperand != null) {
            if (typeLeftOperand == 1)      // CONSTSTR
                result += "\"";

            result += leftOperand;

            if (typeLeftOperand == 1)      // CONSTSTR
                result += "\"";

            if (leftConversion == CONVERSION_BIG_DECIMAL)
                result += "::number";

        } else if (left != null)
            result += assemblyExpression(left);

        result += operators[operator];

        if (rightOperand != null) {
            if (typeRightOperand == 1)      // CONSTSTR
                result += "\"";

            result += rightOperand;

            if (typeRightOperand == 1)      // CONSTSTR
                result += "\"";

            if (rightConversion == CONVERSION_BIG_DECIMAL)
                result += "::number";

        } else if (right != null)
            result += assemblyExpression(right);

        return result;
    }

    /**
     * Assembly conditions of reference
     * @param conds is binder of parsed conditions
     * @return result {@link Binder} with assembled (string) conditions
     */
    private Binder assemblyConditions(Binder conds) {

        if ((conds == null) || (conds.size() == 0))
            return null;

        boolean all = conds.containsKey(all_of.name());
        boolean any = conds.containsKey(any_of.name());

        if (all || any) {
            Binder result = new Binder();
            String keyName = all ? all_of.name() : any_of.name();
            List<Object> assembledList = new ArrayList<>();
            List<Object> condList = conds.getList(keyName, null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of or any_of conditions");

            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))       // already assembled condition
                    assembledList.add(item);
                else {
                    Binder parsed = null;
                    String cond = null;
                    if (item.getClass().getName().endsWith("LinkedHashMap")) {
                        LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                        Binder insideBinder = new Binder(insideHashMap);
                        parsed = assemblyConditions(insideBinder);
                    } else if (((Binder) item).containsKey("operator"))
                        cond = assemblyCondition((Binder) item);
                    else
                        parsed = assemblyConditions((Binder) item);

                    if (parsed != null)
                        assembledList.add(parsed);

                    if (cond != null)
                        assembledList.add(cond);
                }
            }

            result.put(keyName, assembledList);
            return result;
        }
        else
            throw new IllegalArgumentException("Expected all_of or any_of");
    }

    /**
     * Get the conditions from the reference
     * @return conditions reference
     */
    public Binder getConditions() {
        return conditions;
    }

    /**
     * Export the conditions from the reference as strings
     * @return strings conditions reference
     */
    public Binder exportConditions() {
        return assemblyConditions(conditions);
    }

    /**
     * Set the conditions from the reference
     * @return this reference
     */
    public Reference setConditions(Binder conditions) {
        this.conditions = parseConditions(conditions);
        return this;
    }

    //TODO: The method allows to mark the contract as matching reference, bypassing the validation
    /**
     * Add the matching item for the reference
     * @return this reference
     */
    public Reference addMatchingItem(Approvable a) {
        this.matchingItems.add(a);
        return this;
    }

    /**
     * Get the base contract in which the reference is located
     * @return base contract
     */
    public Contract getContract() {
        return baseContract;
    }

    /**
     * Set the base contract from the reference
     * @return this reference
     */
    public Reference setContract(Contract contract) {
        baseContract = contract;
        return this;
    }

    public Set<String> getInternalReferences() {
        return getInternalReferences(0);
    }

    private Set<String> getInternalReferences(int iteration) {
        if (iteration > 16)
            throw new IllegalArgumentException("Recursive checking references have more 16 iterations");

        return getInternalReferencesFromConditions(conditions, iteration);
    }

    private Set<String> getInternalReferencesFromConditions(Binder conditions, int iteration) {
        Set<String> refs = new HashSet<>();

        if ((conditions == null) || (conditions.size() == 0))
            return refs;

        if (conditions.containsKey(all_of.name())) {
            List<Object> condList = conditions.getList(all_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of conditions");

            for (Object item: condList)
                if (item.getClass().getName().endsWith("String"))
                    refs.addAll(getInternalReferencesFromCondition((String) item, iteration));        // not pre-parsed (old) version
                else
                    refs.addAll(getInternalReferencesFromConditions((Binder) item, iteration));

        } else if (conditions.containsKey(any_of.name())) {
            List<Object> condList = conditions.getList(any_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected any_of conditions");

            for (Object item: condList)
                if (item.getClass().getName().endsWith("String"))
                    refs.addAll(getInternalReferencesFromCondition((String) item, iteration));        // not pre-parsed (old) version
                else
                    refs.addAll(getInternalReferencesFromConditions((Binder) item, iteration));

        } else if (conditions.containsKey("operator"))                                             // pre-parsed version
            refs.addAll(getInternalReferencesFromCondition(conditions, iteration));
        else
            throw new IllegalArgumentException("Expected all_of or any_of");

        return refs;
    }

    private Set<String> getInternalReferencesFromCondition(String condition, int iteration) {
        return getInternalReferencesFromCondition(parseCondition(condition), iteration);
    }

    private Set<String> getInternalReferencesFromCondition(Binder condition, int iteration) {
        Set<String> refs = new HashSet<>();

        String leftOperand = condition.getString("leftOperand", null);
        String rightOperand = condition.getString("rightOperand", null);

        int typeLeftOperand = condition.getIntOrThrow("typeOfLeftOperand");
        int typeRightOperand = condition.getIntOrThrow("typeOfRightOperand");

        compareOperandType typeOfLeftOperand = compareOperandType.values()[typeLeftOperand];
        compareOperandType typeOfRightOperand = compareOperandType.values()[typeRightOperand];

        int firstPointPos;
        if (leftOperand != null && typeOfLeftOperand == compareOperandType.FIELD && !leftOperand.startsWith("ref.") &&
            !leftOperand.startsWith("this.") && ((firstPointPos = leftOperand.indexOf(".")) > 0)) {

            if (baseContract == null)
                throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

            String refName = leftOperand.substring(0, firstPointPos);
            refs.add(refName);

            Reference ref = baseContract.findReferenceByName(refName);
            if (ref != null)
                refs.addAll(ref.getInternalReferences(iteration + 1));
        }

        if (rightOperand != null && typeOfRightOperand == compareOperandType.FIELD && !rightOperand.startsWith("ref.") &&
            !rightOperand.startsWith("this.") && ((firstPointPos = rightOperand.indexOf(".")) > 0)) {

            if (baseContract == null)
                throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

            String refName = rightOperand.substring(0, firstPointPos);
            refs.add(refName);

            Reference ref = baseContract.findReferenceByName(refName);
            if (ref != null)
                refs.addAll(ref.getInternalReferences(iteration + 1));
        }

        return refs;
    }

    @Override
    public String toString() {
        String res = "{";
        res += "name:"+name;
        res += ", type:"+type;
        if (comment != null)
            res += ", comment:"+comment;
        if (transactional_id.length() > 8)
            res += ", transactional_id:"+transactional_id.substring(0, 8)+"...";
        else
            res += ", transactional_id:"+transactional_id;
        res += ", contract_id:"+contract_id;
        res += ", required:"+required;
        res += ", origin:"+origin;
        res += ", signed_by:[";
        for (int i = 0; i < signed_by.size(); ++i) {
            if (i > 0)
                res += ", ";
            Role r = signed_by.get(i);
            res += r.toString();
        }
        res += "]";
        res += "}";
        return res;
    }

    static {
        Config.forceInit(Reference.class);
        DefaultBiMapper.registerClass(Reference.class);
    }
};
