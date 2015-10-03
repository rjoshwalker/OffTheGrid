package com.xrmaddness.offthegrid;

import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.io.*;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.NoSuchProviderException;
import javax.mail.util.ByteArrayDataSource;

import org.spongycastle.bcpg.*;
import org.spongycastle.bcpg.sig.*;
import org.spongycastle.openpgp.*;
import org.spongycastle.openpgp.operator.*;
import org.spongycastle.openpgp.operator.bc.*;
import org.spongycastle.util.encoders.Hex;
import org.spongycastle.util.io.Streams;
import org.spongycastle.crypto.params.*;
import org.spongycastle.crypto.generators.*;
import org.spongycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class pgp {
	static Context context;
	
	static PGPPublicKeyRingCollection public_keyring_collection;
	static PGPSecretKeyRing secret_keyring;
	// use a passphrase, but don't bother user with it.
	static char pass[] = {'Q', 'u', 'i', 'c', 'k', 'M', 'S', 'G'};
	
	File file_pub;
	File file_sec;
	
	public static String my_user_id;
	
	private static synchronized void init_rings(pgp pgp)
	{
		if (!pgp.load_keys()) {
			pgp.generate_keys(pgp.my_user_id);
			pgp.load_keys();
		}
	}
	
	public pgp(Context app_context)
	{
		context = app_context;

		file_pub = new File(context.getExternalFilesDir(null), "pubring.pkr");
		file_sec = new File(context.getExternalFilesDir(null), "secring.pkr");

		SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);

	    String user_id = SP.getString("email_address", null);
	    if (user_id == null)
	    	return;

		my_user_id = user_id;
		
		init_rings(this);
		if (secret_keyring == null)
			return;
		if (public_keyring_collection == null)
			return;

		Iterator it = secret_keyring.getSecretKeys();
		
		while (it.hasNext()) {
			PGPSecretKey key = (PGPSecretKey)it.next();
			
			Iterator it2 = key.getUserIDs();
			
			while (it2.hasNext()) {
				String key_user_id = (String)it2.next();
				
				if (key_user_id.equals(user_id))
					return;
			}
		}
		
		generate_keys(user_id);
		load_keys();
	}

	@SuppressWarnings("deprecation")
	public Boolean load_keys()
	{
		FileInputStream pubin;
		FileInputStream secin;
		
		Log.d("load_keys", "Load pgp keys");
		
		try {
			pubin = new FileInputStream(file_pub);
			secin = new FileInputStream(file_sec);

			public_keyring_collection = new PGPPublicKeyRingCollection(pubin);
			pubin.close();
			secret_keyring = new PGPSecretKeyRing(secin);
			secin.close();
		} 
		catch (Exception e) {
			Log.e("load_keys", e.getMessage());
			return false;
		}
		
		Log.d("load_keys", "Keys loaded");
		return true;
	}

	private static synchronized void save_keys(pgp pgp)
	{
		try {
			FileOutputStream pubout = new FileOutputStream(pgp.file_pub);
			pgp.public_keyring_collection.encode(pubout);
			pubout.close();

			FileOutputStream secout = new FileOutputStream(pgp.file_sec);
			pgp.secret_keyring.encode(secout);
			secout.close();
		}
		catch (Exception e) {
			Log.e("save_keys", e.getMessage());
		}
	}
	
	public void generate_keys(String email_address)
	{
		Log.d("generate_keys", "Generate new key pair for " + email_address);

		try {
			PGPPublicKeyRing public_keyring;
			
			PGPKeyRingGenerator krgen = generateKeyRingGenerator
	            (email_address, pass);
			
			Log.d("generate_keys", "Create public keyring");
			
			// Generate public key ring, dump to file.
			public_keyring = krgen.generatePublicKeyRing();
			FileOutputStream pubout = new FileOutputStream(file_pub);
			//BufferedOutputStream pubout = new BufferedOutputStream
			//        (new FileOutputStream("dummy.pkr"));
			public_keyring.encode(pubout);
			pubout.close();

			Log.d("generate_keys", "Create secret keyring");

			// Generate private key, dump to file.
			secret_keyring = krgen.generateSecretKeyRing();
			FileOutputStream secout = new FileOutputStream(file_sec);
			//BufferedOutputStream secout = new BufferedOutputStream
			//        (new FileOutputStream("dummy.skr"));
			secret_keyring.encode(secout);
			secout.close();
		}
		catch (Exception e){
			Log.e("generate_keys", e.getMessage());
		}
		
		Log.d("generate_keys", "Keys generated");
	}
	
	public final static PGPKeyRingGenerator generateKeyRingGenerator
    	(String id, char[] pass)
    			throws Exception
    { return generateKeyRingGenerator(id, pass, 0xc0); }

	// Note: s2kcount is a number between 0 and 0xff that controls the
	// number of times to iterate the password hash before use. More
	// iterations are useful against offline attacks, as it takes more
	// time to check each password. The actual number of iterations is
	// rather complex, and also depends on the hash function in use.
	// Refer to Section 3.7.1.3 in rfc4880.txt. Bigger numbers give
	// you more iterations.  As a rough rule of thumb, when using
	// SHA256 as the hashing function, 0x10 gives you about 64
	// iterations, 0x20 about 128, 0x30 about 256 and so on till 0xf0,
	// or about 1 million iterations. The maximum you can go to is
	// 0xff, or about 2 million iterations.  I'll use 0xc0 as a
	// default -- about 130,000 iterations.

	public final static PGPKeyRingGenerator generateKeyRingGenerator
		(String id, char[] pass, int s2kcount)
		throws Exception
	{
    	// This object generates individual key-pairs.
		RSAKeyPairGenerator  kpg = new RSAKeyPairGenerator();

		// Boilerplate RSA parameters, no need to change anything
		// except for the RSA key-size (2048). You can use whatever
		// key-size makes sense for you -- 4096, etc.
		kpg.init
        	(new RSAKeyGenerationParameters
        			(BigInteger.valueOf(0x10001),
        	 new SecureRandom(), 2048, 12));

		Toast.makeText(context, "Create master key", Toast.LENGTH_SHORT).show();

		// First create the master (signing) key with the generator.
		PGPKeyPair rsakp_sign =
			new BcPGPKeyPair
			(PGPPublicKey.RSA_SIGN, kpg.generateKeyPair(), new Date());
		// Then an encryption subkey.
		PGPKeyPair rsakp_enc =
			new BcPGPKeyPair
			(PGPPublicKey.RSA_ENCRYPT, kpg.generateKeyPair(), new Date());

		// Add a self-signature on the id
		PGPSignatureSubpacketGenerator signhashgen =
				new PGPSignatureSubpacketGenerator();
    
		// Add signed metadata on the signature.
		// 1) Declare its purpose
		signhashgen.setKeyFlags
        	(false, KeyFlags.SIGN_DATA|KeyFlags.CERTIFY_OTHER);
		// 2) Set preferences for secondary crypto algorithms to use
		//    when sending messages to this key.
		signhashgen.setPreferredSymmetricAlgorithms
			(false, new int[] {
					SymmetricKeyAlgorithmTags.AES_256,
					SymmetricKeyAlgorithmTags.AES_192,
					SymmetricKeyAlgorithmTags.AES_128
			});
		signhashgen.setPreferredHashAlgorithms(false, new int[] {
				HashAlgorithmTags.SHA256,
				HashAlgorithmTags.SHA1,
				HashAlgorithmTags.SHA384,
				HashAlgorithmTags.SHA512,
				HashAlgorithmTags.SHA224,
        });
		// 3) Request senders add additional checksums to the
		//    message (useful when verifying unsigned messages.)
		signhashgen.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);

		// Create a signature on the encryption subkey.
		PGPSignatureSubpacketGenerator enchashgen =
				new PGPSignatureSubpacketGenerator();
		// Add metadata to declare its purpose
		enchashgen.setKeyFlags
        	(false, KeyFlags.ENCRYPT_COMMS|KeyFlags.ENCRYPT_STORAGE);

		// Objects used to encrypt the secret key.
		PGPDigestCalculator sha1Calc =
				new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
		PGPDigestCalculator sha256Calc =
				new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256);

		// bcpg 1.48 exposes this API that includes s2kcount. Earlier
		// versions use a default of 0x60.
		PBESecretKeyEncryptor pske =
				(new BcPBESecretKeyEncryptorBuilder
				(PGPEncryptedData.AES_256, sha256Calc, s2kcount)).build(pass);
    
		Toast.makeText(context, "Create keyring", Toast.LENGTH_SHORT).show();

		// Finally, create the keyring itself. The constructor
		// takes parameters that allow it to generate the self
		// signature.
		PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator
				(PGPSignature.POSITIVE_CERTIFICATION, rsakp_sign,
				id, sha1Calc, signhashgen.generate(), null,
				new BcPGPContentSignerBuilder
				(rsakp_sign.getPublicKey().getAlgorithm(),
						HashAlgorithmTags.SHA1),
						pske);

		// Add our encryption subkey, together with its signature.
		keyRingGen.addSubKey(rsakp_enc, enchashgen.generate(), null);
		return keyRingGen;
	}

	public String public_keyring_add_key(InputStream is)
	{
		PGPPublicKeyRing keyring;
		try {
			keyring = new PGPPublicKeyRing(is);
		}
		catch (Exception e) {
			Log.d("add_key", e.getMessage() + ", attempt to read armored key");
			
			try {
				keyring = new PGPPublicKeyRing(new ArmoredInputStream(is));
			}
			catch (Exception e2) {
				Log.e("add_key", e2.getMessage());
				return null;
			}
		}
		String add = public_keyring_get_address(keyring);
		Log.d("add_key", "Received key for: " + add);
		
		PGPPublicKeyRing r_exist = public_keyring_get_by_address(add);
		if (r_exist != null) {
			Log.d("add_key", "Already have a key for this address");
			return add;
		}
		Log.d("add_key", "New key, add it to collection");
		
		public_keyring_collection = PGPPublicKeyRingCollection.addPublicKeyRing(
				public_keyring_collection, keyring);
		
		save_keys(this);
		
		return add;
	}
	
	public void public_keyring_get_armored_by_address(String address, OutputStream os)
	{
		PGPPublicKeyRing keyring = public_keyring_get_by_address(address);
		if (keyring == null) {
			Log.d("public_keyring_get_armored_by_address", "can't get key for: " + address);
			return;
		}
		
		ArmoredOutputStream osa = new ArmoredOutputStream(os);
		try {
			keyring.encode(osa);
			osa.close();
		}
		catch (Exception e) {
			Log.e("public_keyring_get_armored_by_address", e.getMessage());
		}
	}
	

	public static String public_keyring_get_address(PGPPublicKeyRing ring)
	{
		Iterator it2 = ring.getPublicKeys();
		
		while (it2.hasNext()) {
			PGPPublicKey key = (PGPPublicKey)it2.next();
			
			Iterator it3 = key.getUserIDs();
			
			while (it3.hasNext()) {
				String user_id = (String)it3.next();
				
				if (!user_id.contains("@"))
					continue;
				Pattern pattern = Pattern.compile("<(.*?)>");
				Matcher matcher = pattern.matcher(user_id);
				if (matcher.find()) {
					user_id = matcher.group(1);
				}
				return user_id;
			}
		}
		return null;
	}
	
	public static PGPPublicKeyRing public_keyring_get_by_address(String address)
	{
		Iterator it = public_keyring_collection.getKeyRings();
		
		while (it.hasNext()) {
			PGPPublicKeyRing ring = (PGPPublicKeyRing)it.next();
			String user_id = public_keyring_get_address(ring);
			
			if (user_id.contains(address))
				return ring;
		}
		return null;
	}
	
	public String fingerprint(String address)
	{
		Iterator it = public_keyring_collection.getKeyRings();
		
		while (it.hasNext()) {
			PGPPublicKeyRing ring = (PGPPublicKeyRing)it.next();
			
			Iterator it2 = ring.getPublicKeys();
			
			while (it2.hasNext()) {
				PGPPublicKey key = (PGPPublicKey)it2.next();
				
				Iterator it3 = key.getUserIDs();
				
				while (it3.hasNext()) {
					String user_id = (String)it3.next();
					
					if (user_id.contains(address))
						return new String(Hex.encode(key.getFingerprint()));
				}
			}
		}
		
		return null;
	}

	public String public_keyring_get_fingerprint(PGPPublicKeyRing ring)
	{
		Iterator it2 = ring.getPublicKeys();
			
		while (it2.hasNext()) {
			PGPPublicKey key = (PGPPublicKey)it2.next();
				
			Iterator it3 = key.getUserIDs();
				
			while (it3.hasNext()) {
				return new String(Hex.encode(key.getFingerprint()));
			}
		}
		
		return null;
	}

	public void public_keyring_remove_by_address(String address)
	{
		PGPPublicKeyRing pk = public_keyring_get_by_address(address);
		if (pk == null)
			return;
		
		public_keyring_collection = PGPPublicKeyRingCollection.removePublicKeyRing(
				public_keyring_collection, pk);
		save_keys(this);
	}

	public static PGPPublicKey public_key_encrypt_get_by_address(String id)
	{
		PGPPublicKeyRing pkr = public_keyring_get_by_address(id);
		
		if (pkr == null) {
			Log.d("public_key_encrypt_get_by_address", "pkr null for: " + id);
		}
		
		Iterator<PGPPublicKey> keyIter = pkr.getPublicKeys();
		// iterate over public keys in the key ring
	    while ( keyIter.hasNext() ) {
	    	PGPPublicKey tmpKey = keyIter.next();
	    	Log.d("public_key_encrypt_get_by_address", "next key: " + (tmpKey == null));
	    	if (tmpKey == null)
	    		continue;
	    	
	    	Log.d("public_key_encrypt_get_by_address", "algo: " + tmpKey.getAlgorithm() +
	    			" bits: " + tmpKey.getBitStrength() + " isEnc: " +tmpKey.isEncryptionKey() + " isMaster: " +
	    			tmpKey.isMasterKey());
            // we need a master encryption key
	        if ( tmpKey.isEncryptionKey()) {
	        	Log.d("public_key_encrypt_get_by_address", "found key");
	        	return tmpKey;
	        }
	    }
	    Log.d("public_key_encrypt_get_by_address", "public encryption key not found");
	    return null;
 	}
	
	private static PGPSecretKey secret_key_sign_get()
	{
	        PGPSecretKeyRing secretKeyRing = secret_keyring;
	        PGPSecretKey tmpKey = secretKeyRing.getSecretKey();
		        
	        if (tmpKey != null) { 
	        	if ( tmpKey.isSigningKey() && tmpKey.isMasterKey() )
	        	{
	            	return tmpKey;
	        	}
		    }
		    return null;
	}
	
	private static PGPPrivateKey private_key_get_by_id(long id)
	{
		PGPSecretKeyRing pgpsec = secret_keyring;
		PGPSecretKey pgpSecKey = pgpsec.getSecretKey(id);

	    if (pgpSecKey == null) {
	    	return null;
	    }

	    try {
	    	PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(pass);
			return pgpSecKey.extractPrivateKey(decryptor);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static byte[] byte_decrypt(byte[] encrypted)
            throws IOException, PGPException, NoSuchProviderException {
        InputStream in = new ByteArrayInputStream(encrypted);

        in = PGPUtil.getDecoderStream(in);

        PGPObjectFactory pgpF = new PGPObjectFactory(in);
        PGPEncryptedDataList enc = null;
        Object o = pgpF.nextObject();

        //
        // the first object might be a PGP marker packet.
        //
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }



        //
        // find the secret key
        //
        Iterator it = enc.getEncryptedDataObjects();
        PGPPrivateKey sKey = null;
        PGPPublicKeyEncryptedData pbe = null;

        while (sKey == null && it.hasNext()) {
            pbe = (PGPPublicKeyEncryptedData) it.next();

            sKey = private_key_get_by_id(pbe.getKeyID());
        }

        if (sKey == null) {
            throw new IllegalArgumentException(
                    "secret key for message not found.");
        }

        InputStream clear;
		try {
			BcPublicKeyDataDecryptorFactory bcpkf = new BcPublicKeyDataDecryptorFactory(sKey);
			clear = pbe.getDataStream(bcpkf);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}



        PGPObjectFactory pgpFact = new PGPObjectFactory(clear);

        PGPCompressedData cData = (PGPCompressedData) pgpFact.nextObject();

        pgpFact = new PGPObjectFactory(cData.getDataStream());

        PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();

        InputStream unc = ld.getInputStream();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ch;

        while ((ch = unc.read()) >= 0) {
            out.write(ch);

        }

        byte[] returnBytes = out.toByteArray();
        out.close();
        return returnBytes;
    }
	
	public static byte[] byte_decrypt_verify(byte[] encrypted)
            throws IOException, PGPException, NoSuchProviderException {
        InputStream in = new ByteArrayInputStream(encrypted);

        in = PGPUtil.getDecoderStream(in);

        PGPObjectFactory pgpF = new PGPObjectFactory(in);
        PGPEncryptedDataList enc = null;
        Object o = pgpF.nextObject();

        //
        // the first object might be a PGP marker packet.
        //
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }

        //
        // find the secret key
        //
        Iterator it = enc.getEncryptedDataObjects();
        PGPPrivateKey sKey = null;
        PGPPublicKeyEncryptedData pbe = null;

        while (sKey == null && it.hasNext()) {
            pbe = (PGPPublicKeyEncryptedData) it.next();

            sKey = private_key_get_by_id(pbe.getKeyID());
        }

        if (sKey == null) {
            throw new IllegalArgumentException(
                    "secret key for message not found.");
        }

        InputStream clear;
		try {
			BcPublicKeyDataDecryptorFactory bcpkf = new BcPublicKeyDataDecryptorFactory(sKey);
			clear = pbe.getDataStream(bcpkf);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		PGPObjectFactory plainFact = new PGPObjectFactory(clear);

	    Object message = null;

	    PGPOnePassSignatureList onePassSignatureList = null;
	    PGPSignatureList signatureList = null;
	    PGPCompressedData compressedData = null;

	    message = plainFact.nextObject();
	    ByteArrayOutputStream actualOutput = new ByteArrayOutputStream();

	    while (message != null) {
	        Log.d("byte_decrypt_verify", message.toString());
	        if (message instanceof PGPCompressedData) {
	            compressedData = (PGPCompressedData) message;
	            plainFact = new PGPObjectFactory(compressedData.getDataStream());
	            message = plainFact.nextObject();
	        }

	        if (message instanceof PGPLiteralData) {
	            // have to read it and keep it somewhere.
	            Streams.pipeAll(((PGPLiteralData) message).getInputStream(), actualOutput);
	        } else if (message instanceof PGPOnePassSignatureList) {
	            onePassSignatureList = (PGPOnePassSignatureList) message;
	        } else if (message instanceof PGPSignatureList) {
	            signatureList = (PGPSignatureList) message;
	        } else {
	            throw new PGPException("message unknown message type.");
	        }
	        message = plainFact.nextObject();
	    }
	    actualOutput.close();
	    PGPPublicKey publicKey = null;
	    byte[] output = actualOutput.toByteArray();
	    if (onePassSignatureList == null || signatureList == null) {
	        Log.d("byte_decrypt_verify", "poor PGP. Signatures not found.");
	        return null;
	    } else {

	        for (int i = 0; i < onePassSignatureList.size(); i++) {
	            PGPOnePassSignature ops = onePassSignatureList.get(0);
	            Log.d("byte_decrypt_verify", "verifier : " + ops.getKeyID());

	            publicKey = public_keyring_collection.getPublicKey(ops.getKeyID()); 
	            if (publicKey != null) {
	                ops.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), publicKey);
	                try {
						ops.update(output);
					} catch (SignatureException e) {
						e.printStackTrace();
						return null;
					}
	                PGPSignature signature = signatureList.get(i);
	                try {
						if (ops.verify(signature)) {
						    Iterator<?> userIds = publicKey.getUserIDs();
						    while (userIds.hasNext()) {
						        String userId = (String) userIds.next();
						        Log.d("byte_decrypt_verify", "Signed by: "+ userId);
						    }
						    Log.d("byte_decrypt_verify", "Signature verified");
						} else {
						    Log.d("byte_decrypt_verify", "Signature verification failed");
						    return null;
						}
					} catch (SignatureException e) {
						Log.d("byte_decrypt_verify", e.getMessage());
						e.printStackTrace();
						return null;
					}
	            } else {
	            	Log.d("byte decrypt verify", "No public key found");
	            }
	        }

	    }

	    ByteArrayOutputStream out = new ByteArrayOutputStream();

	    if (pbe.isIntegrityProtected() && !pbe.verify()) {
	        throw new PGPException("Data is integrity protected but integrity is lost.");
	    } else if (publicKey == null) {
	    	return null;
	    } else {
	        out.write(output);
        }

        byte[] returnBytes = out.toByteArray();
        
        out.close();
        return returnBytes;
    }
	
	public static byte[] byte_encrypt(byte[] clearData, PGPPublicKey encKey,
            String fileName,boolean withIntegrityCheck, boolean armor)
            throws IOException, PGPException, NoSuchProviderException 
    {
        if (fileName == null) {
            fileName = PGPLiteralData.CONSOLE;
        }

        ByteArrayOutputStream encOut = new ByteArrayOutputStream();

        OutputStream out = encOut;
        if (armor) {
            out = new ArmoredOutputStream(out);
        }

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(
                PGPCompressedDataGenerator.ZIP);
        OutputStream cos = comData.open(bOut); // open it with the final
        // destination
        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

        
        // we want to generate compressed data. This might be a user option
        // later,
        // in which case we would pass in bOut.
        OutputStream pOut = lData.open(cos, // the compressed output stream
                PGPLiteralData.BINARY, fileName, // "filename" to store
                clearData.length, // length of clear data
                new Date() // current time
                );
        pOut.write(clearData);

        lData.close();
        comData.close();

//        PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(
//               PGPEncryptedData.CAST5, withIntegrityCheck, new SecureRandom(),
//                "BC");
     // Initialise encrypted data generator (the new way)  
        BcPGPDataEncryptorBuilder builder = new BcPGPDataEncryptorBuilder(PGPEncryptedData.CAST5);  
        builder.setSecureRandom(new SecureRandom());  
        PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(builder);  
        
        try {
			cPk.addMethod(encKey);
		} catch (java.security.NoSuchProviderException e) {
			Log.e("pgp", e.getMessage());
		}

        byte[] bytes = bOut.toByteArray();

        OutputStream cOut = cPk.open(out, bytes.length);

        cOut.write(bytes); // obtain the actual bytes from the compressed stream

        cOut.close();

        out.close();

        return encOut.toByteArray();
    }

	public static byte[] byte_encrypt_sign(byte[] clearData, PGPPublicKey encKey,
            String fileName,boolean withIntegrityCheck, boolean armor, PGPSecretKey secKey)
            throws IOException, PGPException, NoSuchProviderException {
        if (fileName == null) {
            fileName = PGPLiteralData.CONSOLE;
        }
        Log.d("byte_encrypt_sign", "start encryption and signing");
        
        PGPPrivateKey privKey;
		try {
			privKey = secKey.extractPrivateKey(pass, "BC" );
		} catch (Exception e) {
			Log.e("encrypt sign", e.getMessage());
			return null;
		}
		Log.d("byte_encrypt_sign", "got private key");
		
        ByteArrayOutputStream encOut = new ByteArrayOutputStream();

        OutputStream out = encOut;
        if (armor) {
            out = new ArmoredOutputStream(out);
        }

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        Log.d("byte_encrypt_sign", "Going to create compressed data generator");
        
        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(
                PGPCompressedDataGenerator.ZIP);
        OutputStream cos = comData.open(bOut); // open it with the final

        Log.d("byte_encrypt_sign", "Create signature generator");
        
        PGPSignatureGenerator signatureGenerator;
		try {
			signatureGenerator = new PGPSignatureGenerator(secKey.getPublicKey().getAlgorithm(), PGPUtil.SHA1, "BC");
		} catch (Exception e) {
			Log.e("encryp sign", e.getMessage());
			return null;
		}
        signatureGenerator.initSign(PGPSignature.BINARY_DOCUMENT, privKey);
        Iterator<String> userIds = secKey.getPublicKey().getUserIDs();
        // use the first userId
        if ( userIds.hasNext() )
        {
            PGPSignatureSubpacketGenerator subpacketGenerator = new PGPSignatureSubpacketGenerator();
            subpacketGenerator.setSignerUserID( false, userIds.next() );
            signatureGenerator.setHashedSubpackets( subpacketGenerator.generate() );
        }

        Log.d("byte_encrypt_sign", "generate one pass version");
        
        signatureGenerator.generateOnePassVersion(false).encode(cos);
        
        // destination
        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

        Log.d("byte_encrypt_sign", "Generate compressed data");
        // we want to generate compressed data. This might be a user option
        // later,
        // in which case we would pass in bOut.
        OutputStream pOut = lData.open(cos, // the compressed output stream
                PGPLiteralData.BINARY, fileName, // "filename" to store
                clearData.length, // length of clear data
                new Date() // current time
                );
        pOut.write(clearData);
        try {
			signatureGenerator.update(clearData);
		} catch (SignatureException e1) {
			Log.e("Byte encrypt sign", e1.getMessage());
			e1.printStackTrace();
			return null;
		}

        lData.close();
        try {
			signatureGenerator.generate().encode(cos);
		} catch (SignatureException e) {
			Log.e("byte encrypt sign", e.getMessage());
			e.printStackTrace();
		}
        comData.close();

        Log.d("byte_encrypt_sign", "encrypted data generator");
        
  //      PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(
  //              PGPEncryptedData.CAST5, withIntegrityCheck, new SecureRandom(), "BC");
        // Initialise encrypted data generator (the new way)  
        BcPGPDataEncryptorBuilder builder = new BcPGPDataEncryptorBuilder(PGPEncryptedData.CAST5);  
        builder.setSecureRandom(new SecureRandom());  
        PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(builder);  
  
        Log.d("byte_encrypt_sign", "add enc key");
        if (cPk == null) {
        	Log.d("byte_encrypt_sign", "cPk == null");
        }
        
        try {
			cPk.addMethod(encKey);
		} catch (Exception e) {
			Log.e("pgp", e.getMessage());
		}

        Log.d("byte_encrypt_sign", "generate byte array");
        
        byte[] bytes = bOut.toByteArray();

        Log.d("byte_encrypt_sign", "open output stream");
        
        OutputStream cOut = cPk.open(out, bytes.length);

        Log.d("byte_encrypt_sign", "write bytes");
        
        cOut.write(bytes); // obtain the actual bytes from the compressed stream

        cOut.close();

        out.close();

        return encOut.toByteArray();
    }

	public attachment encrypt_sign(attachment a_in, String to_user)
	{
		String filename = a_in.name;
		byte[] enc_data;
		PGPSecretKey secKey = secret_key_sign_get();
		PGPPublicKey encKey = public_key_encrypt_get_by_address(to_user);
		
		//= a_in.datahandler.getDataSource().getInputStream().
		ByteArrayOutputStream clear_os = new ByteArrayOutputStream();
		try {
			a_in.datahandler.writeTo(clear_os);

			Log.d("encrypt sign", "going to get clear data");
			
			byte[] clear_data = clear_os.toByteArray();
		
			Log.d("encrypt sign", "got clear_data");
			enc_data = byte_encrypt_sign(clear_data, encKey,
		            filename, true, true, secKey);
//			enc_data = byte_encrypt(clear_data, encKey,
//		            filename, true, true);
		} catch (PGPException e) {
			Log.e("encrypt sign pgp", e.getMessage());
			e.getUnderlyingException().printStackTrace();
			return null;
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			Log.e("encrypt sign nsp", e.getMessage());
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e("encrypt sign io", e.getMessage());
			return null;
		}

		attachment a_out = new attachment();
		a_out.name = filename + ".asc";
		
		DataSource ds = new ByteArrayDataSource(enc_data, "application/octet-stream");
		
		a_out.datahandler = new DataHandler(ds);
		
		return a_out;
	}

	public attachment decrypt_verify(attachment a_in)
	{
		String filename = a_in.name;
		byte[] dec_data;
		
		//= a_in.datahandler.getDataSource().getInputStream().
		ByteArrayOutputStream clear_os = new ByteArrayOutputStream();
		try {
			a_in.datahandler.writeTo(clear_os);

			Log.d("decrypt verify", "going to get clear data");
			
			byte[] enc_data = clear_os.toByteArray();
		
			Log.d("decrypt verify", "got clear_data");
			dec_data = byte_decrypt_verify(enc_data);
			if (dec_data == null) {
				Log.d("decrypt verify", "Failed to decrypt and verify");
				return null;
			}
		} catch (PGPException e) {
			Log.e("decrypt sign pgp", e.getMessage());
			e.getUnderlyingException().printStackTrace();
			return null;
		} catch (Exception e) {
			Log.e("decrypt sign", e.getMessage());
			return null;
		}
		
		attachment a_out = new attachment();
		a_out.name = filename + ".asc";
		
		DataSource ds = new ByteArrayDataSource(dec_data, "multipart/mixed");
		
		a_out.datahandler = new DataHandler(ds);
		
		return a_out;
		
	}
	
	attachment pgpmime_id()
	{
		attachment a = new attachment();
		a.disposition = "inline";
		
		DataSource ds;
		try {
			ds = new ByteArrayDataSource("Version: 1\n", "application/pgp-encrypted");
		} catch (IOException e) {
			return null;
		} 
		
		a.datahandler = new DataHandler(ds);
		a.name = "pgp_mime_id";
		
		return a;
	}
	
	public attachment key_attachment(String keyuser)
	{
		Log.d("key_attachment", "generate armored public key");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		public_keyring_get_armored_by_address(keyuser, baos);
		
		attachment attachment = new attachment();
		attachment.datahandler = new DataHandler(new  ByteArrayDataSource(baos.toByteArray(),"application/pgp-keys"));
		attachment.name = fingerprint(keyuser) + ".asc";
		
		return attachment;
	}

}
