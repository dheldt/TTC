package de.konfidas.ttc.messages;

import de.konfidas.ttc.setup.Utilities;
import de.konfidas.ttc.utilities.ByteArrayOutputStream;
import de.konfidas.ttc.utilities.oid;
import org.bouncycastle.asn1.*;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static de.konfidas.ttc.setup.Utilities.getEncodedValue;


public abstract class TestLogMessageFactory {
    final static Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    int version = 2;
    oid certifiedDataType;
    ArrayList<ASN1Primitive> certifiedData = new ArrayList<>();
    byte[] serialNumber = "HelloWorld".getBytes(StandardCharsets.UTF_8);
    String signatureAlgorithm = "0.4.0.127.0.7.1.1.4.1.3";
    ArrayList<ASN1Primitive> signatureAlgorithmParameters = new ArrayList<>();
    String logTimeType = "unixTime";
    String logTimeUTC = "";
    String logTimeGeneralizedTime = "";
    long logTimeUnixTime = Instant.now().getEpochSecond();
    byte[] signatureValue = null;
    BigInteger signatureCounter = new BigInteger(64, new Random());
    byte[] seAuditData = null;
    byte[] dtbs = null;
    String filename = "";
    ASN1EncodableVector logMessageVector = new ASN1EncodableVector();
    ASN1Integer versionAsASN1;
    DEROctetString serialNumberAsASN1;
    ASN1EncodableVector signatureAlgorithmElementsList = new ASN1EncodableVector();
    ASN1ObjectIdentifier signatureAlgorithmAsASN1;

    ASN1Integer signatureCounterAsASN1;
    ASN1Integer logTimeUnixTimeAsASN1;
    ASN1UTCTime logTimeUTCAsASN1;
    ASN1GeneralizedTime logTimeGeneralizedTimeAsASN1;
    ASN1OctetString seAuditDataAsASN1;
    ASN1ObjectIdentifier certifiedDataTypeAsASN1;
    ASN1OctetString certifiedDataAsASN1;
    ASN1OctetString signatureValueAsASN1;

    public String getFilename() {
        return filename;
    }

    /******************************************************************
     * Abstrakte Basisclasse, um LogMessages zum Testen zu Ereugen
     * Jede Log Message muss den Lebenszyklus aus
     *  1) prepare
     *  2) calculateDTBS
     *  3) sign
     *  4) build
     *  6) ExportToFolder oder finalizeMessage
     *  durchlaufen
     ******************************************************************/

