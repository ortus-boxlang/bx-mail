package ortus.boxlang.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

import org.apache.commons.mail2.jakarta.Email;
import org.apache.commons.mail2.jakarta.MultiPartEmail;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEToolkit;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMultipart;
import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.KeyCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MailTest {

	static BoxRuntime	instance;
	IBoxContext			context;
	IScope				variables;
	static Key			result					= new Key( "result" );
	static Key			messageId				= Key.of( "messageId" );
	static Key			messageVar				= Key.of( "messageVar" );
	static String		testURLImage			= "https://ortus-public.s3.amazonaws.com/logos/ortus-medium.jpg";
	static String		tmpDirectory			= "src/test/resources/tmp/MailTest";
	static String		testCert				= tmpDirectory + "/test.certificate.p12";
	static String		testKeystore			= tmpDirectory + "/test.keystore";
	static String		testBinaryFile			= tmpDirectory + "/test.jpg";
	static String		testKeystorePassword	= "foo";
	static String		testKeystoreAlias		= "testCert";

	@BeforeAll
	public static void setUp() throws IOException, URISyntaxException {
		instance = BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );

		System.out.println(
		    "Schedulers : " + instance.getSchedulerService().getSchedulers().entrySet().stream().map( entry -> KeyCaster.cast( entry.getKey() ).getName() )
		        .collect( Collectors.joining( "," ) ) );

		System.out.println( "Temp Directory Exists " + FileSystemUtil.exists( tmpDirectory ) );
		if ( !FileSystemUtil.exists( tmpDirectory ) ) {
			FileSystemUtil.createDirectory( tmpDirectory, true, null );
		}
		if ( Security.getProvider( BouncyCastleProvider.PROVIDER_NAME ) == null ) {
			Security.addProvider( new BouncyCastleProvider() );
		}
	}

	@BeforeEach
	public void setupEach()
	    throws IOException, URISyntaxException, CertificateException, KeyStoreException, NoSuchAlgorithmException, OperatorCreationException,
	    InvalidAlgorithmParameterException {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );
		variables.clear();
		System.out.println( "Test file exists " + FileSystemUtil.exists( testBinaryFile ) );
		if ( !FileSystemUtil.exists( testBinaryFile ) ) {
			BufferedInputStream urlStream = new BufferedInputStream( new URI( testURLImage ).toURL().openStream() );
			FileSystemUtil.write( testBinaryFile, urlStream.readAllBytes(), true );
			System.out.println( "Test file exists " + FileSystemUtil.exists( testBinaryFile ) );
			assertTrue( FileSystemUtil.exists( testBinaryFile ) );
		}
		if ( !FileSystemUtil.exists( testCert ) ) {
			generateTestCertificate();
		}
		if ( !FileSystemUtil.exists( testKeystore ) ) {
			generateTestKeyStore();
		}
	}

	@AfterAll
	public static void teardown() throws IOException {
		if ( FileSystemUtil.exists( tmpDirectory ) ) {
			FileSystemUtil.deleteDirectory( tmpDirectory, true );
		}

	}

	@DisplayName( "It can test a basic sending of mail with no spool" )
	@Test
	public void testMailComponent() {
		instance.executeSource(
		    """
		    <cfsetting enablecfoutputonly="true" />
		    <cfset message = "Hello mail!" />
		                        	<cfmail
		       			from="jclausen@ortussolutions.com"
		       			to="jclausen@ortussolutions.com"
		       			subject="Mail Test"
		       			server="127.0.0.1"
		       			port="25"
		       			spoolEnable="false"
		       			debug="true"
		       messageIdentifier="messageId"
		       messageVariable="messageVar"
		       		>
		       #message#
		       </cfmail>
		                        """,
		    context, BoxSourceType.CFTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with bx script" )
	@Test
	public void testMailComponentBX() {
		instance.executeSource(
		    """
		                     	<bx:mail
		    			from="jclausen@ortussolutions.com"
		    			to="jclausen@ortussolutions.com"
		    			subject="Mail Test"
		    			server="127.0.0.1"
		    			port="25"
		    			spoolEnable="false"
		    			debug="true"
		    messageIdentifier="messageId"
		    messageVariable="messageVar"
		    		>
		    Hello mail!
		    </bx:mail>
		                     """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with script" )
	@Test
	public void testMailComponentScript() {
		// @formatter:off
		instance.executeSource(
		    """
				bx:mail
				from="jclausen@ortussolutions.com"
				to="jclausen@ortussolutions.com"
				subject="Mail Test"
				server="127.0.0.1"
				port="25"
				spoolEnable="false"
				debug="true"
				messageIdentifier="messageId"
				messageVariable="messageVar"
				{
					writeOutput( "Hello mail!" );
				}
			""",
		    context, BoxSourceType.BOXSCRIPT );
		// @formatter:on

		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail using the default config settings" )
	@Test
	public void testDefaultSettings() {
		// @formatter:off
		instance.executeSource(
		    """
		    <bx:mail
				from="jclausen@ortussolutions.com"
				to="jclausen@ortussolutions.com"
				subject="Mail Test"
				spoolEnable="false"
				debug="true"
				messageIdentifier="messageId"
				messageVariable="messageVar"
			>
		    Hello mail!
		    </bx:mail>
		                     """,
		    context, BoxSourceType.BOXTEMPLATE );
		// @formatter:on
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertEquals( "Hello mail!", StringCaster.cast( message.getContent() ).trim() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with component parts" )
	@Test
	public void testMailComponentParts() throws MessagingException, IOException {
		instance.executeSource(
		    """
		                        	<bx:mail
		       			from="jclausen@ortussolutions.com"
		       			to="jclausen@ortussolutions.com"
		       			subject="Mail Test"
		       			server="127.0.0.1"
		       			port="25"
		       			spoolEnable="false"
		       			debug="true"
		       messageIdentifier="messageId"
		       messageVariable="messageVar"
		       		>
		       <bx:mailpart type="text">
		    Hello mail!
		    </bx:mailpart>
		       <bx:mailpart type="html">
		    <h1>Hello mail!</h1>
		    </bx:mailpart>
		       </bx:mail>
		                        """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertTrue( message.getEmailBody() instanceof MimeMultipart );
		MimeMultipart	part1	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 0 ).getContent();
		MimeMultipart	part2	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 1 ).getContent();
		assertEquals( "Hello mail!", part1.getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/plain;charset=utf-8", part1.getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "<h1>Hello mail!</h1>", part2.getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/html;charset=utf-8", part2.getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with a mime attachment" )
	@Test
	public void testMailMimeAttach() throws IOException, MessagingException {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		instance.executeSource(
		    """
		                          	<bx:mail
		         			from="jclausen@ortussolutions.com"
		         			to="jclausen@ortussolutions.com"
		         			subject="Mail Test"
		         			server="127.0.0.1"
		         			port="25"
		         			spoolEnable="false"
		         			debug="true"
		      mimeAttach="#testFile#"
		         messageIdentifier="messageId"
		         messageVariable="messageVar"
		         		>
		    Here's an image!
		         </bx:mail>
		                          """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email			message	= ( Email ) variables.get( messageVar );
		MimeMultipart	part1	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 0 ).getContent();
		assertEquals( "Here's an image!", part1.getBodyPart( 0 ).getContent().toString().trim() );
		assertTrue( ( ( MultiPartEmail ) message ).isBoolHasAttachments() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with a mime attachment, deleting the file after the message is sent" )
	@Test
	public void testMailMimeAttachRemove() throws InterruptedException, IOException, MessagingException {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		assertTrue( FileSystemUtil.exists( testBinaryFile ) );
		instance.executeSource(
		    """
		                           	<bx:mail
		          			from="jclausen@ortussolutions.com"
		          			to="jclausen@ortussolutions.com"
		          			subject="Mail Test"
		          			server="127.0.0.1"
		          			port="25"
		          			spoolEnable="false"
		          			debug="true"
		       mimeAttach="#testFile#"
		    remove=true
		          messageIdentifier="messageId"
		          messageVariable="messageVar"
		          		>
		     Here's an image!
		          </bx:mail>
		                           """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email			message	= ( Email ) variables.get( messageVar );
		MimeMultipart	part1	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 0 ).getContent();
		assertEquals( "Here's an image!", part1.getBodyPart( 0 ).getContent().toString().trim() );
		assertTrue( ( ( MultiPartEmail ) message ).isBoolHasAttachments() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
		// TODO: Per the docs the DELETE_ON_CLOSE may have a delay. We'll circle back to this and figure out away to test this
		// message = null;
		// assertFalse( FileSystemUtil.exists( testBinaryFile ) );
	}

	@DisplayName( "It can test a basic sending of mail with a mime attachment" )
	@Test
	public void testMultiPartWithFile() throws MessagingException, IOException {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		instance.executeSource(
		    """
		                           	<bx:mail
		          			from="jclausen@ortussolutions.com"
		          			to="jclausen@ortussolutions.com"
		          			subject="Mail Test"
		          			server="127.0.0.1"
		          			port="25"
		          			spoolEnable="false"
		          			debug="true"
		          messageIdentifier="messageId"
		          messageVariable="messageVar"
		          		>
		          <bx:mailpart type="text">
		       Hello mail!
		       </bx:mailpart>
		          <bx:mailpart type="html">
		       <h1>Hello mail!</h1>
		       </bx:mailpart>
		    <bx:mailparam file="#testFile#"/>
		          </bx:mail>
		                           """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertTrue( message.getEmailBody() instanceof MimeMultipart );
		assertTrue( ( ( MultiPartEmail ) message ).isBoolHasAttachments() );
		MimeMultipart	part1	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 0 ).getContent();
		MimeMultipart	part2	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 1 ).getContent();
		assertEquals( "Hello mail!", part1.getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/plain;charset=utf-8", part1.getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "<h1>Hello mail!</h1>", part2.getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/html;charset=utf-8", part2.getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic sending of mail with an inline attachment" )
	@Test
	public void testMultiPartWithInline() throws MessagingException, IOException {
		variables.put( Key.of( "testFile" ), testBinaryFile );
		instance.executeSource(
		    """
		                           	<bx:mail
		          			from="jclausen@ortussolutions.com"
		          			to="jclausen@ortussolutions.com"
		          			subject="Mail Test"
		          			server="127.0.0.1"
		          			port="25"
		          			spoolEnable="false"
		          			debug="true"
		          messageIdentifier="messageId"
		          messageVariable="messageVar"
		          		>
		          <bx:mailpart type="text">
		       Hello mail!
		       </bx:mailpart>
		          <bx:mailpart type="html">
		       <h1>Hello mail!</h1>
		       </bx:mailpart>
		    <bx:mailparam file="#testFile#" fileName="foo.jpg" disposition="inline"/>
		          </bx:mail>
		                           """,
		    context, BoxSourceType.BOXTEMPLATE );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email message = ( Email ) variables.get( messageVar );
		assertTrue( message.getEmailBody() instanceof MimeMultipart );
		MimeMultipart	part1	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 0 ).getContent();
		MimeMultipart	part2	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 1 ).getContent();
		assertEquals( "Hello mail!", part1.getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/plain;charset=utf-8", part1.getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "<h1>Hello mail!</h1>", part2.getBodyPart( 0 ).getContent().toString().trim() );
		assertEquals( "text/html;charset=utf-8", part2.getBodyPart( 0 ).getContentType().toString() );
		assertEquals( "image/jpeg; name=foo.jpg", message.getEmailBody().getBodyPart( 2 ).getContentType().toString() );
		assertEquals( "Mail Test", StringCaster.cast( message.getSubject() ).trim() );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test a basic encrypting of mail" )
	@Test
	public void testMailEncrypt() throws IOException, MessagingException {
		variables.put( Key.of( "testCert" ), testCert );
		instance.executeSource(
		    """
		    bx:mail
		    	from="jclausen@ortussolutions.com"
		    	to="jclausen@ortussolutions.com"
		    	subject="Mail Test"
		    	server="127.0.0.1"
		    	port="25"
		    	useSSL="false"
		    	useTLS="no"
		    	spoolEnable="false"
		    	debug="true"
		    	messageIdentifier="messageId"
		    	messageVariable="messageVar"
		    	encrypt=true
		    	recipientCert="#testCert#"{
		    		writeOutput( "Hello mail!" );
		    	}
		                                  """,
		    context, BoxSourceType.BOXSCRIPT );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email			message	= ( Email ) variables.get( messageVar );
		MimeMultipart	part1	= ( MimeMultipart ) message.getEmailBody().getBodyPart( 0 ).getContent();
		SMIMEToolkit	toolkit	= new SMIMEToolkit( new BcDigestCalculatorProvider() );
		assertTrue( toolkit.isEncrypted( ( ( MimeMultipart ) message.getContent() ).getBodyPart( 0 ) ) );
		assertTrue( toolkit.isEncrypted( part1.getBodyPart( 0 ) ) );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	@DisplayName( "It can test the signing of mail" )
	@Test
	public void testMailSign() throws IOException, MessagingException {
		variables.put( Key.of( "testKeystore" ), testKeystore );
		variables.put( Key.of( "keystorePassword" ), testKeystorePassword );
		variables.put( Key.of( "keystoreAlias" ), testKeystoreAlias );
		instance.executeSource(
		    """
		    bx:mail
		    	from="jclausen@ortussolutions.com"
		    	to="jclausen@ortussolutions.com"
		    	subject="Mail Test"
		    	server="127.0.0.1"
		    	port="25"
		    	spoolEnable="false"
		    	debug="true"
		    	messageIdentifier="messageId"
		    	messageVariable="messageVar"
		    	sign=true
		    	keystore="#testKeystore#"
		    	keystorePassword="#keystorePassword#"
		    	keyAlias="#keystoreAlias#"
		    	keyPassword="#keystorePassword#"
		          	{
		              writeOutput( "Hello mail!" );
		             }
		                               """,
		    context, BoxSourceType.BOXSCRIPT );
		assertTrue( variables.get( messageId ) instanceof String );
		assertTrue( variables.get( messageVar ) instanceof Email );
		Email			message	= ( Email ) variables.get( messageVar );
		SMIMEToolkit	toolkit	= new SMIMEToolkit( new BcDigestCalculatorProvider() );
		assertTrue( toolkit.isSigned( message.getMimeMessage() ) );
		assertEquals( "jclausen@ortussolutions.com", message.getToAddresses().get( 0 ).toString() );
		assertEquals( "jclausen@ortussolutions.com", message.getFromAddress().toString() );
	}

	private void generateTestCertificate() throws CertificateException, FileNotFoundException, IOException {
		String					cert		= "-----BEGIN CERTIFICATE-----\n"
		    + "MIIEQTCCAymgAwIBAgIBATANBgkqhkiG9w0BAQUFADCBkzEaMBgGA1UEAxMRTW9u\n"
		    + "a2V5IE1hY2hpbmUgQ0ExCzAJBgNVBAYTAlVLMREwDwYDVQQIEwhTY290bGFuZDEQ\n"
		    + "MA4GA1UEBxMHR2xhc2dvdzEcMBoGA1UEChMTbW9ua2V5bWFjaGluZS5jby51azEl\n"
		    + "MCMGCSqGSIb3DQEJARYWY2FAbW9ua2V5bWFjaGluZS5jby51azAeFw0wNTAzMDYy\n"
		    + "MzI4MjJaFw0wNjAzMDYyMzI4MjJaMIGvMQswCQYDVQQGEwJVSzERMA8GA1UECBMI\n"
		    + "U2NvdGxhbmQxEDAOBgNVBAcTB0dsYXNnb3cxGzAZBgNVBAoTEk1vbmtleSBNYWNo\n"
		    + "aW5lIEx0ZDElMCMGA1UECxMcT3BlbiBTb3VyY2UgRGV2ZWxvcG1lbnQgTGFiLjEU\n"
		    + "MBIGA1UEAxMLTHVrZSBUYXlsb3IxITAfBgkqhkiG9w0BCQEWEmx1a2VAbW9ua2V5\n"
		    + "bWFjaGluZTBcMA0GCSqGSIb3DQEBAQUAA0sAMEgCQQDItxZr07mm65ttYH7RMaVo\n"
		    + "VeMCq4ptfn+GFFEk4+54OkDuh1CHlk87gEc1jx3ZpQPJRTJx31z3YkiAcP+RDzxr\n"
		    + "AgMBAAGjggFIMIIBRDAJBgNVHRMEAjAAMBEGCWCGSAGG+EIBAQQEAwIHgDALBgNV\n"
		    + "HQ8EBAMCBeAwHQYDVR0OBBYEFG7mW1czzw4vFcL03+wUvvvPVFY8MIHABgNVHSME\n"
		    + "gbgwgbWAFKt47K8QG4qbH8exJY8WKPIXmq02oYGZpIGWMIGTMRowGAYDVQQDExFN\n"
		    + "b25rZXkgTWFjaGluZSBDQTELMAkGA1UEBhMCVUsxETAPBgNVBAgTCFNjb3RsYW5k\n"
		    + "MRAwDgYDVQQHEwdHbGFzZ293MRwwGgYDVQQKExNtb25rZXltYWNoaW5lLmNvLnVr\n"
		    + "MSUwIwYJKoZIhvcNAQkBFhZjYUBtb25rZXltYWNoaW5lLmNvLnVrggEAMDUGCWCG\n"
		    + "SAGG+EIBBAQoFiZodHRwczovL21vbmtleW1hY2hpbmUuY28udWsvY2EtY3JsLnBl\n"
		    + "bTANBgkqhkiG9w0BAQUFAAOCAQEAZ961bEgm2rOq6QajRLeoljwXDnt0S9BGEWL4\n"
		    + "PMU2FXDog9aaPwfmZ5fwKaSebwH4HckTp11xwe/D9uBZJQ74Uf80UL9z2eo0GaSR\n"
		    + "nRB3QPZfRvop0I4oPvwViKt3puLsi9XSSJ1w9yswnIf89iONT7ZyssPg48Bojo8q\n"
		    + "lcKwXuDRBWciODK/xWhvQbaegGJ1BtXcEHtvNjrUJLwSMDSr+U5oUYdMohG0h1iJ\n"
		    + "R+JQc49I33o2cTc77wfEWLtVdXAyYY4GSJR6VfgvV40x85ItaNS3HHfT/aXU1x4m\n"
		    + "W9YQkWlA6t0blGlC+ghTOY1JbgWnEfXMmVgg9a9cWaYQ+NQwqA==\n" + "-----END CERTIFICATE-----";
		ByteArrayInputStream	in			= new ByteArrayInputStream( cert.getBytes() );
		CertificateFactory		cf			= CertificateFactory.getInstance( "X.509" );
		X509Certificate			finalCert	= ( X509Certificate ) cf.generateCertificate( in );
		try ( FileOutputStream fos = new FileOutputStream( Path.of( testCert ).toFile() ) ) {
			fos.write( finalCert.getEncoded() );
		}
	}

	private void generateTestKeyStore()
	    throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, OperatorCreationException, InvalidAlgorithmParameterException {
		KeyStore	ks			= KeyStore.getInstance( KeyStore.getDefaultType() );

		char[]		password	= testKeystorePassword.toCharArray();

		ks.load( null, password );

		SecureRandom rnd = SecureRandom.getInstance( "SHA1PRNG" );
		rnd.setSeed( testKeystorePassword.getBytes() );

		RSAKeyGenParameterSpec	spec			= new RSAKeyGenParameterSpec( 2048, RSAKeyGenParameterSpec.F4 );
		KeyPairGenerator		pairGenerator	= KeyPairGenerator.getInstance( "RSA" );
		pairGenerator.initialize( spec, rnd );

		KeyPair						keyPair			= pairGenerator.generateKeyPair();
		SubjectPublicKeyInfo		subPubKeyInfo	= SubjectPublicKeyInfo.getInstance( keyPair.getPublic().getEncoded() );
		Instant						now				= Instant.now();
		Date						validFrom		= Date.from( now );
		Date						validTo			= Date.from( now.plusSeconds( 60L * 60 * 24 * 365 ) );

		X509v3CertificateBuilder	certBuilder		= new X509v3CertificateBuilder(
		    new X500Name( "CN=My Application,O=My Organisation,L=My City,C=DE" ),
		    BigInteger.ONE,
		    validFrom,
		    validTo,
		    new X500Name( "CN=My Application,O=My Organisation,L=My City,C=DE" ),
		    subPubKeyInfo
		);

		ContentSigner				signer			= new JcaContentSignerBuilder( "SHA256WithRSA" )
		    .setProvider( BouncyCastleProvider.PROVIDER_NAME )
		    .build( keyPair.getPrivate() );

		X509CertificateHolder		certificate		= certBuilder.build( signer );

		X509Certificate[]			chain			= new X509Certificate[] {
		    new JcaX509CertificateConverter()
		        .setProvider( BouncyCastleProvider.PROVIDER_NAME )
		        .getCertificate( certificate )
		};
		ks.setKeyEntry(
		    testKeystoreAlias, keyPair.getPrivate(), testKeystorePassword.toCharArray(), chain
		);

		// Save the keystore to a file
		FileOutputStream fos = new FileOutputStream( Path.of( testKeystore ).toFile() );
		ks.store( fos, password );
		fos.close();
	}

}
