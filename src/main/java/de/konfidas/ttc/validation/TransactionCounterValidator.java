package de.konfidas.ttc.validation;

import de.konfidas.ttc.exceptions.LogMessageValidationException;
import de.konfidas.ttc.exceptions.ValidationException;
import de.konfidas.ttc.messages.LogMessage;
import de.konfidas.ttc.messages.TransactionLog;
import de.konfidas.ttc.tars.LogMessageArchive;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;

public class TransactionCounterValidator implements Validator {
    final HashMap<String, Transaction> openTransactions; // TODO: allow multiple TSE SERIAL
    final HashMap<String,BigInteger> expectedTransactionCounters;

    public TransactionCounterValidator(){
        openTransactions = new HashMap<>();
        expectedTransactionCounters = new HashMap<>();
    }

    public ValidationResult validate(LogMessageArchive tar){
        ArrayList<ValidationException> result = new ArrayList<>();

        for(LogMessage msg : tar.getSortedLogMessages()){
            if(msg instanceof TransactionLog){
                result.addAll(updateState((TransactionLog) msg));
            }
        }

        return new ValidationResultImpl().append(Collections.singleton(this), result);
    }

    Collection<ValidationException> updateState(TransactionLog msg) {
        LinkedList <ValidationException> result = new LinkedList<>();

        final String hexSerial =  Hex.toHexString(msg.getSerialNumber());
        BigInteger expectedTransactionCounter = BigInteger.ONE;
        if(expectedTransactionCounters.containsKey(hexSerial)){
            expectedTransactionCounter = expectedTransactionCounters.get(hexSerial);
        }else{
            expectedTransactionCounters.put(hexSerial,expectedTransactionCounter);
        }

        final String key= hexSerial+"_"+msg.getTransactionNumber();
        BigInteger transactionNumber = msg.getTransactionNumber();
        if("StartTransaction".equals(msg.getOperationType())){
            final int comparison = expectedTransactionCounter.compareTo(transactionNumber);
            if(comparison<0){
                result.add(new MissingTransactionCounterException(expectedTransactionCounter, msg));
                expectedTransactionCounter = transactionNumber.add(BigInteger.ONE);
            }else{
                if(comparison== 0) {
                    expectedTransactionCounter = expectedTransactionCounter.add(BigInteger.ONE);
                }
            }
            expectedTransactionCounters.replace(hexSerial,expectedTransactionCounter);

            if(openTransactions.containsKey(key)){
                Transaction duplicate = openTransactions.get(key);
                result.add(new DuplicateTransactionCounterFoundException(msg.getTransactionNumber(), msg));
                duplicate.signatureCounterLastUpdate = msg.getSignatureCounter();
            }else{
                if(comparison>0) {
                    result.add(new DelayedTransActionCounterException(expectedTransactionCounter,msg));
                }
                openTransactions.put(key, new Transaction(msg));
            }
        }

        if("UpdateTransaction".equals(msg.getOperationType())){
            if(!openTransactions.containsKey(key)){
                result.add(new UpdateForNotOpenTransactionException(expectedTransactionCounter, msg));
            }else{
                if(!openTransactions.get(key).isOpen()){
                    result.add(new UpdateForClosedTransactionException(expectedTransactionCounter, msg));
                }
            }
        }

        if("FinishTransaction".equals(msg.getOperationType())){
            if(!openTransactions.containsKey(key)){
                result.add(new FinishForNotOpenTransactionException(expectedTransactionCounter, msg));
            }else{
                if(!openTransactions.get(key).isOpen()){
                    result.add(new FinishForClosedTransactionException(expectedTransactionCounter, msg));
                }
                openTransactions.get(key).close();
            }
        }
        return result;
    }


    static class Transaction {
        BigInteger signatureCounterLastUpdate;
        boolean isOpen;

        Transaction(TransactionLog msg) {
            this.signatureCounterLastUpdate = msg.getSignatureCounter();
            isOpen = true;
        }

        void close(){
            isOpen = false;
        }

        boolean isOpen(){return isOpen;}
    }


    public static class DuplicateTransactionCounterFoundException extends LogMessageValidationException {
        final BigInteger expectedCounter;

        DuplicateTransactionCounterFoundException(BigInteger expectedCounter, TransactionLog msg) {
            super(msg);
            this.expectedCounter = expectedCounter;
        }
    }

    public static class UpdateForNotOpenTransactionException extends LogMessageValidationException{
        final BigInteger expectedTransactionCounter;

        UpdateForNotOpenTransactionException(BigInteger transactionCounter, TransactionLog msg) {
            super(msg);
            this.expectedTransactionCounter = transactionCounter;
        }
    }

    public static class UpdateForClosedTransactionException extends LogMessageValidationException{
        final BigInteger expectedTransactionCounter;

        UpdateForClosedTransactionException(BigInteger transactionCounter, TransactionLog msg) {
            super(msg);
            this.expectedTransactionCounter = transactionCounter;
        }
    }

    public static class FinishForNotOpenTransactionException extends LogMessageValidationException{
        final BigInteger expectedTransactionCounter;

        FinishForNotOpenTransactionException(BigInteger transactionCounter, TransactionLog msg) {
            super(msg);
            this.expectedTransactionCounter = transactionCounter;
        }
    }

    public static class FinishForClosedTransactionException extends LogMessageValidationException{
        final BigInteger expectedTransactionCounter;

        FinishForClosedTransactionException(BigInteger transactionCounter, TransactionLog msg) {
            super(msg);
            this.expectedTransactionCounter = transactionCounter;
        }
    }

    public static class MissingTransactionCounterException extends LogMessageValidationException{
        final BigInteger expectedTransactionCounter;

        MissingTransactionCounterException(BigInteger expectedTransactionCounter, TransactionLog msg) {
            super(msg);
            this.expectedTransactionCounter = expectedTransactionCounter;
        }

        @Override
        public String toString(){
            return "Missing transaction start: Transaction Number was "+((TransactionLog)getLogMessage()).getTransactionNumber()+", but "+expectedTransactionCounter+" was expected.";
        }

    }

    public static class DelayedTransActionCounterException extends LogMessageValidationException{
        final BigInteger expectedTransactionCounter;

        DelayedTransActionCounterException(BigInteger expectedTransactionCounter, TransactionLog msg) {
            super(msg);
            this.expectedTransactionCounter = expectedTransactionCounter;
        }


        @Override
        public String toString(){
            return "Delayed transaction start: Transaction Number was "+((TransactionLog)getLogMessage()).getTransactionNumber()+", but "+expectedTransactionCounter+" was expected.";
        }
    }
}
