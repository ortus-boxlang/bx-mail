/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.mail.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.mail.smime.SMIMEToolkit;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public final class MailEncryptionUtil {

	static final HashMap<Key, ASN1ObjectIdentifier> ENCRYPT_ALGORITHMS = new HashMap<Key, ASN1ObjectIdentifier>() {

		{
			put( Key.of( "AES128_CBC" ), CMSAlgorithm.AES128_CBC );
			put( Key.of( "AES128_CCM" ), CMSAlgorithm.AES128_CCM );
			put( Key.of( "AES128_GCM" ), CMSAlgorithm.AES128_GCM );
			put( Key.of( "AES128_WRAP" ), CMSAlgorithm.AES128_WRAP );
			put( Key.of( "AES192_CBC" ), CMSAlgorithm.AES192_CBC );
			put( Key.of( "AES192_CCM" ), CMSAlgorithm.AES192_CCM );
			put( Key.of( "AES192_GCM" ), CMSAlgorithm.AES192_GCM );
			put( Key.of( "AES192_WRAP" ), CMSAlgorithm.AES192_WRAP );
			put( Key.of( "AES256_CBC" ), CMSAlgorithm.AES256_CBC );
			put( Key.of( "AES256_CCM" ), CMSAlgorithm.AES256_CCM );
			put( Key.of( "AES256_GCM" ), CMSAlgorithm.AES256_GCM );
			put( Key.of( "AES256_WRAP" ), CMSAlgorithm.AES256_WRAP );
			put( Key.of( "CAMELLIA128_CBC" ), CMSAlgorithm.CAMELLIA128_CBC );
			put( Key.of( "CAMELLIA128_WRAP" ), CMSAlgorithm.CAMELLIA128_WRAP );
			put( Key.of( "CAMELLIA192_CBC" ), CMSAlgorithm.CAMELLIA192_CBC );
			put( Key.of( "CAMELLIA192_WRAP" ), CMSAlgorithm.CAMELLIA192_WRAP );
			put( Key.of( "CAMELLIA256_CBC" ), CMSAlgorithm.CAMELLIA256_CBC );
			put( Key.of( "CAMELLIA256_WRAP" ), CMSAlgorithm.CAMELLIA256_WRAP );
			put( Key.of( "CAST5_CBC" ), CMSAlgorithm.CAST5_CBC );
			put( Key.of( "DES_CBC" ), CMSAlgorithm.DES_CBC );
			put( Key.of( "DES_EDE3_CBC" ), CMSAlgorithm.DES_EDE3_CBC );
			put( Key.of( "DES_EDE3_WRAP" ), CMSAlgorithm.DES_EDE3_WRAP );
			put( Key.of( "ECCDH_SHA1KDF" ), CMSAlgorithm.ECCDH_SHA1KDF );
			put( Key.of( "ECCDH_SHA224KDF" ), CMSAlgorithm.ECCDH_SHA224KDF );
			put( Key.of( "ECCDH_SHA256KDF" ), CMSAlgorithm.ECCDH_SHA256KDF );
			put( Key.of( "ECCDH_SHA384KDF" ), CMSAlgorithm.ECCDH_SHA384KDF );
			put( Key.of( "ECCDH_SHA512KDF" ), CMSAlgorithm.ECCDH_SHA512KDF );
			put( Key.of( "ECDH_SHA1KDF" ), CMSAlgorithm.ECDH_SHA1KDF );
			put( Key.of( "ECDH_SHA224KDF" ), CMSAlgorithm.ECDH_SHA224KDF );
			put( Key.of( "ECDH_SHA256KDF" ), CMSAlgorithm.ECDH_SHA256KDF );
			put( Key.of( "ECDH_SHA384KDF" ), CMSAlgorithm.ECDH_SHA384KDF );
			put( Key.of( "ECDH_SHA512KDF" ), CMSAlgorithm.ECDH_SHA512KDF );
			put( Key.of( "ECMQV_SHA1KDF" ), CMSAlgorithm.ECMQV_SHA1KDF );
			put( Key.of( "ECMQV_SHA224KDF" ), CMSAlgorithm.ECMQV_SHA224KDF );
			put( Key.of( "ECMQV_SHA256KDF" ), CMSAlgorithm.ECMQV_SHA256KDF );
			put( Key.of( "ECMQV_SHA384KDF" ), CMSAlgorithm.ECMQV_SHA384KDF );
			put( Key.of( "ECMQV_SHA512KDF" ), CMSAlgorithm.ECMQV_SHA512KDF );
			put( Key.of( "GOST3411" ), CMSAlgorithm.GOST3411 );
			put( Key.of( "IDEA_CBC" ), CMSAlgorithm.IDEA_CBC );
			put( Key.of( "MD5" ), CMSAlgorithm.MD5 );
			put( Key.of( "RC2_CBC" ), CMSAlgorithm.RC2_CBC );
			put( Key.of( "RIPEMD128" ), CMSAlgorithm.RIPEMD128 );
			put( Key.of( "RIPEMD160" ), CMSAlgorithm.RIPEMD160 );
			put( Key.of( "RIPEMD256" ), CMSAlgorithm.RIPEMD256 );
			put( Key.of( "SEED_CBC" ), CMSAlgorithm.SEED_CBC );
			put( Key.of( "SEED_WRAP" ), CMSAlgorithm.SEED_WRAP );
			put( Key.of( "SHA1" ), CMSAlgorithm.SHA1 );
			put( Key.of( "SHA224" ), CMSAlgorithm.SHA224 );
			put( Key.of( "SHA256" ), CMSAlgorithm.SHA256 );
			put( Key.of( "SHA384" ), CMSAlgorithm.SHA384 );
			put( Key.of( "SHA512" ), CMSAlgorithm.SHA512 );
		}
	};

	public static MimeMultipart signMessagePart(
	    IStruct attributes,
	    MimeBodyPart messagePart ) {
		String	keystorePath		= attributes.getAsString( MailKeys.keystore );
		String	keystorePassword	= attributes.getAsString( MailKeys.keystorePassword );
		String	keyAlias			= attributes.getAsString( MailKeys.keyAlias );
		String	keyPassword			= attributes.getAsString( MailKeys.keyPassword );

		if ( keystorePath == null ) {
			throw new BoxRuntimeException( "A keystore argument is required in order to sign the message." );
		}

		try {
			SMIMESignedGenerator signer = newSignatureGenerator(
			    keystorePath,
			    keystorePassword,
			    keyAlias,
			    keyPassword
			);

			return signer.generate( messagePart );

		} catch ( Exception e ) {
			throw new BoxRuntimeException( "An error occcured while attempting to sign the message: " + e.getMessage(), e );
		}

	}

	public static MimeBodyPart encryptBodyPart(
	    IStruct attributes,
	    MimeBodyPart bodyPart ) {

		String	certPath			= attributes.getAsString( MailKeys.recipientCert );
		Key		encryptionAlgorithm	= Key.of( attributes.getAsString( MailKeys.encryptionAlgorithm ) );

		if ( !ENCRYPT_ALGORITHMS.containsKey( encryptionAlgorithm ) ) {
			throw new BoxRuntimeException( "The encryption algorithm specified [" + encryptionAlgorithm.getName() + "] is not supported." );
		}

		if ( certPath == null ) {
			throw new BoxRuntimeException( "A recipient certificate is required in order to encrypt the message." );
		}

		SMIMEEnvelopedGenerator generator = new SMIMEEnvelopedGenerator();

		try ( InputStream certificateInputStream = Files.newInputStream( Path.of( certPath ).toAbsolutePath(), StandardOpenOption.READ ) ) {
			CertificateFactory	certificateFactory	= new CertificateFactory();
			Certificate			parsedCert			= certificateFactory.engineGenerateCertificate( certificateInputStream );
			X509Certificate		recipientCert		= ( X509Certificate ) parsedCert;
			generator
			    .addRecipientInfoGenerator( new JceKeyTransRecipientInfoGenerator( recipientCert ).setProvider( BouncyCastleProvider.PROVIDER_NAME ) );
		} catch ( Exception e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to load the recipient certificate: " + e.getMessage(), e );
		}

		try {

			return generator.generate( bodyPart,
			    new JceCMSContentEncryptorBuilder( ENCRYPT_ALGORITHMS.get( encryptionAlgorithm ) ).setProvider( "BC" ).build() );

		} catch ( Exception e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to encrypt the message: " + e.getMessage(), e );
		}
	}

	public static MimeBodyPart decryptBodyPart(
	    MimeBodyPart bodyPart,
	    String certPath,
	    PrivateKey privateKey ) {

		try ( InputStream certificateInputStream = Files.newInputStream( Path.of( certPath ).toAbsolutePath(), StandardOpenOption.READ ) ) {
			CertificateFactory	certificateFactory	= new CertificateFactory();
			Certificate			parsedCert			= certificateFactory.engineGenerateCertificate( certificateInputStream );
			X509Certificate		recipientCert		= ( X509Certificate ) parsedCert;
			RecipientId			recipientId			= new JceKeyTransRecipientId( recipientCert );
			SMIMEToolkit		toolkit				= new SMIMEToolkit( new BcDigestCalculatorProvider() );
			return toolkit.decrypt( bodyPart, recipientId, new JceKeyTransEnvelopedRecipient( privateKey ) );
		} catch ( Exception e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to load the recipient certificate: " + e.getMessage(), e );
		}

	}

	/**
	 * Get a SMIMESignedGenerator for signing messages
	 *
	 * @param keystorePath     the absolute path to the keystore file
	 * @param keystorePassword the password for the keystore
	 * @param keyAlias         the alias of the key to use for signing
	 * @param keyPassword      the password for the key
	 *
	 * @return
	 */
	@SuppressWarnings( "unused" )
	public static SMIMESignedGenerator newSignatureGenerator(
	    String keystorePath,
	    String keystorePassword,
	    String keyAlias,
	    String keyPassword ) {

		KeyStore	keystore;
		Certificate	certificate;
		PrivateKey	privateKey;
		try {
			// Even if the keystore format is PKCS12, we still need to load it as PKCS12, which puts it in to compat mode
			keystore = KeyStore.getInstance( "PKCS12", BouncyCastleProvider.PROVIDER_NAME );

			keystore.load( Files.newInputStream( Path.of( keystorePath ), StandardOpenOption.READ ), keystorePassword.toCharArray() );

			certificate	= keystore.getCertificate( keyAlias );

			/* Get the private key to sign the message with */
			privateKey	= ( PrivateKey ) keystore.getKey(
			    keyAlias.toLowerCase(),
			    keyPassword.toCharArray()
			);

		} catch ( KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to load the keystore: " + e.getMessage(), e );
		}

		if ( certificate == null ) {
			throw new BoxRuntimeException( "Cannot find certificate for alias: " + keyAlias );
		}

		if ( privateKey == null ) {
			throw new BoxRuntimeException( "Cannot find private key in the certificate for alias: " + keyAlias );
		}

		/* Create the SMIMESignedGenerator */
		SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
		capabilities.addCapability( SMIMECapability.dES_EDE3_CBC );
		capabilities.addCapability( SMIMECapability.rC2_CBC, 128 );
		capabilities.addCapability( SMIMECapability.dES_CBC );

		ASN1EncodableVector vector = new ASN1EncodableVector();
		vector.add( new SMIMEEncryptionKeyPreferenceAttribute(
		    new IssuerAndSerialNumber(
		        new X500Name( ( ( X509Certificate ) certificate )
		            .getIssuerX500Principal().getName() ),
		        ( ( X509Certificate ) certificate ).getSerialNumber() ) ) );
		vector.add( new SMIMECapabilitiesAttribute( capabilities ) );

		SMIMESignedGenerator signer = new SMIMESignedGenerator();

		try {
			signer.addSignerInfoGenerator(
			    new JcaSimpleSignerInfoGeneratorBuilder().setProvider( "BC" ).setSignedAttributeGenerator( new AttributeTable( vector ) )
			        .build( "DSA".equals( privateKey.getAlgorithm() ) ? "SHA1withDSA" : "MD5withRSA", privateKey, ( X509Certificate ) certificate ) );
			/* Add the list of certs to the generator */
			List<Certificate> certList = new ArrayList<Certificate>();
			certList.add( certificate );
			JcaCertStore certs = new JcaCertStore( certList );
			signer.addCertificates( certs );

		} catch ( CertificateEncodingException | OperatorCreationException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to add the signature information to the mail part: " + e.getMessage(), e );
		}

		return signer;
	}

}
