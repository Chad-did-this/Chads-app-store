package com.chaddidthis.commandpro.shared.adb;

import android.content.Context;
import android.os.Build;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class LocalAdb extends AbsAdbConnectionManager {
    private final PrivateKey key;
    private final Certificate certificate;
    private static LocalAdb instance;
    public static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor();
    public static final java.util.concurrent.ScheduledExecutorService TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    public static synchronized LocalAdb peek() { return instance; }

    public static synchronized LocalAdb get(Context context) throws Exception {
        if (instance == null) instance = new LocalAdb(context.getApplicationContext());
        return instance;
    }

    private LocalAdb(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);
        setHostAddress("127.0.0.1");
        File identity = new File(context.getNoBackupFilesDir(), "identity.p12data");
        if (identity.exists()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(identity))) {
                int size = in.readInt();
                if (size < 1 || size > 16384) throw new IOException("Invalid local identity");
                byte[] bytes = new byte[size];
                in.readFully(bytes);
                key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
                certificate = CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            key = pair.getPrivate();
            X500Name name = new X500Name("CN=Command Center PRO");
            long now = System.currentTimeMillis();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name,
                new BigInteger(128, new SecureRandom()).setBit(127),
                new Date(now - 86400000L), new Date(now + 315360000000L), name, pair.getPublic());
            certificate = new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(key)));
            File temporary = new File(identity.getParentFile(), "identity.tmp");
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(temporary))) {
                byte[] bytes = key.getEncoded();
                out.writeInt(bytes.length);
                out.write(bytes);
                out.write(certificate.getEncoded());
            }
            if (!temporary.renameTo(identity)) throw new IOException("Could not save local identity");
        }
    }

    @Override protected PrivateKey getPrivateKey() { return key; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "Command Center PRO"; }
}
