package de.konfidas.ttc.validation;

import de.konfidas.ttc.exceptions.ValidationException;
import de.konfidas.ttc.messages.LogMessage;
import de.konfidas.ttc.messages.TransactionLog;
import de.konfidas.ttc.tars.LogMessageArchive;
import org.junit.Test;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class TransActionCounterValidatorMockTest {
    ArrayList<LogMessage> messages;

    static class LMM extends LogMessageMock implements TransactionLog{
        final BigInteger transactionNumber;
        final String operationType;
        final byte[] serial;

        LMM(byte[] serial, BigInteger transactionNumber, String operationType){
            this.transactionNumber = transactionNumber;
            this.operationType = operationType;
            this.serial = serial;
        }


        @Override
        public byte[] getSerialNumber(){
            return serial;
        }
        @Override
        public BigInteger getTransactionNumber() {
            return transactionNumber;
        }

        @Override
        public String getClientID() {
            return null;
        }

        @Override
        public String getOperationType() {
            return operationType;
        }
    }

    public TransActionCounterValidatorMockTest(){
        messages = new ArrayList<>();
    }

    class TestTar implements LogMessageArchive {
        @Override
        public Map<String, X509Certificate> getIntermediateCertificates() {
            return null;
        }

        @Override
        public Map<String, X509Certificate> getClientCertificates() {
            return null;
        }

        @Override
        public Collection<LogMessage> getLogMessages() {
            return messages;
        }

        @Override
        public Collection<? extends LogMessage> getSortedLogMessages() {
            return messages;
        }
    }


    @Test
    public void testEmpty(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.isEmpty());
    }


    @Test
    public void testS1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.isEmpty());
    }

    @Test
    public void testS2(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.TWO, "StartTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.MissingTransactionCounterException);
    }


    @Test
    public void testS1S2(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.TWO, "StartTransaction"));

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.isEmpty());
    }

    @Test
    public void testS1S1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.DuplicateTransactionCounterFoundException);
    }


    @Test
    public void testU1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.UpdateForNotOpenTransactionException);
    }

    @Test
    public void testF1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "FinishTransaction"));

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.FinishForNotOpenTransactionException);
    }

    @Test
    public void testS1U1F1U1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "FinishTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.UpdateForClosedTransactionException);
    }

    @Test
    public void testS1U1F1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "FinishTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "FinishTransaction"));

        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.FinishForClosedTransactionException);
    }

    @Test
    public void testS1U1U1U1U1F1(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "UpdateTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "FinishTransaction"));


        Collection<ValidationException> r = validator.validate(tar);

        assertTrue(r.isEmpty());
    }


    @Test
    public void testS1AS1B(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x02}, BigInteger.ONE, "StartTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.isEmpty());
    }


    @Test
    public void testS1AU1B(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x02}, BigInteger.ONE, "UpdateTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.UpdateForNotOpenTransactionException);
    }

    @Test
    public void testS1AF1B(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x02}, BigInteger.ONE, "FinishTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.FinishForNotOpenTransactionException);
    }

    @Test
    public void testS1AS2B(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x02}, BigInteger.TWO, "StartTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.size() == 1);
        assertTrue(r.stream().findFirst().get() instanceof TransactionCounterValidator.MissingTransactionCounterException);
    }

    @Test
    public void testS1AS1BF1AF1B(){
        TransactionCounterValidator validator = new TransactionCounterValidator();
        LogMessageArchive tar = new TestTar();

        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x02}, BigInteger.ONE, "StartTransaction"));
        messages.add(new LMM(new byte[]{0x01}, BigInteger.ONE, "FinishTransaction"));
        messages.add(new LMM(new byte[]{0x02}, BigInteger.ONE, "FinishTransaction"));

        Collection<ValidationException> r = validator.validate(tar);
        assertTrue(r.isEmpty());
    }
}
