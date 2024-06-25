package ortus.boxlang.modules.mail.util;

import java.io.ByteArrayInputStream;
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
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.operator.OperatorCreationException;

import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public final class MailEncryptionUtil {

	/**
	 * Add our mail cap entries to the default command map
	 */
	static final MailcapCommandMap mailcap = ( MailcapCommandMap ) CommandMap
	    .getDefaultCommandMap();
	static {
		mailcap
		    .addMailcap( "application/pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_signature" );
		mailcap
		    .addMailcap( "application/pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_mime" );
		mailcap
		    .addMailcap( "application/x-pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_signature" );
		mailcap
		    .addMailcap( "application/x-pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_mime" );
		mailcap
		    .addMailcap( "multipart/signed;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.multipart_signed" );

		CommandMap.setDefaultCommandMap( mailcap );

		if ( Security.getProvider( BouncyCastleProvider.PROVIDER_NAME ) == null ) {
			Security.addProvider( new BouncyCastleProvider() );
		}
	}

	public static MimeMultipart signMessagePart(
	    IStruct attributes,
	    MimeBodyPart messagePart ) {
		String	keystorePath		= attributes.getAsString( MailKeys.keystore );
		String	keystorePassword	= attributes.getAsString( MailKeys.keystorePassword );
		String	keyAlias			= attributes.getAsString( MailKeys.keyAlias );
		String	keyPassword			= attributes.getAsString( MailKeys.keyPassword );

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

	public static MimeBodyPart encryptMessagePart(
	    IStruct attributes,
	    MimeBodyPart bodyPart ) {

		String	keystorePath		= attributes.getAsString( MailKeys.keystore );
		String	keystorePassword	= attributes.getAsString( MailKeys.keystorePassword );
		String	certPath			= attributes.getAsString( MailKeys.recipientCert );

		try {

			KeyStore keystore = KeyStore.getInstance( "JKS", BouncyCastleProvider.PROVIDER_NAME );

			keystore.load( Files.newInputStream( Path.of( keystorePath ), StandardOpenOption.READ ), keystorePassword.toCharArray() );

			Enumeration<String>	e			= keystore.aliases();
			String				keyAlias	= null;

			while ( e.hasMoreElements() ) {
				String alias = ( String ) e.nextElement();

				if ( keystore.isKeyEntry( alias ) ) {
					keyAlias = alias;
				}
			}

			if ( keyAlias == null ) {
				System.err.println( "can't find a private key!" );
				System.exit( 0 );
			}

			Certificate[]			chain		= keystore.getCertificateChain( keyAlias );

			//
			// create the generator for creating an smime/encrypted message
			//
			SMIMEEnvelopedGenerator	generator	= new SMIMEEnvelopedGenerator();

			generator.addRecipientInfoGenerator( new JceKeyTransRecipientInfoGenerator( ( X509Certificate ) chain[ 0 ] ).setProvider( "BC" ) );

			// If we have been provided a recipient certificate, add it to the generator
			if ( certPath != null ) {
				try ( InputStream certificateInputStream = Files.newInputStream( Path.of( certPath ), StandardOpenOption.READ ) ) {
					byte[]					keyStoreBytes			= certificateInputStream.readAllBytes();
					ByteArrayInputStream	byteArrayInputStream	= new ByteArrayInputStream( keyStoreBytes );
					keystore.load( byteArrayInputStream, keystorePassword.toCharArray() );
					Certificate[]	certificates	= keystore.getCertificateChain( keyAlias );
					X509Certificate	recipientCert	= ( X509Certificate ) certificates[ 0 ];
					generator
					    .addRecipientInfoGenerator( new JceKeyTransRecipientInfoGenerator( recipientCert ).setProvider( BouncyCastleProvider.PROVIDER_NAME ) );
				}
			}

			return generator.generate( bodyPart, new JceCMSContentEncryptorBuilder( CMSAlgorithm.RC2_CBC ).setProvider( "BC" ).build() );

		} catch ( Exception e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to encrypt the message: " + e.getMessage(), e );
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
	public static SMIMESignedGenerator newSignatureGenerator(
	    String keystorePath,
	    String keystorePassword,
	    String keyAlias,
	    String keyPassword ) {

		KeyStore		keystore;
		Certificate[]	chain;
		PrivateKey		privateKey;
		try {
			// Even if the keystore format is PKCS12, we still need to load it as JKS, which puts it in to compat mode
			keystore = KeyStore.getInstance( "JKS", BouncyCastleProvider.PROVIDER_NAME );

			keystore.load( Files.newInputStream( Path.of( keystorePath ), StandardOpenOption.READ ), keystorePassword.toCharArray() );

			chain		= keystore.getCertificateChain( keyAlias );

			/* Get the private key to sign the message with */
			privateKey	= ( PrivateKey ) keystore.getKey(
			    keyAlias,
			    keystorePassword.toCharArray()
			);

		} catch ( KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to load the keystore: " + e.getMessage(), e );
		}

		if ( privateKey == null ) {
			throw new BoxRuntimeException( "cannot find private key for alias: " + keyAlias );
		}

		/* Create the SMIMESignedGenerator */
		SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
		capabilities.addCapability( SMIMECapability.dES_EDE3_CBC );
		capabilities.addCapability( SMIMECapability.rC2_CBC, 128 );
		capabilities.addCapability( SMIMECapability.dES_CBC );

		ASN1EncodableVector vector = new ASN1EncodableVector();
		vector.add( new SMIMEEncryptionKeyPreferenceAttribute(
		    new IssuerAndSerialNumber(
		        new X500Name( ( ( X509Certificate ) chain[ 0 ] )
		            .getIssuerX500Principal().getName() ),
		        ( ( X509Certificate ) chain[ 0 ] ).getSerialNumber() ) ) );
		vector.add( new SMIMECapabilitiesAttribute( capabilities ) );

		SMIMESignedGenerator signer = new SMIMESignedGenerator();

		try {
			signer.addSignerInfoGenerator(
			    new JcaSimpleSignerInfoGeneratorBuilder().setProvider( "BC" ).setSignedAttributeGenerator( new AttributeTable( vector ) )
			        .build( "DSA".equals( privateKey.getAlgorithm() ) ? "SHA1withDSA" : "MD5withRSA", privateKey, ( X509Certificate ) chain[ 0 ] ) );
			/* Add the list of certs to the generator */
			List<Certificate> certList = new ArrayList<Certificate>();
			certList.add( chain[ 0 ] );
			JcaCertStore certs = new JcaCertStore( certList );
			signer.addCertificates( certs );

		} catch ( CertificateEncodingException | OperatorCreationException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to add the signature information to the mail part: " + e.getMessage(), e );
		}

		return signer;
	}

}