    public byte[] finalizeMessage() {
        filename = constructFileName();
        byte[] message = null;
        DERSequence messageSequenceForExport = new DERSequence(logMessageVector);
        try {
            message = messageSequenceForExport.getEncoded();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return message;
    }

    public String exportLogMessageToFolder(String exportFolder) throws TestLogMessageExportError {
        filename = constructFileName();
        DERSequence messageSequenceForExport = new DERSequence(logMessageVector);
        Path exportPath = Paths.get(exportFolder, filename);

        try {
            Files.write(exportPath, messageSequenceForExport.getEncoded());
        } catch (IOException e) {
            throw new TestLogMessageExportError("Fehler beim Export der Message", e);
        }
        return filename;
    }


    String constructFileName() {
        switch (logTimeType) {
            case "unixTime":
                filename = "Unixt_" + logTimeUnixTime + "_Sig-";
                break;
            case "utcTime":
                filename = "UTCTime_" + logTimeUTC + "_Sig-";
                break;
            case "generalizedTime":
                filename = "Gent_" + logTimeGeneralizedTime + "_Sig-";
                break;
        }

        filename = filename + signatureCounter.toString();
        filename = filename + "_Log-Aud.log";
        return filename;
    }

    void calculateDTBS() throws TestLogMessageCreationError {

        try (ByteArrayOutputStream dtbsStream = new ByteArrayOutputStream()) {

            dtbsStream.write(getEncodedValue(versionAsASN1));
            dtbsStream.write(getEncodedValue(certifiedDataTypeAsASN1));
            if (certifiedDataAsASN1 != null) dtbsStream.write(getEncodedValue(certifiedDataAsASN1));

            dtbsStream.write(getEncodedValue(serialNumberAsASN1));
            dtbsStream.write(getEncodedValue(signatureAlgorithmAsASN1));
            if (seAuditDataAsASN1 != null) dtbsStream.write(getEncodedValue(seAuditDataAsASN1));

            dtbsStream.write(getEncodedValue(signatureCounterAsASN1));

            switch (logTimeType) {
                case "unixTime":
                    dtbsStream.write(getEncodedValue(logTimeUnixTimeAsASN1));
                    break;
                case "utcTime":
                    dtbsStream.write(getEncodedValue(logTimeUTCAsASN1));
                    break;
                case "generalizedTime":
                    dtbsStream.write(getEncodedValue(logTimeGeneralizedTimeAsASN1));
                    break;
            }

            this.dtbs = dtbsStream.toByteArray();
        } catch (IOException | Utilities.ExtendLengthValueExceedsInteger e) {
            throw new TestLogMessageCreationError("Fehler beim Erstellen des DTBS", e);
        }

    }

    void sign(PrivateKey key) throws TestLogMessageCreationError {
        Signature signer = null;
        try {
            signer = Signature.getInstance(signatureAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new TestLogMessageCreationError("Fehler beim der Erstellung der Signatur", e);
        }
        try {
            signer.initSign(key);
        } catch (InvalidKeyException e) {
            throw new TestLogMessageCreationError("Fehler beim der Erstellung der Signatur. Ungültiger Schlüssel", e);
        }
        try {
            signer.update(dtbs);
            signatureValue = signer.sign();
            signatureValueAsASN1 = new DEROctetString(signatureValue);

        } catch (SignatureException e) {
            throw new TestLogMessageCreationError("Fehler beim der Erstellung der Signatur", e);
        }

    }

    void prepare() throws ParseException {

        versionAsASN1 = new ASN1Integer(version);
        //certifiedDataType will be set by subclasses
        //certifiedData will be set by subclasses
        serialNumberAsASN1 = new DEROctetString(serialNumber);

        signatureAlgorithmAsASN1 = new ASN1ObjectIdentifier(signatureAlgorithm);
        //fixme: im moment keine parameter für den algorithmus
        signatureAlgorithmElementsList.add(signatureAlgorithmAsASN1);
        //seAuditData filled by subclass
        signatureCounterAsASN1 = new ASN1Integer(signatureCounter);

        //logtime
        switch (logTimeType) {
            case "unixTime":
                logTimeUnixTimeAsASN1 = new ASN1Integer(logTimeUnixTime);
                break;
            case "utcTime":
                DateFormat uTCTimeFormat = new SimpleDateFormat();
                logTimeUTCAsASN1 = new ASN1UTCTime(uTCTimeFormat.parse(logTimeUTC));
                ;
                break;
            case "generalizedTime":
                DateFormat generalizedTimeFormat = new SimpleDateFormat();
                logTimeGeneralizedTimeAsASN1 = new ASN1GeneralizedTime(generalizedTimeFormat.parse(logTimeGeneralizedTime));
                break;
        }
    }

    void build() {
        if (versionAsASN1 != null) logMessageVector.add(versionAsASN1);
        if (certifiedDataTypeAsASN1 != null) logMessageVector.add(certifiedDataTypeAsASN1);
        if (certifiedDataAsASN1 != null) logMessageVector.add(certifiedDataAsASN1);
        if (serialNumberAsASN1 != null) logMessageVector.add(serialNumberAsASN1);
        //fixme: im moment keine parameter für den algorithmus
        if (signatureAlgorithmElementsList != null)
            logMessageVector.add(new DERSequence(signatureAlgorithmElementsList));
        if (seAuditDataAsASN1 != null) logMessageVector.add(seAuditDataAsASN1);
        if (signatureAlgorithmAsASN1 != null) logMessageVector.add(signatureCounterAsASN1);

        switch (logTimeType) {
            case "unixTime":
                logMessageVector.add(logTimeUnixTimeAsASN1);
                break;
            case "utcTime":
                logMessageVector.add(logTimeUTCAsASN1);
                break;
            case "generalizedTime":
                logMessageVector.add(logTimeGeneralizedTimeAsASN1);
                break;
        }
        logMessageVector.add(signatureValueAsASN1);

    }

    public class TestLogMessageCreationError extends Exception {
        public TestLogMessageCreationError(String message, Exception reason) {
            super(message, reason);
        }
    }
    public class TestLogMessageExportError extends Exception {
        public TestLogMessageExportError(String message, Exception reason) {
            super(message, reason);
        }
    }
}


